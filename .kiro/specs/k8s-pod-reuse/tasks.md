# 实现计划：K8s 沙箱 Pod 复用

## 概述

基于设计文档，将 K8s 用户级沙箱 Pod 复用功能拆分为增量式编码任务。后端使用 Java（Spring Boot + Fabric8），前端使用 TypeScript（React + Ant Design）。

## 任务

- [x] 1. 新增 PodEntry 和 PodInfo 数据类
  - [x] 1.1 创建 `PodEntry` 类，包含 podName、podIp、createdAt、connectionCount（AtomicInteger）、idleTimer（ScheduledFuture）字段
    - 路径：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/PodEntry.java`
    - _Requirements: 4.1, 5.2_
  - [x] 1.2 创建 `PodInfo` record，包含 podName、podIp、sidecarWsUri、reused 字段
    - 路径：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/PodInfo.java`
    - _Requirements: 3.3_

- [x] 2. 实现 PodReuseManager 核心逻辑
  - [x] 2.1 创建 `PodReuseManager` Spring Bean，实现 acquirePod 方法（缓存查找 → 健康验证 → K8s API 回退 → 创建新 Pod）
    - 使用 ConcurrentHashMap 作为缓存
    - 注入 K8sConfigService 获取 KubernetesClient
    - acquirePod 内部使用 compute 保证线程安全
    - _Requirements: 3.1, 3.2, 3.4, 3.5, 3.6, 4.2, 4.4, 4.5_
  - [x] 2.2 实现 releasePod 方法（递减连接计数、启动空闲超时计时器）
    - 连接计数归零时启动 ScheduledFuture 空闲计时器（默认 1800 秒）
    - 新连接建立时取消计时器
    - _Requirements: 5.2, 5.3, 5.4, 5.5_
  - [x] 2.3 实现 evictPod 和 getPodEntry 辅助方法
    - evictPod：删除 K8s Pod + 清理缓存
    - getPodEntry：查询缓存条目（用于测试和监控）
    - _Requirements: 4.3_
  - [x] 2.4 编写 PodReuseManager 属性测试
    - **Property 3: Pod 缓存 round-trip**
    - **Property 4: Pod 缓存清理一致性**
    - **Property 5: 新建用户级沙箱 Pod 标签完整性**
    - **Property 7: 连接计数不变量**
    - **Validates: Requirements 3.1, 3.4, 4.2, 4.3, 5.2**
  - [x] 2.5 编写 PodReuseManager 单元测试
    - 测试缓存命中/未命中场景
    - 测试健康检查失败后的清理和重建
    - 测试空闲超时触发 Pod 删除
    - 测试空闲期间新连接取消计时器
    - _Requirements: 3.5, 3.6, 5.3, 5.4, 5.5_

- [x] 3. 修改 K8sRuntimeAdapter 支持复用模式
  - [x] 3.1 新增 `startWithExistingPod(PodInfo)` 方法，跳过 Pod 创建直接建立 WebSocket 连接
    - 设置 podName、podIp、sidecarWsUri
    - 调用 connectToSidecarWebSocket
    - 启动健康检查和空闲检查
    - 创建 PodFileSystemAdapter
    - _Requirements: 3.3_
  - [x] 3.2 新增 `reuseMode` 标志，修改 `close()` 方法：复用模式下只断开 WebSocket 连接，不删除 Pod
    - 新增 setReuseMode/isReuseMode 方法
    - close() 中根据 reuseMode 决定是否调用 cleanupPod
    - _Requirements: 5.1_
  - [x] 3.3 编写 K8sRuntimeAdapter 复用模式属性测试
    - **Property 6: 复用模式 close 不删除 Pod**
    - **Validates: Requirements 5.1**

- [x] 4. Checkpoint - 确保后端核心逻辑测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 5. 修改 AcpHandshakeInterceptor 和 AcpWebSocketHandler
  - [x] 5.1 在 `AcpHandshakeInterceptor.beforeHandshake` 中增加 sandboxMode 参数解析
    - 从查询参数中提取 sandboxMode，存入 session attributes
    - _Requirements: 2.1_
  - [x] 5.2 修改 `AcpWebSocketHandler.afterConnectionEstablished`，根据 sandboxMode 路由到 PodReuseManager
    - K8s 运行时 + sandboxMode=user（或默认）→ 通过 PodReuseManager.acquirePod 获取 Pod
    - 创建 K8sRuntimeAdapter 并调用 startWithExistingPod
    - 维护 sessionId → userId/sandboxMode 映射
    - _Requirements: 2.2, 2.3_
  - [x] 5.3 修改 `AcpWebSocketHandler.cleanup`，在用户级沙箱模式下调用 PodReuseManager.releasePod
    - _Requirements: 5.1, 5.2_
  - [x] 5.4 编写 AcpHandshakeInterceptor sandboxMode 解析属性测试
    - **Property 2: sandboxMode 参数解析与回退**
    - **Validates: Requirements 2.1, 2.3**
  - [x] 5.5 编写 AcpWebSocketHandler 路由逻辑单元测试
    - 测试 sandboxMode=user 走复用路径
    - 测试 sandboxMode 缺失时的默认行为
    - _Requirements: 2.2, 2.3_

- [x] 6. 修改 AcpProperties 配置
  - [x] 6.1 在 `AcpProperties` 中新增 K8s 沙箱相关配置项
    - `acp.k8s.reuse-pod-idle-timeout`：空闲超时秒数，默认 1800
    - 在 `application.yml` 中添加对应配置
    - _Requirements: 5.3, 5.4_

- [x] 7. 前端：修改运行时选择器支持沙箱模式
  - [x] 7.1 在 `types/runtime.ts` 中新增 `SandboxMode` 类型，在 `WsUrlParams` 中新增 `sandboxMode` 字段
    - _Requirements: 1.6_
  - [x] 7.2 修改 `buildAcpWsUrl` 函数，支持 sandboxMode 查询参数
    - _Requirements: 1.6_
  - [x] 7.3 修改 `RuntimeSelector` 组件，当选中 K8s 时展示沙箱模式子选项
    - 用户级沙箱：可选，默认选中
    - 会话级沙箱：标记"即将推出"，disabled
    - _Requirements: 1.1, 1.2, 1.3, 1.4_
  - [x] 7.4 修改 `useRuntimeSelection` hook，增加 sandboxMode 状态管理和 localStorage 持久化
    - _Requirements: 1.4, 1.5_
  - [x] 7.5 修改 `Coding.tsx` 中的 `buildWsUrl` 和 `handleRuntimeChange`，将 sandboxMode 传递到 WebSocket URL
    - _Requirements: 1.6_
  - [x] 7.6 编写 buildAcpWsUrl sandboxMode 属性测试
    - **Property 1: WebSocket URL 构建包含 sandboxMode 参数**
    - **Validates: Requirements 1.6**
  - [x] 7.7 编写 RuntimeSelector 沙箱模式子选项单元测试
    - 测试 K8s 选中时展示子选项
    - 测试会话级沙箱 disabled 状态
    - 测试默认选中用户级沙箱
    - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 8. Final checkpoint - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的任务为可选测试任务，可跳过以加速 MVP
- 每个任务引用了具体的需求编号以保证可追溯性
- Checkpoint 任务确保增量验证
- 属性测试使用 jqwik（Java）和 fast-check（TypeScript）
- 单元测试覆盖具体边界条件和错误场景
