package com.alibaba.himarket.service.hicoding.websocket;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.hicoding.runtime.RemoteRuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeConfig;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxHttpClient;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxInfo;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import com.alibaba.himarket.service.hicoding.session.CliSessionConfig;
import com.alibaba.himarket.service.hicoding.session.SessionInitializer;
import com.alibaba.himarket.service.hicoding.session.SessionInitializer.InitializationResult;
import com.alibaba.himarket.service.hicoding.websocket.HiCodingConnectionManager.DeferredInitParams;
import com.alibaba.himarket.service.hicoding.websocket.HiCodingConnectionManager.DetachedSessionInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;

/**
 * WebSocket event entry point.
 *
 * <p>Receives WebSocket events and delegates specialized work to focused components.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>afterConnectionEstablished: delegate to HiCodingConnectionManager and SessionInitializer
 *   <li>handleTextMessage: delegate to HiCodingMessageRouter
 *   <li>afterConnectionClosed / handleTransportError: delegate to HiCodingConnectionManager.cleanup()
 * </ul>
 */
@Component
public class HiCodingWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(HiCodingWebSocketHandler.class);
    private static final String SESSION_NEW_METHOD = "session/new";
    private static final String SESSION_LOAD_METHOD = "session/load";

    private final AcpProperties properties;
    private final ObjectMapper objectMapper;
    private final SessionInitializer sessionInitializer;
    private final HiCodingMessageRouter messageRouter;
    private final HiCodingConnectionManager connectionManager;
    private final WebSocketPingScheduler pingScheduler;
    private final SandboxHttpClient sandboxHttpClient;

    /**
     * Bounded executor for asynchronous initialization.
     */
    private final ExecutorService podInitExecutor =
            new ThreadPoolExecutor(
                    4,
                    32,
                    60L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(256),
                    r -> {
                        Thread t = new Thread(r, "sandbox-init");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy());

    public HiCodingWebSocketHandler(
            AcpProperties properties,
            ObjectMapper objectMapper,
            SessionInitializer sessionInitializer,
            HiCodingMessageRouter messageRouter,
            HiCodingConnectionManager connectionManager,
            WebSocketPingScheduler pingScheduler,
            SandboxHttpClient sandboxHttpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sessionInitializer = sessionInitializer;
        this.messageRouter = messageRouter;
        this.connectionManager = connectionManager;
        this.pingScheduler = pingScheduler;
        this.sandboxHttpClient = sandboxHttpClient;
    }

    @PreDestroy
    void shutdown() {
        podInitExecutor.shutdownNow();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            logger.error("No userId in session attributes, closing connection");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Resolve sandbox type from session attributes (set by HiCodingHandshakeInterceptor)
        String runtimeParam = (String) session.getAttributes().get("runtime");
        SandboxType sandboxType = resolveSandboxType(runtimeParam);

        // Build per-user working directory (always remote sandbox path)
        String cwd = "/workspace/" + userId;

        logger.info(
                "WebSocket connected, sessionId={}, userId={}, cwd={}, sandboxType={}",
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
            logger.error("Unknown CLI provider, closing connection, provider={}", providerKey);
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        logger.info(
                "Using CLI provider, provider={}, command={}",
                providerKey,
                providerConfig.getCommand());

        // Build RuntimeConfig from provider configuration
        RuntimeConfig config =
                buildRuntimeConfig(userId, providerKey, providerConfig, cwd, sandboxType);

        // Resolve sandboxMode from session attributes (set by HiCodingHandshakeInterceptor)
        String sandboxMode = (String) session.getAttributes().get("sandboxMode");

        // Register connection state.
        connectionManager.registerConnection(session.getId(), userId, cwd, sandboxMode);

        // Wait for the session/config message before starting the pipeline.
        connectionManager.setDeferredInit(
                session.getId(),
                new DeferredInitParams(userId, providerKey, config, providerConfig, sandboxType));
        logger.info(
                "Deferring pipeline init until session config message, sessionId={}",
                session.getId());
        // Non-blocking return for all sandbox types

        // Start protocol-level ping to keep the frontend connection alive.
        pingScheduler.startPing(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        String payload = message.getPayload();
        if (payload.isBlank()) {
            logger.trace("Ignoring blank message from session, sessionId={}", session.getId());
            return;
        }

        // Intercept session/config before pendingMessageMap because the pipeline is not started.
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
                                "Received session/config via WebSocket message, sessionId={},"
                                        + " modelProductId={}, mcpCount={}, skillCount={}",
                                session.getId(),
                                sessionConfig.getModelProductId(),
                                sessionConfig.getMcpServers() != null
                                        ? sessionConfig.getMcpServers().size()
                                        : 0,
                                sessionConfig.getSkills() != null
                                        ? sessionConfig.getSkills().size()
                                        : 0);
                    }
                }
            } catch (Exception e) {
                logger.warn(
                        "Failed to parse session config message, proceeding with null config,"
                                + " sessionId={}, errorMessage={}",
                        session.getId(),
                        e.getMessage(),
                        e);
            }

            // Start the pipeline regardless of whether session/config was parsed successfully.
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

            // session/config has been handled and should not be forwarded to the CLI.
            if (sessionConfig != null) {
                return;
            }
            // Non-session/config messages fall through into pending queue buffering.
        }

        // Queue messages while asynchronous sandbox initialization is still running.
        Queue<String> pendingQueue = connectionManager.getPendingMessages(session.getId());
        if (pendingQueue != null) {
            pendingQueue.add(payload);
            logger.debug(
                    "Sandbox is initializing, queued inbound message, sessionId={}, payload={}",
                    session.getId(),
                    payload);
            return;
        }

        RuntimeAdapter runtime = connectionManager.getRuntime(session.getId());
        if (runtime == null) {
            logger.warn("Runtime not found for WebSocket session, sessionId={}", session.getId());
            return;
        }

        logger.debug(
                "Inbound WebSocket message received, sessionId={}, payload={}",
                session.getId(),
                payload);

        // Rewrite cwd in session/new and session/load requests to the absolute workspace path
        payload = rewriteSessionCwd(session.getId(), payload);

        try {
            messageRouter.forwardToCliAgent(runtime, payload);
        } catch (IOException e) {
            logger.error(
                    "Failed to write to runtime stdin, sessionId={}, errorMessage={}",
                    session.getId(),
                    e.getMessage(),
                    e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
            throws Exception {
        logger.info("WebSocket closed, sessionId={}, status={}", session.getId(), status);
        pingScheduler.stopPing(session.getId());
        connectionManager.cleanup(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception)
            throws Exception {
        logger.error(
                "WebSocket transport error, sessionId={}, errorMessage={}",
                session.getId(),
                exception.getMessage(),
                exception);
        pingScheduler.stopPing(session.getId());
        connectionManager.cleanup(session.getId());
    }

    /**
     * Runs sandbox initialization in the podInitExecutor.
     *
     * <p>Delegates initialization to SessionInitializer and handles success or failure based on the
     * result.
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
            // Prefer reattaching to an existing detached session.
            DetachedSessionInfo detached = connectionManager.takeDetachedSession(userId);
            if (detached != null
                    && detached.adapter() instanceof RemoteRuntimeAdapter remoteAdapter) {
                try {
                    doReattach(session, userId, detached, remoteAdapter);
                    return;
                } catch (Exception e) {
                    logger.warn(
                            "Sandbox reattach failed, falling back to full initialization,"
                                    + " userId={}, errorMessage={}",
                            userId,
                            e.getMessage(),
                            e);
                    try {
                        remoteAdapter.close();
                    } catch (Exception closeEx) {
                        logger.debug(
                                "Failed to close adapter after reattach failure, userId={},"
                                        + " errorMessage={}",
                                userId,
                                closeEx.getMessage(),
                                closeEx);
                    }
                }
            } else if (detached != null) {
                detached.adapter().close();
            }

            logger.info(
                    "Starting async sandbox initialization, userId={}, sessionId={},"
                            + " sandboxType={}",
                    userId,
                    session.getId(),
                    sandboxType);
            sendSandboxStatus(session, "creating", "Preparing sandbox environment...");
            sendInitProgress(
                    session,
                    "sandbox-acquire",
                    "executing",
                    "Acquiring sandbox instance...",
                    0,
                    5,
                    0);

            // Send progress while acquiring the sandbox.
            sendInitProgress(
                    session,
                    "sandbox-acquire",
                    "executing",
                    "Acquiring sandbox instance...",
                    10,
                    5,
                    0);

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
                logger.warn(
                        "WebSocket is closed, skipping sandbox follow-up handling, userId={}",
                        userId);
                return;
            }

            if (result.success()) {
                RuntimeAdapter adapter = result.adapter();

                // Subscribe to stdout and forward it to the frontend.
                Disposable subscription = messageRouter.subscribeAndForward(adapter, session);

                // Register runtime resources.
                connectionManager.registerRuntime(session.getId(), adapter, subscription);

                // Send ready notification.
                SandboxInfo sInfo = result.sandboxInfo();
                String sandboxHost =
                        sInfo != null && sInfo.host() != null && !sInfo.host().isBlank()
                                ? sInfo.host()
                                : null;

                sendSandboxStatus(session, "ready", "Sandbox environment is ready", sandboxHost);
                sendInitProgress(
                        session,
                        "cli-ready",
                        "completed",
                        "Sandbox environment is ready",
                        100,
                        5,
                        5);
                logger.info("Sent sandbox status ready, sandboxHost={}", sandboxHost);

                // Notify the frontend of the actual working directory.
                String cwd = connectionManager.getCwd(session.getId());
                if (cwd != null) {
                    sendWorkspaceInfo(session, cwd);
                }

                // Replay queued messages after applying rewriteSessionCwd to each payload.
                Queue<String> pendingQueue = connectionManager.getPendingMessages(session.getId());
                if (pendingQueue != null) {
                    Queue<String> rewrittenQueue = new ConcurrentLinkedQueue<>();
                    String queued;
                    while ((queued = pendingQueue.poll()) != null) {
                        rewrittenQueue.add(rewriteSessionCwd(session.getId(), queued));
                    }
                    messageRouter.replayPendingMessages(session, adapter, rewrittenQueue);
                }
                // Remove pendingMessageMap so subsequent messages are forwarded directly.
                connectionManager.removePendingMessages(session.getId());

            } else {
                // Send detailed errors without closing the WebSocket; the frontend decides.
                sendSandboxError(session, result, sandboxType);
            }
        } catch (Exception e) {
            logger.error(
                    "Sandbox initialization error, userId={}, errorMessage={}",
                    userId,
                    e.getMessage(),
                    e);
            connectionManager.removePendingMessages(session.getId());
            sendSandboxStatus(session, "error", "Failed to create sandbox: " + e.getMessage());
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SERVER_ERROR);
                }
            } catch (IOException closeEx) {
                logger.debug(
                        "Failed to close WebSocket after sandbox init failure, sessionId={},"
                                + " errorMessage={}",
                        session.getId(),
                        closeEx.getMessage(),
                        closeEx);
            }
        }
    }

    /**
     * Reconnects to a detached Sidecar session.
     *
     * <p>Skips the full sandbox initialization flow, such as acquire and config injection, and
     * restores the WebSocket connection directly through adapter.reconnect().
     */
    private void doReattach(
            WebSocketSession session,
            String userId,
            DetachedSessionInfo detached,
            RemoteRuntimeAdapter remoteAdapter) {
        logger.info(
                "Attempting sandbox reattach, userId={}, sidecarSessionId={}",
                userId,
                detached.sidecarSessionId());

        sendSandboxStatus(session, "reattaching", "Restoring existing session...");

        // Reconnect to the Sidecar in sessionId attach mode.
        remoteAdapter.reconnect();

        // Wait briefly so reactor can process any immediate close signal from an expired session.
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify the connection is still alive after reconnect.
        if (!remoteAdapter.isAlive()) {
            throw new RuntimeException(
                    "Sidecar connection closed immediately after reattach,"
                            + " session may have expired on sidecar side");
        }

        // Validate the Sidecar session through REST because WebSocket accepts any sessionId.
        String sidecarBaseUrl =
                "http://"
                        + properties.getRemote().getHost()
                        + ":"
                        + properties.getRemote().getPort();
        if (!sandboxHttpClient.sessionExists(sidecarBaseUrl, detached.sidecarSessionId())) {
            throw new RuntimeException(
                    "Sidecar session no longer exists: sidecarSessionId="
                            + detached.sidecarSessionId());
        }

        if (!session.isOpen()) {
            logger.warn("WebSocket is closed, skipping sandbox reattach, userId={}", userId);
            remoteAdapter.detach();
            // Ensure no stale detached session remains.
            connectionManager.takeDetachedSession(userId);
            return;
        }

        // Notify the frontend before subscribing to stdout so the status arrives first.
        sendSandboxStatus(session, "ready", "Existing session restored");
        sendInitProgress(session, "cli-ready", "completed", "Existing session restored", 100, 5, 5);

        // Notify the frontend of the actual working directory.
        String cwd = connectionManager.getCwd(session.getId());
        if (cwd != null) {
            sendWorkspaceInfo(session, cwd);
        }

        // Notify the frontend that this connection was reattached.
        sendReattachNotification(session, detached.sidecarSessionId());

        // Subscribe after notifications so residual Sidecar output does not arrive too early.
        Disposable subscription = messageRouter.subscribeAndForward(remoteAdapter, session);

        // Register runtime resources.
        connectionManager.registerRuntime(session.getId(), remoteAdapter, subscription);

        // Replay queued messages.
        Queue<String> pendingQueue = connectionManager.getPendingMessages(session.getId());
        if (pendingQueue != null) {
            Queue<String> rewrittenQueue = new ConcurrentLinkedQueue<>();
            String queued;
            while ((queued = pendingQueue.poll()) != null) {
                rewrittenQueue.add(rewriteSessionCwd(session.getId(), queued));
            }
            messageRouter.replayPendingMessages(session, remoteAdapter, rewrittenQueue);
        }
        connectionManager.removePendingMessages(session.getId());

        logger.info(
                "Sandbox reattach succeeded, userId={}, sidecarSessionId={}",
                userId,
                detached.sidecarSessionId());
    }

    /**
     * Sends detailed sandbox initialization errors for InitializationResult.
     *
     * <p>Includes failedPhase, errorMessage, retryable, and diagnostics.
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
                    "Sandbox initialization failed: "
                            + (result.errorMessage() != null
                                    ? result.errorMessage()
                                    : "Unknown error"));
            params.put("failedPhase", result.failedPhase());
            params.put("sandboxType", sandboxType.getValue());
            params.put("retryable", result.retryable());

            // diagnostics
            ObjectNode diagnostics = objectMapper.createObjectNode();
            List<String> completedPhases = new ArrayList<>();
            // InitializationResult does not hold phaseDurations directly; infer from failedPhase.
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
                    "Sent sandbox error notification, failedPhase={}, retryable={}, message={}",
                    result.failedPhase(),
                    result.retryable(),
                    result.errorMessage());
        } catch (Exception e) {
            logger.warn(
                    "Failed to send sandbox error notification, errorMessage={}",
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Builds a targeted troubleshooting suggestion from the failed phase.
     */
    private String buildSuggestion(InitializationResult result) {
        if (result.failedPhase() == null) {
            return "Check the sandbox configuration or contact an administrator.";
        }
        return switch (result.failedPhase()) {
            case "filesystem-ready" ->
                    "Sandbox service is unreachable. Check that the Sidecar service is running, "
                            + "ACP_REMOTE_HOST and ACP_REMOTE_PORT are correct, and the network is"
                            + " reachable.";
            case "sandbox-acquire" ->
                    "Failed to acquire sandbox instance. Check the sandbox configuration or contact"
                            + " an administrator.";
            case "config-injection" -> "Failed to inject configuration. Try reconnecting.";
            case "sidecar-connect" ->
                    "Failed to connect to the Sidecar WebSocket. Check the Sidecar service status"
                            + " and retry.";
            case "cli-ready" ->
                    "Failed to start the CLI tool. Check the CLI command configuration.";
            default ->
                    result.retryable()
                            ? "Try reconnecting."
                            : "Check the sandbox configuration or contact an administrator.";
        };
    }

    /**
     * Sends an initialization progress notification to the frontend.
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
            logger.warn(
                    "Failed to send init progress notification, errorMessage={}",
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Sends a sandbox status notification to the frontend.
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
            logger.warn(
                    "Failed to send sandbox status notification, errorMessage={}",
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Sends working directory information to the frontend.
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
            logger.warn(
                    "Failed to send workspace info notification, errorMessage={}",
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Sends a reattach notification to the frontend.
     *
     * <p>Indicates that this connection restored an existing Sidecar session.
     */
    private void sendReattachNotification(WebSocketSession session, String sidecarSessionId) {
        try {
            if (!session.isOpen()) return;
            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", "sandbox/reattached");
            ObjectNode params = objectMapper.createObjectNode();
            params.put("sidecarSessionId", sidecarSessionId);
            params.put("reattached", true);
            notification.set("params", params);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(notification)));
            }
        } catch (Exception e) {
            logger.warn("Failed to send reattach notification, errorMessage={}", e.getMessage(), e);
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
            logger.warn(
                    "Unknown sandbox type, defaulting to REMOTE, runtimeParam={}", runtimeParam);
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
        // Point HOME to the user workspace so CLI session files stay on the persistent volume.
        processEnv.put("HOME", cwd);
        RuntimeConfig config = new RuntimeConfig();
        config.setUserId(userId);
        config.setProviderKey(providerKey);
        config.setCommand(providerConfig.getCommand());
        config.setArgs(List.of(providerConfig.getArgs()));
        config.setCwd(cwd);
        config.setEnv(processEnv);

        if (sandboxType == SandboxType.REMOTE) {
            logger.info("Using remote runtime, host={}", properties.getRemote().getHost());
        }

        return config;
    }

    /**
     * Intercept session/new and session/load requests and replace the cwd parameter
     * with the absolute workspace path so that the ACP CLI knows the real directory.
     */
    private String rewriteSessionCwd(String sessionId, String payload) {
        // Fast-path: skip JSON parsing for messages that clearly aren't session/new or session/load
        if (!payload.contains(SESSION_NEW_METHOD) && !payload.contains(SESSION_LOAD_METHOD)) {
            return payload;
        }
        String cwd = connectionManager.getCwd(sessionId);
        if (cwd == null) {
            return payload;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode methodNode = root.get("method");
            if (methodNode == null) {
                return payload;
            }
            String method = methodNode.asText();
            if (!SESSION_NEW_METHOD.equals(method) && !SESSION_LOAD_METHOD.equals(method)) {
                return payload;
            }
            JsonNode params = root.get("params");
            if (params == null || !params.isObject()) {
                return payload;
            }
            ((ObjectNode) params).put("cwd", cwd);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            logger.debug("Failed to rewrite session cwd, forwarding original payload", e);
            return payload;
        }
    }
}
