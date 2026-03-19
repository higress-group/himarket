package com.alibaba.himarket.service.hicoding.terminal;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * 远程终端后端。
 * 通过 WebSocket 连接 Sidecar 的 /terminal 端点，
 * Sidecar 端使用 node-pty 提供交互式 PTY shell。
 * 不依赖 K8s API，适用于任意可达的 Sidecar 服务。
 */
public class RemoteTerminalBackend implements TerminalBackend {

    private static final Logger logger = LoggerFactory.getLogger(RemoteTerminalBackend.class);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long MAX_BACKOFF_MS = 30_000;

    private final String host;
    private final int port;
    private final String cwd;

    private final Sinks.Many<byte[]> outputSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Sinks.Many<String> sendSink = Sinks.many().unicast().onBackpressureBuffer();
    private final AtomicReference<org.springframework.web.reactive.socket.WebSocketSession>
            wsSessionRef = new AtomicReference<>();
    private Disposable wsConnection;
    private volatile boolean closed = false;

    // 心跳保活
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(
                    1,
                    r -> {
                        Thread t = new Thread(r, "remote-terminal-scheduler");
                        t.setDaemon(true);
                        return t;
                    });
    private ScheduledFuture<?> pingFuture;

    // 断连重连
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private volatile boolean reconnecting = false;
    private volatile int lastCols;
    private volatile int lastRows;

    public RemoteTerminalBackend(String host, int port, String cwd) {
        this.host = host;
        this.port = port;
        this.cwd = cwd;
    }

    @Override
    public void start(int cols, int rows) throws IOException {
        this.lastCols = cols;
        this.lastRows = rows;
        doConnect(cols, rows, true);
        logger.info("[RemoteTerminal] Connected to sidecar terminal");
        startHeartbeat();
    }

    private void doConnect(int cols, int rows, boolean blocking) throws IOException {
        String uriStr =
                String.format(
                        "ws://%s:%d/terminal?cols=%d&rows=%d&cwd=%s",
                        host,
                        port,
                        cols,
                        rows,
                        java.net.URLEncoder.encode(cwd, StandardCharsets.UTF_8));
        URI wsUri = URI.create(uriStr);

        logger.info("[RemoteTerminal] Connecting to {}", wsUri);

        ReactorNettyWebSocketClient wsClient = new ReactorNettyWebSocketClient();
        CountDownLatch connectedLatch = blocking ? new CountDownLatch(1) : null;

        wsConnection =
                wsClient.execute(
                                wsUri,
                                session -> {
                                    wsSessionRef.set(session);
                                    if (connectedLatch != null) {
                                        connectedLatch.countDown();
                                    }

                                    Flux<WebSocketMessage> outgoing =
                                            sendSink.asFlux().map(session::textMessage);

                                    return session.send(outgoing)
                                            .and(
                                                    session.receive()
                                                            .doOnNext(
                                                                    msg -> {
                                                                        var buf = msg.getPayload();
                                                                        byte[] data =
                                                                                new byte
                                                                                        [buf
                                                                                                .readableByteCount()];
                                                                        buf.read(data);
                                                                        outputSink.tryEmitNext(
                                                                                data);
                                                                    })
                                                            .doOnComplete(
                                                                    () -> {
                                                                        if (!closed) {
                                                                            logger.info(
                                                                                    "[RemoteTerminal]"
                                                                                        + " Connection"
                                                                                        + " closed"
                                                                                        + " by sidecar");
                                                                            scheduleReconnect();
                                                                        }
                                                                    })
                                                            .doOnError(
                                                                    err -> {
                                                                        if (!closed) {
                                                                            logger.error(
                                                                                    "[RemoteTerminal]"
                                                                                        + " Connection"
                                                                                        + " error",
                                                                                    err);
                                                                            scheduleReconnect();
                                                                        }
                                                                    })
                                                            .then());
                                })
                        .subscribe();

        if (blocking) {
            try {
                if (!connectedLatch.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("连接远程终端超时: " + wsUri);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("连接远程终端被中断", e);
            }
        }
    }

    private static final String HEARTBEAT_MSG = "{\"type\":\"heartbeat\"}";

    private void startHeartbeat() {
        stopHeartbeat();
        try {
            pingFuture =
                    scheduler.scheduleAtFixedRate(
                            () -> {
                                try {
                                    var session = wsSessionRef.get();
                                    if (session == null || !session.isOpen()) {
                                        return;
                                    }
                                    session.send(Mono.just(session.textMessage(HEARTBEAT_MSG)))
                                            .subscribe(
                                                    unused -> {},
                                                    err ->
                                                            logger.warn(
                                                                    "[RemoteTerminal] Heartbeat"
                                                                            + " failed: {}",
                                                                    err.getMessage()));
                                } catch (Exception e) {
                                    logger.warn(
                                            "[RemoteTerminal] Heartbeat error: {}", e.getMessage());
                                }
                            },
                            HEARTBEAT_INTERVAL_SECONDS,
                            HEARTBEAT_INTERVAL_SECONDS,
                            TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            logger.debug("[RemoteTerminal] Scheduler already shutdown, skip heartbeat");
        }
    }

    private void stopHeartbeat() {
        if (pingFuture != null) {
            pingFuture.cancel(false);
            pingFuture = null;
        }
    }

    private void scheduleReconnect() {
        if (closed || reconnecting) return;
        reconnecting = true;
        stopHeartbeat();

        int attempt = reconnectAttempts.get();
        if (attempt >= MAX_RECONNECT_ATTEMPTS) {
            logger.warn(
                    "[RemoteTerminal] Max reconnect attempts ({}) reached, giving up",
                    MAX_RECONNECT_ATTEMPTS);
            reconnecting = false;
            outputSink.tryEmitComplete();
            return;
        }

        long delayMs = Math.min(1000L * (1L << attempt), MAX_BACKOFF_MS);
        logger.info(
                "[RemoteTerminal] Scheduling reconnect attempt {} in {}ms", attempt + 1, delayMs);

        try {
            scheduler.schedule(this::doReconnect, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            logger.debug("[RemoteTerminal] Scheduler shutdown, cannot reconnect");
            reconnecting = false;
            outputSink.tryEmitComplete();
        }
    }

    private void doReconnect() {
        if (closed) {
            reconnecting = false;
            return;
        }

        // 清理旧连接
        if (wsConnection != null && !wsConnection.isDisposed()) {
            wsConnection.dispose();
        }

        try {
            doConnect(lastCols, lastRows, true);
            reconnectAttempts.set(0);
            reconnecting = false;
            startHeartbeat();
            logger.info("[RemoteTerminal] Reconnected successfully");
        } catch (IOException e) {
            logger.warn("[RemoteTerminal] Reconnect failed: {}", e.getMessage());
            reconnectAttempts.incrementAndGet();
            reconnecting = false;
            scheduleReconnect();
        }
    }

    @Override
    public void write(String data) throws IOException {
        if (closed) throw new IOException("Remote terminal is closed");
        sendSink.tryEmitNext(data);
    }

    @Override
    public void resize(int cols, int rows) {
        if (closed) return;
        this.lastCols = cols;
        this.lastRows = rows;
        String resizeMsg =
                String.format("{\"type\":\"resize\",\"cols\":%d,\"rows\":%d}", cols, rows);
        sendSink.tryEmitNext(resizeMsg);
    }

    @Override
    public Flux<byte[]> output() {
        return outputSink.asFlux();
    }

    @Override
    public boolean isAlive() {
        return !closed && wsSessionRef.get() != null;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        logger.info("[RemoteTerminal] Closing");
        stopHeartbeat();
        scheduler.shutdownNow();
        outputSink.tryEmitComplete();
        sendSink.tryEmitComplete();
        if (wsConnection != null && !wsConnection.isDisposed()) {
            wsConnection.dispose();
        }
    }
}
