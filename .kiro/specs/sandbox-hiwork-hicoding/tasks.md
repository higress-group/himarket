# 实现计划：HiWork 和 HiCoding 沙箱模式对接

## 概述

将 HiWork（Quest 页面）和 HiCoding（Coding 页面）对接 K8s 沙箱模式，参考 HiCli 现有实现。改造分为后端配置扩展、前端组件改造、沙箱状态处理、认证差异化处理四个阶段，按依赖关系递进实施。

## 任务

- [x] 1. 后端 CliProviderConfig 扩展 authOptions 和 authEnvVar 属性
  - [x] 1.1 在 `AcpProperties.CliProviderConfig` 中新增 `authOptions`（`List<String>`）和 `authEnvVar`（`String`）属性及 getter/setter
    - 修改文件：`himarket-server/src/main/java/com/alibaba/himarket/config/AcpProperties.java`
    - 在 `CliProviderConfig` 内部类中新增 `private List<String> authOptions` 和 `private String authEnvVar` 字段
    - _需求：8.6, 9.9, 9.11_

  - [x] 1.2 扩展 `CliProviderInfo` record 以包含 `authOptions` 和 `authEnvVar`，并更新 `listProviders()` 方法的映射逻辑
    - 修改文件：`himarket-server/src/main/java/com/alibaba/himarket/controller/CliProviderController.java`
    - 在 `CliProviderInfo` record 中新增 `List<String> authOptions` 和 `String authEnvVar` 字段
    - 在 `listProviders()` 方法中将 `config.getAuthOptions()` 和 `config.getAuthEnvVar()` 传入 `CliProviderInfo` 构造函数
    - _需求：8.7_

  - [ ]* 1.3 编写属性测试验证 /cli-providers 接口返回 authOptions 和 authEnvVar
    - **Property 7: /cli-providers 接口返回 authOptions 和 authEnvVar**
    - **验证：需求 8.7**
    - 测试文件：`himarket-server/src/test/java/com/alibaba/himarket/controller/CliProviderControllerPropertyTest.java`
    - 使用 jqwik 生成随机 CliProviderConfig，验证响应中 authOptions/authEnvVar 与配置一致

  - [x] 1.4 在 `application.yml` 中为各 CLI 工具配置 `auth-options` 和 `auth-env-var` 属性
    - 修改文件：`himarket-bootstrap/src/main/resources/application.yml`
    - qodercli：`auth-options: default,personal_access_token`，`auth-env-var: QODER_PERSONAL_ACCESS_TOKEN`
    - claude-code：`auth-env-var: ANTHROPIC_API_KEY`
    - kiro-cli：不配置 authOptions 和 authEnvVar（标识不支持沙箱认证）
    - _需求：9.11, 11.4_

- [x] 2. 后端 CliSessionConfig 扩展 authToken 及环境变量注入
  - [x] 2.1 在 `CliSessionConfig` 中新增 `authToken` 字段
    - 修改文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/CliSessionConfig.java`
    - 新增 `private String authToken` 字段（Lombok @Data 自动生成 getter/setter）
    - _需求：9.4_

  - [x] 2.2 在 `AcpWebSocketHandler` 中实现 authToken 到环境变量的注入逻辑
    - 修改文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/AcpWebSocketHandler.java`
    - 在构建 RuntimeConfig 或准备配置文件阶段，当 `sessionConfig.getAuthToken() != null && providerConfig.getAuthEnvVar() != null` 时，将 authToken 注入到 CLI 进程环境变量中
    - 当 authEnvVar 未配置但收到 authToken 时，记录 warn 日志并忽略
    - _需求：9.5_

  - [ ]* 2.3 编写属性测试验证 authToken 环境变量注入
    - **Property 6: authToken 环境变量注入**
    - **验证：需求 9.4, 9.5**
    - 测试文件：`himarket-server/src/test/java/com/alibaba/himarket/service/acp/AuthTokenInjectionPropertyTest.java`
    - 使用 jqwik 生成随机 authToken + authEnvVar，验证环境变量正确注入

- [x] 3. 检查点 - 后端改造验证
  - 确保所有后端测试通过，ask the user if questions arise。

- [x] 4. 前端类型和工具函数扩展
  - [x] 4.1 扩展前端 `ICliProvider` 接口，新增 `authOptions` 和 `authEnvVar` 可选属性
    - 查找 `ICliProvider` 接口定义文件并新增 `authOptions?: string[]` 和 `authEnvVar?: string`
    - _需求：8.8, 9.10, 9.12_

  - [x] 4.2 扩展前端 `CliSessionConfig` 类型，新增 `authToken` 可选属性
    - 查找前端 `CliSessionConfig` 类型定义并新增 `authToken?: string`
    - _需求：9.4_

  - [x] 4.3 扩展 `ProviderCapabilities` 接口和 `computeSteps` 函数，新增认证方案步骤
    - 修改文件：`himarket-web/himarket-frontend/src/components/common/stepUtils.ts`
    - `ProviderCapabilities` 新增 `authOptions?: string[]`
    - `computeSteps` 在"选择工具"和"模型配置"之间插入"认证方案"步骤，当 `authOptions` 非空且长度 > 0 时可见
    - _需求：8.1_

  - [ ]* 4.4 编写属性测试验证 computeSteps 纯函数正确性
    - **Property 5: computeSteps 纯函数正确性**
    - **验证：需求 8.1, 8.2, 8.3, 8.4, 8.5**
    - 测试文件：`himarket-web/himarket-frontend/src/components/common/__tests__/stepUtils.property.test.ts`
    - 使用 fast-check 生成随机 ProviderCapabilities 组合，验证步骤可见性规则

- [x] 5. 前端 CliSelector 组件改造
  - [x] 5.1 CliSelector 新增认证方案选择步骤 UI
    - 修改文件：`himarket-web/himarket-frontend/src/components/common/CliSelector.tsx`
    - 当选中的 Provider 有 `authOptions` 时，渲染认证方案选择步骤（默认/Personal Access Token）
    - QoderCli 选择「默认」时不展示额外表单；选择「Personal Access Token」时展示 PAT 输入框
    - Claude Code + K8s 运行时时展示 API Key 输入表单（ANTHROPIC_API_KEY）
    - _需求：9.1, 9.2, 9.3, 9.8_

  - [x] 5.2 CliSelector 实现 Kiro CLI + K8s 运行时的禁用逻辑
    - 当选中 Kiro CLI 且运行时为 K8s 时，禁用连接按钮并显示"Kiro CLI 沙箱认证待支持（仅支持 OAuth 登录）"提示
    - 当 Kiro CLI 使用本地运行时时，正常允许连接
    - _需求：9.7, 11.1, 11.2, 11.3_

  - [x] 5.3 CliSelector 的 `onSelect` 回调传递 `authToken`（通过 `cliSessionConfig`）
    - 将用户输入的 PAT / API Key 作为 `authToken` 字段序列化到 `cliSessionConfig` JSON 中
    - _需求：9.4_

  - [x] 5.4 CliSelector 的 `handleConnect` 中传递 `sandboxMode`
    - 当 runtime 为 k8s 时，在 onSelect 回调中传递 sandboxMode 值（当前固定为 "user"）
    - _需求：6.1_

- [x] 6. HiWork（Quest.tsx）页面改造
  - [x] 6.1 WelcomePage 传递 `showRuntimeSelector={true}`，启用运行时选择器
    - 修改文件：`himarket-web/himarket-frontend/src/pages/Quest.tsx`
    - 找到 `WelcomePage` 组件调用处，添加 `showRuntimeSelector={true}` prop
    - _需求：1.1_

  - [x] 6.2 改造 `handleSelectCli` 使用回调参数中的 `runtime` 值替代硬编码 "local"
    - 修改 `handleSelectCli` 回调函数签名，接收 `runtime` 参数
    - 使用 `runtime || "local"` 构建 WebSocket URL
    - 当 `runtime === "k8s"` 时，在 `buildAcpWsUrl` 中附加 `sandboxMode: "user"`
    - _需求：1.2, 1.3, 3.1_

  - [x] 6.3 K8s 运行时连接时 dispatch `SANDBOX_STATUS: creating` 状态
    - 当 `runtime === "k8s"` 时，立即 dispatch `{ type: "SANDBOX_STATUS", status: "creating", message: "正在连接沙箱环境..." }`
    - _需求：4.1_

  - [x] 6.4 处理 `sandbox/status` WebSocket 通知，更新沙箱状态
    - 监听 WebSocket 推送的 `sandbox/status` 消息
    - 状态为 `ready` 时更新 sandboxStatus 并触发自动创建 Quest
    - 状态为 `error` 时显示错误信息
    - _需求：4.2, 4.3_

  - [x] 6.5 修改自动创建 Quest 逻辑：K8s 运行时等待沙箱就绪后再创建
    - 本地运行时：保持现有行为，连接成功后立即创建 Quest
    - K8s 运行时：等待 `sandboxStatus.status === "ready"` 后再自动创建 Quest
    - `sandboxStatus.status === "creating"` 时不创建 Quest
    - _需求：5.1, 5.2, 5.5_

  - [ ]* 6.6 编写属性测试验证 WebSocket URL 参数完整性和 sandboxMode 条件附加
    - **Property 1: WebSocket URL 参数完整性**
    - **Property 2: sandboxMode 条件附加**
    - **验证：需求 1.2, 1.3, 3.1, 3.3**
    - 测试文件：`himarket-web/himarket-frontend/src/hooks/__tests__/wsUrl.property.test.ts`
    - 使用 fast-check 生成随机 WsUrlParams，验证 URL 包含所有非空参数且 sandboxMode 条件正确

- [x] 7. HiCoding（Coding.tsx）页面改造
  - [x] 7.1 WelcomePage 传递 `showRuntimeSelector={true}`，启用运行时选择器
    - 修改文件：`himarket-web/himarket-frontend/src/pages/Coding.tsx`
    - _需求：2.1_

  - [x] 7.2 改造连接回调使用 `runtime` 参数替代硬编码 "local"，K8s 时附加 `sandboxMode: "user"`
    - 与 Quest.tsx 完全一致的改造模式
    - _需求：2.2, 2.3, 3.2_

  - [x] 7.3 K8s 运行时连接时 dispatch 沙箱创建状态，处理 sandbox/status 通知
    - _需求：4.4, 4.5, 4.6_

  - [x] 7.4 修改自动创建 Quest 逻辑：K8s 运行时等待沙箱就绪后再创建
    - _需求：5.3, 5.4, 5.5_

- [x] 8. 检查点 - 前端页面改造验证
  - 确保所有前端测试通过，ask the user if questions arise。

- [x] 9. HiCli 页面 sandboxMode 参数传递补充
  - [x] 9.1 CliSelector 的 `onSelect` 回调中传递 sandboxMode 给 HiCli 页面
    - 确保 HiCli 页面的 `connectToCli` 方法在 K8s 运行时下将 sandboxMode 传递给 `buildHiCliWsUrl`
    - _需求：6.1, 6.2_

  - [x] 9.2 `buildHiCliWsUrl` 函数在 K8s 运行时附加 sandboxMode 查询参数，本地运行时不附加
    - 修改文件：查找 `buildHiCliWsUrl` 函数所在文件
    - _需求：6.2, 6.3_

- [x] 10. 后端 CliConfigGenerator 扩展（新增 CLI 工具配置生成器）
  - [x] 10.1 新增 `ClaudeCodeConfigGenerator`，MCP 配置写入 `.claude/settings.json`
    - 新建文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/ClaudeCodeConfigGenerator.java`
    - 实现 `CliConfigGenerator` 接口，`generateMcpConfig` 将 MCP Server 配置写入 `.claude/settings.json`
    - _需求：10.3_

  - [x] 10.2 新增 `QoderCliConfigGenerator`，MCP 配置写入 `.qoder/` 目录
    - 新建文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/QoderCliConfigGenerator.java`
    - 实现 `CliConfigGenerator` 接口
    - _需求：10.4_

  - [x] 10.3 在 `AcpWebSocketHandler` 的 `configGeneratorRegistry` 中注册新的 ConfigGenerator
    - 修改文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/AcpWebSocketHandler.java`
    - 注册 `claude-code` → `ClaudeCodeConfigGenerator`，`qodercli` → `QoderCliConfigGenerator`
    - _需求：10.7_

  - [ ]* 10.4 编写属性测试验证未注册 ConfigGenerator 的容错
    - **Property 10: 未注册 ConfigGenerator 的容错**
    - **验证：需求 10.6**
    - 测试文件：`himarket-server/src/test/java/com/alibaba/himarket/service/acp/AcpWebSocketHandlerPropertyTest.java`
    - 使用 jqwik 生成随机未注册 provider key，验证静默跳过不抛异常

  - [ ]* 10.5 编写属性测试验证 Qwen Code MCP 和 Skill 配置写入路径
    - **Property 8: MCP 配置写入路径正确性（Qwen Code）**
    - **Property 9: Skill 配置路径正确性（Qwen Code）**
    - **验证：需求 10.1, 10.2**
    - 测试文件：`himarket-server/src/test/java/com/alibaba/himarket/service/acp/QwenCodeConfigGeneratorPropertyTest.java`
    - 使用 jqwik 生成随机 MCP Server 和 Skill 列表，验证写入路径和内容正确

- [x] 11. useRuntimeSelection Hook 验证与单一运行时自动选中
  - [ ]* 11.1 编写属性测试验证单一运行时自动选中
    - **Property 3: 单一运行时自动选中**
    - **验证：需求 1.4**
    - 测试文件：`himarket-web/himarket-frontend/src/hooks/__tests__/useRuntimeSelection.property.test.ts`
    - 使用 fast-check 生成单元素运行时列表，验证自动选中行为

- [x] 12. 最终检查点 - 全量测试验证
  - 确保所有前端和后端测试通过，ask the user if questions arise。

## 说明

- 标记 `*` 的任务为可选测试任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- 检查点任务确保增量验证
- 属性测试验证正确性属性的普遍性，单元测试验证具体示例和边界情况
