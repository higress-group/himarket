# 需求文档

## 简介

当前 HiCoding 沙箱拉起流程中，Skill 文件以文件夹形式存放在沙箱项目目录下（如 `.qoder/skills/`、`.claude/skills/`），传输方式是后端将 Skill 文件打包为压缩包，通过 HTTP 传输到沙箱后解压。各 `CliConfigGenerator` 实现同时将 Skill 的 Nacos 坐标和凭证写入 CLI 工具各自的 `settings.json` 配置文件。

本需求将 Skill 传输方式从"后端打包压缩 → HTTP 传输压缩包到沙箱 → 解压"改为"传递 Nacos 坐标和凭证（nacos-env.yaml）→ 沙箱内 nacos-cli 下载"。

由于沙箱可能是 Remote 类型常驻实例，每次新会话复用同一个沙箱，因此不能依赖一次性的启动脚本来下载 Skill。本需求改造方案为：

1. `ConfigInjectionPhase`（order=300）改为逐个 `writeFile` 注入配置文件（移除 tar.gz 压缩解压机制，因为 Skill 文件改由 nacos-cli 在沙箱内下载，剩余配置文件数量很少）
2. 新增 `SkillDownloadPhase`（order=350）在 Pipeline 中通过 sidecar exec API 执行 `nacos-cli skill-get` 命令下载 Skill 文件
3. 新增 `SandboxHttpClient.exec()` 方法调用 sidecar 的 `POST /exec` 接口执行命令
4. 新增 `SandboxProvider.exec()` 方法在 Provider 接口中暴露命令执行能力
5. 移除 `extractArchive` 相关方法（SandboxHttpClient、SandboxProvider、RemoteSandboxProvider），不再需要压缩解压

## 术语表

- **ConfigFileBuilder**: 配置文件构建器组件，调用各 `CliConfigGenerator` 生成配置文件并收集为 `ConfigFile` 列表，通过逐个 writeFile 注入沙箱
- **CliConfigGenerator**: CLI 工具配置文件生成器接口，每个 CLI 工具（QwenCode、ClaudeCode、OpenCode、QoderCli）提供各自的实现
- **ResolvedSkillEntry**: 解析后的 Skill 数据对象，包含 Skill 名称、Nacos 坐标（nacosId、namespace、skillName）和 Nacos 凭证（serverAddr、username、password、accessKey、secretKey）
- **nacos-env.yaml**: nacos-cli 工具的连接配置文件，包含 host、port、authType、username、password、namespace 等字段
- **NacosInstance**: 数据库实体，存储 Nacos 实例的连接信息，其中 `serverUrl` 字段为完整 URL 格式（如 `http://nacos:8848`）
- **Skills_Directory**: 各 CLI 工具存放 Skill 文件的目录：QoderCli 使用 `.qoder/skills/`、ClaudeCode 使用 `.claude/skills/`、QwenCode 使用 `.qwen/skills/`、OpenCode 待确认
- **SandboxInitPipeline**: 沙箱初始化流水线，按 order 顺序执行注册的 InitPhase 列表
- **SkillDownloadPhase**: 新增的初始化阶段（order=350），在 ConfigInjectionPhase 之后、SidecarConnectPhase 之前执行，通过 sidecar exec API 调用 nacos-cli 下载 Skill 文件
- **SandboxHttpClient**: 沙箱 Sidecar HTTP 客户端，封装对 Sidecar HTTP API 的调用
- **SandboxProvider**: 统一沙箱提供者接口，抽象不同沙箱环境的差异
- **ExecResult**: 命令执行结果数据对象，包含 exitCode、stdout、stderr 字段

## 需求

### 需求 1：SandboxHttpClient 新增 exec 方法

**用户故事：** 作为沙箱初始化流程，我希望通过 SandboxHttpClient 执行沙箱内的命令，以便在 Pipeline 阶段中调用 nacos-cli 等预装工具。

#### 验收标准

1. THE SandboxHttpClient SHALL 提供 `exec(String baseUrl, String sandboxId, String command, List<String> args, Duration timeout)` 方法，调用 Sidecar 的 `POST /exec` 接口执行命令
2. THE SandboxHttpClient SHALL 将请求体序列化为 JSON 格式，包含 `command` 和 `args` 字段
3. WHEN Sidecar 返回 HTTP 200 时，THE SandboxHttpClient SHALL 从响应 JSON 中解析 `exitCode`、`stdout`、`stderr` 字段并封装为 ExecResult 返回
4. WHEN Sidecar 返回非 200 状态码时，THE SandboxHttpClient SHALL 抛出 IOException，包含 sandboxId、状态码和响应体信息
5. THE SandboxHttpClient SHALL 使用调用方传入的 timeout 参数作为 HTTP 请求超时时间，而非使用默认的 10 秒超时
6. WHEN HTTP 请求被中断时，THE SandboxHttpClient SHALL 恢复线程中断标志并抛出 IOException

### 需求 2：SandboxProvider 新增 exec 方法

**用户故事：** 作为沙箱初始化阶段，我希望通过 SandboxProvider 统一接口执行沙箱内命令，以保持与其他沙箱操作（writeFile、readFile）一致的调用模式。

#### 验收标准

1. THE SandboxProvider 接口 SHALL 提供 `exec(SandboxInfo info, String command, List<String> args, Duration timeout)` 默认方法，返回 ExecResult
2. THE SandboxProvider 的默认实现 SHALL 抛出 UnsupportedOperationException，与 extractArchive 的默认行为保持一致
3. THE RemoteSandboxProvider SHALL 覆写 exec 方法，委托给 SandboxHttpClient.exec() 实现
4. THE ExecResult SHALL 定义为 record 类型，包含 `int exitCode`、`String stdout`、`String stderr` 三个字段

### 需求 3：生成 nacos-env.yaml 配置文件

**用户故事：** 作为沙箱运行时，我希望为每个 Nacos 实例生成独立的 nacos-env.yaml 配置文件，以便 nacos-cli 能够使用正确的凭证连接到对应的 Nacos 服务器。

#### 验收标准

1. WHEN ConfigFileBuilder 调用 generateSkillConfig 且 Skill 列表非空时，THE CliConfigGenerator SHALL 按 nacosId 对 Skill 列表进行分组，为每个不同的 Nacos 实例生成一个独立的 nacos-env.yaml 文件
2. THE CliConfigGenerator SHALL 将 nacos-env.yaml 文件写入工作目录下的 `.nacos/` 子目录，文件命名格式为 `nacos-env-{nacosId}.yaml`
3. THE CliConfigGenerator SHALL 从 ResolvedSkillEntry 的 serverAddr 字段（完整 URL 格式如 `http://host:port`）中解析出独立的 host 和 port 值写入 nacos-env.yaml
4. THE CliConfigGenerator SHALL 在 nacos-env.yaml 中包含以下字段：host、port、namespace、username、password
5. WHEN ResolvedSkillEntry 包含非空的 accessKey 和 secretKey 时，THE CliConfigGenerator SHALL 在 nacos-env.yaml 中设置 authType 为 `aliyun` 并包含 accessKey 和 secretKey 字段
6. WHEN ResolvedSkillEntry 的 accessKey 为空时，THE CliConfigGenerator SHALL 在 nacos-env.yaml 中设置 authType 为 `nacos`
7. IF serverAddr 格式不合法导致 host/port 解析失败，THEN THE CliConfigGenerator SHALL 记录错误日志并跳过该 Nacos 实例的配置生成

### 需求 4：新增 SkillDownloadPhase 初始化阶段

**用户故事：** 作为沙箱初始化流程，我希望在配置文件注入完成后、Sidecar WebSocket 连接建立前，通过 sidecar exec API 执行 nacos-cli 命令下载 Skill 文件到各 CLI 工具的 skills 目录。

#### 验收标准

1. THE SkillDownloadPhase SHALL 实现 InitPhase 接口，name 为 `skill-download`，order 为 350
2. THE SkillDownloadPhase 的 shouldExecute 方法 SHALL 检查 InitContext 中的 resolvedSessionConfig 是否包含非空的 skills 列表，仅在有 Skill 需要下载时返回 true
3. WHEN execute 被调用时，THE SkillDownloadPhase SHALL 按 nacosId 对 Skill 列表进行分组，为每个 nacosId 执行一次 `nacos-cli skill-get <skill1> <skill2> ... --config <nacos-env-path> -o <skills-dir>` 命令，将同一 Nacos 实例下的所有 Skill 名称作为参数一次性传递
4. THE SkillDownloadPhase SHALL 根据 InitContext 中的 providerKey 确定 skills 输出目录：QoderCli 使用 `.qoder/skills/`、ClaudeCode 使用 `.claude/skills/`、QwenCode 使用 `.qwen/skills/`
5. THE SkillDownloadPhase SHALL 使用与 Skill 对应的 nacosId 匹配的 nacos-env 配置文件路径（`.nacos/nacos-env-{nacosId}.yaml`）
6. THE SkillDownloadPhase SHALL 为 exec 调用设置合理的超时时间（60 秒），因为 Skill 下载可能需要较长时间
7. IF nacos-cli 命令执行失败（exitCode 非 0）或 exec 调用抛出异常，THEN THE SkillDownloadPhase SHALL 记录包含 nacosId、skillNames、exitCode、stderr 的警告日志，但继续执行后续 nacosId 分组的下载，不抛出 InitPhaseException
8. THE SkillDownloadPhase 的 retryPolicy 方法 SHALL 返回 RetryPolicy.none()，因为单个 Skill 下载失败不应阻塞会话初始化
9. THE SkillDownloadPhase 的 verify 方法 SHALL 始终返回 true

### 需求 5：注册 SkillDownloadPhase 到 Pipeline

**用户故事：** 作为会话初始化器，我希望 SkillDownloadPhase 被正确注册到 SandboxInitPipeline 中，在 ConfigInjectionPhase 之后、SidecarConnectPhase 之前执行。

#### 验收标准

1. THE AcpSessionInitializer SHALL 在构建 SandboxInitPipeline 时将 SkillDownloadPhase 加入 phases 列表
2. THE SandboxInitPipeline SHALL 按 order 排序后，SkillDownloadPhase（350）位于 ConfigInjectionPhase（300）之后、SidecarConnectPhase（400）之前
3. THE AcpSessionInitializer SHALL 将 providerKey 信息传递给 SkillDownloadPhase，以便其确定 skills 输出目录

### 需求 6：移除旧的 JSON 内嵌 Skill 配置逻辑

**用户故事：** 作为开发者，我希望移除各 CliConfigGenerator 中将 Skill 坐标和凭证写入 settings.json 的旧逻辑，以避免凭证泄露到 CLI 配置文件中。

#### 验收标准

1. THE QoderCliConfigGenerator SHALL 在 generateSkillConfig 方法中停止向 `.qoder/settings.json` 写入 skills JSON 段
2. THE ClaudeCodeConfigGenerator SHALL 在 generateSkillConfig 方法中停止向 `.claude/settings.json` 写入 skills JSON 段
3. THE OpenCodeConfigGenerator SHALL 在 generateSkillConfig 方法中停止向 `opencode.json` 写入 skills JSON 段
4. THE QwenCodeConfigGenerator SHALL 在 generateSkillConfig 方法中停止向 `.qwen/settings.json` 写入 skills JSON 段
5. THE CliConfigGenerator 的各实现 SHALL 在 generateSkillConfig 方法中仅生成 nacos-env.yaml 配置文件

### 需求 7：提取公共 Skill 配置生成逻辑

**用户故事：** 作为开发者，我希望将 nacos-env.yaml 生成逻辑提取为公共方法，以避免在 4 个 CliConfigGenerator 实现中重复相同的代码。

#### 验收标准

1. THE CliConfigGenerator 接口 SHALL 提供 nacos-env.yaml 生成的默认实现（default method），供各子类复用
2. THE CliConfigGenerator 接口 SHALL 定义一个 `skillsDirectory()` 方法，由各实现类返回各自的 skills 目录路径（如 `.qwen/skills/`）
3. WHEN 子类调用默认的 generateSkillConfig 实现时，THE CliConfigGenerator SHALL 使用 skillsDirectory() 返回值，但该值仅用于 SkillDownloadPhase 确定输出目录，generateSkillConfig 本身只负责生成 nacos-env.yaml
4. THE 默认实现 SHALL 将 serverAddr URL 解析逻辑封装为独立的工具方法，包含 host 和 port 的提取

### 需求 8：支持多 Nacos 实例场景

**用户故事：** 作为沙箱运行时，我希望在多个 Skill 来自不同 Nacos 实例时，能够正确地为每个实例生成独立的配置文件和对应的下载命令。

#### 验收标准

1. WHEN Skill 列表中包含来自不同 nacosId 的 Skill 时，THE CliConfigGenerator SHALL 为每个不同的 nacosId 生成独立的 nacos-env-{nacosId}.yaml 文件
2. THE SkillDownloadPhase SHALL 在执行 nacos-cli 命令时为每个 Skill 引用其对应 nacosId 的 nacos-env.yaml 配置文件
3. WHEN 同一 nacosId 下有多个 Skill 时，THE CliConfigGenerator SHALL 复用同一个 nacos-env.yaml 文件，THE SkillDownloadPhase SHALL 将这些 Skill 名称合并为一次 `nacos-cli skill-get <skill1> <skill2> ...` 调用
4. THE SkillDownloadPhase SHALL 确保不同 nacosId 的 Skill 使用各自正确的凭证（通过引用对应的 nacos-env-{nacosId}.yaml）

### 需求 9：ConfigFile 类型推断兼容

**用户故事：** 作为配置文件构建器，我希望 ConfigFileBuilder 能够正确识别新增的 nacos-env.yaml 文件类型，以便沙箱注入流程正确处理这些文件。

#### 验收标准

1. WHEN ConfigFileBuilder 收集到 `.nacos/` 目录下的 yaml 文件时，THE ConfigFileBuilder SHALL 将其 ConfigType 推断为 SKILL_CONFIG
2. THE ConfigFileBuilder 的 inferConfigType 方法 SHALL 保持对已有文件类型（MODEL_SETTINGS、CUSTOM）的推断逻辑不变
