# 实现计划：Terminal 心跳保活与断连重连

## 概述

为 HiCoding WebSocket 连接链路添加心跳保活和断连重连机制。实现分为四个阶段：新建共享 Ping 调度器 → 集成到两个 Handler → RemoteTerminalBackend 心跳+重连 → WebSocket 容器超时配置。

## 任务

- [x] 1. 新建 WebSocketPingScheduler 共享 Ping 调度器
  - [x] 1.1 创建 `WebSocketPingScheduler` 类
    - 新建文件 `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/websocket/WebSocketPingScheduler.java`
    - 使用 `@Component` 注解，作为单例 Bean 供两个 Handler 共享
    - 内部使用 `ScheduledExecutorService`（单线程，daemon 线程，线程名 `ws-ping-scheduler`）
    - 使用 `ConcurrentHashMap<String, ScheduledFuture<?>>` 管理每个 session 的 ping 定时器
    - 实现 `startPing(WebSocketSession session)` 方法：每 30 秒发送 `PingMessage`，发送前检查 session.isOpen()，异常时 log warn 不传播
    - 实现 `stopPing(String sessionId)` 方法：取消 ScheduledFuture 并从 map 中移除
    - `startPing` 对同一 sessionId 重复调用时，先 stopPing 旧的再注册新的
    - _需求: 1.1, 1.2, 1.5, 5.5_

- [x] 2. 集成 Ping 调度器到 HiCodingWebSocketHandler 和 TerminalWebSocketHandler
  - [x] 2.1 修改 `HiCodingWebSocketHandler` 集成 Ping 调度器
    - 修改文件 `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/websocket/HiCodingWebSocketHandler.java`
    - 注入 `WebSocketPingScheduler` 依赖（构造函数注入）
    - 在 `afterConnectionEstablished()` 方法末尾调用 `pingScheduler.startPing(session)`
    - 在 `afterConnectionClosed()` 方法中调用 `pingScheduler.stopPing(session.getId())`
    - 在 `handleTransportError()` 方法中调用 `pingScheduler.stopPing(session.getId())`
    - _需求: 1.1, 1.3, 1.4, 5.1_
  - [x] 2.2 修改 `TerminalWebSocketHandler` 集成 Ping 调度器
    - 修改文件 `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/terminal/TerminalWebSocketHandler.java`
    - 注入 `WebSocketPingScheduler` 依赖（构造函数注入）
    - 在 `afterConnectionEstablished()` 方法中，backend.start() 成功后调用 `pingScheduler.startPing(session)`
    - 在 `cleanup()` 方法中调用 `pingScheduler.stopPing(sessionId)`
    - _需求: 1.2, 1.3, 1.4, 5.1_

- [x] 3. 检查点 - 确认 Ping 调度器集成完成
  - 确保所有代码编译通过，ask the user if questions arise.

- [x] 4. RemoteTerminalBackend 心跳保活与断连重连
  - [x] 4.1 为 `RemoteTerminalBackend` 添加心跳保活机制
    - 修改文件 `himarket-server/src/main/java/com/alibaba/himarket/service/hicoding/terminal/RemoteTerminalBackend.java`
    - 新增 `ScheduledExecutorService scheduler` 字段（单线程，daemon 线程，线程名 `remote-terminal-scheduler`）
    - 新增 `ScheduledFuture<?> pingFuture` 字段
    - 实现 `startHeartbeat()` 方法：每 30 秒通过 WebSocket session 发送 ping frame，参照 `RemoteRuntimeAdapter.startWsPing()` 模式
    - 实现 `stopHeartbeat()` 方法：取消 pingFuture
    - 在 `start()` 方法中 connectedLatch 释放后调用 `startHeartbeat()`
    - 在 `close()` 方法中调用 `stopHeartbeat()` 并 shutdown scheduler
    - ping 发送失败时捕获异常并 log warn
    - _需求: 2.1, 2.2, 2.3, 2.4, 5.2, 5.3, 5.4_
  - [x] 4.2 为 `RemoteTerminalBackend` 添加断连重连机制
    - 继续修改 `RemoteTerminalBackend.java`
    - 新增 `AtomicInteger reconnectAttempts` 字段和 `volatile boolean reconnecting` 字段
    - 保存连接参数（cols, rows）供重连时使用
    - 修改 `start()` 中的 `doOnError` 和 `doOnComplete` 回调：不直接 `outputSink.tryEmitComplete()`，改为调用 `scheduleReconnect()`
    - 实现 `scheduleReconnect()` 方法：检查 closed 标志 → 停止心跳 → 计算退避延迟 `min(1000 * 2^attempt, 30000)` ms → 通过 scheduler 延迟执行 `doReconnect()`
    - 实现 `doReconnect()` 方法：创建新的 WebSocket 连接 → 成功则重置 reconnectAttempts、启动心跳、将新 session 的 receive 流接入 outputSink → 失败则递增计数器，若 < 5 则再次 scheduleReconnect()，否则 outputSink.tryEmitComplete()
    - 修改 `close()` 方法：设置 closed = true 后停止所有重连尝试（取消 scheduler 中的待执行任务）
    - _需求: 3.1, 3.2, 3.3, 3.4, 3.5, 5.2, 5.3_

- [x] 5. 检查点 - 确认 RemoteTerminalBackend 改造完成
  - 确保所有代码编译通过，ask the user if questions arise.

- [x] 6. WebSocket 容器空闲超时配置
  - [x] 6.1 修改 `WebSocketConfig` 添加空闲超时配置
    - 修改文件 `himarket-bootstrap/src/main/java/com/alibaba/himarket/config/WebSocketConfig.java`
    - 在 `createWebSocketContainer()` 方法中添加 `container.setMaxSessionIdleTimeout(120_000L)`（120 秒）
    - _需求: 4.1, 4.2_

- [x] 7. 最终检查点 - 确保所有改动编译通过
  - 确保所有代码编译通过，ask the user if questions arise.

## 备注

- 前端无需改动，WebSocket 协议级 ping frame（RFC 6455）由浏览器自动回复 pong
- 每个任务引用了具体的需求编号以确保可追溯性
- 检查点用于增量验证，确保每个阶段的改动正确
