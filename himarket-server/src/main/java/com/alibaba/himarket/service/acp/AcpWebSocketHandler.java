package com.alibaba.himarket.service.acp;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.acp.AcpConnectionManager.DeferredInitParams;
import com.alibaba.himarket.service.acp.AcpSessionInitializer.InitializationResult;
import com.alibaba.himarket.service.acp.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.acp.runtime.RuntimeConfig;
import com.alibaba.himarket.service.acp.runtime.SandboxInfo;
import com.alibaba.himarket.service.acp.runtime.SandboxType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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

/**
 * WebSocket 事件入口。
 * 仅负责接收 WebSocket 事件并委托给专门的组件处理。
 *
 * <p>职责：
 * <ul>
 *   <li>afterConnectionEstablished → 委托 AcpConnectionManager + AcpSessionInitializer
 *   <li>handleTextMessage → 委托 AcpMessageRouter
 *   <li>afterConnectionClosed / handleTransportError → 委托 AcpConnectionManager.cleanup()
 * </ul>
 */
@Component
public class AcpWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(AcpWebSocketHandler.class);
    private static final String SESSION_NEW_METHOD = "session/new";

    private final AcpProperties properties;
    private final ObjectMapper objectMapper;
    private final AcpSessionInitializer sessionInitializer;
    private final AcpMessageRouter messageRouter;
    private final AcpConnectionManager connectionManager;

    /** 异步初始化线程池 */
    private final ExecutorService podInitExecutor =
            Executors.newCachedThreadPool(
                    r -> {
                        Thread t = new Thread(r, "sandbox-init");
                        t.setDaemon(true);
                        return t;
                    });

    public AcpWebSocketHandler(
            AcpProperties properties,
            ObjectMapper objectMapper,
            AcpSessionInitializer sessionInitializer,
            AcpMessageRouter messageRouter,
            AcpConnectionManager connectionManager) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sessionInitializer = sessionInitializer;
        this.messageRouter = messageRouter;
        this.connectionManager = connectionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            logger.error("No userId in session attributes, closing connection");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Resolve sandbox type from session attributes (set by AcpHandshakeInterceptor)
        String runtimeParam = (String) session.getAttributes().get("runtime");
        SandboxType sandboxType = resolveSandboxType(runtimeParam);

        // Build per-user working directory (always remote sandbox path)
        String cwd = "/workspace/" + userId;

        logger.info(
                "WebSocket connected: id={}, userId={}, cwd={}, sandboxType={}",
                session.getId(),
                userId,
                cwd,
                sandboxType);

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
                buildRuntimeConfig(userId, providerKey, providerConfig, cwd, sandboxType);

        // 获取会话配置（配置注入由 ConfigInjectionPhase 统一处理）
        CliSessionConfig sessionConfig =
                (CliSessionConfig) session.getAttributes().get("cliSessionConfig");

        // Resolve sandboxMode from session attributes (set by AcpHandshakeInterceptor)
        String sandboxMode = (String) session.getAttributes().get("sandboxMode");

        // 注册连接
        connectionManager.registerConnection(session.getId(), userId, cwd, sandboxMode);

        if (sessionConfig != null) {
            // 旧客户端：cliSessionConfig 在 URL 中，立即启动 pipeline
            final String fProviderKey = providerKey;
            final RuntimeConfig fConfig = config;
            final CliProviderConfig fProviderConfig = providerConfig;
            final CliSessionConfig fSessionConfig = sessionConfig;
            podInitExecutor.submit(
                    () ->
                            doInitialize(
                                    session,
                                    userId,
                                    fProviderKey,
                                    fConfig,
                                    fProviderConfig,
                                    fSessionConfig,
                                    sandboxType));
        } else {
            // 新客户端：等待 session/config 消息到达后再启动 pipeline
            connectionManager.setDeferredInit(
                    session.getId(),
                    new DeferredInitParams(
                            userId, providerKey, config, providerConfig, sandboxType));
            logger.info(
                    "No cliSessionConfig in URL, deferring pipeline init until session/config"
                            + " message: session={}",
                    session.getId());
        }
        // Non-blocking return for all sandbox types
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        String payload = message.getPayload();
        if (payload.isBlank()) {
            logger.trace("Ignoring blank message from session {}", session.getId());
            return;
        }

        // 拦截 session/config 消息：前端连接后发送的配置（替代 URL query string 传递）
        // 必须在 pendingMessageMap 检查之前处理，因为此时 pipeline 尚未启动
        DeferredInitParams deferred = connectionManager.getDeferredInit(session.getId());
        if (deferred != null) {
            CliSessionConfig sessionConfig = null;
            try {
                JsonNode root = objectMapper.readTree(payload);
                JsonNode methodNode = root.get("method");
                if (methodNode != null && "session/config".equals(methodNode.asText())) {
                    JsonNode params = root.get("params");
                    if (params != null) {
                        sessionConfig = objectMapper.treeToValue(params, CliSessionConfig.class);
                        logger.info(
                                "Received session/config via WebSocket message: session={},"
                                    + " hasModel={}, mcpCount={}, skillCount={}, hasAuthToken={}",
                                session.getId(),
                                sessionConfig.getModelProductId() != null,
                                sessionConfig.getMcpServers() != null
                                        ? sessionConfig.getMcpServers().size()
                                        : 0,
                                sessionConfig.getSkills() != null
                                        ? sessionConfig.getSkills().size()
                                        : 0,
                                sessionConfig.getAuthToken() != null
                                        && !sessionConfig.getAuthToken().isEmpty());
                    }
                }
            } catch (Exception e) {
                logger.warn(
                        "Failed to parse session/config message, proceeding with null config: {}",
                        e.getMessage());
            }

            // 无论是否成功解析 session/config，都启动 pipeline（config 可以为 null）
            final CliSessionConfig fSessionConfig = sessionConfig;
            final DeferredInitParams fDeferred = deferred;
            podInitExecutor.submit(
                    () ->
                            doInitialize(
                                    session,
                                    fDeferred.userId(),
                                    fDeferred.providerKey(),
                                    fDeferred.config(),
                                    fDeferred.providerConfig(),
                                    fSessionConfig,
                                    fDeferred.sandboxType()));

            // 如果这条消息是 session/config，已处理完毕，不需要转发给 CLI
            if (sessionConfig != null) {
                return;
            }
            // 不是 session/config 消息，fall through 到下面的 pendingQueue 缓存逻辑
        }

        // 如果 Pod 还在异步初始化中，缓存消息
        Queue<String> pendingQueue = connectionManager.getPendingMessages(session.getId());
        if (pendingQueue != null) {
            pendingQueue.add(payload);
            logger.debug("Pod initializing, queued message for session {}", session.getId());
            return;
        }

        RuntimeAdapter runtime = connectionManager.getRuntime(session.getId());
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
            messageRouter.forwardToCliAgent(runtime, payload);
        } catch (IOException e) {
            logger.error("Error writing to runtime stdin for session {}", session.getId(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
            throws Exception {
        logger.info("WebSocket closed: id={}, status={}", session.getId(), status);
        connectionManager.cleanup(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception)
            throws Exception {
        logger.error("WebSocket transport error for session {}", session.getId(), exception);
        connectionManager.cleanup(session.getId());
    }

    /**
     * 在 podInitExecutor 线程池中执行沙箱初始化。
     * 委托 AcpSessionInitializer 完成初始化，根据结果处理成功/失败。
     */
    private void doInitialize(
            WebSocketSession session,
            String userId,
            String providerKey,
            RuntimeConfig config,
            CliProviderConfig providerConfig,
            CliSessionConfig sessionConfig,
            SandboxType sandboxType) {
        try {
            logger.info(
                    "[Sandbox-Init] 开始异步沙箱初始化: userId={}, session={}, type={}",
                    userId,
                    session.getId(),
                    sandboxType);
            sendSandboxStatus(session, "creating", "正在准备沙箱环境...");
            sendInitProgress(session, "sandbox-acquire", "executing", "正在获取沙箱实例...", 0, 5, 0);

            // 推送进度：沙箱获取中
            sendInitProgress(session, "sandbox-acquire", "executing", "正在获取沙箱实例...", 10, 5, 0);

            InitializationResult result =
                    sessionInitializer.initialize(
                            userId,
                            providerKey,
                            providerConfig,
                            config,
                            sessionConfig,
                            sandboxType,
                            session);

            if (!session.isOpen()) {
                logger.warn("[Sandbox-Init] WebSocket 已关闭，放弃后续处理: userId={}", userId);
                return;
            }

            if (result.success()) {
                RuntimeAdapter adapter = result.adapter();

                // 订阅 stdout 并转发到前端
                Disposable subscription = messageRouter.subscribeAndForward(adapter, session);

                // 注册运行时资源
                connectionManager.registerRuntime(session.getId(), adapter, subscription);

                // 推送就绪通知
                SandboxInfo sInfo = result.sandboxInfo();
                String sandboxHost =
                        sInfo != null && sInfo.host() != null && !sInfo.host().isBlank()
                                ? sInfo.host()
                                : null;

                // 记录 userId → sandboxHost 映射，供 DevProxy 反向代理路由使用
                String effectiveHost = sandboxHost != null ? sandboxHost : "localhost";
                connectionManager.setSandboxHost(userId, effectiveHost);
                logger.info(
                        "[Sandbox-Init] 已记录 sandboxHost 映射: userId={}, host={}",
                        userId,
                        effectiveHost);

                sendSandboxStatus(session, "ready", "沙箱环境已就绪", sandboxHost);
                sendInitProgress(session, "cli-ready", "completed", "沙箱环境已就绪", 100, 5, 5);
                logger.info(
                        "[Sandbox-Init] 已发送 sandbox/status: ready, sandboxHost={}", sandboxHost);

                // 通知前端实际使用的工作目录
                String cwd = connectionManager.getCwd(session.getId());
                if (cwd != null) {
                    sendWorkspaceInfo(session, cwd);
                }

                // 回放缓存的消息（先对每条消息做 rewriteSessionNewCwd 变换）
                Queue<String> pendingQueue = connectionManager.getPendingMessages(session.getId());
                if (pendingQueue != null) {
                    Queue<String> rewrittenQueue = new ConcurrentLinkedQueue<>();
                    String queued;
                    while ((queued = pendingQueue.poll()) != null) {
                        rewrittenQueue.add(rewriteSessionNewCwd(session.getId(), queued));
                    }
                    messageRouter.replayPendingMessages(session, adapter, rewrittenQueue);
                }
                // 移除 pendingMessageMap 标记，后续消息直接转发
                connectionManager.removePendingMessages(session.getId());

            } else {
                // 发送详细错误信息，不主动关闭 WebSocket，由前端决定后续行为
                sendSandboxError(session, result, sandboxType);
            }
        } catch (Exception e) {
            logger.error("[Sandbox-Init] 沙箱初始化异常: userId={}, error={}", userId, e.getMessage(), e);
            connectionManager.removePendingMessages(session.getId());
            sendSandboxStatus(session, "error", "沙箱创建失败: " + e.getMessage());
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SERVER_ERROR);
                }
            } catch (IOException closeEx) {
                logger.debug("Error closing WebSocket after sandbox init failure", closeEx);
            }
        }
    }

    /**
     * 发送详细的沙箱初始化错误信息（适配 InitializationResult）。
     * 包含 failedPhase、errorMessage、retryable、diagnostics。
     */
    private void sendSandboxError(
            WebSocketSession session, InitializationResult result, SandboxType sandboxType) {
        try {
            if (!session.isOpen()) return;
            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", "sandbox/status");
            ObjectNode params = objectMapper.createObjectNode();
            params.put("status", "error");
            params.put(
                    "message",
                    "沙箱初始化失败: " + (result.errorMessage() != null ? result.errorMessage() : "未知错误"));
            params.put("failedPhase", result.failedPhase());
            params.put("sandboxType", sandboxType.getValue());
            params.put("retryable", result.retryable());

            // diagnostics
            ObjectNode diagnostics = objectMapper.createObjectNode();
            List<String> completedPhases = new ArrayList<>();
            // InitializationResult 不直接持有 phaseDurations，使用 failedPhase 推断
            diagnostics.set("completedPhases", objectMapper.valueToTree(completedPhases));
            if (result.totalDuration() != null) {
                diagnostics.put(
                        "totalDuration",
                        String.format("%.1fs", result.totalDuration().toMillis() / 1000.0));
            }
            diagnostics.put("suggestion", buildSuggestion(result));
            params.set("diagnostics", diagnostics);

            notification.set("params", params);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(notification)));
            }
            logger.error(
                    "[Sandbox-Init] 发送错误通知: failedPhase={}, retryable={}, message={}",
                    result.failedPhase(),
                    result.retryable(),
                    result.errorMessage());
        } catch (Exception e) {
            logger.warn("Failed to send sandbox error notification: {}", e.getMessage());
        }
    }

    /**
     * 根据失败阶段生成针对性的排查建议。
     */
    private String buildSuggestion(InitializationResult result) {
        if (result.failedPhase() == null) {
            return "请检查沙箱配置或联系管理员";
        }
        return switch (result.failedPhase()) {
            case "filesystem-ready" ->
                    "沙箱服务不可达，请检查: 1) sidecar 服务是否已启动 "
                            + "2) ACP_REMOTE_HOST 和 ACP_REMOTE_PORT 配置是否正确 "
                            + "3) 网络是否可达";
            case "sandbox-acquire" -> "沙箱实例获取失败，请检查沙箱配置或联系管理员";
            case "config-injection" -> "配置注入失败，请重试连接";
            case "sidecar-connect" -> "Sidecar WebSocket 连接失败，请检查 sidecar 服务状态后重试";
            case "cli-ready" -> "CLI 工具启动失败，请检查 CLI 命令配置是否正确";
            default -> result.retryable() ? "请重试连接" : "请检查沙箱配置或联系管理员";
        };
    }

    /**
     * 向前端推送初始化进度消息。
     */
    private void sendInitProgress(
            WebSocketSession session,
            String phase,
            String status,
            String message,
            int progress,
            int totalPhases,
            int completedPhases) {
        try {
            if (!session.isOpen()) return;
            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", "sandbox/init-progress");
            ObjectNode params = objectMapper.createObjectNode();
            params.put("phase", phase);
            params.put("status", status);
            params.put("message", message);
            params.put("progress", progress);
            params.put("totalPhases", totalPhases);
            params.put("completedPhases", completedPhases);
            notification.set("params", params);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(notification)));
            }
        } catch (Exception e) {
            logger.warn("Failed to send init progress notification: {}", e.getMessage());
        }
    }

    /**
     * 向前端推送沙箱状态通知（JSON-RPC notification）。
     */
    private void sendSandboxStatus(WebSocketSession session, String status, String message) {
        sendSandboxStatus(session, status, message, null);
    }

    private void sendSandboxStatus(
            WebSocketSession session, String status, String message, String sandboxHost) {
        try {
            if (!session.isOpen()) return;
            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", "sandbox/status");
            ObjectNode params = objectMapper.createObjectNode();
            params.put("status", status);
            params.put("message", message);
            if (sandboxHost != null && !sandboxHost.isBlank()) {
                params.put("sandboxHost", sandboxHost);
            }
            notification.set("params", params);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(notification)));
            }
        } catch (Exception e) {
            logger.warn("Failed to send sandbox status notification: {}", e.getMessage());
        }
    }

    /**
     * 向前端推送工作目录信息通知（JSON-RPC notification）。
     */
    private void sendWorkspaceInfo(WebSocketSession session, String cwd) {
        try {
            if (!session.isOpen()) return;
            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", "workspace/info");
            ObjectNode params = objectMapper.createObjectNode();
            params.put("cwd", cwd);
            notification.set("params", params);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(notification)));
            }
        } catch (Exception e) {
            logger.warn("Failed to send workspace info notification: {}", e.getMessage());
        }
    }

    /**
     * Resolve the runtime type from the query parameter string.
     * Defaults to REMOTE if the parameter is null, blank, or unrecognized.
     */
    SandboxType resolveSandboxType(String runtimeParam) {
        if (runtimeParam == null || runtimeParam.isBlank()) {
            return SandboxType.REMOTE;
        }
        try {
            return SandboxType.fromValue(runtimeParam);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown sandbox type '{}', defaulting to REMOTE", runtimeParam);
            return SandboxType.REMOTE;
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
            SandboxType sandboxType) {
        Map<String, String> processEnv =
                providerConfig.getEnv() != null
                        ? new HashMap<>(providerConfig.getEnv())
                        : new HashMap<>();
        RuntimeConfig config = new RuntimeConfig();
        config.setUserId(userId);
        config.setProviderKey(providerKey);
        config.setCommand(providerConfig.getCommand());
        config.setArgs(List.of(providerConfig.getArgs()));
        config.setCwd(cwd);
        config.setEnv(processEnv);

        if (sandboxType == SandboxType.REMOTE) {
            logger.info("Remote runtime: host={}", properties.getRemote().getHost());
        }

        return config;
    }

    /**
     * Intercept session/new requests and replace the cwd parameter with the
     * absolute workspace path so that the ACP CLI knows the real directory.
     */
    private String rewriteSessionNewCwd(String sessionId, String payload) {
        String cwd = connectionManager.getCwd(sessionId);
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
}
