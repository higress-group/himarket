# 需求文档

## 简介

统一 HiCli 和 HiCoding 的会话配置传递逻辑，实现"前端只传标识符，后端统一解析完整配置"的终态架构。HiCli 作为快速调试产物，页面交互和后端逻辑统一向 HiCoding 对齐。当前两个前端各自在客户端拼装完整的模型配置（`customModelConfig`）和 Skill 文件内容，导致状态丢失时 NPE、认证失败等问题。本次改造将配置解析职责统一收归后端，前端仅传递 `productId` 等标识符。涉及所有支持自定义模型的 CLI 工具（Qwen Code、Claude Code、OpenCode 等），Qoder CLI 保持现有逻辑不变。不考虑向后兼容，直接面向终态方案演进。

## 术语表

- **Session_Config**: `CliSessionConfig` Java 对象，前端传入的纯标识符 DTO，承载 `modelProductId`、`mcpServers[].productId`、`skills[].productId`、认证凭据等，通过 WebSocket 消息传递给后端
- **Resolved_Session_Config**: `ResolvedSessionConfig` Java 对象（新建），后端解析后的完整配置 DTO，包含完整的 `CustomModelConfig`、MCP Server 完整配置（url、transportType、headers）、Skill 文件内容等，供 `prepareConfigFiles` 和各 Config_Generator 使用
- **Config_Sidebar**: HiCoding 前端的配置侧边栏组件（`ConfigSidebar.tsx`），负责构建 `cliSessionConfig` JSON，作为统一的配置交互标准
- **Cli_Selector**: HiCli 前端的配置选择器组件（`CliSelector.tsx`），需向 Config_Sidebar 的交互方式对齐
- **Market_Skill_Selector**: HiCli 前端的 Skill 选择器组件（`MarketSkillSelector.tsx`），当前在前端下载 Skill 文件内容，需简化为仅传标识符
- **Market_Model_Selector**: HiCli 前端的模型选择器组件（`MarketModelSelector.tsx`），当前在前端拼装完整 `CustomModelFormData`，需简化为仅传标识符
- **Prepare_Config_Files**: `AcpWebSocketHandler.prepareConfigFiles()` 方法，负责在 WebSocket 连接建立时生成 CLI 配置文件
- **Model_Config_Resolver**: 待新建的后端服务，负责根据 `modelProductId` 解析完整模型配置（baseUrl、apiKey、modelId、protocolType）
- **Skill_Package_Service**: 已有的后端服务（`SkillPackageService`），提供 `getAllFiles(productId)` 方法下载 Skill 完整文件
- **Config_Generator**: `CliConfigGenerator` 接口及其实现（`QwenCodeConfigGenerator`、`ClaudeCodeConfigGenerator`、`OpenCodeConfigGenerator` 等），负责将配置写入 CLI 工具特定格式的文件。Qoder CLI（`QoderCliConfigGenerator`）保持现有逻辑不变，不在本次改造范围内
- **Custom_Model_Config**: `CustomModelConfig` Java 对象，包含 baseUrl、apiKey、modelId、protocolType 等模型接入信息（后端内部使用，不再由前端传递）

## 需求

### 需求 1：Session_Config 数据结构重构

**用户故事：** 作为后端开发者，我希望前端传入的 DTO 和后端内部使用的 DTO 职责分离，以便类型清晰、扩展方便。

#### 验收标准

1. THE Session_Config（前端 DTO）SHALL 包含顶层 `modelProductId`（String 类型）字段，用于标识用户选中的市场模型产品
2. THE Session_Config 的 SkillEntry SHALL 仅包含 `productId`（String 类型）和 `name`（String 类型）字段
3. THE Session_Config SHALL 移除 `customModelConfig` 字段
4. THE Session_Config 的 SkillEntry SHALL 移除 `skillMdContent` 和 `files` 字段
5. THE Session_Config 的 McpServerEntry SHALL 仅包含 `productId`（String 类型）和 `name`（String 类型）字段，移除 `url`、`transportType`、`headers` 字段
6. THE Prepare_Config_Files SHALL 新建 Resolved_Session_Config（后端内部 DTO），包含解析后的完整 `CustomModelConfig`、带完整连接信息的 MCP Server 列表和带文件内容的 Skill 列表
7. WHEN `modelProductId` 非空时，THE Prepare_Config_Files SHALL 通过 Model_Config_Resolver 解析完整模型配置，填充到 Resolved_Session_Config
8. WHEN McpServerEntry 包含 `productId` 时，THE Prepare_Config_Files SHALL 通过 Mcp_Config_Resolver 解析完整 MCP 配置（url、transportType、headers），填充到 Resolved_Session_Config
9. THE Prepare_Config_Files SHALL 将 Resolved_Session_Config 传递给各 Config_Generator，而非直接传递 Session_Config

### 需求 2：后端模型配置解析服务

**用户故事：** 作为后端开发者，我希望有一个统一的模型配置解析服务，以便所有 CLI 工具共用同一套模型解析逻辑。

#### 验收标准

1. THE Model_Config_Resolver SHALL 根据 `modelProductId`（市场产品 ID）和当前开发者身份解析出完整的 Custom_Model_Config（包含 baseUrl、apiKey、modelId、protocolType）
2. THE Model_Config_Resolver SHALL 复用 `ConsumerService.getPrimaryConsumer()` 获取当前开发者的 Consumer 信息
3. THE Model_Config_Resolver SHALL 复用 `ConsumerService.listConsumerSubscriptions()` 获取订阅列表并筛选 APPROVED 状态的订阅
4. THE Model_Config_Resolver SHALL 通过 `ProductService.getProducts()` 获取 `modelProductId` 对应的产品详情，并通过 `BaseUrlExtractor` 和 `ProtocolTypeMapper` 提取 baseUrl 和 protocolType，通过产品 feature 提取底层 modelId
5. THE Model_Config_Resolver SHALL 复用 `ConsumerService.getCredential()` 提取 apiKey
6. IF 当前开发者无 Primary Consumer，THEN THE Model_Config_Resolver SHALL 返回空结果并记录 debug 级别日志
7. IF `modelProductId` 对应的产品未找到或未订阅，THEN THE Model_Config_Resolver SHALL 返回空结果并记录 warn 级别日志
8. IF apiKey 提取失败，THEN THE Model_Config_Resolver SHALL 返回空结果并记录 warn 级别日志

### 需求 3：后端 MCP 配置解析服务

**用户故事：** 作为后端开发者，我希望有一个统一的 MCP 配置解析服务，以便根据 `productId` 自动解析出完整的 MCP 连接信息（url、transportType、headers），前端无需传递这些细节。

#### 验收标准

1. THE Mcp_Config_Resolver SHALL 根据 `mcpProductId`（市场产品 ID）和当前开发者身份解析出完整的 MCP 连接配置（name、url、transportType、headers）
2. THE Mcp_Config_Resolver SHALL 复用 `CliProviderController.buildMarketMcpInfo()` 中的逻辑：通过 `ProductService.getProducts()` 获取产品详情，通过 `product.getMcpConfig().toTransportConfig()` 提取 url 和 transportType
3. THE Mcp_Config_Resolver SHALL 复用 `CliProviderController.extractAuthHeaders()` 中的逻辑：通过 `ConsumerService.getDefaultCredential()` 提取认证请求头
4. IF `mcpProductId` 对应的产品未找到或 mcpConfig 不完整，THEN THE Mcp_Config_Resolver SHALL 返回空结果并记录 warn 级别日志
5. IF 认证请求头提取失败，THEN THE Mcp_Config_Resolver SHALL 仍返回 MCP 配置（headers 为空），并记录 debug 级别日志

### 需求 4：后端 Skill 文件自动下载

**用户故事：** 作为后端开发者，我希望后端能根据 Skill 的 productId 自动下载文件内容，以便前端无需传递任何文件数据。

#### 验收标准

1. WHEN SkillEntry 包含 `productId` 时，THE Prepare_Config_Files SHALL 调用 Skill_Package_Service 的 `getAllFiles(productId)` 下载完整文件列表
2. WHEN Skill_Package_Service 返回文件列表后，THE Prepare_Config_Files SHALL 将文件内容传递给 Config_Generator 生成配置
3. IF Skill_Package_Service 下载失败，THEN THE Prepare_Config_Files SHALL 记录 error 级别日志并跳过该 Skill，继续处理其余 Skill

### 需求 5：Skill 配置生成 NPE 防护

**用户故事：** 作为后端开发者，我希望 Skill 配置生成逻辑具备 null 安全检查，以便在 Skill 数据不完整时不会抛出 NPE。

#### 验收标准

1. WHEN SkillEntry 的 `name` 为 null 时，THE Config_Generator 的 `generateSkillConfig()` SHALL 跳过该 SkillEntry 并记录 warn 级别日志
2. WHEN 下载的文件列表为空时，THE Config_Generator 的 `generateSkillConfig()` SHALL 跳过该 SkillEntry 并记录 warn 级别日志
3. THE QwenCodeConfigGenerator 及其他支持自定义模型的 Config_Generator（Claude Code、OpenCode 等）的 `generateSkillConfig()` SHALL 在处理每个 SkillEntry 前执行上述 null 检查（Qoder CLI 不在本次范围内）

### 需求 6：HiCoding 前端简化（Config_Sidebar）

**用户故事：** 作为前端开发者，我希望 Config_Sidebar 只传递标识符到后端，以便消除前端内存状态依赖导致的配置丢失问题。

#### 验收标准

1. THE Config_Sidebar 的 `buildFinalConfig()` SHALL 在 `cliSessionConfig` 中仅传递 `modelProductId`（市场产品 ID 字符串）
2. THE Config_Sidebar 的 `buildFinalConfig()` SHALL 在 Skill 条目中仅传递 `productId` 和 `name`
3. THE Config_Sidebar SHALL 移除 `marketModels` 和 `marketModelsApiKey` 状态变量及其获取逻辑
4. THE Config_Sidebar 的模型下拉列表 SHALL 展示市场产品名称（`name`），而非底层模型 ID（`modelId`）
5. THE Config_Sidebar 的 `buildFinalConfig()` SHALL 在 MCP Server 条目中仅传递 `productId` 和 `name`，不再传递 `url`、`transportType`、`headers`
6. THE Config_Sidebar SHALL 移除 `mcpAuthHeaders` 状态变量及 `extractAuthHeaders` 获取逻辑

### 需求 7：HiCli 前端对齐（Cli_Selector 及子组件向 HiCoding 对齐）

**用户故事：** 作为前端开发者，我希望 HiCli 的配置选择器向 HiCoding 的 Config_Sidebar 对齐，只传递标识符到后端，移除前端的文件下载和配置拼装逻辑。

#### 验收标准

1. THE Market_Skill_Selector SHALL 在用户选择 Skill 时仅传递 `productId` 和 `name`，移除 `getSkillAllFiles()` 和 `downloadSkill()` 的调用逻辑
2. THE Market_Model_Selector SHALL 在用户选择模型时仅传递 `productId`（市场产品 ID），移除 `CustomModelFormData` 的拼装逻辑（baseUrl、apiKey、protocolType）
3. THE Market_Model_Selector SHALL 展示市场产品名称（`name`），而非底层模型 ID（`modelId`）
4. THE Cli_Selector 的 `handleConnect()` SHALL 在 `cliSessionConfig` 中仅传递 `modelProductId`（市场产品 ID 字符串）
5. THE Cli_Selector 的 `handleConnect()` SHALL 在 Skill 条目中仅传递 `productId` 和 `name`
6. THE Cli_Selector 的 `handleConnect()` SHALL 在 MCP Server 条目中仅传递 `productId` 和 `name`，不再传递 `url`、`transportType`、`headers`
7. THE Cli_Selector SHALL 保持 authToken 的传递方式不变

### 需求 8：前端类型定义更新

**用户故事：** 作为前端开发者，我希望 TypeScript 类型定义与新的标识符传递方式保持一致，以便获得编译期类型检查。

#### 验收标准

1. THE 前端 `CliSessionConfig` 类型 SHALL 包含 `modelProductId`（string 类型，可选）字段
2. THE 前端 `SkillEntry` 类型 SHALL 仅包含 `productId`（string 类型）和 `name`（string 类型）字段
3. THE 前端 `McpServerEntry` 类型 SHALL 仅包含 `productId`（string 类型）和 `name`（string 类型）字段，移除 `url`、`transportType`、`headers` 字段
4. THE 前端 `CliSessionConfig` 类型 SHALL 移除 `customModelConfig` 字段
5. THE 前端 `SkillEntry` 类型 SHALL 移除 `skillMdContent` 和 `files` 字段
