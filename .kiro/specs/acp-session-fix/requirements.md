# 需求文档

## 简介

在引入 K8s 沙箱运行时后，hiwork（Quest 模块）和 hicoding（Coding 模块）的 ACP 会话管理出现了回归问题。当前 hiwork 和 hicoding 在页面加载时立即建立 WebSocket 连接，且在顶部栏暴露了 CLI 切换下拉框，导致会话创建后不显示、CLI 切换后模型列表加载失败等问题。本修复参考 HiCli 模块的延迟连接模式，为 hiwork 和 hicoding 引入欢迎页和 CLI 选择流程，移除顶部栏的 CLI 切换入口，将 CLI 选择统一到欢迎页和侧边栏左下角。hiwork 和 hicoding 仅支持 local 运行时模式，不涉及 K8s 沙箱。

## 术语表

- **Quest**：一个 ACP 会话实例，包含聊天消息、模型配置、终端等状态
- **ACP**：Agent Communication Protocol，前后端与 CLI Agent 之间的 JSON-RPC 通信协议
- **Session_Manager**：前端会话管理 hook（useAcpSession / useHiCliSession），负责 ACP 协议初始化、会话创建和消息收发
- **State_Store**：前端 Context 中的 reducer 状态管理，维护所有 Quest 的状态
- **WebSocket_Manager**：前端 `useAcpWebSocket` hook，负责 WebSocket 连接的建立、断开和重连
- **CLI_Provider**：后端 CLI 代理进程的配置标识，不同 provider 对应不同的 Agent 实现（如 Qoder CLI、Qwen Code 等）
- **CLI_Selector**：CLI 选择器组件，展示可用的 CLI 工具列表供用户选择
- **Welcome_Page**：欢迎页组件，在未连接状态下展示 CLI_Selector，在已连接状态下展示创建 Quest 引导
- **Backend_Handler**：后端 `AcpWebSocketHandler`，负责 WebSocket 连接管理和消息转发
- **Pending_Message_Map**：后端用于 K8s 异步 Pod 初始化期间缓存前端消息的队列映射
- **HiCli_Pattern**：HiCli 模块采用的延迟连接模式——先在欢迎页选择 CLI，选择后才建立 WebSocket 连接，连接成功后自动创建 Quest；切换工具时断开连接并返回欢迎页

## 需求

### 需求 1：hiwork 引入欢迎页和 CLI 选择流程

**用户故事：** 作为 hiwork 用户，我希望进入页面后先看到欢迎页并选择 CLI 工具，选择后系统自动建立连接和创建会话，以便我能明确知道使用的是哪个 Agent。

#### 验收标准

1. WHEN 用户首次进入 hiwork 页面, THE Quest 页面 SHALL 展示 Welcome_Page 并显示 CLI_Selector 供用户选择 CLI 工具，而非立即建立 WebSocket 连接
2. WHEN 用户在 CLI_Selector 中选择一个 CLI_Provider 并点击连接, THE Session_Manager SHALL 构建对应的 WebSocket URL 并建立连接
3. WHEN WebSocket 连接建立且协议初始化完成后, THE Session_Manager SHALL 自动创建第一个 Quest 会话
4. WHEN Quest 创建成功, THE 侧边栏 SHALL 在列表中显示新创建的 Quest 并将其设为活跃状态
5. WHEN 用户在侧边栏左下角点击切换工具按钮, THE Quest 页面 SHALL 断开当前连接、重置所有状态并返回 Welcome_Page 的 CLI 选择界面

### 需求 2：hicoding 引入欢迎页和 CLI 选择流程

**用户故事：** 作为 hicoding 用户，我希望进入页面后先看到欢迎页并选择 CLI 工具，选择后系统自动建立连接和创建会话，以便切换 CLI 时不需要刷新页面。

#### 验收标准

1. WHEN 用户首次进入 hicoding 页面, THE Coding 页面 SHALL 展示 Welcome_Page 并显示 CLI_Selector 供用户选择 CLI 工具，而非立即建立 WebSocket 连接
2. WHEN 用户在 CLI_Selector 中选择一个 CLI_Provider 并点击连接, THE Session_Manager SHALL 构建对应的 WebSocket URL 并建立连接
3. WHEN WebSocket 连接建立且协议初始化完成后, THE Session_Manager SHALL 自动创建第一个 Quest 会话并加载模型列表
4. WHEN 用户点击切换工具按钮, THE Coding 页面 SHALL 断开当前连接、重置所有状态（包括 autoCreatedRef）并返回 Welcome_Page 的 CLI 选择界面
5. WHEN CLI 切换完成后新连接建立, THE Session_Manager SHALL 能正确加载新 CLI_Provider 的模型列表，无需用户手动刷新页面

### 需求 3：移除顶部栏的 CLI 切换入口

**用户故事：** 作为用户，我希望顶部栏不再显示 CLI 切换下拉框，CLI 选择只在欢迎页和侧边栏左下角进行，以保持界面简洁和操作流程一致。

#### 验收标准

1. THE QuestTopBar 组件 SHALL 移除 CliProviderSelect 下拉框，不再在顶部栏展示 CLI 切换入口
2. THE CodingTopBar 组件 SHALL 移除 CliProviderSelect 下拉框，不再在顶部栏展示 CLI 切换入口
3. WHEN 用户需要切换 CLI_Provider, THE 页面 SHALL 仅通过侧边栏左下角的切换按钮或返回欢迎页来完成切换操作
4. THE QuestTopBar 和 CodingTopBar SHALL 保留模型选择下拉框、连接状态指示器和 Token 用量显示

### 需求 4：WebSocket 延迟连接模式

**用户故事：** 作为开发者，我希望 hiwork 和 hicoding 采用与 HiCli 一致的延迟连接模式，在用户选择 CLI 之前不建立 WebSocket 连接，以避免无效连接和竞态条件。

#### 验收标准

1. WHEN WebSocket URL 为空字符串, THE WebSocket_Manager SHALL 跳过自动连接（autoConnect 为 false）
2. WHEN 用户选择 CLI 后 WebSocket URL 被设置为有效值, THE WebSocket_Manager SHALL 自动建立连接
3. WHEN 连接断开时, THE Session_Manager SHALL 重置 initializedRef 为 false 和 autoCreatedRef 为 false，确保重新连接后能正确初始化和自动创建 Quest
4. WHEN RESET_STATE 被 dispatch, THE State_Store SHALL 将所有状态恢复到初始值，包括 connected、initialized、quests 和 activeQuestId

### 需求 5：会话创建和状态同步的可靠性

**用户故事：** 作为用户，我希望会话创建过程中的各种异常情况都能被妥善处理，以便我能获得明确的错误反馈而不是无响应。

#### 验收标准

1. WHEN createQuest 正在执行中, THE Session_Manager SHALL 拒绝重复的创建请求并返回错误
2. WHEN createQuest 执行完成（无论成功或失败）, THE Session_Manager SHALL 将 creatingQuestRef 和 creatingQuest 状态重置为 false
3. WHEN `session/new` 响应中包含 models 和 modes 列表, THE State_Store SHALL 将模型列表和模式列表同步更新到新创建的 Quest 数据中
4. WHEN WebSocket 连接在请求等待期间断开, THE Session_Manager SHALL 通过 clearPendingRequests 拒绝所有等待中的请求

### 需求 6：后端 local 运行时路径的可靠性

**用户故事：** 作为开发者，我希望后端在处理 local 运行时类型的连接时，不受 K8s 相关逻辑的影响，以确保消息转发的可靠性。

#### 验收标准

1. WHEN 前端传递 runtime=local 参数, THE Backend_Handler SHALL 直接走本地运行时同步启动路径，跳过 K8s Pod 异步初始化逻辑
2. WHEN runtime 参数为 local, THE Backend_Handler SHALL 确保 Pending_Message_Map 中不存在该 session 的条目，消息直接转发到本地运行时进程
3. WHEN resolveRuntimeType 接收到 null 或空白字符串, THE Backend_Handler SHALL 返回 RuntimeType.LOCAL 作为默认值
