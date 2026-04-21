# Nacos 源码参考索引

> 本地路径: `reference-projects/nacos/`
> 版本: 3.2.1-SNAPSHOT | Java 17（服务端）/ Java 8+（客户端）
> 用途: HiMarket 开发时的 Nacos 源码参考，不提交到 git

Nacos 是阿里巴巴开源的服务发现与配置管理平台，3.x 版本新增 AI 注册表能力（Skill/Agent/MCP/Pipeline）。HiMarket 通过 `nacos-maintainer-client` 模块与 Nacos 交互，管理 Skill、Worker、MCP 服务器和命名空间。

## 参考价值

以下场景建议打开本索引查阅 Nacos 源码：

1. **maintainer-client API 行为理解**：当 `SkillMaintainerService` 等接口行为不明确时，追溯到 `ai/service/` 查看服务端完整实现
2. **API 模型字段语义**：`api/ai/model/` 下的 Skill、AgentCard、McpServerBasicInfo 等模型类定义了客户端与服务端共享的数据结构
3. **错误码与异常处理**：Nacos 异常（`NacosException`）的错误码定义在 `api/exception/` 中，HiMarket 在 Service 层统一转换为 `BusinessException`
4. **Console REST API 参考**：`console/controller/v3/` 的 API 设计（路径风格、认证注解、响应格式）可作为参考
5. **AI 注册表架构**：`ai/` 模块展示了 Skill/Agent/MCP/Pipeline 的统一管理模式

## 架构总览

```
                        ┌──────────────────────┐
                        │     bootstrap/       │
                        │  NacosBootstrap 入口  │
                        └──────────┬───────────┘
                  ┌────────────────┼────────────────┐
                  ▼                ▼                 ▼
           ┌────────────┐  ┌────────────┐  ┌──────────────┐
           │  console/   │  │  server/   │  │ ai-registry- │
           │  Web 控制台  │  │ 服务器聚合  │  │  adaptor/    │
           └──────┬─────┘  └─────┬──────┘  └──────┬───────┘
                  │              │                  │
         ┌───────┴───────┬──────┴──────┬───────────┘
         ▼               ▼             ▼
  ┌────────────┐  ┌────────────┐  ┌──────────┐
  │  naming/   │  │  config/   │  │   ai/    │
  │ 服务发现    │  │ 配置管理    │  │ AI 能力   │
  └──────┬─────┘  └──────┬─────┘  └────┬─────┘
         └───────────────┼──────────────┘
                         ▼
                  ┌────────────┐
                  │   core/    │
                  │ 核心框架    │
                  └──────┬─────┘
         ┌───────────────┼──────────────┐
         ▼               ▼              ▼
  ┌──────────────┐ ┌────────────┐ ┌──────────┐
  │ consistency/ │ │persistence/│ │  auth/   │
  │ 一致性协议    │ │ 数据持久化  │ │ 认证授权  │
  └──────────────┘ └────────────┘ └──────────┘
         │               │              │
         └───────────────┼──────────────┘
                         ▼
              ┌─────────────────────┐
              │ common/ + api/ + sys│
              │ 公共基础设施          │
              └─────────────────────┘
```

## 模块索引

### 公共基础层

| 模块 | 说明 | 关键类 |
|------|------|--------|
| `api/` | 客户端-服务端通信协议和领域模型 | `NacosFactory`, `NamingService`, `ConfigService`, `LockService`, `Instance` |
| `common/` | 事件系统、HTTP 客户端、线程池、gRPC 客户端 | `NotifyCenter`, `NacosAsyncHttpClient`, `ExecutorFactory` |
| `sys/` | 系统配置、部署类型、环境变量 | `EnvUtil`, `Constants`, `ModuleManager` |

### 核心框架层

| 模块 | 说明 | 关键类 |
|------|------|--------|
| `core/` | 集群管理、gRPC 服务端、请求控制、命名空间 | `ServerMemberManager`, `GrpcBiStreamRequestAcceptor`, `NamespaceService` |
| `auth/` | 认证框架、`@Secured` 注解、请求解析 | `AuthenticationContext`, `@Secured`, `HttpResourceParser` |
| `consistency/` | Raft (CP) 和 Distro (AP) 一致性协议 | `SnapshotOperation`, `HessianSerializer` |
| `persistence/` | 多数据库持久化（Derby/MySQL/PostgreSQL） | `DataSourceFactory`, `DatasourcePlatformConstant` |
| `lock/` | 分布式锁服务 | `LockService`, `LockInfo` |

### 业务模块层

| 模块 | 说明 | 关键类 |
|------|------|--------|
| `naming/` | 服务注册、发现、健康检查、负载均衡 | `ServiceManager`, `InstanceManager`, `ServiceIndexesManager` |
| `config/` | 配置存储、发布、订阅、变更监听 | `ConfigService`, `ConfigHistoryService`, `ConfigInfo`, `ChangePublisher` |
| `ai/` | Agent 管理、Prompt、MCP、A2A、Skills、Pipeline | `AgentSpecsService`, `SkillsService`, `PromptService`, `PipelineService` |
| `cmdb/` | CMDB 集成（设备、机房、地域） | `CmdbManager`, `CmdbService` |
| `address/` | 地址服务器（客户端动态获取服务器列表） | `AddressServerController` |

### AI 扩展层

| 模块 | 说明 | 关键类 |
|------|------|--------|
| `ai-registry-adaptor/` | AI 注册表适配 | `NacosAiRegistry` |
| `mcp-registry-adaptor/` | MCP 协议适配（暴露 AI 资源为 MCP 格式） | — |
| `copilot/` | AI Copilot 能力 | — |

### 客户端模块

| 模块 | 说明 | 关键类 |
|------|------|--------|
| `client/` | Java 客户端 SDK（Java 8+） | `NacosConfigService`, `NacosNamingService` |
| `client-basic/` | 客户端底层基础设施 | — |
| `maintainer-client/` | **HiMarket 使用此模块** — 维护者管理客户端 | `AiMaintainerService`, `NamingMaintainerService`, `SkillMaintainerService`, `McpMaintainerService`, `AgentSpecMaintainerService` |

### 服务端部署层

| 模块 | 说明 | 关键类 |
|------|------|--------|
| `bootstrap/` | 启动入口，支持 MERGED/SERVER/CONSOLE 三种模式 | `NacosBootstrap` |
| `server/` | 服务器聚合（naming + config + istio + prometheus） | — |
| `console/` | Web 控制台 REST API（V3） | `ConsoleConfigController`, `ConsoleNamingController`, `ConsoleMcpController` |
| `console-ui-next/` | Web 控制台新版前端 | — |
| `distribution/` | 二进制分发包打包 | — |

### 插件与集成层

| 模块 | 说明 | 关键类 |
|------|------|--------|
| `plugin/` | 插件 SPI 接口定义 | — |
| `plugin-default-impl/` | 默认插件实现（认证、加密、数据源、限流） | `NacosAuthProvider`, `MysqlDatasourceServiceImpl`, `AesEncryptionPluginService` |
| `prometheus/` | Prometheus 指标集成 | `PrometheusController` |
| `istio/` | Istio 服务网格 xDS/MCP 支持 | `IstioXdsServer` |
| `k8s-sync/` | Kubernetes 服务同步 | — |

## HiMarket 集成点映射

HiMarket 通过 `nacos-maintainer-client` 与 Nacos 交互。以下是 HiMarket 代码到 Nacos 源码的对应关系：

### 依赖关系

```
HiMarket NacosServiceImpl
    → nacos-maintainer-client (maintainer-client/)
        → nacos-client-basic (client-basic/)
        → nacos-api (api/)
        → nacos-common (common/)
```

### 功能映射

| HiMarket 操作 | HiMarket 代码 | Nacos 客户端 API | Nacos 服务端处理 |
|---------------|--------------|-----------------|-----------------|
| Skill 列表 | `NacosServiceImpl` → `skillService.listSkills()` | `maintainer-client/ai/SkillMaintainerService` | `ai/service/skills/SkillsService` |
| Skill 上传 | `SkillServiceImpl` → `skillService.uploadSkillFromZip()` | `maintainer-client/ai/SkillMaintainerService` | `ai/service/skills/SkillsService` |
| Skill 发布 | `SkillServiceImpl` → `skillService.publish()` | `maintainer-client/ai/SkillMaintainerService` | `ai/service/skills/SkillsService` |
| MCP 服务器列表 | `NacosServiceImpl` → `mcpService.listMcpServer()` | `maintainer-client/ai/McpMaintainerService` | `ai/service/` |
| Agent 列表 | `NacosServiceImpl` → `aiService.listAgentCards()` | `maintainer-client/ai/AiMaintainerService` | `ai/service/agentspecs/AgentSpecsService` |
| Worker 上传 | `WorkerServiceImpl` → `agentSpecService.uploadAgentSpecFromZip()` | `maintainer-client/ai/AgentSpecMaintainerService` | `ai/service/agentspecs/AgentSpecsService` |
| 命名空间列表 | `NacosServiceImpl` → `namingService.getNamespaceList()` | `maintainer-client/naming/NamingMaintainerService` | `core/namespace/NamespaceService` |
| 下载数同步 | `DownloadCountSyncTask` → `skillService.getSkillMeta()` | `maintainer-client/ai/SkillMaintainerService` | `ai/service/skills/SkillsService` |

### 关键 API 模型（共享于 `nacos-api`）

| 模型类 | 包路径 | HiMarket 使用场景 |
|--------|--------|------------------|
| `Skill` | `api.ai.model.skills` | Skill 详情 |
| `SkillSummary` | `api.ai.model.skills` | Skill 列表项 |
| `SkillMeta` | `api.ai.model.skills` | Skill 元信息（下载数等） |
| `AgentCard` | `api.ai.model.a2a` | Agent 详情 |
| `AgentSpec` | `api.ai.model.agentspecs` | Worker 规格详情 |
| `McpServerBasicInfo` | `api.ai.model.mcp` | MCP 服务器摘要 |
| `McpServerDetailInfo` | `api.ai.model.mcp` | MCP 服务器详情（含工具） |
| `McpTool` | `api.ai.model.mcp` | MCP 工具定义 |
| `Page<T>` | `api.model` | 分页结果 |

## Console REST API（V3）

Nacos 控制台的 REST API 对理解数据流有参考价值：

| 端点前缀 | Controller | 功能 |
|---------|-----------|------|
| `/v3/console/cs/config` | `ConsoleConfigController` | 配置 CRUD |
| `/v3/console/cs/history` | `ConsoleHistoryController` | 配置历史 |
| `/v3/console/ns/service` | `ConsoleNamingController` | 服务 CRUD |
| `/v3/console/ns/instance` | `ConsoleNamingController` | 实例查询/删除 |
| `/v3/console/core/cluster/nodes` | `ConsoleClusterController` | 集群节点 |
| `/v3/console/core/namespace` | `ConsoleNamespaceController` | 命名空间管理 |
| `/v3/console/ai/mcp` | `ConsoleMcpController` | MCP 管理 |
| `/v3/console/ai/pipeline` | `ConsolePipelineController` | Pipeline 管理 |

## 快速查找

| 我要找... | 去哪里看 |
|-----------|---------|
| 配置管理代码 | `config/` → `ConfigService` |
| 服务发现代码 | `naming/` → `ServiceManager`, `InstanceManager` |
| AI/Agent/Skill | `ai/service/` → `SkillsService`, `AgentSpecsService` |
| MCP 协议实现 | `ai/service/` + `mcp-registry-adaptor/` |
| HiMarket 用的客户端 | `maintainer-client/` → `AiMaintainerService` |
| 认证授权 | `auth/` + `plugin-default-impl/` auth impl |
| 一致性协议 | `consistency/` (Raft/Distro) + `core/distributed/` |
| 数据库操作 | `persistence/` → `DataSourceFactory` |
| REST API | `console/controller/v3/` |
| 集群管理 | `core/cluster/` → `ServerMemberManager` |
| 插件扩展 | `plugin/` (SPI) + `plugin-default-impl/` |

## 部署模式

| 模式 | 参数 | 说明 |
|------|------|------|
| MERGED | 默认 | 服务器 + 控制台合并运行 |
| SERVER | `-Dnacos.deployment.type=server` | 仅服务器 |
| CONSOLE | `-Dnacos.deployment.type=console` | 仅控制台 |
