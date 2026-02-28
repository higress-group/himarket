package com.alibaba.himarket.service.acp;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.acp.runtime.CliReadyPhase;
import com.alibaba.himarket.service.acp.runtime.ConfigFile;
import com.alibaba.himarket.service.acp.runtime.ConfigInjectionPhase;
import com.alibaba.himarket.service.acp.runtime.FileSystemReadyPhase;
import com.alibaba.himarket.service.acp.runtime.InitConfig;
import com.alibaba.himarket.service.acp.runtime.InitContext;
import com.alibaba.himarket.service.acp.runtime.InitResult;
import com.alibaba.himarket.service.acp.runtime.K8sConfigService;
import com.alibaba.himarket.service.acp.runtime.K8sRuntimeAdapter;
import com.alibaba.himarket.service.acp.runtime.PodInfo;
import com.alibaba.himarket.service.acp.runtime.PodReuseManager;
import com.alibaba.himarket.service.acp.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.acp.runtime.RuntimeConfig;
import com.alibaba.himarket.service.acp.runtime.RuntimeFactory;
import com.alibaba.himarket.service.acp.runtime.RuntimeType;
import com.alibaba.himarket.service.acp.runtime.SandboxAcquirePhase;
import com.alibaba.himarket.service.acp.runtime.SandboxConfig;
import com.alibaba.himarket.service.acp.runtime.SandboxInfo;
import com.alibaba.himarket.service.acp.runtime.SandboxInitPipeline;
import com.alibaba.himarket.service.acp.runtime.SandboxProvider;
import com.alibaba.himarket.service.acp.runtime.SandboxProviderRegistry;
import com.alibaba.himarket.service.acp.runtime.SandboxType;
import com.alibaba.himarket.service.acp.runtime.SidecarConnectPhase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
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
    private final SandboxProviderRegistry sandboxProviderRegistry;
    private final Map<String, CliConfigGenerator> configGeneratorRegistry;
    private final Map<String, RuntimeAdapter> runtimeMap = new ConcurrentHashMap<>();
    private final Map<String, Disposable> subscriptionMap = new ConcurrentHashMap<>();
    private final Map<String, String> cwdMap = new ConcurrentHashMap<>();
    private final Map<String, String> userIdMap = new ConcurrentHashMap<>();
    private final Map<String, String> sandboxModeMap = new ConcurrentHashMap<>();

    /** 记录每个 session 生成的配置文件路径，用于启动失败时清理 */
    private final Map<String, java.util.List<Path>> generatedConfigFilesMap =
            new ConcurrentHashMap<>();

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

    /**
     * 延迟初始化上下文：当 WebSocket 握手时 URL 中没有 cliSessionConfig，
     * 等待前端通过 session/config 消息发送配置后再启动 pipeline。
     * 存储 afterConnectionEstablished 中解析好的参数，供 session/config 消息到达时使用。
     */
    private record DeferredInitParams(
            String userId,
            String providerKey,
            RuntimeConfig config,
            AcpProperties.CliProviderConfig providerConfig,
            SandboxType sandboxType) {}

    private final Map<String, DeferredInitParams> deferredInitMap = new ConcurrentHashMap<>();

    public AcpWebSocketHandler(
            AcpProperties properties,
            ObjectMapper objectMapper,
            RuntimeFactory runtimeFactory,
            K8sConfigService k8sConfigService,
            PodReuseManager podReuseManager,
            SandboxProviderRegistry sandboxProviderRegistry) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.runtimeFactory = runtimeFactory;
        this.k8sConfigService = k8sConfigService;
        this.podReuseManager = podReuseManager;
        this.sandboxProviderRegistry = sandboxProviderRegistry;

        // 初始化配置生成器注册表
        this.configGeneratorRegistry = new HashMap<>();
        OpenCodeConfigGenerator openCodeGenerator = new OpenCodeConfigGenerator(objectMapper);
        QwenCodeConfigGenerator qwenCodeGenerator = new QwenCodeConfigGenerator(objectMapper);
        ClaudeCodeConfigGenerator claudeCodeGenerator = new ClaudeCodeConfigGenerator(objectMapper);
        QoderCliConfigGenerator qoderCliGenerator = new QoderCliConfigGenerator(objectMapper);
        this.configGeneratorRegistry.put(openCodeGenerator.supportedProvider(), openCodeGenerator);
        this.configGeneratorRegistry.put(qwenCodeGenerator.supportedProvider(), qwenCodeGenerator);
        this.configGeneratorRegistry.put(
                claudeCodeGenerator.supportedProvider(), claudeCodeGenerator);
        this.configGeneratorRegistry.put(qoderCliGenerator.supportedProvider(), qoderCliGenerator);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId == null) {
            logger.error("No userId in session attributes, closing connection");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Resolve runtime type from session attributes (set by AcpHandshakeInterceptor)
        String runtimeParam = (String) session.getAttributes().get("runtime");
        RuntimeType runtimeType = resolveRuntimeType(runtimeParam);

        // Build per-user working directory
        // K8s 运行时：CLI 在 Pod 内运行，cwd 使用 Pod 内路径 /workspace
        // 本地运行时：使用服务器本地路径
        String cwd = runtimeType == RuntimeType.K8S ? "/workspace" : buildWorkspacePath(userId);

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

        // 获取会话配置（配置注入由 ConfigInjectionPhase 统一处理）
        // 优先从 URL query string 获取（向后兼容旧客户端），
        // 新客户端不再通过 URL 传递，改为连接后发送 session/config 消息
        CliSessionConfig sessionConfig =
                (CliSessionConfig) session.getAttributes().get("cliSessionConfig");

        // Resolve sandboxMode from session attributes (set by AcpHandshakeInterceptor)
        String sandboxMode = (String) session.getAttributes().get("sandboxMode");

        // Map RuntimeType to SandboxType — 所有沙箱类型走统一的异步初始化路径
        SandboxType sandboxType =
                runtimeType == RuntimeType.K8S ? SandboxType.K8S : SandboxType.LOCAL;

        // Store session state
        cwdMap.put(session.getId(), cwd);
        userIdMap.put(session.getId(), userId);
        sandboxModeMap.put(session.getId(), sandboxMode != null ? sandboxMode : "");
        pendingMessageMap.put(session.getId(), new java.util.concurrent.ConcurrentLinkedQueue<>());

        if (sessionConfig != null) {
            // 旧客户端：cliSessionConfig 在 URL 中，立即启动 pipeline
            final String fProviderKey = providerKey;
            final RuntimeConfig fConfig = config;
            final CliProviderConfig fProviderConfig = providerConfig;
            final CliSessionConfig fSessionConfig = sessionConfig;
            podInitExecutor.submit(
                    () ->
                            initSandboxAsync(
                                    session,
                                    userId,
                                    fProviderKey,
                                    fConfig,
                                    fProviderConfig,
                                    fSessionConfig,
                                    sandboxType));
        } else {
            // 新客户端：等待 session/config 消息到达后再启动 pipeline
            // 存储解析好的参数供后续使用
            deferredInitMap.put(
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
        DeferredInitParams deferred = deferredInitMap.remove(session.getId());
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
                                        + " hasModel={}, mcpCount={}, skillCount={}",
                                session.getId(),
                                sessionConfig.getCustomModelConfig() != null,
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
                        "Failed to parse session/config message, proceeding with null config: {}",
                        e.getMessage());
            }

            // 无论是否成功解析 session/config，都启动 pipeline（config 可以为 null）
            // 如果第一条消息不是 session/config（旧客户端不发此消息），也正常启动
            final CliSessionConfig fSessionConfig = sessionConfig;
            final DeferredInitParams fDeferred = deferred;
            podInitExecutor.submit(
                    () ->
                            initSandboxAsync(
                                    session,
                                    fDeferred.userId(),
                                    fDeferred.providerKey(),
                                    fDeferred.config(),
                                    fDeferred.providerConfig(),
                                    fSessionConfig,
                                    fDeferred.sandboxType()));

            // 如果这条消息是 session/config，已处理完毕，不需要转发给 CLI
            // 如果不是 session/config（比如直接发了 initialize），需要缓存到 pendingQueue
            if (sessionConfig != null) {
                return;
            }
            // 不是 session/config 消息，fall through 到下面的 pendingQueue 缓存逻辑
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
        deferredInitMap.remove(sessionId);

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

        // 清理生成的配置文件记录（不删除文件，仅清理 map 条目）
        generatedConfigFilesMap.remove(sessionId);

        cwdMap.remove(sessionId);
        userIdMap.remove(sessionId);
        sandboxModeMap.remove(sessionId);
    }

    /**
     * 根据 provider 类型获取生成的配置文件路径列表。
     */
    private java.util.List<Path> getGeneratedConfigFiles(String providerKey, String cwd) {
        java.util.List<Path> files = new java.util.ArrayList<>();
        if ("opencode".equals(providerKey)) {
            files.add(Path.of(cwd, "opencode.json"));
        } else if ("qwen-code".equals(providerKey)) {
            files.add(Path.of(cwd, ".qwen", "settings.json"));
        }
        return files;
    }

    /**
     * 清理已生成的配置文件，防止残留配置影响后续会话。
     * 在 CLI 进程启动失败时调用。
     */
    private void cleanupGeneratedConfigFiles(String sessionId) {
        java.util.List<Path> files = generatedConfigFilesMap.remove(sessionId);
        if (files == null || files.isEmpty()) {
            return;
        }
        for (Path file : files) {
            try {
                if (Files.deleteIfExists(file)) {
                    logger.info("Cleaned up generated config file: {}", file);
                }
            } catch (IOException e) {
                logger.warn(
                        "Failed to clean up generated config file {}: {}", file, e.getMessage());
            }
        }
    }

    /**
     * 通过 Pipeline + SandboxProvider 异步初始化沙箱环境。
     * <p>
     * 流程：
     * 1. 通过 SandboxProviderRegistry 获取对应的 SandboxProvider
     * 2. 构建 SandboxConfig 和 InitContext
     * 3. 准备配置文件（填充 InitContext.injectedConfigs）
     * 4. 执行 SandboxInitPipeline
     * 5. 成功：获取 RuntimeAdapter，订阅 stdout，发送 ready
     * 6. 失败：发送详细错误信息（含 failedPhase、retryable、diagnostics）
     * <p>
     * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
     */
    private void initSandboxAsync(
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

            // 1. 获取 Provider
            SandboxProvider provider = sandboxProviderRegistry.getProvider(sandboxType);

            // 1.5 注入 authToken 到 CLI 进程环境变量
            if (sessionConfig != null && sessionConfig.getAuthToken() != null) {
                if (providerConfig.getAuthEnvVar() != null) {
                    config.getEnv()
                            .put(providerConfig.getAuthEnvVar(), sessionConfig.getAuthToken());
                    logger.info(
                            "[Sandbox-Init] authToken injected to env var '{}' for provider '{}'",
                            providerConfig.getAuthEnvVar(),
                            providerKey);
                } else {
                    logger.warn(
                            "Received authToken but authEnvVar is not configured for provider: {},"
                                    + " ignoring authToken",
                            providerKey);
                }
            }

            // 2. 构建 SandboxConfig
            SandboxConfig sandboxConfig =
                    new SandboxConfig(
                            userId,
                            sandboxType,
                            config.getCwd(),
                            config.getEnv() != null ? config.getEnv() : Map.of(),
                            config.getK8sConfigId(),
                            Map.of(),
                            null,
                            0);

            // 3. 构建 InitContext
            InitContext context =
                    new InitContext(
                            provider,
                            userId,
                            sandboxConfig,
                            config,
                            providerConfig,
                            sessionConfig,
                            session);

            // 4. 准备配置文件（填充 injectedConfigs）
            prepareConfigFiles(context, providerKey, providerConfig, sessionConfig, config);

            // 5. 构建并执行 Pipeline
            SandboxInitPipeline pipeline =
                    new SandboxInitPipeline(
                            List.of(
                                    new SandboxAcquirePhase(),
                                    new FileSystemReadyPhase(),
                                    new ConfigInjectionPhase(),
                                    new SidecarConnectPhase(),
                                    new CliReadyPhase()),
                            InitConfig.defaults());

            // 推送进度：沙箱获取中
            sendInitProgress(session, "sandbox-acquire", "executing", "正在获取沙箱实例...", 10, 5, 0);

            InitResult result = pipeline.execute(context);

            if (!session.isOpen()) {
                logger.warn("[Sandbox-Init] WebSocket 已关闭，放弃后续处理: userId={}", userId);
                return;
            }

            if (result.success()) {
                RuntimeAdapter adapter = context.getRuntimeAdapter();
                runtimeMap.put(session.getId(), adapter);

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
                                                logger.error(
                                                        "Error sending message to WebSocket", e);
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
                SandboxInfo sInfo = context.getSandboxInfo();
                String sandboxHost =
                        sInfo != null && sInfo.host() != null && !sInfo.host().isBlank()
                                ? sInfo.host()
                                : null;
                sendSandboxStatus(session, "ready", "沙箱环境已就绪", sandboxHost);
                sendInitProgress(session, "cli-ready", "completed", "沙箱环境已就绪", 100, 5, 5);
                logger.info(
                        "[Sandbox-Init] 已发送 sandbox/status: ready, sandboxHost={}", sandboxHost);

                // 通知前端实际使用的工作目录
                String cwd = cwdMap.get(session.getId());
                if (cwd != null) {
                    sendWorkspaceInfo(session, cwd);
                }

                // 回放缓存的消息
                replayPendingMessages(session, adapter);

            } else {
                // 发送详细错误信息
                sendSandboxError(session, result, sandboxType);
                try {
                    if (session.isOpen()) {
                        session.close(CloseStatus.SERVER_ERROR);
                    }
                } catch (IOException closeEx) {
                    logger.debug("Error closing WebSocket after sandbox init failure", closeEx);
                }
            }
        } catch (Exception e) {
            logger.error("[Sandbox-Init] 沙箱初始化异常: userId={}, error={}", userId, e.getMessage(), e);
            pendingMessageMap.remove(session.getId());
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
     * 准备配置文件，填充 InitContext.injectedConfigs。
     * 使用 CliConfigGenerator 生成配置内容，构建 ConfigFile 列表供 ConfigInjectionPhase 使用。
     */
    private void prepareConfigFiles(
            InitContext context,
            String providerKey,
            CliProviderConfig providerConfig,
            CliSessionConfig sessionConfig,
            RuntimeConfig config) {
        if (sessionConfig == null
                || providerConfig == null
                || !providerConfig.isSupportsCustomModel()) {
            return;
        }
        CliConfigGenerator generator = configGeneratorRegistry.get(providerKey);
        if (generator == null) {
            return;
        }

        List<ConfigFile> configFiles = new ArrayList<>();

        // 使用同一个临时目录生成所有配置，避免 settings.json 互相覆盖
        // （generateConfig 写入 modelProviders，generateMcpConfig 读取已有文件后合并 mcpServers）
        java.nio.file.Path sharedTempDir = null;
        try {
            sharedTempDir = java.nio.file.Files.createTempDirectory("sandbox-config-all-");

            // 1. 模型配置（先写入 settings.json，包含 modelProviders + env）
            if (sessionConfig.getCustomModelConfig() != null) {
                try {
                    Map<String, String> extraEnv =
                            generator.generateConfig(
                                    sharedTempDir.toString(), sessionConfig.getCustomModelConfig());
                    config.getEnv().putAll(extraEnv);

                    logger.info(
                            "[Sandbox-Config] 模型配置已准备: provider={}, baseUrl={}, modelId={}",
                            providerKey,
                            sessionConfig.getCustomModelConfig().getBaseUrl(),
                            sessionConfig.getCustomModelConfig().getModelId());
                } catch (Exception e) {
                    logger.error(
                            "[Sandbox-Config] 模型配置生成失败: provider={}, error={}",
                            providerKey,
                            e.getMessage(),
                            e);
                }
            }

            // 2. MCP 配置（读取已有 settings.json 后合并 mcpServers，不会丢失模型配置）
            if (sessionConfig.getMcpServers() != null
                    && !sessionConfig.getMcpServers().isEmpty()
                    && providerConfig.isSupportsMcp()) {
                try {
                    generator.generateMcpConfig(
                            sharedTempDir.toString(), sessionConfig.getMcpServers());

                    logger.info(
                            "[Sandbox-Config] MCP 配置已准备: provider={}, {} server(s)",
                            providerKey,
                            sessionConfig.getMcpServers().size());
                } catch (Exception e) {
                    logger.error(
                            "[Sandbox-Config] MCP 配置生成失败: provider={}, error={}",
                            providerKey,
                            e.getMessage(),
                            e);
                }
            }

            // 3. Skill 配置（写入独立的 .qwen/skills/xxx/SKILL.md 文件，不影响 settings.json）
            if (sessionConfig.getSkills() != null
                    && !sessionConfig.getSkills().isEmpty()
                    && providerConfig.isSupportsSkill()) {
                try {
                    generator.generateSkillConfig(
                            sharedTempDir.toString(), sessionConfig.getSkills());

                    logger.info(
                            "[Sandbox-Config] Skill 配置已准备: provider={}, {} skill(s)",
                            providerKey,
                            sessionConfig.getSkills().size());
                } catch (Exception e) {
                    logger.error(
                            "[Sandbox-Config] Skill 配置生成失败: provider={}, error={}",
                            providerKey,
                            e.getMessage(),
                            e);
                }
            }

            // 统一收集所有生成的文件
            final java.nio.file.Path tempDirRef = sharedTempDir;
            java.nio.file.Files.walk(sharedTempDir)
                    .filter(java.nio.file.Files::isRegularFile)
                    .forEach(
                            file -> {
                                try {
                                    String relativePath = tempDirRef.relativize(file).toString();
                                    String content = java.nio.file.Files.readString(file);
                                    String hash = sha256(content);
                                    ConfigFile.ConfigType type = inferConfigType(relativePath);
                                    configFiles.add(
                                            new ConfigFile(relativePath, content, hash, type));
                                } catch (IOException e) {
                                    logger.warn("[Sandbox-Config] 读取配置文件失败: {}", e.getMessage());
                                }
                            });

        } catch (Exception e) {
            logger.error(
                    "[Sandbox-Config] 配置准备失败: provider={}, error={}",
                    providerKey,
                    e.getMessage(),
                    e);
        } finally {
            if (sharedTempDir != null) {
                deleteDirectory(sharedTempDir);
            }
        }

        if (!configFiles.isEmpty()) {
            context.setInjectedConfigs(configFiles);
        }
    }

    /**
     * 根据文件相对路径推断配置类型。
     */
    private static ConfigFile.ConfigType inferConfigType(String relativePath) {
        if (relativePath.contains("skills") && relativePath.endsWith("SKILL.md")) {
            return ConfigFile.ConfigType.SKILL_CONFIG;
        }
        if (relativePath.endsWith("settings.json")) {
            // settings.json 现在包含合并后的模型+MCP配置，标记为 MODEL_SETTINGS
            return ConfigFile.ConfigType.MODEL_SETTINGS;
        }
        return ConfigFile.ConfigType.CUSTOM;
    }

    /**
     * 发送详细的沙箱初始化错误信息。
     * 包含 failedPhase、errorMessage、retryable、diagnostics。
     * <p>
     * Requirements: 7.4, 9.4, 9.5
     */
    private void sendSandboxError(
            WebSocketSession session, InitResult result, SandboxType sandboxType) {
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

            // 判断是否可重试：基于失败阶段的 retryPolicy
            boolean retryable = isPhaseRetryable(result.failedPhase());
            params.put("retryable", retryable);

            // diagnostics
            ObjectNode diagnostics = objectMapper.createObjectNode();
            List<String> completedPhases = new ArrayList<>();
            if (result.phaseDurations() != null) {
                for (Map.Entry<String, java.time.Duration> entry :
                        result.phaseDurations().entrySet()) {
                    if (!entry.getKey().equals(result.failedPhase())) {
                        completedPhases.add(entry.getKey());
                    }
                }
            }
            diagnostics.set("completedPhases", objectMapper.valueToTree(completedPhases));
            if (result.totalDuration() != null) {
                diagnostics.put(
                        "totalDuration",
                        String.format("%.1fs", result.totalDuration().toMillis() / 1000.0));
            }
            diagnostics.put("suggestion", retryable ? "请重试连接" : "请检查沙箱配置或联系管理员");
            params.set("diagnostics", diagnostics);

            notification.set("params", params);
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(notification)));
            }
            logger.error(
                    "[Sandbox-Init] 发送错误通知: failedPhase={}, retryable={}, message={}",
                    result.failedPhase(),
                    retryable,
                    result.errorMessage());
        } catch (Exception e) {
            logger.warn("Failed to send sandbox error notification: {}", e.getMessage());
        }
    }

    /**
     * 判断失败阶段是否可重试。
     */
    private boolean isPhaseRetryable(String phaseName) {
        if (phaseName == null) return false;
        return switch (phaseName) {
            case "filesystem-ready", "config-injection", "sidecar-connect" -> true;
            case "sandbox-acquire", "cli-ready" -> false;
            default -> false;
        };
    }

    /**
     * 向前端推送初始化进度消息。
     * <p>
     * Requirements: 7.5
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
     * 回放缓存的消息到 RuntimeAdapter。
     */
    private void replayPendingMessages(WebSocketSession session, RuntimeAdapter adapter) {
        java.util.Queue<String> pendingQueue = pendingMessageMap.remove(session.getId());
        if (pendingQueue != null) {
            int count = 0;
            String queued;
            while ((queued = pendingQueue.poll()) != null) {
                count++;
                logger.info(
                        "[Sandbox-Init] 回放缓存消息 #{}: {}",
                        count,
                        queued.length() > 200 ? queued.substring(0, 200) + "..." : queued);
                String rewritten = rewriteSessionNewCwd(session.getId(), queued);
                try {
                    adapter.send(rewritten);
                } catch (IOException e) {
                    logger.error(
                            "Error replaying queued message for session {}", session.getId(), e);
                }
            }
            logger.info("[Sandbox-Init] 回放完成，共 {} 条消息", count);
        } else {
            logger.warn("[Sandbox-Init] pendingQueue 为 null，没有缓存的消息可回放");
        }
    }

    /**
     * 计算 SHA-256 哈希值。
     */
    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    /**
     * 异步初始化 K8s Pod，通过 WebSocket 向前端推送进度通知。
     * <p>
     * 流程：
     * 1. 推送 "sandbox_creating" 通知
     * 2. 调用 acquirePod（可能耗时数分钟）
     * 3. 成功后创建 RuntimeAdapter，订阅 stdout，回放缓存消息
     * 4. 失败则推送错误通知并关闭连接
     * <p>
     * @deprecated 已被 {@link #initSandboxAsync} 替代，保留供回退使用
     */
    @Deprecated
    private void initK8sPodAsync(
            WebSocketSession session,
            String userId,
            String providerKey,
            RuntimeConfig config,
            CliProviderConfig providerConfig,
            CliSessionConfig sessionConfig) {
        try {
            logger.info("[K8s-Init] 开始异步 Pod 初始化: userId={}, session={}", userId, session.getId());
            sendSandboxStatus(session, "creating", "正在准备沙箱环境...");

            logger.info("[K8s-Init] 调用 acquirePod: userId={}", userId);
            PodInfo podInfo = podReuseManager.acquirePod(userId, config);
            logger.info(
                    "[K8s-Init] acquirePod 完成: pod={}, podIp={}, serviceIp={}, sidecarUri={},"
                            + " reused={}",
                    podInfo.podName(),
                    podInfo.podIp(),
                    podInfo.serviceIp(),
                    podInfo.sidecarWsUri(),
                    podInfo.reused());

            if (!session.isOpen()) {
                logger.warn("WebSocket already closed during pod init: userId={}", userId);
                return;
            }

            sendSandboxStatus(session, "connecting", "沙箱已就绪，正在连接...");

            logger.info("[K8s-Init] 创建 K8sRuntimeAdapter: sidecarUri={}", podInfo.sidecarWsUri());
            K8sRuntimeAdapter adapter =
                    (K8sRuntimeAdapter) runtimeFactory.create(RuntimeType.K8S, config);
            adapter.setReuseMode(true);

            // 先初始化 Pod 基本信息和文件系统适配器（不建立 WebSocket 连接）
            // startWithExistingPod 内部已将 fileSystem 创建提前到 connectToSidecarWebSocket 之前
            // 但我们需要在 CLI 启动前注入配置，所以先手动准备 adapter 再注入配置
            adapter.prepareForExistingPod(podInfo, config);

            // K8s 运行时：在 CLI 启动前，通过 PodFileSystemAdapter 注入配置文件到 Pod 内部
            // 必须在 connectToSidecarWebSocket 之前完成，因为 Sidecar 收到连接后会立即 spawn CLI
            injectConfigIntoPod(adapter, providerKey, providerConfig, sessionConfig, config);

            // 现在建立 WebSocket 连接，触发 Sidecar 启动 CLI 进程（此时配置文件已就位）
            adapter.connectAndStart();
            logger.info("[K8s-Init] Sidecar WebSocket 连接成功: pod={}", podInfo.podName());

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
                                        logger.info(
                                                "[K8s-Init] Sidecar 响应: {}",
                                                line.length() > 300
                                                        ? line.substring(0, 300) + "..."
                                                        : line);
                                        // 检测 CLI 返回的 JSON-RPC error 响应，记录有意义的告警日志
                                        try {
                                            JsonNode node = objectMapper.readTree(line);
                                            if (node.has("error") && node.has("jsonrpc")) {
                                                JsonNode error = node.get("error");
                                                logger.warn(
                                                        "[K8s-Init] CLI 返回错误响应: code={},"
                                                                + " message={}, pod={}",
                                                        error.path("code").asInt(),
                                                        error.path("message").asText(),
                                                        podInfo.podName());
                                            }
                                        } catch (Exception ignored) {
                                            // 非 JSON 内容，忽略
                                        }
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
            String sandboxHost =
                    podInfo.serviceIp() != null && !podInfo.serviceIp().isBlank()
                            ? podInfo.serviceIp()
                            : podInfo.podIp();
            sendSandboxStatus(session, "ready", "沙箱环境已就绪", sandboxHost);
            logger.info("[K8s-Init] 已发送 sandbox/status: ready, sandboxHost={}", sandboxHost);

            // 通知前端实际使用的工作目录
            String cwd = cwdMap.get(session.getId());
            if (cwd != null) {
                sendWorkspaceInfo(session, cwd);
            }

            // 回放缓存的消息
            java.util.Queue<String> pendingQueue = pendingMessageMap.remove(session.getId());
            if (pendingQueue != null) {
                int count = 0;
                String queued;
                while ((queued = pendingQueue.poll()) != null) {
                    count++;
                    logger.info(
                            "[K8s-Init] 回放缓存消息 #{}: {}",
                            count,
                            queued.length() > 200 ? queued.substring(0, 200) + "..." : queued);
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
                logger.info("[K8s-Init] 回放完成，共 {} 条消息", count);
            } else {
                logger.warn("[K8s-Init] pendingQueue 为 null，没有缓存的消息可回放");
            }

            // 短暂等待后检查 Sidecar 连接是否存活
            // Sidecar 可能在连接后立即关闭（进程未运行、端口不匹配等）
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            if (!adapter.isAlive()
                    || adapter.getStatus()
                            == com.alibaba.himarket.service.acp.runtime.RuntimeStatus.ERROR) {
                logger.error(
                        "[K8s-Init] Sidecar 连接已断开，Pod 内 Sidecar 可能未正常运行: pod={}",
                        podInfo.podName());
                sendSandboxStatus(session, "error", "沙箱 Sidecar 连接失败，请检查 Pod 内 Sidecar 进程是否正常运行");
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
     * K8s 运行时：Pod Ready 后通过 PodFileSystemAdapter 将配置文件写入 Pod 内部。
     * <p>
     * 解决原有逻辑中在后端 JVM 本地写 /workspace 导致 Read-only file system 的问题。
     * 配置生成仍复用 CliConfigGenerator 的内存逻辑，但最终通过 kubectl exec 写入 Pod。
     */
    private void injectConfigIntoPod(
            K8sRuntimeAdapter adapter,
            String providerKey,
            CliProviderConfig providerConfig,
            CliSessionConfig sessionConfig,
            RuntimeConfig config) {
        if (sessionConfig == null
                || providerConfig == null
                || !providerConfig.isSupportsCustomModel()) {
            return;
        }
        CliConfigGenerator generator = configGeneratorRegistry.get(providerKey);
        if (generator == null) {
            return;
        }

        var fileSystem = adapter.getFileSystem();
        if (fileSystem == null) {
            logger.warn(
                    "[K8s-Config] PodFileSystemAdapter not available, skipping config injection");
            return;
        }

        String cwd = "/workspace";

        // 使用同一个临时目录生成所有配置，避免 settings.json 互相覆盖
        java.nio.file.Path sharedTempDir = null;
        try {
            sharedTempDir = java.nio.file.Files.createTempDirectory("k8s-config-all-");

            // 1. 模型配置注入
            if (sessionConfig.getCustomModelConfig() != null) {
                try {
                    Map<String, String> extraEnv =
                            generator.generateConfig(
                                    sharedTempDir.toString(), sessionConfig.getCustomModelConfig());
                    config.getEnv().putAll(extraEnv);

                    logger.info(
                            "[K8s-Config] Custom model config generated for provider '{}': "
                                    + "baseUrl={}, modelId={}",
                            providerKey,
                            sessionConfig.getCustomModelConfig().getBaseUrl(),
                            sessionConfig.getCustomModelConfig().getModelId());
                } catch (Exception e) {
                    logger.error(
                            "[K8s-Config] Failed to generate custom model config for provider '{}':"
                                    + " {}",
                            providerKey,
                            e.getMessage(),
                            e);
                }
            }

            // 2. MCP 配置注入（读取已有 settings.json 后合并，不会丢失模型配置）
            if (sessionConfig.getMcpServers() != null
                    && !sessionConfig.getMcpServers().isEmpty()
                    && providerConfig.isSupportsMcp()) {
                try {
                    generator.generateMcpConfig(
                            sharedTempDir.toString(), sessionConfig.getMcpServers());
                    logger.info(
                            "[K8s-Config] MCP config generated for provider '{}': {}"
                                    + " server(s)",
                            providerKey,
                            sessionConfig.getMcpServers().size());
                } catch (Exception e) {
                    logger.error(
                            "[K8s-Config] Failed to generate MCP config for provider '{}': {}",
                            providerKey,
                            e.getMessage(),
                            e);
                }
            }

            // 3. Skill 配置注入
            if (sessionConfig.getSkills() != null
                    && !sessionConfig.getSkills().isEmpty()
                    && providerConfig.isSupportsSkill()) {
                try {
                    generator.generateSkillConfig(
                            sharedTempDir.toString(), sessionConfig.getSkills());
                    logger.info(
                            "[K8s-Config] Skill config generated for provider '{}': {}"
                                    + " skill(s)",
                            providerKey,
                            sessionConfig.getSkills().size());
                } catch (Exception e) {
                    logger.error(
                            "[K8s-Config] Failed to generate Skill config for provider '{}': {}",
                            providerKey,
                            e.getMessage(),
                            e);
                }
            }

            // 统一将临时目录中所有配置文件写入 Pod
            copyTempConfigToPod(sharedTempDir, cwd, fileSystem);
            logger.info(
                    "[K8s-Config] All configs injected into pod for provider '{}'", providerKey);

        } catch (Exception e) {
            logger.error(
                    "[K8s-Config] Failed to inject configs for provider '{}': {}",
                    providerKey,
                    e.getMessage(),
                    e);
        } finally {
            if (sharedTempDir != null) {
                deleteDirectory(sharedTempDir);
            }
        }
    }

    /**
     * 将临时目录中生成的配置文件递归写入 Pod 内部。
     *
     * @param tempDir  本地临时目录（CliConfigGenerator 生成的配置文件所在目录）
     * @param podCwd   Pod 内的目标根目录（如 /workspace）
     * @param fileSystem PodFileSystemAdapter 实例
     */
    private void copyTempConfigToPod(
            java.nio.file.Path tempDir,
            String podCwd,
            com.alibaba.himarket.service.acp.runtime.FileSystemAdapter fileSystem) {
        try {
            java.nio.file.Files.walk(tempDir)
                    .filter(java.nio.file.Files::isRegularFile)
                    .forEach(
                            file -> {
                                try {
                                    // 计算相对路径：tempDir 下的相对路径即为 Pod 内 cwd 下的相对路径
                                    String relativePath = tempDir.relativize(file).toString();
                                    String content = java.nio.file.Files.readString(file);
                                    // PodFileSystemAdapter.writeFile 接受相对于 basePath(/workspace) 的路径
                                    fileSystem.writeFile(relativePath, content);
                                    logger.debug(
                                            "[K8s-Config] Written to pod: {}/{}",
                                            podCwd,
                                            relativePath);
                                } catch (Exception e) {
                                    logger.warn(
                                            "[K8s-Config] Failed to write file to pod: {}",
                                            e.getMessage());
                                }
                            });
        } catch (IOException e) {
            logger.warn("[K8s-Config] Failed to walk temp directory: {}", e.getMessage());
        }
    }

    /**
     * 递归删除临时目录。
     */
    private void deleteDirectory(java.nio.file.Path dir) {
        try {
            java.nio.file.Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    java.nio.file.Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                }
                            });
        } catch (IOException ignored) {
        }
    }

    /**
     * 向前端推送沙箱状态通知（JSON-RPC notification）。
     * <p>
     * 格式：{"jsonrpc":"2.0","method":"sandbox/status","params":{"status":"...","message":"..."}}
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
     * <p>
     * 格式：{"jsonrpc":"2.0","method":"workspace/info","params":{"cwd":"..."}}
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
            config.setK8sConfigId(k8sConfigService.getDefaultConfigId());
            logger.info(
                    "K8s runtime: using cluster configId={}, image={}",
                    config.getK8sConfigId(),
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
