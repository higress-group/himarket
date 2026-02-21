# 需求文档：Skill 市场 V2 — 多安装源存储与分发

## 简介

HiMarket 平台当前的 Agent Skill 市场将 Skill 简化为单个 SKILL.md 文件存储在数据库 `document` 字段中，下载接口仅返回纯文本 SKILL.md。然而，主流 Agent Skill 标准（Anthropic Skills、Vercel AI SDK、playbooks.com 等）中，一个 Skill 是一个目录结构，包含 `SKILL.md` + `scripts/` + `references/` + `assets/` 等多文件。

本次升级采用混合模式：HiMarket 作为 Skill 的索引/注册中心，不引入单独的文件存储服务，支持多种安装途径（Git 仓库下载、npx 安装、直接文件下载）。根据 Skill 的来源类型，平台展示对应的安装命令。SKILL.md 内容仍可存储在数据库中用于预览展示。

## 术语表

- **Skill（技能）**: 一种 API 产品类型（AGENT_SKILL），代表可被 AI 编程助手加载的智能体技能包
- **SKILL.md**: 技能的核心描述文件，采用 YAML frontmatter + Markdown 格式
- **SkillConfig（技能配置）**: 存储在 Product 实体 feature JSON 字段中的技能专属配置
- **InstallSource（安装源）**: 描述 Skill 安装来源的配置对象，包含类型、地址、子路径等信息
- **InstallSourceType（安装源类型）**: 枚举值，包括 GIT（Git 仓库）、NPM（npm 包）、DOWNLOAD（直接下载）
- **Admin_Portal（管理端）**: 管理员使用的后台管理界面（himarket-admin）
- **Developer_Portal（开发者门户）**: 开发者使用的前台浏览界面（himarket-frontend）
- **Product（产品）**: HiMarket 平台中的 API 产品实体，技能作为其中一种类型
- **CLI_Tool（CLI 工具）**: 开发者使用的命令行 AI 编程助手，如 Qwen Code、Kiro CLI 等
- **SkillMdParser（SKILL.md 解析器）**: 后端解析 SKILL.md YAML frontmatter 和 Markdown 内容的组件

## 需求

### 需求 1：SkillConfig 数据模型扩展 — 新增安装源信息

**用户故事：** 作为平台管理员，我希望为每个 Skill 指定安装来源信息，以便平台能够根据不同来源类型向开发者展示正确的安装方式。

#### 验收标准

1. THE SkillConfig SHALL 新增 installSource 字段，类型为 InstallSource 对象
2. THE InstallSource SHALL 包含以下字段：type（InstallSourceType 枚举，必填）、url（安装地址，对 GIT 和 NPM 类型必填）、subPath（仓库子路径，可选，仅 GIT 类型使用）
3. THE InstallSourceType 枚举 SHALL 包含三个值：GIT（Git 仓库来源）、NPM（npm 包来源）、DOWNLOAD（直接下载来源）
4. WHEN InstallSource 的 type 为 GIT 时，THE url 字段 SHALL 存储 Git 仓库的 HTTPS 克隆地址
5. WHEN InstallSource 的 type 为 NPM 时，THE url 字段 SHALL 存储 npm 包名称（如 `@anthropic/skill-name`）
6. WHEN InstallSource 的 type 为 DOWNLOAD 时，THE url 字段 SHALL 为空或 null，系统使用现有的 document 字段下载逻辑
7. THE SkillConfig SHALL 保留现有的 skillTags 和 downloadCount 字段，保持向后兼容
8. WHEN 现有 Skill 产品的 installSource 字段为 null 时，THE 系统 SHALL 将其视为 DOWNLOAD 类型（默认行为）

### 需求 2：管理端 Skill 创建与编辑 — 安装源配置

**用户故事：** 作为平台管理员，我希望在创建或编辑 Skill 时能够指定安装源类型和地址，以便灵活管理不同来源的 Skill。

#### 验收标准

1. WHEN 管理员进入 Skill 创建或编辑页面时，THE Admin_Portal SHALL 展示安装源类型选择器，提供 GIT、NPM、DOWNLOAD 三个选项
2. WHEN 管理员选择 GIT 类型时，THE Admin_Portal SHALL 展示 Git 仓库 URL 输入框和可选的子路径输入框
3. WHEN 管理员选择 NPM 类型时，THE Admin_Portal SHALL 展示 npm 包名称输入框
4. WHEN 管理员选择 DOWNLOAD 类型时，THE Admin_Portal SHALL 隐藏 URL 输入框，仅保留现有的 SKILL.md 在线编辑器
5. WHEN 管理员保存 Skill 时，THE 后端 SHALL 将 InstallSource 信息持久化到 SkillConfig 中
6. THE Admin_Portal SHALL 对 GIT 和 NPM 类型验证 url 字段非空，阻止空地址保存
7. THE Admin_Portal SHALL 继续支持在线编辑 SKILL.md 内容（用于预览展示），无论安装源类型为何

### 需求 3：管理端从 Git 仓库拉取 SKILL.md 预览内容

**用户故事：** 作为平台管理员，我希望系统能从 Git 仓库自动拉取 SKILL.md 内容用于预览，以减少手动复制粘贴的工作量。

#### 验收标准

1. WHEN 管理员在 GIT 类型的 Skill 编辑页面点击"从仓库拉取"按钮时，THE Admin_Portal SHALL 调用后端接口获取指定 Git 仓库中的 SKILL.md 内容
2. WHEN 后端接收到拉取请求时，THE 后端 SHALL 通过 Git 仓库的 raw 文件 URL（如 GitHub raw.githubusercontent.com）获取 SKILL.md 内容
3. WHEN 拉取成功时，THE Admin_Portal SHALL 将获取的内容填充到 SKILL.md 编辑器中，供管理员确认或修改
4. IF Git 仓库 URL 格式不合法或无法访问，THEN THE 后端 SHALL 返回描述性错误信息
5. IF 指定路径下不存在 SKILL.md 文件，THEN THE 后端 SHALL 返回提示信息，指明文件未找到
6. WHEN InstallSource 包含 subPath 时，THE 后端 SHALL 在 subPath 目录下查找 SKILL.md 文件

### 需求 4：开发者门户详情页 — 多安装源展示

**用户故事：** 作为开发者，我希望在 Skill 详情页看到与该 Skill 安装源类型匹配的安装命令，以便快速正确地安装 Skill。

#### 验收标准

1. WHEN 开发者进入 GIT 类型 Skill 的详情页时，THE Developer_Portal SHALL 展示 `git clone` 命令，包含仓库 URL 和子路径信息（如有）
2. WHEN 开发者进入 NPM 类型 Skill 的详情页时，THE Developer_Portal SHALL 展示 `npx`/`bunx`/`pnpm dlx` 安装命令，包含 npm 包名称
3. WHEN 开发者进入 DOWNLOAD 类型 Skill 的详情页时，THE Developer_Portal SHALL 展示 curl 下载命令和"复制 SKILL.md 内容"按钮（保留现有行为）
4. THE Developer_Portal SHALL 为每条安装命令提供一键复制按钮
5. WHEN Skill 的 installSource 为 null 时，THE Developer_Portal SHALL 按 DOWNLOAD 类型展示安装命令（向后兼容）
6. THE Developer_Portal SHALL 继续展示 SKILL.md 的 Markdown 渲染预览，无论安装源类型为何

### 需求 5：Skill 下载接口扩展 — 返回安装源元数据

**用户故事：** 作为 CLI 工具或前端客户端，我希望通过 API 获取 Skill 的安装源信息，以便根据来源类型执行正确的安装流程。

#### 验收标准

1. THE 后端 SHALL 新增 `GET /skills/{productId}/install-info` 接口，返回 Skill 的安装源元数据
2. THE 接口 SHALL 返回以下信息：installSourceType（GIT/NPM/DOWNLOAD）、url（安装地址）、subPath（子路径，仅 GIT 类型）、skillName（技能名称）、downloadUrl（SKILL.md 下载地址，仅 DOWNLOAD 类型）
3. WHEN 请求的 Skill 不存在或未发布时，THE 接口 SHALL 返回 404 错误
4. THE 现有 `GET /skills/{productId}/download` 接口 SHALL 保持不变，继续返回 SKILL.md 纯文本内容（向后兼容）
5. WHEN 调用 install-info 接口时，THE 后端 SHALL 递增该 Skill 的下载次数计数器

### 需求 6：CLI Skill 注入逻辑适配多安装源

**用户故事：** 作为使用 CLI 工具的开发者，我希望系统根据 Skill 的安装源类型自动执行正确的安装操作，以便在 CLI 会话中使用完整的 Skill 目录结构。

#### 验收标准

1. WHEN CLI 工具注入 DOWNLOAD 类型的 Skill 时，THE CliConfigGenerator SHALL 保留现有逻辑，将 SKILL.md 内容写入 `.agents/skills/<skill-name>/SKILL.md`（或对应 CLI 工具的目录）
2. WHEN CLI 工具注入 GIT 类型的 Skill 时，THE CliConfigGenerator SHALL 执行 `git clone` 命令将仓库克隆到工作目录的 `.agents/skills/<skill-name>/` 目录
3. WHEN GIT 类型的 Skill 包含 subPath 时，THE CliConfigGenerator SHALL 仅将 subPath 指定的子目录内容复制到目标目录
4. WHEN CLI 工具注入 NPM 类型的 Skill 时，THE CliConfigGenerator SHALL 执行 `npx <package-name>` 命令在工作目录中安装 Skill
5. IF git clone 或 npx 命令执行失败，THEN THE CliConfigGenerator SHALL 记录错误日志并回退到使用 SKILL.md 内容写入的降级策略（如果 SKILL.md 内容可用）
6. IF 降级策略也不可用（SKILL.md 内容为空），THEN THE CliConfigGenerator SHALL 记录警告日志并跳过该 Skill 的注入，继续处理其他 Skill

### 需求 7：CliSessionConfig 扩展 — 传递安装源信息

**用户故事：** 作为平台开发者，我希望 CliSessionConfig 中的 Skill 信息包含安装源元数据，以便后端 CliConfigGenerator 根据来源类型执行正确的注入逻辑。

#### 验收标准

1. THE CliSessionConfig.SkillEntry SHALL 新增 installSourceType 字段（GIT/NPM/DOWNLOAD）
2. THE CliSessionConfig.SkillEntry SHALL 新增 installSourceUrl 字段（安装地址）
3. THE CliSessionConfig.SkillEntry SHALL 新增 subPath 字段（仓库子路径，可选）
4. THE CliSessionConfig.SkillEntry SHALL 保留现有的 name 和 skillMdContent 字段，保持向后兼容
5. WHEN CliSessionConfig 中的 SkillEntry 缺少 installSourceType 字段时，THE 系统 SHALL 将其视为 DOWNLOAD 类型（向后兼容）
6. FOR ALL 合法的 CliSessionConfig 对象（含新增字段），序列化为 JSON 再反序列化 SHALL 产生等价的对象（往返一致性）

### 需求 8：前端 Skill 选择器适配 — 传递安装源信息

**用户故事：** 作为 HiCli/HiCoding/HiWork 用户，我希望在选择市场 Skill 时，系统自动获取并传递 Skill 的安装源信息，以便后端执行正确的安装操作。

#### 验收标准

1. THE `GET /cli-providers/market-skills` 接口 SHALL 在响应中新增 installSourceType、installSourceUrl 和 subPath 字段
2. WHEN 用户在 CliSelector 中选择 Skill 时，THE 前端 SHALL 将安装源信息一并存储到 CliSessionConfig.SkillEntry 中
3. WHEN Skill 的 installSource 为 null 时，THE 接口 SHALL 返回 installSourceType 为 DOWNLOAD，installSourceUrl 为 null（向后兼容）

### 需求 9：向后兼容保障

**用户故事：** 作为平台用户，我希望现有的 Skill 功能在升级后继续正常工作，不受新安装源机制的影响。

#### 验收标准

1. THE 现有 `GET /skills/{productId}/download` 接口 SHALL 保持原有行为不变，返回 SKILL.md 纯文本内容
2. WHEN 现有 Skill 产品的 SkillConfig 中不包含 installSource 字段时，THE 系统 SHALL 在所有环节将其视为 DOWNLOAD 类型
3. THE 现有的 CLI Skill 注入逻辑（写入 SKILL.md 文件）SHALL 作为 DOWNLOAD 类型的默认行为继续工作
4. THE 现有的开发者门户 Skill 列表页和详情页 SHALL 对无 installSource 的 Skill 保持现有展示效果
5. THE 数据库 schema 变更 SHALL 通过 Flyway 迁移脚本执行，不影响现有数据

### 需求 10：InstallSource 数据验证

**用户故事：** 作为系统，我需要确保 InstallSource 数据的完整性和合法性，以防止无效配置导致安装失败。

#### 验收标准

1. WHEN InstallSource 的 type 为 GIT 时，THE 后端 SHALL 验证 url 字段为合法的 Git HTTPS URL 格式（以 https:// 开头，以 .git 结尾或为合法的 Git 托管平台 URL）
2. WHEN InstallSource 的 type 为 NPM 时，THE 后端 SHALL 验证 url 字段为合法的 npm 包名称格式（符合 npm 命名规范）
3. WHEN InstallSource 的 type 为 DOWNLOAD 时，THE 后端 SHALL 允许 url 字段为空
4. IF InstallSource 验证失败，THEN THE 后端 SHALL 返回 400 错误，包含具体的验证失败原因
5. FOR ALL 合法的 InstallSource 对象，序列化为 JSON 再反序列化 SHALL 产生等价的对象（往返一致性）
