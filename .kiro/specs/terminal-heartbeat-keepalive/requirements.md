# 需求文档

## 简介

本需求文档描述 HiCoding WebSocket 心跳保活与 Terminal 后端重连机制。

### 问题现状

HiCoding 有两个 WebSocket 通道：ACP（`/ws/acp`，承载聊天会话流）和 Terminal（`/ws/terminal`，承载终端 I/O）。文件树浏览走 REST API，不受影响。

当前 Terminal 连接在用户空闲时（无键盘输入）缺少任何消息流，导致中间网络层（代理、防火墙、k8s port-forward、Ingress）因超时而断开连接。ACP 连接虽然在活跃对话时有消息流充当隐式心跳，但长时间不对话时同样面临被断开的风险。

### 连接链路

```
前端浏览器 →(WebSocket /ws/acp)→ Java 后端 HiCodingWebSocketHandler →(WebSocket)→ Sidecar CLI Agent
前端浏览器 →(WebSocket /ws/terminal)→ Java 后端 TerminalWebSocketHandler →(WebSocket)→ Sidecar RemoteTerminalBackend → node-pty shell
```

### 现有机制

- ACP 后端→Sidecar 链路：`RemoteRuntimeAdapter` 已有 WebSocket 协议级 ping 保活（`startWsPing()`，每 10 秒发送 ping frame）✅
- ACP 前端→后端链路：无心跳 ❌
- Terminal 前端→后端链路：无心跳 ❌
- Terminal 后端→Sidecar 链路（`RemoteTerminalBackend`）：无心跳，无断连重连 ❌
- 前端两个通道均已有无限重连 + 指数退避机制 ✅

## 术语表

- **ACP_WebSocket**：前端浏览器与 Java 后端之间的 ACP WebSocket 连接（路径 `/ws/acp`）
- **Terminal_WebSocket**：前端浏览器与 Java 后端之间的 Terminal WebSocket 连接（路径 `/ws/terminal`）
- **Sidecar_Terminal_WebSocket**：Java 后端 RemoteTerminalBackend 与 Sidecar 之间的 WebSocket 连接（路径 `/terminal`）
- **HiCodingWebSocketHandler**：Java 后端处理 ACP WebSocket 连接的 Handler
- **TerminalWebSocketHandler**：Java 后端处理 Terminal WebSocket 连接的 Handler
- **RemoteTerminalBackend**：Java 后端通过 WebSocket 连接 Sidecar 终端的后端组件
- **RemoteRuntimeAdapter**：Java 后端通过 WebSocket 连接 Sidecar CLI Agent 的运行时适配器（已有 ping 保活）
- **WebSocketConfig**：Spring WebSocket 配置类
- **Ping_Frame**：WebSocket 协议级 ping 帧（RFC 6455），浏览器自动回复 pong，无需前端代码处理

## 需求

### 需求 1：后端对前端 WebSocket 连接的协议级 Ping 保活

**用户故事：** 作为用户，我希望 ACP 和 Terminal 的 WebSocket 连接在空闲时不会被网络中间层超时断开。

#### 验收标准

1. WHILE ACP_WebSocket 处于连接状态, THE HiCodingWebSocketHandler SHALL 每 30 秒向前端发送一个 WebSocket 协议级 Ping_Frame
2. WHILE Terminal_WebSocket 处于连接状态, THE TerminalWebSocketHandler SHALL 每 30 秒向前端发送一个 WebSocket 协议级 Ping_Frame
3. WHEN WebSocket 连接建立成功（afterConnectionEstablished）, THE Handler SHALL 启动 ping 定时器
4. WHEN WebSocket 连接关闭（afterConnectionClosed）或传输错误（handleTransportError）, THE Handler SHALL 停止 ping 定时器并释放资源
5. THE Ping_Frame SHALL 使用 WebSocket 协议级 ping（RFC 6455），浏览器自动回复 pong，无需前端代码改动

### 需求 2：RemoteTerminalBackend 心跳保活

**用户故事：** 作为开发者，我希望后端到 Sidecar 的 Terminal WebSocket 连接也有心跳保活机制，与 RemoteRuntimeAdapter 保持一致。

#### 验收标准

1. WHILE Sidecar_Terminal_WebSocket 处于连接状态, THE RemoteTerminalBackend SHALL 每 30 秒发送一条心跳消息保持连接活跃
2. WHEN RemoteTerminalBackend 连接建立成功（connectedLatch 释放后）, THE RemoteTerminalBackend SHALL 启动心跳定时器
3. WHEN RemoteTerminalBackend 关闭（close() 被调用）, THE RemoteTerminalBackend SHALL 停止心跳定时器并释放调度器资源
4. THE RemoteTerminalBackend SHALL 使用 ScheduledExecutorService 管理心跳定时器，与 RemoteRuntimeAdapter 的实现模式保持一致

### 需求 3：RemoteTerminalBackend 断连重连

**用户故事：** 作为用户，我希望后端到 Sidecar 的 Terminal 连接断开后能自动重连，而不是直接 cleanup 导致前端需要重建整个链路。

#### 验收标准

1. WHEN Sidecar_Terminal_WebSocket 连接意外断开（doOnError 或 doOnComplete 触发）, THE RemoteTerminalBackend SHALL 自动尝试重连而非直接 emitComplete
2. THE RemoteTerminalBackend SHALL 使用指数退避策略重连：初始延迟 1 秒，每次翻倍，最大延迟 30 秒
3. WHEN 重连成功, THE RemoteTerminalBackend SHALL 恢复输出流订阅，使前端 WebSocket 连接无需重建
4. IF 重连连续失败超过 5 次, THEN THE RemoteTerminalBackend SHALL 放弃重连并触发 outputSink.tryEmitComplete()，通知上层连接已不可恢复
5. WHEN RemoteTerminalBackend 的 close() 被主动调用, THE RemoteTerminalBackend SHALL 停止所有重连尝试

### 需求 4：WebSocket 容器空闲超时配置

**用户故事：** 作为开发者，我希望 Spring WebSocket 容器配置合理的空闲超时时间，配合心跳机制工作。

#### 验收标准

1. THE WebSocketConfig SHALL 配置 WebSocket 容器的最大空闲超时时间为 120 秒
2. THE WebSocketConfig SHALL 确保空闲超时时间大于 ping 间隔（30 秒）的 2 倍，为 ping 丢失提供容错空间

### 需求 5：资源管理与线程安全

**用户故事：** 作为开发者，我希望心跳和重连机制的资源管理正确，不会导致内存泄漏或线程安全问题。

#### 验收标准

1. WHEN Handler 的 cleanup/afterConnectionClosed 方法被调用, THE Handler SHALL 确保所有关联的 ping 定时器被取消
2. WHEN RemoteTerminalBackend 的 close 方法被调用, THE RemoteTerminalBackend SHALL 先停止心跳定时器和重连尝试，再关闭 WebSocket 连接
3. THE RemoteTerminalBackend SHALL 使用 daemon 线程运行心跳和重连定时器，避免阻止 JVM 关闭
4. IF ping 发送时 WebSocket 连接已关闭, THEN THE ping 定时器 SHALL 捕获异常并优雅处理而不抛出未处理异常
5. THE HiCodingWebSocketHandler 和 TerminalWebSocketHandler SHALL 使用共享的 ScheduledExecutorService 管理 ping 定时器，避免每个连接创建独立线程池
