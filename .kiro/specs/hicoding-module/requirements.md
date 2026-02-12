# 需求文档

## 简介

HiWork 是 HiMarket AI 开放平台的核心模块，旨在将 AI IDE 的编码能力从桌面端单租户形态扩展到 Web 端多租户形态。该模块通过 WebSocket-to-stdio 代理层，将 ACP（Agent Client Protocol）兼容的 CLI Agent（如 qodercli、claude code 等）封装为可弹性调度的 AgentRun 实例，为每个用户/任务提供隔离的 Agent 会话环境。HiWork 不仅支持编码场景，还统一抽象了 PPT 制作、写作、信息图制作等多种 Agent 能力，通过 Skill 和 MCP 配置将通用 AgentLoop 转化为垂类 Agent。

### 主流 AI IDE 架构分析

当前主流桌面端 AI IDE（如 Cursor、Windsurf、Kiro、Zed 等）的架构核心思路：
- **编辑器内核**：基于 VS Code（Electron + Monaco Editor）或类似架构
- **Agent 进程**：通过 CLI 子进程（qodercli、claude code 等）运行 AgentLoop
- **通信协议**：使用 ACP（Agent Client Protocol）基于 JSON-RPC 2.0 over stdio
- **能力扩展**：通过 MCP（Model Context Protocol）服务器提供工具和数据源
- **Skill 配置**：通过 Skill/Steering 文件将通用 Agent 配置为垂类 Agent

### Web 端与桌面端的关键差异

| 维度 | 桌面端 AI IDE | Web 端 HiWork |
|------|-------------|-----------------|
| 租户模型 | 单用户单进程 | 多租户共享集群 |
| 进程生命周期 | 随编辑器启停 | 需独立管理 AgentRun 生命周期 |
| 通信传输 | stdio（本地子进程） | WebSocket → stdio（需代理层） |
| 会话隔离 | 天然隔离（单进程） | 需显式隔离（多用户并发） |
| 资源管理 | 操作系统管理 | 需平台级资源调度和限制 |
| 文件系统 | 本地文件系统 | 隔离的虚拟工作空间 |

## 术语表

- **HiWork_Module**：HiMarket 平台中负责 Agent 能力管理和调度的核心模块
- **AgentRun**：一个运行中的 Agent CLI 进程实例，每个 AgentRun 对应一个独立的 CLI 子进程
- **AgentRun_Manager**：负责 AgentRun 生命周期管理的组件，包括创建、监控、回收
- **ACP_Proxy**：WebSocket-to-stdio 代理层，负责将 Web 客户端的 WebSocket 消息转换为 CLI 进程的 stdio 通信
- **Session_Router**：会话路由器，负责将用户会话映射到正确的 AgentRun 实例
- **Workspace_Manager**：工作空间管理器，负责为每个用户/任务创建和管理隔离的文件系统工作空间
- **Skill_Registry**：Skill 注册中心，管理可用的 Agent Skill 配置（编码、PPT、写作、信息图等）
- **Agent_Type**：Agent 类型，通过 Skill 和 MCP 配置定义的垂类 Agent（如编码 Agent、PPT Agent 等）
- **Tenant**：租户，HiMarket 平台中的一个用户或组织
- **ACP**：Agent Client Protocol，Agent 与客户端之间的标准通信协议，基于 JSON-RPC 2.0
- **MCP**：Model Context Protocol，为 Agent 提供外部工具和数据源的协议

## 需求

### 需求 1：AgentRun 生命周期管理

**用户故事：** 作为平台运维人员，我希望系统能自动管理 AgentRun 实例的完整生命周期，以便在多租户环境下高效利用服务器资源。

#### 验收标准

1. WHEN 用户发起新的 Agent 会话请求, THE AgentRun_Manager SHALL 创建一个新的 AgentRun 实例并启动对应的 CLI 子进程
2. WHEN AgentRun 实例创建时, THE AgentRun_Manager SHALL 为该实例分配唯一标识符并记录其所属租户、Agent 类型和创建时间
3. WHILE AgentRun 实例处于运行状态, THE AgentRun_Manager SHALL 每 30 秒执行一次健康检查以确认 CLI 子进程仍然存活
4. WHEN AgentRun 实例的 CLI 子进程意外退出, THE AgentRun_Manager SHALL 在 5 秒内检测到异常并通知关联的客户端会话
5. WHEN AgentRun 实例空闲超过配置的超时时间, THE AgentRun_Manager SHALL 优雅关闭该实例并释放相关资源
6. WHEN 系统关闭时, THE AgentRun_Manager SHALL 向所有活跃的 AgentRun 实例发送优雅关闭信号并等待最多 10 秒后强制终止

### 需求 2：WebSocket-to-stdio ACP 代理

**用户故事：** 作为 Web 端开发者，我希望通过 WebSocket 连接与 Agent 交互，就像桌面端 IDE 通过 stdio 与 Agent 通信一样透明。

#### 验收标准

1. WHEN Web 客户端建立 WebSocket 连接, THE ACP_Proxy SHALL 将该连接关联到对应的 AgentRun 实例的 stdio 流
2. WHEN Web 客户端通过 WebSocket 发送 JSON-RPC 消息, THE ACP_Proxy SHALL 将消息转发到 AgentRun 实例的 stdin 并保持消息完整性
3. WHEN AgentRun 实例的 stdout 产生 JSON-RPC 消息, THE ACP_Proxy SHALL 将消息转发到关联的 WebSocket 连接
4. WHEN ACP_Proxy 转发 `session/new` 请求时, THE ACP_Proxy SHALL 将请求中的 `cwd` 参数重写为该用户的隔离工作空间绝对路径
5. IF WebSocket 连接意外断开, THEN THE ACP_Proxy SHALL 保留 AgentRun 实例至少 60 秒以支持客户端重连
6. WHEN 客户端在断连保留期内重新连接, THE ACP_Proxy SHALL 恢复与原 AgentRun 实例的关联并重放未送达的消息

### 需求 3：多租户会话隔离

**用户故事：** 作为平台用户，我希望我的 Agent 会话与其他用户完全隔离，以确保数据安全和操作互不干扰。

#### 验收标准

1. THE Session_Router SHALL 确保每个 AgentRun 实例仅服务于单一租户的会话请求
2. WHEN 用户发起会话请求, THE Session_Router SHALL 验证该用户的身份和权限后再路由到对应的 AgentRun 实例
3. THE Workspace_Manager SHALL 为每个租户创建独立的文件系统工作空间目录，路径中包含租户标识符
4. WHEN AgentRun 实例启动时, THE Workspace_Manager SHALL 将该实例的工作目录限制在其所属租户的工作空间范围内
5. IF 一个租户的 AgentRun 实例尝试访问另一个租户的工作空间路径, THEN THE Workspace_Manager SHALL 拒绝该访问请求并记录安全审计日志
6. THE Session_Router SHALL 维护租户到 AgentRun 实例的映射关系，同一租户的多个会话可以复用同一个 AgentRun 实例

### 需求 4：多 Agent 类型支持

**用户故事：** 作为平台用户，我希望能够使用不同类型的 Agent（编码、PPT 制作、写作、信息图制作），以便在同一平台上完成多种 AI 辅助任务。

#### 验收标准

1. THE Skill_Registry SHALL 维护一个 Agent 类型注册表，每个类型包含名称、描述、Skill 配置和 MCP 服务器配置
2. WHEN 用户选择特定 Agent 类型发起会话, THE AgentRun_Manager SHALL 使用该类型对应的 Skill 配置和 MCP 服务器列表启动 AgentRun 实例
3. WHEN AgentRun 实例启动时, THE AgentRun_Manager SHALL 通过 ACP 的 `session/new` 请求将对应的 MCP 服务器配置传递给 CLI Agent
4. THE Skill_Registry SHALL 支持通过 REST API 动态注册和更新 Agent 类型配置
5. WHEN 查询可用 Agent 类型列表时, THE Skill_Registry SHALL 返回当前租户有权限使用的所有 Agent 类型及其描述信息

### 需求 5：资源管理与限制

**用户故事：** 作为平台运维人员，我希望系统能限制和管理每个租户的资源使用，以防止单个租户耗尽系统资源。

#### 验收标准

1. THE AgentRun_Manager SHALL 对每个租户的并发 AgentRun 实例数量实施上限控制
2. WHEN 租户的并发 AgentRun 实例数达到上限时, THE AgentRun_Manager SHALL 拒绝新的 AgentRun 创建请求并返回资源不足的错误信息
3. THE AgentRun_Manager SHALL 跟踪每个 AgentRun 实例的内存使用量和 CPU 时间
4. WHEN 单个 AgentRun 实例的资源使用超过配置的阈值, THE AgentRun_Manager SHALL 终止该实例并通知关联的客户端
5. THE HiWork_Module SHALL 通过配置文件定义全局和租户级别的资源限制参数

### 需求 6：Agent 会话持久化与恢复

**用户故事：** 作为平台用户，我希望能够恢复之前的 Agent 会话，以便继续未完成的任务而不丢失上下文。

#### 验收标准

1. WHEN AgentRun 实例的 CLI Agent 支持 `loadSession` 能力, THE ACP_Proxy SHALL 在初始化阶段检测并记录该能力
2. WHEN 用户请求恢复之前的会话, THE Session_Router SHALL 创建新的 AgentRun 实例并通过 ACP 的 `session/load` 方法加载历史会话
3. THE Session_Router SHALL 持久化存储会话元数据（会话 ID、租户 ID、Agent 类型、创建时间、最后活跃时间）
4. WHEN 用户查询历史会话列表时, THE Session_Router SHALL 返回该用户所有可恢复的会话及其元数据

### 需求 7：认证与授权集成

**用户故事：** 作为平台管理员，我希望 HiWork 模块与平台现有的认证授权体系无缝集成，以确保只有授权用户才能使用 Agent 服务。

#### 验收标准

1. WHEN 客户端建立 WebSocket 连接时, THE ACP_Proxy SHALL 通过 token 查询参数或 Authorization 请求头验证用户身份
2. IF 认证失败, THEN THE ACP_Proxy SHALL 拒绝 WebSocket 握手并返回 401 状态码
3. WHEN 已认证用户发起 Agent 会话请求, THE Session_Router SHALL 检查该用户是否具有使用目标 Agent 类型的权限
4. IF 用户缺少使用目标 Agent 类型的权限, THEN THE Session_Router SHALL 拒绝请求并返回 403 权限不足的错误信息

### 需求 8：可观测性与监控

**用户故事：** 作为平台运维人员，我希望能够监控 HiWork 模块的运行状态和性能指标，以便及时发现和解决问题。

#### 验收标准

1. THE HiWork_Module SHALL 暴露 AgentRun 实例总数、活跃数、各状态分布等指标
2. WHEN AgentRun 实例状态发生变化时, THE AgentRun_Manager SHALL 记录包含实例 ID、租户 ID、旧状态、新状态和时间戳的结构化日志
3. THE ACP_Proxy SHALL 记录每条 JSON-RPC 消息的方向、方法名、会话 ID 和处理耗时
4. WHEN AgentRun 实例创建或销毁时, THE AgentRun_Manager SHALL 发布事件以供外部监控系统消费
