package com.alibaba.himarket.service.terminal;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.service.acp.terminal.LocalTerminalBackend;
import com.alibaba.himarket.service.acp.terminal.RemoteTerminalBackend;
import com.alibaba.himarket.service.acp.terminal.TerminalBackend;
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
 * Terminal WebSocket handler。
 * 根据 runtime 参数选择终端后端：
 * - local：本地 PTY（pty4j）
 * - remote/k8s/shared-k8s：通过 WebSocket 连接 Sidecar 的 /terminal 端点（node-pty）
 */
@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(TerminalWebSocketHandler.class);

    private final AcpProperties acpProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, TerminalBackend> backendMap = new ConcurrentHashMap<>();
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

        String runtimeParam = (String) session.getAttributes().get("runtime");
        boolean isRemote =
                "k8s".equalsIgnoreCase(runtimeParam)
                        || "shared-k8s".equalsIgnoreCase(runtimeParam)
                        || "shared_k8s".equalsIgnoreCase(runtimeParam)
                        || "remote".equalsIgnoreCase(runtimeParam);

        logger.info(
                "Terminal WebSocket connected: id={}, userId={}, runtime={}",
                session.getId(),
                userId,
                runtimeParam);

        TerminalBackend backend;
        if (isRemote && acpProperties.getRemote().isConfigured()) {
            // 远程终端模式：通过 WebSocket 连接 Sidecar 的 /terminal 端点
            String host = acpProperties.getRemote().getHost();
            int port = acpProperties.getRemote().getPort();
            String cwd = "/workspace/" + userId;

            logger.info("Creating RemoteTerminalBackend: host={}:{}, cwd={}", host, port, cwd);
            backend = new RemoteTerminalBackend(host, port, cwd);

            try {
                backend.start(80, 24);
            } catch (Exception e) {
                logger.warn(
                        "Remote terminal not available, falling back to local: {}", e.getMessage());
                String localCwd = buildWorkspacePath(userId);
                backend = new LocalTerminalBackend(localCwd);
                backend.start(80, 24);
            }
        } else {
            // 本地模式
            String cwd = buildWorkspacePath(userId);
            backend = new LocalTerminalBackend(cwd);
            logger.info("Created LocalTerminalBackend for user {}, cwd={}", userId, cwd);

            try {
                backend.start(80, 24);
            } catch (Exception e) {
                logger.error("Failed to start terminal for user {}", userId, e);
                session.close(CloseStatus.SERVER_ERROR);
                return;
            }
        }

        backendMap.put(session.getId(), backend);

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
                                        logger.error("Error sending terminal output", e);
                                    }
                                },
                                error ->
                                        logger.error(
                                                "Terminal output error for session {}",
                                                session.getId(),
                                                error),
                                () -> {
                                    logger.info("Terminal exited for session {}", session.getId());
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
                                        logger.debug("Error sending exit message", e);
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
        logger.info("Terminal closed: id={}, status={}", session.getId(), status);
        cleanup(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.error("Terminal transport error for session {}", session.getId(), exception);
        cleanup(session.getId());
    }

    private void cleanup(String sessionId) {
        Disposable subscription = subscriptionMap.remove(sessionId);
        if (subscription != null && !subscription.isDisposed()) subscription.dispose();
        TerminalBackend backend = backendMap.remove(sessionId);
        if (backend != null) backend.close();
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
