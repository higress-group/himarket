# 需求文档

## 简介

HiMarket 平台当前的 HiCli 模块支持多种 CLI Agent 工具（如 Open Code、Qwen Code 等），但这些工具默认连接各自厂商的 LLM 服务。本功能为用户提供在 HiCli 中配置自定义模型接入点和 API Key 的能力，使 Open Code 和 Qwen Code 这两个支持自定义配置的开源 CLI 工具能够连接到用户指定的 LLM 服务。

本功能作为 POC 阶段的实现，不对接 Model 市场，由用户手动输入模型接入点 URL 和 API Key。系统在启动 CLI 进程时自动生成对应工具的配置文件并注入环境变量，实现无感知的模型切换。

核心原则：
- 不破坏已有的 HiCli/HiWork/HiCoding 功能，仅作为额外可选项
- 不对接 Model 市场，用户手动输入配置
- 在本地或沙箱模式运行时，自动配置并切换到用户选择的模型
- 优先针对 Open Code 和 Qwen Code 两个 CLI 工具

## 术语表

- **Custom_Model_Config**：自定义模型配置，包含模型接入点 URL、API Key、模型 ID 和显示名称等信息
- **Model_Endpoint**：模型接入点，LLM 服务的 Base URL（如 `https://api.example.com/v1`）
- **Config_Generator**：配置生成器，根据 CLI 工具类型和用户的 Custom_Model_Config 生成对应格式的配置文件
- **Open_Code**：Open Code CLI 工具，通过 `opencode.json` 配置文件和环境变量支持自定义 Provider
- **Qwen_Code**：Qwen Code CLI 工具，通过 `settings.json` 配置文件和环境变量支持自定义模型提供者
- **Config_Injection**：配置注入，在 CLI 进程启动前将生成的配置文件写入工作目录或通过环境变量传递给进程
- **CliProviderConfig**：已有的 CLI Provider 配置类，定义了命令、参数、环境变量等启动参数
- **Custom_Model_Form**：自定义模型配置表单，前端 UI 组件，用于收集用户的模型接入点和凭证信息

## 需求

### 需求 1：自定义模型配置数据模型

**用户故事：** 作为平台开发者，我希望定义自定义模型配置的数据结构，以便系统能够存储和传递用户的模型配置信息。

#### 验收标准

1. THE Custom_Model_Config SHALL 包含以下字段：模型接入点 URL（baseUrl）、API Key（apiKey）、模型 ID（modelId）、模型显示名称（modelName）和协议类型（protocolType，取值为 openai、anthropic、gemini 之一）
2. WHEN Custom_Model_Config 的 baseUrl 字段为空或格式不合法时，THE 系统 SHALL 拒绝该配置并返回格式校验错误信息
3. WHEN Custom_Model_Config 的 apiKey 字段为空时，THE 系统 SHALL 拒绝该配置并返回缺少凭证的错误信息
4. WHEN Custom_Model_Config 的 modelId 字段为空时，THE 系统 SHALL 拒绝该配置并返回缺少模型标识的错误信息
5. THE Custom_Model_Config SHALL 将 protocolType 默认值设为 openai，因为大多数自定义接入点兼容 OpenAI 协议

### 需求 2：Open Code 配置文件生成

**用户故事：** 作为使用 Open Code 的开发者，我希望系统根据我的自定义模型配置自动生成 `opencode.json` 配置文件，以便 Open Code 连接到我指定的 LLM 服务。

#### 验收标准

1. WHEN 用户为 Open_Code 提供 Custom_Model_Config 时，THE Config_Generator SHALL 生成符合 Open Code 配置格式的 `opencode.json` 文件，包含 provider 定义和 model 指定
2. THE Config_Generator SHALL 在生成的 `opencode.json` 中使用 `@ai-sdk/openai-compatible` 作为 npm 包，将 baseUrl 设为用户提供的 Model_Endpoint，将 apiKey 设为环境变量引用格式 `{env:CUSTOM_MODEL_API_KEY}`
3. THE Config_Generator SHALL 将生成的 `opencode.json` 文件写入 CLI 进程的工作目录中
4. WHEN 工作目录中已存在 `opencode.json` 文件时，THE Config_Generator SHALL 将自定义 provider 配置合并到现有配置中，保留用户已有的其他配置项
5. THE Config_Generator SHALL 在生成的配置中将 `model` 字段设为 `custom-provider/{modelId}` 格式，使 Open Code 默认使用用户指定的模型

### 需求 3：Qwen Code 配置文件生成

**用户故事：** 作为使用 Qwen Code 的开发者，我希望系统根据我的自定义模型配置自动生成 `settings.json` 配置文件，以便 Qwen Code 连接到我指定的 LLM 服务。

#### 验收标准

1. WHEN 用户为 Qwen_Code 提供 Custom_Model_Config 时，THE Config_Generator SHALL 生成符合 Qwen Code 配置格式的 `settings.json` 文件，包含 modelProviders 定义
2. THE Config_Generator SHALL 根据 protocolType 字段选择对应的 Qwen Code provider 类型（openai 对应 openai provider，anthropic 对应 anthropic provider）
3. THE Config_Generator SHALL 将生成的 `settings.json` 文件写入 `{workingDirectory}/.qwen/` 目录中
4. WHEN `.qwen/settings.json` 文件已存在时，THE Config_Generator SHALL 将自定义模型配置合并到现有 modelProviders 中，保留用户已有的其他配置项
5. THE Config_Generator SHALL 在生成的配置中通过 env 字段设置对应的 API Key 环境变量（如 OPENAI_API_KEY）

### 需求 4：配置注入与进程启动集成

**用户故事：** 作为平台开发者，我希望在 CLI 进程启动时自动注入自定义模型配置，以便用户无需手动配置即可使用自定义模型。

#### 验收标准

1. WHEN 用户选择了 Custom_Model_Config 并启动 CLI 会话时，THE 系统 SHALL 在 CLI 进程启动前调用 Config_Generator 生成配置文件并写入工作目录
2. THE 系统 SHALL 将 API Key 通过环境变量（CUSTOM_MODEL_API_KEY）注入到 CLI 进程的启动环境中，环境变量通过 AcpProcess 的 extraEnv 参数传递
3. WHEN 用户未提供 Custom_Model_Config 时，THE 系统 SHALL 按照现有逻辑启动 CLI 进程，不生成额外配置文件，不注入额外环境变量
4. THE 系统 SHALL 确保配置注入逻辑与现有的 RuntimeAdapter 接口兼容，在 Local_Runtime 和 K8s_Runtime 中均能正常工作
5. WHEN CLI 进程启动失败时，THE 系统 SHALL 清理已生成的配置文件，防止残留配置影响后续会话

### 需求 5：前端自定义模型配置表单

**用户故事：** 作为 HiCli 用户，我希望在选择 CLI 工具后能够配置自定义模型接入点和 API Key，以便使用我自己的 LLM 服务。

#### 验收标准

1. WHEN 用户在 HiCli 中选择 Open_Code 或 Qwen_Code 作为 CLI 工具时，THE Custom_Model_Form SHALL 在连接配置区域展示"使用自定义模型"的可选开关
2. WHEN 用户开启"使用自定义模型"开关时，THE Custom_Model_Form SHALL 展示模型接入点 URL 输入框、API Key 输入框、模型 ID 输入框、模型显示名称输入框和协议类型选择器
3. THE Custom_Model_Form SHALL 对 API Key 输入框使用密码类型（password type），防止凭证明文展示
4. WHEN 用户提交自定义模型配置时，THE Custom_Model_Form SHALL 对 baseUrl 进行 URL 格式校验，对必填字段进行非空校验
5. WHEN 用户选择的 CLI 工具不支持自定义模型配置时，THE Custom_Model_Form SHALL 隐藏"使用自定义模型"开关
6. THE Custom_Model_Form SHALL 使用 himarket-frontend 的 Ant Design 组件（Form、Input、Switch、Select）和 Tailwind CSS 样式体系

### 需求 6：自定义模型配置通过 WebSocket 传递

**用户故事：** 作为平台开发者，我希望前端的自定义模型配置能够通过 WebSocket 连接参数传递给后端，以便后端在启动 CLI 进程时使用。

#### 验收标准

1. WHEN 用户配置了自定义模型并点击连接时，THE 系统 SHALL 将 Custom_Model_Config 序列化为 JSON 字符串，通过 WebSocket URL 的查询参数（customModelConfig）传递给后端
2. THE AcpHandshakeInterceptor SHALL 从 WebSocket 握手请求中解析 customModelConfig 参数，反序列化为 Custom_Model_Config 对象，并存储到 WebSocket session attributes 中
3. WHEN customModelConfig 参数不存在或为空时，THE AcpHandshakeInterceptor SHALL 跳过自定义模型配置解析，按现有逻辑处理连接
4. IF customModelConfig 参数的 JSON 格式不合法，THEN THE AcpHandshakeInterceptor SHALL 记录警告日志并忽略该参数，按现有逻辑继续处理连接

### 需求 7：CLI Provider 自定义模型支持标识

**用户故事：** 作为平台开发者，我希望在 CLI Provider 配置中标识哪些工具支持自定义模型配置，以便前端根据此标识决定是否展示配置表单。

#### 验收标准

1. THE CliProviderConfig SHALL 新增 supportsCustomModel 布尔字段，标识该 CLI 工具是否支持自定义模型配置
2. THE CliProviderController SHALL 在 CliProviderInfo 响应中包含 supportsCustomModel 字段，供前端使用
3. WHEN supportsCustomModel 为 true 时，THE 系统 SHALL 允许用户为该 CLI 工具配置自定义模型
4. THE 系统 SHALL 在 application.yml 中为 Open_Code 和 Qwen_Code 的 provider 配置设置 supportsCustomModel 为 true，其他 CLI 工具默认为 false

### 需求 8：配置文件序列化与反序列化

**用户故事：** 作为平台开发者，我希望配置文件的生成和解析逻辑是可靠的，以便确保生成的配置文件能被 CLI 工具正确读取。

#### 验收标准

1. THE Config_Generator SHALL 使用 JSON 序列化生成配置文件，确保输出格式符合对应 CLI 工具的配置规范
2. FOR ALL 合法的 Custom_Model_Config 对象，序列化为 JSON 再反序列化 SHALL 产生等价的对象（往返一致性）
3. THE Config_Generator SHALL 在合并已有配置文件时，先反序列化现有文件内容，合并自定义配置后再序列化写回，确保不破坏现有配置结构
4. IF 现有配置文件内容不是合法 JSON，THEN THE Config_Generator SHALL 记录警告日志并使用全新配置覆盖该文件
