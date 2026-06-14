package com.alibaba.himarket.service.hicoding.websocket;

import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import java.io.IOException;
import java.util.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.Disposable;

/**
 * Bidirectional message router between frontend and CLI.
 *
 * <p>Extracts message forwarding logic from HiCodingWebSocketHandler. Responsibilities include:
 *
 * <ul>
 *   <li>subscribing to the CLI stdout stream and forwarding it to the frontend WebSocket
 *   <li>forwarding frontend messages to the CLI process
 *   <li>replaying messages queued during initialization
 * </ul>
 */
@Component
public class HiCodingMessageRouter {

    private static final Logger logger = LoggerFactory.getLogger(HiCodingMessageRouter.class);

    /**
     * Subscribes to CLI stdout and forwards it to the frontend WebSocket.
     *
     * <p>Extracted from HiCodingWebSocketHandler.initSandboxAsync(). When the stdout stream
     * completes, the corresponding WebSocket session is closed automatically.
     *
     * @param adapter CLI runtime adapter
     * @param session frontend WebSocket session
     * @return Disposable subscription handle used for cancellation
     */
    public Disposable subscribeAndForward(RuntimeAdapter adapter, WebSocketSession session) {
        return adapter.stdout()
                .subscribe(
                        line -> sendToFrontend(session, line),
                        error ->
                                logger.error(
                                        "Stdout stream error, sessionId={}, errorMessage={}",
                                        session.getId(),
                                        error.getMessage(),
                                        error),
                        () -> {
                            logger.info("Stdout stream completed, sessionId={}", session.getId());
                            try {
                                if (session.isOpen()) {
                                    session.close(CloseStatus.NORMAL);
                                }
                            } catch (IOException e) {
                                logger.debug(
                                        "Failed to close WebSocket after stdout completion,"
                                                + " sessionId={}, errorMessage={}",
                                        session.getId(),
                                        e.getMessage(),
                                        e);
                            }
                        });
    }

    /**
     * Forwards a frontend message to the CLI process.
     *
     * @param adapter CLI runtime adapter
     * @param message message content sent by the frontend
     * @throws IOException when sending fails
     */
    public void forwardToCliAgent(RuntimeAdapter adapter, String message) throws IOException {
        adapter.send(message);
    }

    /**
     * Replays messages queued during initialization.
     *
     * <p>Migrated from HiCodingWebSocketHandler.replayPendingMessages(). This method replays
     * messages directly without transformation. Callers should rewrite messages, such as applying
     * rewriteSessionNewCwd, before enqueueing or before calling this method.
     *
     * @param session frontend WebSocket session used for logging
     * @param adapter CLI runtime adapter
     * @param pending queued messages to replay
     */
    public void replayPendingMessages(
            WebSocketSession session, RuntimeAdapter adapter, Queue<String> pending) {
        if (pending == null) {
            return;
        }
        String queued;
        while ((queued = pending.poll()) != null) {
            try {
                adapter.send(queued);
            } catch (IOException e) {
                logger.error(
                        "Failed to replay queued message, sessionId={}, errorMessage={}",
                        session.getId(),
                        e.getMessage(),
                        e);
            }
        }
    }

    /**
     * Sends a message to the frontend WebSocket session.
     *
     * <p>Uses synchronized to preserve message ordering for the same session.
     *
     * @param session frontend WebSocket session
     * @param message message content to send
     */
    private void sendToFrontend(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        } catch (IOException e) {
            logger.error(
                    "Failed to send message to WebSocket, sessionId={}, errorMessage={}",
                    session.getId(),
                    e.getMessage(),
                    e);
        }
    }
}
