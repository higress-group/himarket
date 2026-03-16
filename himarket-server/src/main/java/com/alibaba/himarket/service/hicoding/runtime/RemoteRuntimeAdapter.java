package com.alibaba.himarket.service.hicoding.runtime;

import com.alibaba.himarket.service.hicoding.filesystem.FileSystemAdapter;
import com.alibaba.himarket.service.hicoding.filesystem.SidecarFileSystemAdapter;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
 *
 * <p>通过 WebSocket 连接远程 Sidecar 服务与 CLI 进程通信。 不依赖 K8s API，健康状态完全基于 WebSocket
 * 连接状态判断。 Sidecar 可以部署在 K8s、Docker、裸机等任意环境。
 *
 * <p>支持 detach/reconnect 语义：WebSocket 断开时进入 DETACHED 状态， sidecar 端的 CLI
 * 进程继续运行并缓冲输出，后续可通过 reconnect() 重新连接。
 */
public class RemoteRuntimeAdapter implements RuntimeAdapter {

    private static final Logger logger = LoggerFactory.getLogger(RemoteRuntimeAdapter.class);
    private static final ObjectMapper CONTROL_MSG_MAPPER = new ObjectMapper();

    static final long WS_PING_INTERVAL_SECONDS = 10;

    private final String host;
    private final int port;

    private final Sinks.Many<String> stdoutSink =
            Sinks.many().multicast().onBackpressureBuffer(256, false);
    private Sinks.Many<String> wsSendSink = Sinks.many().unicast().onBackpressureBuffer();
    private volatile RuntimeStatus status = RuntimeStatus.CREATING;
    private volatile String sidecarSessionId;
    private URI sidecarWsUri;
    private SidecarFileSystemAdapter fileSystem;
    private Disposable wsConnection;
    private ScheduledFuture<?> wsPingFuture;
    private final AtomicReference<org.springframework.web.reactive.socket.WebSocketSession>
            wsSessionRef = new AtomicReference<>();
    private final ScheduledExecutorService scheduler;

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
            status = RuntimeStatus.RUNNING;
        } catch (Exception e) {
            status = RuntimeStatus.ERROR;
            throw new RuntimeException("Failed to connect to remote sidecar: " + e.getMessage(), e);
        }
    }

    /**
     * 获取 sidecar 分配的会话 ID。 通过 sidecar 的 session_meta 控制消息获取，首次连接后可用。
     */
    public String getSidecarSessionId() {
        return sidecarSessionId;
    }

    /**
     * 将适配器从 RUNNING 状态切换到 DETACHED 状态。 关闭 WebSocket 连接和 ping，但保留 stdoutSink
     * 以供后续 reattach。
     */
    public void detach() {
        if (status != RuntimeStatus.RUNNING) {
            logger.warn("Cannot detach: current status is {}", status);
            return;
        }
        logger.info(
                "Detaching RemoteRuntimeAdapter: host={}:{}, sidecarSessionId={}",
                host,
                port,
                sidecarSessionId);

        // 先设置状态，使得 WS 关闭触发的 doOnError/doOnComplete 不会误判为异常
        status = RuntimeStatus.DETACHED;

        if (wsPingFuture != null) {
            wsPingFuture.cancel(false);
            wsPingFuture = null;
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
        // 注意：不 complete stdoutSink，保持它可用于 reattach
    }

    /**
     * 从 DETACHED 状态重新连接到 sidecar，使用已存储的 sidecarSessionId。
     */
    public void reconnect() {
        if (sidecarSessionId == null) {
            throw new RuntimeException("Cannot reconnect: no sidecarSessionId available");
        }
        URI attachUri =
                URI.create(
                        "ws://"
                                + host
                                + ":"
                                + port
                                + "/?sessionId="
                                + URLEncoder.encode(sidecarSessionId, StandardCharsets.UTF_8));
        reconnect(attachUri);
    }

    /**
     * 从 DETACHED 状态重新连接到指定的 sidecar WebSocket URI。
     */
    public void reconnect(URI wsUri) {
        if (status != RuntimeStatus.DETACHED) {
            throw new RuntimeException("Cannot reconnect: current status is " + status);
        }
        logger.info("Reconnecting RemoteRuntimeAdapter to: {}", wsUri);

        this.sidecarWsUri = wsUri;
        this.wsSendSink = Sinks.many().unicast().onBackpressureBuffer();

        try {
            connectWebSocket(wsUri);
            startWsPing();
            status = RuntimeStatus.RUNNING;
        } catch (Exception e) {
            status = RuntimeStatus.ERROR;
            throw new RuntimeException(
                    "Failed to reconnect to remote sidecar: " + e.getMessage(), e);
        }
    }

    @Override
    public void send(String jsonLine) throws IOException {
        if (status != RuntimeStatus.RUNNING) {
            throw new IOException("Remote runtime is not running, current status: " + status);
        }
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

    public void setFaultListener(Consumer<RuntimeFaultNotification> listener) {
        this.faultListener = listener;
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

                                                                // Sidecar 可能在单个 WebSocket
                                                                // 帧中发送多条 JSONL
                                                                // 消息（换行分隔），
                                                                // 需逐行拆分后分别处理，
                                                                // 否则前端 JSON.parse 会失败
                                                                if (text.indexOf('\n') < 0) {
                                                                    processReceivedLine(text);
                                                                } else {
                                                                    for (String line :
                                                                            text.split("\n")) {
                                                                        if (!line.isBlank()) {
                                                                            processReceivedLine(
                                                                                    line);
                                                                        }
                                                                    }
                                                                }
                                                            })
                                                    .doOnError(
                                                            err -> {
                                                                // detach 期间 WS 关闭是预期行为
                                                                if (status
                                                                        == RuntimeStatus.DETACHED) {
                                                                    logger.debug(
                                                                            "[WS-Remote] WS error"
                                                                                + " during detach"
                                                                                + " (expected): {}",
                                                                            err.getMessage());
                                                                    return;
                                                                }
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
                                                                if (status
                                                                        == RuntimeStatus.DETACHED) {
                                                                    logger.debug(
                                                                            "[WS-Remote] WS"
                                                                                + " completed"
                                                                                + " during detach"
                                                                                + " (expected)");
                                                                    return;
                                                                }
                                                                logger.warn(
                                                                        "[WS-Remote] Receive stream"
                                                                            + " completed (sidecar"
                                                                            + " closed)");
                                                                status = RuntimeStatus.ERROR;
                                                                notifyFault(
                                                                        RuntimeFaultNotification
                                                                                .FAULT_CONNECTION_LOST,
                                                                        RuntimeFaultNotification
                                                                                .ACTION_RECONNECT);
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
                                                                                    msg))
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

    /**
     * 处理单条接收到的消息行：拦截控制消息或转发到 stdoutSink。
     */
    private void processReceivedLine(String line) {
        if (isControlMessage(line)) {
            handleControlMessage(line);
            return;
        }
        logger.info("[WS-Remote] Received: {}", line);
        stdoutSink.tryEmitNext(line);
    }

    /**
     * 判断是否为 sidecar 控制消息（session_meta、buffer_truncated、process_exited）。
     */
    private boolean isControlMessage(String text) {
        return text.contains("\"type\":")
                && (text.contains("\"session_meta\"")
                        || text.contains("\"buffer_truncated\"")
                        || text.contains("\"process_exited\""));
    }

    /**
     * 处理 sidecar 控制消息，不转发到 stdoutSink（process_exited 除外）。
     */
    private void handleControlMessage(String text) {
        try {
            JsonNode node = CONTROL_MSG_MAPPER.readTree(text);
            String type = node.has("type") ? node.get("type").asText() : null;

            if ("session_meta".equals(type)) {
                if (node.has("sessionId")) {
                    sidecarSessionId = node.get("sessionId").asText();
                }
                logger.info(
                        "[WS-Remote] session_meta: sidecarSessionId={}, state={}",
                        sidecarSessionId,
                        node.has("state") ? node.get("state").asText() : "unknown");
                return;
            }

            if ("buffer_truncated".equals(type)) {
                long dropped = node.has("droppedBytes") ? node.get("droppedBytes").asLong() : 0;
                logger.warn("[WS-Remote] Buffer truncated: droppedBytes={}", dropped);
                return;
            }

            if ("process_exited".equals(type)) {
                int code = node.has("code") ? node.get("code").asInt(-1) : -1;
                String signal = node.has("signal") ? node.get("signal").asText(null) : null;
                logger.info("[WS-Remote] Process exited: code={}, signal={}", code, signal);
                // 转发给前端，让前端感知 CLI 进程退出
                stdoutSink.tryEmitNext(text);
                return;
            }
        } catch (Exception e) {
            logger.debug(
                    "Failed to parse control message, forwarding as stdout: {}", e.getMessage());
        }
        // 无法解析为控制消息，作为普通 stdout 转发
        stdoutSink.tryEmitNext(text);
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
}
