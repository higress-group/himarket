package com.alibaba.himarket.service.terminal;

import com.alibaba.himarket.config.AcpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * WebSocket handler that provides an interactive shell terminal.
 * Each WebSocket connection spawns a PTY shell process for the user.
 *
 * Protocol:
 *   Client → Server: { "type": "input", "data": "..." }
 *   Client → Server: { "type": "resize", "cols": N, "rows": N }
 *   Server → Client: { "type": "output", "data": "..." }
 *   Server → Client: { "type": "exit", "code": N }
 */
@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(TerminalWebSocketHandler.class);

    private final AcpProperties acpProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, TerminalProcess> processMap = new ConcurrentHashMap<>();
    private final Map<String, Disposable> subscriptionMap = new ConcurrentHashMap<>();

    public TerminalWebSocketHandler(AcpProperties acpProperties, ObjectMapper objectMapper) {
        this.acpProperties = acpProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            logger.error("No userId in session attributes, closing terminal connection");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String cwd = buildWorkspacePath(userId);
        logger.info(
                "Terminal WebSocket connected: id={}, userId={}, cwd={}",
                session.getId(),
                userId,
                cwd);

        TerminalProcess terminalProcess = new TerminalProcess(cwd);
        try {
            terminalProcess.start(80, 24);
        } catch (Exception e) {
            logger.error("Failed to start terminal for user {}", userId, e);
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        processMap.put(session.getId(), terminalProcess);

        // Subscribe to terminal output → send to WebSocket as JSON
        Disposable subscription =
                terminalProcess
                        .output()
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
                                                "Error sending terminal output to WebSocket", e);
                                    }
                                },
                                error ->
                                        logger.error(
                                                "Terminal output stream error for session {}",
                                                session.getId(),
                                                error),
                                () -> {
                                    logger.info(
                                            "Terminal process exited for session {}",
                                            session.getId());
                                    try {
                                        if (session.isOpen()) {
                                            int exitCode = 0;
                                            try {
                                                TerminalProcess tp =
                                                        processMap.get(session.getId());
                                                if (tp != null && !tp.isAlive()) {
                                                    // Process ended
                                                    exitCode = 0;
                                                }
                                            } catch (Exception ignored) {
                                            }
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
                                        logger.debug("Error sending exit message", e);
                                    }
                                });

        subscriptionMap.put(session.getId(), subscription);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        TerminalProcess terminalProcess = processMap.get(session.getId());
        if (terminalProcess == null) {
            logger.warn("No terminal process for session {}", session.getId());
            return;
        }

        String payload = message.getPayload();
        if (payload.isBlank()) return;

        try {
            JsonNode root = objectMapper.readTree(payload);
            String type = root.has("type") ? root.get("type").asText() : "";

            switch (type) {
                case "input" -> {
                    String data = root.has("data") ? root.get("data").asText() : "";
                    if (!data.isEmpty()) {
                        terminalProcess.write(data);
                    }
                }
                case "resize" -> {
                    int cols = root.has("cols") ? root.get("cols").asInt(80) : 80;
                    int rows = root.has("rows") ? root.get("rows").asInt(24) : 24;
                    terminalProcess.resize(cols, rows);
                }
                default -> logger.debug("Unknown terminal message type: {}", type);
            }
        } catch (Exception e) {
            logger.error("Error handling terminal message for session {}", session.getId(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
            throws Exception {
        logger.info("Terminal WebSocket closed: id={}, status={}", session.getId(), status);
        cleanup(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception)
            throws Exception {
        logger.error(
                "Terminal WebSocket transport error for session {}", session.getId(), exception);
        cleanup(session.getId());
    }

    private void cleanup(String sessionId) {
        Disposable subscription = subscriptionMap.remove(sessionId);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }

        TerminalProcess terminalProcess = processMap.remove(sessionId);
        if (terminalProcess != null) {
            terminalProcess.close();
        }
    }

    private String buildWorkspacePath(String userId) {
        String sanitized = userId.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path workspacePath =
                Paths.get(acpProperties.getWorkspaceRoot(), sanitized).toAbsolutePath().normalize();

        try {
            Files.createDirectories(workspacePath);
        } catch (IOException e) {
            logger.error("Failed to create workspace directory: {}", workspacePath, e);
        }

        return workspacePath.toString();
    }
}
