# HiCli 延迟创建会话 Bugfix 设计

## 概述

HiCli 页面当前在 WebSocket 连接成功且 ACP 协议初始化完成后，会通过 `useEffect` 自动调用 `session/new` 创建一个空会话（Quest），即使用户尚未发送任何消息。这导致大量无用空会话、浪费服务端资源。此外，当已存在空白会话时，点击"+"按钮会重复创建新的空会话。

修复策略：移除自动创建逻辑，改为在用户发送第一条消息时才触发 `session/new`；同时适配 UI 组件以支持无 Quest 的空白初始状态，并在点击"+"时复用已有空白会话。

## 术语表

- **Bug_Condition (C)**：触发 bug 的条件——WebSocket 连接成功且 ACP 初始化完成后，系统在无用户交互的情况下自动创建会话
- **Property (P)**：期望行为——仅在用户发送第一条消息时才创建会话
- **Preservation**：现有的消息发送、会话切换、鼠标交互等行为必须保持不变
- **Quest**：ACP 协议中的会话（session），对应一次对话上下文
- **autoCreatedRef**：`HiCli.tsx` 中用于防止重复自动创建的 ref 标记
- **sendPrompt**：`useHiCliSession.ts` 中发送用户消息的方法，当前要求必须有活跃 Quest
- **createQuest**：`useHiCliSession.ts` 中创建新会话的方法，调用 `session/new`
- **initialize**：ACP 协议初始化请求，返回 agentInfo、modes 等元数据

## Bug 详情

### 故障条件

Bug 在以下场景触发：用户选择任意 CLI Provider 进入 HiCli 页面，WebSocket 连接成功且 ACP 初始化完成（对于 k8s 运行时还需沙箱就绪），系统立即自动调用 `session/new` 创建空会话，无需用户发送任何消息。

具体代码位于 `HiCli.tsx` 的 `useEffect` 中：当 `isConnected && state.initialized && sandboxReady && Object.keys(state.quests).length === 0` 时，自动调用 `session.createQuest()`。

**形式化规约：**
```
FUNCTION isBugCondition(input)
  INPUT: input of type { event: string, hasActiveQuest: boolean, userSentMessage: boolean, existingEmptyQuest: boolean }
  OUTPUT: boolean

  // 场景1：自动创建空会话
  IF input.event == "connection_initialized"
     AND input.hasActiveQuest == false
     AND input.userSentMessage == false
  THEN RETURN true

  // 场景2：已有空白会话时点击"+"重复创建
  IF input.event == "plus_button_clicked"
     AND input.existingEmptyQuest == true
  THEN RETURN true

  RETURN false
END FUNCTION
```

### 示例

- 用户选择 native CLI Provider（local 运行时）→ WebSocket 连接成功 → ACP 初始化完成 → **实际**：自动创建空会话 → **期望**：展示空白初始页面，等待用户输入
- 用户选择 nodejs CLI Provider（k8s 运行时）→ WebSocket 连接成功 → 沙箱就绪 → ACP 初始化完成 → **实际**：自动创建空会话 → **期望**：展示空白初始页面，等待用户输入
- 用户已有一个空白会话（无消息）→ 点击"+"按钮 → **实际**：创建第二个空会话 → **期望**：切换到已有的空白会话
- 用户在空白初始页面输入消息并发送 → **期望**：先自动创建会话，再发送消息（对用户透明）

## 期望行为

### 保持不变的行为

**不变行为：**
- 已有活跃会话时，发送消息继续正常工作（所有 CLI Provider 和运行时类型）
- 多会话之间切换继续正常工作，展示对应聊天记录
- 所有会话都有消息时，点击"+"按钮继续正常创建新会话
- WebSocket 断开连接时继续正常重置连接状态和初始化标记
- k8s 运行时沙箱创建中时继续展示状态提示，不允许发送消息
- 切换 CLI 工具时继续正常断开连接并重置所有状态
- `session/new` 返回的 models/modes 信息继续正常合并到状态中

**范围：**
所有不涉及"自动创建会话"和"空白会话重复创建"的输入和交互应完全不受此修复影响，包括：
- 已有活跃会话时的所有消息发送操作
- 会话切换操作
- 鼠标点击、键盘输入等 UI 交互
- WebSocket 连接/断开生命周期
- 沙箱状态管理

## 假设的根因

基于代码分析，最可能的问题如下：

1. **`HiCli.tsx` 中的自动创建 `useEffect`**：当 `isConnected && state.initialized && sandboxReady && Object.keys(state.quests).length === 0` 时，无条件调用 `session.createQuest()`。这是 bug 的直接原因——不应在无用户交互时自动创建会话。

2. **`sendPrompt` 不支持无 Quest 状态**：`useHiCliSession.ts` 中的 `sendPrompt` 方法在 `activeQuestId` 为 null 时直接返回 `{ queued: false }`，不会尝试先创建会话。这意味着移除自动创建后，用户在无 Quest 状态下无法发送消息。

3. **`HiCli.tsx` 主内容区的条件渲染**：当 `!hasActiveQuest` 时展示 `HiCliWelcome`（含"新建 Quest"按钮），而非展示输入框。移除自动创建后，用户需要手动点击按钮才能开始对话，体验不佳。

4. **`HiCliTopBar` 的 model/mode 回退逻辑**：`modelOptions` 和 `modeOptions` 在无活跃 Quest 时回退到 `hiCliState.models` 和 `hiCliState.modes`。`hiCliState.models` 初始为空数组，但 `hiCliState.modes` 在 `PROTOCOL_INITIALIZED` 时已从 initialize 响应中填充。model 选择器在无 Quest 时不显示是合理的（models 通常由 `session/new` 返回），但 mode 选择器应能正常展示 initialize 返回的 modes。

5. **Sidebar "+"按钮无空白会话复用逻辑**：`HiCliSidebar` 的 `onCreateQuest` 直接调用 `session.createQuest()`，没有检查是否已存在空白会话。

## 正确性属性

Property 1: 故障条件 - 延迟创建会话

_对于任意_ 输入，当 WebSocket 连接成功且 ACP 初始化完成（含 k8s 沙箱就绪），但用户尚未发送任何消息时，修复后的系统不应调用 `session/new`，应保持无会话的空白初始状态。仅当用户发送第一条消息时，系统才应先创建会话再发送消息。

**验证需求：2.1, 2.2, 2.6, 2.7**

Property 2: 保持不变 - 已有会话的消息发送与交互

_对于任意_ 输入，当已存在活跃会话时，修复后的 `sendPrompt` 函数应与原函数产生完全相同的结果，保持消息发送、队列排队、会话切换等所有现有行为不变。

**验证需求：3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8**

## 修复实现

### 所需变更

假设根因分析正确：

**文件**：`himarket-web/himarket-frontend/src/pages/HiCli.tsx`

**变更 1：移除自动创建 Quest 的 `useEffect`**
- 删除 `autoCreatedRef` 及其相关的 `useEffect`（约第 45-65 行）
- 连接成功且初始化完成后，不再自动调用 `session.createQuest()`

**变更 2：主内容区条件渲染调整**
- 当 `isConnected && state.initialized && !hasActiveQuest` 时，不再展示"新建 Quest"按钮的 `HiCliWelcome`，改为展示输入框（`QuestInput`）+ 欢迎提示
- 输入框的 `onSend` 绑定到改造后的 `sendPrompt`（支持无 Quest 时先创建再发送）

**变更 3："+"按钮增加空白会话复用逻辑**
- `handleCreateQuest` 中增加判断：如果当前已存在无消息的空白会话，则切换到该会话而非创建新会话

---

**文件**：`himarket-web/himarket-frontend/src/hooks/useHiCliSession.ts`

**变更 4：`sendPrompt` 支持无 Quest 状态**
- 当 `activeQuestId` 为 null 时，不再直接返回 `{ queued: false }`
- 改为：先调用 `createQuest()` 创建会话，创建成功后立即调用 `startPrompt()` 发送消息
- 返回值保持兼容（`{ queued: false, requestId }` 或 `{ queued: true }`）
- 此行为对所有 CLI Provider 和运行时类型一致，`cliSessionConfig` 已在 WebSocket URL 中传递，不影响 `session/new` 调用

---

**文件**：`himarket-web/himarket-frontend/src/components/hicli/HiCliWelcome.tsx`

**变更 5：欢迎页适配无 Quest 状态**
- 已连接且初始化完成但无 Quest 时，展示欢迎提示文案（如"发送消息开始新对话"），不再展示"新建 Quest"按钮作为唯一入口

---

**文件**：`himarket-web/himarket-frontend/src/components/hicli/HiCliSidebar.tsx`

**变更 6：空会话列表提示文案更新**
- 已连接但无 Quest 时，空状态提示改为"发送消息开始新对话"

---

**文件**：`himarket-web/himarket-frontend/src/components/hicli/HiCliTopBar.tsx`

**变更 7：mode 选择器无 Quest 时的 value 处理**
- 当无活跃 Quest 时，mode 选择器的 `value` 应回退到 `hiCliState` 中存储的 `currentModeId`（由 `PROTOCOL_INITIALIZED` 设置），而非 `undefined`
- model 选择器同理，但 models 通常在 `session/new` 后才有数据，无 Quest 时不显示是合理的

## 测试策略

### 验证方法

测试策略分两阶段：首先在未修复代码上复现 bug（探索性测试），然后验证修复的正确性和行为保持。

### 探索性故障条件检查

**目标**：在实施修复前，复现 bug 并确认根因分析。如果根因被否定，需要重新假设。

**测试计划**：编写测试模拟 WebSocket 连接成功 + ACP 初始化完成的场景，断言系统是否自动调用了 `session/new`。在未修复代码上运行以观察失败。

**测试用例**：
1. **Local 运行时自动创建测试**：模拟 native CLI Provider 连接成功 + 初始化完成，验证是否自动调用 `createQuest`（未修复代码上会失败——会自动创建）
2. **K8s 运行时自动创建测试**：模拟 k8s CLI Provider 连接成功 + 沙箱就绪 + 初始化完成，验证是否自动调用 `createQuest`（未修复代码上会失败）
3. **空白会话重复创建测试**：已有一个无消息的 Quest，点击"+"按钮，验证是否创建了新 Quest（未修复代码上会失败——会重复创建）
4. **无 Quest 时发送消息测试**：模拟无活跃 Quest 时调用 `sendPrompt`，验证是否能先创建会话再发送（未修复代码上会失败——直接返回 `{ queued: false }`）

**预期反例**：
- 连接初始化完成后立即触发 `session/new`，无需用户交互
- 可能原因：`HiCli.tsx` 中的 `useEffect` 自动创建逻辑

### 修复检查

**目标**：验证对于所有触发 bug 条件的输入，修复后的函数产生期望行为。

**伪代码：**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := fixedSystem(input)
  IF input.event == "connection_initialized" THEN
    ASSERT noSessionCreated(result)
    ASSERT showsBlankWelcomePage(result)
  END IF
  IF input.event == "first_message_sent" AND NOT hasActiveQuest THEN
    ASSERT sessionCreatedThenMessageSent(result)
  END IF
  IF input.event == "plus_button_clicked" AND existingEmptyQuest THEN
    ASSERT switchedToExistingEmptyQuest(result)
  END IF
END FOR
```

### 保持检查

**目标**：验证对于所有不触发 bug 条件的输入，修复后的函数与原函数产生相同结果。

**伪代码：**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalSystem(input) == fixedSystem(input)
END FOR
```

**测试方法**：推荐使用属性基测试（Property-Based Testing）进行保持检查，因为：
- 自动生成大量测试用例覆盖输入域
- 捕获手动单元测试可能遗漏的边界情况
- 对所有非 bug 输入的行为不变性提供强保证

**测试计划**：先在未修复代码上观察已有会话时的消息发送、会话切换等行为，然后编写属性基测试捕获这些行为。

**测试用例**：
1. **已有会话消息发送保持**：观察未修复代码上已有活跃 Quest 时 `sendPrompt` 的行为，验证修复后行为一致
2. **会话切换保持**：观察未修复代码上多会话切换行为，验证修复后行为一致
3. **WebSocket 生命周期保持**：观察未修复代码上连接/断开时的状态重置行为，验证修复后行为一致
4. **沙箱状态管理保持**：观察未修复代码上 k8s 沙箱创建/就绪/错误状态的处理，验证修复后行为一致

### 单元测试

- 测试 `sendPrompt` 在无活跃 Quest 时的行为（先创建再发送）
- 测试 `sendPrompt` 在有活跃 Quest 时的行为（直接发送，保持不变）
- 测试 `handleCreateQuest` 在已有空白会话时的复用逻辑
- 测试 `handleCreateQuest` 在所有会话都有消息时的正常创建逻辑
- 测试 TopBar mode 选择器在无 Quest 时的 value 回退

### 属性基测试

- 生成随机的 Quest 状态（有/无会话、有/无消息），验证 `sendPrompt` 在有活跃 Quest 时行为与原函数一致
- 生成随机的 CLI Provider 配置（不同 runtimeCategory、不同 cliSessionConfig），验证延迟创建行为一致
- 生成随机的会话列表状态，验证"+"按钮在所有会话都有消息时正常创建新会话

### 集成测试

- 完整流程测试：选择 CLI Provider → 连接 → 初始化 → 验证无自动创建 → 发送消息 → 验证会话创建 + 消息发送
- K8s 运行时完整流程：连接 → 沙箱创建中 → 沙箱就绪 → 验证无自动创建 → 发送消息 → 验证会话创建
- 空白会话复用流程：创建会话 → 不发消息 → 点击"+" → 验证切换到已有空白会话
- 多会话切换 + 消息发送流程：验证修复不影响现有多会话交互
