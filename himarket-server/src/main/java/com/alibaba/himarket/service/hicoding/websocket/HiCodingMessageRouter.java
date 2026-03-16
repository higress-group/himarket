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
 * 前端 ↔ CLI 双向消息路由器。
 *
 * <p>从 HiCodingWebSocketHandler 中提取消息转发逻辑，职责包括：
 *
 * <ul>
 *   <li>订阅 CLI stdout 流并转发到前端 WebSocket
 *   <li>将前端消息转发到 CLI 进程
 *   <li>回放初始化期间缓存的待转发消息
 * </ul>
 */
@Component
public class HiCodingMessageRouter {

    private static final Logger logger = LoggerFactory.getLogger(HiCodingMessageRouter.class);

    /**
     * 订阅 CLI stdout 并转发到前端 WebSocket。
     *
     * <p>从 HiCodingWebSocketHandler.initSandboxAsync() 中提取的 stdout 订阅逻辑。 当 stdout
     * 流完成时，会自动关闭对应的 WebSocket session。
     *
     * @param adapter CLI 运行时适配器
     * @param session 前端 WebSocket session
     * @return Disposable 订阅句柄（用于取消订阅）
     */
    public Disposable subscribeAndForward(RuntimeAdapter adapter, WebSocketSession session) {
        return adapter.stdout()
                .subscribe(
                        line -> sendToFrontend(session, line),
                        error ->
                                logger.error(
                                        "Stdout stream error for session {}",
                                        session.getId(),
                                        error),
                        () -> {
                            logger.info("Stdout stream completed for session {}", session.getId());
                            try {
                                if (session.isOpen()) {
                                    session.close(CloseStatus.NORMAL);
                                }
                            } catch (IOException e) {
                                logger.debug("Error closing WebSocket after stdout completion", e);
                            }
                        });
    }

    /**
     * 将前端消息转发到 CLI 进程。
     *
     * @param adapter CLI 运行时适配器
     * @param message 前端发送的消息内容
     * @throws IOException 发送失败时抛出
     */
    public void forwardToCliAgent(RuntimeAdapter adapter, String message) throws IOException {
        adapter.send(message);
    }

    /**
     * 回放初始化期间缓存的待转发消息。
     *
     * <p>从 HiCodingWebSocketHandler.replayPendingMessages() 迁移。 注意：本方法直接回放消息，不做消息变换。
     * 如需对消息进行重写（如 rewriteSessionNewCwd），由调用方在入队前或调用前处理。
     *
     * @param session 前端 WebSocket session（用于日志记录）
     * @param adapter CLI 运行时适配器
     * @param pending 待回放的消息队列
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
                logger.error("Error replaying queued message for session {}", session.getId(), e);
            }
        }
    }

    /**
     * 将消息发送到前端 WebSocket session。
     *
     * <p>使用 synchronized 保证同一 session 的消息发送顺序。
     *
     * @param session 前端 WebSocket session
     * @param message 要发送的消息内容
     */
    private void sendToFrontend(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        } catch (IOException e) {
            logger.error("Error sending message to WebSocket", e);
        }
    }
}
