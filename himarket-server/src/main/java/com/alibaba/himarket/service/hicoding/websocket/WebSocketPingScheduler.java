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
 * WebSocket 协议级 ping 调度器。
 *
 * <p>管理共享的 ScheduledExecutorService，为每个 WebSocketSession 调度周期性 ping。
 * 作为单例 Bean 供 HiCodingWebSocketHandler 和 TerminalWebSocketHandler 共享。
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
     * 为指定 session 启动 ping 定时器。
     *
     * <p>每 {@value PING_INTERVAL_SECONDS} 秒发送一个 WebSocket 协议级 PingMessage。
     * 对同一 sessionId 重复调用时，先停止旧的定时器再注册新的。
     */
    public void startPing(WebSocketSession session) {
        String sessionId = session.getId();

        // 重复调用时先停止旧的
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
                                        "[WS-Ping] Failed to send ping for session {}: {}",
                                        sessionId,
                                        e.getMessage());
                            }
                        },
                        PING_INTERVAL_SECONDS,
                        PING_INTERVAL_SECONDS,
                        TimeUnit.SECONDS);

        pingFutures.put(sessionId, future);
        logger.info("[WS-Ping] Started ping scheduler for session {}", sessionId);
    }

    /**
     * 停止指定 session 的 ping 定时器。
     */
    public void stopPing(String sessionId) {
        ScheduledFuture<?> future = pingFutures.remove(sessionId);
        if (future != null) {
            future.cancel(false);
            logger.info("[WS-Ping] Stopped ping scheduler for session {}", sessionId);
        }
    }
}
