# 需求文档

## 简介

当 ACP（Agent Client Protocol）返回 JSON-RPC 错误响应时（如网络中断、内部错误等），当前系统在 HiCoding、HiWork、HiCli 三个模块中均未将错误信息展示在对话流中。错误仅被 Promise `.catch()` 捕获并设置 `stopReason` 为 `"error"`，但错误详情（错误码、错误消息）完全丢失，用户无法感知具体发生了什么问题。本需求旨在实现 ACP 错误响应的完整兼容处理，使错误信息能够在对话流中以可视化方式呈现给用户。

## 术语表

- **ACP**：Agent Client Protocol，基于 JSON-RPC 2.0 的代理通信协议
- **JSON-RPC 错误响应**：包含 `error` 字段（含 `code` 和 `message`）的 JSON-RPC 2.0 响应消息
- **ChatItem**：对话流中的消息单元类型，当前支持 `user`、`agent`、`thought`、`tool_call`、`plan` 五种类型
- **ChatStream**：对话流 UI 组件，负责渲染 `ChatItem` 列表
- **QuestSessionContext**：Quest 会话状态管理上下文，包含 reducer 和 dispatch 机制
- **HiCliSessionContext**：HiCli 会话状态管理上下文，继承自 QuestSessionContext 并扩展调试功能
- **useAcpSession**：HiCoding/HiWork 模块的 ACP 会话 Hook
- **useHiCliSession**：HiCli 模块的 ACP 会话 Hook
- **trackRequest**：ACP 工具函数，用于跟踪 JSON-RPC 请求并返回 Promise，错误响应时 reject 并携带 `{ code, message }` 对象

## 需求

### 需求 1：错误消息类型定义

**用户故事：** 作为开发者，我希望系统具备错误消息的类型定义，以便在对话流中统一表示和渲染 ACP 错误响应。

#### 验收标准

1. THE ChatItem 类型联合体 SHALL 包含一个 `error` 类型变体，该变体包含唯一标识符、错误码和错误消息字段
2. WHEN ACP 错误响应包含 `error.data` 扩展数据时，THE ChatItemError 类型 SHALL 支持存储该扩展数据

### 需求 2：错误消息状态管理

**用户故事：** 作为开发者，我希望 QuestSessionContext 的 reducer 能够处理错误消息的添加，以便错误信息能够被纳入对话流状态。

#### 验收标准

1. WHEN 一个 `PROMPT_ERROR` action 被 dispatch 时，THE questReducer SHALL 将一条错误类型的 ChatItem 追加到对应 quest 的 messages 列表中
2. WHEN 一个 `PROMPT_ERROR` action 被 dispatch 时，THE questReducer SHALL 同时将 `isProcessing` 设为 false 并将 `inflightPromptId` 设为 null
3. WHEN 一个 `PROMPT_ERROR` action 的 requestId 与当前 `inflightPromptId` 不匹配时，THE questReducer SHALL 忽略该 action 并保持状态不变

### 需求 3：Hook 层错误捕获与分发

**用户故事：** 作为用户，我希望当 ACP 请求失败时，系统能够自动捕获错误并将其展示在对话流中，以便我了解具体的错误原因。

#### 验收标准

1. WHEN useAcpSession 中 `trackRequest` 的 Promise 被 reject 时，THE useAcpSession Hook SHALL dispatch 一个包含错误码和错误消息的 `PROMPT_ERROR` action
2. WHEN useHiCliSession 中 `trackRequest` 的 Promise 被 reject 时，THE useHiCliSession Hook SHALL dispatch 一个包含错误码和错误消息的 `PROMPT_ERROR` action
3. WHEN ACP 错误响应包含 `error.data` 扩展数据时，THE Hook SHALL 将扩展数据一并传递给 `PROMPT_ERROR` action

### 需求 4：错误消息 UI 渲染

**用户故事：** 作为用户，我希望在对话流中看到清晰的错误提示信息，以便我能够理解问题并采取相应措施。

#### 验收标准

1. WHEN ChatStream 渲染一个 `error` 类型的 ChatItem 时，THE ChatStream 组件 SHALL 以视觉上可区分的样式（如红色边框或背景）展示错误消息
2. WHEN 错误消息被渲染时，THE 错误消息组件 SHALL 展示错误码和错误描述文本
3. WHEN 错误消息包含扩展数据时，THE 错误消息组件 SHALL 展示扩展数据中的关键信息

### 需求 5：错误信息完整性保障

**用户故事：** 作为开发者，我希望从 ACP 错误响应到 UI 展示的整个链路中，错误信息不会丢失，以便用户能够获得完整的错误上下文。

#### 验收标准

1. FOR ALL 有效的 ACP 错误响应对象，THE resolveResponse 函数 SHALL 将完整的 `{ code, message }` 对象传递给 pending request 的 reject 回调
2. FOR ALL 经过 Hook 层处理的 ACP 错误，THE 最终渲染的错误消息 SHALL 包含与原始 ACP 错误响应中相同的错误码和错误消息文本
