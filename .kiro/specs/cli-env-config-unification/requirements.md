# 需求文档：CLI 配置注入机制统一重构

## 简介

HiMarket 平台当前支持 6 个 CLI Provider，其中 Kiro CLI 和 Codex CLI 已不再维护。现有的认证和模型配置注入方式不统一，部分 CLI 将敏感信息写入配置文件。本次重构旨在移除废弃 Provider、统一认证注入方式为环境变量、新增 Claude Code 自定义模型支持、迁移 Claude Code MCP 配置路径、并为 Claude Code 和 QoderCli 新增 Skills 配置注入能力。

## 术语表

- **ConfigGenerator**：实现 `CliConfigGenerator` 接口的各 CLI 配置生成器，负责生成环境变量和配置文件
- **CustomModelConfig**：自定义模型配置对象，包含 apiKey、baseUrl、modelId、protocolType 等字段
- **RuntimeConfig**：运行时配置对象，包含注入到 CLI 进程的环境变量 Map（`env` 字段）
- **SettingsFile**：各 CLI 工具的 JSON 配置文件（如 `.claude/settings.json`、`.qwen/settings.json`、`opencode.json`）
- **McpConfig**：MCP（Model Context Protocol）Server 配置，定义 CLI 可连接的外部工具服务
- **SkillEntry**：Skills 配置条目，包含 name、description 和 Markdown 内容，写入 `SKILL.md` 文件
- **Provider**：CLI 工具的标识符，用于在 application.yml 中注册和查找对应的 CLI 配置

## 需求

### 需求 1：移除废弃 CLI Provider

**用户故事：** 作为平台维护者，我希望移除不再维护的 Kiro CLI 和 Codex CLI Provider 配置及相关代码，以减少维护负担并避免混淆。

#### 验收标准

1. WHEN 系统加载 application.yml 配置时，THE ConfigGenerator SHALL 仅注册 QoderCli、Claude Code、Qwen Code、OpenCode 四个 Provider
2. WHEN 代码编译完成后，THE 系统 SHALL 不包含任何对 `kiro-cli` 或 `codex` Provider key 的硬编码引用
3. WHEN K8s 运行时加载 allowed-commands 配置时，THE 系统 SHALL 不包含 `kiro-cli` 命令

### 需求 2：Claude Code 自定义模型支持

**用户故事：** 作为开发者，我希望 Claude Code 支持自定义模型配置（通过代理或第三方端点），以便在受限网络环境中使用 Claude Code。

#### 验收标准

1. WHEN Claude Code ConfigGenerator 接收到有效的 CustomModelConfig 时，THE ConfigGenerator SHALL 返回包含 `ANTHROPIC_API_KEY`、`ANTHROPIC_BASE_URL`、`ANTHROPIC_MODEL` 三个键的环境变量 Map
2. WHEN Claude Code ConfigGenerator 生成配置时，THE ConfigGenerator SHALL 在 `.claude/settings.json` 中写入 `env` 字段（包含 ANTHROPIC_API_KEY、ANTHROPIC_BASE_URL、ANTHROPIC_MODEL）和 `model` 字段
3. WHEN `.claude/settings.json` 已存在其他字段（如 mcpServers）时，THE ConfigGenerator SHALL 保留已有字段，仅合并新增的 `env` 和 `model` 字段
4. WHEN application.yml 中 claude-code Provider 配置加载时，THE 系统 SHALL 包含 `supports-custom-model: true` 属性

### 需求 3：Qwen Code 配置优化

**用户故事：** 作为平台维护者，我希望 Qwen Code 的 API Key 不再写入配置文件的 `env` 字段，以减少敏感信息在磁盘上的暴露。

#### 验收标准

1. WHEN Qwen Code ConfigGenerator 生成配置时，THE ConfigGenerator SHALL 返回包含协议对应 API Key 环境变量的 Map（如 `OPENAI_API_KEY`、`ANTHROPIC_API_KEY` 或 `GOOGLE_API_KEY`）
2. WHEN Qwen Code ConfigGenerator 写入 settings.json 时，THE ConfigGenerator SHALL 不包含 `env` 字段
3. WHEN Qwen Code ConfigGenerator 写入 settings.json 时，THE ConfigGenerator SHALL 保留 `modelProviders`、`security.auth.selectedType`、`model.name`、`tools.approvalMode` 字段
4. WHEN `.qwen/settings.json` 已存在其他字段时，THE ConfigGenerator SHALL 保留已有字段不被覆盖

### 需求 4：Claude Code MCP 配置路径迁移

**用户故事：** 作为开发者，我希望 Claude Code 的 MCP 配置使用官方推荐的 `.mcp.json` 文件路径，以便与 Claude Code 官方文档保持一致并支持 git 提交。

#### 验收标准

1. WHEN Claude Code ConfigGenerator 生成 MCP 配置时，THE ConfigGenerator SHALL 将 mcpServers 写入 `{workingDirectory}/.mcp.json` 文件
2. WHEN Claude Code ConfigGenerator 生成 MCP 配置时，THE ConfigGenerator SHALL 不再将 mcpServers 写入 `.claude/settings.json`
3. WHEN `.mcp.json` 文件已存在时，THE ConfigGenerator SHALL 合并新的 mcpServers 配置而非覆盖整个文件
4. WHILE 其他 CLI Provider（QoderCli、Qwen Code、OpenCode）生成 MCP 配置时，THE ConfigGenerator SHALL 保持各自原有的 MCP 配置文件路径不变

### 需求 5：Claude Code Skills 配置注入

**用户故事：** 作为开发者，我希望 Claude Code 支持 Skills 配置注入，以便在 Claude Code 中使用平台提供的自定义 Skill。

#### 验收标准

1. WHEN Claude Code ConfigGenerator 接收到非空的 Skills 列表时，THE ConfigGenerator SHALL 为每个 Skill 在 `.claude/skills/{kebab-case-name}/` 目录下创建 `SKILL.md` 文件
2. WHEN Claude Code ConfigGenerator 写入 SKILL.md 时，THE ConfigGenerator SHALL 包含 YAML frontmatter（至少包含 `name` 和 `description` 字段）和 Markdown 正文内容
3. WHEN Skills 列表为空或 null 时，THE ConfigGenerator SHALL 不创建任何 Skills 目录或文件
4. WHEN application.yml 中 claude-code Provider 配置加载时，THE 系统 SHALL 包含 `supports-skill: true` 属性

### 需求 6：QoderCli Skills 配置注入

**用户故事：** 作为开发者，我希望 QoderCli 支持 Skills 配置注入，以便在 QoderCli 中使用平台提供的自定义 Skill。

#### 验收标准

1. WHEN QoderCli ConfigGenerator 接收到非空的 Skills 列表时，THE ConfigGenerator SHALL 为每个 Skill 在 `.qoder/skills/{kebab-case-name}/` 目录下创建 `SKILL.md` 文件
2. WHEN QoderCli ConfigGenerator 写入 SKILL.md 时，THE ConfigGenerator SHALL 包含 YAML frontmatter（至少包含 `name` 和 `description` 字段）和 Markdown 正文内容
3. WHEN Skills 列表为空或 null 时，THE ConfigGenerator SHALL 不创建任何 Skills 目录或文件

### 需求 7：认证信息环境变量统一注入

**用户故事：** 作为平台维护者，我希望所有 CLI 的认证信息统一通过进程环境变量注入，以确保敏感凭证不依赖配置文件明文存储。

#### 验收标准

1. THE ConfigGenerator SHALL 通过 `RuntimeConfig.env` 将所有认证信息（API Key、Token）注入到 CLI 进程环境变量中
2. WHEN QoderCli 启动时，THE 系统 SHALL 通过 `QODER_PERSONAL_ACCESS_TOKEN` 环境变量注入认证 Token
3. WHEN Claude Code 启动时，THE 系统 SHALL 通过 `ANTHROPIC_API_KEY` 环境变量注入 API Key
4. WHEN Qwen Code 启动时，THE 系统 SHALL 通过协议对应的环境变量（`OPENAI_API_KEY` / `ANTHROPIC_API_KEY` / `GOOGLE_API_KEY`）注入 API Key
5. WHEN OpenCode 启动时，THE 系统 SHALL 通过 `CUSTOM_MODEL_API_KEY` 环境变量注入 API Key

### 需求 8：配置文件写入容错处理

**用户故事：** 作为平台维护者，我希望配置文件写入失败时系统能优雅降级，以确保沙箱初始化流程不被阻断。

#### 验收标准

1. IF 配置文件写入时发生 IOException，THEN THE ConfigGenerator SHALL 记录错误日志并继续执行后续流程
2. IF 已有配置文件的 JSON 格式不合法，THEN THE ConfigGenerator SHALL 记录 warn 日志并使用全新配置覆盖
3. WHILE 配置文件写入失败时，THE 系统 SHALL 确保环境变量仍然正常注入到 CLI 进程

### 需求 9：Skills 目录隔离

**用户故事：** 作为平台维护者，我希望各 CLI 的 Skills 文件写入各自独立的目录，以避免不同 CLI 工具之间的配置冲突。

#### 验收标准

1. THE ConfigGenerator SHALL 将 QoderCli 的 Skills 写入 `.qoder/skills/` 目录
2. THE ConfigGenerator SHALL 将 Claude Code 的 Skills 写入 `.claude/skills/` 目录
3. THE ConfigGenerator SHALL 将 Qwen Code 的 Skills 写入 `.qwen/skills/` 目录
4. THE ConfigGenerator SHALL 将 OpenCode 的 Skills 写入 `.opencode/skills/` 目录
5. WHEN 多个 CLI Provider 同时配置相同名称的 Skill 时，THE ConfigGenerator SHALL 在各自目录下独立创建文件，互不影响
