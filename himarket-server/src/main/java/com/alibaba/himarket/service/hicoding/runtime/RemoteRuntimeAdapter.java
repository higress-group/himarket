package com.alibaba.himarket.service.hicoding.runtime;

import com.alibaba.himarket.service.hicoding.filesystem.FileSystemAdapter;
import com.alibaba.himarket.service.hicoding.filesystem.SidecarFileSystemAdapter;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * 远程 Sidecar 运行时适配器。
 * <p>
 * 通过 WebSocket 连接远程 Sidecar 服务与 CLI 进程通信。
 * 不依赖 K8s API，健康状态完全基于 WebSocket 连接状态判断。
 * Sidecar 可以部署在 K8s、Docker、裸机等任意环境。
 */
public class RemoteRuntimeAdapter implements RuntimeAdapter {

    private static final Logger logger = LoggerFactory.getLogger(RemoteRuntimeAdapter.class);

    static final long WS_PING_INTERVAL_SECONDS = 10;
    static final long DEFAULT_IDLE_TIMEOUT_SECONDS = 600;

    private final String host;
    private final int port;

    private final Sinks.Many<String> stdoutSink =
            Sinks.many().multicast().onBackpressureBuffer(256, false);
    private final Sinks.Many<String> wsSendSink = Sinks.many().unicast().onBackpressureBuffer();
    private volatile RuntimeStatus status = RuntimeStatus.CREATING;
    private URI sidecarWsUri;
    private SidecarFileSystemAdapter fileSystem;
    private Disposable wsConnection;
    private ScheduledFuture<?> wsPingFuture;
    private ScheduledFuture<?> idleCheckFuture;
    private final AtomicReference<org.springframework.web.reactive.socket.WebSocketSession>
            wsSessionRef = new AtomicReference<>();
    private final ScheduledExecutorService scheduler;

    private volatile Instant lastActiveAt;
    private long idleTimeoutSeconds = DEFAULT_IDLE_TIMEOUT_SECONDS;
    private Consumer<RuntimeFaultNotification> faultListener;

    public RemoteRuntimeAdapter(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be null or blank");
        }
        this.host = host;
        this.port = port;
        this.fileSystem = new SidecarFileSystemAdapter(host);
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "remote-runtime-scheduler");
                            t.setDaemon(true);
                            return t;
                        });
    }

    @Override
    public SandboxType getType() {
        return SandboxType.REMOTE;
    }

    @Override
    public String start(RuntimeConfig config) throws RuntimeException {
        throw new UnsupportedOperationException("使用 connect(URI) 方法连接远程 Sidecar");
    }

    /**
     * 连接到远程 Sidecar WebSocket 端点。
     */
    public void connect(URI wsUri) {
        if (status != RuntimeStatus.CREATING) {
            throw new RuntimeException("Cannot connect: current status is " + status);
        }
        this.sidecarWsUri = wsUri;

        try {
            connectWebSocket(wsUri);
            startWsPing();
            startIdleCheck();
            lastActiveAt = Instant.now();
            status = RuntimeStatus.RUNNING;
        } catch (Exception e) {
            status = RuntimeStatus.ERROR;
            throw new RuntimeException("Failed to connect to remote sidecar: " + e.getMessage(), e);
        }
    }

    @Override
    public void send(String jsonLine) throws IOException {
        if (status != RuntimeStatus.RUNNING) {
            throw new IOException("Remote runtime is not running, current status: " + status);
        }
        lastActiveAt = Instant.now();
        Sinks.EmitResult result = wsSendSink.tryEmitNext(jsonLine);
        if (result.isFailure()) {
            throw new IOException("Failed to send message to sidecar, emit result: " + result);
        }
    }

    @Override
    public Flux<String> stdout() {
        return stdoutSink.asFlux();
    }

    @Override
    public RuntimeStatus getStatus() {
        // 纯粹基于 WebSocket 连接状态判断
        if (status == RuntimeStatus.RUNNING) {
            var session = wsSessionRef.get();
            if (session == null || !session.isOpen()) {
                status = RuntimeStatus.ERROR;
            }
        }
        return status;
    }

    @Override
    public boolean isAlive() {
        if (status != RuntimeStatus.RUNNING) {
            return false;
        }
        var session = wsSessionRef.get();
        return session != null && session.isOpen();
    }

    @Override
    public void close() {
        if (status == RuntimeStatus.STOPPED) {
            return;
        }
        logger.info("Closing RemoteRuntimeAdapter: host={}:{}", host, port);

        if (wsPingFuture != null) {
            wsPingFuture.cancel(false);
            wsPingFuture = null;
        }
        if (idleCheckFuture != null) {
            idleCheckFuture.cancel(false);
            idleCheckFuture = null;
        }

        wsSendSink.tryEmitComplete();
        if (wsConnection != null) {
            wsConnection.dispose();
            wsConnection = null;
        }
        var wsSession = wsSessionRef.getAndSet(null);
        if (wsSession != null) {
            wsSession.close().subscribe();
        }

        stdoutSink.tryEmitComplete();
        scheduler.shutdownNow();
        status = RuntimeStatus.STOPPED;
    }

    @Override
    public FileSystemAdapter getFileSystem() {
        return fileSystem;
    }

    // ===== 公共方法 =====

    public void touchActivity() {
        lastActiveAt = Instant.now();
    }

    public void setFaultListener(Consumer<RuntimeFaultNotification> listener) {
        this.faultListener = listener;
    }

    public void setIdleTimeoutSeconds(long seconds) {
        this.idleTimeoutSeconds = seconds;
    }

    // ===== 内部方法 =====

    private void connectWebSocket(URI wsUri) {
        logger.info("Connecting to remote sidecar WebSocket: {}", wsUri);
        ReactorNettyWebSocketClient wsClient =
                new ReactorNettyWebSocketClient(
                        reactor.netty.http.client.HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(30)));
        wsClient.setHandlePing(true);
        wsClient.setMaxFramePayloadLength(1024 * 1024);
        CountDownLatch connectedLatch = new CountDownLatch(1);

        wsConnection =
                wsClient.execute(
                                wsUri,
                                session -> {
                                    wsSessionRef.set(session);
                                    logger.info(
                                            "[WS-Remote] Session established: host={}:{},"
                                                    + " sessionId={}",
                                            host,
                                            port,
                                            session.getId());

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
                                                                        "[WS-Remote] Received: {}",
                                                                        text.length() > 300
                                                                                ? text.substring(
                                                                                                0,
                                                                                                300)
                                                                                        + "..."
                                                                                : text);
                                                                lastActiveAt = Instant.now();
                                                                stdoutSink.tryEmitNext(text);
                                                            })
                                                    .doOnError(
                                                            err -> {
                                                                logger.warn(
                                                                        "[WS-Remote] Receive error:"
                                                                                + " {}",
                                                                        err.getMessage());
                                                                status = RuntimeStatus.ERROR;
                                                                notifyFault(
                                                                        RuntimeFaultNotification
                                                                                .FAULT_CONNECTION_LOST,
                                                                        RuntimeFaultNotification
                                                                                .ACTION_RECONNECT);
                                                            })
                                                    .doOnComplete(
                                                            () -> {
                                                                logger.warn(
                                                                        "[WS-Remote] Receive stream"
                                                                            + " completed (sidecar"
                                                                            + " closed)");
                                                                status = RuntimeStatus.ERROR;
                                                            })
                                                    .then();

                                    Mono<Void> send =
                                            session.send(
                                                    wsSendSink
                                                            .asFlux()
                                                            .doOnNext(
                                                                    msg ->
                                                                            logger.info(
                                                                                    "[WS-Remote]"
                                                                                        + " Sending:"
                                                                                        + " {}",
                                                                                    msg.length()
                                                                                                    > 300
                                                                                            ? msg
                                                                                                            .substring(
                                                                                                                    0,
                                                                                                                    300)
                                                                                                    + "..."
                                                                                            : msg))
                                                            .map(session::textMessage));

                                    connectedLatch.countDown();
                                    return Mono.when(receive, send);
                                })
                        .subscribe(
                                unused -> logger.info("[WS-Remote] Connection completed normally"),
                                err -> {
                                    logger.error(
                                            "[WS-Remote] Connection failed: {}", err.getMessage());
                                    connectedLatch.countDown();
                                });

        try {
            if (!connectedLatch.await(10, TimeUnit.SECONDS)) {
                throw new RuntimeException(
                        "Timeout waiting for WebSocket connection to sidecar at " + wsUri);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while connecting to sidecar WebSocket", e);
        }

        if (wsSessionRef.get() == null) {
            throw new RuntimeException(
                    "Failed to establish WebSocket connection to sidecar at " + wsUri);
        }
        logger.info("WebSocket connected to remote sidecar: {}", wsUri);
    }

    private void startWsPing() {
        wsPingFuture =
                scheduler.scheduleAtFixedRate(
                        () -> {
                            try {
                                var session = wsSessionRef.get();
                                if (session == null || !session.isOpen()) {
                                    return;
                                }
                                session.send(
                                                Mono.just(
                                                        session.pingMessage(
                                                                factory ->
                                                                        factory.wrap(
                                                                                "ping"
                                                                                        .getBytes(
                                                                                                StandardCharsets
                                                                                                        .UTF_8)))))
                                        .subscribe(
                                                unused -> {},
                                                err ->
                                                        logger.warn(
                                                                "[WS-Ping] Failed: {}",
                                                                err.getMessage()));
                            } catch (Exception e) {
                                logger.warn("[WS-Ping] Error: {}", e.getMessage());
                            }
                        },
                        WS_PING_INTERVAL_SECONDS,
                        WS_PING_INTERVAL_SECONDS,
                        TimeUnit.SECONDS);
    }

    private void startIdleCheck() {
        idleCheckFuture =
                scheduler.scheduleAtFixedRate(
                        () -> {
                            try {
                                if (status != RuntimeStatus.RUNNING || lastActiveAt == null) {
                                    return;
                                }
                                long idleSeconds =
                                        Duration.between(lastActiveAt, Instant.now()).getSeconds();
                                if (idleSeconds >= idleTimeoutSeconds) {
                                    logger.info(
                                            "Remote sidecar idle for {}s (threshold: {}s),"
                                                    + " notifying fault",
                                            idleSeconds,
                                            idleTimeoutSeconds);
                                    status = RuntimeStatus.STOPPED;
                                    notifyFault(
                                            RuntimeFaultNotification.FAULT_IDLE_TIMEOUT,
                                            RuntimeFaultNotification.ACTION_RECREATE);
                                    stdoutSink.tryEmitComplete();
                                }
                            } catch (Exception e) {
                                logger.warn("Idle check error: {}", e.getMessage());
                            }
                        },
                        30,
                        30,
                        TimeUnit.SECONDS);
    }

    private void notifyFault(String faultType, String suggestedAction) {
        if (faultListener != null) {
            try {
                faultListener.accept(
                        new RuntimeFaultNotification(
                                faultType, SandboxType.REMOTE, suggestedAction));
            } catch (Exception e) {
                logger.warn("Error notifying fault listener: {}", e.getMessage());
            }
        }
    }

    // ===== 用于测试的 Getter =====

    URI getSidecarWsUri() {
        return sidecarWsUri;
    }

    Instant getLastActiveAt() {
        return lastActiveAt;
    }
}
