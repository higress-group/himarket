# 实现计划：统一沙箱运行时抽象

## 概述

按照设计文档的迁移策略分三个阶段实施：阶段一完成 Sidecar 文件操作端点和 SandboxProvider 抽象（K8s 先行）；阶段二实现本地沙箱统一；阶段三清理旧代码和预留扩展点。每个阶段结束有检查点确保增量验证。

## 任务

### 阶段一：Sidecar 文件操作端点 + SandboxProvider 抽象（K8s 先行）

- [x] 1. Sidecar Server 文件操作 HTTP 端点
  - [x] 1.1 实现 Sidecar 文件操作路由和路径安全校验
    - 在 `sandbox/sidecar-server/index.js` 中新增 `resolveSafePath()` 函数，确保路径在 WORKSPACE_ROOT 内
    - 实现 `POST /files/write` 端点：接受 `{ path, content }`，自动创建父目录，返回 `{ success: true }`
    - 实现 `POST /files/read` 端点：接受 `{ path }`，返回 `{ content }`，文件不存在返回 404
    - 实现 `POST /files/mkdir` 端点：接受 `{ path }`，递归创建目录
    - 实现 `POST /files/exists` 端点：接受 `{ path }`，返回 `{ exists, isFile, isDirectory }`
    - 所有端点参数缺失时返回 HTTP 400
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8_
  - [x] 1.2 实现 Sidecar 本地模式安全监听
    - 通过环境变量 `SIDECAR_MODE` 区分本地/K8s 模式
    - 本地模式仅监听 `127.0.0.1`，K8s 模式监听 `0.0.0.0`
    - _Requirements: 6.9_

  - [x] 1.3 编写 Sidecar 文件操作端点的属性基测试
    - **Property 9: 文件操作路径安全性** — 对任意包含 `../`、符号链接、绝对路径等路径遍历模式的输入，`resolveSafePath()` 必须拒绝并返回错误
    - **Property 10: Sidecar 文件操作幂等性** — 连续多次 `POST /files/write` 写入相同内容，最终文件状态与单次写入一致；`POST /files/mkdir` 对已存在目录返回成功
    - **Validates: Requirements 6.7, 6.8, 6.1, 6.3**
  - [x] 1.4 编写 Sidecar 文件操作端点的单元测试
    - 测试 /files/write 正常写入、参数缺失 400、写入失败 500
    - 测试 /files/read 正常读取、文件不存在 404、参数缺失 400
    - 测试 /files/mkdir 递归创建、已存在目录
    - 测试 /files/exists 文件存在/不存在/目录
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [x] 2. 核心数据模型和接口定义
  - [x] 2.1 创建 SandboxType 枚举和 SandboxInfo 记录
    - 在 `himarket-server/.../service/acp/runtime/` 下创建 `SandboxType.java` 枚举（LOCAL, K8S, E2B），包含 `fromValue()` 静态方法
    - 创建 `SandboxInfo.java` record，包含 type、sandboxId、host、sidecarPort、workspacePath、reused、metadata 字段
    - 实现 `sidecarWsUri(command, args)` 方法，构建 Sidecar WebSocket URI（含 URL 编码）
    - _Requirements: 10.1, 10.2, 10.4, 10.5_
  - [x] 2.2 创建 SandboxConfig 记录
    - 创建 `SandboxConfig.java` record，包含 userId、type、workspacePath、env、k8sConfigId、resources、e2bTemplate、localSidecarPort 字段
    - _Requirements: 10.3_
  - [x] 2.3 定义 SandboxProvider 接口
    - 创建 `SandboxProvider.java` 接口，定义 getType()、acquire()、release()、healthCheck()、writeFile()、readFile()、connectSidecar()、getSidecarUri() 方法
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_
  - [x] 2.4 创建 SandboxProviderRegistry
    - 创建 `SandboxProviderRegistry.java`，通过 Spring 自动注入 `List<SandboxProvider>`，按 SandboxType 建立映射
    - 未注册类型抛出 `IllegalArgumentException`
    - _Requirements: 1.8, 1.9_

  - [x] 2.5 编写 SandboxInfo 的属性基测试
    - **Property 11: SandboxInfo 构建正确性** — 对任意 command 和 args 组合，`sidecarWsUri()` 构建的 URI 包含正确的 host、port、command 和 URL 编码的 args；LOCAL 类型 host 为 "localhost"，sandboxId 格式为 "local-{port}"；K8S 类型 metadata 包含 podName 和 namespace
    - **Validates: Requirements 10.2, 10.4, 10.5**
  - [x] 2.6 编写 SandboxProviderRegistry 的单元测试
    - 测试正常查找、未注册类型抛异常、supportedTypes() 返回所有已注册类型
    - _Requirements: 1.8, 1.9_

- [x] 3. 初始化流水线框架
  - [x] 3.1 创建流水线辅助类型
    - 创建 `InitPhaseException.java`（包含 phaseName、retryable 字段）
    - 创建 `RetryPolicy.java` record（maxRetries、initialDelay、backoffMultiplier、maxDelay），提供 none()、defaultPolicy()、fileOperation() 工厂方法
    - 创建 `InitConfig.java` record（totalTimeout、failFast、enableVerification、enableProgressNotify），提供 defaults() 工厂方法
    - 创建 `InitResult.java` record（success、failedPhase、errorMessage、totalDuration、phaseDurations、events），提供 success() 和 failure() 工厂方法
    - 创建 `InitEvent.java` record 和 `EventType` 枚举、`PhaseStatus` 枚举
    - 创建 `ConfigFile.java` record（relativePath、content、contentHash、ConfigType 枚举）
    - _Requirements: 9.1, 9.4, 9.5_
  - [x] 3.2 定义 InitPhase 接口和 InitContext 上下文
    - 创建 `InitPhase.java` 接口，定义 name()、order()、shouldExecute()、execute()、verify()、retryPolicy() 方法
    - 创建 `InitContext.java`，持有 SandboxProvider、userId、SandboxConfig、RuntimeConfig、CliProviderConfig、CliSessionConfig、WebSocketSession 等输入参数，以及 SandboxInfo、RuntimeAdapter、injectedConfigs 等阶段产出，和 phaseStatuses、events 状态追踪
    - _Requirements: 4.1, 4.2, 4.3, 5.1_
  - [x] 3.3 实现 SandboxInitPipeline 编排逻辑
    - 实现 `execute(InitContext)` 方法：按 order 升序执行各阶段，支持 shouldExecute 跳过、verify 验证、重试退避、总超时终止
    - 实现 `resumeFrom(InitContext, fromPhase)` 方法：从指定阶段恢复执行
    - 实现 `executeWithRetry(phase, context)` 私有方法：按 RetryPolicy 执行重试逻辑
    - 每个阶段记录 PHASE_START、PHASE_COMPLETE、PHASE_SKIP、PHASE_RETRY、PHASE_FAIL 事件
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8_

  - [x] 3.4 编写 SandboxInitPipeline 的属性基测试
    - **Property 1: 沙箱类型无关性** — 对任意合法初始化配置，Pipeline 的阶段执行顺序和编排行为与 SandboxType 无关，给定相同 InitPhase 列表，不同 Provider 实现的编排行为完全一致
    - **Validates: Requirements 4.1, 4.2, 4.3**
    - **Property 3: 阶段执行顺序保证** — 各阶段实际执行顺序严格按 order() 升序排列，后续阶段不会在前置阶段 verify() 返回 true 之前被调用，shouldExecute() 返回 false 的阶段被跳过并记录 PHASE_SKIP 事件
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.6**
    - **Property 4: 失败结果完整性** — 阶段失败时 InitResult 包含失败阶段名称、错误信息、总耗时、各阶段耗时和完整事件日志
    - **Validates: Requirements 4.5, 9.4, 9.5**
    - **Property 6: 超时保证** — 总耗时不超过 InitConfig.totalTimeout + 5s 清理时间
    - **Validates: Requirements 4.7**
  - [x] 3.5 编写 SandboxInitPipeline 的单元测试
    - 使用 mock SandboxProvider 验证阶段顺序执行
    - 测试 shouldExecute 返回 false 时跳过阶段
    - 测试阶段失败后快速退出
    - 测试重试逻辑（第 N 次成功）
    - 测试总超时终止
    - 测试 resumeFrom 恢复执行
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.7, 4.8_

- [x] 4. 检查点 — 阶段一基础框架验证
  - 确保所有测试通过，ask the user if questions arise.

- [x] 5. 实现五个初始化阶段
  - [x] 5.1 实现 SandboxAcquirePhase
    - order=100，调用 `provider.acquire(sandboxConfig)` 获取沙箱实例
    - 将 SandboxInfo 存入 InitContext
    - verify: 检查 sandboxInfo 非空且 host 非空
    - retryPolicy: none()（获取失败不重试）
    - _Requirements: 5.1, 5.2_
  - [x] 5.2 实现 FileSystemReadyPhase
    - order=200，调用 `provider.healthCheck(sandboxInfo)` 验证文件系统可访问
    - verify: 通过 writeFile + readFile 写入并读回临时文件验证读写能力
    - retryPolicy: defaultPolicy()（3 次重试，1s 初始延迟，2.0 倍退避）
    - _Requirements: 5.3, 5.4, 9.2_
  - [x] 5.3 实现 ConfigInjectionPhase
    - order=300，shouldExecute 检查 sessionConfig 和 providerConfig.isSupportsCustomModel()
    - 使用 CliConfigGenerator 生成配置内容，通过 `provider.writeFile()` 写入
    - 每个文件写入后通过 `provider.readFile()` 读回，SHA-256 哈希比较验证一致性
    - 支持模型配置（settings.json）、MCP 配置、Skill 配置三种类型
    - 敏感信息仅记录文件路径和哈希值，不记录内容
    - retryPolicy: fileOperation()（2 次重试，500ms 初始延迟）
    - _Requirements: 5.5, 5.6, 8.1, 8.2, 8.3, 8.4, 8.5_

  - [x] 5.4 实现 SidecarConnectPhase
    - order=400，调用 `provider.connectSidecar(sandboxInfo, runtimeConfig)` 建立 WebSocket 连接
    - 将 RuntimeAdapter 存入 InitContext
    - verify: 检查 adapter 非空且 status == RUNNING
    - retryPolicy: 2 次重试，2s 初始延迟，2.0 倍退避，8s 最大延迟
    - _Requirements: 5.7, 9.3_
  - [x] 5.5 实现 CliReadyPhase
    - order=500，监听 RuntimeAdapter.stdout() 首条消息确认 CLI 就绪
    - 超时 15 秒未收到就绪信号标记失败
    - retryPolicy: none()
    - _Requirements: 5.8, 5.9_
  - [x] 5.6 编写 ConfigInjectionPhase 的属性基测试
    - **Property 2: 配置写入往返一致性** — 对任意合法配置内容（含特殊字符、Unicode），writeFile 后 readFile 读回内容 SHA-256 哈希相等
    - **Validates: Requirements 5.4, 5.6, 8.2**
    - **Property 5: 重试幂等性** — 对支持重试的阶段，重试执行的最终效果与首次成功执行一致，多次 writeFile 同一文件不产生重复内容
    - **Validates: Requirements 4.4, 8.3**
  - [x] 5.7 编写五个初始化阶段的单元测试
    - 使用 mock SandboxProvider 测试各阶段的 execute、verify、shouldExecute 逻辑
    - 测试 SandboxAcquirePhase 获取失败快速退出
    - 测试 FileSystemReadyPhase 健康检查失败和读写验证
    - 测试 ConfigInjectionPhase 写入后读回验证不一致时重试
    - 测试 SidecarConnectPhase WebSocket 连接失败重试
    - 测试 CliReadyPhase 15 秒超时
    - _Requirements: 5.1-5.9, 8.1-8.5, 9.1-9.3_

- [x] 6. 实现 K8sSandboxProvider
  - [x] 6.1 实现 K8sSandboxProvider 核心逻辑
    - 创建 `K8sSandboxProvider.java`，实现 SandboxProvider 接口
    - acquire: 复用 PodReuseManager.acquirePod() 获取 Pod，构建 SandboxInfo（含 podName、namespace、podIp metadata）
    - release: 通过 PodReuseManager 释放 Pod
    - writeFile/readFile: 通过 Pod 内 Sidecar HTTP API（POST /files/write、/files/read），不使用 kubectl exec
    - healthCheck: 通过 Sidecar HTTP GET /health 验证
    - connectSidecar: 复用现有 K8sRuntimeAdapter 的 WebSocket 连接逻辑
    - HTTP 请求超时 10 秒，IOException 包含 Pod 标识信息
    - 使用 `java.net.http.HttpClient` 调用 Sidecar HTTP API
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_
  - [x] 6.2 编写 K8sSandboxProvider 的属性基测试
    - **Property 8: Provider 接口契约一致性** — acquire 成功后 healthCheck 返回 true；writeFile 后 readFile 返回相同内容；connectSidecar 成功后 RuntimeAdapter.isAlive() 为 true
    - **Validates: Requirements 3.2, 3.3, 3.4, 3.5**
  - [x] 6.3 编写 K8sSandboxProvider 的单元测试
    - 使用 mock PodReuseManager 和 mock HttpClient 测试
    - 测试 acquire 构建正确的 SandboxInfo
    - 测试 writeFile/readFile 通过 HTTP API 而非 kubectl exec
    - 测试 HTTP 超时返回 IOException
    - _Requirements: 3.1-3.7_

- [x] 7. 改造 AcpWebSocketHandler（K8s 路径）
  - [x] 7.1 重构 AcpWebSocketHandler 使用 Pipeline + K8sSandboxProvider
    - 注入 SandboxProviderRegistry 和 SandboxInitPipeline
    - 在 afterConnectionEstablished 中：通过 Registry 获取 Provider，构建 InitContext，提交 pipeline.execute() 到线程池
    - Pipeline 成功：从 InitContext 获取 RuntimeAdapter，订阅 stdout，发送 sandbox/status: ready
    - Pipeline 失败：发送 sandbox/status: error（含 failedPhase、errorMessage、retryable、diagnostics）
    - 在初始化过程中推送 sandbox/init-progress 消息（当前阶段、状态、进度百分比）
    - K8s 路径先行改造，本地路径暂保留原逻辑
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 8. 检查点 — 阶段一 K8s 路径验证
  - 确保 K8s 沙箱通过 Pipeline + K8sSandboxProvider 正常初始化，文件操作走 Sidecar HTTP API。确保所有测试通过，ask the user if questions arise.


### 阶段二：本地沙箱统一

- [x] 9. 实现 LocalSandboxProvider
  - [x] 9.1 创建 LocalSidecarProcess 封装和本地 Sidecar 进程管理
    - 创建 `LocalSidecarProcess.java` record（process、port、startedAt），实现 isAlive() 和 stop() 方法
    - stop() 先 destroy()，5 秒后 destroyForcibly()
    - _Requirements: 2.7_
  - [x] 9.2 实现 LocalSandboxProvider 核心逻辑
    - 创建 `LocalSandboxProvider.java`，实现 SandboxProvider 接口
    - 维护 `ConcurrentHashMap<String, LocalSidecarProcess>` 用户→Sidecar 进程映射
    - acquire: 检查可复用进程（isAlive），复用时返回 reused=true；否则启动新 Sidecar 进程（`node sidecar-server/index.js`），设置 SIDECAR_PORT、ALLOWED_COMMANDS 环境变量，轮询 /health 等待就绪（超时 10s）
    - release: 无其他会话使用时终止 Sidecar 进程
    - writeFile/readFile: 通过本地 Sidecar HTTP API（POST /files/write、/files/read），不直接调用 Java Files API
    - healthCheck: HTTP GET /health 验证 Sidecar 存活
    - connectSidecar: 建立到本地 Sidecar 的 WebSocket 连接（ws://localhost:{port}）
    - 每个用户独立 Sidecar 进程（不同端口）
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8_
  - [x] 9.3 编写 LocalSandboxProvider 的属性基测试
    - **Property 7: 本地 Sidecar 进程生命周期** — acquire 成功后 Sidecar 进程存活且 /health 可响应；同一用户连续两次 acquire 返回 reused=true 且端口不变；不同用户返回不同端口；release 后无其他会话时进程被终止
    - **Validates: Requirements 2.1, 2.2, 2.7, 2.8**
    - **Property 8: Provider 接口契约一致性（Local）** — acquire 成功后 healthCheck 返回 true；writeFile 后 readFile 返回相同内容（通过 Sidecar HTTP API）
    - **Validates: Requirements 2.3, 2.4, 2.5, 2.6**
  - [x] 9.4 编写 LocalSandboxProvider 的单元测试
    - 测试 Sidecar 进程启动和复用逻辑
    - 测试 writeFile/readFile 通过 HTTP API 而非 Java Files API
    - 测试 release 正确终止进程
    - 测试不同用户独立端口
    - _Requirements: 2.1-2.8_

- [x] 10. 改造 AcpWebSocketHandler（本地路径统一）
  - [x] 10.1 将本地路径改为 Pipeline + LocalSandboxProvider
    - 删除 AcpWebSocketHandler 中的 `if (runtimeType == RuntimeType.K8S)` 分支
    - 本地路径也走 SandboxProviderRegistry → LocalSandboxProvider → SandboxInitPipeline
    - 所有沙箱类型走同一个异步初始化路径
    - _Requirements: 7.1, 7.2_
  - [x] 10.2 编写 AcpWebSocketHandler 改造后的单元测试
    - 验证不再有类型分支，所有类型走统一 Pipeline 路径
    - 测试 Pipeline 成功时发送 sandbox/status: ready
    - 测试 Pipeline 失败时发送 sandbox/status: error（含完整诊断信息）
    - 测试初始化过程中推送 sandbox/init-progress 消息
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 11. 检查点 — 阶段二本地沙箱统一验证
  - 确保本地模式与 K8s 模式行为完全一致（两者都通过 Sidecar HTTP API 操作文件），AcpWebSocketHandler 无类型分支。确保所有测试通过，ask the user if questions arise.


### 阶段三：清理与扩展

- [x] 12. 清理旧代码
  - [x] 12.1 废弃 RuntimeFactory 和 RuntimeType
    - 标记 `RuntimeFactory.java` 为 @Deprecated，将其调用方迁移到 SandboxProviderRegistry
    - 标记 `RuntimeType.java` 为 @Deprecated，将其引用迁移到 SandboxType
    - 更新 `RuntimeSelectorConfig.java` 使用 SandboxType
    - _Requirements: 1.8_
  - [x] 12.2 废弃 PodFileSystemAdapter 的 kubectl exec 写文件逻辑
    - 标记 `PodFileSystemAdapter.java` 中 kubectl exec 写文件相关方法为 @Deprecated
    - 文件操作已统一走 Sidecar HTTP API，kubectl exec 写文件不再需要
    - _Requirements: 3.2, 3.3_
  - [x] 12.3 废弃 LocalRuntimeAdapter 的直接进程启动逻辑
    - 标记 `LocalRuntimeAdapter.java` 为 @Deprecated
    - 其 ProcessBuilder 直接启动 CLI 的逻辑被 LocalSandboxProvider + Sidecar 替代
    - 标记 `LocalFileSystemAdapter.java` 为 @Deprecated（文件操作已走 Sidecar HTTP API）
    - _Requirements: 2.3, 2.4_

- [x] 13. 预留 E2B 扩展点
  - [x] 13.1 创建 E2BSandboxProvider 骨架
    - 创建 `E2BSandboxProvider.java` 骨架类，实现 SandboxProvider 接口
    - 所有方法抛出 `UnsupportedOperationException("E2B 沙箱尚未实现")`
    - 不注册为 Spring Bean（不加 @Component），仅作为扩展点示例
    - 添加 TODO 注释说明未来实现方向
    - _Requirements: 1.7（E2B 类型已在 SandboxType 枚举中定义）_

- [x] 14. 最终检查点 — 全量验证
  - 确保所有测试通过，旧代码已标记 @Deprecated，新架构完整可用。ask the user if questions arise.

## 备注

- 标记 `*` 的任务为可选测试任务，可跳过以加速 MVP
- 每个任务引用了具体的需求编号，确保可追溯性
- 检查点确保增量验证，避免大规模回归
- 属性基测试使用 jqwik 框架，验证设计文档中的正确性属性
- 单元测试和属性基测试互补：单元测试验证具体场景，属性基测试验证通用不变量
- 阶段一先改造 K8s 路径，降低风险；阶段二再统一本地路径
