# 需求文档

## 简介

HiCli 是 HiMarket 平台的新模块，旨在将 acp-demo 项目的前端 ACP 协议调试能力集成到 himarket-frontend 中。acp-demo 最初作为独立项目开发，提供了通过 ACP（Agent Client Protocol）协议调试各类 CLI Agent 的完整前端能力，包括 CLI 工具选择、WebSocket 连接管理、ACP 协议日志查看、Agent 信息展示等调试专用功能。

HiCli 模块将放置在 HiCoding 右侧的导航标签中，专注于 ACP 协议调试场景。与 HiWork（通用 Agent 对话）和 HiCoding（编码 IDE）不同，HiCli 强调的是对 ACP 协议交互过程的可视化和调试能力，帮助开发者理解和排查 Agent 通信问题。

### 与现有模块的关系

acp-demo、HiWork 和 HiCoding 最早源自同一份 POC 代码，三者在 ACP 协议通信层面共享大量逻辑：

| 能力 | HiWork | HiCoding | HiCli（新增） |
|------|--------|----------|---------------|
| ACP WebSocket 通信 | ✅ 复用 | ✅ 复用 | ✅ 复用 |
| 会话管理（Quest） | ✅ | ✅ | ✅ |
| 聊天流渲染 | ✅ | ✅ | ✅ |
| 权限对话框 | ✅ | ✅ | ✅ |
| CLI 工具选择器 | ❌ | ✅（CliProviderSelect） | ✅（独立 CLI 选择页） |
| ACP 协议日志面板 | ❌ | ❌ | ✅（核心特性） |
| Agent 信息卡片 | ❌ | ❌ | ✅（核心特性） |
| 协议消息检查器 | ❌ | ❌ | ✅（核心特性） |
| 日志聚合与过滤 | ❌ | ❌ | ✅（核心特性） |
| 文件树/编辑器 | ❌ | ✅ | ❌ |
| 终端面板 | ❌ | ✅ | ❌ |
| 预览面板 | ❌ | ✅ | ❌ |

### 迁移策略

1. **可复用逻辑**：ACP WebSocket 通信（useAcpWebSocket）、ACP 工具函数（lib/utils/acp.ts）、消息规范化（acpNormalize.ts）、QuestSessionContext 等已在 himarket 中存在，HiCli 直接复用
2. **需迁移逻辑**：ACP 日志聚合器（logAggregator）、日志过滤器（logFilter）、日志类型定义（log.ts）、ACP 日志面板（AcpLogPanel）、Agent 信息卡片（AgentInfoCard）、协议检查器（ProtocolInspector）等调试专用组件需从 acp-demo 迁移
3. **需适配逻辑**：CLI 选择器需适配 himarket 的 CliProvider API；SessionContext 中的调试相关状态（rawMessages、aggregatedLogs）需扩展到 QuestSessionContext

## 术语表

- **HiCli_Module**：HiMarket 平台中专注于 ACP 协议调试的前端模块
- **ACP_Log_Panel**：ACP 协议日志面板，展示聚合后的协议消息日志
- **Protocol_Inspector**：协议消息检查器，展示原始 JSON-RPC 消息的详细信息
- **Agent_Info_Card**：Agent 信息卡片，展示当前连接的 Agent 的元数据（名称、版本、能力、认证方式等）
- **Log_Aggregator**：日志聚合器，将流式 chunk 消息拼接为完整的聚合日志条目
- **Log_Filter**：日志过滤器，按 method 或摘要文本过滤日志条目
- **CLI_Selector**：CLI 工具选择器，用于选择要调试的 CLI Agent 并配置工作目录
- **Debug_Panel**：调试面板，包含 ACP 日志和 Agent 信息的可切换面板区域
- **Aggregated_Log_Entry**：聚合日志条目，流式消息拼接后或非流式消息直接生成的日志记录
- **Raw_Message**：原始消息，未经聚合处理的 JSON-RPC 协议消息
- **Quest**：一次 Agent 会话，对应 ACP 协议中的一个 session
- **QuestSessionContext**：himarket 中管理 Quest 会话状态的 React Context

## 需求

### 需求 1：HiCli 路由与导航集成

**用户故事：** 作为 HiMarket 用户，我希望在顶部导航栏中看到 HiCli 标签并能点击进入，以便快速访问 ACP 协议调试功能。

#### 验收标准

1. THE HiCli_Module SHALL 在 Header 导航栏中注册一个名为 "HiCli" 的标签，位于 "HiCoding" 标签右侧
2. WHEN 用户点击 "HiCli" 标签, THE HiCli_Module SHALL 导航到 `/hicli` 路由并渲染 HiCli 页面
3. WHEN 用户位于 `/hicli` 路由, THE Header SHALL 高亮 "HiCli" 标签以指示当前活跃页面
4. THE HiCli_Module SHALL 复用 himarket-frontend 现有的 Header 组件和 Layout 样式体系

### 需求 2：CLI 工具选择与连接

**用户故事：** 作为开发者，我希望在 HiCli 中选择要调试的 CLI Agent 工具并建立连接，以便开始 ACP 协议调试会话。

#### 验收标准

1. WHEN 用户进入 HiCli 页面且未选择 CLI 工具, THE HiCli_Module SHALL 展示欢迎页面和 CLI 工具选择界面
2. WHEN HiCli 页面加载时, THE CLI_Selector SHALL 通过 himarket 的 CliProvider API（`/api/cli-providers`）获取可用的 CLI 工具列表
3. WHEN 用户选择一个 CLI 工具并输入工作目录后点击连接, THE HiCli_Module SHALL 构建 WebSocket URL 并建立 ACP 连接
4. WHEN WebSocket 连接建立成功, THE HiCli_Module SHALL 自动执行 ACP `initialize` 握手协议
5. IF WebSocket 连接失败, THEN THE HiCli_Module SHALL 展示错误信息并提供重试选项
6. WHEN 用户点击"切换工具"按钮, THE HiCli_Module SHALL 断开当前连接并返回 CLI 工具选择界面

### 需求 3：ACP 会话管理

**用户故事：** 作为开发者，我希望在 HiCli 中创建和管理多个 ACP 调试会话，以便同时调试不同的交互场景。

#### 验收标准

1. WHEN 用户点击"新建 Quest"按钮, THE HiCli_Module SHALL 通过 ACP 协议发送 `session/new` 请求创建新会话
2. THE HiCli_Module SHALL 在左侧边栏展示所有活跃的 Quest 会话列表，按创建时间倒序排列
3. WHEN 用户点击侧边栏中的某个 Quest, THE HiCli_Module SHALL 切换到该 Quest 的聊天视图和调试面板
4. WHEN 用户关闭一个 Quest, THE HiCli_Module SHALL 从会话列表中移除该 Quest 并自动切换到最近的活跃 Quest
5. THE HiCli_Module SHALL 复用 QuestSessionContext 管理会话状态，与 HiWork 和 HiCoding 共享状态管理逻辑

### 需求 4：聊天交互功能

**用户故事：** 作为开发者，我希望在 HiCli 中与 Agent 进行对话交互，以便触发 ACP 协议消息并观察调试信息。

#### 验收标准

1. WHEN 用户在输入框中输入消息并按 Enter 键, THE HiCli_Module SHALL 通过 ACP 协议发送 `session/prompt` 请求
2. WHEN Agent 返回流式消息（agent_message_chunk）, THE HiCli_Module SHALL 实时渲染消息内容到聊天流中
3. WHEN Agent 返回思考过程（agent_thought_chunk）, THE HiCli_Module SHALL 以可折叠的形式展示思考内容
4. WHEN Agent 发起工具调用（tool_call）, THE HiCli_Module SHALL 展示工具调用卡片，包含工具名称、状态和输入参数
5. WHEN Agent 返回执行计划（plan）, THE HiCli_Module SHALL 展示计划条目及其状态
6. WHEN 用户点击取消按钮, THE HiCli_Module SHALL 发送 `session/cancel` 通知终止当前 Agent 处理
7. THE HiCli_Module SHALL 复用 himarket-frontend 现有的 ChatStream、QuestInput、ToolCallCard 等聊天组件

### 需求 5：ACP 协议日志面板

**用户故事：** 作为开发者，我希望查看 ACP 协议的聚合日志，以便理解 Agent 通信的完整流程和排查协议问题。

#### 验收标准

1. WHEN 用户切换到"ACP 日志"调试标签, THE ACP_Log_Panel SHALL 展示当前会话的所有聚合日志条目
2. THE Log_Aggregator SHALL 将连续的流式 chunk 消息（agent_message_chunk、agent_thought_chunk）拼接为单条聚合日志条目
3. THE Log_Aggregator SHALL 将非流式消息（initialize、session/new、tool_call 等）作为独立日志条目输出
4. WHEN 新的日志条目产生, THE ACP_Log_Panel SHALL 自动滚动到最新条目（当用户未手动滚动时）
5. THE ACP_Log_Panel SHALL 为每条日志展示方向箭头（发送/接收）、method 名称（带颜色编码）、RPC ID、聚合消息数徽章和时间戳
6. WHEN 用户点击某条日志条目, THE ACP_Log_Panel SHALL 展开显示该条目的完整 JSON 数据
7. WHEN 用户在过滤输入框中输入文本, THE Log_Filter SHALL 按 method 名称或摘要文本过滤日志条目（不区分大小写）
8. THE Log_Aggregator SHALL 对聚合日志条目生成摘要文本，流式消息取前 100 个字符，非流式消息使用 method 名称

### 需求 6：Agent 信息展示

**用户故事：** 作为开发者，我希望查看当前连接的 Agent 的详细信息，以便了解 Agent 的能力和配置。

#### 验收标准

1. WHEN 用户切换到"Agent 信息"调试标签, THE Agent_Info_Card SHALL 展示当前 Agent 的元数据信息
2. THE Agent_Info_Card SHALL 展示 Agent 基本信息（名称、标题、版本），数据来源标注为 "initialize"
3. THE Agent_Info_Card SHALL 展示 Agent 支持的认证方式列表，包含 ID、名称、类型和描述
4. THE Agent_Info_Card SHALL 展示可用的 Mode 列表，包含 ID、名称和描述，并标注数据来源（initialize 或 session/new）
5. THE Agent_Info_Card SHALL 展示 Agent 能力配置的完整 JSON 数据
6. THE Agent_Info_Card SHALL 展示可用的 Model 列表，包含 Model ID 和名称，数据来源标注为 "session/new"
7. THE Agent_Info_Card SHALL 展示可用的 Slash Command 列表，包含命令名称和描述
8. WHEN Agent 未提供某项信息, THE Agent_Info_Card SHALL 展示"未提供"的占位提示

### 需求 7：调试面板布局与切换

**用户故事：** 作为开发者，我希望在聊天区域旁边查看调试信息，以便同时观察对话内容和协议细节。

#### 验收标准

1. THE HiCli_Module SHALL 在顶部工具栏提供"ACP 日志"和"Agent 信息"两个调试标签切换按钮
2. WHEN 用户点击"ACP 日志"按钮, THE HiCli_Module SHALL 在聊天区域右侧展示 ACP 日志面板
3. WHEN 用户点击"Agent 信息"按钮, THE HiCli_Module SHALL 在聊天区域右侧展示 Agent 信息面板
4. WHEN 用户再次点击已激活的调试标签按钮, THE HiCli_Module SHALL 关闭调试面板
5. THE Debug_Panel SHALL 包含标题栏和关闭按钮，关闭按钮点击后隐藏面板
6. THE HiCli_Module SHALL 使用 himarket-frontend 的 Tailwind CSS 样式体系渲染调试面板，替代 acp-demo 的原生 CSS 样式

### 需求 8：调试状态扩展

**用户故事：** 作为开发者，我希望 HiCli 能记录完整的协议交互数据，以便在调试面板中查看原始消息和聚合日志。

#### 验收标准

1. THE HiCli_Module SHALL 扩展 QuestSessionContext 的状态，增加 rawMessages（原始消息列表）和 aggregatedLogs（聚合日志列表）字段
2. WHEN ACP 消息通过 WebSocket 发送或接收, THE HiCli_Module SHALL 将原始消息记录到 rawMessages 状态中
3. WHEN ACP 消息被处理, THE Log_Aggregator SHALL 将聚合后的日志条目分发到 aggregatedLogs 状态中
4. THE HiCli_Module SHALL 记录每条原始消息的方向（client_to_agent 或 agent_to_client）、时间戳、method 名称和 RPC ID
5. WHEN 用户切换 CLI 工具或断开连接, THE HiCli_Module SHALL 清空 rawMessages 和 aggregatedLogs 状态

### 需求 9：顶部工具栏

**用户故事：** 作为开发者，我希望在 HiCli 的顶部工具栏中查看连接状态和切换 Agent 配置，以便快速了解当前调试环境。

#### 验收标准

1. THE HiCli_Module SHALL 在顶部工具栏展示当前 Agent 的名称和版本信息（如果 Agent 提供）
2. THE HiCli_Module SHALL 在顶部工具栏展示当前选中的 CLI 工具名称
3. WHEN Agent 提供多个 Model 选项, THE HiCli_Module SHALL 在顶部工具栏展示 Model 选择下拉框
4. WHEN Agent 提供多个 Mode 选项, THE HiCli_Module SHALL 在顶部工具栏展示 Mode 选择下拉框
5. THE HiCli_Module SHALL 在顶部工具栏展示 WebSocket 连接状态（disconnected、connecting、connected）
6. WHEN Agent 返回 usage 信息, THE HiCli_Module SHALL 在顶部工具栏展示 Token 使用量和费用信息

### 需求 10：权限请求处理

**用户故事：** 作为开发者，我希望在 Agent 请求权限时看到权限对话框，以便控制 Agent 的操作权限。

#### 验收标准

1. WHEN Agent 发送 `session/request_permission` 请求, THE HiCli_Module SHALL 展示权限对话框，包含工具调用信息和可选的权限选项
2. WHEN 用户选择一个权限选项, THE HiCli_Module SHALL 将选择结果通过 ACP 协议响应给 Agent
3. THE HiCli_Module SHALL 复用 himarket-frontend 现有的 PermissionDialog 组件

### 需求 11：样式适配

**用户故事：** 作为 HiMarket 用户，我希望 HiCli 的界面风格与 HiMarket 平台保持一致，以获得统一的视觉体验。

#### 验收标准

1. THE HiCli_Module SHALL 使用 himarket-frontend 的 Tailwind CSS 工具类替代 acp-demo 的原生 CSS 类名
2. THE HiCli_Module SHALL 复用 himarket-frontend 的颜色体系（gray-50/30 背景、blue-500/600 主色调、gray-200/60 边框等）
3. THE HiCli_Module SHALL 使用 himarket-frontend 的 Ant Design 组件（Select、Button 等）替代 acp-demo 的原生 HTML 控件
4. THE HiCli_Module SHALL 使用 lucide-react 图标库，与 HiWork 和 HiCoding 保持一致
5. THE HiCli_Module SHALL 采用与 HiCoding 类似的 flex 布局结构（左侧聊天区 + 右侧调试面板）
