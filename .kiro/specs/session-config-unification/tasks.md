# 实现计划：会话配置统一化

## 概述

将 HiCli 和 HiCoding 的会话配置传递逻辑统一为"前端只传标识符，后端统一解析完整配置"的终态架构。实现顺序：后端数据结构重构 → 后端解析服务 → 后端 Config Generator 适配 → 前端类型定义 → 前端组件简化。

## Tasks

- [x] 1. 后端：CliSessionConfig 数据结构重构与 ResolvedSessionConfig 新建
  - [x] 1.1 重构 `CliSessionConfig` 类
    - 修改 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/CliSessionConfig.java`
    - 新增 `modelProductId`（String 类型）字段
    - 移除 `customModelConfig` 字段
    - 简化 `McpServerEntry` 内部类：仅保留 `productId`（String）和 `name`（String），移除 `url`、`transportType`、`headers` 字段
    - 简化 `SkillEntry` 内部类：仅保留 `productId`（String）和 `name`（String），移除 `skillMdContent`、`files` 字段及 `SkillFileEntry` 内部类
    - _需求: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 1.2 新建 `ResolvedSessionConfig` 类
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/` 下新建 `ResolvedSessionConfig.java`
    - 包含 `customModelConfig`（CustomModelConfig）、`mcpServers`（List\<ResolvedMcpEntry\>）、`skills`（List\<ResolvedSkillEntry\>）、`authToken`（String）
    - 内部类 `ResolvedMcpEntry` 包含 `name`（String）、`url`（String）、`transportType`（String）、`headers`（Map\<String, String\>）
    - 内部类 `ResolvedSkillEntry` 包含 `name`（String）和 `files`（List\<SkillFileContentResult\>）
    - _需求: 1.6_

  - [x] 1.3 更新 `AcpHandshakeInterceptor` 移除向后兼容逻辑
    - 修改 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/AcpHandshakeInterceptor.java`
    - 移除对 `customModelConfig` 的解析和向后兼容包装逻辑（不再需要从 URL 解析旧格式）
    - _需求: 1.3_

- [x] 2. 后端：新建 ModelConfigResolver 服务
  - [x] 2.1 创建 `ModelConfigResolver` Spring Service
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/` 下新建 `ModelConfigResolver.java`
    - 注入 `ConsumerService` 和 `ProductService`
    - 实现 `resolve(String modelProductId)` 方法：
      - 调用 `ConsumerService.getPrimaryConsumer()` 获取 consumerId
      - 调用 `ConsumerService.listConsumerSubscriptions(consumerId)` 筛选 APPROVED 状态订阅
      - 调用 `ProductService.getProducts([modelProductId])` 获取产品详情
      - 使用 `BaseUrlExtractor.extract()` 提取 baseUrl
      - 使用 `ProtocolTypeMapper.map()` 提取 protocolType
      - 从产品 feature 提取 modelId
      - 调用 `ConsumerService.getCredential(consumerId)` 提取 apiKey
      - 组装并返回 `CustomModelConfig`
    - 错误处理：无 Primary Consumer 返回 null（DEBUG 日志）；产品未找到/未订阅返回 null（WARN 日志）；apiKey 提取失败返回 null（WARN 日志）
    - _需求: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8_

  - [ ]* 2.2 编写 `ModelConfigResolver` 属性测试：解析完整性
    - **属性 1: ModelConfigResolver 解析完整性**
    - 对于任意非空 modelProductId，当所有依赖服务返回有效数据时，resolve() 返回的 CustomModelConfig 应包含非空的 baseUrl、apiKey、modelId、protocolType
    - **验证需求: 1.6, 2.1**

  - [ ]* 2.3 编写 `ModelConfigResolver` 属性测试：订阅筛选
    - **属性 2: 订阅筛选只保留 APPROVED 状态**
    - 对于任意包含各种状态的订阅列表，ModelConfigResolver 只保留 APPROVED 状态的订阅
    - **验证需求: 2.3**

  - [ ]* 2.4 编写 `ModelConfigResolver` 属性测试：错误处理
    - **属性 7: ModelConfigResolver 错误处理返回空**
    - 对于任意导致解析失败的场景，resolve() 应返回 null 而非抛出异常
    - **验证需求: 2.6, 2.7, 2.8**

  - [ ]* 2.5 编写 `ModelConfigResolver` 单元测试
    - 测试各错误场景的具体示例（无 Primary Consumer、产品未找到、未订阅、apiKey 失败）
    - 测试正常解析流程的具体示例
    - _需求: 2.1, 2.6, 2.7, 2.8_

- [x] 3. 后端：新建 McpConfigResolver 服务
  - [x] 3.1 创建 `McpConfigResolver` Spring Service
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/` 下新建 `McpConfigResolver.java`
    - 注入 `ConsumerService`、`ProductService`、`ContextHolder`
    - 实现 `resolve(List<CliSessionConfig.McpServerEntry> mcpEntries)` 方法：
      - 批量获取产品详情：`ProductService.getProducts(productIds)`
      - 对每个产品：`product.getMcpConfig().toTransportConfig()` → 提取 url、transportType（复用 `CliProviderController.buildMarketMcpInfo()` 逻辑）
      - 获取认证头：`ConsumerService.getDefaultCredential(userId)` → `credentialContext.copyHeaders()`（复用 `CliProviderController.extractAuthHeaders()` 逻辑）
      - 组装 `ResolvedSessionConfig.ResolvedMcpEntry`（name、url、transportType、headers）
    - 错误处理：产品未找到或 mcpConfig 不完整时跳过该条目（WARN 日志）；认证头提取失败时仍返回配置但 headers 为空（DEBUG 日志）
    - _需求: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ]* 3.2 编写 `McpConfigResolver` 单元测试
    - 测试正常解析流程（产品存在、mcpConfig 有效）
    - 测试错误场景（产品未找到、mcpConfig 不完整、认证头失败）
    - _需求: 3.1, 3.4, 3.5_

- [x] 4. 后端：改造 CliConfigGenerator 接口和 prepareConfigFiles 流程
  - [x] 4.1 修改 `CliConfigGenerator` 接口签名
    - 修改 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/CliConfigGenerator.java`
    - `generateSkillConfig` 参数类型从 `List<CliSessionConfig.SkillEntry>` 改为 `List<ResolvedSessionConfig.ResolvedSkillEntry>`
    - `generateMcpConfig` 参数类型从 `List<CliSessionConfig.McpServerEntry>` 改为 `List<ResolvedSessionConfig.ResolvedMcpEntry>`
    - _需求: 1.9_

  - [x] 4.2 适配 `QwenCodeConfigGenerator`
    - 修改 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/QwenCodeConfigGenerator.java`
    - `generateSkillConfig`：接收 `ResolvedSkillEntry`（含 name + files），添加 NPE 防护
    - `generateMcpConfig`：接收 `ResolvedMcpEntry`（含 name + url + transportType + headers），适配字段访问
    - _需求: 5.1, 5.2, 5.3_

  - [x] 4.3 适配 `ClaudeCodeConfigGenerator`
    - 修改 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/ClaudeCodeConfigGenerator.java`
    - 同 4.2 的改造逻辑
    - _需求: 5.1, 5.2, 5.3_

  - [x] 4.4 适配 `OpenCodeConfigGenerator`
    - 修改 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/OpenCodeConfigGenerator.java`
    - 同 4.2 的改造逻辑
    - _需求: 5.1, 5.2, 5.3_

  - [x] 4.5 改造 `AcpWebSocketHandler.prepareConfigFiles` 方法
    - 修改 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/AcpWebSocketHandler.java`
    - 注入 `ModelConfigResolver`、`McpConfigResolver` 和 `SkillPackageService`
    - 改造流程：
      1. 若 `modelProductId` 非空，调用 `ModelConfigResolver.resolve()` 获取 `CustomModelConfig`
      2. 若 `mcpServers` 非空，调用 `McpConfigResolver.resolve()` 解析完整 MCP 连接配置
      3. 遍历 skills，对每个有 `productId` 的条目调用 `SkillPackageService.getAllFiles()` 下载文件，失败时记录 ERROR 日志并跳过
      4. 构建 `ResolvedSessionConfig`（透传 authToken）
      5. 将 `ResolvedSessionConfig` 传递给 `CliConfigGenerator`
    - _需求: 1.6, 1.7, 1.8, 1.9, 4.1, 4.2, 4.3_

  - [ ]* 4.6 编写属性测试：Skill 文件下载与传递完整性
    - **属性 3: Skill 文件下载与传递完整性**
    - 对于任意包含 productId 的 SkillEntry 列表，解析后的 ResolvedSkillEntry 文件列表应与 SkillPackageService.getAllFiles() 返回一致
    - **验证需求: 4.1, 4.2**

  - [ ]* 4.7 编写属性测试：Skill 配置生成 NPE 防护
    - **属性 4: Skill 配置生成 NPE 防护**
    - 对于任意 ResolvedSkillEntry 列表，generateSkillConfig() 应跳过 name 为 null 或文件列表为空的条目
    - **验证需求: 5.1, 5.2**

- [x] 5. 检查点 - 后端编译与测试验证
  - 确保所有后端代码编译通过，运行 `./scripts/run.sh` 验证启动成功
  - 确保所有测试通过，ask the user if questions arise.

- [x] 6. 前端：类型定义更新
  - [x] 6.1 更新 `CliSessionConfig`、`McpServerEntry` 和 `SkillEntry` TypeScript 类型
    - 修改 `himarket-web/himarket-frontend/src/lib/apis/cliProvider.ts`
    - `CliSessionConfig`：新增 `modelProductId?: string`，移除 `customModelConfig` 字段
    - `McpServerEntry`：仅保留 `productId: string` 和 `name: string`，移除 `url`、`transportType`、`headers` 字段
    - `SkillEntry`：仅保留 `productId: string` 和 `name: string`，移除 `skillMdContent` 和 `files` 字段
    - 移除不再需要的 `SkillFileEntry` 类型（如果仅被 SkillEntry 使用）
    - _需求: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [x] 6.2 更新 `CodingConfig` 类型
    - 修改 `himarket-web/himarket-frontend/src/types/coding.ts`
    - 将 `modelId: string | null` 改为 `modelProductId: string | null`（存储市场产品 ID 而非底层 modelId）
    - 同步更新 `DEFAULT_CONFIG` 和 `isConfigComplete` 函数
    - _需求: 8.1_

- [x] 7. 前端：HiCoding ConfigSidebar 简化
  - [x] 7.1 简化 `ConfigSidebar` 组件
    - 修改 `himarket-web/himarket-frontend/src/components/coding/ConfigSidebar.tsx`
    - 移除 `marketModels`、`marketModelsApiKey` 状态变量及 `fetchModels` 中的 apiKey 获取逻辑
    - 移除 `mcpAuthHeaders` 状态变量及 `extractAuthHeaders` 获取逻辑
    - `buildFinalConfig()` 中模型配置改为传递 `modelProductId`（产品 ID 字符串），不再拼装 `customModelConfig` 对象
    - `buildFinalConfig()` 中 MCP 条目改为传递 `{ productId, name }`，不再包含 url、transportType、headers
    - Skill 条目改为传递 `{ productId, name }`，不再包含文件内容
    - 模型下拉列表展示产品名称（`name`），value 使用 `productId` 而非 `modelId`
    - _需求: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [x] 7.2 更新 `Coding.tsx` 中对 `config.modelId` 的引用
    - 将所有 `config.modelId` 引用改为 `config.modelProductId`
    - _需求: 6.1_

- [x] 8. 前端：HiCli CliSelector 及子组件对齐
  - [x] 8.1 简化 `MarketModelSelector` 组件
    - 修改 `himarket-web/himarket-frontend/src/components/hicli/MarketModelSelector.tsx`
    - `onChange` 回调改为返回 `{ productId, name }` 而非 `CustomModelFormData`
    - 移除 apiKey 获取和 `CustomModelFormData` 拼装逻辑
    - 下拉列表展示产品名称（`name`），不再展示 `modelId`
    - _需求: 7.2, 7.3_

  - [x] 8.2 简化 `MarketSkillSelector` 组件
    - 修改 `himarket-web/himarket-frontend/src/components/hicli/MarketSkillSelector.tsx`
    - 选中 Skill 时仅传递 `{ productId, name }`
    - 移除 `getSkillAllFiles()` 和 `downloadSkill()` 调用
    - 移除 `downloadedContent` 缓存和 `downloadingIds` 状态
    - _需求: 7.1_

  - [x] 8.3 简化 `MarketMcpSelector` 组件
    - 修改 `himarket-web/himarket-frontend/src/components/hicli/MarketMcpSelector.tsx`
    - 选中 MCP 时仅传递 `{ productId, name }`，不再包含 url、transportType
    - 移除 authHeaders 的拼装逻辑
    - _需求: 7.6_

  - [x] 8.4 更新 `CliSelector` 组件的 `handleConnect`
    - 修改 `himarket-web/himarket-frontend/src/components/common/CliSelector.tsx`
    - `handleConnect()` 中模型配置改为传递 `modelProductId`（产品 ID 字符串），不再传递 `customModelConfig`
    - MCP 条目改为传递 `{ productId, name }`，不再包含 url、transportType、headers
    - Skill 条目改为传递 `{ productId, name }`
    - 更新 `handleMarketModelChange` 回调类型（接收 `{ productId, name }` 而非 `CustomModelFormData`）
    - authToken 保持不变
    - _需求: 7.4, 7.5, 7.6, 7.7_

- [x] 9. 前端：清理不再需要的 API 函数和类型
  - [x] 9.1 清理 `cliProvider.ts` 中不再需要的导出
    - 移除 `downloadSkill` 函数（如果仅被 MarketSkillSelector 使用）
    - 移除 `getSkillAllFiles` 函数（如果仅被 MarketSkillSelector 使用）
    - 移除 `SkillFileEntry` 类型（如果仅被 SkillEntry 使用）
    - 评估 `MarketModelsResponse.apiKey` 是否仍需要（ConfigSidebar 和 MarketModelSelector 都不再使用）
    - _需求: 8.4, 8.5_

- [x] 10. 后端：更新现有测试适配新数据结构
  - [x] 10.1 更新 `QwenCodeConfigGeneratorSkillTest`
    - 修改 `himarket-server/src/test/java/com/alibaba/himarket/service/acp/QwenCodeConfigGeneratorSkillTest.java`
    - 将测试中的 `CliSessionConfig.SkillEntry` 改为 `ResolvedSessionConfig.ResolvedSkillEntry`
    - 适配新的字段结构（productId + name + files 替代 skillMdContent）
    - _需求: 1.6, 5.3_

  - [x] 10.2 更新 `InitPhasesTest` 和 `ConfigInjectionPhasePropertyTest`
    - 修改相关测试文件中对 `CliSessionConfig` 的使用，移除 `customModelConfig` 相关断言
    - 适配 MCP 相关测试（McpServerEntry 改为标识符结构）
    - _需求: 1.3, 1.5_

- [x] 11. 最终检查点 - 全量编译与端到端验证
  - 确保前后端编译通过，所有测试通过
  - 重启后端 `./scripts/run.sh`，通过 WebSocket 验证完整的会话配置流程
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- Qoder CLI（`QoderCliConfigGenerator`）不在本次改造范围内，保持现有逻辑不变
- 属性测试使用 jqwik（后端）和 fast-check（前端）
- 不考虑向后兼容，直接面向终态方案
