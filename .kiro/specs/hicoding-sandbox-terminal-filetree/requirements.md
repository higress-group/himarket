# 需求文档

## 简介

HiCoding（Coding 页面）在选择 K8s 沙箱运行时后，终端（TerminalPanel）和文件树/文件内容（WorkspaceController）仍然连接本地服务器。本需求将终端和文件操作模块改造为根据 runtime 类型动态路由：本地模式保持现有行为，K8s 模式下终端通过 K8s Exec API 在 Pod 内 spawn 交互式 shell，文件操作通过 Sidecar HTTP API 读写 Pod 内文件。同时清理已废弃的 PodFileSystemAdapter 代码。

## 术语表

- **TerminalWebSocketHandler**：后端 WebSocket 处理器，负责终端连接的建立、输入输出转发和生命周期管理
- **TerminalBackend**：终端后端抽象接口，统一本地 PTY 和 K8s Exec 两种终端实现
- **LocalTerminalBackend**：本地终端后端实现，封装 pty4j PTY 进程
- **K8sTerminalBackend**：K8s 终端后端实现，通过 fabric8 K8s Exec API 在 Pod 内执行交互式 shell
- **WorkspaceController**：后端控制器，提供文件树、文件内容读取、文件变更等 REST API
- **RemoteWorkspaceService**：新增的后端 Service，封装通过 Sidecar HTTP API 操作 Pod 内文件的逻辑
- **Sidecar_HTTP_API**：Pod 内 Sidecar 容器提供的 HTTP 接口，用于文件读写和目录树获取
- **PodReuseManager**：后端 Pod 复用管理器，负责获取用户关联的 Pod 信息（podName、podIp、namespace）
- **TerminalPanel**：前端终端面板组件，基于 xterm.js 渲染终端界面
- **workspaceApi**：前端 API 模块，封装文件树、文件内容、文件变更等 HTTP 请求
- **PodFileSystemAdapter**：已废弃的文件系统适配器类，通过 K8s exec 执行文件操作（标记 @deprecated，待清理）
- **runtime 参数**：运行时类型标识，值为 "local"（本地）或 "k8s"（K8s 沙箱），用于后端路由决策

## 需求

### 需求 1：终端后端抽象与 runtime 路由

**用户故事：** 作为开发者，我希望终端连接能根据 runtime 类型自动选择本地 PTY 或 K8s Pod shell，以便在 K8s 沙箱中获得交互式终端体验。

#### 验收标准

1. WHEN WebSocket 连接建立且 session attributes 中 runtime 为 "k8s", THE TerminalWebSocketHandler SHALL 创建 K8sTerminalBackend 实例作为终端后端
2. WHEN WebSocket 连接建立且 session attributes 中 runtime 为 "local" 或缺失, THE TerminalWebSocketHandler SHALL 创建 LocalTerminalBackend 实例作为终端后端
3. THE TerminalBackend 接口 SHALL 定义 start、write、resize、output、isAlive、close 六个方法，统一两种终端实现的调用方式
4. THE LocalTerminalBackend SHALL 封装现有 TerminalProcess 逻辑，保持与改造前完全一致的行为

### 需求 2：K8s 终端后端实现

**用户故事：** 作为开发者，我希望在 K8s 沙箱模式下能通过终端在 Pod 内执行命令，以便在隔离环境中进行开发调试。

#### 验收标准

1. WHEN K8sTerminalBackend 的 start 方法被调用, THE K8sTerminalBackend SHALL 通过 fabric8 K8s Exec API 在指定 Pod 容器内启动交互式 shell（/bin/sh -l），并启用 TTY 模式
2. WHEN 用户通过 WebSocket 发送输入数据, THE K8sTerminalBackend SHALL 将数据完整写入 ExecWatch 的 stdin 流
3. WHEN Pod 内 shell 产生 stdout 输出, THE K8sTerminalBackend SHALL 通过 output() 响应式流将输出完整传递到 WebSocket
4. WHEN K8sTerminalBackend 的 resize 方法被调用, THE K8sTerminalBackend SHALL 通过 ExecWatch 的 resize 方法调整 Pod 内终端大小
5. WHEN K8sTerminalBackend 的 close 方法被调用, THE K8sTerminalBackend SHALL 关闭 ExecWatch 连接并释放相关资源

### 需求 3：文件操作 runtime 路由

**用户故事：** 作为开发者，我希望文件树和文件内容 API 能根据 runtime 类型自动选择本地文件系统或 K8s Pod 内文件，以便在 K8s 沙箱中浏览和编辑代码。

#### 验收标准

1. WHEN 请求 /workspace/tree 且 runtime 参数为 "k8s", THE WorkspaceController SHALL 通过 RemoteWorkspaceService 从 Sidecar_HTTP_API 获取 Pod 内目录树
2. WHEN 请求 /workspace/tree 且 runtime 参数为 "local" 或缺失, THE WorkspaceController SHALL 从本地文件系统获取目录树，保持现有行为不变
3. WHEN 请求 /workspace/file 且 runtime 参数为 "k8s", THE WorkspaceController SHALL 通过 RemoteWorkspaceService 从 Sidecar_HTTP_API 读取 Pod 内文件内容
4. WHEN 请求 /workspace/file 且 runtime 参数为 "local" 或缺失, THE WorkspaceController SHALL 从本地文件系统读取文件内容，保持现有行为不变
5. WHEN 请求 /workspace/changes 且 runtime 参数为 "k8s", THE WorkspaceController SHALL 通过 RemoteWorkspaceService 从 Sidecar_HTTP_API 获取 Pod 内文件变更
6. WHEN 请求 /workspace/changes 且 runtime 参数为 "local" 或缺失, THE WorkspaceController SHALL 从本地文件系统获取文件变更，保持现有行为不变

### 需求 4：RemoteWorkspaceService 实现

**用户故事：** 作为开发者，我希望 K8s 文件操作逻辑封装在独立 Service 中，以便 WorkspaceController 保持简洁且文件操作逻辑可复用。

#### 验收标准

1. WHEN RemoteWorkspaceService 的 getDirectoryTree 方法被调用, THE RemoteWorkspaceService SHALL 通过 PodReuseManager 获取用户 Pod 信息，并调用 Sidecar_HTTP_API 的 /files/list 端点获取目录树
2. WHEN RemoteWorkspaceService 的 readFile 方法被调用, THE RemoteWorkspaceService SHALL 通过 PodReuseManager 获取用户 Pod 信息，并调用 Sidecar_HTTP_API 的 /files/read 端点读取文件内容
3. THE RemoteWorkspaceService SHALL 将 Sidecar_HTTP_API 返回的文件列表转换为前端期望的树形结构格式
4. WHEN PodReuseManager 返回的 PodEntry 包含 serviceIp, THE RemoteWorkspaceService SHALL 优先使用 serviceIp 作为 Sidecar 访问地址；否则使用 podIp

### 需求 5：Sidecar /files/list 端点扩展

**用户故事：** 作为开发者，我希望 Sidecar 提供目录树查询端点，以便后端能通过统一的 HTTP API 获取 Pod 内文件结构。

#### 验收标准

1. WHEN 收到 POST /files/list 请求且请求体包含 path 和 depth 参数, THE Sidecar_HTTP_API SHALL 返回指定路径下指定深度的目录树 JSON 结构
2. THE Sidecar_HTTP_API 的 /files/list 响应 SHALL 包含每个条目的 name、type（dir 或 file）字段，目录类型条目包含 children 数组
3. WHEN 请求的 path 不存在, THE Sidecar_HTTP_API SHALL 返回 404 状态码和描述性错误信息

### 需求 6：前端 TerminalPanel runtime 参数传递

**用户故事：** 作为开发者，我希望前端终端组件能将当前 runtime 类型传递给后端，以便后端创建正确类型的终端后端。

#### 验收标准

1. THE TerminalPanel 组件 SHALL 接收 runtime prop，并在构建 WebSocket URL 时将 runtime 作为查询参数附加
2. WHEN runtime prop 为非空值, THE buildTerminalWsUrl 函数 SHALL 在生成的 URL 中包含 runtime 查询参数且值与输入一致
3. WHEN runtime prop 为 undefined, THE buildTerminalWsUrl 函数 SHALL 生成不包含 runtime 查询参数的 URL
4. WHEN runtime prop 值发生变化, THE TerminalPanel SHALL 断开当前 WebSocket 连接并使用新 URL 重新建立连接

### 需求 7：前端 workspaceApi runtime 参数传递

**用户故事：** 作为开发者，我希望前端文件操作 API 能将当前 runtime 类型传递给后端，以便后端从正确的数据源获取文件数据。

#### 验收标准

1. THE fetchDirectoryTree 函数 SHALL 接收可选的 runtime 参数，并在 HTTP 请求中将 runtime 作为查询参数附加
2. THE fetchArtifactContent 函数 SHALL 接收可选的 runtime 参数，并在 HTTP 请求中将 runtime 作为查询参数附加
3. THE fetchWorkspaceChanges 函数 SHALL 接收可选的 runtime 参数，并在 HTTP 请求中将 runtime 作为查询参数附加
4. WHEN runtime 参数为 undefined, THE workspaceApi 函数 SHALL 发送不包含 runtime 查询参数的请求

### 需求 8：Coding.tsx runtime 状态传递

**用户故事：** 作为开发者，我希望 Coding 页面将当前 runtime 状态传递给终端和文件操作组件，以便所有子组件使用一致的运行时类型。

#### 验收标准

1. THE Coding.tsx SHALL 将 currentRuntimeRef 的值传递给 TerminalPanel 的 runtime prop
2. THE Coding.tsx SHALL 在调用 fetchDirectoryTree 时传递 currentRuntimeRef 的值作为 runtime 参数
3. THE Coding.tsx SHALL 在调用 fetchArtifactContent 时传递 currentRuntimeRef 的值作为 runtime 参数
4. THE Coding.tsx SHALL 在调用 fetchWorkspaceChanges 时传递 currentRuntimeRef 的值作为 runtime 参数

### 需求 9：K8s 模式错误处理

**用户故事：** 作为开发者，我希望在 K8s 沙箱不可用时获得清晰的错误提示，以便了解问题原因并采取相应措施。

#### 验收标准

1. IF PodReuseManager 返回 null（Pod 未就绪）且当前为终端连接, THEN THE TerminalWebSocketHandler SHALL 关闭 WebSocket 连接并发送 exit 消息（code=-1）
2. IF PodReuseManager 返回 null（Pod 未就绪）且当前为文件 API 请求, THEN THE WorkspaceController SHALL 返回 HTTP 503 状态码和错误信息"沙箱未就绪"
3. IF Sidecar_HTTP_API 不可达, THEN THE RemoteWorkspaceService SHALL 返回 HTTP 502 状态码和错误信息"沙箱连接失败"
4. IF K8s Exec 连接建立失败, THEN THE K8sTerminalBackend SHALL 关闭相关资源并通过 output 流发送错误通知
5. IF K8s Exec 连接在使用过程中断开, THEN THE K8sTerminalBackend SHALL 触发 output 流完成信号，前端显示 "[Shell exited]"

### 需求 10：废弃代码清理

**用户故事：** 作为开发者，我希望清理已废弃的 PodFileSystemAdapter 代码，以便减少代码库中的技术债务和维护负担。

#### 验收标准

1. THE 代码库 SHALL 删除 PodFileSystemAdapter.java 文件
2. THE 代码库 SHALL 删除所有对 PodFileSystemAdapter 的引用和导入语句
3. WHEN PodFileSystemAdapter 被删除后, THE 代码库 SHALL 保持编译通过，无对该类的残留依赖

### 需求 11：本地模式行为不变性

**用户故事：** 作为开发者，我希望本次改造不影响本地模式的任何现有行为，以便本地开发体验保持稳定。

#### 验收标准

1. WHEN runtime 为 "local" 或缺失, THE TerminalWebSocketHandler SHALL 保持与改造前完全一致的终端创建和管理行为
2. WHEN runtime 为 "local" 或缺失, THE WorkspaceController 的 /workspace/tree 端点 SHALL 保持与改造前完全一致的目录树返回行为
3. WHEN runtime 为 "local" 或缺失, THE WorkspaceController 的 /workspace/file 端点 SHALL 保持与改造前完全一致的文件内容返回行为
4. WHEN runtime 为 "local" 或缺失, THE WorkspaceController 的 /workspace/changes 端点 SHALL 保持与改造前完全一致的文件变更返回行为

### 需求 12：路径安全防护

**用户故事：** 作为系统管理员，我希望 K8s 模式下的文件操作受到路径限制，以防止用户访问 Pod 内 /workspace 之外的敏感目录。

#### 验收标准

1. WHEN K8s 模式下文件操作的路径参数包含路径遍历字符（如 "../"）, THE RemoteWorkspaceService SHALL 拒绝该请求并返回错误
2. THE RemoteWorkspaceService SHALL 验证所有文件操作路径位于 /workspace 目录范围内
