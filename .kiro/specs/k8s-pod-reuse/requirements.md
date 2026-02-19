# 需求文档：K8s 沙箱 Pod 复用

## 简介

当前 HiMarket 的 K8s 运行时模式中，每个 WebSocket 会话都会创建一个独立的 K8s Pod。本功能引入"用户级沙箱"模式作为 POC，为每个用户维护一个常驻 Pod，多个会话复用同一个 Pod，从而显著减少 Pod 创建开销，提升调试和开发体验。

K8s 沙箱将分为两种模式，由用户在前端 UI 中选择：
- **用户级沙箱（User-Scoped）**：常驻 Pod，多会话复用，适合调试开发（本次 POC 实现）
- **会话级沙箱（Session-Scoped）**：未来对接 AgentRun 架构，通过空闲超时自动销毁 Pod，而非 WebSocket 断开即销毁（本次不实现，保留占位）

由于 websocat 天然支持多个并发 WebSocket 连接（每个连接独立 fork 一个 CLI 子进程），容器端无需改动，核心改动集中在 Java 后端和前端运行时选择器。

## 术语表

- **User_Scoped_Sandbox**: 用户级沙箱模式，为每个用户维护一个常驻 Pod，多个会话共享该 Pod
- **Session_Scoped_Sandbox**: 会话级沙箱模式，未来对接 AgentRun 架构实现，本次 POC 不实现
- **Sandbox_Mode**: 沙箱模式，用户在前端选择的 K8s 沙箱隔离级别，取值为 `user`（本次实现）或 `session`（占位）
- **Pod_Reuse_Manager**: Pod 复用管理器，负责维护用户级沙箱 Pod 的本地缓存、查找和生命周期管理
- **K8sRuntimeAdapter**: K8s 运行时适配器，管理 Pod 生命周期和 WebSocket 通信
- **RuntimeSelector_UI**: 前端运行时选择器组件，展示可选的运行时方案和沙箱模式
- **Pod_Cache**: Pod 本地缓存，存储用户级沙箱 Pod 的元信息（podName、podIp、活跃连接数等）
- **Label_Selector**: K8s 标签选择器，通过 `app=sandbox`、`userId=xxx`、`sandboxMode=user` 标签定位目标 Pod
- **Connection_Count**: 当前 Pod 上活跃的 WebSocket 连接数，用于判断 Pod 是否空闲

## 需求

### 需求 1：沙箱模式前端选择

**用户故事：** 作为开发者，我希望在前端 UI 中选择 K8s 沙箱的隔离模式，以便根据当前工作场景选择合适的沙箱策略。

#### 验收标准

1. WHEN 用户选择 K8s 运行时时，THE RuntimeSelector_UI SHALL 展示沙箱模式子选项，包含用户级沙箱选项
2. THE RuntimeSelector_UI SHALL 将用户级沙箱描述为"常驻 Pod，多会话复用，适合调试开发"
3. THE RuntimeSelector_UI SHALL 展示会话级沙箱选项并标记为"即将推出"且不可选
4. THE RuntimeSelector_UI SHALL 默认选中用户级沙箱模式
5. WHEN 用户选择沙箱模式后，THE RuntimeSelector_UI SHALL 将选择持久化到 localStorage
6. WHEN 建立 WebSocket 连接时，THE RuntimeSelector_UI SHALL 将沙箱模式作为查询参数 `sandboxMode=user` 传递给后端

### 需求 2：后端沙箱模式解析

**用户故事：** 作为平台开发者，我希望后端能解析前端传递的沙箱模式参数，以便路由到正确的 Pod 管理策略。

#### 验收标准

1. THE AcpWebSocketHandler SHALL 从 WebSocket 握手请求的查询参数中解析 `sandboxMode` 值
2. WHEN WebSocket 连接携带 `sandboxMode=user` 参数时，THE AcpWebSocketHandler SHALL 使用 Pod_Reuse_Manager 查找或创建用户级沙箱 Pod
3. IF sandboxMode 参数缺失或值无法识别，THEN THE AcpWebSocketHandler SHALL 回退到用户级沙箱模式（POC 阶段默认行为）

### 需求 3：用户级沙箱 Pod 查找与复用

**用户故事：** 作为开发者，我希望选择用户级沙箱后新建会话时能自动复用已有的常驻 Pod，以便跳过 Pod 创建等待时间，快速进入编码状态。

#### 验收标准

1. WHEN 用户级沙箱模式下用户发起新会话时，THE Pod_Reuse_Manager SHALL 优先从 Pod_Cache 中查找该用户的可复用 Pod
2. WHEN 缓存未命中时，THE Pod_Reuse_Manager SHALL 通过 Label_Selector（`app=sandbox`, `userId={userId}`, `sandboxMode=user`）查询 K8s API 查找该用户已有的 Running 状态 Pod
3. WHEN 找到可复用的 Running Pod 时，THE K8sRuntimeAdapter SHALL 直接建立 WebSocket 连接到该 Pod 的 websocat 端点，跳过 Pod 创建流程
4. WHEN 未找到可复用的 Pod 时，THE K8sRuntimeAdapter SHALL 创建新的 Pod，并在 Pod 标签中添加 `sandboxMode=user`，然后将其注册到 Pod_Cache
5. WHEN 复用已有 Pod 时，THE K8sRuntimeAdapter SHALL 验证 Pod 的健康状态（Phase 为 Running），确认可用后再建立连接
6. IF Pod 健康检查失败，THEN THE Pod_Reuse_Manager SHALL 清理该不健康的 Pod 缓存记录并创建新的 Pod

### 需求 4：Pod 本地缓存

**用户故事：** 作为平台开发者，我希望有一个本地缓存机制来存储用户级沙箱 Pod 的信息，以便避免每次会话都查询 K8s API，降低 API Server 压力。

#### 验收标准

1. THE Pod_Reuse_Manager SHALL 维护一个以 userId 为键的 Pod 元信息缓存，包含 podName、podIp、创建时间和活跃连接数
2. WHEN 新的用户级沙箱 Pod 创建成功时，THE Pod_Reuse_Manager SHALL 将该 Pod 的元信息写入缓存
3. WHEN Pod 被删除或变为不可用时，THE Pod_Reuse_Manager SHALL 从缓存中移除对应条目
4. WHEN K8s API 回退查询命中时，THE Pod_Reuse_Manager SHALL 将查询结果回填到缓存中
5. THE Pod_Reuse_Manager SHALL 使用 ConcurrentHashMap 保证缓存操作的线程安全性

### 需求 5：连接计数与 Pod 生命周期

**用户故事：** 作为开发者，我希望关闭用户级沙箱的会话时只断开 WebSocket 连接而不删除 Pod，以便后续会话可以继续复用该 Pod。

#### 验收标准

1. WHEN 用户级沙箱模式下会话关闭时，THE K8sRuntimeAdapter SHALL 仅断开当前会话的 WebSocket 连接，保留 Pod 运行
2. THE Pod_Reuse_Manager SHALL 维护每个 Pod 的活跃连接计数，在新连接建立时递增，在连接断开时递减
3. WHEN Pod 的活跃连接计数降为零时，THE Pod_Reuse_Manager SHALL 启动空闲超时计时器（默认 1800 秒）
4. WHEN 空闲超时计时器到期且连接计数仍为零时，THE Pod_Reuse_Manager SHALL 删除该 Pod 并清理缓存
5. WHEN 空闲超时期间有新连接建立时，THE Pod_Reuse_Manager SHALL 取消空闲超时计时器
