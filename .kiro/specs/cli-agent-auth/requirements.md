# 需求文档

## 简介

为 HiMarket 平台的 HiCoding 模块实现 CLI Agent 认证功能。当 CLI Agent 在 ACP 协议交互中返回认证错误时，前端能够感知认证状态、展示认证界面，后端能够启动独立的认证子进程，通过 WebSocket 透传认证交互，最终完成凭证写入并恢复正常的 ACP 会话。该功能需要以通用方式支持不同 CLI Agent 的认证需求，当前主要对接 qwen-code，但架构上不绑定特定 CLI 的认证方式。

## 术语表

- **ACP_Session**：基于 ACP（Agent Client Protocol）协议的 WebSocket 会话，用于前端与 CLI Agent 子进程之间的 JSON-RPC 通信
- **CLI_Agent**：ACP 兼容的命令行工具（如 qwen-code、claude-code、qodercli 等），由后端作为子进程启动
- **Auth_Method**：CLI Agent 在 `initialize` 响应或 `session/new` 错误中声明的认证方式，包含 id、name、type、args 等字段；不同 CLI Agent 可能声明不同的认证方式
- **Auth_Process**：为完成认证而启动的独立 CLI 子进程，使用 Auth_Method 中指定的 args 参数运行
- **Auth_WebSocket**：专门用于认证流程的 WebSocket 连接（或复用现有连接的认证通道），透传 Auth_Process 的 stdin/stdout
- **Workspace**：每个用户的隔离工作目录（`~/.himarket/workspaces/{userId}/`），CLI Agent 的凭证文件存储于此
- **Terminal_Auth**：type 为 `"terminal"` 的认证方式，需要启动独立 CLI 进程完成交互式认证，认证过程中 CLI 可能输出提示文本、URL 链接或要求用户输入

## 需求

### 需求 1：认证状态感知

**用户故事：** 作为 HiCoding 用户，我希望系统能自动检测 CLI Agent 是否需要认证，以便我知道何时需要登录。

#### 验收标准

1. WHEN `session/new` 返回错误码 `-32000` 且消息为 `"Authentication required"` 时，THE ACP_Session SHALL 解析错误响应中的 `data.authMethods` 字段并将认证状态通知前端
2. WHEN `initialize` 响应中包含 `authMethods` 字段时，THE ACP_Session SHALL 存储可用的认证方式列表供后续使用
3. WHEN 认证错误被检测到时，THE ACP_Session SHALL 阻止自动重试 `session/new` 请求，等待用户完成认证流程
4. IF `data.authMethods` 字段缺失或为空数组，THEN THE ACP_Session SHALL 向用户展示通用错误提示而非认证界面

### 需求 2：认证方式选择界面

**用户故事：** 作为 HiCoding 用户，我希望在需要认证时看到清晰的认证方式选择界面，以便我选择合适的登录方式。

#### 验收标准

1. WHEN 认证状态被触发时，THE 认证界面 SHALL 展示所有可用的 Auth_Method，每个选项显示名称（name）和描述（description）
2. WHEN 用户选择一种 Auth_Method 时，THE 认证界面 SHALL 根据所选方式的 type 字段启动对应的认证流程
3. WHILE 认证流程进行中，THE 认证界面 SHALL 显示认证进度状态，禁用聊天输入区域
4. WHEN 用户取消认证流程时，THE 认证界面 SHALL 关闭认证进程并恢复到认证方式选择状态
5. WHEN Auth_Method 列表中仅有一种认证方式时，THE 认证界面 SHALL 自动选择该方式并直接进入认证流程，跳过选择步骤

### 需求 3：后端认证进程管理

**用户故事：** 作为系统，我需要为用户启动独立的认证子进程，以便在隔离环境中完成 CLI Agent 的凭证获取。

#### 验收标准

1. WHEN 前端请求启动 Terminal_Auth 类型的认证流程时，THE 后端 SHALL 启动一个独立的 Auth_Process，使用当前 CLI Provider 配置的 command 加上 Auth_Method 指定的 args 参数
2. WHEN Auth_Process 启动时，THE 后端 SHALL 将其 HOME 环境变量设为用户的 Workspace 路径，与 ACP 主进程保持一致的凭证隔离
3. WHEN Auth_Process 的 stdout 产生输出时，THE 后端 SHALL 通过 Auth_WebSocket 将输出实时转发给前端
4. WHEN Auth_WebSocket 收到前端消息时，THE 后端 SHALL 将消息写入 Auth_Process 的 stdin
5. WHEN Auth_Process 退出时，THE 后端 SHALL 通过 Auth_WebSocket 通知前端认证进程已结束，并携带退出码
6. IF Auth_Process 在 300 秒内未退出，THEN THE 后端 SHALL 强制终止该进程并通知前端认证超时
7. THE 后端 SHALL 通过 Auth_Method 的 type 字段区分认证类型，仅对 `"terminal"` 类型启动子进程；对于未来可能出现的其他 type，预留扩展点

### 需求 4：Terminal 类型认证交互

**用户故事：** 作为 HiCoding 用户，我希望在 Terminal 类型认证过程中能够与 CLI 进程进行交互，以便完成各种认证方式的操作。

#### 验收标准

1. WHEN Auth_Process 的输出中包含 URL 模式（http:// 或 https:// 开头的链接）时，THE 前端 SHALL 将该 URL 渲染为可点击的超链接，在新标签页中打开
2. WHEN Auth_Process 的输出中包含用户输入提示时，THE 前端 SHALL 显示一个文本输入框，允许用户输入内容并通过 stdin 发送给 Auth_Process
3. WHEN 输入提示涉及敏感信息（如包含 "key"、"secret"、"password"、"token" 等关键词）时，THE 前端 SHALL 使用 password 类型的输入框遮蔽输入内容
4. THE 前端 SHALL 将 Auth_Process 的所有 stdout 输出以终端风格展示，保留原始文本格式
5. WHILE 等待 Auth_Process 响应时，THE 前端 SHALL 显示加载指示器，告知用户认证进程正在运行

### 需求 5：认证完成后重连

**用户故事：** 作为 HiCoding 用户，我希望认证完成后系统自动恢复正常的编码会话，以便我无缝继续工作。

#### 验收标准

1. WHEN Auth_Process 以退出码 0 退出时，THE 前端 SHALL 自动断开当前 ACP_Session 的 WebSocket 连接并重新建立新连接
2. WHEN 新的 ACP_Session 建立后，THE 前端 SHALL 自动执行 `initialize` 和 `session/new` 流程
3. WHEN 重连后 `session/new` 仍然返回认证错误时，THE 前端 SHALL 重新展示认证界面并提示用户认证可能未成功
4. IF Auth_Process 以非零退出码退出，THEN THE 前端 SHALL 显示认证失败提示并允许用户重试

### 需求 6：认证状态持久化与复用

**用户故事：** 作为 HiCoding 用户，我希望认证凭证在页面刷新或重新连接后仍然有效，以便我不需要反复登录。

#### 验收标准

1. THE 认证系统 SHALL 依赖 CLI Agent 自身的凭证存储机制（凭证文件写入用户 Workspace），不额外存储认证信息
2. WHEN 用户刷新页面或重新连接时，THE ACP_Session SHALL 正常执行 `initialize` 和 `session/new`，由 CLI Agent 自行判断凭证是否有效
3. WHEN 已认证用户切换 CLI Provider 时，THE 系统 SHALL 独立处理每个 Provider 的认证状态，互不影响
