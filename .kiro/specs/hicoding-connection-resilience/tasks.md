# 实现计划：HiCoding 连接韧性与产物去重优化

## 概述

本计划分为三个阶段：首先修复产物检测的路径归一化和时机控制问题，然后增强 WebSocket 重连机制并添加断连 UI 提示，最后实现终端重连联动。所有代码使用 TypeScript，测试使用 vitest + fast-check。

## 任务

- [x] 1. 实现路径归一化与产物检测去重
  - [x] 1.1 在 `artifactDetector.ts` 中实现 `normalizePath()` 函数并集成到路径提取流程
    - 在 `himarket-web/himarket-frontend/src/lib/utils/artifactDetector.ts` 中新增 `normalizePath(filePath: string): string` 函数
    - 移除 `./` 前缀、合并连续 `/`、移除末尾 `/`
    - 在 `extractAllPaths()` 中对所有提取的路径调用 `normalizePath()` 后再加入 `seen` 集合
    - 导出 `normalizePath` 供测试使用
    - _需求: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [ ]* 1.2 为 `normalizePath` 编写属性测试：幂等性
    - **属性 1：路径归一化幂等性**
    - 使用 fast-check 生成任意路径字符串，验证 `normalizePath(normalizePath(p)) === normalizePath(p)`
    - **验证: 需求 1.4**

  - [ ]* 1.3 为 `normalizePath` 编写单元测试
    - 覆盖 `./src/foo.html` → `src/foo.html`、`src//foo.html` → `src/foo.html`、`src/foo/` → `src/foo`、空字符串、绝对路径等边界情况
    - _需求: 1.1, 1.2, 1.3_

- [x] 2. 修复产物检测时机控制
  - [x] 2.1 修改 `QuestSessionContext.tsx` 中 `tool_call_update` 分支的产物检测触发条件
    - 在 `handleSessionUpdate` 的 `tool_call_update` 分支中，将 `if ((reachedTerminal || hasLocationsField) && mergedToolCall)` 改为 `if (reachedTerminal && mergedToolCall)`
    - 确保 `tool_call_update` 仅在 status 为 `completed` 或 `failed` 时触发 `applyArtifactDetection`
    - _需求: 2.1, 2.2, 2.3_

  - [x] 2.2 在 `upsertDetectedArtifacts` 中使用 `normalizePath` 进行路径比较
    - 导入 `normalizePath`，将 `a.path === artifact.path` 改为 `normalizePath(a.path) === normalizePath(artifact.path)`
    - 存储归一化后的路径到 artifact 对象
    - _需求: 1.5, 2.4_

  - [ ]* 2.3 为产物去重编写属性测试
    - **属性 2：产物路径去重不变量**
    - 使用 fast-check 生成 tool_call 消息序列，验证最终产物列表中不存在路径归一化后相同的重复项
    - **验证: 需求 1.5, 2.4**

- [x] 3. 检查点 - 产物检测修复验证
  - 确保所有测试通过，ask the user if questions arise.

- [x] 4. 增强 ACP WebSocket 重连机制
  - [x] 4.1 重构 `useAcpWebSocket.ts` 实现无限重连与新状态模型
    - 扩展 `WsStatus` 类型，新增 `"reconnecting"` 状态
    - 移除 `maxReconnectAttempts` 参数，改为无限重连
    - 实现指数退避策略：初始 1s，每次翻倍，最大 30s
    - 提取 `RECONNECT_CONFIG` 常量（baseDelay: 1000, maxDelay: 30000, backoffMultiplier: 2）
    - 连接意外断开时设置状态为 `reconnecting`（而非 `disconnected`）
    - 暴露 `reconnectAttempt` 状态和 `manualReconnect()` 方法
    - `disconnect()` 调用时停止所有重连并设置状态为 `disconnected`
    - 组件卸载时调用 `disconnect()` 清理定时器
    - _需求: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 7.1, 7.2, 7.3, 7.4, 8.2_

  - [ ]* 4.2 为指数退避延迟编写属性测试
    - **属性 3：指数退避延迟上界**
    - 使用 fast-check 生成任意重连次数 N，验证延迟等于 `min(baseDelay × 2^N, maxDelay)` 且不超过 30s
    - **验证: 需求 3.2, 3.6**

  - [ ]* 4.3 为 disconnect 终止行为编写属性测试
    - **属性 4：disconnect 终止不变量**
    - 验证任意状态下调用 `disconnect()` 后状态变为 `disconnected` 且无后续重连
    - **验证: 需求 3.4, 4.4, 7.4**

- [x] 5. 增强 Terminal WebSocket 重连机制
  - [x] 5.1 重构 `useTerminalWebSocket.ts` 实现无限重连与外部重连方法
    - 与 ACP WebSocket 相同的无限重连 + 指数退避策略
    - 移除 `maxReconnectAttempts` 参数
    - 新增 `reconnect()` 方法供外部调用（ACP 重连联动）
    - 重连成功后自动重新发送 resize 信息（保存最近一次 cols/rows）
    - `disconnect()` 调用时停止所有重连并清理资源
    - _需求: 4.1, 4.2, 4.3, 4.4, 8.3_

- [x] 6. 实现断连提示横幅组件
  - [x] 6.1 创建 `ConnectionBanner` 组件
    - 新建 `himarket-web/himarket-frontend/src/components/coding/ConnectionBanner.tsx`
    - 接收 `acpStatus`、`reconnectAttempt`、`onManualReconnect` props
    - `reconnecting` 状态：黄色横幅，显示"连接已断开，正在重连..."和当前重连次数
    - `disconnected` 状态：红色横幅，显示"连接已断开"和"重新连接"按钮
    - `connected` 状态（从 reconnecting 恢复）：短暂显示"已重新连接"后 2 秒自动隐藏
    - `connected` 状态（正常）：不渲染任何 DOM 节点
    - 使用 Tailwind CSS，风格与现有组件一致，使用 lucide-react 图标
    - _需求: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [ ]* 6.2 为 ConnectionBanner 重连次数显示编写属性测试
    - **属性 5：ConnectionBanner 重连次数显示**
    - 使用 fast-check 生成任意正整数 N，验证 `reconnecting` 状态下渲染内容包含该重连次数
    - **验证: 需求 5.1**

- [x] 7. 检查点 - WebSocket 重连与 UI 验证
  - 确保所有测试通过，ask the user if questions arise.

- [x] 8. 集成连接与终端联动
  - [x] 8.1 在 `CodingPage` 中集成 ConnectionBanner 和终端重连联动
    - 在 `Coding.tsx` 的 `CodingContent` 组件中引入 `ConnectionBanner`
    - 将 `useAcpWebSocket` 返回的 `status`、`reconnectAttempt`、`manualReconnect` 传递给 `ConnectionBanner`
    - 添加 `useEffect` 监听 ACP status 从 `reconnecting` 变为 `connected`，自动调用终端的 `reconnect()`
    - 使用 `useRef` 保存前一次 ACP status 用于状态转换检测
    - 横幅放置在 IDE 布局顶部，不遮挡内容
    - _需求: 6.1, 6.2_

  - [x] 8.2 更新依赖 `useAcpWebSocket` 的组件适配新状态类型
    - 检查 `ConversationTopBar` 等使用 `session.status` 的组件，确保兼容新增的 `reconnecting` 状态
    - 更新 `isConnected` 判断逻辑（仅 `connected` 为已连接）
    - _需求: 7.1_

- [x] 9. 最终检查点 - 全部功能验证
  - 确保所有测试通过，ask the user if questions arise.

## 备注

- 标记 `*` 的任务为可选，可跳过以加速 MVP 交付
- 每个任务引用了具体需求编号以确保可追溯性
- 属性测试使用 fast-check 库，验证设计文档中定义的正确性属性
- 检查点确保增量验证，避免问题累积
