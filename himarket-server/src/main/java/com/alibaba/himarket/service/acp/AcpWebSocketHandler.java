package com.alibaba.himarket.service.acp;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.acp.runtime.K8sConfigService;
import com.alibaba.himarket.service.acp.runtime.K8sRuntimeAdapter;
import com.alibaba.himarket.service.acp.runtime.PodInfo;
import com.alibaba.himarket.service.acp.runtime.PodReuseManager;
import com.alibaba.himarket.service.acp.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.acp.runtime.RuntimeConfig;
import com.alibaba.himarket.service.acp.runtime.RuntimeFactory;
import com.alibaba.himarket.service.acp.runtime.RuntimeType;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final RuntimeFactory runtimeFactory;
    private final K8sConfigService k8sConfigService;
    private final PodReuseManager podReuseManager;
    private final Map<String, RuntimeAdapter> runtimeMap = new ConcurrentHashMap<>();
    private final Map<String, Disposable> subscriptionMap = new ConcurrentHashMap<>();
    private final Map<String, String> cwdMap = new ConcurrentHashMap<>();
    private final Map<String, String> userIdMap = new ConcurrentHashMap<>();
    private final Map<String, String> sandboxModeMap = new ConcurrentHashMap<>();

    /** K8s Pod 异步初始化线程池 */
    private final ExecutorService podInitExecutor =
            Executors.newCachedThreadPool(
                    r -> {
                        Thread t = new Thread(r, "pod-init");
                        t.setDaemon(true);
                        return t;
                    });

    /** 标记正在异步初始化 Pod 的 session，期间缓存前端消息 */
    private final Map<String, java.util.Queue<String>> pendingMessageMap =
            new ConcurrentHashMap<>();

    public AcpWebSocketHandler(
            AcpProperties properties,
            ObjectMapper objectMapper,
            RuntimeFactory runtimeFactory,
            K8sConfigService k8sConfigService,
            PodReuseManager podReuseManager) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.runtimeFactory = runtimeFactory;
        this.k8sConfigService = k8sConfigService;
        this.podReuseManager = podReuseManager;
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

        // Resolve runtime type from session attributes (set by AcpHandshakeInterceptor)
        String runtimeParam = (String) session.getAttributes().get("runtime");
        RuntimeType runtimeType = resolveRuntimeType(runtimeParam);

        logger.info(
                "WebSocket connected: id={}, userId={}, cwd={}, runtime={}",
                session.getId(),
                userId,
                cwd,
                runtimeType);

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

        // Build RuntimeConfig from provider configuration
        RuntimeConfig config =
                buildRuntimeConfig(userId, providerKey, providerConfig, cwd, runtimeType);

        // Resolve sandboxMode from session attributes (set by AcpHandshakeInterceptor)
        String sandboxMode = (String) session.getAttributes().get("sandboxMode");
        // POC 阶段：K8s 运行时默认使用用户级沙箱
        boolean isUserScoped = "user".equals(sandboxMode) || runtimeType == RuntimeType.K8S;

        // Create runtime via factory, route based on sandboxMode
        RuntimeAdapter runtime;
        try {
            if (runtimeType == RuntimeType.K8S && isUserScoped) {
                // K8s 用户级沙箱：异步创建 Pod，先让 WebSocket 连接建立
                // 前端可以立即收到进度通知
                cwdMap.put(session.getId(), cwd);
                userIdMap.put(session.getId(), userId);
                sandboxModeMap.put(session.getId(), sandboxMode != null ? sandboxMode : "");
                pendingMessageMap.put(
                        session.getId(), new java.util.concurrent.ConcurrentLinkedQueue<>());

                final String fProviderKey = providerKey;
                final RuntimeConfig fConfig = config;
                podInitExecutor.submit(
                        () -> initK8sPodAsync(session, userId, fProviderKey, fConfig));
                return; // 不阻塞，直接返回让 WebSocket 连接完成
            } else {
                // 原有逻辑：本地运行时同步启动
                runtime = runtimeFactory.create(runtimeType, config);
                runtime.start(config);
            }
        } catch (Exception e) {
            logger.error("Failed to start runtime (type={}) for user {}", runtimeType, userId, e);
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        runtimeMap.put(session.getId(), runtime);
        cwdMap.put(session.getId(), cwd);
        userIdMap.put(session.getId(), userId);
        sandboxModeMap.put(session.getId(), sandboxMode != null ? sandboxMode : "");

        // Subscribe to stdout: pipe runtime output → WebSocket
        Disposable subscription =
                runtime.stdout()
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
        String payload = message.getPayload();
        if (payload.isBlank()) {
            logger.trace("Ignoring blank message from session {}", session.getId());
            return;
        }

        // 如果 Pod 还在异步初始化中，缓存消息
        java.util.Queue<String> pendingQueue = pendingMessageMap.get(session.getId());
        if (pendingQueue != null) {
            pendingQueue.add(payload);
            logger.debug("Pod initializing, queued message for session {}", session.getId());
            return;
        }

        RuntimeAdapter runtime = runtimeMap.get(session.getId());
        if (runtime == null) {
            logger.warn("No runtime for session {}", session.getId());
            return;
        }

        logger.debug(
                "Inbound [{}]: {}",
                session.getId(),
                payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);

        // Rewrite cwd in session/new requests to the absolute workspace path
        payload = rewriteSessionNewCwd(session.getId(), payload);

        try {
            runtime.send(payload);
        } catch (IOException e) {
            logger.error("Error writing to runtime stdin for session {}", session.getId(), e);
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
        pendingMessageMap.remove(sessionId);

        Disposable subscription = subscriptionMap.remove(sessionId);
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }

        RuntimeAdapter runtime = runtimeMap.remove(sessionId);
        if (runtime != null) {
            runtime.close(); // 复用模式下只断开 WebSocket，不删除 Pod
            // 释放 Pod 连接计数
            String userId = userIdMap.get(sessionId);
            String sandboxMode = sandboxModeMap.get(sessionId);
            if ("user".equals(sandboxMode) && userId != null) {
                podReuseManager.releasePod(userId);
            }
        }

        cwdMap.remove(sessionId);
        userIdMap.remove(sessionId);
        sandboxModeMap.remove(sessionId);
    }

    /**
     * 异步初始化 K8s Pod，通过 WebSocket 向前端推送进度通知。
     * <p>
     * 流程：
     * 1. 推送 "sandbox_creating" 通知
     * 2. 调用 acquirePod（可能耗时数分钟）
     * 3. 成功后创建 RuntimeAdapter，订阅 stdout，回放缓存消息
     * 4. 失败则推送错误通知并关闭连接
     */
    private void initK8sPodAsync(
            WebSocketSession session, String userId, String providerKey, RuntimeConfig config) {
        try {
            sendSandboxStatus(session, "creating", "正在准备沙箱环境...");

            PodInfo podInfo = podReuseManager.acquirePod(userId, config);

            if (!session.isOpen()) {
                logger.warn("WebSocket already closed during pod init: userId={}", userId);
                return;
            }

            K8sRuntimeAdapter adapter =
                    (K8sRuntimeAdapter) runtimeFactory.create(RuntimeType.K8S, config);
            adapter.setReuseMode(true);
            adapter.startWithExistingPod(podInfo);

            runtimeMap.put(session.getId(), adapter);

            logger.info(
                    "Async pod init complete: userId={}, pod={}, reused={}",
                    userId,
                    podInfo.podName(),
                    podInfo.reused());

            // 订阅 stdout
            Disposable subscription =
                    adapter.stdout()
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
                                                    "Error closing WebSocket after stdout"
                                                            + " completion",
                                                    e);
                                        }
                                    });
            subscriptionMap.put(session.getId(), subscription);

            // 推送就绪通知
            sendSandboxStatus(session, "ready", "沙箱环境已就绪");

            // 回放缓存的消息
            java.util.Queue<String> pendingQueue = pendingMessageMap.remove(session.getId());
            if (pendingQueue != null) {
                String queued;
                while ((queued = pendingQueue.poll()) != null) {
                    String rewritten = rewriteSessionNewCwd(session.getId(), queued);
                    try {
                        adapter.send(rewritten);
                    } catch (IOException e) {
                        logger.error(
                                "Error replaying queued message for session {}",
                                session.getId(),
                                e);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Async pod init failed for user {}: {}", userId, e.getMessage(), e);
            pendingMessageMap.remove(session.getId());
            sendSandboxStatus(session, "error", "沙箱创建失败: " + e.getMessage());
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SERVER_ERROR);
                }
            } catch (IOException closeEx) {
                logger.debug("Error closing WebSocket after pod init failure", closeEx);
            }
        }
    }

    /**
     * 向前端推送沙箱状态通知（JSON-RPC notification）。
     * <p>
     * 格式：{"jsonrpc":"2.0","method":"sandbox/status","params":{"status":"...","message":"..."}}
     */
    private void sendSandboxStatus(WebSocketSession session, String status, String message) {
        try {
            if (!session.isOpen()) return;
            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", "sandbox/status");
            ObjectNode params = objectMapper.createObjectNode();
            params.put("status", status);
            params.put("message", message);
            notification.set("params", params);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(notification)));
            }
        } catch (Exception e) {
            logger.warn("Failed to send sandbox status notification: {}", e.getMessage());
        }
    }

    /**
     * Resolve the runtime type from the query parameter string.
     * Defaults to LOCAL if the parameter is null, blank, or unrecognized (backward compatibility).
     */
    RuntimeType resolveRuntimeType(String runtimeParam) {
        if (runtimeParam == null || runtimeParam.isBlank()) {
            return RuntimeType.LOCAL;
        }
        try {
            return RuntimeType.valueOf(runtimeParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown runtime type '{}', defaulting to LOCAL", runtimeParam);
            return RuntimeType.LOCAL;
        }
    }

    /**
     * Build a RuntimeConfig from the CLI provider configuration and session context.
     */
    private RuntimeConfig buildRuntimeConfig(
            String userId,
            String providerKey,
            CliProviderConfig providerConfig,
            String cwd,
            RuntimeType runtimeType) {
        // Build process environment: start from provider-level env config.
        // If isolateHome is enabled, override HOME so the CLI stores credentials
        // under the per-user workspace.
        Map<String, String> processEnv = new HashMap<>(providerConfig.getEnv());
        if (providerConfig.isIsolateHome()) {
            processEnv.put("HOME", cwd);
            logger.info("HOME isolated for provider '{}': {}", providerKey, cwd);
        }

        RuntimeConfig config = new RuntimeConfig();
        config.setUserId(userId);
        config.setProviderKey(providerKey);
        config.setCommand(providerConfig.getCommand());
        config.setArgs(List.of(providerConfig.getArgs()));
        config.setCwd(cwd);
        config.setEnv(processEnv);
        config.setIsolateHome(providerConfig.isIsolateHome());

        // K8s 运行时：填充容器镜像和集群配置
        if (runtimeType == RuntimeType.K8S) {
            config.setContainerImage(providerConfig.getContainerImage());
            // POC 阶段：自动使用第一个已注册的 K8s 集群
            List<com.alibaba.himarket.service.acp.runtime.K8sClusterInfo> clusters =
                    k8sConfigService.listClusters();
            if (clusters.isEmpty()) {
                throw new IllegalStateException("没有已注册的 K8s 集群，无法使用 K8S 运行时");
            }
            config.setK8sConfigId(clusters.get(0).configId());
            logger.info(
                    "K8s runtime: using cluster configId={}, image={}",
                    clusters.get(0).configId(),
                    config.getContainerImage());
        }

        return config;
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
