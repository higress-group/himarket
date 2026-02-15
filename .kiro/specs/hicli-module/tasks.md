# 实现计划: HiCli 模块

## 概述

将 acp-demo 的 ACP 协议调试前端能力迁移到 himarket-frontend，作为新的 HiCli 模块。采用增量实现策略：先搭建基础框架和复用层，再迁移调试专用组件，最后完成样式适配和集成。

## 任务

- [x] 1. 新增日志类型定义和工具函数
  - [x] 1.1 创建 `src/types/log.ts`，定义 AggregatedLogEntry、ChunkBuffer、RawMessage、RawMessageDirection 类型
    - 从 acp-demo 的 `types/log.ts` 和 `types/acp.ts` 中迁移相关类型
    - RawMessageDirection 使用 himarket 的 JsonRpcId 类型对齐
    - _需求: 8.1, 8.4_
  - [x] 1.2 在 `src/types/acp.ts` 中新增 AgentInfo、AuthMethod、AgentCapabilities 接口
    - 从 acp-demo 的 `types/acp.ts` 中迁移这三个接口
    - _需求: 6.2, 6.3, 6.5_
  - [x] 1.3 创建 `src/lib/utils/logAggregator.ts`，迁移 LogAggregator 类
    - 从 acp-demo 的 `utils/logAggregator.ts` 完整迁移
    - 调整 import 路径指向 himarket 的类型文件
    - 调整对 acp.ts 工具函数的引用指向 `lib/utils/acp.ts`
    - _需求: 5.2, 5.3, 5.8_
  - [x] 1.4 创建 `src/lib/utils/logFilter.ts`，迁移日志过滤函数
    - 从 acp-demo 的 `utils/logFilter.ts` 完整迁移
    - _需求: 5.7_
  - [x]* 1.5 为 LogAggregator 编写属性测试
    - **Property 1: LogAggregator 流式消息聚合**
    - **Validates: Requirements 5.2, 5.3, 5.8**
  - [x]* 1.6 为 logFilter 编写属性测试
    - **Property 2: 日志过滤正确性**
    - **Validates: Requirements 5.7**

- [x] 2. 创建 HiCliSessionContext 并编写测试
  - [x] 2.1 创建 `src/context/HiCliSessionContext.tsx`
    - 扩展 QuestSessionContext 的状态，新增 rawMessages、aggregatedLogs、agentInfo、authMethods、agentCapabilities、modesSource、selectedCliId、cwd 字段
    - 新增 RAW_MESSAGE、AGGREGATED_LOG、CLI_SELECTED、DEBUG_PROTOCOL_INITIALIZED、RESET_STATE 等 action
    - 实现 hiCliReducer，复用 questReducer 处理共享 action，新增处理调试专用 action
    - 提供 HiCliSessionProvider、useHiCliState、useHiCliDispatch hooks
    - _需求: 8.1, 8.2, 8.3, 8.5_
  - [x]* 2.2 为 HiCliSessionContext reducer 编写属性测试：Quest 列表排序与管理
    - 在 `src/context/__tests__/HiCliSessionContext.test.ts` 中编写
    - **Property 3: Quest 列表排序与管理**
    - 验证 Quest 列表按 createdAt 降序排列，关闭 Quest 后列表数量减少 1
    - **Validates: Requirements 3.2, 3.4**
  - [x]* 2.3 为 HiCliSessionContext reducer 编写属性测试：流式消息累积渲染
    - **Property 4: 流式消息累积渲染**
    - 验证 agent_message_chunk 序列处理后的最终文本等于所有 chunk 文本的顺序拼接
    - **Validates: Requirements 4.2**
  - [x]* 2.4 为 HiCliSessionContext reducer 编写属性测试：原始消息记录完整性
    - **Property 5: 原始消息记录完整性**
    - 验证每条 WebSocket 消息都被记录到 rawMessages 中，且包含 direction、timestamp、data 字段
    - **Validates: Requirements 8.2, 8.4**

- [x] 3. 创建 useHiCliSession hook
  - [x] 3.1 创建 `src/hooks/useHiCliSession.ts`
    - 基于 useAcpSession 的模式，增加日志记录功能
    - 在消息发送和接收时调用 LogAggregator.processMessage 和 dispatch RAW_MESSAGE
    - 在 initialize 响应中提取 agentInfo、authMethods、agentCapabilities 并 dispatch DEBUG_PROTOCOL_INITIALIZED
    - 在 session/new 响应中提取 modes、models 等信息
    - 实现 connectToCli 方法：构建 WebSocket URL、重置状态、触发连接
    - 暴露 status、connect、disconnect、createQuest、switchQuest、closeQuest、sendPrompt、cancelPrompt、setModel、setMode、respondPermission 等方法
    - _需求: 2.3, 2.4, 2.5, 2.6, 3.1, 4.1, 4.6, 8.2, 8.3, 9.1, 10.2_

- [x] 4. Checkpoint - 确保基础层测试通过
  - 确保所有测试通过，如有问题请询问用户。

- [x] 5. 创建 HiCli 专用 UI 组件
  - [x] 5.1 创建 `src/components/hicli/HiCliSelector.tsx`
    - 使用 himarket 的 getCliProviders API（`/api/cli-providers`）获取 CLI 列表
    - 使用 Ant Design Select 组件替代原生 select
    - 包含工作目录输入框和连接按钮
    - 使用 Tailwind CSS 样式，使用 lucide-react 图标
    - _需求: 2.1, 2.2, 2.3, 11.1, 11.3, 11.4_
  - [x] 5.2 创建 `src/components/hicli/HiCliWelcome.tsx`
    - 未选择 CLI 工具时展示欢迎页面和 HiCliSelector
    - 已连接时展示"新建 Quest"按钮
    - 使用 Tailwind CSS 样式
    - _需求: 2.1, 2.5_
  - [x] 5.3 创建 `src/components/hicli/HiCliSidebar.tsx`
    - 展示 Quest 列表（按创建时间倒序排列）、新建 Quest 按钮、连接状态
    - 包含"切换工具"按钮，点击后断开连接并返回 CLI 选择界面
    - 点击 Quest 切换到对应会话，支持关闭 Quest 并自动切换到最近活跃 Quest
    - 复用 QuestSidebar 的逻辑模式，适配 HiCli 的状态
    - 使用 Tailwind CSS 样式
    - _需求: 2.6, 3.1, 3.2, 3.3, 3.4, 11.1_
  - [x] 5.4 创建 `src/components/hicli/AcpLogPanel.tsx`
    - 从 acp-demo 迁移 AcpLogPanel 组件逻辑
    - 展示聚合日志条目：方向箭头（发送/接收）、method 名称（带颜色编码）、RPC ID、聚合消息数徽章、时间戳
    - 点击日志条目展开显示完整 JSON 数据
    - 包含过滤输入框，使用 logFilter 按 method 或摘要文本过滤（不区分大小写）
    - 新日志产生时自动滚动到最新条目（用户未手动滚动时）
    - 使用 Tailwind CSS 替代原生 CSS 类名
    - 使用 useHiCliState 获取 aggregatedLogs
    - _需求: 5.1, 5.4, 5.5, 5.6, 5.7, 11.1, 11.2_
  - [x]* 5.5 为 AcpLogPanel 编写属性测试：日志条目渲染完整性
    - **Property 7: 日志条目渲染完整性**
    - 验证每条 AggregatedLogEntry 渲染输出包含方向指示、method/summary、时间戳，聚合条目包含消息数徽章
    - **Validates: Requirements 5.5**
  - [x] 5.6 创建 `src/components/hicli/AgentInfoCard.tsx`
    - 从 acp-demo 迁移 AgentInfoCard 组件逻辑
    - 展示 Agent 基本信息（名称、标题、版本），数据来源标注为 "initialize"
    - 展示认证方式列表（ID、名称、类型、描述）
    - 展示 Mode 列表（ID、名称、描述），标注数据来源（initialize 或 session/new）
    - 展示 Agent 能力配置的完整 JSON 数据
    - 展示 Model 列表（Model ID、名称），数据来源标注为 "session/new"
    - 展示 Slash Command 列表（命令名称、描述）
    - 未提供信息时展示"未提供"占位提示
    - 使用 Tailwind CSS 替代原生 CSS 类名
    - 使用 useHiCliState 获取 agentInfo、authMethods、agentCapabilities 等
    - _需求: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 11.1_
  - [x]* 5.7 为 AgentInfoCard 编写属性测试：Agent 信息缺失字段占位
    - **Property 6: Agent 信息缺失字段占位**
    - 验证 AgentInfo 字段为 undefined 时渲染占位文本
    - **Validates: Requirements 6.8**
  - [x] 5.8 创建 `src/components/hicli/HiCliTopBar.tsx`
    - 展示当前 Agent 名称/版本信息
    - 展示当前选中的 CLI 工具名称
    - 展示 Model 选择下拉框（Agent 提供多个 Model 时）
    - 展示 Mode 选择下拉框（Agent 提供多个 Mode 时）
    - 展示 WebSocket 连接状态（disconnected、connecting、connected）
    - 展示 Token 使用量和费用信息（Agent 返回 usage 时）
    - 包含"ACP 日志"和"Agent 信息"调试标签切换按钮
    - 使用 Ant Design Select 组件、Tailwind CSS 样式、lucide-react 图标
    - _需求: 7.1, 7.2, 7.3, 7.4, 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 11.1, 11.3, 11.4_

- [x] 6. 创建 HiCli 页面并集成路由
  - [x] 6.1 创建 `src/pages/HiCli.tsx`
    - 使用 Header + HiCliSessionProvider + HiCliContent 结构
    - HiCliContent 组合所有子组件：HiCliSidebar、HiCliTopBar、ChatStream、QuestInput、DebugPanel（AcpLogPanel/AgentInfoCard）、PermissionDialog
    - 未选择 CLI 工具时展示 HiCliWelcome
    - 已连接且无活跃 Quest 时展示新建 Quest 引导
    - 实现调试面板切换逻辑（none/acplog/info），再次点击已激活标签关闭面板
    - 调试面板包含标题栏和关闭按钮
    - 布局：左侧聊天区 + 右侧调试面板（类似 HiCoding 的 flex 左右分栏）
    - 复用 ChatStream、QuestInput、ToolCallCard、PermissionDialog 等聊天组件
    - 流式消息实时渲染、思考过程可折叠展示、工具调用卡片展示、执行计划展示
    - 取消按钮发送 `session/cancel` 通知
    - _需求: 1.4, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 10.1, 10.2, 10.3, 11.5_
  - [x] 6.2 修改 `src/router.tsx`，添加 `/hicli` 路由
    - 导入 HiCli 页面组件
    - 在 Coding 路由之后添加 HiCli 路由
    - _需求: 1.2_
  - [x] 6.3 修改 `src/components/Header.tsx`，在 tabs 数组中添加 HiCli 标签
    - 在 "HiCoding" 之后添加 `{ path: "/hicli", label: "HiCli" }`
    - 确保当前路由为 `/hicli` 时高亮该标签
    - _需求: 1.1, 1.3_

- [x] 7. 最终 Checkpoint - 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。

## 备注

- 标记 `*` 的子任务为可选测试任务，可跳过以加速 MVP 开发
- 每个任务引用了具体的需求编号以确保可追溯性
- 属性测试使用 fast-check 库，每个属性测试至少运行 100 次迭代
- 复用的组件（ChatStream、QuestInput、PermissionDialog、ToolCallCard、PlanDisplay 等）来自 `src/components/quest/` 目录
- HiCli 的 useHiCliSession 基于现有 useAcpSession 的模式，但增加了日志记录和调试信息采集
- 样式全部使用 Tailwind CSS + Ant Design 组件 + lucide-react 图标，与 HiWork/HiCoding 保持一致
