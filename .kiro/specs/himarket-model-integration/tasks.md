# 实现计划：HiMarket 模型市场集成

## 概述

在现有 CLI 自定义模型配置基础上，增加从模型市场直接选择已发布模型的能力。后端新增一个聚合接口，前端在公共 CliSelector 中新增 MarketModelSelector 组件，复用现有 CustomModelConfig 和 ConfigGenerator 机制。

## 任务

- [x] 1. 后端：创建 BaseUrlExtractor 和 ProtocolTypeMapper 工具类
  - [x] 1.1 创建 BaseUrlExtractor 工具类
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/` 下创建 `BaseUrlExtractor.java`
    - 实现 `extract(List<HttpRouteResult> routes)` 静态方法
    - 从 routes[0].domains[0] 提取 protocol、domain、port
    - 从 routes[0].match.path.value 提取 pathPrefix（去掉 `/chat/completions` 后缀）
    - 端口处理：null 或标准端口（http:80, https:443）时省略，非标准端口时包含
    - 路由数据不完整时返回 null
    - _Requirements: 1.7, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x] 1.2 创建 ProtocolTypeMapper 工具类
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/` 下创建 `ProtocolTypeMapper.java`
    - 实现 `map(List<String> aiProtocols)` 静态方法
    - 包含 "OpenAI" 映射为 "openai"，包含 "Anthropic" 映射为 "anthropic"，其他默认 "openai"
    - _Requirements: 1.8_

  - [ ]* 1.3 编写 BaseUrl 提取正确性属性测试
    - **Property 1: BaseUrl 提取正确性**
    - **Validates: Requirements 1.7, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6**

  - [ ]* 1.4 编写协议类型映射正确性属性测试
    - **Property 2: 协议类型映射正确性**
    - **Validates: Requirements 1.8**

- [x] 2. 后端：实现 market-models 接口
  - [x] 2.1 创建 MarketModelInfo 和 MarketModelsResponse DTO
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/dto/result/cli/` 下创建 `MarketModelInfo.java` 和 `MarketModelsResponse.java`
    - MarketModelInfo 包含 productId、name、modelId、baseUrl、protocolType、description 字段
    - MarketModelsResponse 包含 models 列表和 apiKey 字段
    - _Requirements: 1.4, 1.10_

  - [x] 2.2 在 CliProviderController 中新增 listMarketModels 接口
    - 新增 `GET /cli-providers/market-models` 端点，使用 `@DeveloperAuth` 注解
    - 注入 ConsumerService 和 ProductService
    - 实现逻辑：获取 Primary Consumer → 获取订阅列表 → 筛选 MODEL_API + APPROVED → 批量获取产品详情 → 提取 apiKey → 组装响应
    - 无 Primary Consumer 时返回空列表和 null apiKey
    - 产品数据不完整时跳过并记录警告日志
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.9, 1.10_

  - [ ]* 2.3 编写订阅筛选正确性属性测试
    - **Property 3: 订阅筛选正确性**
    - **Validates: Requirements 1.3**

  - [ ]* 2.4 编写后端单元测试
    - 测试 listMarketModels 接口：无 Primary Consumer、无订阅、有订阅、产品数据不完整等场景
    - 测试 BaseUrlExtractor 具体示例：各种 port 和 path 组合
    - 测试 ProtocolTypeMapper 具体示例
    - _Requirements: 1.5, 1.6, 1.9_

- [x] 3. 检查点 - 确保后端测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 4. 前端：扩展 API 层和类型定义
  - [x] 4.1 在 cliProvider.ts 中新增类型和 API 函数
    - 新增 `MarketModelInfo` 接口（productId、name、modelId、baseUrl、protocolType、description）
    - 新增 `MarketModelsResponse` 接口（models、apiKey）
    - 新增 `getMarketModels()` 函数，调用 `GET /cli-providers/market-models`
    - _Requirements: 4.1, 4.2, 4.3_

- [x] 5. 前端：创建 MarketModelSelector 组件
  - [x] 5.1 创建 MarketModelSelector 组件
    - 在 `himarket-web/himarket-frontend/src/components/hicli/` 下创建 `MarketModelSelector.tsx`
    - 接收 enabled 和 onChange props
    - enabled 为 true 时调用 getMarketModels 获取数据
    - 展示加载状态、错误状态、空列表提示、apiKey 为 null 提示
    - 模型列表非空时展示 Select 下拉选择器（显示产品名称和模型 ID）
    - 用户选择模型后组装 CustomModelFormData 并通过 onChange 回调
    - 使用 Ant Design 组件和 Tailwind CSS 样式
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10_

  - [ ]* 5.2 编写模型市场数据到 CustomModelFormData 组装正确性属性测试
    - **Property 4: 组装正确性**
    - **Validates: Requirements 2.5, 3.5**

- [x] 6. 前端：改造 CliSelector 支持模式切换
  - [x] 6.1 改造 CliSelector 公共组件
    - 引入 `ModelConfigMode` 类型（'none' | 'custom' | 'market'）替代现有的 `customModelEnabled` 布尔状态
    - 将现有"使用自定义模型"开关改为模式切换逻辑的一部分
    - 新增"使用模型市场模型"开关，与"使用自定义模型"互斥
    - 根据 modelConfigMode 决定显示 CustomModelForm 还是 MarketModelSelector
    - 模式切换时清除前一个模式的配置数据
    - 切换 CLI 工具时重置 modelConfigMode 为 'none'
    - 连接时统一从 customModelData 获取配置（无论来源是手动还是模型市场）
    - _Requirements: 2.1, 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ]* 6.2 编写模式切换状态互斥性属性测试
    - **Property 5: 模式切换状态互斥性**
    - **Validates: Requirements 3.1, 3.2, 3.3**

  - [ ]* 6.3 编写前端组件单元测试
    - 测试 MarketModelSelector 渲染：加载中、成功、空列表、apiKey 为 null、接口失败
    - 测试 CliSelector 模式切换：custom ↔ market ↔ none 的状态变化
    - _Requirements: 2.3, 2.6, 2.7, 2.8, 3.1, 3.2_

- [x] 7. 最终检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号以确保可追溯性
- 后端改动集中在 CliProviderController 和两个新工具类，不修改现有 CustomModelConfig/ConfigGenerator 逻辑
- 前端改动集中在公共 CliSelector 组件，HiCli/HiCoding/HiWork 三个模块自动获得新能力
- 属性测试验证通用正确性属性，单元测试验证具体示例和边界条件
