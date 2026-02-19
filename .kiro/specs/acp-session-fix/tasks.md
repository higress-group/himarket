# 实现计划: ACP 会话修复

## 概述

将 hiwork 和 hicoding 的 ACP 会话管理重构为与 HiCli 一致的延迟连接模式。按照从底层到上层的顺序实现：先修改 hook 层支持延迟连接，再提取公共 CLI 选择器组件，然后改造各页面组件，最后清理顶部栏。

## 任务

- [x] 1. 修改 useAcpSession 支持延迟连接模式
  - [x] 1.1 修改 `useAcpSession.ts` 中 `useAcpWebSocket` 的 `autoConnect` 参数，当 `wsUrl` 为空字符串时设为 false
    - 将 `autoConnect: !isWebContainer` 改为 `autoConnect: !isWebContainer && !!wsUrl`
    - _Requirements: 4.1, 4.2_
  - [ ]* 1.2 编写属性测试验证 WebSocket URL 与连接行为的关系
    - **Property 4: WebSocket URL 与连接行为的关系**
    - **Validates: Requirements 4.1, 4.2**

- [x] 2. 提取公共 CLI 选择器组件
  - [x] 2.1 从 `HiCliSelector.tsx` 提取公共 `CliSelector` 组件到 `src/components/common/CliSelector.tsx`
    - 复用 HiCliSelector 的核心逻辑（加载 provider 列表、选择、连接按钮）
    - 添加 `showRuntimeSelector` prop（默认 false），hiwork/hicoding 不显示运行时选择
    - HiCliSelector 改为引用公共 CliSelector 并传 `showRuntimeSelector={true}`
    - _Requirements: 1.1, 2.1_

- [x] 3. 改造 hiwork（Quest 模块）页面
  - [x] 3.1 改造 `QuestWelcome.tsx` 支持 CLI 选择
    - 参考 `HiCliWelcome.tsx`，添加 `onSelectCli`、`isConnected` props
    - 未连接时显示 CliSelector，已连接时显示创建 Quest 按钮
    - _Requirements: 1.1, 1.2_
  - [x] 3.2 改造 `QuestSidebar.tsx` 添加切换工具按钮
    - 参考 `HiCliSidebar.tsx`，添加 `onSwitchTool`、`status` props
    - 在底部连接状态旁添加「切换」按钮（仅已连接时显示）
    - _Requirements: 1.5_
  - [x] 3.3 改造 `Quest.tsx` 实现延迟连接流程
    - 初始 `wsUrl` 为空字符串 `""`
    - 添加 `handleSelectCli` 回调：设置 provider、构建 wsUrl
    - 添加 `handleSwitchTool` 回调：disconnect + RESET_STATE + 清空 wsUrl
    - 添加 `autoCreatedRef`，在 `!isConnected` 时重置为 false
    - 添加自动创建 Quest 的 useEffect（initialized + quests 为空 + autoCreatedRef=false）
    - 移除 `handleProviderChange` 中的顶部栏切换逻辑
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_
  - [ ]* 3.4 编写属性测试验证 QUEST_CREATED 状态同步
    - **Property 6: QUEST_CREATED 状态同步**
    - **Validates: Requirements 1.4, 5.3**

- [x] 4. Checkpoint - 确保 hiwork 改造完成
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 5. 改造 hicoding（Coding 模块）页面
  - [x] 5.1 改造 `Coding.tsx` 实现延迟连接流程
    - 初始 `wsUrl` 为空字符串 `""`
    - 添加 `handleSelectCli` 回调：设置 provider、构建 wsUrl
    - 添加 `handleSwitchTool` 回调：disconnect + RESET_STATE + 清空 wsUrl + 重置 autoCreatedRef
    - 修改 `autoCreatedRef` 在 `!isConnected` 时重置为 false
    - 未连接时显示欢迎页（含 CliSelector），已连接时显示 IDE 界面
    - 移除 `handleProviderChange` 中的顶部栏切换逻辑
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 6. 清理顶部栏 CLI 切换入口
  - [x] 6.1 修改 `QuestTopBar.tsx` 移除 CliProviderSelect
    - 移除 `CliProviderSelect` 组件引用和相关 props（currentProvider、onProviderChange）
    - 保留模型选择、连接状态、Token 用量
    - _Requirements: 3.1, 3.4_
  - [x] 6.2 修改 `CodingTopBar.tsx` 移除 CliProviderSelect
    - 移除 `CliProviderSelect` 组件引用和相关 props（currentProvider、onProviderChange）
    - 保留模型选择、连接状态、Token 用量、文件树切换按钮
    - _Requirements: 3.2, 3.4_

- [ ] 7. Reducer 和工具函数的属性测试
  - [ ]* 7.1 编写属性测试验证 RESET_STATE 恢复初始状态
    - **Property 2: RESET_STATE 恢复初始状态**
    - **Validates: Requirements 4.4**
  - [ ]* 7.2 编写属性测试验证 clearPendingRequests 清理
    - **Property 7: clearPendingRequests 清理所有等待请求**
    - **Validates: Requirements 5.4**
  - [ ]* 7.3 编写属性测试验证 URL 构建正确性
    - **Property 1: CLI 选择后 URL 构建正确性**
    - **Validates: Requirements 1.2, 2.2**

- [ ] 8. 后端 local 运行时路径验证
  - [ ]* 8.1 编写属性测试验证 resolveRuntimeType 默认值
    - **Property 9: resolveRuntimeType 默认值**
    - **Validates: Requirements 6.3**
  - [ ]* 8.2 编写单元测试验证 local 运行时不使用 pendingMessageMap
    - **Property 8: local 运行时不使用 pendingMessageMap**
    - **Validates: Requirements 6.1, 6.2**

- [x] 9. Final checkpoint - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的任务为可选测试任务，可跳过以加快 MVP 进度
- 每个任务引用了具体的需求编号以确保可追溯性
- Checkpoint 任务确保增量验证
- 属性测试验证通用正确性属性，单元测试验证具体示例和边界情况
