package com.alibaba.himarket.service.hicoding.terminal;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.service.hicoding.websocket.WebSocketPingScheduler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;

/**
 * Terminal WebSocket handler.
 *
 * <p>Provides terminal access by connecting to the remote Sidecar /terminal endpoint.
 */
@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(TerminalWebSocketHandler.class);

    private final AcpProperties acpProperties;
    private final ObjectMapper objectMapper;
    private final WebSocketPingScheduler pingScheduler;
    private final Map<String, TerminalBackend> backendMap = new ConcurrentHashMap<>();
    private final Map<String, Disposable> subscriptionMap = new ConcurrentHashMap<>();

    public TerminalWebSocketHandler(
            AcpProperties acpProperties,
            ObjectMapper objectMapper,
            WebSocketPingScheduler pingScheduler) {
        this.acpProperties = acpProperties;
        this.objectMapper = objectMapper;
        this.pingScheduler = pingScheduler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!acpProperties.isTerminalEnabled()) {
            logger.info("Terminal feature is disabled, closing connection, id={}", session.getId());
            session.close(CloseStatus.NORMAL);
            return;
        }

        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            logger.error("No userId in session attributes, closing terminal connection");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String runtimeParam = (String) session.getAttributes().get("runtime");

        logger.info(
                "Terminal WebSocket connected, id={}, userId={}, runtime={}",
                session.getId(),
                userId,
                runtimeParam);

        if (!acpProperties.getRemote().isConfigured()) {
            logger.error("Remote sandbox not configured, cannot create terminal");
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        String host = acpProperties.getRemote().getHost();
        int port = acpProperties.getRemote().getPort();
        String cwd = "/workspace/" + userId;

        logger.info("Creating remote terminal backend, host={}, port={}, cwd={}", host, port, cwd);
        TerminalBackend backend = new RemoteTerminalBackend(host, port, cwd);

        try {
            backend.start(80, 24);
        } catch (Exception e) {
            logger.error(
                    "Failed to start remote terminal, userId={}, errorMessage={}",
                    userId,
                    e.getMessage(),
                    e);
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        backendMap.put(session.getId(), backend);

        // Start protocol-level WebSocket ping to keep the frontend connection alive.
        pingScheduler.startPing(session);

        Disposable subscription =
                backend.output()
                        .subscribe(
                                data -> {
                                    try {
                                        if (session.isOpen()) {
                                            String encoded =
                                                    Base64.getEncoder().encodeToString(data);
                                            String json =
                                                    objectMapper.writeValueAsString(
                                                            Map.of(
                                                                    "type", "output", "data",
                                                                    encoded));
                                            synchronized (session) {
                                                session.sendMessage(new TextMessage(json));
                                            }
                                        }
                                    } catch (IOException e) {
                                        logger.error(
                                                "Failed to send terminal output, sessionId={},"
                                                        + " errorMessage={}",
                                                session.getId(),
                                                e.getMessage(),
                                                e);
                                    }
                                },
                                error ->
                                        logger.error(
                                                "Terminal output error, sessionId={},"
                                                        + " errorMessage={}",
                                                session.getId(),
                                                error.getMessage(),
                                                error),
                                () -> {
                                    logger.info("Terminal exited, sessionId={}", session.getId());
                                    try {
                                        if (session.isOpen()) {
                                            int exitCode = 0;
                                            String json =
                                                    objectMapper.writeValueAsString(
                                                            Map.of(
                                                                    "type", "exit", "code",
                                                                    exitCode));
                                            synchronized (session) {
                                                session.sendMessage(new TextMessage(json));
                                            }
                                        }
                                    } catch (IOException e) {
                                        logger.debug(
                                                "Failed to send terminal exit message,"
                                                        + " sessionId={}, errorMessage={}",
                                                session.getId(),
                                                e.getMessage(),
                                                e);
                                    }
                                });

        subscriptionMap.put(session.getId(), subscription);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        TerminalBackend backend = backendMap.get(session.getId());
        if (backend == null) return;

        String payload = message.getPayload();
        if (payload.isBlank()) return;

        JsonNode root = objectMapper.readTree(payload);
        String type = root.has("type") ? root.get("type").asText() : "";

        switch (type) {
            case "input" -> {
                String data = root.has("data") ? root.get("data").asText() : "";
                if (!data.isEmpty()) backend.write(data);
            }
            case "resize" -> {
                int cols = root.has("cols") ? root.get("cols").asInt(80) : 80;
                int rows = root.has("rows") ? root.get("rows").asInt(24) : 24;
                backend.resize(cols, rows);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.info("Terminal closed, sessionId={}, status={}", session.getId(), status);
        cleanup(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error(
                "Terminal transport error, sessionId={}, errorMessage={}",
                session.getId(),
                exception.getMessage(),
                exception);
        cleanup(session.getId());
    }

    private void cleanup(String sessionId) {
        pingScheduler.stopPing(sessionId);
        Disposable subscription = subscriptionMap.remove(sessionId);
        if (subscription != null && !subscription.isDisposed()) subscription.dispose();
        TerminalBackend backend = backendMap.remove(sessionId);
        if (backend != null) backend.close();
    }
}
