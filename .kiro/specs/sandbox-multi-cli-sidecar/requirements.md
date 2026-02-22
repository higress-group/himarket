# 需求文档

## 简介

将 K8s 沙箱 Pod 中的 sidecar 从 websocat（只能运行一个固定 CLI 命令）改造为自研 WebSocket Server，支持单个 Pod 内同时运行多个不同的 CLI 工具。连接方通过 WebSocket URL 参数动态指定要启动的 CLI，无需在 Pod 创建时固定 CLI 命令。同时简化后端 Pod 复用逻辑，使其不再区分 provider。

## 术语表

- **Sidecar_Server**: 运行在 K8s 沙箱 Pod 内的自研 WebSocket 服务，替代 websocat，负责接收 WebSocket 连接并根据参数启动对应的 CLI 子进程
- **CLI_Process**: 由 Sidecar_Server 根据连接参数启动的 CLI 工具子进程（如 qodercli、qwen-code），通过 stdin/stdout 与 WebSocket 消息双向桥接
- **Provider**: 后端配置中定义的 CLI 工具标识（如 qodercli、qwen-code），包含命令名和参数
- **Pod_Reuse_Manager**: 后端 Java 组件，负责按用户缓存和复用 K8s Pod
- **K8s_Runtime_Adapter**: 后端 Java 组件，负责管理 Pod 生命周期和 Sidecar WebSocket 连接
- **ACP_Handler**: 后端 WebSocket 处理器，负责前端连接管理和消息转发
- **Allowed_Commands**: Sidecar_Server 维护的白名单，定义允许启动的 CLI 命令集合

## 需求

### 需求 1：Sidecar WebSocket Server 基础能力

**用户故事：** 作为平台开发者，我希望 Pod 内有一个自研的 WebSocket Server 替代 websocat，以便支持更灵活的 CLI 进程管理。

#### 验收标准

1. WHEN Sidecar_Server 启动时, THE Sidecar_Server SHALL 在指定端口（默认 8080）监听 WebSocket 连接
2. WHEN 一个 WebSocket 连接建立且 URL 查询参数中包含 `command` 和可选的 `args` 参数时, THE Sidecar_Server SHALL 启动对应的 CLI_Process 子进程
3. WHEN WebSocket 客户端发送文本消息时, THE Sidecar_Server SHALL 将消息内容写入对应 CLI_Process 的 stdin
4. WHEN CLI_Process 的 stdout 产生输出时, THE Sidecar_Server SHALL 将输出作为 WebSocket 文本消息发送给对应的客户端
5. WHEN WebSocket 连接关闭时, THE Sidecar_Server SHALL 终止对应的 CLI_Process 子进程并释放相关资源
6. WHEN CLI_Process 子进程退出时, THE Sidecar_Server SHALL 关闭对应的 WebSocket 连接
7. THE Sidecar_Server SHALL 支持多个并发 WebSocket 连接，每个连接独立管理各自的 CLI_Process

### 需求 2：CLI 命令安全校验

**用户故事：** 作为平台运维人员，我希望 Sidecar 只允许启动预定义的 CLI 命令，以防止任意命令执行带来的安全风险。

#### 验收标准

1. THE Sidecar_Server SHALL 维护一个 Allowed_Commands 白名单，通过环境变量或配置文件定义
2. WHEN WebSocket 连接请求的 `command` 参数不在 Allowed_Commands 白名单中时, THE Sidecar_Server SHALL 拒绝连接并返回包含错误原因的关闭帧
3. WHEN WebSocket 连接请求缺少 `command` 参数时, THE Sidecar_Server SHALL 拒绝连接并返回包含错误原因的关闭帧

### 需求 3：沙箱镜像和入口脚本改造

**用户故事：** 作为平台开发者，我希望沙箱镜像使用新的 Sidecar Server 替代 websocat，以便支持多 CLI 能力。

#### 验收标准

1. THE Dockerfile SHALL 移除 websocat 二进制文件的安装步骤，并安装新的 Sidecar_Server
2. THE entrypoint.sh SHALL 启动 Sidecar_Server 而非 websocat
3. THE entrypoint.sh SHALL 不再依赖 `CLI_COMMAND` 和 `CLI_ARGS` 环境变量来决定启动哪个 CLI
4. THE entrypoint.sh SHALL 通过环境变量 `ALLOWED_COMMANDS` 向 Sidecar_Server 传递允许的命令白名单

### 需求 4：后端 Pod 创建逻辑改造

**用户故事：** 作为平台开发者，我希望 Pod 创建时不再固定 CLI 命令，以便一个 Pod 能服务于多种 CLI 工具。

#### 验收标准

1. WHEN K8s_Runtime_Adapter 构建 Pod 规格时, THE K8s_Runtime_Adapter SHALL 不再设置 `CLI_COMMAND` 和 `CLI_ARGS` 环境变量
2. WHEN K8s_Runtime_Adapter 构建 Pod 规格时, THE K8s_Runtime_Adapter SHALL 通过环境变量向容器传递 Allowed_Commands 白名单（从应用配置中获取）
3. WHEN Pod_Reuse_Manager 创建新 Pod 时, THE Pod_Reuse_Manager SHALL 不再设置 `CLI_COMMAND` 和 `CLI_ARGS` 环境变量
4. WHEN Pod_Reuse_Manager 创建新 Pod 时, THE Pod_Reuse_Manager SHALL 不再在 Pod 标签中设置 `provider` 字段

### 需求 5：后端 Pod 复用逻辑简化

**用户故事：** 作为平台开发者，我希望 Pod 复用不再区分 provider，因为一个 Pod 现在能运行任意 CLI。

#### 验收标准

1. WHEN Pod_Reuse_Manager 查询可复用 Pod 时, THE Pod_Reuse_Manager SHALL 仅按 `userId` 匹配，不再按 `provider` 过滤
2. WHEN Pod_Reuse_Manager 通过 K8s API 回退查询 Pod 时, THE Pod_Reuse_Manager SHALL 仅使用 `app`、`userId` 和 `sandboxMode` 标签进行过滤

### 需求 6：后端 Sidecar WebSocket 连接改造

**用户故事：** 作为平台开发者，我希望后端连接 Sidecar 时能动态指定要启动的 CLI，以便同一个 Pod 支持不同的 CLI 会话。

#### 验收标准

1. WHEN K8s_Runtime_Adapter 连接 Sidecar WebSocket 时, THE K8s_Runtime_Adapter SHALL 在 WebSocket URL 中包含 `command` 和 `args` 查询参数
2. WHEN ACP_Handler 初始化 K8s Pod 异步流程时, THE ACP_Handler SHALL 将当前会话的 provider 对应的 command 和 args 传递给 K8s_Runtime_Adapter 用于构建 Sidecar WebSocket URL
3. WHEN K8s_Runtime_Adapter 通过 `startWithExistingPod` 复用已有 Pod 时, THE K8s_Runtime_Adapter SHALL 使用包含 CLI 参数的 WebSocket URL 连接 Sidecar

### 需求 7：进程生命周期管理

**用户故事：** 作为平台开发者，我希望 Sidecar 能正确管理 CLI 子进程的生命周期，避免僵尸进程和资源泄漏。

#### 验收标准

1. WHEN CLI_Process 子进程被终止时, THE Sidecar_Server SHALL 先发送 SIGTERM 信号，等待优雅退出超时后再发送 SIGKILL 信号
2. WHEN Sidecar_Server 自身收到 SIGTERM 信号时, THE Sidecar_Server SHALL 终止所有活跃的 CLI_Process 子进程并关闭所有 WebSocket 连接后退出
3. IF CLI_Process 子进程启动失败, THEN THE Sidecar_Server SHALL 向对应的 WebSocket 客户端发送错误消息并关闭连接

### 需求 8：健康检查端点

**用户故事：** 作为平台运维人员，我希望 Sidecar Server 提供健康检查端点，以便 K8s 探针和监控系统使用。

#### 验收标准

1. THE Sidecar_Server SHALL 在 HTTP 路径 `/health` 上提供健康检查端点
2. WHEN Sidecar_Server 正常运行时, THE 健康检查端点 SHALL 返回 HTTP 200 状态码
3. WHEN 健康检查端点被请求时, THE Sidecar_Server SHALL 在响应中包含当前活跃连接数和活跃 CLI_Process 数量
