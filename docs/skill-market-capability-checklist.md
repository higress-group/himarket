# Skill 市场能力需求清单

> 本文档基于 HiMarket 现有 Skill 市场的功能梳理，提炼出 Skill 管理组件需要提供的通用接口和能力要求。
> 目标：作为 Nacos Skill 管理能力的对齐清单，确保原子能力覆盖完整。

## 一、Skill 生命周期管理

### 1.1 基础 CRUD

| 序号 | 能力 | 方法 | 说明 |
|------|------|------|------|
| 1 | 创建 Skill | POST | 创建一个 Skill 记录，包含名称、描述、图标、标签等元数据 |
| 2 | 查询 Skill 列表 | GET | 分页查询，支持按名称、状态、分类等条件筛选 |
| 3 | 查询 Skill 详情 | GET | 根据 Skill ID 返回完整信息，包含元数据、标签、下载计数等 |
| 4 | 更新 Skill | PUT | 更新名称、描述、图标、标签等元数据（部分更新） |
| 5 | 删除 Skill | DELETE | 删除 Skill 及其关联的所有文件数据 |

### 1.2 发布与下线

| 序号 | 能力 | 方法 | 说明 |
|------|------|------|------|
| 6 | 发布 Skill | POST | 将 Skill 设为已发布状态，对外可见可下载 |
| 7 | 下线 Skill | DELETE | 取消发布，不再对外展示 |
| 8 | 查询发布记录 | GET | 分页查询 Skill 的发布历史 |

### 1.3 分类管理

| 序号 | 能力 | 方法 | 说明 |
|------|------|------|------|
| 9 | 设置分类 | POST | 为 Skill 绑定一个或多个分类标签 |
| 10 | 查询分类 | GET | 获取 Skill 已绑定的分类列表 |

## 二、Skill 包管理

### 2.1 上传与解析

| 序号 | 能力 | 方法 | 说明 |
|------|------|------|------|
| 11 | 上传 Skill 包 | POST | 接收 ZIP 或 TAR.GZ 压缩包，自动解析并存储所有文件；替换式更新（每次上传覆盖旧文件） |

上传时需自动完成：
- 解压并遍历所有文件
- 查找并解析 SKILL.md 的 YAML frontmatter，提取 `name` 和 `description`
- 自动检测文件编码（文本 / 二进制）
- 过滤隐藏文件（`.` 开头）和路径穿越条目（含 `..`）
- 如果所有文件在同一顶层目录下，自动剥离该目录前缀

### 2.2 下载与分发

| 序号 | 能力 | 方法 | 说明 |
|------|------|------|------|
| 12 | 下载 SKILL.md | GET | 返回 SKILL.md 原始 Markdown 内容；需校验 Skill 已发布；自动递增下载计数 |
| 13 | 下载完整 Skill 包 | GET | 将所有文件打包为 ZIP 流式返回 |

### 2.3 文件浏览

| 序号 | 能力 | 方法 | 说明 |
|------|------|------|------|
| 14 | 获取文件树 | GET | 返回树形目录结构（目录优先排序），不含文件内容 |
| 15 | 获取所有文件（含内容） | GET | 返回所有文件的路径、内容、编码、大小 |
| 16 | 获取单个文件内容 | GET | 按路径获取指定文件的内容 |

### 2.4 SKILL.md 独立更新

| 序号 | 能力 | 方法 | 说明 |
|------|------|------|------|
| 17 | 更新 SKILL.md | PUT | 单独更新 SKILL.md 内容，同步更新元数据和文件存储 |

## 三、查询与检索

### 3.1 精简列表接口（供 CLI / SDK 消费）

| 序号 | 能力 | 方法 | 说明 |
|------|------|------|------|
| 18 | 获取已发布 Skill 列表 | GET | 返回所有已发布 Skill 的精简信息，供命令行工具和 SDK 快速获取可用 Skill |

返回字段：
- `skillId` — 唯一标识
- `name` — Skill 名称
- `description` — 描述
- `skillTags` — 标签列表

### 3.2 列表查询支持的筛选条件

| 参数 | 类型 | 说明 |
|------|------|------|
| status | String | 状态筛选（草稿 / 就绪 / 已发布） |
| name | String | 按名称模糊搜索 |
| categoryIds | List\<String\> | 按分类筛选 |
| excludeCategoryId | String | 排除某个分类 |
| page / size | int | 分页参数 |


## 四、数据模型

### 4.1 Skill 元数据

| 字段 | 类型 | 说明 |
|------|------|------|
| skillId | String | 唯一标识 |
| name | String(50) | Skill 名称 |
| description | String(1000) | Skill 描述 |
| status | Enum | `DRAFT` → `READY` → `PUBLISHED` |
| document | Text | SKILL.md 原始内容（冗余存储，方便快速读取） |
| icon | JSON | 图标信息 |
| skillTags | List\<String\> | 标签列表 |
| downloadCount | Long | 下载次数 |
| createdAt | DateTime | 创建时间 |
| updatedAt | DateTime | 更新时间 |

### 4.2 Skill 文件

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 自增主键 |
| skillId | String | 关联 Skill ID |
| path | String(512) | 文件相对路径（如 `scripts/setup.sh`） |
| encoding | String(16) | `text` 或 `base64` |
| content | LargeText | 文件内容（文本原文或 base64 编码） |
| size | Int | 文件大小（字节） |
| 唯一约束 | — | `(skillId, path)` |

## 五、返回数据结构参考

### 5.1 Skill 详情

```json
{
  "skillId": "skill-001",
  "name": "my-skill",
  "description": "A useful coding skill",
  "status": "PUBLISHED",
  "document": "---\nname: my-skill\ndescription: A useful coding skill\n---\n# My Skill\n...",
  "icon": { "type": "emoji", "value": "🔧" },
  "skillTags": ["coding", "python"],
  "downloadCount": 42,
  "categories": [...],
  "createdAt": "2025-01-01T00:00:00",
  "updatedAt": "2025-06-01T00:00:00"
}
```

### 5.2 精简列表项（CLI 接口）

```json
{
  "skillId": "skill-001",
  "name": "my-skill",
  "description": "A useful coding skill",
  "skillTags": ["coding", "python"]
}
```

### 5.3 文件树节点

```json
{
  "name": "scripts",
  "path": "scripts",
  "type": "directory",
  "children": [
    {
      "name": "setup.sh",
      "path": "scripts/setup.sh",
      "type": "file",
      "encoding": "text",
      "size": 1024
    }
  ]
}
```

### 5.4 文件内容

```json
{
  "path": "SKILL.md",
  "content": "---\nname: my-skill\n---\n...",
  "encoding": "text",
  "size": 512
}
```

### 5.5 上传结果

```json
{
  "fileCount": 12
}
```

## 六、SKILL.md 规范

SKILL.md 是每个 Skill 包的核心描述文件，格式为 YAML frontmatter + Markdown body：

```markdown
---
name: my-skill
description: A useful coding skill
---

# My Skill

这里是 Skill 的详细说明...
```

frontmatter 必填字段：
- `name` — Skill 名称
- `description` — Skill 描述

## 七、业务规则与约束

| 规则 | 说明 |
|------|------|
| 包格式 | 支持 ZIP 和 TAR.GZ |
| SKILL.md 必须存在 | 压缩包根目录或一级子目录下必须有 SKILL.md |
| YAML frontmatter 校验 | 必须包含 `name` 和 `description` 字段 |
| 单文件大小限制 | 最大 5MB |
| 文件数量限制 | 最多 500 个文件 |
| 编码自动检测 | 已知文本后缀 → text；已知二进制后缀 → base64；未知后缀 → 尝试 UTF-8 解码，失败则 base64 |
| 隐藏文件过滤 | 文件名以 `.` 开头的自动跳过 |
| 路径穿越防护 | 路径中包含 `..` 的条目自动跳过 |
| 顶层目录剥离 | 如果所有文件都在同一顶层目录下，自动去除该目录前缀 |
| 下载计数 | 每次下载 SKILL.md 自动递增计数 |
| 状态流转 | 创建即就绪（无需额外绑定），发布后对外可见 |

## 八、Nacos CLI 工具

除了服务端 API，还需要提供一个 `nacos-cli` 命令行工具，用于在沙箱环境或开发机器上直接操作 Skill。

### 8.1 核心命令

| 命令 | 说明 |
|------|------|
| `nacos-cli skill list` | 列出所有已发布的 Skill（名称、描述、标签） |
| `nacos-cli skill search <keyword>` | 按关键词搜索 Skill |
| `nacos-cli skill install <skillId> [--dir <path>]` | 下载 Skill 包并解压到指定目录（默认当前目录） |
| `nacos-cli skill info <skillId>` | 查看 Skill 详情（描述、标签、文件列表、下载次数等） |

### 8.2 使用场景

主要用于沙箱环境中，AI Agent 或开发者可以通过 CLI 直接将 Skill 下载到工作目录：

```bash
# 列出可用 Skill
nacos-cli skill list

# 安装 Skill 到指定目录
nacos-cli skill install my-skill --dir .claude/skills/my-skill

# 查看 Skill 详情
nacos-cli skill info my-skill
```

### 8.3 要求

- 单二进制分发，无额外运行时依赖（方便预装到沙箱镜像）
- 支持通过环境变量或参数指定 Nacos 服务端地址
- `install` 命令下载完整 Skill 包（ZIP）并自动解压到目标目录
- 输出格式支持 JSON（`--output json`），方便程序化调用

## 九、能力总结

Nacos 作为 Skill 管理组件，需要覆盖以下 10 个能力域：

| 序号 | 能力域 | 具体要求 |
|------|--------|----------|
| 1 | 元数据 CRUD | Skill 的名称、描述、图标、标签、状态的增删改查 |
| 2 | 包存储与分发 | 上传压缩包 → 解析 → 存储多文件 → 按需下载（ZIP 流式输出） |
| 3 | SKILL.md 读写 | 作为 Skill 的核心描述文件，支持独立读取和更新 |
| 4 | 文件浏览 | 文件树查询 + 单文件内容获取 + 全量文件获取 |
| 5 | 发布/下线 | 生命周期管理，支持发布、下线、查询发布历史 |
| 6 | 分类体系 | 支持多分类绑定和按分类筛选 |
| 7 | 统计计数 | 下载次数统计（每次下载自动递增） |
| 8 | 列表查询 | 分页 + 多条件筛选（状态、名称、分类） |
| 9 | CLI 友好接口 | 精简列表接口，返回 skillId、name、description、skillTags |
| 10 | 安全约束 | 文件大小/数量限制、路径穿越防护、隐藏文件过滤、编码自动检测 |
| 11 | CLI 工具 | 提供 nacos-cli，支持在沙箱/开发环境中列出、搜索、安装 Skill 到指定目录 |





\1. /api/v1/nacos/{nacosId}/skills/... 这个路径不对，不应该包含 nacos，skill 是 himarket 的一等公民

\2. ConfigFileBuilder 为什么下边是 QoderCliConfigGenerator，是因为 QoderCli 有什么特殊逻辑吗？qwen code/claude code/open code 应该是相同的逻辑啊

\3. 阅读下 nacos 的源码，现在上传 zip 包的这个逻辑应该不在 
