# 需求文档：HiCoding 架构重构

## 简介

本需求文档描述 HiCoding 模块的架构重构，目标是理清职责边界、简化配置传递链路、消除代码重复，为后续对接 OpenSandbox 打下干净的架构基础。

当前架构的核心问题：
1. `AcpWebSocketHandler` 超过 1000 行，承担了配置解析、沙箱初始化、消息转发、错误处理等多重职责
2. 配置传递链路过长（前端 → URL 编码 → 握手解析 → Handler 解析 → 数据库查询 → ResolvedConfig → ConfigGenerator → 文件写入），经过 7+ 次转换
3. `prepareConfigFiles()` 方法（230+ 行）嵌入在 WebSocket Handler 中，包含模型解析、MCP 解析、Skill 下载、配置生成、临时文件管理等逻辑
4. `K8sSandboxProvider` 和 `LocalSandboxProvider` 的 HTTP 客户端调用、文件操作逻辑高度重复
5. `CliConfigGenerator` 各实现类的文件写入逻辑重复，且直接写本地临时文件再由 Handler 收集
6. `InitContext` 虽然已经类型安全，但缺少 `ResolvedSessionConfig`，配置解析仍在 Handler 中完成

重构原则：
- 渐进式重构，每个任务可独立验证，不破坏现有功能
- 不改变前端接口协议（WebSocket 消息格式不变）
- 不改变数据库 schema
- 为 OpenSandbox 对接预留扩展点（SandboxProvider 接口保持稳定，未来 OpenSandbox 作为新的 Provider 实现接入）

## 术语表

- **SessionConfigResolver**：会话配置解析服务，将前端传入的标识符（CliSessionConfig）解析为完整的配置信息（ResolvedSessionConfig）
- **ResolvedSessionConfig**：已解析的会话配置，包含完整的模型配置、MCP 连接信息、Skill 文件内容
- **CliSessionConfig**：前端传入的会话配置 DTO，仅包含 productId 等标识符
- **ConfigFileBuilder**：配置文件构建器，将 ResolvedSessionConfig 转换为 ConfigFile 列表，封装 CliConfigGenerator 调用和临时文件管理
- **AbstractSandboxProvider**：沙箱提供者抽象基类，提取 Local/K8s Provider 的公共 HTTP 客户端和文件操作逻辑
- **SandboxHttpClient**：沙箱 HTTP 客户端封装，统一 Sidecar HTTP API 调用（writeFile/readFile/healthCheck/extractArchive）
- **AcpConnectionManager**：WebSocket 连接管理器，管理连接状态、会话映射、资源清理
- **AcpMessageRouter**：消息路由器，处理前端 ↔ CLI 的双向消息转发
- **AcpSessionInitializer**：会话初始化器，编排沙箱初始化流程（配置解析 → Pipeline 执行 → 结果处理）
- **InitErrorCode**：初始化错误码枚举，定义细粒度的错误分类

## 需求

### 需求 1：提取 SessionConfigResolver 配置解析服务

**用户故事：** 作为平台开发者，我希望配置解析逻辑从 AcpWebSocketHandler 中独立出来，成为可独立测试的服务，以便简化 Handler 职责并支持配置缓存。

#### 验收标准

1. THE SessionConfigResolver SHALL 接受 CliSessionConfig 和 userId 作为输入，返回 ResolvedSessionConfig
2. THE SessionConfigResolver SHALL 封装模型配置解析逻辑（通过 ModelConfigResolver 查询数据库，构建 CustomModelConfig）
3. THE SessionConfigResolver SHALL 封装 MCP 配置解析逻辑（通过 McpConfigResolver 解析 MCP Server 连接信息）
4. THE SessionConfigResolver SHALL 封装 Skill 文件下载逻辑（通过 SkillPackageService 下载 Skill 文件内容）
5. WHEN 模型配置解析失败时，THE SessionConfigResolver SHALL 记录错误日志并返回 customModelConfig 为 null 的 ResolvedSessionConfig（不阻断整体流程）
6. WHEN MCP 或 Skill 解析失败时，THE SessionConfigResolver SHALL 记录错误日志并跳过失败项，继续处理其余项
7. THE SessionConfigResolver SHALL 正确设置 Spring Security 上下文（userId）以支持 ModelConfigResolver 的权限检查，并在完成后清理上下文
8. THE AcpWebSocketHandler.prepareConfigFiles() 中的配置解析逻辑（模型/MCP/Skill 解析部分）SHALL 被替换为调用 SessionConfigResolver.resolve()

### 需求 2：提取 ConfigFileBuilder 配置文件构建器

**用户故事：** 作为平台开发者，我希望配置文件的生成逻辑（调用 CliConfigGenerator、管理临时文件、收集 ConfigFile 列表）从 AcpWebSocketHandler 中独立出来，使配置生成可独立测试。

#### 验收标准

1. THE ConfigFileBuilder SHALL 接受 ResolvedSessionConfig、providerKey、CliProviderConfig 和 RuntimeConfig 作为输入，返回 List<ConfigFile>
2. THE ConfigFileBuilder SHALL 通过 CliConfigGeneratorRegistry 获取对应的 CliConfigGenerator 实现
3. THE ConfigFileBuilder SHALL 管理临时目录的创建和清理（创建 → 调用 generator → 收集文件 → 删除临时目录）
4. THE ConfigFileBuilder SHALL 调用 generator.generateConfig() 生成模型配置，并将返回的额外环境变量合并到 RuntimeConfig.env 中
5. THE ConfigFileBuilder SHALL 调用 generator.generateMcpConfig() 生成 MCP 配置（当 providerConfig.isSupportsMcp() 为 true 时）
6. THE ConfigFileBuilder SHALL 调用 generator.generateSkillConfig() 生成 Skill 配置（当 providerConfig.isSupportsSkill() 为 true 时）
7. THE ConfigFileBuilder SHALL 遍历临时目录收集所有生成的文件，计算 SHA-256 哈希，推断 ConfigType，构建 ConfigFile 列表
8. THE AcpWebSocketHandler.prepareConfigFiles() 中的配置文件生成逻辑 SHALL 被替换为调用 ConfigFileBuilder.build()

### 需求 3：提取 SandboxHttpClient 统一 HTTP 客户端

**用户故事：** 作为平台开发者，我希望 K8sSandboxProvider 和 LocalSandboxProvider 中重复的 Sidecar HTTP 调用逻辑被提取到公共组件中，消除代码重复。

#### 验收标准

1. THE SandboxHttpClient SHALL 提供 writeFile(baseUrl, relativePath, content) 方法，封装 POST /files/write 调用
2. THE SandboxHttpClient SHALL 提供 readFile(baseUrl, relativePath) 方法，封装 POST /files/read 调用
3. THE SandboxHttpClient SHALL 提供 healthCheck(baseUrl) 方法，封装 GET /health 调用
4. THE SandboxHttpClient SHALL 提供 extractArchive(baseUrl, tarGzBytes) 方法，封装 POST /files/extract 调用
5. THE SandboxHttpClient SHALL 统一处理 HTTP 超时（默认 10 秒）、错误响应（非 200 抛出 IOException）、中断处理（InterruptedException 恢复中断标志）
6. THE SandboxHttpClient SHALL 在 IOException 中包含沙箱标识信息（sandboxId）以便排查
7. THE K8sSandboxProvider 和 LocalSandboxProvider 中的 writeFile/readFile/healthCheck/extractArchive 方法 SHALL 委托给 SandboxHttpClient

### 需求 4：拆分 AcpWebSocketHandler

**用户故事：** 作为平台开发者，我希望 AcpWebSocketHandler 的职责被合理拆分，使每个组件职责单一、可独立测试。

#### 验收标准

1. THE AcpSessionInitializer SHALL 封装沙箱初始化的完整编排逻辑：调用 SessionConfigResolver 解析配置 → 调用 ConfigFileBuilder 生成配置文件 → 填充 InitContext → 执行 SandboxInitPipeline → 处理结果
2. THE AcpMessageRouter SHALL 封装前端 ↔ CLI 的双向消息转发逻辑：订阅 RuntimeAdapter.stdout() → 转发到 WebSocket session；接收 WebSocket 消息 → 转发到 RuntimeAdapter.send()
3. THE AcpConnectionManager SHALL 管理连接级别的状态和资源：维护 session → RuntimeAdapter/Subscription/cwd/userId 的映射；处理连接关闭时的资源清理（取消订阅、关闭 RuntimeAdapter）
4. THE AcpWebSocketHandler SHALL 仅作为 WebSocket 事件的入口点，将具体逻辑委托给 AcpSessionInitializer、AcpMessageRouter、AcpConnectionManager
5. 重构后的 AcpWebSocketHandler SHALL 不超过 200 行

### 需求 5：统一错误处理

**用户故事：** 作为平台开发者和运维人员，我希望初始化过程中的错误有清晰的分类和详细的诊断信息，以便快速定位问题。

#### 验收标准

1. THE InitErrorCode 枚举 SHALL 定义以下错误码：SANDBOX_ACQUIRE_FAILED、FILESYSTEM_NOT_READY、CONFIG_RESOLVE_FAILED、CONFIG_INJECTION_FAILED、SIDECAR_CONNECT_FAILED、CLI_NOT_READY、PIPELINE_TIMEOUT、UNKNOWN_ERROR
2. THE AcpSessionInitializer SHALL 在初始化失败时构建包含 InitErrorCode、错误消息、失败阶段、是否可重试、诊断信息（已完成阶段列表、总耗时）的错误响应
3. THE 错误响应 SHALL 通过 WebSocket 发送给前端，格式为 `{ type: "sandbox/status", status: "error", errorCode: "...", message: "...", phase: "...", retryable: true/false }`
4. WHEN 配置解析失败时，THE 错误响应 SHALL 包含具体失败的配置项（如 "模型配置解析失败: modelProductId=xxx"）

### 需求 6：InitContext 增强

**用户故事：** 作为平台开发者，我希望 InitContext 持有 ResolvedSessionConfig，使配置解析结果在整个初始化流水线中可用，避免在 Pipeline 外部准备配置文件。

#### 验收标准

1. THE InitContext SHALL 新增 resolvedSessionConfig 字段（ResolvedSessionConfig 类型）
2. THE AcpSessionInitializer SHALL 在调用 Pipeline 之前通过 SessionConfigResolver 解析配置，并设置到 InitContext.resolvedSessionConfig
3. THE ConfigInjectionPhase SHALL 从 InitContext.resolvedSessionConfig 获取已解析的配置，而非依赖外部预先填充的 injectedConfigs
4. THE ConfigInjectionPhase SHALL 内部调用 ConfigFileBuilder 生成 ConfigFile 列表，使配置文件生成逻辑完全封装在 Pipeline 阶段内
5. THE AcpWebSocketHandler 中的 prepareConfigFiles() 方法 SHALL 被移除，其逻辑由 SessionConfigResolver + ConfigFileBuilder + ConfigInjectionPhase 共同承担

### 需求 7：为 OpenSandbox 对接预留扩展点

**用户故事：** 作为平台架构师，我希望重构后的架构能够方便地接入 OpenSandbox 作为新的沙箱提供者，无需修改上层代码。

#### 验收标准

1. THE SandboxProvider 接口 SHALL 保持稳定，不因本次重构引入破坏性变更
2. THE SandboxType 枚举 SHALL 预留 OPEN_SANDBOX 值（或通过注释说明扩展方式）
3. THE SandboxHttpClient SHALL 设计为可复用组件，未来 OpenSandboxProvider 可直接使用其 HTTP 调用能力（OpenSandbox 的 execd 也提供 HTTP API）
4. THE ConfigInjectionPhase SHALL 通过 SandboxProvider 接口操作文件，不假设底层实现（本地/K8s/OpenSandbox 均可）
5. THE 架构文档 SHALL 在代码注释中说明 OpenSandbox 对接的扩展路径
