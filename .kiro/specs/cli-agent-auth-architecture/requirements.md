# 需求文档

## 简介

HiMarket 平台当前的 CLI Agent 认证体系存在架构层面的缺失。已有的 `cli-agent-auth` spec 解决了"如何完成认证交互"（ACP 协议层面的认证流程），但未解决更根本的问题：**沙箱中 CLI 工具的凭证究竟归属于谁？认证架构应如何设计？**

核心挑战在于：HiMarket 是一个多租户平台，用户在沙箱环境中使用各类 CLI Agent（qwen-code、qodercli 等）。这些 CLI 工具原本设计为个人使用，其认证机制（OAuth、API Key 等）假设的是单用户场景。当它们被部署到平台沙箱中时，凭证的归属、管理和安全性成为必须解决的架构问题。

本 spec 从架构层面设计 CLI Agent 的凭证归属模型、认证策略选型、凭证安全管理机制，以及从 POC 到生产环境的渐进式演进路径。本 spec 的设计成果将指导 `cli-agent-auth` spec 的实现细化，并与 `sandbox-runtime-strategy` spec 的运行时隔离机制协同工作。

## 术语表

- **Credential_Ownership_Model**：凭证归属模型，定义沙箱中 CLI 工具凭证的所有权和管理责任归属（平台、用户或混合）
- **Platform_Credential**：平台凭证，由 HiMarket 平台统一管理的 API Key 或 Service Account，用于代表平台调用 LLM 服务
- **User_Credential**：用户凭证，由用户自行提供的 API Key 或 OAuth Token，用于以用户身份调用 LLM 服务
- **Credential_Store**：凭证存储服务，安全存储和管理各类凭证的后端组件，提供加密存储、访问控制和生命周期管理
- **Credential_Injector**：凭证注入器，在 CLI Agent 启动时将凭证以环境变量或配置文件形式注入到运行时环境中
- **CLI_Auth_Strategy**：CLI 认证策略，针对特定 CLI 工具类型定义的认证方式和凭证管理方案
- **Auth_Proxy**：认证代理，位于 CLI Agent 和 LLM API 之间的代理层，负责拦截请求并注入平台凭证
- **Credential_Scope**：凭证作用域，定义凭证的有效范围（全局、特定 CLI Provider、特定工作空间）
- **Open_CLI**：开源 CLI 工具（如 qwen-code），可自由部署，支持配置自定义模型端点和 API Key
- **Commercial_CLI**：商业 CLI 工具（如 qodercli），需要商业授权，凭证管理受限于厂商认证体系
- **Credential_Rotation**：凭证轮换，定期更新凭证以降低泄露风险的安全机制
- **Usage_Quota**：用量配额，平台为每个用户或租户分配的 LLM API 调用额度

## 需求

### 需求 1：凭证归属模型定义

**用户故事：** 作为平台架构师，我希望明确定义沙箱中 CLI 工具凭证的归属模型，以便在安全性、用户体验和运营成本之间取得平衡。

#### 验收标准

1. THE Credential_Ownership_Model SHALL 支持三种凭证模式：平台凭证模式（Platform_Credential）、用户凭证模式（User_Credential）和混合模式（Hybrid），每种模式有明确的适用场景定义
2. WHEN 使用平台凭证模式时，THE 系统 SHALL 由平台统一管理 LLM API Key，用户无需提供任何凭证即可使用 CLI Agent
3. WHEN 使用用户凭证模式时，THE 系统 SHALL 要求用户提供自己的 API Key 或通过 OAuth 登录，CLI Agent 使用用户自有凭证调用 LLM 服务
4. WHEN 使用混合模式时，THE 系统 SHALL 提供平台基础额度（使用 Platform_Credential），同时允许用户绑定自有凭证（User_Credential）以获取更高额度或使用特定模型
5. THE Credential_Ownership_Model SHALL 为每个 CLI_Provider 独立配置凭证模式，不同 CLI 工具可使用不同的凭证归属策略

### 需求 2：CLI 工具认证策略分类

**用户故事：** 作为平台架构师，我希望根据 CLI 工具的类型（开源/商业）制定不同的认证策略，以便最大化平台的灵活性和可控性。

#### 验收标准

1. THE CLI_Auth_Strategy SHALL 将 CLI 工具分为两类：Open_CLI（开源 CLI，如 qwen-code）和 Commercial_CLI（商业 CLI，如 qodercli），并为每类定义独立的认证策略
2. WHEN 使用 Open_CLI 时，THE CLI_Auth_Strategy SHALL 支持通过环境变量或配置文件注入自定义模型端点 URL 和 API Key，使 CLI 工具连接到平台自有的 LLM 服务
3. WHEN 使用 Commercial_CLI 时，THE CLI_Auth_Strategy SHALL 支持通过 ACP 协议的 authMethods 机制完成厂商要求的认证流程（如 OAuth 登录）
4. THE CLI_Auth_Strategy SHALL 为每个 CLI_Provider 配置声明其认证类型（env_injection、oauth_interactive、api_key_input）和所需的凭证参数列表
5. WHEN CLI_Provider 配置中声明认证类型为 env_injection 时，THE 系统 SHALL 在启动 CLI 进程前将凭证注入到进程环境变量中，CLI 进程启动后无需额外的交互式认证

### 需求 3：凭证安全存储

**用户故事：** 作为平台安全工程师，我希望所有凭证都经过加密存储并有严格的访问控制，以防止凭证泄露。

#### 验收标准

1. THE Credential_Store SHALL 对所有存储的凭证（API Key、OAuth Token、Service Account Key）使用 AES-256 加密，密钥通过独立的密钥管理机制保护
2. THE Credential_Store SHALL 为每条凭证记录关联 Credential_Scope，包含所属用户 ID、适用的 CLI_Provider 列表和有效期
3. WHEN 凭证被读取时，THE Credential_Store SHALL 验证请求者的身份和权限，仅允许凭证所有者或平台管理员访问
4. THE Credential_Store SHALL 记录所有凭证访问操作的审计日志，包含访问者、访问时间、操作类型和目标凭证 ID
5. IF 凭证超过配置的有效期，THEN THE Credential_Store SHALL 标记该凭证为过期状态，拒绝后续使用并通知凭证所有者
6. THE Credential_Store SHALL 支持 Credential_Rotation，允许平台管理员或用户更新凭证而不中断正在运行的会话

### 需求 4：凭证注入机制

**用户故事：** 作为平台开发者，我希望凭证能在 CLI Agent 启动时自动注入到运行时环境中，以便实现无感知的认证体验。

#### 验收标准

1. THE Credential_Injector SHALL 在 CLI Agent 进程启动前，根据 CLI_Provider 的认证策略配置从 Credential_Store 获取对应凭证
2. WHEN CLI_Provider 的认证类型为 env_injection 时，THE Credential_Injector SHALL 将凭证以环境变量形式注入到 CLI 进程的启动环境中，环境变量名称由 CLI_Provider 配置指定
3. WHEN 使用 Local_Runtime 时，THE Credential_Injector SHALL 通过 ProcessBuilder 的环境变量设置机制注入凭证
4. WHEN 使用 K8s_Runtime 时，THE Credential_Injector SHALL 通过 K8s Secret 挂载或 Pod 环境变量注入凭证，凭证不出现在 Pod Spec 的明文字段中
5. THE Credential_Injector SHALL 确保注入的凭证仅对目标 CLI 进程可见，同一宿主机或同一节点上的其他进程无法访问
6. IF Credential_Store 中不存在所需凭证，THEN THE Credential_Injector SHALL 通知上层触发交互式认证流程（回退到 cli-agent-auth spec 定义的 ACP 认证交互）

### 需求 5：用户凭证管理界面

**用户故事：** 作为 HiMarket 用户，我希望在平台上管理我的 API Key 和认证信息，以便控制我的凭证使用范围和生命周期。

#### 验收标准

1. WHEN 用户进入凭证管理页面时，THE 系统 SHALL 展示用户已绑定的所有凭证列表，每条凭证显示名称、关联的 CLI_Provider、创建时间和状态（有效/过期/已撤销）
2. WHEN 用户添加新凭证时，THE 系统 SHALL 根据目标 CLI_Provider 的认证类型展示对应的输入表单（API Key 输入框或 OAuth 授权按钮）
3. WHEN 用户提交 API Key 类型的凭证时，THE 系统 SHALL 对 API Key 进行格式校验，并尝试调用目标 LLM 服务验证凭证有效性
4. WHEN 用户撤销一条凭证时，THE 系统 SHALL 立即将该凭证标记为已撤销状态，后续 CLI Agent 启动时不再使用该凭证
5. THE 系统 SHALL 对凭证的敏感字段（API Key 值）进行脱敏展示，仅显示前 4 位和后 4 位字符

### 需求 6：平台凭证与用量配额管理

**用户故事：** 作为平台管理员，我希望管理平台级别的 LLM API 凭证和用户用量配额，以便控制平台运营成本。

#### 验收标准

1. THE 系统 SHALL 支持平台管理员配置 Platform_Credential，包括 API Key、关联的 LLM 服务端点和适用的 CLI_Provider 列表
2. WHEN 使用平台凭证模式时，THE 系统 SHALL 为每个用户维护 Usage_Quota，记录 API 调用次数或 Token 消耗量
3. WHEN 用户的 Usage_Quota 达到上限时，THE 系统 SHALL 拒绝新的 CLI Agent 会话请求，并提示用户升级配额或绑定自有凭证
4. THE 系统 SHALL 支持按用户、按 CLI_Provider、按时间段查询用量统计数据
5. IF 用户已绑定自有凭证且选择使用自有凭证时，THEN THE 系统 SHALL 不扣减该用户的平台 Usage_Quota

### 需求 7：认证架构与运行时集成

**用户故事：** 作为平台开发者，我希望认证架构能与 sandbox-runtime-strategy 的运行时抽象层无缝集成，以便在不同运行时环境中提供一致的认证体验。

#### 验收标准

1. THE Credential_Injector SHALL 通过 RuntimeAdapter 接口与运行时层交互，不直接依赖具体的运行时实现（Local 或 K8s）
2. WHEN RuntimeAdapter 创建运行时实例时，THE 系统 SHALL 在 RuntimeConfig 中携带凭证注入配置，由 RuntimeAdapter 的具体实现负责将凭证注入到目标环境
3. THE 系统 SHALL 确保凭证注入发生在 CLI 进程启动之前，CLI 进程启动时凭证已就绪
4. WHEN 运行时实例被销毁时，THE 系统 SHALL 清理注入到该实例中的临时凭证文件或环境变量，防止凭证残留
5. THE 系统 SHALL 支持在运行时实例运行期间动态更新凭证（如 OAuth Token 刷新），通过 RuntimeAdapter 的通信接口将新凭证传递给 CLI 进程

### 需求 8：POC 到生产的演进策略

**用户故事：** 作为平台架构师，我希望有一条清晰的从 POC 到生产的认证架构演进路径，以便团队能渐进式地实现完整的认证体系。

#### 验收标准

1. THE 系统 SHALL 定义三个演进阶段：Phase 1（POC 阶段，环境变量直注入）、Phase 2（基础生产阶段，Credential_Store + Credential_Injector）、Phase 3（完整生产阶段，混合模式 + 用量配额 + 凭证轮换）
2. WHEN 系统处于 Phase 1 时，THE 系统 SHALL 通过 application.yml 配置文件直接定义每个 CLI_Provider 的凭证环境变量，由 AcpProcess 启动时注入
3. WHEN 系统从 Phase 1 演进到 Phase 2 时，THE 系统 SHALL 保持 CLI_Provider 配置格式的向后兼容，已有的环境变量配置方式继续有效
4. THE 系统 SHALL 通过功能开关（Feature Flag）控制各阶段功能的启用状态，允许按需开启高级功能而不影响基础功能
5. WHEN 功能开关未启用 Credential_Store 时，THE 系统 SHALL 回退到 Phase 1 的配置文件直注入模式

### 需求 9：Open CLI 优先策略

**用户故事：** 作为平台架构师，我希望平台优先采用开源 CLI 工具配合自有模型的方案，以便最大化平台对认证流程的控制力。

#### 验收标准

1. THE 系统 SHALL 将 Open_CLI（如 qwen-code）作为推荐的 CLI 工具类型，在 CLI 选择界面中优先展示并标注为"推荐"
2. WHEN 使用 Open_CLI 配合平台自有模型端点时，THE 系统 SHALL 通过 Credential_Injector 注入模型端点 URL 和 API Key，CLI 工具无需执行任何交互式认证
3. THE 系统 SHALL 支持为 Open_CLI 配置自定义模型端点（base_url），使其连接到平台部署的 LLM 推理服务而非公共 API
4. WHEN 用户选择 Commercial_CLI 时，THE 系统 SHALL 提示用户需要使用自有凭证，并引导用户完成厂商要求的认证流程
5. THE 系统 SHALL 在 CLI_Provider 配置中标注每个 CLI 工具的认证复杂度等级（low、medium、high），帮助用户做出选择
