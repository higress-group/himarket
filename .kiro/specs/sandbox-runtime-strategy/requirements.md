# 需求文档

## 简介

本需求文档描述对 HiMarket 沙箱运行时架构的根本性重构。核心理念是：本地 Mac 是一种沙箱，K8s Pod 是一种沙箱，未来的 E2B 也是一种沙箱——它们共享相同的初始化流水线、通信协议和生命周期管理。

当前架构中，`LocalRuntimeAdapter` 通过 `ProcessBuilder` 直接启动 CLI 子进程，而 `K8sRuntimeAdapter` 通过 Sidecar WebSocket 桥接 CLI。两条路径的初始化逻辑、配置注入方式、文件操作方式完全不同，导致本地模式和沙箱模式的行为不一致，`AcpWebSocketHandler` 中存在大量类型分支。

重构目标：
1. 引入统一的 `SandboxProvider` 抽象层，所有沙箱类型实现相同接口
2. 本地也启动 Sidecar Server，使本地流程与 K8s 沙箱完全一致
3. 所有文件操作（writeFile/readFile）统一走 Sidecar HTTP API，Java 后端不直接操作文件系统
4. `SandboxInitPipeline` 流水线模式，5 个阶段有序执行，带重试和就绪验证
5. 消除 `AcpWebSocketHandler` 中的类型分支，面向 `SandboxProvider` 接口编程

## 术语表

- **SandboxProvider**：统一沙箱提供者接口，抽象不同沙箱环境（本地 Mac、K8s Pod、E2B）的差异，为 SandboxInitPipeline 提供统一的操作契约
- **SandboxType**：沙箱类型枚举，包含 LOCAL、K8S、E2B 三种类型
- **SandboxInfo**：沙箱实例信息记录，由 SandboxProvider.acquire() 返回，包含连接沙箱所需的地址、端口、工作空间路径等
- **SandboxConfig**：沙箱创建/获取配置，统一各沙箱类型的配置参数
- **SandboxInitPipeline**：统一初始化流水线，按顺序执行注册的 InitPhase，对所有沙箱类型通用
- **InitPhase**：初始化阶段接口，每个阶段通过 InitContext.getProvider() 获取 SandboxProvider 执行操作
- **InitContext**：初始化上下文，各阶段通过此对象共享数据，持有 SandboxProvider 引用
- **InitResult**：初始化结果记录，包含成功/失败状态、各阶段耗时和事件日志
- **SandboxProviderRegistry**：提供者注册中心，根据 SandboxType 查找对应的 Provider 实现
- **Sidecar_Server**：运行在沙箱内的 Node.js 服务，提供 WebSocket CLI 桥接和文件操作 HTTP API
- **Sidecar_HTTP_API**：Sidecar Server 提供的文件操作 HTTP 端点，包括 /files/write、/files/read、/files/mkdir、/files/exists
- **LocalSandboxProvider**：本地沙箱提供者，在本地 Mac 上启动 Sidecar Server 进程
- **K8sSandboxProvider**：K8s 沙箱提供者，复用 PodReuseManager 管理 Pod 生命周期
- **RetryPolicy**：重试策略记录，定义最大重试次数、初始延迟、退避倍数和最大延迟
- **ConfigFile**：配置文件写入记录，包含路径、内容、SHA-256 哈希和配置类型
- **CliConfigGenerator**：CLI 配置生成器，根据不同 CLI 工具生成对应的配置文件内容

## 需求

### 需求 1：SandboxProvider 统一抽象接口

**用户故事：** 作为平台开发者，我希望有一个统一的沙箱提供者接口，以便所有沙箱类型（本地、K8s、E2B）共享相同的操作契约，上层代码无需感知底层沙箱实现。

#### 验收标准

1. THE SandboxProvider SHALL 定义 acquire(SandboxConfig) 方法，返回 SandboxInfo 实例，用于获取或创建沙箱
2. THE SandboxProvider SHALL 定义 release(SandboxInfo) 方法，用于释放沙箱资源
3. THE SandboxProvider SHALL 定义 healthCheck(SandboxInfo) 方法，通过 Sidecar_HTTP_API 验证沙箱文件系统可读写
4. THE SandboxProvider SHALL 定义 writeFile(SandboxInfo, relativePath, content) 方法，通过 Sidecar_HTTP_API 的 POST /files/write 端点写入文件
5. THE SandboxProvider SHALL 定义 readFile(SandboxInfo, relativePath) 方法，通过 Sidecar_HTTP_API 的 POST /files/read 端点读取文件
6. THE SandboxProvider SHALL 定义 connectSidecar(SandboxInfo, RuntimeConfig) 方法，建立到 Sidecar_Server 的 WebSocket 连接并返回 RuntimeAdapter
7. THE SandboxProvider SHALL 定义 getType() 方法，返回该提供者对应的 SandboxType
8. THE SandboxProviderRegistry SHALL 根据 SandboxType 查找并返回对应的 SandboxProvider 实现
9. IF 请求的 SandboxType 未注册，THEN THE SandboxProviderRegistry SHALL 抛出 IllegalArgumentException 并包含未知类型信息

### 需求 2：本地沙箱提供者（LocalSandboxProvider）

**用户故事：** 作为平台开发者，我希望本地开发时也启动 Sidecar Server，使本地流程与 K8s 沙箱完全一致，消除本地模式与远程模式的行为差异。

#### 验收标准

1. WHEN LocalSandboxProvider 执行 acquire 时，THE LocalSandboxProvider SHALL 启动本地 Sidecar_Server 进程（Node.js），并等待其 /health 端点可响应后返回 SandboxInfo
2. WHEN 已有可复用的 Sidecar_Server 进程存活时，THE LocalSandboxProvider SHALL 复用该进程而非启动新进程
3. WHEN LocalSandboxProvider 执行 writeFile 时，THE LocalSandboxProvider SHALL 通过本地 Sidecar_HTTP_API 的 POST /files/write 端点写入文件，不直接调用 Java Files API
4. WHEN LocalSandboxProvider 执行 readFile 时，THE LocalSandboxProvider SHALL 通过本地 Sidecar_HTTP_API 的 POST /files/read 端点读取文件，不直接调用 Java Files API
5. WHEN LocalSandboxProvider 执行 healthCheck 时，THE LocalSandboxProvider SHALL 通过 HTTP GET /health 验证本地 Sidecar_Server 进程存活且响应正常
6. WHEN LocalSandboxProvider 执行 connectSidecar 时，THE LocalSandboxProvider SHALL 建立到本地 Sidecar_Server 的 WebSocket 连接，通过 Sidecar 桥接 CLI 进程
7. WHEN LocalSandboxProvider 执行 release 且无其他会话使用该 Sidecar 时，THE LocalSandboxProvider SHALL 正确终止本地 Sidecar_Server 进程
8. THE LocalSandboxProvider SHALL 为每个用户维护独立的 Sidecar_Server 进程（不同端口）

### 需求 3：K8s 沙箱提供者（K8sSandboxProvider）

**用户故事：** 作为平台运维人员，我希望 K8s 沙箱的文件操作统一通过 Pod 内 Sidecar HTTP API 完成，不再使用 kubectl exec 写文件，以提升稳定性和性能。

#### 验收标准

1. WHEN K8sSandboxProvider 执行 acquire 时，THE K8sSandboxProvider SHALL 通过 PodReuseManager 获取或创建 Pod，并返回包含 Pod IP 和 Sidecar 端口的 SandboxInfo
2. WHEN K8sSandboxProvider 执行 writeFile 时，THE K8sSandboxProvider SHALL 通过 Pod 内 Sidecar_HTTP_API 的 POST /files/write 端点写入文件，不使用 kubectl exec
3. WHEN K8sSandboxProvider 执行 readFile 时，THE K8sSandboxProvider SHALL 通过 Pod 内 Sidecar_HTTP_API 的 POST /files/read 端点读取文件，不使用 kubectl exec
4. WHEN K8sSandboxProvider 执行 healthCheck 时，THE K8sSandboxProvider SHALL 通过 Pod 内 Sidecar 的 HTTP GET /health 验证可用性
5. WHEN K8sSandboxProvider 执行 connectSidecar 时，THE K8sSandboxProvider SHALL 建立到 Pod 内 Sidecar_Server 的 WebSocket 连接
6. WHEN K8sSandboxProvider 执行 release 时，THE K8sSandboxProvider SHALL 通过 PodReuseManager 释放 Pod（复用模式下仅断开连接）
7. IF Sidecar_HTTP_API 请求超时，THEN THE K8sSandboxProvider SHALL 在 10 秒内返回 IOException 并包含 Pod 标识信息

### 需求 4：SandboxInitPipeline 统一初始化流水线

**用户故事：** 作为平台开发者，我希望所有沙箱类型共享同一个初始化流水线，按固定顺序执行 5 个阶段，以确保初始化行为一致且可观测。

#### 验收标准

1. THE SandboxInitPipeline SHALL 按 InitPhase.order() 值升序依次执行注册的初始化阶段
2. WHEN 某个 InitPhase 的 shouldExecute 返回 false 时，THE SandboxInitPipeline SHALL 跳过该阶段并记录 PHASE_SKIP 事件
3. WHEN 某个 InitPhase 执行成功后，THE SandboxInitPipeline SHALL 调用该阶段的 verify() 方法验证就绪状态，verify 返回 true 后才执行下一阶段
4. WHEN 某个 InitPhase 执行失败且 RetryPolicy 允许重试时，THE SandboxInitPipeline SHALL 按退避策略重试，直到成功或达到最大重试次数
5. WHEN 某个 InitPhase 最终失败时，THE SandboxInitPipeline SHALL 返回包含失败阶段名称、错误信息和已完成阶段列表的 InitResult
6. THE SandboxInitPipeline SHALL 记录每个阶段的开始、完成、跳过、重试、失败事件及时间戳
7. WHEN 总执行时间超过 InitConfig.totalTimeout 时，THE SandboxInitPipeline SHALL 终止当前阶段并返回超时失败的 InitResult
8. THE SandboxInitPipeline SHALL 支持从指定阶段恢复执行（resumeFrom），用于部分失败后重试

### 需求 5：五个初始化阶段

**用户故事：** 作为平台开发者，我希望初始化流程分为明确的 5 个阶段，每个阶段职责单一，通过 SandboxProvider 接口执行操作，不直接依赖具体沙箱实现。

#### 验收标准

1. THE SandboxAcquirePhase（order=100）SHALL 调用 SandboxProvider.acquire() 获取沙箱实例，并将 SandboxInfo 存入 InitContext
2. WHEN SandboxAcquirePhase 获取沙箱失败时，THE SandboxAcquirePhase SHALL 快速失败且不重试
3. THE FileSystemReadyPhase（order=200）SHALL 调用 SandboxProvider.healthCheck() 验证沙箱文件系统可访问
4. WHEN FileSystemReadyPhase 验证就绪状态时，THE FileSystemReadyPhase SHALL 通过写入并读回临时文件来验证文件系统读写能力
5. THE ConfigInjectionPhase（order=300）SHALL 通过 SandboxProvider.writeFile() 将模型配置、MCP 配置、Skill 配置注入沙箱
6. WHEN ConfigInjectionPhase 写入配置文件后，THE ConfigInjectionPhase SHALL 通过 SandboxProvider.readFile() 读回内容进行验证
7. THE SidecarConnectPhase（order=400）SHALL 调用 SandboxProvider.connectSidecar() 建立 WebSocket 连接并将 RuntimeAdapter 存入 InitContext
8. THE CliReadyPhase（order=500）SHALL 等待 CLI 进程启动并通过 RuntimeAdapter 接收到首条 stdout 消息确认就绪
9. WHEN CliReadyPhase 等待超过 15 秒未收到 CLI 就绪信号时，THE CliReadyPhase SHALL 标记为失败且不重试

### 需求 6：Sidecar Server 文件操作 HTTP 端点

**用户故事：** 作为平台开发者，我希望 Sidecar Server 提供文件操作 HTTP API，使所有沙箱类型的文件读写统一通过这些端点完成，Java 后端不再直接操作文件系统。

#### 验收标准

1. THE Sidecar_Server SHALL 提供 POST /files/write 端点，接受 { path, content } 请求体，将内容写入工作空间内的指定路径并自动创建父目录
2. THE Sidecar_Server SHALL 提供 POST /files/read 端点，接受 { path } 请求体，返回 { content } 响应
3. THE Sidecar_Server SHALL 提供 POST /files/mkdir 端点，接受 { path } 请求体，递归创建目录
4. THE Sidecar_Server SHALL 提供 POST /files/exists 端点，接受 { path } 请求体，返回 { exists, isFile, isDirectory } 响应
5. WHEN /files/write 请求缺少 path 或 content 参数时，THE Sidecar_Server SHALL 返回 HTTP 400 状态码和错误描述
6. WHEN /files/read 请求的文件不存在时，THE Sidecar_Server SHALL 返回 HTTP 404 状态码和 ENOENT 错误信息
7. THE Sidecar_Server SHALL 通过 resolveSafePath 函数对所有文件路径进行安全校验，确保解析后的绝对路径始终在 WORKSPACE_ROOT 目录内
8. WHEN 文件路径包含路径遍历模式（如 ../）时，THE Sidecar_Server SHALL 拒绝请求并返回路径越界错误
9. WHEN 本地模式运行时，THE Sidecar_Server SHALL 仅监听 127.0.0.1，不暴露到外部网络

### 需求 7：AcpWebSocketHandler 改造

**用户故事：** 作为平台开发者，我希望 AcpWebSocketHandler 不再包含任何沙箱类型分支，统一通过 SandboxInitPipeline 和 SandboxProvider 处理所有沙箱类型。

#### 验收标准

1. WHEN 前端建立 WebSocket 连接时，THE AcpWebSocketHandler SHALL 通过 SandboxProviderRegistry 获取对应的 SandboxProvider，而非使用 if/else 类型分支
2. THE AcpWebSocketHandler SHALL 将所有沙箱类型的初始化委托给 SandboxInitPipeline.execute()，走统一的异步初始化路径
3. WHEN SandboxInitPipeline 返回成功结果时，THE AcpWebSocketHandler SHALL 从 InitContext 获取 RuntimeAdapter，订阅 stdout 并向前端发送 sandbox/status: ready 消息
4. WHEN SandboxInitPipeline 返回失败结果时，THE AcpWebSocketHandler SHALL 向前端发送包含失败阶段、错误信息和建议操作的 sandbox/status: error 消息
5. THE AcpWebSocketHandler SHALL 在初始化过程中向前端推送 sandbox/init-progress 消息，包含当前阶段、状态、进度百分比

### 需求 8：配置注入统一化

**用户故事：** 作为平台开发者，我希望所有沙箱类型的配置注入走完全相同的代码路径，通过 Sidecar HTTP API 写入配置文件，消除本地直接写文件和 K8s kubectl exec 写文件的差异。

#### 验收标准

1. THE ConfigInjectionPhase SHALL 使用 CliConfigGenerator 生成配置文件内容，然后通过 SandboxProvider.writeFile() 统一写入
2. WHEN 配置文件写入后，THE ConfigInjectionPhase SHALL 通过 SandboxProvider.readFile() 读回内容，与写入内容进行 SHA-256 哈希比较验证一致性
3. IF 写入后读回验证不一致，THEN THE ConfigInjectionPhase SHALL 按 RetryPolicy.fileOperation() 策略重试（最多 2 次，初始延迟 500ms）
4. THE ConfigInjectionPhase SHALL 支持注入模型配置（settings.json）、MCP 配置和 Skill 配置三种类型
5. WHEN 配置文件内容包含 API Key 等敏感信息时，THE ConfigInjectionPhase SHALL 仅在日志中记录文件路径和哈希值，不记录文件内容

### 需求 9：重试与错误处理

**用户故事：** 作为平台运维人员，我希望初始化流水线具备合理的重试机制和详细的错误诊断信息，以便快速定位和恢复故障。

#### 验收标准

1. THE RetryPolicy SHALL 支持配置最大重试次数、初始延迟、退避倍数和最大延迟
2. WHEN FileSystemReadyPhase 的 Sidecar /health 请求超时时，THE SandboxInitPipeline SHALL 按 defaultPolicy（3 次重试，1s 初始延迟，2.0 倍退避）重试
3. WHEN SidecarConnectPhase 的 WebSocket 连接失败时，THE SandboxInitPipeline SHALL 按策略（2 次重试，2s 初始延迟，2.0 倍退避，8s 最大延迟）重试
4. WHEN 初始化失败时，THE InitResult SHALL 包含失败阶段名称、错误信息、总耗时、各阶段耗时和完整事件日志
5. THE 错误通知 SHALL 包含 sandboxType、failedPhase、retryable 标志和 diagnostics（已完成阶段列表、总耗时、建议操作）

### 需求 10：SandboxInfo 与 SandboxConfig 数据模型

**用户故事：** 作为平台开发者，我希望沙箱实例信息和配置参数有清晰的数据模型，以便各组件之间传递完整的沙箱上下文。

#### 验收标准

1. THE SandboxInfo SHALL 包含 type（SandboxType）、sandboxId（唯一标识）、host（访问地址）、sidecarPort（Sidecar 端口）、workspacePath（工作空间路径）、reused（是否复用）和 metadata（扩展元数据）字段
2. THE SandboxInfo SHALL 提供 sidecarWsUri(command, args) 方法，构建 Sidecar WebSocket URI
3. THE SandboxConfig SHALL 包含 userId、type（SandboxType）、workspacePath、env（环境变量）以及各沙箱类型的特有配置字段
4. WHEN SandboxType 为 LOCAL 时，THE SandboxInfo 的 host SHALL 为 "localhost"，sandboxId 格式为 "local-{port}"
5. WHEN SandboxType 为 K8S 时，THE SandboxInfo 的 metadata SHALL 包含 podName 和 namespace 信息

