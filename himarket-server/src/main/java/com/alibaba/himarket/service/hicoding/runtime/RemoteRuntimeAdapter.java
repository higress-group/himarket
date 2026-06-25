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
 * Remote Sidecar runtime adapter.
 *
 * <p>Connects to remote Sidecar through WebSocket to communicate with the CLI process. It does not
 * depend on K8s APIs; health is determined from the WebSocket connection state. Sidecar can run in
 * K8s, Docker, bare metal, or any reachable environment.
 *
 * <p>Supports detach/reconnect semantics: when WebSocket disconnects, the adapter enters DETACHED
 * state while the CLI process continues running on Sidecar and buffers output. reconnect() can
 * attach later.
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
        throw new UnsupportedOperationException("Use connect(URI) to connect remote Sidecar");
    }

    /**
     * Connects to the remote Sidecar WebSocket endpoint.
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
     * Returns the Sidecar-assigned session ID.
     * It is populated from the Sidecar session_meta control message after the first connection.
     */
    public String getSidecarSessionId() {
        return sidecarSessionId;
    }

    /**
     * Moves the adapter from RUNNING to DETACHED.
     * Closes the WebSocket connection and ping task while keeping stdoutSink for later reattach.
     */
    public void detach() {
        if (status != RuntimeStatus.RUNNING) {
            logger.warn("Cannot detach remote runtime adapter, status={}", status);
            return;
        }
        logger.info(
                "Detaching remote runtime adapter, host={}, port={}, sidecarSessionId={}",
                host,
                port,
                sidecarSessionId);

        // Set status first so WS close callbacks are not treated as failures.
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
        // Keep stdoutSink open so it can be reused after reattach.
    }

    /**
     * Reconnects from DETACHED state using the stored sidecarSessionId.
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
     * Reconnects from DETACHED state to the specified Sidecar WebSocket URI.
     */
    public void reconnect(URI wsUri) {
        if (status != RuntimeStatus.DETACHED) {
            throw new RuntimeException("Cannot reconnect: current status is " + status);
        }
        logger.info("Reconnecting remote runtime adapter, wsUri={}", wsUri);

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
        logger.info("Closing remote runtime adapter, host={}, port={}", host, port);

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

    // Public methods.

    public void setFaultListener(Consumer<RuntimeFaultNotification> listener) {
        this.faultListener = listener;
    }

    // Internal methods.

    private void connectWebSocket(URI wsUri) {
        logger.info("Connecting to remote sidecar WebSocket, wsUri={}", wsUri);
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
                                            "Remote WebSocket session established, host={},"
                                                    + " port={}, sessionId={}",
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

                                                                // Sidecar may send multiple
                                                                // newline-separated JSONL
                                                                // messages in one WebSocket
                                                                // frame. Split them so the
                                                                // frontend can parse each line.
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
                                                                // WS close during detach is
                                                                // expected.
                                                                if (status
                                                                        == RuntimeStatus.DETACHED) {
                                                                    logger.debug(
                                                                            "Remote WebSocket error"
                                                                                + " during detach,"
                                                                                + " errorMessage={}",
                                                                            err.getMessage());
                                                                    return;
                                                                }
                                                                logger.warn(
                                                                        "Remote WebSocket receive"
                                                                            + " error,"
                                                                            + " errorMessage={}",
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
                                                                            "Remote WebSocket"
                                                                                + " completed"
                                                                                + " during detach");
                                                                    return;
                                                                }
                                                                logger.warn(
                                                                        "Remote WebSocket receive"
                                                                            + " stream completed");
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
                                                                            logger.debug(
                                                                                    "Sending remote"
                                                                                        + " WebSocket"
                                                                                        + " message,"
                                                                                        + " payload={}",
                                                                                    msg))
                                                            .map(session::textMessage));

                                    connectedLatch.countDown();
                                    return Mono.when(receive, send);
                                })
                        .subscribe(
                                unused ->
                                        logger.info(
                                                "Remote WebSocket connection completed normally"),
                                err -> {
                                    logger.error(
                                            "Remote WebSocket connection failed, errorMessage={}",
                                            err.getMessage(),
                                            err);
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
        logger.info("Connected remote sidecar WebSocket, wsUri={}", wsUri);
    }

    /**
     * Processes one received message line by intercepting control messages or forwarding stdout.
     */
    private void processReceivedLine(String line) {
        if (isControlMessage(line)) {
            handleControlMessage(line);
            return;
        }
        logger.debug("Received remote WebSocket message, line={}", line);
        stdoutSink.tryEmitNext(line);
    }

    /**
     * Checks whether a message is a Sidecar control message.
     */
    private boolean isControlMessage(String text) {
        return text.contains("\"type\":")
                && (text.contains("\"session_meta\"")
                        || text.contains("\"buffer_truncated\"")
                        || text.contains("\"process_exited\""));
    }

    /**
     * Handles Sidecar control messages without forwarding them to stdoutSink, except process_exited.
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
                        "Received remote WebSocket session metadata, sidecarSessionId={}, state={}",
                        sidecarSessionId,
                        node.has("state") ? node.get("state").asText() : "unknown");
                return;
            }

            if ("buffer_truncated".equals(type)) {
                long dropped = node.has("droppedBytes") ? node.get("droppedBytes").asLong() : 0;
                logger.warn("Remote WebSocket buffer truncated, droppedBytes={}", dropped);
                return;
            }

            if ("process_exited".equals(type)) {
                int code = node.has("code") ? node.get("code").asInt(-1) : -1;
                String signal = node.has("signal") ? node.get("signal").asText(null) : null;
                logger.info("Remote process exited, code={}, signal={}", code, signal);
                // Forward to the frontend so it can observe CLI process exit.
                stdoutSink.tryEmitNext(text);
                return;
            }
        } catch (Exception e) {
            logger.debug(
                    "Failed to parse control message, forwarding as stdout, errorMessage={}",
                    e.getMessage());
        }
        // Forward messages that cannot be parsed as control messages as regular stdout.
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
                                                                "Remote WebSocket ping failed,"
                                                                        + " errorMessage={}",
                                                                err.getMessage(),
                                                                err));
                            } catch (Exception e) {
                                logger.warn(
                                        "Remote WebSocket ping error, errorMessage={}",
                                        e.getMessage(),
                                        e);
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
                logger.warn("Failed to notify fault listener, errorMessage={}", e.getMessage(), e);
            }
        }
    }

    // Test-only getters.

    URI getSidecarWsUri() {
        return sidecarWsUri;
    }
}
