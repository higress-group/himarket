# 实现计划

- [x] 1. 编写 bug 条件探索性测试
  - **Property 1: Fault Condition** - 连接初始化后自动创建会话
  - **重要**：此测试必须在实施修复之前编写
  - **关键**：此测试在未修复代码上必须失败——失败即确认 bug 存在
  - **不要**在测试失败时尝试修复测试或代码
  - **说明**：此测试编码了期望行为——修复后测试通过即验证修复正确性
  - **目标**：生成反例，证明 bug 存在
  - **Scoped PBT 方法**：针对确定性 bug，将属性范围限定到具体失败场景以确保可复现
  - 测试文件：`src/hooks/__tests__/useHiCliSession.lazySession.test.ts`
  - 测试场景 1：模拟 WebSocket 连接成功 + ACP 初始化完成（local 运行时），断言 `createQuest` 不应被自动调用
    - 构造输入：`{ event: "connection_initialized", hasActiveQuest: false, userSentMessage: false }`
    - 期望行为：`noSessionCreated(result)` — 不应触发 `session/new`
    - 故障条件：`isBugCondition(input)` 中 `event == "connection_initialized" AND hasActiveQuest == false AND userSentMessage == false`
  - 测试场景 2：模拟 k8s 运行时连接成功 + 沙箱就绪 + ACP 初始化完成，断言 `createQuest` 不应被自动调用
  - 测试场景 3：已有一个无消息的空白 Quest，点击"+"按钮，断言不应创建新 Quest 而应切换到已有空白 Quest
    - 故障条件：`isBugCondition(input)` 中 `event == "plus_button_clicked" AND existingEmptyQuest == true`
  - 测试场景 4：无活跃 Quest 时调用 `sendPrompt`，断言应先创建会话再发送消息（而非直接返回 `{ queued: false }`）
  - 在未修复代码上运行测试
  - **预期结果**：测试失败（这是正确的——证明 bug 存在）
  - 记录发现的反例以理解根因
  - 当测试编写完成、运行完毕、失败已记录后，标记任务完成
  - _Requirements: 1.1, 1.2, 1.5, 2.1, 2.2, 2.5, 2.6_

- [x] 2. 编写保持性属性测试（在实施修复之前）
  - **Property 2: Preservation** - 已有会话时的消息发送与交互行为
  - **重要**：遵循观察优先方法论
  - **步骤**：先在未修复代码上观察非 bug 条件下的行为，再编写属性测试捕获这些行为
  - 测试文件：`src/hooks/__tests__/useHiCliSession.preservation.test.ts`
  - 观察 1：已有活跃 Quest 时，`sendPrompt("hello")` 应调用 `startPrompt` 并返回 `{ queued: false, requestId }`
  - 观察 2：已有活跃 Quest 且正在处理中时，`sendPrompt("hello")` 应入队并返回 `{ queued: true, queuedPromptId }`
  - 观察 3：多会话切换时，`switchQuest(questId)` 应 dispatch `QUEST_SWITCHED` 事件
  - 观察 4：所有会话都有消息时，点击"+"按钮（`createQuest`）应正常创建新会话
  - 观察 5：WebSocket 断开时应重置连接状态和初始化标记
  - 编写属性基测试：对于所有非 bug 条件输入（`NOT isBugCondition(input)`），行为应与原函数一致
  - 属性基测试自动生成大量测试用例，提供更强的保持性保证
  - 在未修复代码上运行测试
  - **预期结果**：测试通过（确认基线行为已被捕获）
  - 当测试编写完成、运行完毕、在未修复代码上通过后，标记任务完成
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

- [x] 3. 实施 HiCli 延迟创建会话修复

  - [x] 3.1 移除 `HiCli.tsx` 中自动创建 Quest 的 `useEffect`
    - 删除 `autoCreatedRef` 及其相关的 `useEffect`（约第 45-65 行）
    - 连接成功且初始化完成后，不再自动调用 `session.createQuest()`
    - _Bug_Condition: isBugCondition(input) where event == "connection_initialized" AND hasActiveQuest == false AND userSentMessage == false_
    - _Expected_Behavior: noSessionCreated(result) — 不触发 session/new_
    - _Preservation: 断开连接时的状态重置行为不受影响_
    - _Requirements: 1.1, 1.2, 2.1, 2.2_

  - [x] 3.2 修改 `HiCli.tsx` 主内容区条件渲染
    - 当 `isConnected && state.initialized && !hasActiveQuest` 时，展示 `QuestInput` 输入框 + 欢迎提示，而非仅展示"新建 Quest"按钮
    - 输入框的 `onSend` 绑定到改造后的 `sendPrompt`（支持无 Quest 时先创建再发送）
    - _Bug_Condition: 无活跃 Quest 时用户无法直接发送消息_
    - _Expected_Behavior: showsBlankWelcomePage(result) AND inputBoxAvailable(result)_
    - _Preservation: 已有活跃 Quest 时的渲染逻辑不变_
    - _Requirements: 2.1, 2.2, 2.3, 2.6_

  - [x] 3.3 修改 `HiCli.tsx` "+"按钮增加空白会话复用逻辑
    - `handleCreateQuest` 中增加判断：如果当前已存在无消息的空白会话，则切换到该会话而非创建新会话
    - _Bug_Condition: isBugCondition(input) where event == "plus_button_clicked" AND existingEmptyQuest == true_
    - _Expected_Behavior: switchedToExistingEmptyQuest(result)_
    - _Preservation: 所有会话都有消息时，"+"按钮继续正常创建新会话_
    - _Requirements: 1.5, 2.5, 3.3_

  - [x] 3.4 修改 `useHiCliSession.ts` 的 `sendPrompt` 支持无 Quest 状态
    - 当 `activeQuestId` 为 null 时，先调用 `createQuest()` 创建会话，创建成功后立即调用 `startPrompt()` 发送消息
    - 返回值保持兼容（`{ queued: false, requestId }` 或 `{ queued: true }`）
    - 此行为对所有 CLI Provider 和运行时类型一致
    - _Bug_Condition: activeQuestId == null 时 sendPrompt 直接返回 { queued: false }_
    - _Expected_Behavior: sessionCreatedThenMessageSent(result)_
    - _Preservation: 已有活跃 Quest 时 sendPrompt 行为完全不变_
    - _Requirements: 2.6, 2.7, 3.1_

  - [x] 3.5 修改 `HiCliWelcome.tsx` 欢迎页适配无 Quest 状态
    - 已连接且初始化完成但无 Quest 时，展示欢迎提示文案（如"发送消息开始新对话"），不再以"新建 Quest"按钮作为唯一入口
    - _Preservation: 未连接或未初始化时的欢迎页行为不变_
    - _Requirements: 2.1, 2.3_

  - [x] 3.6 修改 `HiCliSidebar.tsx` 空会话列表提示文案
    - 已连接但无 Quest 时，空状态提示改为"发送消息开始新对话"
    - _Preservation: 有 Quest 时的 sidebar 列表展示不变_
    - _Requirements: 2.3_

  - [x] 3.7 修改 `HiCliTopBar.tsx` mode 选择器无 Quest 时的 value 处理
    - 当无活跃 Quest 时，mode 选择器的 `value` 回退到 `hiCliState` 中存储的 `currentModeId`（由 `PROTOCOL_INITIALIZED` 设置）
    - model 选择器在无 Quest 时不显示是合理的（models 通常由 `session/new` 返回）
    - _Preservation: 有活跃 Quest 时的 model/mode 选择器行为不变_
    - _Requirements: 1.4, 2.4, 3.8_

  - [x] 3.8 验证 bug 条件探索性测试现在通过
    - **Property 1: Expected Behavior** - 延迟创建会话
    - **重要**：重新运行任务 1 中的同一测试——不要编写新测试
    - 任务 1 的测试编码了期望行为，当测试通过时即确认期望行为已满足
    - 运行 bug 条件探索性测试
    - **预期结果**：测试通过（确认 bug 已修复）
    - _Requirements: 2.1, 2.2, 2.5, 2.6_

  - [x] 3.9 验证保持性测试仍然通过
    - **Property 2: Preservation** - 已有会话的消息发送与交互行为
    - **重要**：重新运行任务 2 中的同一测试——不要编写新测试
    - 运行保持性属性测试
    - **预期结果**：测试通过（确认无回归）
    - 确认修复后所有测试仍然通过（无回归）

- [x] 4. 检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。
