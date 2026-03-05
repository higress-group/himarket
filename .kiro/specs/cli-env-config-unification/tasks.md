# 实现计划：CLI 配置注入机制统一重构

## 概述

按照设计文档，分步完成 6 个 CLI Provider 到 4 个的精简、Claude Code 自定义模型支持、Qwen Code 配置优化、Claude Code MCP 路径迁移、以及 Claude Code / QoderCli 的 Skills 配置注入。任务按依赖顺序排列，每步可独立编译验证。

## 任务

- [x] 1. 移除废弃 CLI Provider（Kiro CLI、Codex CLI）
  - [x] 1.1 清理 application.yml 中的 kiro-cli 和 codex provider 配置
    - 删除 `himarket-bootstrap/src/main/resources/application.yml` 中 `acp.providers` 下的 `kiro-cli` 和 `codex` 两个 provider 配置块
    - 将 `acp.k8s.allowed-commands` 默认值从 `qodercli,qwen,npx,kiro-cli,opencode` 改为 `qodercli,qwen,npx,opencode`
    - 同步更新 `AcpProperties.K8sConfig` 中 `allowedCommands` 字段的默认值
    - _需求: 1.1, 1.2, 1.3_

  - [ ]* 1.2 编写单元测试验证 Provider 移除
    - 验证 application.yml 加载后 providers map 仅包含 qodercli、claude-code、qwen-code、opencode 四个 key
    - 验证 allowed-commands 不包含 kiro-cli
    - _需求: 1.1, 1.2, 1.3_

- [x] 2. Claude Code 新增自定义模型支持
  - [x] 2.1 改造 ClaudeCodeConfigGenerator.generateConfig() 方法
    - 修改 `ClaudeCodeConfigGenerator.generateConfig()` 使其不再返回空 Map
    - 构建并返回包含 `ANTHROPIC_API_KEY`、`ANTHROPIC_BASE_URL`、`ANTHROPIC_MODEL` 三个键的环境变量 Map
    - 创建 `.claude/` 目录，读取已有 `settings.json`（调用 `readExistingConfig`）
    - 合并写入 `env` 字段（包含 ANTHROPIC_API_KEY、ANTHROPIC_BASE_URL、ANTHROPIC_MODEL）和 `model` 字段
    - 保留已有 settings.json 中的其他字段（如 mcpServers）不被覆盖
    - _需求: 2.1, 2.2, 2.3_

  - [ ]* 2.2 编写属性测试：环境变量完整性
    - **Property 1: 环境变量完整性**
    - 对于任意有效的 CustomModelConfig，调用 `generateConfig()` 返回的 Map 必须包含 `ANTHROPIC_API_KEY`、`ANTHROPIC_BASE_URL`、`ANTHROPIC_MODEL` 三个键且值非空
    - **验证: 需求 2.1**

  - [ ]* 2.3 编写属性测试：配置文件合并保留
    - **Property 2: 配置文件合并保留**
    - 对于任意已存在任意字段的 `.claude/settings.json`，调用 `generateConfig()` 后原有非冲突字段应全部保留
    - **验证: 需求 2.3**

  - [ ]* 2.4 编写属性测试：settings.json 内容正确性
    - **Property 3: Claude Code settings.json 内容正确性**
    - 对于任意有效的 CustomModelConfig，调用 `generateConfig()` 后 `.claude/settings.json` 必须包含 `env` 字段和 `model` 字段
    - **验证: 需求 2.2**

- [x] 3. 更新 application.yml 中 claude-code Provider 属性
  - 在 `himarket-bootstrap/src/main/resources/application.yml` 的 `claude-code` provider 配置中新增：
    - `supports-custom-model: true`
    - `supports-mcp: true`
    - `supports-skill: true`
  - _需求: 2.4, 5.4_

- [x] 4. Checkpoint - 确保编译通过
  - 确保所有代码编译通过，运行已有测试无回归，如有问题请向用户确认。

- [x] 5. Qwen Code 配置优化 - 移除 env 字段写入
  - [x] 5.1 修改 QwenCodeConfigGenerator.mergeCustomModelProvider() 方法
    - 移除 `mergeCustomModelProvider()` 中写入 `env` 字段到 settings.json 的代码（即删除 `root.put("env", env)` 相关逻辑）
    - 保留 `modelProviders`、`security.auth.selectedType`、`model.name`、`tools.approvalMode` 的写入逻辑不变
    - API Key 仅通过 `generateConfig()` 返回的环境变量 Map 注入，不再写入配置文件
    - _需求: 3.1, 3.2, 3.3_

  - [ ]* 5.2 编写属性测试：Qwen Code settings.json 不含 env 字段
    - **Property 4: Qwen Code settings.json 不含 env 字段**
    - 对于任意有效的 CustomModelConfig，调用 `generateConfig()` 后 `.qwen/settings.json` 不包含 `env` 字段，但包含 `modelProviders`、`security`、`model`、`tools` 字段
    - **验证: 需求 3.2, 3.3**

- [x] 6. Claude Code MCP 配置路径迁移
  - [x] 6.1 修改 ClaudeCodeConfigGenerator.generateMcpConfig() 方法
    - 将 MCP 配置写入路径从 `.claude/settings.json` 改为 `{workingDirectory}/.mcp.json`
    - 读取已有 `.mcp.json` 文件（如存在），合并 mcpServers 配置
    - 不再将 mcpServers 写入 `.claude/settings.json`
    - _需求: 4.1, 4.2, 4.3_

  - [ ]* 6.2 编写属性测试：MCP 配置路径正确性
    - **Property 5: Claude Code MCP 配置路径正确性**
    - 对于任意有效的 MCP Server 列表，调用 `generateMcpConfig()` 后 mcpServers 应存在于 `.mcp.json` 中，且 `.claude/settings.json` 中不包含 mcpServers 字段
    - **验证: 需求 4.1, 4.2**

- [x] 7. Claude Code 新增 generateSkillConfig 实现
  - [x] 7.1 在 ClaudeCodeConfigGenerator 中实现 generateSkillConfig() 方法
    - 覆盖 `CliConfigGenerator` 接口的 `generateSkillConfig()` 默认方法
    - 为每个 Skill 在 `.claude/skills/{kebab-case-name}/` 目录下创建文件
    - 支持 `files` 列表（新路径：写入完整目录结构，含 base64 解码）和向后兼容（仅写 SKILL.md）
    - 复用 `toKebabCase()` 工具方法（可从 QwenCodeConfigGenerator 提取或复制）
    - Skills 列表为空或 null 时直接返回，不创建任何目录
    - _需求: 5.1, 5.2, 5.3, 9.2_

  - [ ]* 7.2 编写属性测试：Skills 文件创建与格式正确性
    - **Property 6: Skills 文件创建与格式正确性**
    - 对于任意非空的 Skills 列表，调用 `generateSkillConfig()` 后每个 Skill 应在 `.claude/skills/{name}/` 下创建对应文件
    - **验证: 需求 5.1, 5.2**

- [x] 8. QoderCli 新增 generateSkillConfig 实现
  - [x] 8.1 在 QoderCliConfigGenerator 中实现 generateSkillConfig() 方法
    - 覆盖 `CliConfigGenerator` 接口的 `generateSkillConfig()` 默认方法
    - 为每个 Skill 在 `.qoder/skills/{kebab-case-name}/` 目录下创建文件
    - 实现逻辑与 ClaudeCodeConfigGenerator.generateSkillConfig() 一致（目录前缀不同）
    - Skills 列表为空或 null 时直接返回
    - _需求: 6.1, 6.2, 6.3, 9.1_

  - [ ]* 8.2 编写属性测试：QoderCli Skills 目录隔离
    - **Property 7: Skills 目录隔离性**
    - 对于任意 Skill 名称，QoderCli 应在 `.qoder/skills/` 下创建文件，与其他 CLI 的 Skills 目录互不影响
    - **验证: 需求 6.1, 9.1, 9.5**

- [x] 9. 最终 Checkpoint - 全量验证
  - 确保所有代码编译通过，所有测试通过，如有问题请向用户确认。
  - 使用 `./scripts/run.sh` 重启后端验证无启动异常。

## 备注

- 标记 `*` 的子任务为可选测试任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- OpenCodeConfigGenerator 当前实现已符合设计要求，无需改造
- `toKebabCase()` 工具方法在 QwenCodeConfigGenerator 和 OpenCodeConfigGenerator 中已有实现，新增的 ClaudeCode 和 QoderCli 可复用相同逻辑
- 属性测试验证设计文档中定义的 7 个正确性属性
