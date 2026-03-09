# 需求文档

## 简介

本需求文档描述 HiCoding 连接韧性与产物去重优化特性。该特性解决两个核心问题：（1）ACP tool_call 消息的产物检测存在多次触发和路径格式不一致导致的重复问题；（2）WebSocket 断连后仅重试有限次数就放弃，缺少 UI 反馈和终端联动重连。

## 术语表

- **ACP_WebSocket**：前端与 ACP（AI Coding Platform）后端之间的 WebSocket 连接，用于传输 tool_call 消息和聊天流
- **Terminal_WebSocket**：前端与后端之间的终端 WebSocket 连接，用于传输终端输入输出数据
- **Artifact_Detector**：产物检测器，从 tool_call 消息中提取文件路径并构建产物列表
- **Path_Normalizer**：路径归一化函数 `normalizePath()`，消除文件路径格式差异
- **Connection_Banner**：断连提示横幅 UI 组件，向用户展示 WebSocket 连接状态
- **Quest_Reducer**：会话状态管理器，处理 tool_call 和 tool_call_update 消息的状态更新
- **tool_call**：ACP 返回的完整工具调用消息
- **tool_call_update**：ACP 返回的增量工具调用更新消息
- **终态**：tool_call 的 status 为 `completed` 或 `failed` 的状态

## 需求

### 需求 1：路径归一化

**用户故事：** 作为开发者，我希望文件路径在检测产物时被归一化处理，以避免因路径格式差异导致的重复产物。

#### 验收标准

1. WHEN Artifact_Detector 接收到包含 `./` 前缀的文件路径, THE Path_Normalizer SHALL 移除 `./` 前缀并返回归一化路径
2. WHEN Artifact_Detector 接收到包含连续斜杠的文件路径（如 `src//foo.html`）, THE Path_Normalizer SHALL 将连续斜杠合并为单个斜杠
3. WHEN Artifact_Detector 接收到包含末尾斜杠的文件路径, THE Path_Normalizer SHALL 移除末尾斜杠
4. THE Path_Normalizer SHALL 对任意路径字符串满足幂等性：对同一路径多次归一化的结果与单次归一化的结果相同
5. WHEN Artifact_Detector 提取文件路径时, THE Artifact_Detector SHALL 对所有提取的路径调用 Path_Normalizer 进行归一化后再进行去重比较

### 需求 2：产物检测时机控制

**用户故事：** 作为开发者，我希望产物检测仅在 tool_call 到达终态时触发，以避免同一个 tool_call 被多次检测产生重复产物。

#### 验收标准

1. WHEN Quest_Reducer 收到 tool_call 消息且 status 为终态, THE Artifact_Detector SHALL 执行产物检测
2. WHEN Quest_Reducer 收到 tool_call_update 消息且 status 为终态, THE Artifact_Detector SHALL 执行产物检测
3. WHEN Quest_Reducer 收到 tool_call_update 消息且 status 不是终态, THE Quest_Reducer SHALL 仅合并消息字段而不触发产物检测
4. WHEN 同一个 tool_call 先收到 tool_call_update（终态）再收到 tool_call（终态）, THE Artifact_Detector SHALL 通过路径归一化去重确保不产生重复产物

### 需求 3：ACP WebSocket 无限重连

**用户故事：** 作为用户，我希望 ACP WebSocket 断连后能自动无限重连，以便在网络恢复后无需手动刷新页面。

#### 验收标准

1. WHEN ACP_WebSocket 连接意外断开, THE ACP_WebSocket SHALL 自动开始重连并将状态设置为 `reconnecting`
2. WHILE ACP_WebSocket 处于 `reconnecting` 状态, THE ACP_WebSocket SHALL 使用指数退避策略持续重连，初始延迟 1 秒，每次翻倍，最大延迟 30 秒
3. WHEN ACP_WebSocket 重连成功, THE ACP_WebSocket SHALL 将状态设置为 `connected` 并调用 `onConnected` 回调
4. WHEN 用户调用 `disconnect()` 方法, THE ACP_WebSocket SHALL 停止所有重连尝试并将状态设置为 `disconnected`
5. WHEN 用户调用 `manualReconnect()` 方法, THE ACP_WebSocket SHALL 重置重连计数器并立即发起重连
6. THE ACP_WebSocket SHALL 在每次重连尝试时更新 `reconnectAttempt` 计数器以反映当前重连次数

### 需求 4：Terminal WebSocket 无限重连

**用户故事：** 作为用户，我希望终端 WebSocket 断连后也能自动无限重连，以保持终端会话的连续性。

#### 验收标准

1. WHEN Terminal_WebSocket 连接意外断开, THE Terminal_WebSocket SHALL 自动开始重连并使用与 ACP_WebSocket 相同的指数退避策略
2. WHEN Terminal_WebSocket 重连成功, THE Terminal_WebSocket SHALL 自动重新发送终端 resize 信息
3. WHEN 外部调用 Terminal_WebSocket 的 `reconnect()` 方法, THE Terminal_WebSocket SHALL 立即发起重连
4. WHEN 用户调用 `disconnect()` 方法, THE Terminal_WebSocket SHALL 停止所有重连尝试并清理资源

### 需求 5：断连提示横幅

**用户故事：** 作为用户，我希望在 WebSocket 断连时看到清晰的 UI 提示，以了解当前连接状态和重连进度。

#### 验收标准

1. WHILE ACP_WebSocket 处于 `reconnecting` 状态, THE Connection_Banner SHALL 显示黄色横幅，内容包含"连接已断开，正在重连..."和当前重连次数
2. WHILE ACP_WebSocket 处于 `disconnected` 状态, THE Connection_Banner SHALL 显示红色横幅，内容包含"连接已断开"和"重新连接"按钮
3. WHEN ACP_WebSocket 从 `reconnecting` 状态变为 `connected` 状态, THE Connection_Banner SHALL 短暂显示"已重新连接"提示后自动隐藏
4. WHILE ACP_WebSocket 处于 `connected` 状态且无需显示重连成功提示, THE Connection_Banner SHALL 不渲染任何 DOM 节点
5. WHEN 用户点击 Connection_Banner 上的"重新连接"按钮, THE Connection_Banner SHALL 调用 `onManualReconnect` 回调触发手动重连

### 需求 6：终端重连联动

**用户故事：** 作为用户，我希望 ACP 重连成功后终端也能自动重连，以避免需要手动恢复终端连接。

#### 验收标准

1. WHEN ACP_WebSocket 从 `reconnecting` 状态变为 `connected` 状态, THE CodingPage SHALL 自动触发 Terminal_WebSocket 的 `reconnect()` 方法
2. WHEN 终端联动重连被触发, THE Terminal_WebSocket SHALL 清理旧的输出缓冲后建立新连接

### 需求 7：WebSocket 状态模型

**用户故事：** 作为开发者，我希望 WebSocket 状态转换有明确的规则，以确保 UI 和重连逻辑的一致性。

#### 验收标准

1. THE ACP_WebSocket SHALL 支持四种状态：`disconnected`、`connecting`、`connected`、`reconnecting`
2. WHEN ACP_WebSocket 处于 `connected` 状态且连接意外断开, THE ACP_WebSocket SHALL 转换为 `reconnecting` 状态而非 `disconnected` 状态
3. WHEN ACP_WebSocket 处于 `reconnecting` 状态且重连成功, THE ACP_WebSocket SHALL 转换为 `connected` 状态
4. WHEN 任意状态下调用 `disconnect()`, THE ACP_WebSocket SHALL 转换为 `disconnected` 状态并停止所有重连

### 需求 8：安全与资源管理

**用户故事：** 作为开发者，我希望重连机制能正确处理认证失败和资源清理，以避免安全风险和内存泄漏。

#### 验收标准

1. IF WebSocket 重连时服务端返回认证失败（如 401）, THEN THE ACP_WebSocket SHALL 停止重连并引导用户重新登录
2. WHEN 组件卸载时, THE ACP_WebSocket SHALL 调用 `disconnect()` 停止重连并清理所有定时器
3. WHEN 组件卸载时, THE Terminal_WebSocket SHALL 调用 `disconnect()` 停止重连并清理所有资源
