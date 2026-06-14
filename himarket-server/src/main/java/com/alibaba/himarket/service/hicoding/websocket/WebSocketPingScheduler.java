package com.alibaba.himarket.service.hicoding.websocket;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket protocol-level ping scheduler.
 *
 * <p>Manages a shared ScheduledExecutorService and schedules periodic pings for each
 * WebSocketSession. Shared as a singleton bean by HiCodingWebSocketHandler and
 * TerminalWebSocketHandler.
 */
@Component
public class WebSocketPingScheduler {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketPingScheduler.class);

    static final long PING_INTERVAL_SECONDS = 30;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(
                    2,
                    r -> {
                        Thread t = new Thread(r, "ws-ping-scheduler");
                        t.setDaemon(true);
                        return t;
                    });

    private final ConcurrentHashMap<String, ScheduledFuture<?>> pingFutures =
            new ConcurrentHashMap<>();

    /**
     * Starts the ping timer for the specified session.
     *
     * <p>Sends a WebSocket protocol-level PingMessage every {@value PING_INTERVAL_SECONDS}
     * seconds. Repeated calls for the same sessionId stop the previous timer before registering a
     * new one.
     */
    public void startPing(WebSocketSession session) {
        String sessionId = session.getId();

        // Stop any previous timer before registering a new one.
        stopPing(sessionId);

        ScheduledFuture<?> future =
                scheduler.scheduleAtFixedRate(
                        () -> {
                            try {
                                if (!session.isOpen()) {
                                    return;
                                }
                                session.sendMessage(new PingMessage());
                            } catch (Exception e) {
                                logger.warn(
                                        "Failed to send WebSocket ping, sessionId={},"
                                                + " errorMessage={}",
                                        sessionId,
                                        e.getMessage(),
                                        e);
                            }
                        },
                        PING_INTERVAL_SECONDS,
                        PING_INTERVAL_SECONDS,
                        TimeUnit.SECONDS);

        pingFutures.put(sessionId, future);
        logger.info("Started WebSocket ping scheduler, sessionId={}", sessionId);
    }

    /**
     * Stops the ping timer for the specified session.
     */
    public void stopPing(String sessionId) {
        ScheduledFuture<?> future = pingFutures.remove(sessionId);
        if (future != null) {
            future.cancel(false);
            logger.info("Stopped WebSocket ping scheduler, sessionId={}", sessionId);
        }
    }
}
