package com.alibaba.himarket.service.acp.runtime;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * 本地 Sidecar WebSocket 适配器。
 *
 * <p>通过 Reactor Netty WebSocket 连接到本地 Sidecar Server， 桥接 CLI 进程的 stdin/stdout。不依赖 K8s。
 */
public class LocalSidecarAdapter implements RuntimeAdapter {

    private static final Logger logger = LoggerFactory.getLogger(LocalSidecarAdapter.class);
    private static final long WS_PING_INTERVAL_SECONDS = 10;

    private final Sinks.Many<String> stdoutSink = Sinks.many().multicast().directBestEffort();
    private final Sinks.Many<String> wsSendSink = Sinks.many().unicast().onBackpressureBuffer();
    private volatile RuntimeStatus status = RuntimeStatus.CREATING;
    private final URI sidecarWsUri;
    private Disposable wsConnection;
    private ScheduledFuture<?> wsPingFuture;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<org.springframework.web.reactive.socket.WebSocketSession>
            wsSessionRef = new AtomicReference<>();

    public LocalSidecarAdapter(URI sidecarWsUri) {
        this.sidecarWsUri = sidecarWsUri;
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "local-sidecar-ping");
                            t.setDaemon(true);
                            return t;
                        });
    }

    /** 建立 WebSocket 连接并启动心跳。 */
    public void connect() {
        logger.info("[LocalSidecar] 连接到 Sidecar WebSocket: {}", sidecarWsUri);
        ReactorNettyWebSocketClient wsClient =
                new ReactorNettyWebSocketClient(
                        reactor.netty.http.client.HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(30)));
        wsClient.setHandlePing(true);
        wsClient.setMaxFramePayloadLength(1024 * 1024);
        CountDownLatch connectedLatch = new CountDownLatch(1);

        wsConnection =
                wsClient.execute(
                                sidecarWsUri,
                                session -> {
                                    wsSessionRef.set(session);
                                    Mono<Void> receive =
                                            session.receive()
                                                    .doOnNext(
                                                            msg -> {
                                                                if (msg.getType()
                                                                        == WebSocketMessage.Type
                                                                                .PONG) {
                                                                    return;
                                                                }
                                                                String text =
                                                                        msg.getPayloadAsText();
                                                                logger.info(
                                                                        "[LocalSidecar] 收到: {}",
                                                                        text.length() > 300
                                                                                ? text.substring(
                                                                                                0,
                                                                                                300)
                                                                                        + "..."
                                                                                : text);
                                                                stdoutSink.tryEmitNext(text);
                                                            })
                                                    .doOnError(
                                                            err -> {
                                                                logger.warn(
                                                                        "[LocalSidecar] 接收错误:"
                                                                                + " {}",
                                                                        err.getMessage());
                                                                status = RuntimeStatus.ERROR;
                                                            })
                                                    .doOnComplete(
                                                            () -> {
                                                                logger.warn(
                                                                        "[LocalSidecar]"
                                                                                + " 连接已关闭");
                                                                status = RuntimeStatus.ERROR;
                                                            })
                                                    .then();
                                    Mono<Void> send =
                                            session.send(
                                                    wsSendSink.asFlux().map(session::textMessage));
                                    connectedLatch.countDown();
                                    return Mono.when(receive, send);
                                })
                        .subscribe(
                                unused -> {},
                                err -> {
                                    logger.error("[LocalSidecar] 连接失败: {}", err.getMessage());
                                    connectedLatch.countDown();
                                });

        try {
            if (!connectedLatch.await(10, TimeUnit.SECONDS)) {
                throw new RuntimeException("连接本地 Sidecar WebSocket 超时: " + sidecarWsUri);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("连接本地 Sidecar WebSocket 被中断", e);
        }

        if (wsSessionRef.get() == null) {
            throw new RuntimeException("无法建立到本地 Sidecar 的 WebSocket 连接: " + sidecarWsUri);
        }

        startWsPing();
        status = RuntimeStatus.RUNNING;
        logger.info("[LocalSidecar] WebSocket 连接成功: {}", sidecarWsUri);
    }

    @Override
    public SandboxType getType() {
        return SandboxType.LOCAL;
    }

    @Override
    public String start(RuntimeConfig config) {
        throw new UnsupportedOperationException("使用 connect() 方法");
    }

    @Override
    public void send(String jsonLine) throws IOException {
        if (status != RuntimeStatus.RUNNING) {
            throw new IOException("本地 Sidecar 未运行, 状态: " + status);
        }
        Sinks.EmitResult result = wsSendSink.tryEmitNext(jsonLine);
        if (result.isFailure()) {
            throw new IOException("发送消息到 Sidecar 失败: " + result);
        }
    }

    @Override
    public Flux<String> stdout() {
        return stdoutSink.asFlux();
    }

    @Override
    public RuntimeStatus getStatus() {
        return status;
    }

    @Override
    public boolean isAlive() {
        return status == RuntimeStatus.RUNNING && wsSessionRef.get() != null;
    }

    @Override
    public void close() {
        if (status == RuntimeStatus.STOPPED) {
            return;
        }
        if (wsPingFuture != null) {
            wsPingFuture.cancel(false);
        }
        wsSendSink.tryEmitComplete();
        if (wsConnection != null) {
            wsConnection.dispose();
        }
        var session = wsSessionRef.getAndSet(null);
        if (session != null) {
            session.close().subscribe();
        }
        stdoutSink.tryEmitComplete();
        scheduler.shutdownNow();
        status = RuntimeStatus.STOPPED;
    }

    @Override
    public FileSystemAdapter getFileSystem() {
        return null; // 本地模式文件操作通过 Sidecar HTTP API
    }

    private void startWsPing() {
        wsPingFuture =
                scheduler.scheduleAtFixedRate(
                        () -> {
                            var session = wsSessionRef.get();
                            if (session == null || !session.isOpen()) {
                                return;
                            }
                            session.send(
                                            Mono.just(
                                                    session.pingMessage(
                                                            f ->
                                                                    f.wrap(
                                                                            "ping"
                                                                                    .getBytes(
                                                                                            StandardCharsets
                                                                                                    .UTF_8)))))
                                    .subscribe(unused -> {}, err -> {});
                        },
                        WS_PING_INTERVAL_SECONDS,
                        WS_PING_INTERVAL_SECONDS,
                        TimeUnit.SECONDS);
    }
}
