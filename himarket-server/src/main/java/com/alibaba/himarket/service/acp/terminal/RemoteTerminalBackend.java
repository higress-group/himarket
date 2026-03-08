package com.alibaba.himarket.service.acp.terminal;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 远程终端后端。
 * 通过 WebSocket 连接 Sidecar 的 /terminal 端点，
 * Sidecar 端使用 node-pty 提供交互式 PTY shell。
 * 不依赖 K8s API，适用于任意可达的 Sidecar 服务。
 */
public class RemoteTerminalBackend implements TerminalBackend {

    private static final Logger logger = LoggerFactory.getLogger(RemoteTerminalBackend.class);

    private final String host;
    private final int port;
    private final String cwd;

    private final Sinks.Many<byte[]> outputSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Sinks.Many<String> sendSink = Sinks.many().unicast().onBackpressureBuffer();
    private final AtomicReference<org.springframework.web.reactive.socket.WebSocketSession>
            wsSessionRef = new AtomicReference<>();
    private Disposable wsConnection;
    private volatile boolean closed = false;

    public RemoteTerminalBackend(String host, int port, String cwd) {
        this.host = host;
        this.port = port;
        this.cwd = cwd;
    }

    @Override
    public void start(int cols, int rows) throws IOException {
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
        CountDownLatch connectedLatch = new CountDownLatch(1);

        wsConnection =
                wsClient.execute(
                                wsUri,
                                session -> {
                                    wsSessionRef.set(session);
                                    connectedLatch.countDown();

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
                                                                            outputSink
                                                                                    .tryEmitComplete();
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
                                                                            outputSink
                                                                                    .tryEmitComplete();
                                                                        }
                                                                    })
                                                            .then());
                                })
                        .subscribe();

        try {
            if (!connectedLatch.await(10, TimeUnit.SECONDS)) {
                throw new IOException("连接远程终端超时: " + wsUri);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("连接远程终端被中断", e);
        }

        logger.info("[RemoteTerminal] Connected to sidecar terminal");
    }

    @Override
    public void write(String data) throws IOException {
        if (closed) throw new IOException("Remote terminal is closed");
        sendSink.tryEmitNext(data);
    }

    @Override
    public void resize(int cols, int rows) {
        if (closed) return;
        // 发送 JSON resize 控制消息
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
        outputSink.tryEmitComplete();
        sendSink.tryEmitComplete();
        if (wsConnection != null && !wsConnection.isDisposed()) {
            wsConnection.dispose();
        }
    }
}
