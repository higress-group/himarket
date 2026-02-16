# 实现计划：ACP 错误响应处理

## 概述

将 ACP JSON-RPC 错误响应从"被静默吞掉"改为"在对话流中可视化展示"。变更涉及类型定义、状态管理、Hook 层错误捕获、UI 渲染四个层面，按自底向上的顺序实现。

## 任务

- [x] 1. 类型定义与 ACP 工具层修改
  - [x] 1.1 在 `src/types/acp.ts` 中新增 `ChatItemError` 接口并扩展 `ChatItem` 联合类型，同时扩展 `AcpResponse.error` 以支持 `data` 字段
    - 新增 `ChatItemError` 接口：包含 `type: "error"`、`id: string`、`code: number`、`message: string`、`data?: Record<string, unknown>`
    - 将 `ChatItemError` 加入 `ChatItem` 联合类型
    - 扩展 `AcpResponse.error` 类型为 `{ code: number; message: string; data?: Record<string, unknown> }`
    - _Requirements: 1.1, 1.2_

  - [x] 1.2 编写 `resolveResponse` 错误传递的属性测试
    - **Property 4: resolveResponse 正确传递错误对象**
    - 在 `src/lib/utils/acp.test.ts` 中使用 `fast-check` 生成随机 AcpResponse（含 error 字段），验证 reject 回调收到完整的 `{ code, message, data? }` 对象
    - **Validates: Requirements 5.1**

- [x] 2. 状态管理层修改
  - [x] 2.1 在 `src/context/QuestSessionContext.tsx` 中新增 `PROMPT_ERROR` action 类型和 reducer 处理逻辑
    - 在 `QuestAction` 联合类型中新增 `PROMPT_ERROR` action（含 `questId`、`requestId`、`code`、`message`、`data?`）
    - 在 `questReducer` 中添加 `PROMPT_ERROR` case：追加 `ChatItemError` 到 messages、重置 `isProcessing` 和 `inflightPromptId`、设置 `lastStopReason` 和 `lastCompletedAt`
    - 包含 requestId 与 inflightPromptId 的匹配检查，不匹配时忽略
    - _Requirements: 2.1, 2.2, 2.3_

  - [x] 2.2 编写 questReducer PROMPT_ERROR 行为的属性测试
    - **Property 1: PROMPT_ERROR action 正确更新 reducer 状态**
    - 在 `src/context/QuestSessionContext.test.ts` 中使用 `fast-check` 生成随机 code、message、data，验证 reducer 正确追加错误消息并重置处理状态
    - **Validates: Requirements 2.1, 2.2**

  - [x] 2.3 编写过期请求忽略的属性测试
    - **Property 2: 过期请求的 PROMPT_ERROR 被忽略**
    - 在 `src/context/QuestSessionContext.test.ts` 中使用 `fast-check` 生成不匹配的 requestId，验证 reducer 状态不变
    - **Validates: Requirements 2.3**

- [x] 3. Hook 层错误捕获修改
  - [x] 3.1 修改 `src/hooks/useAcpSession.ts` 中 `startPrompt` 的 `.catch()` 逻辑
    - 从 reject 的 error 对象中提取 `code`、`message`、`data`
    - dispatch `PROMPT_ERROR` action 替代原来的 `PROMPT_COMPLETED`
    - 对 error 对象做防御性类型检查，格式异常时使用默认值
    - _Requirements: 3.1, 3.3_

  - [x] 3.2 修改 `src/hooks/useHiCliSession.ts` 中 `startPrompt` 的 `.catch()` 逻辑
    - 同 3.1 的修改逻辑
    - _Requirements: 3.2, 3.3_

- [x] 4. Checkpoint - 确保类型和状态管理层正确
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 5. UI 层错误消息渲染
  - [x] 5.1 创建 `src/components/quest/ErrorMessage.tsx` 错误消息渲染组件
    - 接收 `code: number`、`message: string`、`data?: Record<string, unknown>` 作为 props
    - 使用红色边框/背景样式以视觉区分
    - 展示错误码和错误描述文本
    - 当 data 存在时展示扩展数据中的关键信息
    - _Requirements: 4.1, 4.2, 4.3_

  - [x] 5.2 编写 ErrorMessage 组件渲染完整性的属性测试
    - **Property 3: 错误消息渲染包含完整错误信息**
    - 在 `src/components/quest/ErrorMessage.test.tsx` 中使用 `fast-check` 生成随机 code 和 message，验证渲染输出包含这些信息
    - **Validates: Requirements 4.2**

  - [x] 5.3 修改 `src/components/quest/ChatStream.tsx` 添加 `error` 类型 ChatItem 的渲染分支
    - 在 `renderItems` 的 switch 语句中添加 `case "error"` 分支
    - 导入并使用 `ErrorMessage` 组件渲染错误消息
    - _Requirements: 4.1_

- [x] 6. 端到端属性测试
  - [x] 6.1 编写端到端错误信息保真的属性测试
    - **Property 5: 错误信息端到端保真**
    - 在 `src/context/QuestSessionContext.test.ts` 中使用 `fast-check` 生成随机 ACP 错误响应，模拟完整链路（构造 error → 提取字段 → dispatch PROMPT_ERROR → 验证 messages 中的 ChatItemError），验证 code 和 message 保真
    - **Validates: Requirements 5.2**

- [x] 7. 最终 Checkpoint - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号以确保可追溯性
- 属性测试验证通用正确性属性，单元测试验证具体示例和边界情况
- Checkpoint 确保增量验证
