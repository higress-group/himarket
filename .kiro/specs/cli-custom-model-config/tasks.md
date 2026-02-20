# 实现计划：CLI 自定义模型配置

## 概述

基于需求和设计文档，将功能拆分为后端数据模型、配置生成器、WebSocket 集成、前端表单组件等增量任务。每个任务构建在前一个任务之上，最终完成端到端集成。

## 任务

- [x] 1. 创建 CustomModelConfig 数据模型和校验逻辑
  - [x] 1.1 创建 CustomModelConfig Java 类
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/` 下创建 `CustomModelConfig.java`
    - 包含 baseUrl、apiKey、modelId、modelName、protocolType 字段
    - protocolType 默认值为 "openai"
    - 实现 `validate()` 方法：校验 baseUrl 非空且为合法 URL、apiKey 非空、modelId 非空、protocolType 在允许范围内
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [ ]* 1.2 编写 CustomModelConfig 序列化往返属性测试
    - **Property 1: CustomModelConfig 序列化往返一致性**
    - **Validates: Requirements 8.2, 6.1, 6.2**

  - [ ]* 1.3 编写 CustomModelConfig 校验属性测试
    - **Property 2: 非法 CustomModelConfig 校验拒绝**
    - **Validates: Requirements 1.2, 1.3, 1.4**

- [x] 2. 实现 CliConfigGenerator 接口和 OpenCodeConfigGenerator
  - [x] 2.1 创建 CliConfigGenerator 接口
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/` 下创建 `CliConfigGenerator.java` 接口
    - 定义 `supportedProvider()` 和 `generateConfig(String workingDirectory, CustomModelConfig config)` 方法
    - _Requirements: 2.1, 3.1_

  - [x] 2.2 实现 OpenCodeConfigGenerator
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/` 下创建 `OpenCodeConfigGenerator.java`
    - 生成 opencode.json，包含 provider 定义（npm、options、models）和 model 字段
    - 支持与已有 opencode.json 合并
    - 返回环境变量 map（CUSTOM_MODEL_API_KEY）
    - 处理已有文件非法 JSON 的情况（覆盖并记录警告）
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 8.1, 8.3, 8.4_

  - [ ]* 2.3 编写 OpenCode 配置生成正确性属性测试
    - **Property 3: OpenCode 配置生成正确性**
    - **Validates: Requirements 2.1, 2.2, 2.5, 4.2**

  - [ ]* 2.4 编写 OpenCode 配置合并属性测试
    - **Property 4: OpenCode 配置合并保留已有配置**
    - **Validates: Requirements 2.4, 8.3**

- [x] 3. 实现 QwenCodeConfigGenerator
  - [x] 3.1 实现 QwenCodeConfigGenerator
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/` 下创建 `QwenCodeConfigGenerator.java`
    - 生成 .qwen/settings.json，包含 modelProviders 和 env 字段
    - 根据 protocolType 映射到对应的 provider key 和环境变量名
    - 支持与已有 .qwen/settings.json 合并
    - 处理已有文件非法 JSON 的情况
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 8.1, 8.3, 8.4_

  - [ ]* 3.2 编写 QwenCode 配置生成正确性属性测试
    - **Property 5: QwenCode 配置生成正确性**
    - **Validates: Requirements 3.1, 3.2, 3.5, 4.2**

  - [ ]* 3.3 编写 QwenCode 配置合并属性测试
    - **Property 6: QwenCode 配置合并保留已有配置**
    - **Validates: Requirements 3.4, 8.3**

- [x] 4. 检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 5. 后端集成：CliProviderConfig 扩展和配置注入
  - [x] 5.1 扩展 CliProviderConfig 和 CliProviderInfo
    - 在 `AcpProperties.CliProviderConfig` 中新增 `supportsCustomModel` 布尔字段（默认 false）
    - 在 `CliProviderController.CliProviderInfo` record 中新增 `supportsCustomModel` 字段
    - 更新 `listProviders()` 方法传递该字段
    - 在 `application.yml` 中为 opencode 和 qwen-code 设置 `supports-custom-model: true`
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [x] 5.2 扩展 AcpHandshakeInterceptor 解析 customModelConfig
    - 在 `beforeHandshake` 方法中解析 `customModelConfig` 查询参数
    - 使用 Jackson ObjectMapper 反序列化为 CustomModelConfig 对象
    - 存入 WebSocket session attributes
    - 参数缺失或 JSON 非法时记录警告并跳过
    - _Requirements: 6.2, 6.3, 6.4_

  - [x] 5.3 在 AcpWebSocketHandler 中集成配置生成和注入
    - 创建 ConfigGeneratorRegistry（Map<String, CliConfigGenerator>），注册 OpenCode 和 QwenCode 生成器
    - 在 `afterConnectionEstablished` 中，CLI 进程启动前：
      - 从 session attributes 获取 CustomModelConfig
      - 如果存在且 provider 支持自定义模型，调用对应生成器
      - 将生成器返回的环境变量合并到进程环境中
    - CLI 进程启动失败时清理已生成的配置文件
    - 无 customModelConfig 时保持现有逻辑不变
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [ ]* 5.4 编写后端集成单元测试
    - 测试 AcpHandshakeInterceptor 解析 customModelConfig（正常、缺失、非法 JSON）
    - 测试 CliProviderController 响应包含 supportsCustomModel 字段
    - 测试无 customModelConfig 时的向后兼容性
    - _Requirements: 6.2, 6.3, 6.4, 7.2, 4.3_

- [x] 6. 前端：扩展 ICliProvider 接口和 WebSocket URL
  - [x] 6.1 扩展 ICliProvider 接口和 WsUrlParams
    - 在 `cliProvider.ts` 的 `ICliProvider` 接口中新增 `supportsCustomModel?: boolean`
    - 在 `wsUrl.ts` 的 `WsUrlParams` 中新增 `customModelConfig?: string`
    - 在 `buildAcpWsUrl` 函数中处理 customModelConfig 参数
    - _Requirements: 7.2, 6.1_

  - [x] 6.2 创建 CustomModelForm 组件
    - 在 `himarket-web/himarket-frontend/src/components/hicli/` 下创建 `CustomModelForm.tsx`
    - 使用 Ant Design 的 Form、Input、Switch、Select 组件
    - 包含"使用自定义模型"开关
    - 开关开启后展示：baseUrl 输入框、apiKey 密码输入框、modelId 输入框、modelName 输入框、protocolType 选择器
    - 实现前端校验：baseUrl URL 格式、必填字段非空
    - 根据 provider 的 supportsCustomModel 决定是否显示开关
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x] 6.3 集成 CustomModelForm 到 CliSelector
    - 在 `CliSelector.tsx` 中引入 CustomModelForm
    - 当选中的 provider 支持自定义模型时显示表单
    - 连接时将表单数据序列化为 JSON 并通过 WebSocket URL 传递
    - 更新 `useHiCliSession.ts` 的 `buildHiCliWsUrl` 和 `connectToCli` 方法接收 customModelConfig
    - _Requirements: 5.1, 5.5, 6.1_

  - [ ]* 6.4 编写前端组件单元测试
    - 测试 CustomModelForm 渲染（开关显示/隐藏、密码输入框类型）
    - 测试 buildAcpWsUrl 包含 customModelConfig 参数
    - _Requirements: 5.1, 5.3, 5.5, 6.1_

- [x] 7. 最终检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号以确保可追溯性
- 属性测试验证通用正确性属性，单元测试验证具体示例和边界条件
- 检查点确保增量验证
