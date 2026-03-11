# Block Grouping (分组折叠) 算法与设计文档

> 面向复刻的设计参考。描述 AI 对话界面中将连续的低价值操作块折叠成分组卡片的算法思路、策略体系和交互设计。

---

## 1. 问题背景

AI Agent 在执行任务时会产生大量中间操作（读文件、搜索代码、思考等），这些操作对用户来说信息密度低，但数量多、占屏空间大。需要在不丢失信息的前提下，将这些操作折叠成紧凑的"活动卡片"，只在用户需要时展开查看细节。

**核心矛盾**：

- 用户关注的是**最终结果**（文件编辑、终端执行、回答文本）
- Agent 的**中间过程**（读文件、搜索、思考）虽有用但不紧急
- 不同场景（对话问答 vs 自主任务 vs 快速执行）对信息可见度的需求不同

**举例**：Agent 收到"帮我重构这个函数"的请求后，可能依次执行：

```
[thought]      思考应该怎么做...
[tool_call]    read_file("src/utils.ts")        ← 探索
[tool_call]    search_codebase("usage of foo")   ← 探索
[tool_call]    read_file("src/helpers.ts")       ← 探索
[message]      "我发现这个函数被3处引用..."       ← 短文本
[tool_call]    edit_file("src/utils.ts", ...)    ← 编辑（用户需要看到）
[message]      "已完成重构，主要变更是..."         ← 最终回复
```

理想的折叠效果：前 5 个块合并成一张"已探索 · 3 文件 1 搜索"的卡片，`edit_file` 和最终回复独立展示。

---

## 2. 整体架构

### 2.1 分层设计

```
┌──────────────────────────────────┐
│          UI 渲染层               │  折叠卡片组件、展开/收起动画、状态持久化
├──────────────────────────────────┤
│          分组服务层              │  策略调度、分组循环、SubAgent 递归
├──────────────────────────────────┤
│          策略层                  │  各模式的折叠规则（策略模式）
├──────────────────────────────────┤
│          块转换层                │  ACP 事件流 → RenderBlock 转换、聚合
├──────────────────────────────────┤
│          ACP 协议层              │  流状态机、事件接收、会话管理
├──────────────────────────────────┤
│          类型与分类层            │  Block 类型定义、工具分类常量
└──────────────────────────────────┘
```

各层职责边界清晰：

- **类型与分类层**：纯数据定义，无业务逻辑。定义 Block 类型、工具分类常量、接口类型。
- **ACP 协议层**：管理会话生命周期和流状态（Initial → Streaming → Completed 等），接收并存储原始 ACP 事件。
- **块转换层**：将 ACP 事件流转换为 RenderBlock 列表（流式聚合、原位更新、SubAgent 分离）。
- **策略层**：封装"什么块该折叠、分组何时结束"的判断规则，不关心如何遍历和组装。
- **分组服务层**：执行分组循环（遍历 + flush），调用策略层做判断，计算分组元数据。
- **UI 渲染层**：根据分组结果渲染卡片，管理展开/收起交互和状态持久化。

### 2.2 数据流

```
ACP 事件流 (session/update)
      │
      ▼
[状态机] 更新 ACPStreamState，存储 progress
      │
      ▼
[块转换] computeBlocks: progress[] → ACPRenderBlock[]
      │
      ▼
[策略选择] 根据会话模式 + 执行模式选择折叠策略
      │
      ▼
[预处理] 扫描所有 blocks，标记"受保护"的最后消息索引
      │
      ▼
[分组循环] 逐块遍历，判断折叠/不折叠，连续可折叠块合并为 Group
      │
      ▼
[元数据计算] 为每个 Group 计算 isExploring / toolsSummary / isThinkingOnly 等
      │
      ▼
[SubAgent 递归] 对嵌套的子 Agent 内部 blocks 递归执行同样的分组
      │
      ▼
分组后的 Block 列表 (Group | 原始Block)
      │
      ▼
[UI 渲染] Group → 折叠卡片; 原始 Block → 正常渲染
```

---

## 3. ACP 协议与数据流

Block Grouping 的输入不是凭空产生的，它依赖上游的 **ACP (Agent Client Protocol)** 协议。本章描述从 ACP 事件流到分组输入的完整数据管线。

### 3.1 ACP 协议概述

ACP 是 Agent 与 Client 之间的通信协议。Agent 在处理请求时通过 `session/update` 事件流式推送进度，Client 接收后转换为 UI 可渲染的 Block 列表。

**通信模型**：

```
Client (IDE)                           Agent (后端)
    │                                     │
    │──── session/new ────────────────────►│  创建会话
    │◄─── { sessionId } ─────────────────│
    │                                     │
    │──── session/prompt ─────────────────►│  发送用户消息
    │                                     │
    │◄─── session/update ─────────────────│  Agent 流式推送：
    │◄─── session/update ─────────────────│    思考、工具调用、消息...
    │◄─── session/update ─────────────────│    （多次）
    │                                     │
    │  [可选：权限请求]                     │
    │◄─── session/request-permission ─────│  工具需要用户确认
    │──── permission response ────────────►│  用户批准/拒绝
    │                                     │
    │◄─── session/prompt response ────────│  本轮结束（含 stopReason）
    │                                     │
    │──── session/prompt ─────────────────►│  下一轮...
```

### 3.2 session/update 事件类型

每个 `session/update` 事件携带一个 `SessionUpdate`，通过 `sessionUpdate` 字段区分类型：

| sessionUpdate 类型 | 含义 | 转换为的 Block 类型 |
|-------------------|------|---------------------|
| `user_message_chunk` | 用户消息片段 | `user_message` |
| `agent_message_chunk` | Agent 文本回复片段 | `message` |
| `agent_thought_chunk` | Agent 思考过程片段 | `thought` |
| `tool_call` | 工具调用（创建） | `tool_call` |
| `tool_call_update` | 工具调用状态更新 | `tool_call`（原位更新） |
| `plan` | 任务计划变更 | `plan` |
| `memory_reference` | 记忆召回 | `memory_reference` |
| `notification` | 各类通知 | `notification` |
| `current_mode_update` | 模式切换通知 | （不产生 Block） |

**session/prompt 响应**同样会产生 Block：

| 事件 | 含义 | 转换为的 Block 类型 |
|------|------|---------------------|
| `session/prompt` response | 本轮结束 | `request_end` |

### 3.3 事件元数据 (_meta)

每个 ACP 事件携带 `_meta` 字典，提供额外上下文信息。对分组有影响的关键 meta 字段：

| Meta Key | 含义 | 对分组的影响 |
|----------|------|-------------|
| `request-id` | 请求 ID（标识本轮对话） | 用于按轮次保护最后消息 |
| `tool-kind` | 工具类别（READ/EDIT/SEARCH/...） | 直接参与工具分类判断 |
| `tool-name` | 工具名称 | 参与工具分类判断（toolKind 的 fallback） |
| `file-change-mode` | 文件变更模式（Create/Modify/Delete） | Quest 模式下区分编辑操作类型 |
| `tool-call-internal-status` | 业务层工具状态 | 判断工具是否完成/失败 |
| `tool-call-error-code/message` | 工具错误信息 | 判断是否为错误工具（折叠进分组） |
| `streamed` | 消息是否流式传输 | 决定 message chunk 聚合策略 |
| `thinking-duration-millis` | 思考时长 | 传入 thought block 供分组时长计算 |
| `parent-tool-call-id` | 父工具调用 ID（SubAgent） | 关联子 Agent 数据到父 block |
| `sub-session-id` | 子会话 ID | SubAgent 分组使用 |

### 3.4 ACP 流状态机

会话的流状态（`ACPStreamState`）直接影响分组算法中的多个判断：

```
              ┌──────────┐
              │  Initial │ ← 空会话，等待用户首次输入
              └────┬─────┘
                   │ 用户发送消息
                   ▼
              ┌──────────┐
              │Prompting │ ← 消息已提交，等待 Agent 开始响应
              └────┬─────┘
                   │ 收到第一个 session/update
                   ▼
              ┌──────────┐
       ┌──────│Streaming │ ← Agent 流式响应中（持续收到 update）
       │      └────┬─────┘
       │           │ 收到权限请求
       ▼           │
 ┌──────────┐      │
 │Suspended │◄─────┘ ← 暂停，等待用户操作（权限确认等）
 └────┬─────┘
      │ 用户确认后继续
      └──────┬─────┘
             │ session/prompt 返回 stopReason
         ┌───┴────┬──────────┐
         ▼        ▼          ▼
   ┌──────────┐ ┌──────────┐ ┌───────┐
   │Completed │ │Cancelled │ │ Error │  ← 终止态
   └──────────┘ └──────────┘ └───────┘
         │                        │
         └────── 用户再次发送 ────┘
                    │
              ┌──────────┐
              │Prompting │ ← 新一轮开始
              └──────────┘
```

**流状态对分组的影响**：

| 状态 | 对分组的影响 |
|------|-------------|
| `Streaming` | 最后一个 group 可能 `isExploring=true`；当前轮次的最后消息不受保护 |
| `Suspended` | 同 Streaming（等待用户操作，Agent 还没结束） |
| `Prompting` | 同 Streaming（等待 Agent 开始，用户刚发了新消息） |
| `Completed` / `Cancelled` / `Error` | 所有 group 的 `isExploring=false`；所有轮次的最后消息受保护；`config.isCompleted=true` |
| `Initial` | 无 blocks，分组不执行 |

### 3.5 从 ACP 事件到 RenderBlock 的转换

`computeBlocks()` 是一个纯函数，负责将 `ACPChatProgress[]` 转换为 `ACPRenderBlock[]`。这是分组算法的直接上游。

**核心转换逻辑**：

```
function computeBlocks(progressList):
    blocks = []

    // 聚合缓冲区
    currentMessageChunks = []     // 流式消息片段缓冲
    currentThoughtChunks = []     // 思考片段缓冲
    userMessageMap = Map()         // 用户消息按 requestId 聚合

    for each progress in progressList:
        requestId = progress.meta['request-id']

        switch progress.update.sessionUpdate:

            case 'user_message_chunk':
                // 按 requestId 聚合（一条用户消息可能多个 chunk）
                userMessageMap.get(requestId).push(chunk)

            case 'agent_message_chunk':
                flushUserMessages()    // 先输出已聚合的用户消息
                flushThoughts()        // 先输出已聚合的思考
                if chunk.isStreamed:
                    currentMessageChunks.push(chunk)  // 流式聚合
                else:
                    flushMessages()    // 非流式：先输出之前的，再单独输出
                    blocks.push(MessageBlock(chunk))

            case 'agent_thought_chunk':
                flushUserMessages()
                flushMessages()        // 思考到来时先输出已聚合的消息
                currentThoughtChunks.push(chunk)
                // 记录 startTime、endTime、durationMillis

            case 'tool_call':
                flushAll()             // 工具调用到来时先输出所有缓冲
                existingBlock = blocks.findById(toolCallId)
                if existingBlock:
                    existingBlock.update(toolCall)  // 原位更新
                else:
                    blocks.push(ToolCallBlock(toolCall))

            case 'tool_call_update':
                flushAll()
                existingBlock = blocks.findById(toolCallId)
                existingBlock.updateResult(result)   // 原位更新状态和结果

            case 'plan':
                flushAll()
                blocks.push(PlanBlock(entries, isFirstUpdate))

            case 'session/prompt' (stopReason):
                flushAll()
                blocks.push(RequestEndBlock(requestId))

    return blocks
```

**关键设计**：

1. **流式聚合**：连续的 `agent_message_chunk`（且 `isStreamed=true`）聚合为一个 `MessageBlock`。连续的 `agent_thought_chunk` 聚合为一个 `ThoughtBlock`。遇到不同类型的 update 时先 flush 缓冲区。

2. **原位更新**：`tool_call` 和 `tool_call_update` 不会创建新 block，而是查找已有的 `ToolCallBlock` 并原位更新其 `result`、`status` 等字段。这保证了 blocks 数组中工具调用的位置不变。

3. **全量计算**：每次 progress 数组变化时，从头遍历全部 progress 重新计算 blocks（而非增量计算）。这保证了正确性，虽然看起来效率较低，但 progress 数组的长度通常可控。

4. **meta 提取**：每个 block 从 ACP 事件的 `_meta` 中提取 `toolKind`、`toolName`、`fileChangeMode` 等字段，存储在 block 上供后续分组策略使用。

### 3.6 SubAgent 数据流

SubAgent 的事件也通过 ACP 流推送，但带有 `parent-tool-call-id` meta 标记。数据流特殊处理：

```
收到 session/update
    │
    ├── 无 parent-tool-call-id → 主会话事件，正常处理
    │
    └── 有 parent-tool-call-id → SubAgent 事件
            │
            ├── 分离到 subAgentProgressMap
            │
            ├── 路由到 SubAgentService（独立计算 SubAgent 的 blocks）
            │
            └── SubAgent 的 blocks 关联到父级 ToolCallBlock.subAgentState
                    │
                    └── 分组时递归处理（§5.6）
```

### 3.7 全链路数据流总览

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ACP 事件流                                   │
│  WebSocket / IPC 接收 session/update 事件                           │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     状态机处理                                       │
│  ACPProgressStateMachine: 更新 ACPStreamState                       │
│  progress 追加到 session.acpStream.progress[]                       │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ progress[] 变化触发
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    块转换计算 (computeBlocks)                        │
│  1. 分离主会话 / SubAgent progress                                  │
│  2. 全量遍历 progress[]，聚合 + 转换为 ACPRenderBlock[]              │
│  3. SubAgent blocks 关联到父级 ToolCallBlock                        │
│  输出: ACPRenderBlock[]                                             │
└──────────────────────────┬──────────────────────────────────────────┘
                           │ blocks[] 变化触发
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    分组服务 (BlockGroupingService)                    │
│  1. 选择策略（按 sessionType + executionMode）                      │
│  2. 预处理：标记受保护的最后消息                                      │
│  3. 核心循环：逐块判断折叠，连续可折叠块合并为 ActivityGroup           │
│  4. 元数据计算：isExploring / toolsSummary / isThinkingOnly          │
│  5. SubAgent 递归：对嵌套子 Agent 的 blocks 递归执行分组              │
│  输出: GroupedRenderBlock[] (ActivityGroup | ACPRenderBlock)         │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       UI 渲染                                       │
│  ActivityGroup → ActivityGroupCard（折叠卡片）                       │
│  ACPRenderBlock → 正常 Block 渲染                                   │
└─────────────────────────────────────────────────────────────────────┘
```

**触发时机**：整个管线是**响应式**的——每当 ACP 事件到达，状态机更新 progress 数组和 streamState，这触发 `computeBlocks` 重新计算 blocks，进而触发 `useBlockGrouping` 重新分组，最终触发 UI 重渲染。在流式响应过程中，这个管线会高频执行（每收到一个 event 就走一遍）。

### 3.8 分组的重算策略

由于 `computeBlocks` 是全量计算，分组也随之全量重算：

- **每次 progress 变化**：重新从全部 progress 计算 blocks → 重新从全部 blocks 执行分组
- **blocks 引用变化**：React `useMemo` 的依赖项包含 blocks 数组引用，当 blocks 变化时重新分组
- **streamState 变化**：流状态变化也会触发重新分组（因为它影响 isCompleted、isExploring、保护策略）

**性能考量**：
- `computeBlocks` 是 O(n) 的纯函数，n 为 progress 数量
- 分组循环也是 O(n)，n 为 blocks 数量
- 对于典型对话（几十到几百个 blocks），这个开销可以忽略
- 如果需要优化，可以在 `computeBlocks` 层引入增量计算（但目前全量计算已足够）

### 3.9 权限请求与分组的交互

ACP 支持工具执行前的权限请求（human-in-the-loop）：

```
时间线:
  Agent 发起 tool_call (run_in_terminal)
       │
       ▼
  Client 收到 session/request-permission
       │
       ├── StreamState → Suspended
       │
       ├── toolCallId 加入 excludeToolCallIds
       │   → 该工具不被折叠，等待用户确认
       │
       ▼
  用户点击"允许" / "拒绝"
       │
       ├── toolCallId 从 excludeToolCallIds 移除
       ├── toolCallId 加入 confirmedToolCallIds
       │
       ├── StreamState → Streaming (Agent 继续)
       │
       └── 根据 foldAfterConfirmation 配置决定:
           ├── false: 已确认的工具保持独立展示
           └── true: 已确认的工具按正常规则判断是否折叠
```

---

## 4. 核心概念

### 3.1 Block 类型

对话流中的每个元素称为一个 Block。每个 Block 都有一个 `type` 字段和一个 `requestId` 字段（标识它属于哪次请求的响应）。

| 类型 | 含义 | 关键属性 |
|------|------|----------|
| **tool_call** | 工具调用 | toolName, toolKind, toolCallId, status, result, isSubAgent, fileChangeMode |
| **thought** | 模型思考过程 | chunks[], startTime, endTime, durationMillis |
| **message** | 文本消息 | chunks[]（每个 chunk 含 content） |
| **user_message** | 用户消息 | （用于划分对话轮次） |
| **plan** | 任务计划 | isFirstUpdate（是否首次创建） |
| **request_end** | 请求结束标记 | （不折叠） |
| **notification** | 通知消息 | （不折叠） |
| **memory_reference** | 记忆引用 | （不折叠） |

### 3.2 工具分类

将工具按"用户需要感知的程度"分为几类，这是折叠判断的基础。分类通过两种方式判断：按工具名（toolName）或按工具类别（toolKind）。

| 分类 | 特征 | 折叠倾向 | toolKind | 典型 toolName |
|------|------|----------|----------|---------------|
| **探索性工具** | 只读、信息收集，不影响文件系统 | 折叠 | READ, SEARCH, FETCH | read_file, list_dir, search_codebase, search_symbol, search_file, grep_code, fetch_rules, search_memory, get_problems |
| **编辑工具** | 修改文件系统，需要用户审查 | 不折叠 | EDIT, DELETE, MOVE | create_file, edit_file, search_replace, delete_file |
| **确认工具** | 需要用户交互或确认 | 不折叠 | ASK_USER_QUESTION, EXECUTE | run_in_terminal, ask_user, run_preview, switch_mode |
| **TODO 工具** | 任务管理操作 | 不折叠 | — | add_tasks, update_tasks, view_tasklist |
| **网页工具** | 网络搜索和抓取 | 视策略而定 | FETCH | search_web, fetch_content |
| **记忆工具** | 上下文记忆操作 | 视策略而定 | — | update_memory, search_memory |

**双重判断机制**：工具分类优先通过 `toolKind` 判断（更通用），fallback 到 `toolName` 判断（具体工具名）。这样当新增工具时，只要 toolKind 正确就能自动归入正确的分类，无需修改分组代码。

### 3.3 工具状态

每个 tool_call block 都有执行状态，这影响折叠判断：

| 状态 | 含义 | 判断方式 |
|------|------|----------|
| **pending** | 等待执行 / 执行中 | status='pending' 或无明确完成状态 |
| **completed** | 执行成功 | status='completed' 或 internalStatus=FINISHED |
| **error** | 执行失败 | status='failed' 或 internalStatus=ERROR/CANCELLED |

**关键设计**：错误的工具调用也会被折叠（而不是独立展示），因为失败的操作对用户来说是噪音，错误详情可以在展开分组后查看。

### 3.4 编辑工具的操作类型

编辑工具有一个额外的 `fileChangeMode` 属性，区分操作类型：

| fileChangeMode | 含义 | 在自主模式下的折叠行为 |
|----------------|------|------------------------|
| **MODIFY** | 修改已有文件 | 折叠（Agent 频繁修改同文件是常态） |
| 其他（新增/删除） | 创建或删除文件 | 不折叠（文件级别的变更用户需要感知） |

### 3.5 ActivityGroup（活动分组）

连续的可折叠 blocks 合并后的产物。它与原始 Block 共享同一个输出列表类型：

```
GroupedBlock = ActivityGroup | OriginalBlock
```

ActivityGroup 携带以下元数据：

| 字段 | 类型 | 含义 |
|------|------|------|
| `type` | `'activity-group'` | 标识这是一个分组 |
| `blocks` | Block[] | 分组内的原始 block 列表 |
| `id` | string | 分组唯一标识，格式：`{requestId}-activity-group-{startIndex}` |
| `requestId` | string | 所属请求 ID |
| `startIndex` / `endIndex` | number | 在原始列表中的位置范围 |
| `isExploring` | boolean | 分组是否正在进行中（详见 §5.5） |
| `isThinkingOnly` | boolean | 分组是否只包含思考块和短文本（详见 §7.2） |
| `isEditOnly` | boolean | 分组是否只包含同文件编辑（详见 §7.3） |
| `editFilePath` | string? | 当 isEditOnly 时，编辑的文件路径 |
| `toolsSummary` | ToolsSummary | 工具使用统计（详见 §7.1） |
| `activityOrder` | ActivityType[] | 活动类型出现顺序 |
| `hasErrorTool` | boolean | 分组内是否有执行失败的工具调用 |
| `thinkingDurationSeconds` | number? | 思考总时长（秒）（详见 §7.4） |
| `sessionType` | enum | 所属会话类型（影响标签文案） |

### 3.6 ActivityType（活动类型）

将 block 映射到活动类型，用于 toolsSummary 统计和 activityOrder 记录：

| ActivityType | 对应 block/tool |
|-------------|-----------------|
| `thinking` | thought 块 |
| `text` | message 块（短文本） |
| `file` | toolKind=READ 或 read_file |
| `list` | list_dir |
| `search` | toolKind=SEARCH 或各种 search_* |
| `web_search` | toolKind=FETCH 或 search_web, fetch_content |
| `edit` | toolKind=EDIT/DELETE/MOVE 或 create_file, edit_file 等 |
| `terminal` | toolKind=EXECUTE 或 run_in_terminal |
| `todo` | update_tasks |
| `memory` | search_memory, update_memory |
| `problems` | get_problems |

映射优先级：**先查 toolKind，再查 toolName**。toolKind 是更抽象的分类，toolName 是具体工具名。

---

## 4. 策略模式设计

### 4.1 策略接口

每种折叠策略需要实现四个核心方法：

```
interface IGroupingStrategy {
    name: string                       // 策略名称
    supportedSessionTypes: enum[]      // 支持的会话类型列表

    // 判断单个 block 是否应该被折叠
    shouldFoldBlock(block, context, isInGroup, previousBlocks) → FoldDecision

    // 判断当前分组是否应该在此处结束（在 shouldFold=true 时才调用）
    shouldEndGroup(currentGroup, nextBlock, context) → boolean

    // 构建分组的工具使用统计
    buildToolsSummary(blocks, context) → ToolsSummary

    // 获取 block 的活动类型分类
    getActivityType(block) → ActivityType | null
}
```

**FoldDecision**：

```
{
    shouldFold: boolean       // 是否应该折叠
    reason: string            // 原因（仅调试用，如 'exploration_tool', 'short_text'）
    activityType: string?     // 如果折叠，它的活动类型是什么
}
```

**GroupingContext**（分组上下文，传递给策略方法）：

```
{
    sessionId: string                 // 会话 ID
    sessionType: enum                 // 会话类型
    streamState: enum                 // 流状态（Streaming/Completed/Cancelled/Error/...）
    config: GroupingConfig            // 分组配置
    executionMode: 'vibe' | 'execute' // 执行模式（仅自主模式有效）
    subAgentContext: {                // 仅在 SubAgent 内部分组时存在
        isSubAgent: true
        subAgentType: string          // SubAgent 类型
        parentSessionType: enum
        parentToolCallId: string
        nestLevel: number             // 嵌套层级
    }?
}
```

### 4.2 策略继承体系

```
IGroupingStrategy (接口)
       │
Base (基础策略，提供完整默认实现)
       │
       ├── Assistant (对话模式)
       │     覆盖: shouldFoldToolCall, shouldFoldThought
       │     差异: 更积极折叠网页/记忆/终端输出工具
       │
       └── Quest (自主任务模式)
             覆盖: shouldFoldBlock, shouldFoldToolCall, shouldFoldThought, shouldEndGroup
             差异: 编辑工具区分 modify/新增、同文件编辑聚合
                   │
                   └── Vibe (快速执行模式)
                         覆盖: shouldFoldToolCall, shouldEndGroup
                         差异: 除 SubAgent 外几乎全部折叠，不区分文件
```

每个子策略只需要覆盖有差异的方法，其余行为继承自父类。

### 4.3 策略选择算法

```
function selectStrategy(sessionType, executionMode):
    if sessionType == QUEST and executionMode == 'vibe':
        return strategyRegistry.getByName('vibe')   // 按名称查找，不按 sessionType
    return strategyRegistry.getBySessionType(sessionType)
    ?? defaultStrategy  // fallback 到 Base
```

**设计细节**：Vibe 策略不注册 `supportedSessionTypes`（设为空数组），只通过名称查找。这样它不会覆盖 Quest 策略在 sessionType 注册表中的位置，两者可以共存。

### 4.4 策略注册

服务启动时注册所有内置策略：

```
strategyMap:   Map<SessionType, Strategy>  // 按 sessionType 索引
strategyNames: Map<string, Strategy>       // 按 name 索引

register(strategy):
    for each sessionType in strategy.supportedSessionTypes:
        strategyMap.set(sessionType, strategy)
    strategyNames.set(strategy.name, strategy)
```

---

## 5. 分组算法详解

### 5.1 总体流程

```
function groupBlocks(blocks, sessionId, sessionType, streamState, config, executionMode):
    // 1. 合并配置
    mergedConfig = { ...DEFAULT_CONFIG, ...config }
    mergedConfig.isCompleted = isTerminalState(streamState)  // Completed/Cancelled/Error

    // 2. 创建上下文
    context = { sessionId, sessionType, streamState, config: mergedConfig, executionMode }

    // 3. 选择策略
    strategy = selectStrategy(sessionType, executionMode)

    // 4. 执行核心分组循环
    groupedBlocks = executeGrouping(blocks, strategy, context)

    // 5. 递归处理 SubAgent
    return processSubAgentBlocks(groupedBlocks, ...)
```

### 5.2 核心分组循环（executeGrouping）

这是整个算法的核心，是一次 O(n) 的线性扫描：

```
function executeGrouping(blocks, strategy, context):
    result = []
    currentGroup = []         // 当前正在累积的分组
    groupStartIndex = 0       // 当前分组的起始索引
    groupRequestId = ''       // 当前分组的请求 ID
    isFlushingFinalGroup = false  // 标记是否正在 flush 最后一个分组

    // 预处理：找出受保护的"最后消息"索引
    protectedIndices = findProtectedMessageIndices(blocks, context)

    // 定义 flush 函数
    function flushGroup():
        if currentGroup is empty: return

        if currentGroup.length == 1:
            // 单个块：再次判断是否真的该折叠
            singleBlock = currentGroup[0]
            decision = strategy.shouldFoldBlock(singleBlock, context, isInGroup=false, previousBlocks=[])
            if decision.shouldFold:
                group = createActivityGroup(currentGroup, ..., isFlushingFinalGroup)
                result.push(group)
            else:
                result.push(singleBlock)  // 不折叠，原样输出
        else:
            // 多个块：创建分组
            group = createActivityGroup(currentGroup, ..., isFlushingFinalGroup)
            result.push(group)

        currentGroup = []

    // 主循环
    for i = 0 to blocks.length - 1:
        block = blocks[i]
        isInGroup = currentGroup.length > 0

        // 受保护的最后消息：强制不折叠
        if block.type == 'message' AND protectedIndices.has(block.requestId, i):
            isFlushingFinalGroup = false
            flushGroup()
            result.push(block)
            continue

        // 调用策略判断
        decision = strategy.shouldFoldBlock(block, context, isInGroup, blocks[0..i-1])

        if decision.shouldFold:
            // 在加入分组之前，检查是否需要先结束当前分组
            if isInGroup AND strategy.shouldEndGroup(currentGroup, block, context):
                isFlushingFinalGroup = false
                flushGroup()

            // 加入分组
            if currentGroup is empty:
                groupStartIndex = i
                groupRequestId = block.requestId
            currentGroup.push(block)
        else:
            // 不折叠：先 flush 当前分组，再输出原始块
            isFlushingFinalGroup = false
            flushGroup()
            result.push(block)

    // 处理最后的分组
    isFlushingFinalGroup = true  // 标记这是最后一个 flush
    flushGroup()

    return result
```

**关键细节**：

1. **`isFlushingFinalGroup` 标记**：这个标记传递给 `createActivityGroup`，用于计算 `isExploring`。只有最后一个 group 才可能处于"进行中"状态。在循环中间的每次 flush，这个标记都是 false；只有在循环结束后的最后一次 flush，才设为 true。

2. **单个块的二次判断**：当 currentGroup 只有 1 个块时，flush 会再次调用 `shouldFoldBlock`（此时 `isInGroup=false`）。这是因为有些块（如 message）只在已有分组时才折叠（`isInGroup=true` 时折叠短文本），单独存在时不该折叠。

3. **shouldEndGroup 只在 shouldFold=true 时调用**：如果当前块不该折叠，直接 flush 即可，不需要额外判断分组边界。shouldEndGroup 只在"这个块该折叠，但需要检查是否放入当前组还是开始新组"时才调用。

### 5.3 "最后消息"保护策略

**设计目标**：保证每轮对话中 Agent 的最后一段文本回复始终可见，不被折叠。

**为什么需要这个**：Agent 的回复模式通常是：思考 → 工具调用 → 思考 → 工具调用 → ... → 最终文本回复。中间的短文本消息（如"我来看看这个文件..."）可能被折叠，但最终的回复不应该被折叠。

**算法**：

```
function findProtectedMessageIndices(blocks, context):
    protectedIndices = Map<requestId, index>

    // 1. 找出所有 user_message 的位置，划分对话轮次
    userMessagePositions = [i for i in 0..blocks.length where blocks[i].type == 'user_message']

    // 2. 遍历每个对话轮次
    for roundIndex = 0 to userMessagePositions.length - 1:
        roundStart = userMessagePositions[roundIndex]
        roundEnd = (roundIndex < last) ? userMessagePositions[roundIndex + 1] - 1 : blocks.length - 1

        // 判断是否需要保护这一轮
        isLastRound = (roundIndex == userMessagePositions.length - 1)
        isSessionInProgress = streamState in [Streaming, Prompting, Suspended]

        shouldProtect = context.isCompleted                          // 会话已结束 → 保护所有
                     OR (isSessionInProgress AND NOT isLastRound)    // 进行中 → 只保护非最后轮

        if shouldProtect:
            // 从后往前找该轮次的最后一个 message block
            for i = roundEnd downto roundStart:
                if blocks[i].type == 'message' AND blocks[i].requestId exists:
                    protectedIndices.set(blocks[i].requestId, i)
                    break

    return protectedIndices
```

**为什么进行中时不保护最后一轮**：

考虑流式场景。Agent 边执行边输出，此时最后一轮还在进行中。如果保护当前轮的"最后 message"，那么每当新的 message 块到达时，之前的"最后 message"就不再是最后的了——但它已经被保护显示出来。这会导致 UI 频繁跳动（一个 message 先被独立展示，后来又被折叠进分组）。

所以**只保护已完成轮次**的最后消息，当前轮的消息允许被折叠（因为后续可能还有新消息）。当会话结束时，再保护所有轮次。

### 5.4 flush 与 ActivityGroup 创建

当 `currentGroup` 被 flush 时，需要创建 ActivityGroup 并计算各种元数据：

```
function createActivityGroup(blocks, startIndex, requestId, strategy, context, isLastGroup):
    // 1. 收集活动类型顺序
    activityOrder = []
    for block in blocks:
        type = strategy.getActivityType(block)
        if type != null: activityOrder.push(type)

    // 2. 构建工具汇总
    toolsSummary = strategy.buildToolsSummary(blocks, context)

    // 3. 计算 thinking 持续时间
    thinkingDurationSeconds = calculateThinkingDuration(blocks)

    // 4. 判断 isExploring
    hasRunningTool = any block in blocks where:
        block.type == 'tool_call'
        AND NOT block.isCompleted
        AND NOT block.isError
    isExploring = isLastGroup
              AND NOT context.config.isCompleted
              AND (streamState == Streaming OR hasRunningTool)

    // 5. 判断 isThinkingOnly
    hasThinking = any block.type == 'thought'
    allThinkingOrMessage = every block.type in ['thought', 'message']
    isThinkingOnly = blocks.length > 0 AND hasThinking AND allThinkingOrMessage

    // 6. 判断 isEditOnly
    (isEditOnly, editFilePath) = checkEditOnlyGroup(blocks)

    // 7. 检查是否有错误工具
    hasErrorTool = any block in blocks where:
        block.type == 'tool_call'
        AND NOT block.isSubAgent
        AND NOT block.toolKind in [SUB_AGENT, SWITCH_MODE]
        AND block.isError

    return ActivityGroup { ... }
```

### 5.5 isExploring 判断逻辑详解

这是一个关键状态，直接影响卡片的 UI 表现（是否显示"进行中"动画、是否自动展开）。

**三个条件必须同时满足**：

1. **是最后一个 group**：通过 `isLastGroup` 参数判断。在循环中间 flush 的 group 的 `isLastGroup=false`，只有循环结束后 flush 的最后一个 group 为 `true`。
   - **为什么**：如果后面还有其他 blocks（不管是原始 block 还是另一个 group），说明这个 group 已经结束了。只有"位于末尾的 group"才可能还在进行中。

2. **会话未结束**：`isCompleted` 为 false，即 streamState 不是 Completed/Cancelled/Error。
   - **为什么**：会话已结束意味着所有操作都完成了。

3. **组内有至少一个工具仍在执行中**：遍历组内所有 tool_call block，如果有任何一个既不是 completed 也不是 error，则认为还在执行。
   - **为什么**：即使是最后一个 group 且会话在 streaming，如果组内所有工具都已完成，那这个 group 也是"已完成"的。
   - **注意**：thought 块不影响 running 状态判断，因为 thinking 没有独立的完成状态信号。

**isExploring 的状态转换**：

```
                    ┌─────────────┐
                    │ isExploring │
                    │   = true    │
                    └──────┬──────┘
                           │ 以下任一条件触发：
                           │ - 新的不可折叠 block 到达（group 不再是最后一个）
                           │ - 会话结束
                           │ - 组内所有工具执行完毕
                           ▼
                    ┌─────────────┐
                    │ isExploring │
                    │   = false   │
                    └─────────────┘
```

### 5.6 SubAgent 递归处理

Agent 可以调用子 Agent (SubAgent)，子 Agent 内部也有自己的 block 列表。分组算法需要递归处理。

**递归算法**：

```
function processSubAgentBlocks(groupedBlocks, sessionType, config, executionMode, nestLevel=0):
    return groupedBlocks.map(block =>
        if block.type == 'tool_call' AND block.isSubAgent AND block.subAgentState:
            // 获取子 Agent 类型
            agentType = block.subAgentState.agentType ?? 'general_purpose'

            // 查询是否有定制配置
            configOverride = subAgentConfigRegistry.get(agentType, sessionType, nestLevel)

            // 确定策略
            if configOverride.useCustomStrategy:
                strategy = strategyRegistry.getByName(configOverride.customStrategyName)
            else:
                strategy = strategyRegistry.getBySessionType(sessionType)  // 默认用父级策略

            // 合并配置
            subConfig = { ...config, ...configOverride.configOverrides }
            subConfig.isCompleted = isTerminalState(block.subAgentState.streamState)

            // 创建子上下文
            subContext = {
                ...context,
                sessionId: block.subAgentState.subSessionId,
                subAgentContext: {
                    isSubAgent: true,
                    subAgentType: agentType,
                    parentSessionType: sessionType,
                    parentToolCallId: block.subAgentState.parentToolCallId,
                    nestLevel: nestLevel,
                }
            }

            // 执行分组
            subGroupedBlocks = executeGrouping(block.subAgentState.blocks, strategy, subContext)

            // 递归处理嵌套的 SubAgent（nestLevel + 1）
            subGroupedBlocks = processSubAgentBlocks(subGroupedBlocks, ..., nestLevel + 1)

            // 返回更新后的 block
            return { ...block, subAgentState: { ...block.subAgentState, groupedBlocks: subGroupedBlocks } }
        else:
            return block
    )
```

**设计点**：

- SubAgent 的 block 列表存储在 `tool_call.subAgentState.blocks` 中
- 分组后的结果写入 `subAgentState.groupedBlocks`（不修改原始 blocks）
- 默认使用父级策略，但支持通过 `ISubAgentGroupingConfigProvider` 注册定制
- `nestLevel` 追踪嵌套层级，传递给策略供参考

---

## 6. 策略折叠规则详解

### 6.1 shouldFoldBlock 的判断流程

所有策略共享一个 **通用前置检查**（在 Base 策略中实现，子类通常不覆盖这部分）：

```
function shouldFoldBlock(block, context, isInGroup, previousBlocks):
    // ===== 通用前置检查（仅对 tool_call 类型）=====
    if block.type == 'tool_call':
        toolCallId = block.toolCallId

        // 优先级 1：待用户确认 → 不折叠
        if toolCallId in config.excludeToolCallIds:
            return { shouldFold: false, reason: 'pending_confirmation' }

        // 优先级 2：模式切换工具 → 不折叠
        if block.toolKind in [SWITCH_MODE, SWITCH_MODE_ENTER]:
            return { shouldFold: false, reason: 'switch_mode' }

        // 优先级 3：SubAgent 工具 → 不折叠
        if block.isSubAgent OR block.toolKind == SUB_AGENT OR block.toolName == 'task':
            return { shouldFold: false, reason: 'subagent_tool' }

        // 优先级 4：执行出错 → 折叠（错误信息留在分组内）
        if block.isError:
            return { shouldFold: true, reason: 'tool_error', activityType: ... }

        // 优先级 5：已确认的工具 → 根据配置决定
        if toolCallId in config.confirmedToolCallIds:
            if NOT config.foldAfterConfirmation:
                return { shouldFold: false, reason: 'confirmed_no_fold' }
            // else: 继续下面的分类判断

    // ===== 按块类型分发 =====
    switch block.type:
        case 'tool_call':   return shouldFoldToolCall(block, context, isInGroup)
        case 'thought':     return shouldFoldThought(block, context, isInGroup)
        case 'message':     return shouldFoldMessage(block, context, isInGroup)
        case 'user_message': return { shouldFold: false }
        case 'plan':        return handlePlanBlock(block, context)
        case 'request_end': return { shouldFold: false }
        default:            return { shouldFold: false }
```

### 6.2 Base 策略的 shouldFoldToolCall

```
function shouldFoldToolCall(block, context, isInGroup):
    if NOT config.groupToolCalls: return { shouldFold: false }

    toolInfo = extractToolInfo(block)

    // 编辑工具 → 不折叠
    if isEditingTool(toolInfo): return { shouldFold: false }

    // 确认工具 → 不折叠
    if isConfirmationTool(toolInfo): return { shouldFold: false }

    // TODO 工具 → 不折叠
    if isTodoTool(toolInfo): return { shouldFold: false }

    // 探索性工具 → 折叠
    if isExplorationTool(toolInfo):
        return { shouldFold: true, activityType: mapToActivityType(toolInfo) }

    // 网页工具 → 根据配置
    if isWebTool(toolInfo):
        return { shouldFold: config.groupSearchTools, activityType: 'web_search' }

    // 其他 → 不折叠（保守策略）
    return { shouldFold: false }
```

### 6.3 Base 策略的 shouldFoldThought

```
function shouldFoldThought(block, context, isInGroup):
    if NOT config.groupThinking: return { shouldFold: false }
    return { shouldFold: true, activityType: 'thinking' }
```

**注意**：thinking 块可以**开启**一个新的分组（即使当前不在分组中）。但如果分组里只有 thinking（和短文本），UI 上会特殊处理（显示"思考了 Ns"而非"探索中"）。

### 6.4 Base 策略的 shouldFoldMessage

```
function shouldFoldMessage(block, context, isInGroup):
    if NOT config.groupText: return { shouldFold: false }

    // 关键：message 块不能开启分组，只能加入已有分组
    if NOT isInGroup: return { shouldFold: false }

    text = extractMessageText(block)
    if isShortText(text):
        return { shouldFold: true, activityType: 'text' }
    return { shouldFold: false }
```

**短文本判断算法**：

```
function isShortText(text, maxLength=150, maxLines=2):
    if text is null/undefined: return false
    if text == '': return true

    // 包含代码块 → 不是短文本（代码块展开后可能很长）
    if text contains '```' or '`': return false

    return text.length <= maxLength AND text.split('\n').length <= maxLines
```

**设计意图**：Agent 在工具调用间隙可能输出"让我看看这个文件..."之类的短消息。如果这段消息出现在一个正在累积的分组中间，就把它一起折叠进去。但如果它不在分组中（前面没有被折叠的块），就独立展示。

### 6.5 Base 策略的 shouldEndGroup

```
function shouldEndGroup(currentGroup, nextBlock, context):
    if nextBlock is null: return true

    // 如果下一个块（在加入分组的语境下）不应该折叠，就结束
    decision = shouldFoldBlock(nextBlock, context, isInGroup=true, currentGroup)
    return NOT decision.shouldFold
```

这是最简单的分组边界规则：连续的可折叠块就一直累积，遇到不可折叠块就断开。

### 6.6 Assistant 策略的覆盖

只覆盖 `shouldFoldToolCall` 和 `shouldFoldThought`：

```
// shouldFoldThought: 无条件折叠（不受 config.groupThinking 控制）
function shouldFoldThought(block, context, isInGroup):
    return { shouldFold: true, activityType: 'thinking' }

// shouldFoldToolCall: 在 Base 基础上增加几种折叠
function shouldFoldToolCall(block, context, isInGroup):
    ... // Base 的所有判断保持不变，额外增加：

    // 记忆搜索 → 折叠（记忆更新不折叠，因为它代表一个可见的操作）
    if toolInfo.toolName == 'search_memory':
        return { shouldFold: true, activityType: 'memory' }

    // 终端输出读取 → 折叠（这只是读取之前命令的输出）
    if toolInfo.toolName == 'get_terminal_output':
        return { shouldFold: true, activityType: 'terminal' }

    // 网页工具 → 无条件折叠（Base 是根据配置决定）
    if isWebTool(toolInfo):
        return { shouldFold: true, activityType: 'web_search' }
```

### 6.7 Quest 策略的覆盖

Quest 覆盖了 `shouldFoldBlock`、`shouldFoldToolCall`、`shouldFoldThought` 和 `shouldEndGroup`。

**shouldFoldBlock 覆盖**：增加 plan 块的处理

```
function shouldFoldBlock(block, context, isInGroup, previousBlocks):
    // 待确认的工具检查（与 Base 相同）
    if block.type == 'tool_call' AND block.toolCallId in excludeList:
        return { shouldFold: false }

    // tool_call 直接调用 Quest 的 shouldFoldToolCall（带 previousBlocks）
    if block.type == 'tool_call':
        return shouldFoldToolCall(block, context, isInGroup, previousBlocks)

    // plan 块特殊处理
    if block.type == 'plan':
        if block.isFirstUpdate:
            return { shouldFold: false }          // 首次创建 → 展示
        return { shouldFold: true, activityType: 'todo' }  // 后续更新 → 折叠

    // 其他类型回退到 Base
    return super.shouldFoldBlock(...)
```

**shouldFoldToolCall 覆盖**：编辑工具的 modify 区分

```
function shouldFoldToolCall(block, context, isInGroup, previousBlocks):
    if NOT config.groupToolCalls: return { shouldFold: false }
    if isSubAgent(block): return { shouldFold: false }

    toolInfo = extractToolInfo(block)

    if toolInfo.toolKind in [SWITCH_MODE, SWITCH_MODE_ENTER]:
        return { shouldFold: false }

    // 错误 → 折叠
    if toolInfo.isError:
        return { shouldFold: true, activityType: ... }

    // 编辑工具 → 区分 modify 和 新增/删除
    if isEditingTool(toolInfo) OR isEditingKind(toolInfo):
        if NOT toolInfo.isModify:
            return { shouldFold: false }       // 新增/删除 → 不折叠
        else:
            return { shouldFold: true, activityType: 'edit' }  // modify → 折叠

    // 确认工具 → 不折叠
    if isConfirmationTool(toolInfo): return { shouldFold: false }

    // 探索性工具 → 折叠（同时支持 toolName 和 toolKind 判断）
    if isExplorationTool(toolInfo) OR isExplorationKind(toolInfo):
        return { shouldFold: true, activityType: ... }

    // 网页工具 → 仅 FETCH 类折叠（搜索结果页面不折叠）
    if toolInfo.toolKind == FETCH:
        return { shouldFold: true, activityType: 'web_search' }

    // 记忆工具 → 折叠（包括 update_memory，与 Assistant 不同）
    if toolInfo.toolName in ['search_memory', 'update_memory']:
        return { shouldFold: true, activityType: 'memory' }

    return { shouldFold: false }
```

**shouldEndGroup 覆盖**：同文件编辑聚合（Quest 最复杂的部分）

```
function shouldEndGroup(currentGroup, nextBlock, context):
    if nextBlock is null: return true

    // SubAgent 到来 → 结束（SubAgent 需要独立展示为任务卡片）
    if nextBlock.type == 'tool_call' AND isSubAgent(nextBlock): return true

    // 首次创建的 plan → 结束（计划需要独立展示）
    if nextBlock.type == 'plan' AND nextBlock.isFirstUpdate: return true

    // ===== 编辑聚合逻辑 =====
    hasEditInGroup = 当前组内是否有可聚合的编辑工具（isModify 或 isError 的编辑）
    editFilePath = 组内第一个能提取到路径的编辑工具的文件路径

    if nextBlock.type == 'tool_call':
        nextToolInfo = extractToolInfo(nextBlock)
        isNextEditing = isEditingTool(next) OR isEditingKind(next)
        isNextAggregable = isNextEditing AND (nextToolInfo.isModify OR nextToolInfo.isError)

        // 新增/删除文件 → 结束（不参与聚合，需要独立展示）
        if isNextEditing AND NOT nextToolInfo.isModify AND NOT nextToolInfo.isError:
            return true

        // 当前组无编辑 + 下一个是可聚合编辑 → 结束
        //（不让编辑混进前面的探索工具组，让编辑开始自己的组）
        if NOT hasEditInGroup AND isNextAggregable:
            return true

        // 当前组有编辑
        if hasEditInGroup:
            // 下一个不是可聚合编辑 → 结束（编辑组不混入非编辑块）
            if NOT isNextAggregable: return true

            // 下一个是可聚合编辑，检查文件路径
            if editFilePath is not null:
                nextFilePath = extractFilePath(nextBlock)
                if nextFilePath is not null AND editFilePath != nextFilePath:
                    return true  // 不同文件 → 结束

            // 同文件 或 路径无法提取（如失败的编辑）→ 继续聚合
            return false

    // 当前组有编辑 + 下一个不是 tool_call → 结束
    if hasEditInGroup: return true

    // 当前组无编辑 → 使用默认规则
    return super.shouldEndGroup(currentGroup, nextBlock, context)
```

**视觉化示例**（Quest 模式）：

```
输入序列:
  [read_file A] [search X] [edit_file A (modify)] [edit_file A (modify)] [edit_file B (modify)] [message]

分组结果:
  Group 1: [read_file A, search X]           → "已探索 · 1文件 1搜索"
  Group 2: [edit_file A, edit_file A]         → "已执行 · 2编辑" (同文件A聚合)
  Group 3: [edit_file B]                      → 平铺展示 (不同文件B独立)
  原始:    [message]                           → 正常渲染

输入序列（含新增文件）:
  [read_file] [edit_file A (modify)] [create_file B] [edit_file B (modify)]

分组结果:
  Group 1: [read_file]                        → "已探索 · 1文件"
  Group 2: [edit_file A]                      → 平铺展示 (单个 modify)
  原始:    [create_file B]                     → 正常渲染 (新增不折叠)
  Group 3: [edit_file B]                      → 平铺展示 (后续 modify)
```

### 6.8 Vibe 策略的覆盖

继承自 Quest，进一步激进化：

**shouldFoldToolCall**：

```
function shouldFoldToolCall(block, context, isInGroup):
    if NOT config.groupToolCalls: return { shouldFold: false }

    // SubAgent → 不折叠（这是唯一不折叠的）
    if isSubAgent(block): return { shouldFold: false }

    toolInfo = extractToolInfo(block)

    // 确认工具中的特殊处理
    if isConfirmationTool(toolInfo):
        // 终端工具 → 折叠（Vibe 下终端也折叠）
        if isTerminalTool(block): return { shouldFold: true }
        // 模式切换 → 不折叠
        if toolKind in [SWITCH_MODE, SWITCH_MODE_ENTER]: return { shouldFold: false }
        // 其他确认工具 → 不折叠
        return { shouldFold: false }

    // 其他所有工具 → 折叠
    return { shouldFold: true, activityType: mapToActivityType(toolInfo) ?? 'edit' }
```

**shouldEndGroup**：

```
function shouldEndGroup(currentGroup, nextBlock, context):
    if nextBlock is null: return true

    // SubAgent → 结束
    if isSubAgent(nextBlock): return true

    // 首次 plan → 结束
    if nextBlock.type == 'plan' AND nextBlock.isFirstUpdate: return true

    // tool_call → 不结束！（所有连续工具都在同一组，不管什么工具、什么文件）
    if nextBlock.type == 'tool_call': return false

    // 非 tool_call → 用 Base 的逻辑（跳过 Quest 的同文件检查）
    decision = shouldFoldBlock(nextBlock, context, isInGroup=true, currentGroup)
    return NOT decision.shouldFold
```

**设计理念**：Vibe 模式的用户选择了"快速执行"，意味着高度信任 Agent。所有中间过程（包括编辑、终端执行）都折叠，只展示 SubAgent 卡片和最终回复。

---

## 7. 分组元数据计算

### 7.1 ToolsSummary（工具汇总统计）

```
function buildToolsSummary(blocks, context):
    summary = { files: 0, directories: 0, searches: 0, todos: 0,
                terminalCommands: 0, edits: 0, toolCalls: 0,
                problems: 0, hasProblemTool: false, hasListDirTool: false }

    filePathSet = Set()  // 文件去重用

    for block in blocks where block.type == 'tool_call':
        activityType = getActivityTypeForTool(block)

        switch activityType:
            case 'file':
                filePath = extractFilePath(block)
                if filePath: filePathSet.add(filePath)
                else: summary.files++          // 无法提取路径时退化为计数
            case 'list':
                summary.hasListDirTool = true
                summary.directories++
            case 'search', 'web_search':
                summary.searches++
            case 'terminal':
                summary.terminalCommands++
            case 'todo':
                if block.toolName == 'update_tasks':  // 只统计更新，不统计查看
                    summary.todos++
            case 'edit':
                if context.sessionType == QUEST:
                    summary.edits++            // 只有自主模式统计编辑数
                else:
                    summary.toolCalls++        // 其他模式归入兜底（因为编辑在这些模式下不折叠，走到这里说明是错误的编辑）
            case 'problems':
                summary.hasProblemTool = true
                summary.problems += extractResultArrayLength(block)  // 按结果数组长度统计
            default:
                summary.toolCalls++            // 兜底

    summary.files += filePathSet.size          // 加上去重后的文件数
    return summary
```

**文件路径提取的优先级**：
1. `result.locations[0].path` — 结构化位置信息
2. `result.content` 中 `type='diff'` 的 `path` — diff 内容中的路径
3. `rawInput.file_path / filePath / path / file` — 原始输入参数

### 7.2 isThinkingOnly 判断

```
isThinkingOnly = blocks.length > 0
              AND blocks 中至少有一个 thought 块
              AND blocks 中所有块的 type 都是 'thought' 或 'message'
```

**用途**：
- 影响卡片标签：isThinkingOnly 的组显示"思考了 Ns"而非"已探索"
- 影响卡片摘要：isThinkingOnly 的组不显示摘要（标题已经包含了时长信息）
- 影响特殊渲染：isThinkingOnly 且 blocks <= 2 时平铺展示（不包装成卡片）

### 7.3 isEditOnly 判断

```
function checkEditOnlyGroup(blocks):
    editFilePath = null
    hasNonEdit = false

    for block in blocks:
        if block.type == 'tool_call':
            toolInfo = extractToolInfo(block)
            if isEditingTool(toolInfo) OR isEditingKind(toolInfo):
                filePath = extractFilePath(block)
                if editFilePath is null:
                    editFilePath = filePath
                else if editFilePath != filePath:
                    return { isEditOnly: false }  // 不同文件
                continue

        // 遇到非编辑块（包括 thought、message 等）
        hasNonEdit = true
        break

    isEditOnly = editFilePath is not null AND NOT hasNonEdit
    return { isEditOnly, editFilePath: isEditOnly ? editFilePath : null }
```

**用途**：在自主模式下，编辑组可以有特殊的标签和摘要（如显示文件名）。

### 7.4 思考时长计算

```
function calculateThinkingDuration(blocks):
    totalSeconds = null

    for block in blocks where block.type == 'thought':
        millis = block.durationMillis
              ?? (block.endTime - block.startTime)  // fallback
              ?? null

        if millis is not null:
            // 关键：先单独取整，再累加
            ceiledSeconds = ceil(millis / 1000)
            totalSeconds = (totalSeconds ?? 0) + ceiledSeconds

    return totalSeconds
```

**为什么先取整再累加**：

考虑两个思考块，分别是 1.2s 和 1.3s：
- 方案 A（先加后整）：1200 + 1300 = 2500ms → ceil(2.5) = **3s**
- 方案 B（先整后加）：ceil(1.2) + ceil(1.3) = 2 + 2 = **4s**

方案 B 与单个块独立展示时的时长一致（单独显示时分别是 2s 和 2s），聚合后也是 4s。选择方案 B 保证一致性。

### 7.5 hasErrorTool 检查

```
function checkHasErrorTool(blocks):
    for block in blocks where block.type == 'tool_call':
        // 排除 SubAgent 和模式切换（它们的错误有独立的展示方式）
        if block.isSubAgent: continue
        if block.toolKind in [SUB_AGENT, SWITCH_MODE, SWITCH_MODE_ENTER]: continue

        if block.isError: return true
    return false
```

**用途**：当 `hasErrorTool=true` 且分组只有一个块时，决定是否仍然渲染为卡片形式。

---

## 8. UI 交互设计

### 8.1 折叠卡片状态机

每个 ActivityGroupCard 的展开/收起状态受三个因素控制：

```
                     ┌───────────────────────────────┐
                     │     ActivityGroupCard 状态     │
                     │                               │
                     │  isExpanded: boolean           │
                     │  hasManuallyToggled: boolean   │
                     │  prevIsExploring: boolean      │
                     └───────────────────────────────┘

初始化:
  isExpanded = (autoExpandWhileExploring AND group.isExploring) OR defaultExpanded
  hasManuallyToggled = false
  prevIsExploring = group.isExploring

当 group.isExploring 变化时 (自动逻辑):
  if hasManuallyToggled: return  // 用户手动操作过 → 不再自动控制

  if prevIsExploring == true AND group.isExploring == false:
      // "进行中" → "已完成"
      if autoCollapseOnComplete: isExpanded = false

  if prevIsExploring == false AND group.isExploring == true:
      // "已完成" → "进行中"（新的 group 开始流入）
      if autoExpandWhileExploring: isExpanded = true

  prevIsExploring = group.isExploring

当用户点击时 (手动逻辑):
  hasManuallyToggled = true  // 标记为手动操作，永久禁用自动逻辑
  isExpanded = NOT isExpanded
```

**设计关键**：`hasManuallyToggled` 是**单向标记**（一旦设为 true 就不会变回 false）。这保证了用户的手动操作永远优先于系统的自动行为。即使后续 isExploring 再次变化，也不会干扰用户手动设定的状态。

### 8.2 特殊渲染规则（何时不渲染为卡片）

尽管数据层已经创建了 ActivityGroup，但 UI 层不一定要将它渲染为折叠卡片。某些情况下"平铺"更好：

```
function renderActivityGroup(group, config):
    // 规则 1：纯思考组且内容少 → 平铺
    if group.isThinkingOnly AND group.blocks.length <= 2:
        return renderTileBlocks(group.blocks)

    // 规则 2：单个工具且无错误 → 平铺
    if group.blocks.length == 1:
        if NOT group.hasErrorTool:
            return renderTileBlocks(group.blocks)
        else if config.foldSingleTool:
            return renderTileBlocks(group.blocks)

    // 规则 3：标准折叠卡片
    return renderFoldableCard(group)
```

**规则 1 的原因**：1-2 个思考块单独包装成卡片视觉效果不好（太轻量了不值得额外包装），直接平铺展示更自然。3 个以上思考块才值得折叠。

**规则 2 的原因**：单个工具调用不需要"打开卡片才能看到内容"的额外交互步骤。但如果这个工具出了错，可能需要折叠以减少视觉干扰（按 `foldSingleTool` 配置决定）。

### 8.3 折叠卡片标题算法

```
function getActivityGroupLabel(group):
    // 纯思考组（已完成）→ "思考了 Ns"
    if group.isThinkingOnly AND NOT group.isExploring:
        seconds = max(group.thinkingDurationSeconds ?? 1, 1)
        return formatThinkingDuration(seconds)  // 如 "thought for 3s"

    // 根据会话类型和进行状态选择标签
    if group.sessionType == QUEST:
        return group.isExploring ? "执行中" : "已执行"  // i18n
    else:
        return group.isExploring ? "探索中" : "已探索"  // i18n
```

### 8.4 折叠卡片摘要算法

```
function getActivityGroupSummary(group):
    // 纯思考组不需要摘要（标题已经是"思考了 Ns"）
    if group.isThinkingOnly: return ''

    summary = group.toolsSummary
    parts = []

    // 按固定优先级添加各维度
    if summary.files > 0:      parts.push(formatSummary('file', summary.files, group.sessionType))
    if summary.searches > 0:   parts.push(formatSummary('search', summary.searches, group.sessionType))
    if summary.todos > 0:      parts.push(formatSummary('todo', summary.todos, group.sessionType))
    if summary.edits > 0:      parts.push(formatSummary('edit', summary.edits, group.sessionType))
    if summary.hasProblemTool:  parts.push(formatSummary('problem', summary.problems, group.sessionType))
    if summary.hasListDirTool:  parts.push(formatSummary('directory', summary.directories, group.sessionType))
    if summary.terminalCommands > 0: parts.push(formatSummary('terminal', summary.terminalCommands, group.sessionType))

    // 如果前面已经有内容，不再显示兜底的 toolCalls
    if parts.length > 0: return parts.join(' ')

    // 兜底
    if summary.toolCalls > 0:  parts.push(formatSummary('toolCall', summary.toolCalls, group.sessionType))
    return parts.join(' ')
```

**不同模式的摘要格式差异**：

| 模式 | 格式 | 示例 |
|------|------|------|
| 对话模式 | 纯数字+名词 | "3 文件 5 搜索" |
| 自主模式 | 数字+量词+名词 | "3 个文件 5 次检索" |

### 8.5 摘要的展示规则

- **进行中**（`isExploring=true`）：只显示标签（如"探索中..."），**不显示摘要**（因为数字还在变化）
- **已完成**（`isExploring=false`）：显示标签 + 摘要

### 8.6 展开状态持久化

**问题**：ActivityGroup 内部的子 block 可能有自己的展开/折叠状态（如思考块可以展开查看详情）。当 ActivityGroup 的数据因为流式更新而重新渲染时，子组件会卸载再挂载，导致展开状态丢失。

**方案**：将展开状态提升到会话级别，独立于组件生命周期。

```
interface IBlockExpandStateManager {
    getExpandState(blockId: string) → boolean | undefined
    setExpandState(blockId: string, expanded: boolean) → void
    toggleExpandState(blockId: string) → boolean
    clearExpandState(blockId: string) → void
    clearAllExpandStates() → void
}

// 内部实现：
storage = Map<string, boolean>     // blockId → expanded
sessionId = current session ID     // 当 sessionId 变化时清空 storage

// Hook 封装：
function useBlockExpandState(blockId, defaultExpanded=false):
    manager = getManagerFromContext()
    [isExpanded, setLocal] = useState(manager.get(blockId) ?? defaultExpanded)

    setExpanded = (expanded) =>
        manager.set(blockId, expanded)
        setLocal(expanded)

    toggleExpanded = () =>
        setLocal(prev => { newState = !prev; manager.set(blockId, newState); return newState })
        return !isExpanded

    return [isExpanded, setExpanded, toggleExpanded]
```

**关键设计**：
- 状态存储使用 `useRef`（Map 引用不变），不会因状态变化触发无关重渲染
- 通过 React Context 在组件树中共享 manager 实例
- `sessionId` 变化时自动清除旧状态，避免状态泄漏

### 8.7 分组内子组件的 UI 调整

通过上下文机制告知子组件"你在分组内"：

```
// 在 ActivityGroupCard 渲染子 block 时：
<ActivityGroupContext.Provider value={{ isInActivityGroup: true }}>
    {group.blocks.map(block => renderBlock(block))}
</ActivityGroupContext.Provider>

// 子组件中：
function ToolCallComponent({ block }):
    { isInActivityGroup } = useActivityGroupContext()
    if isInActivityGroup:
        // 隐藏冗余图标、简化展示
```

### 8.8 折叠/展开动画

使用 CSS Grid 实现平滑的高度过渡：

```css
/* 折叠/展开动画容器 */
.content-wrapper {
    display: grid;
    grid-template-rows: 0fr;                /* 折叠态：0 高度 */
    transition: grid-template-rows 0.25s ease-out;
    overflow: hidden;
}

.content-wrapper.expanded {
    grid-template-rows: 1fr;                /* 展开态：自然高度 */
}

.content-wrapper > .content {
    min-height: 0;                          /* 允许收缩到 0 */
    overflow: hidden;
}
```

**为什么用 CSS Grid 而不是 `max-height`**：`max-height` 需要知道内容的具体高度（或设一个很大的值，但会导致动画时长不自然）。CSS Grid 的 `0fr → 1fr` 过渡不需要知道具体高度，动画效果更平滑。

---

## 9. 配置体系

### 9.1 配置项全表

| 配置项 | 类型 | 默认值 | 含义 |
|--------|------|--------|------|
| `groupToolCalls` | bool | true | 总开关：是否启用工具调用分组 |
| `groupThinking` | bool | true | 是否分组思考块 |
| `groupText` | bool | true | 是否分组短文本（仅在已有分组中生效） |
| `groupedTextMaxLength` | number | 150 | 短文本最大长度阈值（字符数） |
| `groupedTextMaxLines` | number | 2 | 短文本最大行数阈值 |
| `foldSingleTool` | bool | false | 单个工具时是否渲染为折叠卡片 |
| `autoExpandWhileExploring` | bool | true | 进行中时是否自动展开卡片 |
| `autoCollapseOnComplete` | bool | true | 完成时是否自动收起卡片 |
| `excludeToolCallIds` | string[] | [] | 待确认的工具 ID（这些不折叠） |
| `confirmedToolCallIds` | string[] | [] | 已确认的工具 ID |
| `foldAfterConfirmation` | bool | false | 工具确认后是否折叠回去 |
| `groupTodos` | bool | true | 是否分组 TODO 操作 |
| `groupSearchTools` | bool | true | 是否分组搜索类工具 |
| `groupReadFile` | bool | true | 是否分组读文件操作 |
| `isCompleted` | bool | (自动) | 会话是否已结束（由 streamState 自动推导） |

### 9.2 配置合并

```
最终配置 = { ...DEFAULT_CONFIG, ...用户覆盖配置 }
最终配置.isCompleted = isTerminalState(streamState)  // 始终由流状态推导
```

### 9.3 工具确认机制

在需要用户确认的场景（如 human-in-the-loop），配置项配合工作：

1. 工具进入待确认状态 → 加入 `excludeToolCallIds` → 不折叠（用户需要看到并确认）
2. 用户确认 → 从 `excludeToolCallIds` 移除，加入 `confirmedToolCallIds`
3. 确认后的行为由 `foldAfterConfirmation` 决定：
   - `false`（默认）：确认后仍然独立展示
   - `true`：确认后按正常折叠规则处理（可能被折叠进分组）

---

## 10. 复刻要点总结

### 10.1 最小可行实现

1. **定义工具分类**（探索/编辑/确认），这是折叠判断的基础
2. **实现核心分组循环**（§5.2），线性扫描 + flush 机制
3. **实现一套基础折叠规则**（探索工具折叠、编辑/确认不折叠、错误工具折叠）
4. **渲染折叠卡片**，支持点击展开/收起
5. **实现工具汇总统计**（文件去重、搜索计数），显示在卡片摘要中

### 10.2 进阶特性（按优先级）

6. **最后消息保护**（§5.3）：保证每轮对话的最后文本输出不被折叠
7. **isExploring 自动展开/收起**（§8.1）：流式场景下的动态卡片状态
8. **手动操作保护**（§8.1）：用户手动操作后停止自动行为
9. **策略模式**（§4）：不同场景使用不同折叠规则
10. **同文件编辑聚合**（§6.7）：自主模式下对同文件的连续修改合并成一组
11. **SubAgent 递归**（§5.6）：嵌套子任务的分组处理
12. **展开状态持久化**（§8.6）：避免重渲染导致的子 block 展开状态丢失
13. **特殊渲染规则**（§8.2）：纯思考组、单工具组的平铺优化

### 10.3 设计原则

- **默认安全**：未知工具类型不折叠（保守策略），宁可多展示也不误折叠
- **错误收敛**：失败的工具调用折叠起来，减少对主流程的视觉干扰
- **尊重用户**：用户手动操作后不再自动干预，手动意图永远优先
- **展示一致性**：思考时长等指标在单独展示和聚合展示中保持一致
- **双重判断**：工具分类同时支持 toolKind（通用）和 toolName（具体），新增工具无需改分组代码
- **可配置**：核心行为通过配置项控制，便于不同产品形态调整
- **渐进增强**：策略继承体系让新模式只需覆盖差异部分，减少代码重复
