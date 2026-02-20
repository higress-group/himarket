# 需求文档

## 简介

HiMarket 平台的 HiCli、HiCoding、HiWork 三个模块均通过公共的 CliSelector 组件支持用户手动输入自定义模型配置（baseUrl、apiKey、modelId 等）来连接自定义 LLM 服务。本功能在此基础上，增加直接使用 HiMarket 模型市场中已发布模型的能力。

用户在 CliSelector 中可以选择"使用模型市场模型"选项（与现有"使用自定义模型"并列），系统自动从模型市场产品数据中提取 baseUrl、modelId、protocolType 等信息，并从开发者的 consumer credential 中获取 apiKey，最终组装成 CustomModelConfig，复用现有的 WebSocket 传递和后端配置生成逻辑。

由于 HiCli、HiCoding、HiWork 三个模块共用 CliSelector 公共组件，本功能的前端改动集中在公共组件层，三个模块自动获得模型市场模型选择能力。

核心原则：
- 不破坏现有的手动自定义模型配置功能
- 复用现有的 CustomModelConfig 数据模型和 ConfigGenerator 机制
- 仅对已订阅且已发布的 MODEL_API 类型产品可用
- 前端负责从模型市场数据中提取并组装 CustomModelConfig
- 充分考虑边界条件，提供友好的用户提示

## 术语表

- **Model_Market**：模型市场，HiMarket 平台中发布和管理 MODEL_API 类型产品的模块
- **MODEL_API_Product**：模型 API 产品，模型市场中类型为 MODEL_API 的已发布产品，包含模型路由、协议和特性信息
- **Consumer**：消费者，开发者在 HiMarket 中注册的 API 消费实体，拥有凭证（apiKey）和订阅关系
- **Primary_Consumer**：主消费者，开发者的默认 Consumer，用于获取 apiKey
- **Subscription**：订阅关系，Consumer 与 Product 之间的绑定，表示该消费者有权使用该产品
- **Custom_Model_Config**：自定义模型配置（已有），包含 baseUrl、apiKey、modelId、modelName、protocolType
- **Config_Generator**：配置生成器（已有），根据 CLI 工具类型生成对应格式的配置文件
- **CliSelector**：CLI 工具选择器（已有公共组件），HiCli、HiCoding、HiWork 三个模块共用
- **MarketModelSelector**：模型市场模型选择器，新增前端 UI 组件，用于展示和选择模型市场中的已订阅模型
- **BaseUrl_Extractor**：BaseUrl 提取逻辑，从 MODEL_API_Product 的路由配置中拼接出模型接入点 URL

## 需求

### 需求 1：后端提供模型市场模型列表接口

**用户故事：** 作为 HiCli/HiCoding/HiWork 用户，我希望系统提供一个轻量接口返回我已订阅的模型市场模型信息，以便前端展示可选模型列表。

#### 验收标准

1. THE 系统 SHALL 在 CliProviderController 中新增 `GET /cli-providers/market-models` 接口，返回当前开发者已订阅的 MODEL_API 类型产品列表及开发者的 apiKey
2. THE 接口 SHALL 使用 `@DeveloperAuth` 注解，要求开发者身份认证
3. WHEN 开发者调用该接口时，THE 系统 SHALL 通过 Primary_Consumer 获取开发者的订阅列表，筛选出 productType 为 MODEL_API 且 status 为 APPROVED 的订阅
4. THE 接口 SHALL 对每个已订阅的 MODEL_API 产品返回以下信息：productId、产品名称（name）、模型 ID（从 feature.modelFeature.model 提取）、baseUrl（从路由配置拼接）、协议类型（从 aiProtocols 映射）和模型描述（description）
5. WHEN 开发者没有 Primary_Consumer 时，THE 接口 SHALL 返回空模型列表和 apiKey 为 null
6. WHEN 开发者有 Primary_Consumer 但没有任何 MODEL_API 订阅时，THE 接口 SHALL 返回空模型列表，apiKey 正常返回（如有凭证）
7. THE 接口返回的 baseUrl SHALL 从产品的 modelConfig.modelAPIConfig.routes[0].domains[0] 中按 `{protocol}://{domain}:{port}{pathPrefix}` 格式拼接，其中 pathPrefix 为路由 path 去掉 `/chat/completions` 后缀的部分；当 port 为 null 或标准端口（http 对应 80，https 对应 443）时省略端口部分
8. THE 接口返回的协议类型 SHALL 根据 aiProtocols 字段映射：包含 "OpenAI" 的值映射为 "openai"，包含 "Anthropic" 的值映射为 "anthropic"，其他情况默认为 "openai"
9. WHEN 产品的 modelConfig 或 routes 数据不完整时，THE 接口 SHALL 跳过该产品并记录警告日志，继续处理其他产品
10. THE 接口 SHALL 在响应中包含开发者 Primary_Consumer 的 apiKey 字段（从 credential 的 apiKeyConfig.credentials[0].apiKey 提取），当 credential 或 apiKeyConfig 为空时返回 null

### 需求 2：前端模型市场模型选择器组件

**用户故事：** 作为 HiCli/HiCoding/HiWork 用户，我希望在 CLI 工具选择界面中能够选择模型市场中的已发布模型，以便无需手动输入配置信息即可使用模型市场模型。

#### 验收标准

1. WHEN 用户在 CliSelector 中选择支持自定义模型的 CLI 工具时，THE 系统 SHALL 在现有"使用自定义模型"开关旁展示"使用模型市场模型"开关，两个开关互斥（开启一个自动关闭另一个）
2. WHEN 用户开启"使用模型市场模型"开关时，THE MarketModelSelector SHALL 调用 `GET /cli-providers/market-models` 接口获取已订阅模型列表
3. WHEN 模型列表加载中时，THE MarketModelSelector SHALL 显示加载指示器
4. WHEN 模型列表加载成功且非空时，THE MarketModelSelector SHALL 以下拉选择器形式展示模型列表，每个选项显示产品名称和模型 ID
5. WHEN 用户选择一个模型时，THE MarketModelSelector SHALL 自动从接口返回数据中提取 baseUrl、modelId、protocolType，并与接口返回的 apiKey 组合，组装成完整的 CustomModelFormData 对象
6. WHEN 接口返回的 apiKey 为 null 时，THE MarketModelSelector SHALL 显示提示信息"请先在模型市场中配置 Consumer 凭证"，并禁用连接按钮
7. WHEN 模型列表为空时，THE MarketModelSelector SHALL 显示提示信息"暂无已订阅的模型，请先在模型市场中订阅模型"
8. WHEN 接口调用失败时，THE MarketModelSelector SHALL 显示错误信息并提供重试按钮
9. WHEN 用户未登录（匿名访问）时，THE MarketModelSelector SHALL 显示提示信息"请先登录以使用模型市场模型"
10. THE MarketModelSelector SHALL 使用 Ant Design 组件（Select、Switch、Alert）和 Tailwind CSS 样式体系，与现有 CustomModelForm 风格一致

### 需求 3：前端模型配置模式切换与状态管理

**用户故事：** 作为 HiCli/HiCoding/HiWork 用户，我希望在手动自定义模型和模型市场模型之间自由切换，且切换时状态正确清理。

#### 验收标准

1. WHEN 用户从"使用模型市场模型"切换到"使用自定义模型"时，THE 系统 SHALL 清除模型市场选择状态，展示手动配置表单
2. WHEN 用户从"使用自定义模型"切换到"使用模型市场模型"时，THE 系统 SHALL 清除手动配置表单数据，展示模型市场选择器
3. WHEN 用户关闭两个开关时，THE 系统 SHALL 清除所有自定义模型配置状态，连接时不传递 customModelConfig 参数
4. WHEN 用户切换选中的 CLI 工具时，THE 系统 SHALL 重置模型配置模式和相关状态
5. THE 系统 SHALL 确保无论通过哪种方式（手动配置或模型市场选择）产生的配置，均使用完全相同的 CustomModelConfig 数据结构和 WebSocket 传递路径，后端无需区分配置来源

### 需求 4：前端 API 层扩展

**用户故事：** 作为前端开发者，我希望有封装好的 API 函数来调用模型市场模型列表接口，以便组件可以方便地获取数据。

#### 验收标准

1. THE 前端 SHALL 在 cliProvider.ts 中新增 `getMarketModels()` API 函数，调用 `GET /cli-providers/market-models` 接口
2. THE API 函数 SHALL 定义 `MarketModelInfo` 类型接口，包含 productId、name、modelId、baseUrl、protocolType、description 字段
3. THE API 函数 SHALL 定义 `MarketModelsResponse` 类型接口，包含 models（MarketModelInfo 数组）和 apiKey（string 或 null）字段

### 需求 5：BaseUrl 提取逻辑的正确性

**用户故事：** 作为平台开发者，我希望从模型产品路由配置中提取 baseUrl 的逻辑是可靠的，以便生成的配置能被 CLI 工具正确使用。

#### 验收标准

1. FOR ALL 包含有效路由配置的 MODEL_API_Product，THE BaseUrl_Extractor SHALL 从 routes[0].domains[0] 中正确拼接出 baseUrl
2. WHEN 路由 path 以 `/chat/completions` 结尾时，THE BaseUrl_Extractor SHALL 去掉该后缀作为 pathPrefix（例如 `/qwen3-max/v1/chat/completions` 提取为 `/qwen3-max/v1`）
3. WHEN 路由 path 不以 `/chat/completions` 结尾时，THE BaseUrl_Extractor SHALL 使用完整 path 作为 pathPrefix
4. WHEN domain 的 port 为 null 时，THE BaseUrl_Extractor SHALL 省略端口部分（例如 `http://api.example.com/v1`）
5. WHEN domain 的 port 为标准端口（http 对应 80，https 对应 443）时，THE BaseUrl_Extractor SHALL 省略端口部分
6. WHEN domain 的 port 为非标准端口时，THE BaseUrl_Extractor SHALL 在 URL 中包含端口（例如 `http://api.example.com:8080/v1`）
