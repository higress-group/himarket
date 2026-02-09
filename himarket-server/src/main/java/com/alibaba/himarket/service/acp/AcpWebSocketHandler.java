package com.alibaba.himarket.service.acp;

import com.alibaba.himarket.config.AcpProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
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

@Component
public class AcpWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(AcpWebSocketHandler.class);

    private final AcpProperties properties;
    private final Map<String, AcpProcess> processMap = new ConcurrentHashMap<>();
    private final Map<String, Disposable> subscriptionMap = new ConcurrentHashMap<>();

    public AcpWebSocketHandler(AcpProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            logger.error("No userId in session attributes, closing connection");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Build per-user working directory
        String cwd = buildWorkspacePath(userId);
        logger.info("WebSocket connected: id={}, userId={}, cwd={}", session.getId(), userId, cwd);

        // Start qodercli process
        AcpProcess acpProcess =
                new AcpProcess(properties.getCliCommand(), List.of(properties.getCliArgs()), cwd);

        try {
            acpProcess.start();
        } catch (Exception e) {
            logger.error("Failed to start qodercli for user {}", userId, e);
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        processMap.put(session.getId(), acpProcess);

        // Subscribe to stdout: pipe qodercli output → WebSocket
        Disposable subscription =
                acpProcess
                        .stdout()
                        .subscribe(
                                line -> {
                                    try {
                                        if (session.isOpen()) {
                                            synchronized (session) {
                                                session.sendMessage(new TextMessage(line));
                                            }
                                        }
                                    } catch (IOException e) {
                                        logger.error("Error sending message to WebSocket", e);
                                    }
                                },
                                error ->
                                        logger.error(
                                                "Stdout stream error for session {}",
                                                session.getId(),
                                                error),
                                () -> {
                                    logger.info(
                                            "Stdout stream completed for session {}",
                                            session.getId());
                                    try {
                                        if (session.isOpen()) {
                                            session.close(CloseStatus.NORMAL);
                                        }
                                    } catch (IOException e) {
                                        logger.debug(
                                                "Error closing WebSocket after stdout completion",
                                                e);
                                    }
                                });

        subscriptionMap.put(session.getId(), subscription);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        AcpProcess acpProcess = processMap.get(session.getId());
        if (acpProcess == null) {
            logger.warn("No ACP process for session {}", session.getId());
            return;
        }

        String payload = message.getPayload();
        if (payload.isBlank()) {
            logger.trace("Ignoring blank message from session {}", session.getId());
            return;
        }

        logger.debug(
                "Inbound [{}]: {}",
                session.getId(),
                payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);

        try {
            acpProcess.send(payload);
        } catch (IOException e) {
            logger.error("Error writing to qodercli stdin for session {}", session.getId(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
            throws Exception {
        logger.info("WebSocket closed: id={}, status={}", session.getId(), status);
        cleanup(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception)
            throws Exception {
        logger.error("WebSocket transport error for session {}", session.getId(), exception);
        cleanup(session.getId());
    }

    private void cleanup(String sessionId) {
        Disposable subscription = subscriptionMap.remove(sessionId);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }

        AcpProcess acpProcess = processMap.remove(sessionId);
        if (acpProcess != null) {
            acpProcess.close();
        }
    }

    private String buildWorkspacePath(String userId) {
        // Sanitize userId to prevent path traversal
        String sanitized = userId.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path workspacePath = Paths.get(properties.getWorkspaceRoot(), sanitized);

        try {
            Files.createDirectories(workspacePath);
        } catch (IOException e) {
            logger.error("Failed to create workspace directory: {}", workspacePath, e);
        }

        return workspacePath.toAbsolutePath().toString();
    }
}
