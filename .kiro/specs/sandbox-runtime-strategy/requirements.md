# 需求文档

## 简介

HiMarket 当前采用 POC 本地启动方案运行 CLI Agent：Java 服务通过 `ProcessBuilder` 在本机直接启动 CLI 子进程（qodercli、kiro-cli、qwen-code 等），通过 stdio 进行 JSON-RPC 通信。该方案存在以下核心局限：

- **无多租户隔离**：所有用户的 CLI 进程共享同一台服务器的文件系统和系统资源，仅通过 HOME 环境变量实现有限的凭证隔离（且 Kiro CLI 不支持此机制）
- **无法远程部署**：CLI 工具必须预装在 Java 服务器所在的机器上，无法弹性扩缩容
- **安全风险**：CLI Agent 可执行任意系统命令，缺乏沙箱隔离，恶意或错误操作可能影响宿主机
- **资源无限制**：单个 CLI 进程可能耗尽服务器 CPU、内存或磁盘资源，影响其他用户

为解决上述问题，需要设计一套多运行时策略，支持两种运行时方案的统一抽象和按需切换：
1. **Local（本地进程）**：保持现有开发调试能力，通过 ProcessBuilder 在本机启动 CLI 子进程
2. **K8s（Kubernetes Pod）**：基于 K8s Pod 的生产级隔离方案，适用于多节点集群部署，提供完整的多租户隔离和弹性调度

通过运行时抽象层屏蔽底层差异，上层业务代码（AcpWebSocketHandler、前端 useAcpSession 等）无需感知具体运行时实现。所有 CLI 工具均兼容这两种运行时方案。

## 术语表

- **Runtime_Abstraction**：运行时抽象层，定义统一的接口规范，屏蔽不同运行时（本地进程、K8s Pod）的底层差异
- **Local_Runtime**：本地运行时，即当前 POC 方案，通过 ProcessBuilder 在 Java 服务器本机启动 CLI 子进程
- **K8s_Runtime**：Kubernetes 运行时，通过连接 K8s 集群按需拉起 Pod，为每个用户/任务提供隔离的 Linux 环境，支持弹性调度
- **Runtime_Selector**：运行时选择器，根据部署环境和配置策略自动选择合适的运行时
- **CLI_Provider**：CLI 工具提供商配置，包含命令、参数、环境变量和运行时兼容性信息
- **Sandbox_Container**：沙箱容器，K8s_Runtime 中为单个用户/任务在 K8s 集群中拉起的 Pod 实例
- **File_System_Adapter**：文件系统适配器，为不同运行时提供统一的文件访问接口
- **Communication_Adapter**：通信适配器，为不同运行时提供统一的消息收发接口（stdio、WebSocket、HTTP API 等）
- **Runtime_Capability**：运行时能力描述，标识某个运行时支持的特性（如原生二进制执行、文件系统访问、网络隔离等）
- **K8s_Config**：K8s 集群连接配置，用户提供的 kubeconfig 内容，用于 K8s_Runtime 连接目标 K8s 集群创建 Pod
- **Workspace_Persistence**：工作空间持久化机制，确保 CLI Agent 产生的文件在运行时实例销毁后不丢失
- **Runtime_Selection_UI**：运行时选择界面，前端提供的运行时方案选择交互组件，允许用户在发起会话时选择运行时方案

## 需求

### 需求 1：运行时抽象层设计

**用户故事：** 作为平台开发者，我希望有一个统一的运行时抽象接口，以便上层业务代码无需关心 CLI Agent 运行在哪种运行时环境中。

#### 验收标准

1. THE Runtime_Abstraction SHALL 定义统一的生命周期接口，包含创建（create）、启动（start）、停止（stop）、销毁（destroy）四个阶段
2. THE Runtime_Abstraction SHALL 定义统一的消息收发接口，支持向 CLI 进程发送 JSON-RPC 消息和接收 JSON-RPC 响应
3. THE Runtime_Abstraction SHALL 定义统一的状态查询接口，支持查询运行时实例的当前状态（创建中、运行中、已停止、异常）
4. THE Runtime_Abstraction SHALL 定义统一的文件操作接口，支持读取、写入、列举和删除工作空间中的文件
5. WHEN 上层业务代码通过 Runtime_Abstraction 接口操作 CLI Agent 时，THE Runtime_Abstraction SHALL 将操作委托给具体的运行时实现，上层代码无需感知底层运行时类型
6. THE Runtime_Abstraction SHALL 为每种运行时实现定义 Runtime_Capability 描述，标识该运行时支持的特性集合

### 需求 2：Local 本地运行时适配

**用户故事：** 作为平台开发者，我希望现有的 POC 本地启动方案能适配到运行时抽象层，以便在开发调试阶段继续使用本地进程方式。

#### 验收标准

1. THE Local_Runtime SHALL 实现 Runtime_Abstraction 定义的全部接口，封装现有的 AcpProcess 和 ProcessBuilder 逻辑
2. WHEN Local_Runtime 创建实例时，THE Local_Runtime SHALL 使用 CLI_Provider 配置中的 command、args、env 参数通过 ProcessBuilder 启动本地子进程
3. WHEN Local_Runtime 发送消息时，THE Local_Runtime SHALL 将 JSON-RPC 消息写入子进程的 stdin 流
4. WHEN Local_Runtime 接收消息时，THE Local_Runtime SHALL 从子进程的 stdout 流读取 JSON-RPC 消息并通过统一接口返回
5. WHEN Local_Runtime 执行文件操作时，THE Local_Runtime SHALL 直接操作本地文件系统中用户工作空间目录下的文件
6. THE Local_Runtime SHALL 支持现有的 HOME 环境变量隔离机制，对支持该特性的 CLI 工具启用凭证隔离

### 需求 3：K8s Pod 沙箱运行时

**用户故事：** 作为平台运维人员，我希望通过 K8s 集群按需拉起 Pod 为每个用户提供隔离的运行环境，以便在生产环境中实现安全的多租户隔离和弹性调度。

#### 验收标准

1. THE K8s_Runtime SHALL 实现 Runtime_Abstraction 定义的全部接口，将操作映射为 K8s Pod 管理操作
2. WHEN K8s_Runtime 初始化时，THE K8s_Runtime SHALL 接受用户提供的 K8s_Config（kubeconfig），通过该配置连接目标 K8s 集群
3. WHEN K8s_Runtime 创建实例时，THE K8s_Runtime SHALL 通过 K8s API 在目标集群中拉起一个 Sandbox_Container Pod，Pod 使用预构建的容器镜像，镜像中预装目标 CLI 工具及其依赖
4. WHEN K8s_Runtime 发送消息时，THE K8s_Runtime SHALL 通过 Pod 暴露的 API 或 WebSocket 端点将 JSON-RPC 消息转发给 Pod 内的 CLI 进程
5. WHEN K8s_Runtime 接收消息时，THE K8s_Runtime SHALL 从 Pod 的 API 或 WebSocket 端点接收 CLI 进程的 JSON-RPC 响应
6. WHEN K8s_Runtime 执行文件操作时，THE K8s_Runtime SHALL 通过 Pod 的文件系统 API 操作 Pod 内工作空间目录中的文件
7. THE K8s_Runtime SHALL 通过 K8s 资源配额（Resource Quota）和限制范围（Limit Range）为每个 Pod 配置资源限制（CPU、内存、磁盘），防止单个 Pod 耗尽集群资源
8. WHEN Sandbox_Container Pod 空闲超过配置的超时时间时，THE K8s_Runtime SHALL 自动删除该 Pod 并释放集群资源
9. THE K8s_Runtime SHALL 支持容器镜像的版本管理，允许为不同 CLI 工具配置不同的基础镜像
10. IF 提供的 K8s_Config 无效或无法连接目标集群，THEN THE K8s_Runtime SHALL 返回明确的连接错误信息

### 需求 4：运行时选择策略

**用户故事：** 作为平台用户，我希望在发起 Agent 会话时能选择运行时方案，同时系统能根据环境可用性过滤不可用的选项。

#### 验收标准

1. THE Runtime_Selector SHALL 维护每个 CLI_Provider 的运行时兼容性配置，所有 CLI 工具均兼容 Local 和 K8s 两种运行时方案
2. WHEN 用户在前端选择 CLI 工具后，THE Runtime_Selection_UI SHALL 展示该 CLI 工具兼容的运行时方案列表，不可用的方案标记为不可选并显示原因
3. WHEN 用户选择运行时方案并发起 Agent 会话请求时，THE Runtime_Selector SHALL 验证所选运行时的可用性（如 K8s 集群是否已配置）
4. WHEN 当前环境仅有一种运行时可用时，THE Runtime_Selection_UI SHALL 自动选中该运行时，无需用户手动选择
5. IF 用户选择的运行时当前不可用（如未配置 K8s_Config），THEN THE Runtime_Selector SHALL 返回明确的错误信息，说明不可用原因和配置指引
6. THE Runtime_Selector SHALL 支持通过配置文件设置默认运行时优先级，作为用户未主动选择时的默认值
7. THE Runtime_Selection_UI SHALL 在 HiWork、HiCoding、HiCli 三个模块中提供一致的运行时选择交互体验

### 需求 5：文件系统抽象

**用户故事：** 作为 HiCoding 用户，我希望无论 CLI Agent 运行在哪种环境中，我都能通过文件树浏览和编辑工作空间中的文件。

#### 验收标准

1. THE File_System_Adapter SHALL 定义统一的文件操作接口，包含读取文件内容、写入文件内容、列举目录、创建目录、删除文件或目录、获取文件元信息六种操作
2. WHEN 使用 Local_Runtime 时，THE File_System_Adapter SHALL 直接通过 Java NIO 操作本地文件系统中的用户工作空间目录
3. WHEN 使用 K8s_Runtime 时，THE File_System_Adapter SHALL 通过 Sandbox_Container 暴露的文件系统 API 操作 Pod 内的文件
4. THE File_System_Adapter SHALL 对所有文件路径进行安全校验，防止路径遍历攻击，确保操作限制在用户工作空间范围内
5. WHEN 文件操作失败时，THE File_System_Adapter SHALL 返回统一格式的错误信息，包含错误类型（权限不足、文件不存在、空间不足等）和运行时类型

### 需求 6：通信层适配

**用户故事：** 作为平台开发者，我希望不同运行时的通信方式差异被屏蔽，以便 AcpWebSocketHandler 无需为每种运行时编写不同的消息转发逻辑。

#### 验收标准

1. THE Communication_Adapter SHALL 定义统一的消息发送接口，接受 JSON-RPC 字符串并将其投递到目标 CLI 进程
2. THE Communication_Adapter SHALL 定义统一的消息接收接口，以响应式流（Reactive Stream）形式返回 CLI 进程的输出消息
3. WHEN 使用 Local_Runtime 时，THE Communication_Adapter SHALL 通过子进程的 stdin/stdout 流进行消息收发
4. WHEN 使用 K8s_Runtime 时，THE Communication_Adapter SHALL 通过与 Sandbox_Container 之间的 WebSocket 或 HTTP API 进行消息收发
5. WHEN 通信链路中断时，THE Communication_Adapter SHALL 触发统一的断连事件，携带运行时类型和断连原因
6. THE Communication_Adapter SHALL 保证消息的顺序性和完整性，不丢失、不重复、不乱序

### 需求 7：运行时健康检查与故障恢复

**用户故事：** 作为平台运维人员，我希望系统能自动检测运行时实例的健康状态并在故障时进行恢复，以保证服务可用性。

#### 验收标准

1. WHILE 运行时实例处于运行状态，THE Runtime_Abstraction SHALL 定期执行健康检查，检查间隔通过配置指定
2. WHEN Local_Runtime 的 CLI 子进程意外退出时，THE Local_Runtime SHALL 在 5 秒内检测到异常并通知上层
3. WHEN K8s_Runtime 的 Sandbox_Container Pod 停止响应时，THE K8s_Runtime SHALL 标记该实例为异常状态并通知上层
4. WHEN 运行时实例被标记为异常状态时，THE Runtime_Abstraction SHALL 向关联的客户端会话发送错误通知，包含故障类型和建议操作（如重新连接）
5. IF 运行时实例连续健康检查失败超过配置的阈值，THEN THE Runtime_Abstraction SHALL 强制销毁该实例并释放相关资源

### 需求 8：工作空间文件持久化

**用户故事：** 作为平台用户，我希望 CLI Agent 通过 vibe coding 产生的文件在会话结束或运行时实例销毁后不会丢失，以便我后续继续使用这些文件。

#### 验收标准

1. WHEN 使用 Local_Runtime 时，THE Workspace_Persistence SHALL 直接依赖本地文件系统持久化，文件存储在 `{workspaceRoot}/{userId}/` 目录下，无需额外持久化操作
2. WHEN 使用 K8s_Runtime 时，THE Workspace_Persistence SHALL 为每个 Pod 挂载 K8s PersistentVolume，将工作空间目录映射到持久化存储，确保 Pod 销毁后文件保留
3. WHEN 用户重新打开 K8s_Runtime 会话时，THE Workspace_Persistence SHALL 将之前持久化的文件挂载到新的 Pod 实例中

### 需求 9：K8s 集群配置管理

**用户故事：** 作为平台管理员，我希望能配置 K8s 集群连接信息，以便 K8s_Runtime 能连接到指定的 K8s 集群创建 Pod。

#### 验收标准

1. THE K8s_Runtime SHALL 支持通过用户提供的 kubeconfig 内容连接目标 K8s 集群
2. WHEN 用户提交 K8s_Config 时，THE 系统 SHALL 验证 kubeconfig 的有效性，包括集群可达性和认证凭证有效性
3. THE 系统 SHALL 安全存储 K8s_Config，对敏感字段（如证书、token）进行加密存储
4. WHEN K8s_Config 未配置时，THE Runtime_Selection_UI SHALL 将 K8s_Runtime 选项标记为不可用，并提示用户先配置 K8s 集群
5. THE 系统 SHALL 支持配置多个 K8s 集群，允许管理员为不同场景（如不同地域、不同资源规格）指定不同的集群

## 附录：两种运行时方案对比分析

| 维度 | Local（本地进程） | K8s（Kubernetes Pod） |
|------|------------------|----------------------|
| 隔离级别 | 无隔离（共享宿主机） | Pod 级隔离（K8s 命名空间 + 容器） |
| 启动速度 | 秒级（进程启动） | 秒~十秒级（Pod 调度 + 容器启动） |
| 资源消耗 | 服务器 CPU/内存 | 集群 CPU/内存（K8s 资源配额管控） |
| CLI 兼容性 | 全部（需预装） | 全部（镜像预装） |
| 多租户支持 | 有限（HOME 隔离） | 完整（Pod 隔离 + 命名空间） |
| 文件系统 | 本地文件系统 | Pod 内文件系统（可挂载 PV） |
| 网络访问 | 无限制 | 可配置 NetworkPolicy |
| 运维复杂度 | 低 | 高（需 K8s 集群） |
| 弹性扩缩容 | 不支持 | 支持（K8s 自动调度） |
| 适用场景 | 开发调试 | 生产多租户部署 |
| 持久化 | 本地磁盘 | PersistentVolume |
| 安全性 | 低（CLI 可执行任意命令） | 高（Pod 沙箱 + 资源限制 + NetworkPolicy） |

### CLI 工具运行时兼容性矩阵

| CLI 工具 | 运行时类型 | Local_Runtime | K8s_Runtime |
|---------|----------|--------------|-------------|
| Qoder CLI | 原生二进制 | ✅ | ✅ |
| Kiro CLI | 原生二进制 | ✅ | ✅ |
| Qwen Code | Python | ✅ | ✅ |
| Claude Code ACP | Node.js (npx) | ✅ | ✅ |
| Codex ACP | Node.js (npx) | ✅ | ✅ |
