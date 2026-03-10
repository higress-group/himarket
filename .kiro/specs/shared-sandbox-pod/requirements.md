# 需求文档：沙箱多形态架构改造（shared-sandbox-pod）

## 简介

本需求文档描述 HiMarket 沙箱体系的多形态架构改造。当前沙箱类型仅有 `LOCAL`、`K8S`、`E2B` 三种，其中 `K8S` 类型绑定了 `PodReuseManager` 的动态 Pod 创建逻辑，部署门槛高、需要大量 K8s RBAC 权限。

本次改造将沙箱类型重新定义为四种最终形态：`LOCAL`、`SHARED_K8S`、`OPEN_SANDBOX`、`E2B`，移除旧的 `K8S` 类型和 `K8sSandboxProvider`，以 `SharedK8sSandboxProvider` 作为默认实现连接 Helm 预置的共享沙箱 Pod，同时为 OpenSandbox 和 E2B 预留空实现接口。

## 术语表

- **SandboxType**：沙箱类型枚举，标识四种沙箱形态（LOCAL、SHARED_K8S、OPEN_SANDBOX、E2B）
- **SandboxProvider**：沙箱提供者接口，定义沙箱的获取、释放、健康检查、文件操作、连接等方法
- **SharedK8sSandboxProvider**：共享 K8s 沙箱提供者，连接 Helm 预置的常驻共享 Pod，不调用 K8s API 写操作
- **OpenSandboxProvider**：OpenSandbox 沙箱提供者，暂为空实现，预留接口
- **E2BSandboxProvider**：E2B 云沙箱提供者，暂为空实现，预留接口
- **SandboxProviderRegistry**：沙箱提供者注册中心，维护 SandboxType → SandboxProvider 的映射
- **RuntimeSelector**：运行时选择器，检查各沙箱类型的可用性并提供 UI 展示信息
- **SandboxInfo**：沙箱信息对象，包含连接地址、端口、工作目录等
- **SandboxConfig**：沙箱配置对象，包含 userId、类型、环境变量等
- **Sidecar**：共享 Pod 内运行的代理进程，接受 WebSocket 连接并 spawn 独立的 CLI 进程
- **Helm_Chart**：Kubernetes 包管理模板，用于部署共享沙箱 Pod 的 Deployment 和 Service

## 需求

### 需求 1：SandboxType 枚举重构

**用户故事：** 作为平台开发者，我希望沙箱类型枚举被重新定义为四种最终形态，以便统一标识所有沙箱类型并移除旧的动态 Pod 逻辑。

#### 验收标准

1. THE SandboxType 枚举 SHALL 恰好包含 LOCAL、SHARED_K8S、OPEN_SANDBOX、E2B 四个值
2. THE SandboxType 枚举 SHALL 移除旧的 K8S 枚举值
3. WHEN SandboxType.fromValue() 接收到 "k8s" 值时，THE SandboxType SHALL 返回 SHARED_K8S（向后兼容）
4. WHEN SandboxType.fromValue() 接收到 "shared-k8s" 值时，THE SandboxType SHALL 返回 SHARED_K8S
5. WHEN SandboxType.fromValue() 接收到 "open-sandbox" 值时，THE SandboxType SHALL 返回 OPEN_SANDBOX
6. WHEN SandboxType.fromValue() 接收到未知值时，THE SandboxType SHALL 抛出 IllegalArgumentException
7. THE SandboxType 枚举的 JSON 序列化值 SHALL 分别为 "local"、"shared-k8s"、"open-sandbox"、"e2b"

### 需求 2：SharedK8sSandboxProvider 核心实现

**用户故事：** 作为平台开发者，我希望有一个默认的 K8s 沙箱实现，直接连接 Helm 预置的共享 Pod，无需动态创建 K8s 资源。

#### 验收标准

1. THE SharedK8sSandboxProvider SHALL 实现 SandboxProvider 接口，getType() 返回 SHARED_K8S
2. WHEN acquire() 被调用时，THE SharedK8sSandboxProvider SHALL 返回包含共享 Pod Service DNS 地址的 SandboxInfo，格式为 "{sharedServiceName}.{namespace}.svc.cluster.local"
3. WHEN acquire() 被调用时，THE SharedK8sSandboxProvider SHALL 根据 config.userId() 构建工作目录路径，格式为 "/workspace/{userId}"
4. THE SharedK8sSandboxProvider.acquire() SHALL 不调用任何 K8s API，不产生副作用
5. WHEN release() 被调用时，THE SharedK8sSandboxProvider SHALL 执行空操作（共享 Pod 生命周期由 Helm 管理）
6. WHEN healthCheck() 被调用时，THE SharedK8sSandboxProvider SHALL 通过 SandboxHttpClient 调用 Sidecar 的 /health 端点
7. THE SharedK8sSandboxProvider 的文件操作（writeFile/readFile/extractArchive）SHALL 委托给 SandboxHttpClient
8. WHEN connectSidecar() 被调用时，THE SharedK8sSandboxProvider SHALL 构建 WebSocket URI 连接到共享 Pod，并将 workspacePath 作为 cwd 参数传递
9. THE SharedK8sSandboxProvider SHALL 作为 @Component 始终注册到 Spring 容器（不受 @ConditionalOnProperty 控制）

### 需求 3：空实现 Provider（OpenSandbox 和 E2B）

**用户故事：** 作为平台架构师，我希望为 OpenSandbox 和 E2B 预留标准的 Provider 接口，以便未来对接时无需修改上层代码。

#### 验收标准

1. THE OpenSandboxProvider SHALL 实现 SandboxProvider 接口，getType() 返回 OPEN_SANDBOX
2. THE E2BSandboxProvider SHALL 实现 SandboxProvider 接口，getType() 返回 E2B
3. WHEN OpenSandboxProvider 或 E2BSandboxProvider 的 acquire()、writeFile()、readFile()、connectSidecar() 被调用时，THE Provider SHALL 抛出 UnsupportedOperationException
4. WHEN OpenSandboxProvider 或 E2BSandboxProvider 的 healthCheck() 被调用时，THE Provider SHALL 返回 false
5. WHEN OpenSandboxProvider 或 E2BSandboxProvider 的 release() 被调用时，THE Provider SHALL 执行空操作，不抛出异常
6. THE OpenSandboxProvider SHALL 仅在 acp.open-sandbox.enabled=true 时注册到 Spring 容器
7. THE E2BSandboxProvider SHALL 仅在 acp.e2b.enabled=true 时注册到 Spring 容器

### 需求 4：SandboxProviderRegistry 适配

**用户故事：** 作为平台开发者，我希望 Provider 注册中心能自动管理四种沙箱类型的 Provider 映射，确保类型路由正确。

#### 验收标准

1. THE SandboxProviderRegistry SHALL 通过 Spring 自动注入所有 SandboxProvider Bean，构建 SandboxType → SandboxProvider 的映射
2. THE SandboxProviderRegistry SHALL 确保每个 SandboxType 最多对应一个 Provider 实例
3. WHEN getProvider() 接收到已注册的 SandboxType 时，THE SandboxProviderRegistry SHALL 返回对应的 Provider 实例
4. WHEN getProvider() 接收到未注册的 SandboxType 时，THE SandboxProviderRegistry SHALL 抛出 IllegalArgumentException
5. THE SandboxProviderRegistry SHALL 在默认配置下注册 LocalSandboxProvider 和 SharedK8sSandboxProvider

### 需求 5：RuntimeSelector 适配

**用户故事：** 作为前端开发者，我希望运行时选择器能正确反映四种沙箱类型的可用性和展示信息，以便用户选择合适的运行时。

#### 验收标准

1. WHEN isSandboxAvailable(SHARED_K8S) 被调用时，THE RuntimeSelector SHALL 在 K8s 集群配置存在时返回 true
2. WHEN isSandboxAvailable(OPEN_SANDBOX) 被调用时，THE RuntimeSelector SHALL 返回 false
3. WHEN isSandboxAvailable(E2B) 被调用时，THE RuntimeSelector SHALL 返回 false
4. THE RuntimeSelector SHALL 为四种沙箱类型提供正确的标签和描述信息
5. THE RuntimeSelector 中所有 switch 语句 SHALL 覆盖四种新的沙箱类型（LOCAL、SHARED_K8S、OPEN_SANDBOX、E2B）

### 需求 6：Helm Chart 共享沙箱模板

**用户故事：** 作为运维人员，我希望通过 Helm 安装即可自动部署共享沙箱 Pod，无需手动创建 K8s 资源。

#### 验收标准

1. WHEN sandbox.enabled=true 时，THE Helm_Chart SHALL 渲染 sandbox-shared Deployment 和 sandbox-shared Service
2. WHEN sandbox.enabled=false 时，THE Helm_Chart SHALL 不渲染任何沙箱相关资源
3. THE sandbox-shared Deployment SHALL 使用 replicas=1，挂载 emptyDir 到 /workspace，标签为 app=sandbox-shared
4. THE sandbox-shared Service SHALL 使用 ClusterIP 类型，端口映射 8080 → 8080
5. THE Helm_Chart SHALL 支持通过 values.yaml 自定义沙箱镜像、资源限制（CPU/内存）和允许的命令白名单

### 需求 7：配置变更与清理

**用户故事：** 作为平台开发者，我希望移除不再需要的配置项并新增共享模式所需的配置，保持配置简洁。

#### 验收标准

1. THE 配置 SHALL 移除 acp.k8s.shared-mode 配置项
2. THE 配置 SHALL 移除 acp.k8s.reuse-pod-idle-timeout 配置项
3. THE 配置 SHALL 移除 acp.k8s.sandbox-access-via-service 配置项
4. THE 配置 SHALL 新增 acp.k8s.shared-service-name 配置项，默认值为 "sandbox-shared"
5. THE 配置 SHALL 将 acp.default-runtime 的默认值改为 "shared-k8s"

### 需求 8：旧代码移除与引用迁移

**用户故事：** 作为平台开发者，我希望移除旧的 K8sSandboxProvider 和 PodReuseManager 相关代码，并迁移所有引用点，保持代码库整洁。

#### 验收标准

1. THE 代码库 SHALL 移除 K8sSandboxProvider 类
2. THE TerminalWebSocketHandler SHALL 将对 PodReuseManager 的引用改为通过 SharedK8sSandboxProvider 获取共享 Pod 信息
3. THE RemoteWorkspaceService SHALL 将对 PodReuseManager.getHealthyPodEntryWithDefaultClient() 的引用改为使用共享 Pod 的固定地址
4. THE CliProviderConfig.compatibleRuntimes 配置中的 "k8s" 值 SHALL 改为 "shared-k8s"
5. THE 所有引用 SandboxType.K8S 的测试代码 SHALL 更新为使用新的枚举值
6. THE K8sSandboxProviderPropertyTest SHALL 重写为 SharedK8sSandboxProviderPropertyTest

### 需求 9：工作目录隔离与安全

**用户故事：** 作为平台安全工程师，我希望共享 Pod 中不同用户的工作目录互相隔离，防止路径穿越攻击。

#### 验收标准

1. WHEN acquire() 为不同 userId 构建 workspacePath 时，THE SharedK8sSandboxProvider SHALL 返回互不相同的路径
2. WHEN acquire() 为相同 userId 多次调用时，THE SharedK8sSandboxProvider SHALL 返回相同的 workspacePath
3. THE SharedK8sSandboxProvider SHALL 校验 config.userId() 不包含 ".." 或 "/" 等路径穿越字符
4. IF config.userId() 为 null 或空字符串，THEN THE SharedK8sSandboxProvider SHALL 拒绝请求

### 需求 10：错误处理

**用户故事：** 作为平台开发者，我希望各种错误场景都有明确的处理策略，确保系统健壮性。

#### 验收标准

1. WHEN 共享 Pod 未就绪时，THE SharedK8sSandboxProvider.healthCheck() SHALL 返回 false
2. WHEN 前端传入 runtime=open-sandbox 或 runtime=e2b 且对应 Provider 未启用时，THE SandboxProviderRegistry SHALL 抛出 IllegalArgumentException
3. WHEN 前端传入 runtime=open-sandbox 或 runtime=e2b 且对应 Provider 为空实现时，THE Provider.acquire() SHALL 抛出 UnsupportedOperationException
4. WHEN 前端传入旧的 runtime=k8s 值时，THE SandboxType.fromValue() SHALL 正确映射到 SHARED_K8S，请求正常处理
