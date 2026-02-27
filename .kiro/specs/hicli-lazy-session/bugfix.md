# Bugfix 需求文档

## 简介

HiCli 页面在用户进入后，一旦 WebSocket 连接成功且 ACP 协议初始化完成，就会自动调用 `session/new` 创建会话（Quest），即使用户尚未发送任何消息。这导致产生大量空会话、浪费服务端资源，且用户无法看到干净的初始空白页面。此外，当已存在空白会话时，点击"+"按钮会重复创建新的空会话，而非复用已有的空白会话。此问题在所有 CLI Provider（不同 `key`、不同 `runtimeCategory`）和所有运行时类型（`local`、`k8s`）下均存在。

## CLI Provider 与运行时覆盖范围

本修复需覆盖以下所有 CLI 场景：

- **CLI Provider 类型**：所有通过 `/cli-providers` 接口返回的 provider（不同 `key`、`runtimeCategory` 为 `native`/`nodejs`/`python`），无论是否支持自定义模型（`supportsCustomModel`）、MCP（`supportsMcp`）或 Skill（`supportsSkill`）
- **运行时类型**：
  - `local`：本地运行，WebSocket 连接后直接进入 initialize 流程，无沙箱创建阶段
  - `k8s`：K8s 沙箱运行，WebSocket 连接后需等待 `sandbox/status` 通知变为 `ready` 才算就绪，存在沙箱创建等待阶段
- **认证方式**：部分 CLI Provider 有 `authOptions`（如 `personal_access_token`），认证凭据通过 `CliSessionConfig.authToken` 传递，不影响本修复的会话创建时机逻辑
- **会话配置**：部分连接携带 `cliSessionConfig`（包含自定义模型、MCP Server、Skill 等配置），这些配置在 `connectToCli` 时已编码到 WebSocket URL 中，不影响延迟创建 session 的逻辑

## Bug 分析

### 当前行为（缺陷）

1.1 WHEN 用户选择任意 CLI Provider（local 运行时）进入 HiCli 页面且 WebSocket 连接成功、ACP 初始化完成 THEN 系统自动调用 `session/new` 创建一个空会话，即使用户没有发送任何消息

1.2 WHEN 用户选择任意 CLI Provider（k8s 运行时）进入 HiCli 页面且 WebSocket 连接成功、ACP 初始化完成、沙箱状态变为 ready THEN 系统自动调用 `session/new` 创建一个空会话，即使用户没有发送任何消息

1.3 WHEN 用户进入 HiCli 页面且没有任何会话存在 THEN 左侧 sidebar 不允许空会话列表状态，始终会被自动创建逻辑填充一个空会话

1.4 WHEN ACP 初始化（initialize）返回了 mode/model 信息但尚未创建会话 THEN 系统无法在顶部工具栏展示这些信息，因为 model/mode 选择器依赖 `quest?.availableModels` 和 `quest?.availableModes`，没有活跃 quest 时回退到 `hiCliState.models`（初始化时为空数组）

1.5 WHEN 当前活跃会话没有任何消息（空白会话）且用户点击"+"按钮 THEN 系统创建一个新的空会话，而不是定位到已有的空白会话

### 期望行为（正确）

2.1 WHEN 用户选择任意 CLI Provider（local 运行时）进入 HiCli 页面且 WebSocket 连接成功、ACP 初始化完成 THEN 系统不应自动创建会话，应展示空白初始页面并等待用户发送第一条消息时才调用 `session/new`

2.2 WHEN 用户选择任意 CLI Provider（k8s 运行时）进入 HiCli 页面且 WebSocket 连接成功、ACP 初始化完成、沙箱状态变为 ready THEN 系统不应自动创建会话，应展示空白初始页面并等待用户发送第一条消息时才调用 `session/new`

2.3 WHEN 用户进入 HiCli 页面且没有任何会话存在 THEN 左侧 sidebar 应展示空状态提示（如"发送消息开始新对话"），允许无会话的空白初始页面

2.4 WHEN ACP 初始化（initialize）返回了 mode/model 信息但尚未创建会话 THEN 系统应将 initialize 返回的 modes 信息展示在顶部工具栏中，不依赖活跃 quest 的存在

2.5 WHEN 当前已存在一个没有任何消息的空白会话且用户点击"+"按钮 THEN 系统应切换到该已有的空白会话，而不是创建新会话

2.6 WHEN 用户在没有会话的状态下发送第一条消息 THEN 系统应先自动创建会话（`session/new`），然后立即发送该消息；此行为对所有 CLI Provider 和运行时类型一致

2.7 WHEN 用户选择携带 `cliSessionConfig`（自定义模型/MCP/Skill/认证凭据）的 CLI Provider 且在没有会话的状态下发送第一条消息 THEN 延迟创建会话的行为应与无配置时一致，`cliSessionConfig` 已在 WebSocket URL 中传递，不影响 `session/new` 的调用时机

### 不变行为（回归防护）

3.1 WHEN 用户已有活跃会话且发送消息 THEN 系统应继续正常发送 prompt 到当前活跃会话（所有 CLI Provider 和运行时类型）

3.2 WHEN 用户在多个会话之间切换 THEN 系统应继续正常切换活跃会话并展示对应的聊天记录

3.3 WHEN 所有会话都有消息且用户点击"+"按钮 THEN 系统应继续正常创建新会话

3.4 WHEN WebSocket 断开连接 THEN 系统应继续正常重置连接状态和初始化标记

3.5 WHEN 用户选择 k8s 运行时且沙箱正在创建中 THEN 系统应继续展示沙箱创建状态提示，不允许用户发送消息

3.6 WHEN 用户选择切换 CLI 工具 THEN 系统应继续正常断开连接并重置所有状态

3.7 WHEN 用户选择不同 CLI Provider（不同 `runtimeCategory`：native/nodejs/python）THEN 延迟创建会话的行为应完全一致，不因 CLI 类型而异

3.8 WHEN CLI Provider 的 `session/new` 返回了额外的 models/modes 信息 THEN 系统应继续正常合并这些信息到状态中（与 initialize 返回的信息合并）
