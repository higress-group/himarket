package com.alibaba.himarket.service.terminal;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.service.acp.runtime.K8sConfigService;
import com.alibaba.himarket.service.acp.runtime.PodEntry;
import com.alibaba.himarket.service.acp.runtime.PodReuseManager;
import com.alibaba.himarket.service.acp.terminal.K8sTerminalBackend;
import com.alibaba.himarket.service.acp.terminal.LocalTerminalBackend;
import com.alibaba.himarket.service.acp.terminal.TerminalBackend;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
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
 * Each WebSocket connection spawns a terminal backend (local PTY or K8s Pod shell)
 * based on the runtime parameter in session attributes.
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
    private final PodReuseManager podReuseManager;
    private final K8sConfigService k8sConfigService;
    private final Map<String, TerminalBackend> backendMap = new ConcurrentHashMap<>();
    private final Map<String, Disposable> subscriptionMap = new ConcurrentHashMap<>();

    public TerminalWebSocketHandler(
            AcpProperties acpProperties,
            ObjectMapper objectMapper,
            PodReuseManager podReuseManager,
            K8sConfigService k8sConfigService) {
        this.acpProperties = acpProperties;
        this.objectMapper = objectMapper;
        this.podReuseManager = podReuseManager;
        this.k8sConfigService = k8sConfigService;
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
        boolean isK8s = "k8s".equalsIgnoreCase(runtimeParam);

        logger.info(
                "Terminal WebSocket connected: id={}, userId={}, runtime={}",
                session.getId(),
                userId,
                runtimeParam);

        TerminalBackend backend;
        if (isK8s) {
            // 获取默认 K8s client
            KubernetesClient client = k8sConfigService.getDefaultClient();

            // K8s 模式：从 PodReuseManager 获取用户的健康 Pod 信息，exec 失败时重试一次
            backend = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                PodEntry podEntry = podReuseManager.getHealthyPodEntry(userId, client);
                if (podEntry == null) {
                    logger.warn("Pod not found for user {}, closing terminal connection", userId);
                    String exitJson =
                            objectMapper.writeValueAsString(Map.of("type", "exit", "code", -1));
                    synchronized (session) {
                        session.sendMessage(new TextMessage(exitJson));
                    }
                    session.close(CloseStatus.SERVER_ERROR);
                    return;
                }

                K8sTerminalBackend k8sBackend =
                        new K8sTerminalBackend(
                                client,
                                podEntry.getPodName(),
                                podReuseManager.getNamespace(),
                                "sandbox");
                logger.info(
                        "Created K8sTerminalBackend for user {}, pod={} (attempt={})",
                        userId,
                        podEntry.getPodName(),
                        attempt);

                try {
                    k8sBackend.start(80, 24);
                    backend = k8sBackend;
                    break; // 成功，跳出重试循环
                } catch (Exception e) {
                    logger.warn(
                            "Terminal exec failed for user {}, pod={}, evicting cache and retrying",
                            userId,
                            podEntry.getPodName());
                    k8sBackend.close();
                    // 清除缓存中的旧 pod，下次 getHealthyPodEntry 会通过 K8s API 重新查找
                    podReuseManager.evictPod(userId);
                    if (attempt == 1) {
                        // 第二次也失败了，放弃
                        logger.error("Failed to start terminal for user {} after retry", userId, e);
                        session.close(CloseStatus.SERVER_ERROR);
                        return;
                    }
                }
            }
        } else {
            // 本地模式：保持现有行为
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

        // Subscribe to terminal output → send to WebSocket as JSON
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
                                                TerminalBackend tb =
                                                        backendMap.get(session.getId());
                                                if (tb != null && !tb.isAlive()) {
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
        TerminalBackend backend = backendMap.get(session.getId());
        if (backend == null) {
            logger.warn("No terminal backend for session {}", session.getId());
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
                        backend.write(data);
                    }
                }
                case "resize" -> {
                    int cols = root.has("cols") ? root.get("cols").asInt(80) : 80;
                    int rows = root.has("rows") ? root.get("rows").asInt(24) : 24;
                    backend.resize(cols, rows);
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

        TerminalBackend backend = backendMap.remove(sessionId);
        if (backend != null) {
            backend.close();
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
