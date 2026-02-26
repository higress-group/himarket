# 需求文档

## 简介

将 HiWork（Quest 页面）和 HiCoding（Coding 页面）对接沙箱模式，参考 HiCli 的现有设计。当前只有 HiCli 页面支持运行时选择（本地/K8s）和沙箱模式（用户级），HiWork 和 HiCoding 页面硬编码使用本地运行时（`runtime: "local"`），不支持 K8s 沙箱环境。本次需求仅支持用户级沙箱（`sandboxMode=user`），会话级沙箱（`sandboxMode=session`）标记为待支持。

此外，不同 CLI 工具在沙箱模式下的选择步骤、认证方式和扩展配置路径存在差异，需要按各工具的特性进行差异化处理。

## 术语表

- **HiWork**：Quest 页面，提供对话式 AI 编程助手功能
- **HiCoding**：Coding 页面，提供 IDE 风格的 AI 编码环境
- **HiCli**：CLI 调试页面，已完整支持沙箱模式的参考实现
- **沙箱模式（SandboxMode）**：沙箱的生命周期管理模式，分为用户级（user）和会话级（session）
- **用户级沙箱**：同一用户的多个 WebSocket 会话共享同一个 K8s Pod，Pod 在用户所有会话断开后经过空闲超时才回收
- **会话级沙箱**：每个 WebSocket 会话独占一个 K8s Pod，会话断开后 Pod 立即回收（待支持）
- **运行时类型（RuntimeType）**：CLI 进程的运行环境，分为本地运行（local）和 K8s 沙箱（k8s）
- **CliSelector**：前端通用 CLI 工具选择器组件，负责展示可用的 CLI Provider 列表，并根据各工具能力动态计算选择步骤
- **RuntimeSelector**：前端运行时选择器组件，展示兼容的运行时选项（本地/K8s）
- **WebSocket_URL**：前端与后端建立 WebSocket 连接的地址，包含 provider、runtime、sandboxMode 等查询参数
- **PodReuseManager**：后端 Pod 复用管理器，负责用户级沙箱 Pod 的创建、复用和回收
- **AcpHandshakeInterceptor**：WebSocket 握手拦截器，从 URL 查询参数中提取 provider、runtime、sandboxMode 等属性
- **CliConfigGenerator**：CLI 工具配置文件生成器接口，各 CLI 工具提供各自的实现，负责生成模型配置、MCP 配置和 Skill 配置
- **StepConfig**：前端步骤配置对象，由 `computeSteps` 函数根据 CLI Provider 的能力标志动态计算可见步骤列表
- **沙箱认证（SandboxAuth）**：CLI 工具在沙箱环境中的认证方式，不同工具支持不同的认证机制（Personal Access Token、API Key、OAuth 等）
- **认证方案（AuthOption）**：CLI 工具提供的认证选项，如「默认」（假设已登录，不需要额外认证配置）和「Personal Access Token」（用户输入 PAT 进行认证）

## 需求

### 需求 1：HiWork 页面支持运行时选择

**用户故事：** 作为开发者，我希望在 HiWork 页面选择 CLI 工具时能够选择运行时类型（本地/K8s 沙箱），以便在隔离的沙箱环境中使用 AI 编程助手。

#### 验收标准

1. WHEN 开发者打开 HiWork 页面的 CLI 选择器, THE CliSelector SHALL 显示运行时选择器（RuntimeSelector），展示当前 CLI Provider 兼容的运行时选项
2. WHEN 开发者选择 K8s 沙箱运行时并点击连接, THE HiWork 页面 SHALL 使用选中的运行时类型构建 WebSocket_URL，替代硬编码的 `runtime: "local"`
3. WHEN 开发者选择本地运行时并点击连接, THE HiWork 页面 SHALL 使用 `runtime: "local"` 构建 WebSocket_URL，保持与现有行为一致
4. WHEN 运行时选项列表中仅有一个可用选项, THE RuntimeSelector SHALL 自动选中该选项

### 需求 2：HiCoding 页面支持运行时选择

**用户故事：** 作为开发者，我希望在 HiCoding 页面选择 CLI 工具时能够选择运行时类型（本地/K8s 沙箱），以便在隔离的沙箱环境中使用 IDE 编码功能。

#### 验收标准

1. WHEN 开发者打开 HiCoding 页面的 CLI 选择器, THE CliSelector SHALL 显示运行时选择器（RuntimeSelector），展示当前 CLI Provider 兼容的运行时选项
2. WHEN 开发者选择 K8s 沙箱运行时并点击连接, THE HiCoding 页面 SHALL 使用选中的运行时类型构建 WebSocket_URL，替代硬编码的 `runtime: "local"`
3. WHEN 开发者选择本地运行时并点击连接, THE HiCoding 页面 SHALL 使用 `runtime: "local"` 构建 WebSocket_URL，保持与现有行为一致

### 需求 3：HiWork 和 HiCoding 的 WebSocket URL 传递 sandboxMode 参数

**用户故事：** 作为开发者，我希望 HiWork 和 HiCoding 在连接 K8s 沙箱时自动传递用户级沙箱模式参数，以便后端正确管理 Pod 的复用和生命周期。

#### 验收标准

1. WHEN 开发者在 HiWork 页面选择 K8s 运行时并连接, THE HiWork 页面 SHALL 在 WebSocket_URL 中附加 `sandboxMode=user` 查询参数
2. WHEN 开发者在 HiCoding 页面选择 K8s 运行时并连接, THE HiCoding 页面 SHALL 在 WebSocket_URL 中附加 `sandboxMode=user` 查询参数
3. WHEN 开发者选择本地运行时, THE WebSocket_URL SHALL 不包含 sandboxMode 查询参数
4. THE buildAcpWsUrl 函数 SHALL 接收 sandboxMode 参数并正确附加到 URL 查询字符串中（此功能已实现）

### 需求 4：HiWork 和 HiCoding 的沙箱状态展示

**用户故事：** 作为开发者，我希望在 HiWork 和 HiCoding 页面连接 K8s 沙箱时能看到沙箱创建进度，以便了解当前连接状态。

#### 验收标准

1. WHEN 开发者在 HiWork 页面选择 K8s 运行时并发起连接, THE HiWork 页面 SHALL 显示沙箱创建中状态提示（如"正在连接沙箱环境..."）
2. WHEN 后端通过 WebSocket 推送 `sandbox/status` 通知且状态为 `ready`, THE HiWork 页面 SHALL 更新状态为沙箱就绪并自动创建 Quest
3. WHEN 后端通过 WebSocket 推送 `sandbox/status` 通知且状态为 `error`, THE HiWork 页面 SHALL 显示错误信息
4. WHEN 开发者在 HiCoding 页面选择 K8s 运行时并发起连接, THE HiCoding 页面 SHALL 显示沙箱创建中状态提示
5. WHEN 后端通过 WebSocket 推送 `sandbox/status` 通知且状态为 `ready`, THE HiCoding 页面 SHALL 更新状态为沙箱就绪并自动创建 Quest
6. WHEN 后端通过 WebSocket 推送 `sandbox/status` 通知且状态为 `error`, THE HiCoding 页面 SHALL 显示错误信息

### 需求 5：HiWork 和 HiCoding 沙箱就绪后再创建 Quest

**用户故事：** 作为开发者，我希望在 K8s 沙箱环境就绪后再自动创建 Quest，避免在沙箱未就绪时发送请求导致失败。

#### 验收标准

1. WHILE K8s 沙箱状态为 `creating`, THE HiWork 页面 SHALL 等待沙箱就绪，不自动创建 Quest
2. WHEN K8s 沙箱状态变为 `ready`, THE HiWork 页面 SHALL 自动创建第一个 Quest
3. WHILE K8s 沙箱状态为 `creating`, THE HiCoding 页面 SHALL 等待沙箱就绪，不自动创建 Quest
4. WHEN K8s 沙箱状态变为 `ready`, THE HiCoding 页面 SHALL 自动创建第一个 Quest
5. WHEN 运行时为本地模式, THE HiWork 页面和 HiCoding 页面 SHALL 保持现有行为，连接成功后立即自动创建 Quest

### 需求 6：HiCli 的 CliSelector 传递 sandboxMode 参数

**用户故事：** 作为开发者，我希望 HiCli 在选择 K8s 运行时连接时也能正确传递 sandboxMode 参数到 WebSocket URL，以便后端统一处理沙箱模式。

#### 验收标准

1. WHEN 开发者在 HiCli 页面选择 K8s 运行时并连接, THE CliSelector SHALL 将 sandboxMode 值传递给 onSelect 回调
2. WHEN useHiCliSession 的 connectToCli 方法被调用且运行时为 K8s, THE buildHiCliWsUrl 函数 SHALL 在 WebSocket_URL 中附加 sandboxMode 查询参数
3. WHEN 运行时为本地模式, THE buildHiCliWsUrl 函数 SHALL 不附加 sandboxMode 查询参数

### 需求 7：会话级沙箱模式标记为待支持

**用户故事：** 作为开发者，我希望在运行时选择界面能看到会话级沙箱模式的存在但标记为待支持状态，以便了解未来功能规划。

#### 验收标准

1. THE useRuntimeSelection Hook SHALL 保持 sandboxMode 状态管理能力，默认值为 `user`
2. WHILE 会话级沙箱功能未实现, THE 前端 SHALL 仅使用 `user` 作为 sandboxMode 值，不向用户暴露沙箱模式切换选项
3. THE 后端 AcpHandshakeInterceptor SHALL 继续支持解析 `sandboxMode` 查询参数（此功能已实现，无需修改）

### 需求 8：CLI 工具选择步骤的差异化处理

**用户故事：** 作为开发者，我希望不同 CLI 工具在选择流程中展示各自特有的配置步骤，以便根据工具特性进行正确配置。

#### 验收标准

1. THE computeSteps 函数 SHALL 根据 CLI Provider 的能力标志（supportsCustomModel、supportsMcp、supportsSkill、authOptions）动态计算可见步骤列表
2. WHEN 开发者选择 Qwen Code 工具, THE CliSelector SHALL 展示三个步骤：选择工具、模型配置（支持自定义模型/市场模型）、扩展配置（MCP + Skill）
3. WHEN 开发者选择 QoderCli 工具, THE CliSelector SHALL 展示该工具特有的配置步骤，包含认证方案选择步骤（默认/Personal Access Token）
4. WHEN 开发者选择 OpenCode 工具, THE CliSelector SHALL 展示两个步骤：选择工具、模型配置（支持自定义模型）
5. WHEN 开发者选择仅支持基础能力的 CLI 工具（如 Codex）, THE CliSelector SHALL 仅展示一个步骤：选择工具
6. THE 后端 CliProviderConfig SHALL 新增可选的 `authOptions` 列表属性，描述该 CLI 工具支持的认证方案列表
7. THE 后端 `/cli-providers` 接口 SHALL 在响应中返回每个 Provider 的 `authOptions` 和 `authEnvVar` 属性
8. THE 前端 ICliProvider 接口 SHALL 新增 `authOptions` 和 `authEnvVar` 可选属性

### 需求 9：CLI 工具的认证差异化处理

**用户故事：** 作为开发者，我希望各 CLI 工具能按照各自的认证方式正确工作，在本地和沙箱模式下都能提供合适的认证选项，以便灵活选择认证方案。

#### 验收标准

1. WHEN 开发者选择 QoderCli, THE CliSelector SHALL 展示认证方案选择，提供「默认」和「Personal Access Token」两种选项
2. WHEN 开发者为 QoderCli 选择「默认」认证方案, THE CliSelector SHALL 不展示额外的认证输入表单，假设用户已通过 QoderCli 自身的登录机制完成认证
3. WHEN 开发者为 QoderCli 选择「Personal Access Token」认证方案, THE CliSelector SHALL 展示 Personal Access Token 输入表单
4. WHEN 开发者为 QoderCli 输入 Personal Access Token 并连接, THE 前端 SHALL 将 Personal Access Token 通过 CliSessionConfig 传递给后端
5. WHEN 后端收到 QoderCli 的 Personal Access Token 配置, THE 后端 SHALL 将 Personal Access Token 注入到 CLI 进程的环境变量中（本地模式和沙箱模式均适用）
6. THE QoderCli 的认证方案选择 SHALL 在本地运行时和 K8s 沙箱运行时下均可用，两种运行时共享相同的认证选项
7. WHEN 开发者选择 Kiro CLI 并使用 K8s 沙箱运行时, THE CliSelector SHALL 显示"沙箱认证待支持"提示，因为 Kiro CLI 仅支持 OAuth 三方登录，当前无法在沙箱中完成认证
8. WHEN 开发者选择 Claude Code 并使用 K8s 沙箱运行时, THE CliSelector SHALL 展示 API Key 输入表单（ANTHROPIC_API_KEY），因为沙箱环境中需要提供 Anthropic API Key
9. THE 后端 CliProviderConfig SHALL 新增可选的 `authEnvVar` 字符串属性，指定该 CLI 工具的 Token/API Key 对应的环境变量名（如 QoderCli 对应的 Personal Access Token 环境变量名、Claude Code 对应 `ANTHROPIC_API_KEY`）
10. THE 前端 ICliProvider 接口 SHALL 新增可选的 `authEnvVar` 字符串属性，用于前端判断该工具需要哪个环境变量的认证凭据
11. THE 后端 CliProviderConfig SHALL 新增可选的 `authOptions` 列表属性，描述该 CLI 工具支持的认证方案（如 `["default", "personal_access_token"]`），前端根据此列表渲染认证方案选择 UI
12. THE 前端 ICliProvider 接口 SHALL 新增可选的 `authOptions` 列表属性，与后端 `authOptions` 对应

### 需求 10：CLI 工具扩展配置路径的差异化处理

**用户故事：** 作为开发者，我希望各 CLI 工具的 MCP 和 Skill 配置能写入各自正确的配置路径，以便工具能正确加载扩展配置。

#### 验收标准

1. WHEN 后端为 Qwen Code 生成 MCP 配置, THE QwenCodeConfigGenerator SHALL 将 MCP Server 配置写入 `.qwen/settings.json` 的 `mcpServers` 字段
2. WHEN 后端为 Qwen Code 生成 Skill 配置, THE QwenCodeConfigGenerator SHALL 将 Skill 文件写入 `.qwen/skills/{skill-name}/SKILL.md` 路径
3. WHEN 后端为 Claude Code 生成 MCP 配置, THE ClaudeCodeConfigGenerator SHALL 将 MCP Server 配置写入 `.claude/settings.json` 或 Claude Code 官方文档指定的配置路径（待确认具体路径）
4. WHEN 后端为 QoderCli 生成 MCP 配置, THE QoderCliConfigGenerator SHALL 将 MCP Server 配置写入 `.qoder/` 目录下 QoderCli 官方文档指定的配置文件（待确认具体路径）
5. THE CliConfigGenerator 接口 SHALL 保持 `generateMcpConfig` 和 `generateSkillConfig` 方法的默认空实现，各 CLI 工具按需覆盖
6. WHEN 某个 CLI 工具未实现 CliConfigGenerator, THE 后端 SHALL 跳过该工具的扩展配置注入，不产生错误
7. THE 后端 AcpWebSocketHandler 的 configGeneratorRegistry SHALL 注册所有已实现 CliConfigGenerator 的 CLI 工具（当前已注册 opencode 和 qwen-code，需按需扩展）

### 需求 11：Kiro CLI 沙箱认证标记为待支持

**用户故事：** 作为开发者，我希望在选择 Kiro CLI 使用沙箱模式时能看到认证功能待支持的提示，以便了解当前限制和未来规划。

#### 验收标准

1. WHEN 开发者选择 Kiro CLI 并切换到 K8s 沙箱运行时, THE CliSelector SHALL 在连接按钮区域显示"Kiro CLI 沙箱认证待支持（仅支持 OAuth 登录）"提示信息
2. WHILE Kiro CLI 沙箱认证功能未实现, THE CliSelector SHALL 禁用 Kiro CLI 在 K8s 沙箱运行时下的连接按钮
3. WHEN 开发者选择 Kiro CLI 并使用本地运行时, THE CliSelector SHALL 正常允许连接，不显示待支持提示
4. THE 后端 CliProviderConfig SHALL 通过空的 `authOptions` 列表且无 `authEnvVar` 配置来标识 Kiro CLI 不支持沙箱认证
