# 实现计划：HiCoding 架构重构

## 概述

按照"提取 → 委托 → 清理"的渐进模式分四个阶段实施。每个阶段结束后系统可正常运行，确保增量验证。

## 任务

### 阶段一：提取独立组件（不改变 Handler）

- [x] 1. 创建 SandboxHttpClient 统一 HTTP 客户端
  - [x] 1.1 创建 SandboxHttpClient 类
    - 在 `himarket-server/.../service/acp/runtime/` 下创建 `SandboxHttpClient.java`，标注 `@Component`
    - 注入 `ObjectMapper`，内部创建 `java.net.http.HttpClient`（connectTimeout=10s, HTTP/1.1）
    - 实现 `writeFile(String baseUrl, String sandboxId, String relativePath, String content)` 方法：POST /files/write，非 200 抛 IOException（含 sandboxId）
    - 实现 `readFile(String baseUrl, String sandboxId, String relativePath)` 方法：POST /files/read，解析 response.body 中的 content 字段
    - 实现 `healthCheck(String baseUrl)` 方法：GET /health，超时 5s，异常返回 false
    - 实现 `extractArchive(String baseUrl, String sandboxId, byte[] tarGzBytes)` 方法：POST /files/extract，Content-Type=application/gzip，超时 30s
    - 实现 `healthCheckWithLog(String baseUrl, String sandboxId)` 方法：同 healthCheck 但在失败时记录 warn 日志（含 sandboxId、host、status、body）
    - 提取私有方法 `doPost(url, body, timeout)` 和 `doGet(url, timeout)` 统一处理 InterruptedException（恢复中断标志 + 包装为 IOException）
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_
  - [x] 1.2 编写 SandboxHttpClient 单元测试
    - 使用 MockWebServer（okhttp3）或内嵌 HTTP 服务器模拟 Sidecar 响应
    - 测试 writeFile 正常写入、非 200 抛 IOException（含 sandboxId）
    - 测试 readFile 正常读取、解析 content 字段
    - 测试 healthCheck 正常返回 true、超时返回 false、异常返回 false
    - 测试 extractArchive 正常解压、非 200 抛 IOException
    - 测试 InterruptedException 处理（恢复中断标志）
    - _Requirements: 3.1-3.6_

- [x] 2. 创建 SessionConfigResolver 配置解析服务
  - [x] 2.1 创建 SessionConfigResolver 类
    - 在 `himarket-server/.../service/acp/` 下创建 `SessionConfigResolver.java`，标注 `@Service`
    - 注入 `ModelConfigResolver`、`McpConfigResolver`、`SkillPackageService`
    - 实现 `resolve(CliSessionConfig sessionConfig, String userId)` 方法，返回 `ResolvedSessionConfig`
    - 从 `AcpWebSocketHandler.prepareConfigFiles()` 中提取模型配置解析逻辑到私有方法 `resolveModelConfig()`：设置 SecurityContext → 调用 modelConfigResolver.resolve() → 清理 SecurityContext → 异常时 log.error 但不抛出
    - 从 `prepareConfigFiles()` 中提取 MCP 配置解析逻辑到私有方法 `resolveMcpConfig()`：调用 mcpConfigResolver.resolve() → 异常时 log.error 并跳过
    - 从 `prepareConfigFiles()` 中提取 Skill 文件下载逻辑到私有方法 `resolveSkillConfig()`：遍历 skills → 调用 skillPackageService.getAllFiles() → 异常时 log.error 并跳过失败项
    - 保持与原 prepareConfigFiles() 完全相同的日志格式和错误处理行为
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_
  - [x] 2.2 编写 SessionConfigResolver 单元测试
    - Mock ModelConfigResolver、McpConfigResolver、SkillPackageService
    - 测试正常解析：modelProductId → CustomModelConfig、mcpServers → ResolvedMcpEntry 列表、skills → ResolvedSkillEntry 列表
    - 测试模型解析失败时 customModelConfig 为 null，不影响 MCP 和 Skill 解析
    - 测试 MCP 解析失败时跳过，不影响 Skill 解析
    - 测试 Skill 单项下载失败时跳过该项，继续处理其余项
    - 测试 modelProductId 为 null/blank 时跳过模型解析
    - 测试 SecurityContext 在正常和异常路径下都被清理
    - _Requirements: 1.1-1.7_

- [x] 3. 创建 ConfigFileBuilder 配置文件构建器
  - [x] 3.1 创建 ConfigFileBuilder 类
    - 在 `himarket-server/.../service/acp/` 下创建 `ConfigFileBuilder.java`，标注 `@Component`
    - 注入 `CliConfigGeneratorRegistry`（现有的 Map<String, CliConfigGenerator> 注册表）
    - 实现 `build(ResolvedSessionConfig resolved, String providerKey, CliProviderConfig providerConfig, RuntimeConfig runtimeConfig)` 方法，返回 `List<ConfigFile>`
    - 从 `AcpWebSocketHandler.prepareConfigFiles()` 中提取以下逻辑：
      - 创建临时目录 `Files.createTempDirectory("sandbox-config-")`
      - 调用 `generator.generateConfig()` 并合并返回的 extraEnv 到 runtimeConfig.env
      - 条件调用 `generator.generateMcpConfig()` 和 `generator.generateSkillConfig()`
      - 遍历临时目录收集文件、计算 SHA-256、推断 ConfigType
      - finally 块中删除临时目录
    - 从 `AcpWebSocketHandler` 中提取 `inferConfigType()` 和 `sha256()` 静态方法到 ConfigFileBuilder
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_
  - [x] 3.2 编写 ConfigFileBuilder 单元测试
    - Mock CliConfigGeneratorRegistry 和 CliConfigGenerator
    - 测试正常构建：generator 写入临时文件 → 收集为 ConfigFile 列表（含正确的 relativePath、content、hash、type）
    - 测试 generator 返回 extraEnv 时合并到 runtimeConfig.env
    - 测试 providerConfig.isSupportsMcp()=false 时不调用 generateMcpConfig
    - 测试 providerConfig.isSupportsSkill()=false 时不调用 generateSkillConfig
    - 测试 generator 为 null 时返回空列表
    - 测试临时目录在正常和异常路径下都被清理
    - _Requirements: 2.1-2.7_

- [x] 4. 创建 InitErrorCode 错误码枚举
  - [x] 4.1 创建 InitErrorCode 枚举
    - 在 `himarket-server/.../service/acp/runtime/` 下创建 `InitErrorCode.java`
    - 定义枚举值：SANDBOX_ACQUIRE_FAILED、FILESYSTEM_NOT_READY、CONFIG_RESOLVE_FAILED、CONFIG_INJECTION_FAILED、SIDECAR_CONNECT_FAILED、CLI_NOT_READY、PIPELINE_TIMEOUT、UNKNOWN_ERROR
    - 每个枚举值包含 code（String）和 defaultMessage（String）字段
    - 实现 `fromPhaseName(String phaseName)` 静态方法：根据 Pipeline 阶段名称映射到错误码
    - _Requirements: 5.1_

- [x] 5. 检查点 — 阶段一验证
  - 确保所有新组件的单元测试通过
  - 确保现有代码未被修改，系统正常运行
  - `mvn test -pl himarket-server` 全量测试通过

### 阶段二：重构 SandboxProvider（消除重复）

- [x] 6. K8sSandboxProvider 委托 SandboxHttpClient
  - [x] 6.1 重构 K8sSandboxProvider
    - 注入 `SandboxHttpClient`（通过构造函数）
    - 将 `writeFile()` 方法体替换为：`sandboxHttpClient.writeFile(sidecarBaseUrl(info), info.sandboxId(), relativePath, content)`
    - 将 `readFile()` 方法体替换为：`return sandboxHttpClient.readFile(sidecarBaseUrl(info), info.sandboxId(), relativePath)`
    - 将 `healthCheck()` 方法体替换为：`return sandboxHttpClient.healthCheckWithLog(sidecarBaseUrl(info), info.sandboxId())`
    - 将 `extractArchive()` 方法体替换为：`return sandboxHttpClient.extractArchive(sidecarBaseUrl(info), info.sandboxId(), tarGzBytes)`
    - 删除 K8sSandboxProvider 中的 `httpClient` 和 `objectMapper` 字段（由 SandboxHttpClient 管理）
    - 保留 `acquire()`、`release()`、`connectSidecar()` 不变（这些包含 K8s 特有逻辑）
    - _Requirements: 3.7_

- [x] 7. LocalSandboxProvider 委托 SandboxHttpClient
  - [x] 7.1 重构 LocalSandboxProvider
    - 注入 `SandboxHttpClient`（通过构造函数）
    - 将 `writeFile()` 方法体替换为：`sandboxHttpClient.writeFile(sidecarBaseUrl(info), info.sandboxId(), relativePath, content)`
    - 将 `readFile()` 方法体替换为：`return sandboxHttpClient.readFile(sidecarBaseUrl(info), info.sandboxId(), relativePath)`
    - 将 `healthCheck()` 方法体替换为：`return sandboxHttpClient.healthCheck(sidecarBaseUrl(info))`（本地模式不需要详细日志）
    - 将 `extractArchive()` 方法体替换为：`return sandboxHttpClient.extractArchive(sidecarBaseUrl(info), info.sandboxId(), tarGzBytes)`
    - 删除 LocalSandboxProvider 中的 `restTemplate` 和 `objectMapper` 字段
    - 保留 `acquire()`、`release()`、`connectSidecar()` 和 Sidecar 进程管理逻辑不变
    - _Requirements: 3.7_

- [x] 8. 检查点 — 阶段二验证
  - 确保 K8sSandboxProvider 和 LocalSandboxProvider 的现有测试全部通过
  - `mvn test -pl himarket-server` 全量测试通过
  - 本次重构是纯内部重构，不改变任何外部行为

### 阶段三：拆分 Handler（核心重构）

- [x] 9. 创建 AcpConnectionManager
  - [x] 9.1 创建 AcpConnectionManager 类
    - 在 `himarket-server/.../service/acp/` 下创建 `AcpConnectionManager.java`，标注 `@Component`
    - 从 `AcpWebSocketHandler` 中迁移以下 ConcurrentHashMap 字段：runtimeMap、subscriptionMap、cwdMap、userIdMap、sandboxModeMap、pendingMessageMap、deferredInitMap
    - 实现 `registerConnection(sessionId, userId, cwd, sandboxMode)` 方法
    - 实现 `registerRuntime(sessionId, adapter, subscription)` 方法
    - 实现 `cleanup(sessionId)` 方法：从 AcpWebSocketHandler.cleanup() 迁移资源清理逻辑
    - 实现 getter 方法：getRuntime()、getUserId()、getCwd()、getPendingMessages()、getDeferredInit()、setDeferredInit()
    - _Requirements: 4.3_

- [x] 10. 创建 AcpMessageRouter
  - [x] 10.1 创建 AcpMessageRouter 类
    - 在 `himarket-server/.../service/acp/` 下创建 `AcpMessageRouter.java`，标注 `@Component`
    - 实现 `subscribeAndForward(RuntimeAdapter adapter, WebSocketSession session)` 方法：从 AcpWebSocketHandler.initSandboxAsync() 中提取 stdout 订阅和转发逻辑，返回 Disposable
    - 实现 `forwardToCliAgent(RuntimeAdapter adapter, String message)` 方法：调用 adapter.send(message)
    - 实现 `replayPendingMessages(WebSocketSession session, RuntimeAdapter adapter, Queue<String> pending)` 方法：从 AcpWebSocketHandler.replayPendingMessages() 迁移
    - _Requirements: 4.2_

- [x] 11. 增强 InitContext 和 ConfigInjectionPhase
  - [x] 11.1 增强 InitContext
    - 在 `InitContext` 中新增 `resolvedSessionConfig` 字段（ResolvedSessionConfig 类型）
    - 新增 `getResolvedSessionConfig()` 和 `setResolvedSessionConfig()` 方法
    - _Requirements: 6.1_
  - [x] 11.2 增强 ConfigInjectionPhase
    - 在 `ConfigInjectionPhase` 构造函数中接受 `ConfigFileBuilder` 参数
    - 修改 `execute()` 方法：
      - 如果 `context.getInjectedConfigs()` 非空，直接使用（向后兼容）
      - 否则，从 `context.getResolvedSessionConfig()` + `context.getProviderConfig()` + `context.getRuntimeConfig()` 调用 `configFileBuilder.build()` 生成 ConfigFile 列表
      - 将生成的 ConfigFile 列表设置到 `context.setInjectedConfigs()`
      - 后续写入逻辑不变（tar.gz 批量注入 + 降级 + 抽样验证）
    - _Requirements: 6.3, 6.4_

- [x] 12. 创建 AcpSessionInitializer
  - [x] 12.1 创建 AcpSessionInitializer 类
    - 在 `himarket-server/.../service/acp/` 下创建 `AcpSessionInitializer.java`，标注 `@Component`
    - 注入 SessionConfigResolver、ConfigFileBuilder、SandboxProviderRegistry
    - 创建 `InitializationResult` record：success、adapter、sandboxInfo、errorCode（InitErrorCode）、errorMessage、failedPhase、retryable、totalDuration
    - 实现 `initialize(userId, providerKey, providerConfig, runtimeConfig, sessionConfig, sandboxType, frontendSession)` 方法：
      - 从 AcpWebSocketHandler.initSandboxAsync() 中提取以下逻辑：
        1. 获取 Provider（providerRegistry.getProvider）
        2. 注入 authToken 到 env
        3. 调用 `configResolver.resolve()` 解析配置
        4. 构建 SandboxConfig 和 InitContext（设置 resolvedSessionConfig）
        5. 构建 Pipeline（传入 ConfigFileBuilder 给 ConfigInjectionPhase）
        6. 执行 pipeline.execute()
        7. 将 InitResult 转换为 InitializationResult
      - 错误码映射：使用 `InitErrorCode.fromPhaseName(result.failedPhase())`
    - _Requirements: 4.1, 5.2, 6.2_
  - [x] 12.2 编写 AcpSessionInitializer 单元测试
    - Mock SessionConfigResolver、ConfigFileBuilder、SandboxProviderRegistry
    - 测试正常初始化流程：resolve → build pipeline → execute → 返回 success
    - 测试配置解析失败时的错误码（CONFIG_RESOLVE_FAILED）
    - 测试 Pipeline 各阶段失败时的错误码映射
    - 测试 authToken 注入逻辑
    - _Requirements: 4.1, 5.2, 5.3, 5.4_

- [x] 13. 重构 AcpWebSocketHandler
  - [x] 13.1 将 Handler 逻辑委托给新组件
    - 注入 AcpSessionInitializer、AcpMessageRouter、AcpConnectionManager
    - 重构 `afterConnectionEstablished()`：
      - 参数提取逻辑保持不变（从 attributes 读取 userId、provider、runtime 等）
      - 调用 `connectionManager.registerConnection()` 替代直接操作 Map
      - 异步初始化提交：调用 `sessionInitializer.initialize()` 替代 `initSandboxAsync()`
      - 成功时：调用 `messageRouter.subscribeAndForward()` + `connectionManager.registerRuntime()`
      - 失败时：发送错误消息（使用 InitializationResult 中的 errorCode 和 errorMessage）
    - 重构 `handleTextMessage()`：
      - 通过 `connectionManager.getRuntime()` 获取 adapter
      - 调用 `messageRouter.forwardToCliAgent()` 转发消息
      - adapter 为 null 时通过 `connectionManager.getPendingMessages()` 缓存
    - 重构 `afterConnectionClosed()`：调用 `connectionManager.cleanup()`
    - 删除 `initSandboxAsync()` 方法（逻辑已迁移到 AcpSessionInitializer）
    - 删除 `prepareConfigFiles()` 方法（逻辑已拆分到 SessionConfigResolver + ConfigFileBuilder + ConfigInjectionPhase）
    - 删除 `cleanup()` 方法（逻辑已迁移到 AcpConnectionManager）
    - 删除 `replayPendingMessages()` 方法（逻辑已迁移到 AcpMessageRouter）
    - 删除所有 ConcurrentHashMap 字段（已迁移到 AcpConnectionManager）
    - 删除 `inferConfigType()` 和 `sha256()` 静态方法（已迁移到 ConfigFileBuilder）
    - 保留 `sendSandboxStatus()`、`sendSandboxError()`、`sendInitProgress()`、`sendWorkspaceInfo()` 等 WebSocket 消息发送方法（或迁移到独立的 AcpNotificationSender）
    - 保留 `resolveSandboxType()`、`buildRuntimeConfig()`、`buildWorkspacePath()` 等辅助方法
    - 保留 `handleBinaryMessage()` 和 `handleTransportError()` 不变
    - 保留 `rewriteSessionNewCwd()` 不变（session/new 消息处理）
    - _Requirements: 4.4, 4.5, 1.8, 2.8, 6.5_

- [x] 14. 检查点 — 阶段三验证
  - 确保 `mvn test -pl himarket-server` 全量测试通过
  - 确保 AcpWebSocketHandler 行数显著减少（目标 < 300 行，理想 < 200 行）
  - 确保 `mvn compile -pl himarket-server` 编译通过，无未使用的 import

### 阶段四：清理与 OpenSandbox 扩展点

- [x] 15. 代码清理
  - [x] 15.1 清理 AcpWebSocketHandler 中的残留代码
    - 删除不再使用的 import
    - 删除不再使用的字段声明
    - 确认 Handler 中不再有配置解析、配置文件生成、资源管理的直接逻辑
    - 确认所有 `@Deprecated` 标记的旧方法已被移除或保留了明确的废弃说明
    - _Requirements: 4.5_

- [x] 16. OpenSandbox 扩展点
  - [x] 16.1 添加 OpenSandbox 扩展点注释和文档
    - 在 `SandboxType` 枚举中添加注释说明如何新增 OPEN_SANDBOX 类型
    - 在 `SandboxProvider` 接口的 Javadoc 中添加 OpenSandbox 对接说明：
      - OpenSandbox 的 execd 提供 HTTP API（/command、/files/*），可通过 SandboxHttpClient 复用
      - OpenSandbox 的生命周期管理通过 Python FastAPI Server（POST /sandboxes），需在 acquire() 中调用
      - WebSocket 桥接需要适配（OpenSandbox 使用 HTTP + SSE，非 WebSocket）
    - 在 `SandboxHttpClient` 的 Javadoc 中说明其可复用性（OpenSandbox execd 的 /files/* API 与 HiMarket Sidecar 兼容）
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 17. 最终检查点 — 全量验证
  - `mvn test -pl himarket-server` 全量测试通过
  - `mvn compile` 全项目编译通过
  - AcpWebSocketHandler 行数 < 300 行
  - 新组件各自有单元测试覆盖
  - 代码中无 TODO 遗留（除 OpenSandbox 扩展点的预留 TODO）

## 备注

- 阶段一纯新增代码，不修改现有文件，零风险
- 阶段二是纯内部重构（委托模式），不改变外部行为
- 阶段三是核心重构，需要仔细验证 WebSocket 消息格式不变
- 本次重构不涉及前端改动、数据库 schema 变更、WebSocket 协议变更
- 每个检查点都应确保 `mvn test` 通过，如有失败需先修复再继续
- 端到端验证（重启 + WebSocket 连接测试）建议在阶段三完成后执行
