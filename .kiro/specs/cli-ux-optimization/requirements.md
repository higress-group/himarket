# 需求文档

## 简介

优化 HiCli、HiCoding、HiWork 三个模块的页面交互体验，将当前 demo 级别的 UI 提升到产品级别。核心改进包括：CLI 工具选择器排序优化（Qwen Code 优先）、MCP/Skill 选择交互重新设计（卡片式选择）、CliSelector 整体布局优化（分步骤引导）、三个页面欢迎页统一美化、连接流程体验优化。本次为纯前端 UI/UX 优化，不涉及后端 API 变更。

## 术语表

- **CliSelector**：CLI 工具选择器组件，三个模块（HiWork、HiCoding、HiCli）共用的核心配置组件
- **CLI_Provider**：后端返回的 CLI 工具对象，包含 key、displayName、available、isDefault 等字段
- **MCP_Server**：Model Context Protocol 服务器，为 CLI 工具提供额外上下文能力
- **Skill**：市场中发布的技能包，包含 SKILL.md 内容，可注入到 CLI 会话中
- **MarketMcpSelector**：MCP Server 多选组件，当前使用 Checkbox.Group 实现
- **MarketSkillSelector**：Skill 多选组件，当前使用 Checkbox.Group 实现
- **RuntimeSelector**：运行时选择器组件，支持本地和 K8s 两种运行时
- **WelcomePage**：欢迎页组件，用户未连接 CLI 工具时展示的引导页面
- **StepWizard**：分步骤引导组件，将 CliSelector 的配置流程拆分为多个步骤

## 需求

### 需求 1：CLI 工具列表排序优化

**用户故事：** 作为开发者，我希望 Qwen Code 排在 CLI 工具列表的第一位，以便我能快速选择最常用的工具。

#### 验收标准

1. WHEN CLI_Provider 列表从 API 加载完成, THE CliSelector SHALL 按以下优先级排序展示：Qwen Code 排在第一位，其余可用工具按 API 返回顺序排列，不可用工具排在末尾
2. WHEN 排序后的列表中存在可用的 CLI_Provider, THE CliSelector SHALL 自动选中排序后的第一个可用 CLI_Provider
3. WHEN CLI_Provider 列表为空或全部不可用, THE CliSelector SHALL 展示"没有可用的 CLI 工具"提示信息

### 需求 2：MCP Server 选择交互重新设计

**用户故事：** 作为开发者，我希望 MCP Server 的选择体验更加直观和美观，以便我能快速浏览和选择需要的 MCP 服务。

#### 验收标准

1. WHEN MCP Server 列表加载完成, THE MarketMcpSelector SHALL 以卡片网格布局展示每个 MCP Server，每张卡片包含名称、描述信息
2. WHEN 用户点击一张 MCP Server 卡片, THE MarketMcpSelector SHALL 切换该卡片的选中状态并通过视觉高亮（边框颜色变化）反馈选中状态
3. WHEN MCP Server 列表包含 4 个以上条目, THE MarketMcpSelector SHALL 提供搜索过滤输入框，支持按名称和描述进行模糊匹配
4. WHEN 用户在搜索框中输入关键词, THE MarketMcpSelector SHALL 实时过滤并仅展示名称或描述中包含该关键词的 MCP Server 卡片
5. IF MCP Server 列表加载失败, THEN THE MarketMcpSelector SHALL 展示错误提示并提供重试按钮

### 需求 3：Skill 选择交互重新设计

**用户故事：** 作为开发者，我希望 Skill 的选择体验更加直观，以便我能快速浏览、搜索和选择需要的技能包。

#### 验收标准

1. WHEN Skill 列表加载完成, THE MarketSkillSelector SHALL 以卡片网格布局展示每个 Skill，每张卡片包含名称、描述和标签信息
2. WHEN 用户点击一张 Skill 卡片, THE MarketSkillSelector SHALL 切换该卡片的选中状态并通过视觉高亮反馈，同时在后台下载对应的 SKILL.md 内容
3. WHEN Skill 列表包含 4 个以上条目, THE MarketSkillSelector SHALL 提供搜索过滤输入框，支持按名称、描述和标签进行模糊匹配
4. WHEN 用户在搜索框中输入关键词, THE MarketSkillSelector SHALL 实时过滤并仅展示匹配的 Skill 卡片
5. WHEN 一个 Skill 正在下载 SKILL.md 内容, THE MarketSkillSelector SHALL 在对应卡片上展示加载指示器
6. IF Skill 列表加载失败, THEN THE MarketSkillSelector SHALL 展示错误提示并提供重试按钮

### 需求 4：CliSelector 分步骤布局优化

**用户故事：** 作为开发者，我希望 CLI 工具的配置流程有清晰的步骤引导，以便我能按顺序完成工具选择、模型配置、扩展配置等操作。

#### 验收标准

1. THE CliSelector SHALL 将配置流程拆分为以下步骤展示：步骤一"选择工具"（CLI 工具选择 + 运行时选择）、步骤二"模型配置"（自定义模型/市场模型，仅在 provider 支持时显示）、步骤三"扩展配置"（MCP Server + Skill 选择，仅在 provider 支持时显示）
2. WHEN 用户完成当前步骤的必填配置, THE CliSelector SHALL 启用"下一步"按钮允许用户进入下一步骤
3. WHEN 当前选中的 CLI_Provider 不支持自定义模型且不支持 MCP 和 Skill, THE CliSelector SHALL 跳过步骤二和步骤三，直接展示连接按钮
4. WHEN 用户处于步骤二或步骤三, THE CliSelector SHALL 提供"上一步"按钮允许用户返回修改之前的配置
5. THE CliSelector SHALL 在顶部展示步骤指示器，标明当前所处步骤及总步骤数
6. WHEN 用户到达最后一个步骤, THE CliSelector SHALL 将"下一步"按钮替换为"连接"按钮

### 需求 5：欢迎页统一设计

**用户故事：** 作为开发者，我希望 HiWork、HiCoding、HiCli 三个模块的欢迎页风格统一且美观，以便获得一致的产品体验。

#### 验收标准

1. THE WelcomePage SHALL 采用统一的布局结构：模块图标、模块名称、模块简要描述、CliSelector 或操作按钮
2. WHEN 用户未连接 CLI 工具, THE WelcomePage SHALL 展示模块图标、名称、描述文案和 CliSelector 组件
3. WHEN 用户已连接 CLI 工具, THE WelcomePage SHALL 展示"新建 Quest"操作按钮（HiWork 和 HiCli 模块）
4. THE WelcomePage SHALL 使用一致的间距、字体大小和颜色方案，三个模块之间视觉风格保持统一
5. THE WelcomePage SHALL 为每个模块展示差异化的图标和描述文案，以区分不同模块的功能定位

### 需求 6：模型配置模式优化

**用户故事：** 作为开发者，我希望模型配置的切换方式更加直观，以便我能快速在自定义模型和市场模型之间选择。

#### 验收标准

1. WHEN CLI_Provider 支持自定义模型, THE CliSelector SHALL 使用分段控制器（Segmented Control）展示三个互斥选项：默认模型、自定义模型、市场模型
2. WHEN 用户选择"自定义模型"选项, THE CliSelector SHALL 展开自定义模型表单
3. WHEN 用户选择"市场模型"选项, THE CliSelector SHALL 展开模型市场选择器
4. WHEN 用户选择"默认模型"选项, THE CliSelector SHALL 收起所有模型配置表单

### 需求 7：CLI 工具选择器视觉优化

**用户故事：** 作为开发者，我希望 CLI 工具选择器有更好的视觉呈现，以便我能直观地区分不同的工具。

#### 验收标准

1. THE CliSelector SHALL 使用卡片式单选布局替代下拉选择框展示 CLI 工具列表，每张卡片包含工具名称和可用状态
2. WHEN 用户点击一张 CLI 工具卡片, THE CliSelector SHALL 高亮选中该卡片并取消其他卡片的选中状态
3. WHEN 一个 CLI_Provider 不可用, THE CliSelector SHALL 将对应卡片置灰并标注"不可用"标签，禁止用户选择
