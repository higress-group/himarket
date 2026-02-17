# 需求文档：Agent Skills 市场

## 简介

为 HiMarket AI 开放平台新增 "Agent Skill"（智能体技能）作为第四种 API 产品类型，与现有的 Model API、MCP Server、Agent API 并列。该功能涵盖管理端（himarket-admin）的技能创建与发布、开发者门户（himarket-frontend）的技能浏览与下载。技能基于开放的 SKILL.md 标准（YAML frontmatter + Markdown），无需订阅即可直接使用，复用平台现有的门户发布基础设施。

安装体验简洁直接：门户详情页提供技能下载链接，开发者可通过 curl 命令一键下载 SKILL.md 到本地项目的 `.agents/skills/<skill-name>/` 目录，或直接在页面上复制 SKILL.md 内容。

## 术语表

- **Skill（技能）**: 一种 API 产品类型（AGENT_SKILL），代表可被 AI 编程助手加载的智能体技能包，核心为 SKILL.md 文件
- **SKILL.md**: 技能的核心描述文件，采用 YAML frontmatter + Markdown 格式，定义技能的元数据和使用说明
- **SkillConfig（技能配置）**: 存储在 Product 实体中的技能专属配置，包含技能标签等信息
- **Admin_Portal（管理端）**: 管理员使用的后台管理界面（himarket-admin）
- **Developer_Portal（开发者门户）**: 开发者使用的前台浏览界面（himarket-frontend）
- **Product（产品）**: HiMarket 平台中的 API 产品实体，技能作为其中一种类型
- **Portal（门户）**: 多租户门户，每个门户拥有独立的产品目录和配置
- **Install_Command（安装命令）**: 开发者用于将技能下载到本地项目的命令，如 curl 下载

## 需求

### 需求 1：技能产品类型注册

**用户故事：** 作为平台管理员，我希望将 Agent Skill 注册为新的 API 产品类型，以便在现有产品管理体系中统一管理技能。

#### 验收标准

1. THE Product 实体 SHALL 支持 AGENT_SKILL 作为 ProductType 枚举值
2. WHEN 管理员创建产品时选择 AGENT_SKILL 类型，THE Admin_Portal SHALL 展示技能专属的配置表单
3. WHEN 创建 AGENT_SKILL 类型产品时，THE 后端 SHALL 在 Product 实体中存储 SkillConfig 配置信息
4. THE AGENT_SKILL 类型产品 SHALL 复用现有的产品发布到门户的流程

### 需求 2：技能元数据管理

**用户故事：** 作为平台管理员，我希望管理技能的元数据信息，以便开发者能够准确了解技能的用途。

#### 验收标准

1. WHEN 管理员创建或编辑技能时，THE Admin_Portal SHALL 提供名称、描述、分类标签的输入字段
2. WHEN 管理员保存技能元数据时，THE 后端 SHALL 将技能标签列表持久化到 SkillConfig 中
3. THE SkillConfig SHALL 包含 skillTags（技能标签列表）字段

### 需求 3：SKILL.md 在线编辑

**用户故事：** 作为平台管理员，我希望在管理端在线编辑 SKILL.md 文件，以便高效地创建和维护技能描述内容。

#### 验收标准

1. WHEN 管理员进入技能编辑页面时，THE Admin_Portal SHALL 展示 Markdown 编辑器和实时预览面板
2. WHEN 管理员编辑 SKILL.md 内容时，THE Admin_Portal SHALL 在预览面板中实时渲染 Markdown 内容
3. WHEN 管理员保存 SKILL.md 时，THE 后端 SHALL 将内容存储到 Product 的 document 字段中
4. IF SKILL.md 内容为空，THEN THE Admin_Portal SHALL 阻止保存并提示用户填写内容

### 需求 4：技能发布到门户

**用户故事：** 作为平台管理员，我希望将技能发布到指定门户，以便开发者可以在门户中浏览和使用技能。

#### 验收标准

1. WHEN 管理员发布技能到门户时，THE Admin_Portal SHALL 复用现有的产品发布流程
2. WHEN 技能发布成功后，THE Developer_Portal SHALL 在对应门户的技能列表中展示该技能
3. WHEN 管理员下线技能时，THE Developer_Portal SHALL 从对应门户的技能列表中移除该技能

### 需求 5：开发者门户技能列表页

**用户故事：** 作为开发者，我希望在门户中浏览和搜索技能，以便快速找到满足需求的技能。

#### 验收标准

1. WHEN 开发者访问技能页面时，THE Developer_Portal SHALL 展示技能卡片列表，每张卡片包含名称、描述和标签
2. WHEN 开发者输入搜索关键词时，THE Developer_Portal SHALL 按名称和描述过滤技能列表
3. WHEN 开发者选择分类筛选时，THE Developer_Portal SHALL 按所选分类过滤技能列表
4. THE Developer_Portal SHALL 在导航栏中新增 "Skills" 标签页，链接到技能列表页

### 需求 6：技能详情页与安装体验

**用户故事：** 作为开发者，我希望查看技能的详细信息并通过简单的命令快速下载安装技能。

#### 验收标准

1. WHEN 开发者进入技能详情页时，THE Developer_Portal SHALL 展示技能的完整描述和使用说明（渲染 SKILL.md 的 Markdown 内容）
2. WHEN 开发者查看安装方式时，THE Developer_Portal SHALL 展示 curl 下载命令（如 `curl -o SKILL.md <download-url>`）供开发者将 SKILL.md 下载到本地项目
3. WHEN 开发者点击复制按钮时，THE Developer_Portal SHALL 将下载命令复制到剪贴板
4. WHEN 开发者查看 SKILL.md 源码时，THE Developer_Portal SHALL 以代码高亮方式展示 SKILL.md 原始内容

### 需求 7：技能下载接口

**用户故事：** 作为 CLI 工具或开发者，我希望通过 API 下载技能的 SKILL.md 内容，以便安装到本地项目中。

#### 验收标准

1. WHEN 客户端请求下载技能时，THE 后端 SHALL 返回 SKILL.md 的原始文本内容
2. WHEN 技能被下载时，THE 后端 SHALL 递增该技能的下载次数计数器
3. IF 请求的技能不存在或未发布到当前门户，THEN THE 后端 SHALL 返回 404 错误

### 需求 8：SKILL.md 解析与序列化

**用户故事：** 作为系统，我需要正确解析和序列化 SKILL.md 格式，以便在存储、展示和下载环节保持数据一致性。

#### 验收标准

1. WHEN 接收到 SKILL.md 内容时，THE 后端 SHALL 解析 YAML frontmatter 并提取元数据字段
2. WHEN 序列化技能数据时，THE 后端 SHALL 生成符合 SKILL.md 标准的 YAML frontmatter + Markdown 格式
3. FOR ALL 合法的 SKILL.md 内容，解析后再序列化 SHALL 产生与原始内容等价的结果（往返一致性）
4. IF SKILL.md 格式不合法，THEN THE 后端 SHALL 返回描述性错误信息，指明格式问题所在
