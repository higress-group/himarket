# 设计文档：HiCoding 架构重构

## 概述

本设计对 HiCoding 模块进行架构重构，核心目标是：拆分 AcpWebSocketHandler 的臃肿职责、提取配置解析和配置文件生成为独立服务、消除 SandboxProvider 实现间的代码重复、为 OpenSandbox 对接预留干净的扩展点。

重构遵循"提取 → 委托 → 清理"的渐进模式：先提取独立组件，再将 Handler 中的逻辑委托给新组件，最后清理旧代码。每一步都保持系统可运行。

### 当前架构 vs 目标架构

| 维度 | 当前架构 | 目标架构 |
|------|---------|---------|
| 配置解析 | 嵌入在 AcpWebSocketHandler.prepareConfigFiles() 中（230+ 行） | 独立的 SessionConfigResolver 服务 |
| 配置文件生成 | 嵌入在 prepareConfigFiles() 中，与解析逻辑混合 | 独立的 ConfigFileBuilder |
| Handler 职责 | 配置解析 + 沙箱初始化 + 消息转发 + 错误处理 + 资源管理（1000+ 行） | 仅作为 WebSocket 事件入口（< 200 行） |
| HTTP 客户端 | K8s/Local Provider 各自实现，代码重复 | 统一的 SandboxHttpClient |
| 错误处理 | 粗粒度，缺少错误码分类 | InitErrorCode 枚举 + 结构化错误响应 |
| 配置在 Pipeline 中的位置 | Pipeline 外部准备 ConfigFile，Pipeline 只负责写入 | Pipeline 内部完成解析 → 生成 → 写入全流程 |

## 架构

### 目标架构总览

```
前端 WebSocket 连接
    ↓
AcpHandshakeInterceptor（认证 + 参数提取，不变）
    ↓
AcpWebSocketHandler（轻量入口，< 200 行）
    ├── 委托 AcpSessionInitializer（初始化编排）
    │   ├── SessionConfigResolver（配置解析）
    │   ├── ConfigFileBuilder（配置文件生成）
    │   └── SandboxInitPipeline（沙箱初始化流水线）
    │       ├── SandboxAcquirePhase
    │       ├── FileSystemReadyPhase
    │       ├── ConfigInjectionPhase（内部调用 ConfigFileBuilder）
    │       ├── SidecarConnectPhase
    │       └── CliReadyPhase
    ├── 委托 AcpMessageRouter（消息转发）
    └── 委托 AcpConnectionManager（连接/资源管理）

SandboxProvider 实现层：
    ├── LocalSandboxProvider ──┐
    └── K8sSandboxProvider  ──┴── 委托 SandboxHttpClient（统一 HTTP 调用）
```

### 配置传递链路对比

当前链路（7+ 次转换）：
```
前端 → URL 编码 cliSessionConfig → AcpHandshakeInterceptor 解码 →
attributes → AcpWebSocketHandler 读取 → prepareConfigFiles() 内部：
  查数据库解析模型 → 查数据库解析 MCP → 下载 Skill 文件 →
  构建 ResolvedSessionConfig → 调用 CliConfigGenerator 写临时文件 →
  遍历临时目录收集 ConfigFile → 设置到 InitContext →
ConfigInjectionPhase 写入沙箱
```

目标链路（3 次转换）：
```
前端 → AcpHandshakeInterceptor → AcpWebSocketHandler →
AcpSessionInitializer：
  1. SessionConfigResolver.resolve(cliSessionConfig) → ResolvedSessionConfig
  2. 设置到 InitContext.resolvedSessionConfig
  3. Pipeline 执行 → ConfigInjectionPhase 内部：
     ConfigFileBuilder.build(resolvedConfig) → List<ConfigFile> → 写入沙箱
```

## 组件与接口

### 1. SessionConfigResolver — 会话配置解析服务

从 AcpWebSocketHandler.prepareConfigFiles() 中提取配置解析逻辑。

```java
/**
 * 会话配置解析服务。
 * 将前端传入的标识符（CliSessionConfig）解析为完整的配置信息（ResolvedSessionConfig）。
 *
 * 从 AcpWebSocketHandler.prepareConfigFiles() 中提取，使配置解析可独立测试。
 * 封装了模型配置查询、MCP 连接信息解析、Skill 文件下载等数据库/远程调用。
 */
@Service
public class SessionConfigResolver {

    private final ModelConfigResolver modelConfigResolver;
    private final McpConfigResolver mcpConfigResolver;
    private final SkillPackageService skillPackageService;

    /**
     * 解析会话配置。
     *
     * @param sessionConfig 前端传入的配置（仅含 productId 等标识符）
     * @param userId 当前用户 ID（用于设置 Security 上下文）
     * @return 已解析的完整配置
     */
    public ResolvedSessionConfig resolve(CliSessionConfig sessionConfig, String userId) {
        ResolvedSessionConfig resolved = new ResolvedSessionConfig();
        resolved.setAuthToken(sessionConfig.getAuthToken());

        // 1. 模型配置解析
        resolveModelConfig(sessionConfig, userId, resolved);

        // 2. MCP 配置解析
        resolveMcpConfig(sessionConfig, resolved);

        // 3. Skill 文件下载
        resolveSkillConfig(sessionConfig, resolved);

        return resolved;
    }

    private void resolveModelConfig(CliSessionConfig config, String userId,
                                     ResolvedSessionConfig resolved) {
        if (config.getModelProductId() == null || config.getModelProductId().isBlank()) {
            return;
        }
        try {
            // 设置 Security 上下文
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()));

            CustomModelConfig modelConfig = modelConfigResolver.resolve(config.getModelProductId());
            resolved.setCustomModelConfig(modelConfig);
        } catch (Exception e) {
            logger.error("[SessionConfigResolver] 模型配置解析失败: productId={}, error={}",
                config.getModelProductId(), e.getMessage(), e);
            // 不阻断流程，customModelConfig 保持 null
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void resolveMcpConfig(CliSessionConfig config, ResolvedSessionConfig resolved) {
        // 解析 MCP Server 连接信息，失败项跳过
    }

    private void resolveSkillConfig(CliSessionConfig config, ResolvedSessionConfig resolved) {
        // 下载 Skill 文件内容，失败项跳过
    }
}
```

### 2. ConfigFileBuilder — 配置文件构建器

从 AcpWebSocketHandler.prepareConfigFiles() 中提取配置文件生成逻辑。

```java
/**
 * 配置文件构建器。
 * 将 ResolvedSessionConfig 转换为 ConfigFile 列表。
 *
 * 封装了 CliConfigGenerator 调用、临时目录管理、文件收集等逻辑。
 * 从 AcpWebSocketHandler.prepareConfigFiles() 中提取。
 */
@Component
public class ConfigFileBuilder {

    private final CliConfigGeneratorRegistry generatorRegistry;

    /**
     * 构建配置文件列表。
     *
     * @param resolved 已解析的会话配置
     * @param providerKey CLI 提供者标识
     * @param providerConfig CLI 提供者配置
     * @param runtimeConfig 运行时配置（额外环境变量会被合并到此对象）
     * @return 配置文件列表（含路径、内容、哈希）
     */
    public List<ConfigFile> build(ResolvedSessionConfig resolved, String providerKey,
                                   CliProviderConfig providerConfig, RuntimeConfig runtimeConfig) {
        CliConfigGenerator generator = generatorRegistry.get(providerKey);
        if (generator == null) {
            logger.warn("[ConfigFileBuilder] 未找到 CliConfigGenerator: {}", providerKey);
            return Collections.emptyList();
        }

        List<ConfigFile> configFiles = new ArrayList<>();
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("sandbox-config-");

            // 1. 模型配置
            generateModelConfig(generator, tempDir, resolved, runtimeConfig);

            // 2. MCP 配置
            if (providerConfig.isSupportsMcp()) {
                generateMcpConfig(generator, tempDir, resolved);
            }

            // 3. Skill 配置
            if (providerConfig.isSupportsSkill()) {
                generateSkillConfig(generator, tempDir, resolved);
            }

            // 4. 收集所有生成的文件
            configFiles = collectConfigFiles(tempDir);
        } catch (Exception e) {
            logger.error("[ConfigFileBuilder] 配置文件构建失败: {}", e.getMessage(), e);
        } finally {
            deleteTempDir(tempDir);
        }
        return configFiles;
    }

    private List<ConfigFile> collectConfigFiles(Path tempDir) throws IOException {
        // 遍历临时目录，计算 SHA-256，推断 ConfigType
    }
}
```

### 3. SandboxHttpClient — 统一 Sidecar HTTP 客户端

提取 K8sSandboxProvider 和 LocalSandboxProvider 中重复的 HTTP 调用逻辑。

```java
/**
 * 沙箱 Sidecar HTTP 客户端。
 * 统一封装对 Sidecar HTTP API 的调用，消除 K8s/Local Provider 中的代码重复。
 *
 * 设计为可复用组件，未来 OpenSandboxProvider 也可使用
 * （OpenSandbox 的 execd 提供类似的 HTTP API）。
 */
@Component
public class SandboxHttpClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SandboxHttpClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 写入文件到沙箱。
     * 调用 Sidecar POST /files/write。
     */
    public void writeFile(String baseUrl, String sandboxId,
                          String relativePath, String content) throws IOException {
        String url = baseUrl + "/files/write";
        String body = objectMapper.writeValueAsString(
            Map.of("path", relativePath, "content", content));
        HttpResponse<String> response = doPost(url, body, DEFAULT_TIMEOUT);
        if (response.statusCode() != 200) {
            throw new IOException(
                "Sidecar writeFile 失败 (sandbox: " + sandboxId + "): " + response.body());
        }
    }

    /**
     * 从沙箱读取文件。
     * 调用 Sidecar POST /files/read。
     */
    public String readFile(String baseUrl, String sandboxId,
                           String relativePath) throws IOException {
        String url = baseUrl + "/files/read";
        String body = objectMapper.writeValueAsString(Map.of("path", relativePath));
        HttpResponse<String> response = doPost(url, body, DEFAULT_TIMEOUT);
        if (response.statusCode() != 200) {
            throw new IOException(
                "Sidecar readFile 失败 (sandbox: " + sandboxId + "): " + response.body());
        }
        return objectMapper.readTree(response.body()).get("content").asText();
    }

    /**
     * 健康检查。
     * 调用 Sidecar GET /health。
     */
    public boolean healthCheck(String baseUrl) {
        try {
            HttpResponse<String> response = doGet(baseUrl + "/health", Duration.ofSeconds(5));
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解压 tar.gz 到沙箱。
     * 调用 Sidecar POST /files/extract。
     */
    public int extractArchive(String baseUrl, String sandboxId,
                              byte[] tarGzBytes) throws IOException {
        // 统一的 extract 逻辑
    }

    // 内部 HTTP 调用方法，统一处理超时和中断
    private HttpResponse<String> doPost(String url, String body, Duration timeout)
            throws IOException {
        try {
            return httpClient.send(
                HttpRequest.newBuilder(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP 请求被中断: " + url, e);
        }
    }

    private HttpResponse<String> doGet(String url, Duration timeout) throws IOException {
        try {
            return httpClient.send(
                HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(timeout)
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP 请求被中断: " + url, e);
        }
    }
}
```

### 4. AcpSessionInitializer — 会话初始化器

从 AcpWebSocketHandler.initSandboxAsync() 中提取初始化编排逻辑。

```java
/**
 * 会话初始化器。
 * 编排沙箱初始化的完整流程：配置解析 → 配置文件生成 → Pipeline 执行 → 结果处理。
 *
 * 从 AcpWebSocketHandler.initSandboxAsync() 中提取。
 */
@Component
public class AcpSessionInitializer {

    private final SessionConfigResolver configResolver;
    private final ConfigFileBuilder configFileBuilder;
    private final SandboxProviderRegistry providerRegistry;

    /**
     * 初始化结果。
     */
    public record InitializationResult(
        boolean success,
        RuntimeAdapter adapter,
        SandboxInfo sandboxInfo,
        InitErrorCode errorCode,
        String errorMessage,
        String failedPhase,
        boolean retryable,
        Duration totalDuration
    ) {}

    /**
     * 执行沙箱初始化。
     *
     * @param userId 用户 ID
     * @param providerKey CLI 提供者标识
     * @param providerConfig CLI 提供者配置
     * @param runtimeConfig 运行时配置
     * @param sessionConfig 前端传入的会话配置
     * @param sandboxType 沙箱类型
     * @param frontendSession 前端 WebSocket session（用于推送进度）
     * @return 初始化结果
     */
    public InitializationResult initialize(
            String userId,
            String providerKey,
            CliProviderConfig providerConfig,
            RuntimeConfig runtimeConfig,
            CliSessionConfig sessionConfig,
            SandboxType sandboxType,
            WebSocketSession frontendSession) {

        // 1. 获取 Provider
        SandboxProvider provider = providerRegistry.getProvider(sandboxType);

        // 2. 注入 authToken
        injectAuthToken(sessionConfig, providerConfig, runtimeConfig);

        // 3. 解析配置
        ResolvedSessionConfig resolved = null;
        if (sessionConfig != null && providerConfig.isSupportsCustomModel()) {
            resolved = configResolver.resolve(sessionConfig, userId);
        }

        // 4. 构建 SandboxConfig
        SandboxConfig sandboxConfig = buildSandboxConfig(userId, sandboxType, runtimeConfig);

        // 5. 构建 InitContext（含 resolvedSessionConfig）
        InitContext context = new InitContext(
            provider, userId, sandboxConfig, runtimeConfig,
            providerConfig, sessionConfig, frontendSession);
        context.setResolvedSessionConfig(resolved);

        // 6. 执行 Pipeline
        SandboxInitPipeline pipeline = buildPipeline();
        InitResult result = pipeline.execute(context);

        // 7. 构建结果
        return toInitializationResult(result, context);
    }
}
```

### 5. AcpMessageRouter — 消息路由器

```java
/**
 * 前端 ↔ CLI 双向消息路由器。
 * 从 AcpWebSocketHandler 中提取消息转发逻辑。
 */
@Component
public class AcpMessageRouter {

    /**
     * 订阅 CLI stdout 并转发到前端 WebSocket。
     * @return Disposable 订阅句柄（用于取消订阅）
     */
    public Disposable subscribeAndForward(RuntimeAdapter adapter, WebSocketSession session) {
        return adapter.stdout().subscribe(
            line -> sendToFrontend(session, line),
            error -> logger.error("Stdout stream error: session={}", session.getId(), error),
            () -> closeSession(session)
        );
    }

    /**
     * 将前端消息转发到 CLI。
     */
    public void forwardToCliAgent(RuntimeAdapter adapter, String message) throws IOException {
        adapter.send(message);
    }

    private void sendToFrontend(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        } catch (IOException e) {
            logger.error("Error sending message to WebSocket: session={}", session.getId(), e);
        }
    }
}
```

### 6. AcpConnectionManager — 连接管理器

```java
/**
 * WebSocket 连接状态和资源管理器。
 * 从 AcpWebSocketHandler 中提取连接级别的状态管理和资源清理逻辑。
 */
@Component
public class AcpConnectionManager {

    // 连接级别的状态映射
    private final ConcurrentHashMap<String, RuntimeAdapter> runtimeMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Disposable> subscriptionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> cwdMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userIdMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sandboxModeMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Queue<String>> pendingMessageMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DeferredInitParams> deferredInitMap = new ConcurrentHashMap<>();

    /**
     * 注册新连接。
     */
    public void registerConnection(String sessionId, String userId, String cwd, String sandboxMode) {
        cwdMap.put(sessionId, cwd);
        userIdMap.put(sessionId, userId);
        sandboxModeMap.put(sessionId, sandboxMode != null ? sandboxMode : "");
        pendingMessageMap.put(sessionId, new ConcurrentLinkedQueue<>());
    }

    /**
     * 注册初始化成功后的运行时资源。
     */
    public void registerRuntime(String sessionId, RuntimeAdapter adapter, Disposable subscription) {
        runtimeMap.put(sessionId, adapter);
        subscriptionMap.put(sessionId, subscription);
    }

    /**
     * 清理连接资源。
     */
    public void cleanup(String sessionId) {
        Disposable sub = subscriptionMap.remove(sessionId);
        if (sub != null && !sub.isDisposed()) sub.dispose();

        RuntimeAdapter adapter = runtimeMap.remove(sessionId);
        if (adapter != null) adapter.close();

        cwdMap.remove(sessionId);
        userIdMap.remove(sessionId);
        sandboxModeMap.remove(sessionId);
        pendingMessageMap.remove(sessionId);
        deferredInitMap.remove(sessionId);
    }

    // Getters
    public RuntimeAdapter getRuntime(String sessionId) { return runtimeMap.get(sessionId); }
    public String getUserId(String sessionId) { return userIdMap.get(sessionId); }
    public String getCwd(String sessionId) { return cwdMap.get(sessionId); }
    public Queue<String> getPendingMessages(String sessionId) { return pendingMessageMap.get(sessionId); }
    public DeferredInitParams getDeferredInit(String sessionId) { return deferredInitMap.remove(sessionId); }
    public void setDeferredInit(String sessionId, DeferredInitParams params) { deferredInitMap.put(sessionId, params); }
}
```

### 7. InitErrorCode — 错误码枚举

```java
/**
 * 初始化错误码枚举。
 * 提供细粒度的错误分类，便于前端展示和运维排查。
 */
public enum InitErrorCode {

    SANDBOX_ACQUIRE_FAILED("SANDBOX_ACQUIRE_FAILED", "沙箱获取失败"),
    FILESYSTEM_NOT_READY("FILESYSTEM_NOT_READY", "文件系统未就绪"),
    CONFIG_RESOLVE_FAILED("CONFIG_RESOLVE_FAILED", "配置解析失败"),
    CONFIG_INJECTION_FAILED("CONFIG_INJECTION_FAILED", "配置注入失败"),
    SIDECAR_CONNECT_FAILED("SIDECAR_CONNECT_FAILED", "Sidecar 连接失败"),
    CLI_NOT_READY("CLI_NOT_READY", "CLI 工具未就绪"),
    PIPELINE_TIMEOUT("PIPELINE_TIMEOUT", "初始化超时"),
    UNKNOWN_ERROR("UNKNOWN_ERROR", "未知错误");

    private final String code;
    private final String defaultMessage;

    /**
     * 根据 Pipeline 失败阶段名称映射到错误码。
     */
    public static InitErrorCode fromPhaseName(String phaseName) {
        if (phaseName == null) return UNKNOWN_ERROR;
        return switch (phaseName) {
            case "sandbox-acquire" -> SANDBOX_ACQUIRE_FAILED;
            case "filesystem-ready" -> FILESYSTEM_NOT_READY;
            case "config-injection" -> CONFIG_INJECTION_FAILED;
            case "sidecar-connect" -> SIDECAR_CONNECT_FAILED;
            case "cli-ready" -> CLI_NOT_READY;
            default -> UNKNOWN_ERROR;
        };
    }
}
```

### 8. 重构后的 AcpWebSocketHandler（目标 < 200 行）

```java
/**
 * WebSocket 事件入口。
 * 仅负责接收事件并委托给专门的组件处理。
 *
 * 职责：
 * - afterConnectionEstablished → 委托 AcpConnectionManager + AcpSessionInitializer
 * - handleTextMessage → 委托 AcpMessageRouter
 * - afterConnectionClosed → 委托 AcpConnectionManager.cleanup()
 */
@Component
public class AcpWebSocketHandler extends TextWebSocketHandler {

    private final AcpSessionInitializer sessionInitializer;
    private final AcpMessageRouter messageRouter;
    private final AcpConnectionManager connectionManager;
    private final AcpProperties properties;
    private final ExecutorService initExecutor;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get("userId");
        // ... 参数提取（保持不变）

        // 注册连接
        connectionManager.registerConnection(sessionId, userId, cwd, sandboxMode);

        // 提交异步初始化
        initExecutor.submit(() -> doInitialize(session, userId, providerKey, ...));
    }

    private void doInitialize(WebSocketSession session, ...) {
        var result = sessionInitializer.initialize(userId, providerKey, ...);

        if (result.success()) {
            // 注册运行时
            Disposable sub = messageRouter.subscribeAndForward(result.adapter(), session);
            connectionManager.registerRuntime(session.getId(), result.adapter(), sub);
            sendStatus(session, "ready", ...);
        } else {
            sendError(session, result.errorCode(), result.errorMessage(), ...);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        RuntimeAdapter adapter = connectionManager.getRuntime(session.getId());
        if (adapter != null) {
            messageRouter.forwardToCliAgent(adapter, message.getPayload());
        } else {
            // 缓存消息（等待初始化完成）
            connectionManager.getPendingMessages(session.getId()).offer(message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connectionManager.cleanup(session.getId());
    }
}
```

### 9. 增强的 ConfigInjectionPhase

```java
/**
 * 配置注入阶段（增强版）。
 * 内部完成配置文件生成 + 写入沙箱的完整流程。
 *
 * 改动：从 InitContext.resolvedSessionConfig 获取已解析配置，
 * 内部调用 ConfigFileBuilder 生成 ConfigFile 列表，
 * 不再依赖外部预先填充 injectedConfigs。
 */
public class ConfigInjectionPhase implements InitPhase {

    private final ConfigFileBuilder configFileBuilder;

    @Override
    public void execute(InitContext context) throws InitPhaseException {
        // 1. 如果 injectedConfigs 已被外部填充（向后兼容），直接使用
        List<ConfigFile> configs = context.getInjectedConfigs();

        // 2. 否则，从 resolvedSessionConfig 生成
        if ((configs == null || configs.isEmpty())
                && context.getResolvedSessionConfig() != null) {
            configs = configFileBuilder.build(
                context.getResolvedSessionConfig(),
                context.getProviderConfig().getKey(),
                context.getProviderConfig(),
                context.getRuntimeConfig());
            context.setInjectedConfigs(configs);
        }

        if (configs == null || configs.isEmpty()) {
            return;
        }

        // 3. 写入沙箱（现有逻辑不变：tar.gz 批量注入 + 降级逐个写入 + 抽样验证）
        injectToSandbox(context.getProvider(), context.getSandboxInfo(), configs);
    }
}
```

## 迁移策略

重构分 4 个阶段，每个阶段结束后系统可正常运行：

### 阶段一：提取独立组件（不改变 Handler）
- 创建 SessionConfigResolver、ConfigFileBuilder、SandboxHttpClient、InitErrorCode
- 编写单元测试验证新组件
- Handler 暂不改动，新旧代码并存

### 阶段二：重构 SandboxProvider（消除重复）
- K8sSandboxProvider 和 LocalSandboxProvider 委托 SandboxHttpClient
- 删除两个 Provider 中重复的 HTTP 调用代码

### 阶段三：拆分 Handler（核心重构）
- 创建 AcpSessionInitializer、AcpMessageRouter、AcpConnectionManager
- 将 Handler 逻辑逐步委托给新组件
- 增强 InitContext（新增 resolvedSessionConfig）
- 增强 ConfigInjectionPhase（内部调用 ConfigFileBuilder）
- 删除 Handler 中的 prepareConfigFiles()

### 阶段四：清理与验证
- 确保 Handler < 200 行
- 端到端验证（本地 + K8s）
- 添加 OpenSandbox 扩展点注释
