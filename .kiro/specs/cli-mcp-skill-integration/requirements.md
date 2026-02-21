# 需求文档

## 简介

HiMarket 平台的 HiCli、HiCoding、HiWork 三个场景当前已支持通过公共 CliSelector 组件选择模型市场的模型来启动 CLI 会话。本功能在此基础上，增加对市场中 MCP Server 和 Agent Skill 的支持，使用户在启动 CLI 会话时可以选择市场中已订阅的 MCP Server 和已发布的 Skill，系统自动将这些配置注入到 CLI 进程中。

当前阶段仅适配 Qwen Code CLI 工具（通过 `.qwen/settings.json` 和 `.agents/skills/` 目录注入配置），但设计上预留未来支持其他 CLI 工具（如 OpenCode、Kiro CLI 等）的扩展能力。

Qwen Code 对 MCP 的支持方式：在 `.qwen/settings.json` 中配置 `mcpServers` 字段，每个 MCP Server 包含 `name`、`url`、`type`（sse 或 streamable-http）等信息。Qwen Code 对 Skill 的支持方式：读取工作目录下 `.agents/skills/<skill-name>/SKILL.md` 文件。

核心原则：
- 不破坏现有的模型选择和自定义模型配置功能
- 复用现有的 WebSocket 传递和后端 ConfigGenerator 机制
- MCP Server 选择仅对已订阅且已发布的 MCP_SERVER 类型产品可用
- Skill 选择对所有已发布到门户的 AGENT_SKILL 类型产品可用（无需订阅）
- 前端改动集中在公共 CliSelector 组件，三个模块自动获得新能力
- 后端 CliConfigGenerator 接口扩展为支持 MCP 和 Skill 配置注入
- 设计上通过接口抽象预留对其他 CLI 工具的扩展能力

## 术语表

- **MCP_Server_Product**：MCP Server 产品，模型市场中类型为 MCP_SERVER 的已发布产品，包含 MCP 服务端点和传输协议信息
- **AGENT_SKILL_Product**：Agent Skill 产品，模型市场中类型为 AGENT_SKILL 的已发布产品，包含 SKILL.md 文件内容
- **McpServerConfig_Injection**：MCP Server 配置注入，在 CLI 进程启动前将选中的 MCP Server 信息写入 CLI 工具的配置文件
- **SkillConfig_Injection**：Skill 配置注入，在 CLI 进程启动前将选中的 Skill 的 SKILL.md 文件写入工作目录的 `.agents/skills/` 目录
- **CliSessionConfig**：CLI 会话配置，扩展现有的 CustomModelConfig，新增 MCP Server 列表和 Skill 列表字段，通过 WebSocket 查询参数传递给后端
- **MarketMcpInfo**：市场 MCP 信息，从 MCP_SERVER 产品中提取的 MCP 服务端点 URL、服务名称和传输协议类型
- **MarketSkillInfo**：市场 Skill 信息，从 AGENT_SKILL 产品中提取的技能名称、产品 ID 和 SKILL.md 内容
- **CliSelector**：CLI 工具选择器（已有公共组件），HiCli、HiCoding、HiWork 三个模块共用
- **Consumer**：消费者，开发者在 HiMarket 中注册的 API 消费实体，拥有凭证和订阅关系
- **Primary_Consumer**：主消费者，开发者的默认 Consumer

## 需求

### 需求 1：后端提供已订阅 MCP Server 列表接口

**用户故事：** 作为 HiCli/HiCoding/HiWork 用户，我希望系统提供一个接口返回我已订阅的 MCP Server 产品信息，以便前端展示可选的 MCP Server 列表。

#### 验收标准

1. THE 系统 SHALL 在 CliProviderController 中新增 `GET /cli-providers/market-mcps` 接口，返回当前开发者已订阅的 MCP_SERVER 类型产品列表及认证信息
2. THE 接口 SHALL 使用 `@DeveloperAuth` 注解，要求开发者身份认证
3. WHEN 开发者调用该接口时，THE 系统 SHALL 通过 Primary_Consumer 获取开发者的订阅列表，筛选出 productType 为 MCP_SERVER 且 status 为 APPROVED 的订阅
4. THE 接口 SHALL 对每个已订阅的 MCP_SERVER 产品返回以下信息：productId、产品名称（name）、MCP 服务名称（mcpServerName）、MCP 端点 URL（从 mcpConfig 中拼接）、传输协议类型（sse 或 streamable-http，从 mcpConfig.meta.protocol 映射）和产品描述（description）
5. WHEN 开发者没有 Primary_Consumer 时，THE 接口 SHALL 返回空 MCP 列表
6. WHEN 开发者有 Primary_Consumer 但没有任何 MCP_SERVER 订阅时，THE 接口 SHALL 返回空 MCP 列表
7. THE 接口返回的 MCP 端点 URL SHALL 复用现有 MCPConfigResult.toTransportConfig() 的 URL 拼接逻辑，从 mcpServerConfig 的 domains 和 path 中拼接，并根据传输协议类型决定是否追加 `/sse` 后缀
8. WHEN 产品的 mcpConfig 数据不完整时，THE 接口 SHALL 跳过该产品并记录警告日志，继续处理其他产品
9. THE 接口 SHALL 在响应中包含开发者 Primary_Consumer 的认证凭证信息（headers 和 queryParams），以便 CLI 工具在连接 MCP Server 时携带认证信息（复用现有 ConsumerService.getDefaultCredential 逻辑）

### 需求 2：后端提供已发布 Skill 列表接口

**用户故事：** 作为 HiCli/HiCoding/HiWork 用户，我希望系统提供一个接口返回当前门户中已发布的 Skill 产品信息，以便前端展示可选的 Skill 列表。

#### 验收标准

1. THE 系统 SHALL 在 CliProviderController 中新增 `GET /cli-providers/market-skills` 接口，返回当前门户中已发布的 AGENT_SKILL 类型产品列表
2. THE 接口 SHALL 无需开发者身份认证（Skill 为公开资源，无需订阅）
3. WHEN 调用该接口时，THE 系统 SHALL 查询所有已发布到当前门户的 AGENT_SKILL 类型产品
4. THE 接口 SHALL 对每个 Skill 产品返回以下信息：productId、产品名称（name）、技能标签（skillTags）和产品描述（description）
5. WHEN 当前门户没有已发布的 AGENT_SKILL 产品时，THE 接口 SHALL 返回空列表

### 需求 3：扩展 CliSessionConfig 数据模型

**用户故事：** 作为平台开发者，我希望扩展现有的会话配置数据模型，以便在 CLI 会话启动时同时传递模型配置、MCP Server 配置和 Skill 配置。

#### 验收标准

1. THE 系统 SHALL 新增 CliSessionConfig 数据模型，包含以下字段：customModelConfig（可选，复用现有 CustomModelConfig）、mcpServers（可选，MarketMcpInfo 列表）和 skills（可选，MarketSkillInfo 列表）
2. THE MarketMcpInfo SHALL 包含以下字段：productId、name（MCP 服务名称）、url（MCP 端点 URL）、transportType（传输协议类型，sse 或 streamable-http）和 headers（可选，认证请求头 Map）
3. THE MarketSkillInfo SHALL 包含以下字段：productId、name（技能名称）和 skillMdContent（SKILL.md 文件内容）
4. WHEN CliSessionConfig 的所有字段均为空或 null 时，THE 系统 SHALL 按照现有逻辑启动 CLI 进程，不生成额外配置
5. THE 系统 SHALL 保持对现有 customModelConfig WebSocket 查询参数的向后兼容，当仅传递 customModelConfig 时行为与现有逻辑一致

### 需求 4：扩展 WebSocket 握手参数传递

**用户故事：** 作为平台开发者，我希望前端的 MCP 和 Skill 配置能够通过 WebSocket 连接参数传递给后端，以便后端在启动 CLI 进程时使用。

#### 验收标准

1. WHEN 用户配置了 MCP Server 或 Skill 并点击连接时，THE 系统 SHALL 将 CliSessionConfig 序列化为 JSON 字符串，通过 WebSocket URL 的查询参数（cliSessionConfig）传递给后端
2. THE AcpHandshakeInterceptor SHALL 从 WebSocket 握手请求中解析 cliSessionConfig 参数，反序列化为 CliSessionConfig 对象，并存储到 WebSocket session attributes 中
3. WHEN cliSessionConfig 参数不存在但 customModelConfig 参数存在时，THE AcpHandshakeInterceptor SHALL 将 customModelConfig 包装为 CliSessionConfig 对象（仅设置 customModelConfig 字段），保持向后兼容
4. WHEN cliSessionConfig 和 customModelConfig 参数均不存在时，THE AcpHandshakeInterceptor SHALL 跳过配置解析，按现有逻辑处理连接
5. IF cliSessionConfig 参数的 JSON 格式不合法，THEN THE AcpHandshakeInterceptor SHALL 记录警告日志并忽略该参数，按现有逻辑继续处理连接

### 需求 5：扩展 CliConfigGenerator 支持 MCP 和 Skill 注入

**用户故事：** 作为平台开发者，我希望 CliConfigGenerator 接口能够支持 MCP Server 和 Skill 的配置注入，以便 CLI 工具在启动时自动加载这些配置。

#### 验收标准

1. THE CliConfigGenerator 接口 SHALL 新增 `generateMcpConfig(String workingDirectory, List<MarketMcpInfo> mcpServers)` 方法，用于生成 MCP Server 配置
2. THE CliConfigGenerator 接口 SHALL 新增 `generateSkillConfig(String workingDirectory, List<MarketSkillInfo> skills)` 方法，用于将 Skill 的 SKILL.md 文件写入工作目录
3. WHEN CliConfigGenerator 的实现类不支持某种配置注入时，THE 实现类 SHALL 提供默认的空实现（不执行任何操作）
4. THE AcpWebSocketHandler SHALL 在 CLI 进程启动前依次调用 generateConfig（模型配置）、generateMcpConfig（MCP 配置）和 generateSkillConfig（Skill 配置）

### 需求 6：Qwen Code MCP Server 配置注入

**用户故事：** 作为使用 Qwen Code 的开发者，我希望系统根据我选择的 MCP Server 自动生成配置，以便 Qwen Code 在启动时自动连接这些 MCP Server。

#### 验收标准

1. WHEN 用户为 Qwen_Code 选择了 MCP Server 时，THE QwenCodeConfigGenerator SHALL 在 `.qwen/settings.json` 中生成 `mcpServers` 配置段
2. THE QwenCodeConfigGenerator SHALL 为每个选中的 MCP Server 生成包含 `name`、`url` 和 `type` 字段的配置条目，其中 type 为 sse 或 streamable-http
3. WHEN MCP Server 包含认证 headers 时，THE QwenCodeConfigGenerator SHALL 在对应的 MCP Server 配置条目中包含 `headers` 字段，传递认证信息
4. WHEN `.qwen/settings.json` 已存在 `mcpServers` 配置时，THE QwenCodeConfigGenerator SHALL 将新的 MCP Server 配置合并到现有配置中，避免重复（按 name 去重）
5. THE QwenCodeConfigGenerator SHALL 确保生成的 MCP Server 配置格式符合 Qwen Code 的 settings.json 规范

### 需求 7：Qwen Code Skill 配置注入

**用户故事：** 作为使用 Qwen Code 的开发者，我希望系统根据我选择的 Skill 自动将 SKILL.md 文件写入工作目录，以便 Qwen Code 在启动时自动加载这些技能。

#### 验收标准

1. WHEN 用户为 Qwen_Code 选择了 Skill 时，THE QwenCodeConfigGenerator SHALL 在工作目录下创建 `.agents/skills/<skill-name>/SKILL.md` 文件
2. THE QwenCodeConfigGenerator SHALL 将 MarketSkillInfo 中的 skillMdContent 写入对应的 SKILL.md 文件
3. WHEN `.agents/skills/<skill-name>/SKILL.md` 文件已存在时，THE QwenCodeConfigGenerator SHALL 用新内容覆盖该文件
4. THE QwenCodeConfigGenerator SHALL 确保 skill-name 目录名使用技能名称的 kebab-case 格式，去除特殊字符

### 需求 8：前端 MCP Server 选择器组件

**用户故事：** 作为 HiCli/HiCoding/HiWork 用户，我希望在 CLI 工具选择界面中能够选择市场中已订阅的 MCP Server，以便在 CLI 会话中使用这些 MCP 工具。

#### 验收标准

1. WHEN 用户在 CliSelector 中选择支持自定义模型的 CLI 工具时，THE 系统 SHALL 在模型配置区域下方展示"启用市场 MCP Server"开关
2. WHEN 用户开启"启用市场 MCP Server"开关时，THE 系统 SHALL 调用 `GET /cli-providers/market-mcps` 接口获取已订阅 MCP Server 列表
3. WHEN MCP Server 列表加载成功且非空时，THE 系统 SHALL 以多选复选框列表形式展示 MCP Server，每个选项显示服务名称和描述
4. WHEN 用户选择一个或多个 MCP Server 时，THE 系统 SHALL 将选中的 MCP Server 信息存储到 CliSessionConfig 中
5. WHEN MCP Server 列表为空时，THE 系统 SHALL 显示提示信息"暂无已订阅的 MCP Server，请先在市场中订阅"
6. WHEN 接口调用失败时，THE 系统 SHALL 显示错误信息并提供重试按钮
7. WHEN 用户未登录（匿名访问）时，THE 系统 SHALL 显示提示信息"请先登录以使用市场 MCP Server"

### 需求 9：前端 Skill 选择器组件

**用户故事：** 作为 HiCli/HiCoding/HiWork 用户，我希望在 CLI 工具选择界面中能够选择市场中已发布的 Skill，以便在 CLI 会话中使用这些技能。

#### 验收标准

1. WHEN 用户在 CliSelector 中选择支持自定义模型的 CLI 工具时，THE 系统 SHALL 在 MCP Server 选择区域下方展示"启用市场 Skill"开关
2. WHEN 用户开启"启用市场 Skill"开关时，THE 系统 SHALL 调用 `GET /cli-providers/market-skills` 接口获取已发布 Skill 列表
3. WHEN Skill 列表加载成功且非空时，THE 系统 SHALL 以多选复选框列表形式展示 Skill，每个选项显示技能名称和描述
4. WHEN 用户选择一个或多个 Skill 时，THE 系统 SHALL 调用 Skill 下载接口获取 SKILL.md 内容，并将选中的 Skill 信息（含 SKILL.md 内容）存储到 CliSessionConfig 中
5. WHEN Skill 列表为空时，THE 系统 SHALL 显示提示信息"暂无已发布的 Skill"
6. WHEN 接口调用失败时，THE 系统 SHALL 显示错误信息并提供重试按钮

### 需求 10：前端 WebSocket URL 扩展与连接集成

**用户故事：** 作为平台开发者，我希望前端在连接时将 MCP 和 Skill 配置统一传递给后端，以便后端在启动 CLI 进程时注入所有配置。

#### 验收标准

1. WHEN 用户点击连接按钮时，THE CliSelector SHALL 将模型配置、选中的 MCP Server 列表和选中的 Skill 列表统一组装为 CliSessionConfig 对象
2. THE CliSelector SHALL 将 CliSessionConfig 序列化为 JSON 字符串，通过 WebSocket URL 的 cliSessionConfig 查询参数传递
3. WHEN 用户仅配置了模型（未选择 MCP 或 Skill）时，THE 系统 SHALL 仍使用 cliSessionConfig 参数传递，保持统一的传递方式
4. WHEN 用户未配置任何额外选项时，THE 系统 SHALL 不传递 cliSessionConfig 参数，按现有逻辑连接

### 需求 11：CLI Provider 配置扩展

**用户故事：** 作为平台开发者，我希望在 CLI Provider 配置中标识哪些工具支持 MCP 和 Skill 配置注入，以便前端根据此标识决定是否展示相关选择器。

#### 验收标准

1. THE CliProviderConfig SHALL 新增 supportsMcp 布尔字段，标识该 CLI 工具是否支持 MCP Server 配置注入，默认为 false
2. THE CliProviderConfig SHALL 新增 supportsSkill 布尔字段，标识该 CLI 工具是否支持 Skill 配置注入，默认为 false
3. THE CliProviderController SHALL 在 CliProviderInfo 响应中包含 supportsMcp 和 supportsSkill 字段，供前端使用
4. THE 系统 SHALL 在 application.yml 中为 Qwen Code 的 provider 配置设置 supportsMcp 和 supportsSkill 为 true

### 需求 12：MCP 端点 URL 提取逻辑的正确性

**用户故事：** 作为平台开发者，我希望从 MCP 产品配置中提取端点 URL 的逻辑是可靠的，以便生成的配置能被 CLI 工具正确使用。

#### 验收标准

1. FOR ALL 包含有效 mcpConfig 的 MCP_SERVER 产品，THE 系统 SHALL 复用现有 MCPConfigResult.toTransportConfig() 的逻辑正确提取端点 URL 和传输协议类型
2. WHEN mcpConfig.meta.protocol 为 "HTTP" 或 "StreamableHTTP" 时，THE 系统 SHALL 将传输类型映射为 streamable-http，端点 URL 不追加 `/sse` 后缀
3. WHEN mcpConfig.meta.protocol 为其他值或为空时，THE 系统 SHALL 将传输类型映射为 sse，端点 URL 追加 `/sse` 后缀（如果尚未以 `/sse` 结尾）
4. THE 系统 SHALL 从 mcpServerConfig.domains 中选择非内网域名（排除 networkType 为 intranet 的域名），按 `{protocol}://{domain}[:{port}]{path}` 格式拼接 URL

### 需求 13：CliSessionConfig 序列化与反序列化

**用户故事：** 作为平台开发者，我希望 CliSessionConfig 的序列化和反序列化逻辑是可靠的，以便前后端之间的数据传递正确无误。

#### 验收标准

1. FOR ALL 合法的 CliSessionConfig 对象，序列化为 JSON 再反序列化 SHALL 产生等价的对象（往返一致性）
2. WHEN CliSessionConfig 仅包含 customModelConfig 字段时，THE 反序列化结果 SHALL 正确还原 customModelConfig，mcpServers 和 skills 字段为 null 或空列表
3. WHEN CliSessionConfig 包含所有字段时，THE 反序列化结果 SHALL 正确还原所有字段的值
