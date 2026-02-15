package com.alibaba.himarket.service.acp;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
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
    private static final String SESSION_NEW_METHOD = "session/new";

    private final AcpProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, AcpProcess> processMap = new ConcurrentHashMap<>();
    private final Map<String, Disposable> subscriptionMap = new ConcurrentHashMap<>();
    private final Map<String, String> cwdMap = new ConcurrentHashMap<>();

    public AcpWebSocketHandler(AcpProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
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

        // Resolve CLI provider: prefer query param, fallback to default
        String providerKey = (String) session.getAttributes().get("provider");
        if (providerKey == null || providerKey.isBlank()) {
            providerKey = properties.getDefaultProvider();
        }
        CliProviderConfig providerConfig = properties.getProvider(providerKey);
        if (providerConfig == null) {
            logger.error("Unknown CLI provider '{}', closing connection", providerKey);
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        logger.info(
                "Using CLI provider '{}' (command={})", providerKey, providerConfig.getCommand());

        // Build process environment: start from provider-level env config.
        // If isolateHome is enabled, override HOME so the CLI stores credentials
        // under the per-user workspace (e.g. ~/.himarket/workspaces/{userId}/).
        Map<String, String> processEnv = new HashMap<>(providerConfig.getEnv());
        if (providerConfig.isIsolateHome()) {
            processEnv.put("HOME", cwd);
            logger.info("HOME isolated for provider '{}': {}", providerKey, cwd);
        }

        // Start ACP CLI process
        AcpProcess acpProcess =
                new AcpProcess(
                        providerConfig.getCommand(),
                        List.of(providerConfig.getArgs()),
                        cwd,
                        processEnv);

        try {
            acpProcess.start();
        } catch (Exception e) {
            logger.error("Failed to start CLI provider '{}' for user {}", providerKey, userId, e);
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        processMap.put(session.getId(), acpProcess);
        cwdMap.put(session.getId(), cwd);

        // Subscribe to stdout: pipe ACP CLI output → WebSocket
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

        // Rewrite cwd in session/new requests to the absolute workspace path
        payload = rewriteSessionNewCwd(session.getId(), payload);

        try {
            acpProcess.send(payload);
        } catch (IOException e) {
            logger.error("Error writing to ACP CLI stdin for session {}", session.getId(), e);
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

        cwdMap.remove(sessionId);
    }

    /**
     * Intercept session/new requests and replace the cwd parameter with the
     * absolute workspace path so that the ACP CLI knows the real directory.
     */
    private String rewriteSessionNewCwd(String sessionId, String payload) {
        String cwd = cwdMap.get(sessionId);
        if (cwd == null) {
            return payload;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode methodNode = root.get("method");
            if (methodNode == null || !SESSION_NEW_METHOD.equals(methodNode.asText())) {
                return payload;
            }
            JsonNode params = root.get("params");
            if (params == null || !params.isObject()) {
                return payload;
            }
            ((ObjectNode) params).put("cwd", cwd);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            logger.debug("Failed to rewrite session/new cwd, forwarding original payload", e);
            return payload;
        }
    }

    private String buildWorkspacePath(String userId) {
        // Sanitize userId to prevent path traversal
        String sanitized = userId.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path workspacePath =
                Paths.get(properties.getWorkspaceRoot(), sanitized).toAbsolutePath().normalize();

        try {
            Files.createDirectories(workspacePath);
        } catch (IOException e) {
            logger.error("Failed to create workspace directory: {}", workspacePath, e);
        }

        return workspacePath.toString();
    }
}
