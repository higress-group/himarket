# 实现计划：CLI MCP 与 Skill 市场集成

## 概述

基于设计文档，将功能拆分为后端数据模型与接口、后端配置生成器扩展、前端组件与 API、集成联调四个阶段，逐步实现 MCP Server 和 Agent Skill 在 HiCli/HiCoding/HiWork 三个场景中的支持。

## 任务

- [x] 1. 后端数据模型与 DTO 定义
  - [x] 1.1 创建 CliSessionConfig 数据模型
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/` 下创建 `CliSessionConfig.java`
    - 包含 `customModelConfig`（CustomModelConfig）、`mcpServers`（List\<McpServerEntry\>）、`skills`（List\<SkillEntry\>）字段
    - 内部类 `McpServerEntry`（name, url, transportType, headers）和 `SkillEntry`（name, skillMdContent）
    - _Requirements: 3.1, 3.2, 3.3_

  - [x] 1.2 创建 MarketMcpInfo 和 MarketMcpsResponse DTO
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/dto/result/cli/` 下创建 `MarketMcpInfo.java` 和 `MarketMcpsResponse.java`
    - MarketMcpInfo 包含 productId, name, url, transportType, description
    - MarketMcpsResponse 包含 mcpServers 列表和 authHeaders Map
    - _Requirements: 1.4, 1.9_

  - [x] 1.3 创建 MarketSkillInfo DTO
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/dto/result/cli/` 下创建 `MarketSkillInfo.java`
    - 包含 productId, name, description, skillTags
    - _Requirements: 2.4_

  - [ ]* 1.4 编写 CliSessionConfig 序列化往返属性测试
    - **Property 7: CliSessionConfig 序列化往返一致性**
    - **Validates: Requirements 13.1, 13.2, 13.3, 4.1, 4.2**

- [x] 2. 后端 CliProviderConfig 和 CliProviderInfo 扩展
  - [x] 2.1 扩展 CliProviderConfig 新增 supportsMcp 和 supportsSkill 字段
    - 在 `AcpProperties.CliProviderConfig` 中新增 `supportsMcp`（默认 false）和 `supportsSkill`（默认 false）布尔字段及 getter/setter
    - _Requirements: 11.1, 11.2_

  - [x] 2.2 扩展 CliProviderInfo record 和 listProviders 方法
    - 在 `CliProviderController.CliProviderInfo` record 中新增 `supportsMcp` 和 `supportsSkill` 字段
    - 更新 `listProviders()` 方法传递新字段
    - _Requirements: 11.3_

  - [x] 2.3 更新 application.yml 为 Qwen Code 配置 supportsMcp 和 supportsSkill
    - 在 `himarket-bootstrap/src/main/resources/application.yml` 中为 `qwen-code` provider 添加 `supports-mcp: true` 和 `supports-skill: true`
    - _Requirements: 11.4_

- [x] 3. 后端 MCP 和 Skill 列表接口
  - [x] 3.1 实现 listMarketMcps 接口
    - 在 `CliProviderController` 中新增 `GET /cli-providers/market-mcps` 接口，使用 `@DeveloperAuth`
    - 获取 Primary Consumer → 获取订阅列表 → 筛选 APPROVED → 批量获取产品详情 → 筛选 MCP_SERVER → 调用 `mcpConfig.toTransportConfig()` 提取 URL 和传输类型 → 获取 CredentialContext 提取 authHeaders
    - 处理无 Consumer、无订阅、mcpConfig 不完整等边界情况
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9_

  - [x] 3.2 实现 listMarketSkills 接口
    - 在 `CliProviderController` 中新增 `GET /cli-providers/market-skills` 接口，无需认证
    - 查询所有已发布的 AGENT_SKILL 类型产品，提取 name, description, skillTags
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [ ]* 3.3 编写 MCP 订阅筛选属性测试
    - **Property 1: MCP 订阅筛选正确性**
    - **Validates: Requirements 1.3**

  - [ ]* 3.4 编写 Skill 发布筛选属性测试
    - **Property 2: Skill 发布筛选正确性**
    - **Validates: Requirements 2.3, 2.4**

  - [ ]* 3.5 编写传输协议类型映射属性测试
    - **Property 8: 传输协议类型映射正确性**
    - **Validates: Requirements 12.2, 12.3**

- [x] 4. Checkpoint - 确保后端接口测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 5. 后端 CliConfigGenerator 接口扩展与 QwenCode 实现
  - [x] 5.1 扩展 CliConfigGenerator 接口新增 default 方法
    - 在 `CliConfigGenerator` 接口中新增 `generateMcpConfig` 和 `generateSkillConfig` 两个 default 方法
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 5.2 实现 QwenCodeConfigGenerator.generateMcpConfig
    - 在 `QwenCodeConfigGenerator` 中实现 MCP 配置注入：读取已有 settings.json → 合并 mcpServers 段（按 name 去重）→ 写回
    - 生成格式：`mcpServers: { "<name>": { url, type, headers } }`
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x] 5.3 实现 QwenCodeConfigGenerator.generateSkillConfig
    - 在 `QwenCodeConfigGenerator` 中实现 Skill 配置注入：创建 `.agents/skills/<kebab-name>/SKILL.md` 文件
    - 实现 `toKebabCase` 工具方法
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [ ]* 5.4 编写 QwenCode MCP 配置生成属性测试
    - **Property 3: QwenCode MCP 配置生成正确性**
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.5**

  - [ ]* 5.5 编写 QwenCode MCP 配置合并属性测试
    - **Property 4: QwenCode MCP 配置合并保留已有条目**
    - **Validates: Requirements 6.4**

  - [ ]* 5.6 编写 Skill 文件写入往返属性测试
    - **Property 5: Skill 文件写入往返一致性**
    - **Validates: Requirements 7.1, 7.2, 7.3**

  - [ ]* 5.7 编写 kebab-case 名称转换属性测试
    - **Property 6: kebab-case 名称转换正确性**
    - **Validates: Requirements 7.4**

- [x] 6. 后端 WebSocket 握手与会话启动扩展
  - [x] 6.1 扩展 AcpHandshakeInterceptor 解析 cliSessionConfig
    - 优先解析 `cliSessionConfig` 查询参数，反序列化为 CliSessionConfig
    - 向后兼容：如果没有 cliSessionConfig 但有 customModelConfig，包装为 CliSessionConfig
    - 两者都不存在时跳过
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

  - [x] 6.2 扩展 AcpWebSocketHandler 调用 MCP 和 Skill 配置注入
    - 在 `afterConnectionEstablished` 中，从 session attributes 获取 CliSessionConfig
    - 依次调用 generateConfig（模型）、generateMcpConfig（MCP）、generateSkillConfig（Skill）
    - 检查 providerConfig 的 supportsMcp 和 supportsSkill 标志
    - _Requirements: 5.4, 3.4_

  - [ ]* 6.3 编写 AcpHandshakeInterceptor 单元测试
    - 测试 cliSessionConfig 解析（正常、缺失、非法 JSON）
    - 测试向后兼容 customModelConfig 参数
    - _Requirements: 4.2, 4.3, 4.5_

- [x] 7. Checkpoint - 确保后端全部测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 8. 前端 API 层与类型定义扩展
  - [x] 8.1 扩展 cliProvider.ts 新增 MCP 和 Skill API 类型与函数
    - 新增 MarketMcpInfo、MarketMcpsResponse、MarketSkillInfo 类型接口
    - 新增 McpServerEntry、SkillEntry、CliSessionConfig 类型接口
    - 新增 `getMarketMcps()`、`getMarketSkills()`、`downloadSkill(productId)` API 函数
    - _Requirements: 4.1, 8.2, 9.2_

  - [x] 8.2 扩展 ICliProvider 接口新增 supportsMcp 和 supportsSkill 字段
    - 在 `cliProvider.ts` 的 `ICliProvider` 接口中新增可选字段
    - _Requirements: 11.3_

  - [x] 8.3 扩展 WsUrlParams 和 buildAcpWsUrl 支持 cliSessionConfig
    - 在 `wsUrl.ts` 的 `WsUrlParams` 中新增 `cliSessionConfig` 字段
    - 在 `buildAcpWsUrl` 中添加 cliSessionConfig 参数支持
    - _Requirements: 10.2_

- [x] 9. 前端 MCP 和 Skill 选择器组件
  - [x] 9.1 创建 MarketMcpSelector 组件
    - 在 `himarket-web/himarket-frontend/src/components/hicli/` 下创建 `MarketMcpSelector.tsx`
    - enabled 变为 true 时调用 getMarketMcps 获取列表
    - 以 Checkbox.Group 展示 MCP Server 列表，支持多选
    - 处理加载中、空列表、错误、未登录等状态
    - 选中时将 authHeaders 注入到每个 McpServerEntry 的 headers 中
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

  - [x] 9.2 创建 MarketSkillSelector 组件
    - 在 `himarket-web/himarket-frontend/src/components/hicli/` 下创建 `MarketSkillSelector.tsx`
    - enabled 变为 true 时调用 getMarketSkills 获取列表
    - 以 Checkbox.Group 展示 Skill 列表，支持多选
    - 选中时调用 downloadSkill 获取 SKILL.md 内容
    - 处理加载中、空列表、错误等状态
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

- [x] 10. 前端 CliSelector 公共组件扩展与集成
  - [x] 10.1 扩展 CliSelector 组件集成 MCP 和 Skill 选择器
    - 新增 mcpEnabled、skillEnabled 状态和对应的 Switch 开关
    - 根据 selectedProvider 的 supportsMcp/supportsSkill 决定是否展示开关
    - 集成 MarketMcpSelector 和 MarketSkillSelector 组件
    - 切换 CLI 工具时重置 MCP/Skill 选择状态
    - _Requirements: 8.1, 9.1_

  - [x] 10.2 扩展 CliSelector 连接逻辑组装 CliSessionConfig
    - 修改 handleConnect 方法，将模型配置、MCP 列表、Skill 列表统一组装为 CliSessionConfig
    - 通过 cliSessionConfig 查询参数传递（替代原有的 customModelConfig）
    - 无任何配置时不传递参数
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

  - [x] 10.3 更新 useHiCliSession 的 connectToCli 方法
    - 将 `customModelConfig` 参数改为 `cliSessionConfig` 参数
    - 更新 `buildHiCliWsUrl` 函数使用 cliSessionConfig
    - 保持 HiCli 页面的 handleSelectCli 回调兼容
    - _Requirements: 10.2_

  - [ ]* 10.4 编写 CliSessionConfig 组装属性测试（前端）
    - **Property 9: CliSessionConfig 组装正确性**
    - **Validates: Requirements 10.1, 10.3**

- [x] 11. 最终 Checkpoint - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 说明

- 标记 `*` 的任务为可选测试任务，可跳过以加速 MVP 开发
- 每个任务引用了具体的需求编号，确保需求覆盖
- Checkpoint 任务确保增量验证
- 属性测试验证通用正确性属性，单元测试验证具体示例和边界条件
