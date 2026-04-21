# Higress 文档站源码参考索引

> 本地路径: `reference-projects/higress-group.github.io/`
> 技术栈: Astro + Starlight | 分支: `ai`
> 用途: HiMarket 相关文档的编辑与维护，不提交到 HiMarket 仓库的 git

Higress 文档站（higress-group.github.io）是 Higress 官网与文档的统一站点，基于 Astro + Starlight 构建，支持中英文双语。HiMarket 的产品文档、部署指南、使用教程等均托管在此站点中。

## 参考价值

以下场景建议打开本索引查阅文档站源码：

1. **编辑 HiMarket 文档**：新增或修改 HiMarket 相关的产品文档、部署指南、使用教程
2. **新增文档页面**：需要了解文档的目录结构、frontmatter 格式、sidebar 配置方式
3. **中英文文档同步**：中文文档修改后需要同步更新英文版
4. **可观测文档维护**：更新 SLS 或数据库方案的可观测配置指南

## 项目结构

```
reference-projects/higress-group.github.io/
├── astro.config.mjs              # Astro 配置（路由、sidebar、国际化）
├── src/
│   ├── content/
│   │   ├── docs/                 # 文档内容（Starlight 管理）
│   │   │   ├── docs/             # 中文文档（默认语言，路径前缀 /docs/）
│   │   │   │   ├── himarket/     # ★ HiMarket 中文文档
│   │   │   │   ├── ai/           # AI 相关文档（含 himarket 子目录）
│   │   │   │   ├── latest/       # Higress 核心文档
│   │   │   │   ├── hiclaw/       # HiClaw 文档
│   │   │   │   └── developers/   # 开发者指南
│   │   │   └── en/               # 英文文档
│   │   │       └── docs/
│   │   │           ├── ai/
│   │   │           │   └── himarket/  # ★ HiMarket 英文文档
│   │   │           ├── latest/
│   │   │           └── developers/
│   │   ├── blog/                 # 博客
│   │   └── faq/                  # FAQ
│   ├── components/               # Astro 组件
│   ├── i18n/                     # 国际化配置
│   ├── styles/                   # 样式
│   └── utils/                    # 工具函数（sidebar 加载、frontmatter 处理等）
├── public/                       # 静态资源
└── package.json
```

## HiMarket 文档索引

### 中文文档（主要编辑目标）

路径前缀: `src/content/docs/docs/himarket/`

| 文件 | 标题 | 内容概要 |
|------|------|---------|
| `_sidebar.json` | — | Sidebar 导航配置，定义文档的目录结构和顺序 |
| `himarket-introduction.md` | HiMarket 介绍 | 产品定位、使用场景（AI 场景/AI 市场/AI 治理）、产品优势、快速体验、Roadmap |
| `himarket-deployment.md` | HiMarket 部署指南 | 本地搭建、Docker Compose 部署、Helm 云原生部署、阿里云计算巢一键部署 |
| `himarket-usage.md` | HiMarket 使用指南 | 管理后台全流程（注册管理员→导入 Higress→创建 Portal→创建/关联/发布 API Product）、开发者门户全流程（注册→订阅→创建 Consumer→HiChat） |
| `himarket-observability-overview.md` | 可观测能力介绍 | 模型监控大盘、MCP 监控大盘、SLS 方案 vs 数据库方案对比、架构设计 |
| `himarket-sls-observability.md` | 使用阿里云 SLS 实现可观测 | SLS 资源准备、日志采集配置、HiMarket 环境变量配置、Higress 插件配置（ai-statistics/pre-request/pre-response）、预设场景说明 |
| `himarket-db-observability.md` | 使用数据库实现可观测 | db-log-pusher + db-log-collector + MySQL 方案、数据库建表、collector 部署（K8s/Docker）、插件配置、HiMarket 数据源切换 |
| `himarket-agentscope-agent.md` | 上架 AgentScope Agent | AgentScope Agent 开发→注册到 Nacos→HiMarket 导入 Nacos 实例→创建并关联 Agent 产品→发布到门户 |

### 英文文档

路径前缀: `src/content/docs/en/docs/ai/himarket/`

| 文件 | 对应中文文档 | 备注 |
|------|------------|------|
| `himarket-introduction.md` | himarket-introduction.md | 英文版产品介绍 |
| `himarket-deployment.md` | himarket-deployment.md | 英文版部署指南 |
| `himarket-usage.md` | himarket-usage.md | 英文版使用指南 |
| `himarket-statistics.md` | himarket-sls-observability.md | 英文版 SLS 可观测（早期版本，仅 SLS 方案） |
| `himarket-db-log-pusher.md` | himarket-db-observability.md | 英文版数据库可观测（侧重 db-log-pusher 插件） |
| `himarket-agentscope-agent.md` | himarket-agentscope-agent.md | 英文版 AgentScope Agent 上架指南 |

> 注意：英文文档位于 `en/docs/ai/himarket/` 路径下，而中文文档位于 `docs/himarket/`。中文文档同时在 `docs/ai/himarket/` 下有一份副本（内容相同），这是因为文档站有两个 sidebar 分类（`ai` 和 `himarket`）都引用了这些文档。

### AI 分类下的 HiMarket 文档（中文副本）

路径前缀: `src/content/docs/docs/ai/himarket/`

此目录下的文档与 `docs/himarket/` 下的内容相同，是为了在 AI 分类的 sidebar 中也能展示 HiMarket 文档。编辑时以 `docs/himarket/` 为准。

## 文档编写规范

### Frontmatter 格式

每个 Markdown 文件必须包含 YAML frontmatter：

```yaml
---
title: "文档标题"
description: "文档描述"
date: "YYYY-MM-DD"
category: "article"
keywords: ["HiMarket", "关键词1", "关键词2"]
authors: "Higress Team"
---
```

### Sidebar 配置

`_sidebar.json` 定义文档的导航结构，格式示例：

```json
[
  {
    "label": "HiMarket",
    "translations": { "en": "HiMarket" },
    "collapsed": false,
    "items": [
      {
        "label": "HiMarket 介绍",
        "translations": { "en": "HiMarket Introduction" },
        "link": "docs/himarket-introduction"
      },
      {
        "label": "HiMarket 统计",
        "translations": { "en": "HiMarket Statistics" },
        "collapsed": false,
        "items": [
          {
            "label": "子文档标题",
            "translations": { "en": "Sub Doc Title" },
            "link": "docs/sub-doc-slug"
          }
        ]
      }
    ]
  }
]
```

关键规则：
- `link` 值为 `docs/<文件名不含.md>`，不需要完整路径
- 支持嵌套 `items` 实现多级目录
- `translations` 提供英文翻译
- `collapsed` 控制是否默认折叠

### 图片引用

文档中的图片使用 GitHub 图床或外部 URL：

```markdown
![描述](https://github.com/user-attachments/assets/xxx)
```

居中显示：

```markdown
<div align="center">

![描述](https://github.com/user-attachments/assets/xxx)

</div>
```

### 新增文档步骤

1. 在 `src/content/docs/docs/himarket/` 下创建 `.md` 文件
2. 添加正确的 frontmatter
3. 编辑 `_sidebar.json` 添加导航项
4. （可选）在 `src/content/docs/en/docs/ai/himarket/` 下创建英文版
5. 如需在 AI 分类中也展示，在 `src/content/docs/docs/ai/himarket/` 下放置副本

## 快速查找

| 我要... | 去哪里 |
|---------|--------|
| 编辑 HiMarket 产品介绍 | `src/content/docs/docs/himarket/himarket-introduction.md` |
| 编辑部署文档 | `src/content/docs/docs/himarket/himarket-deployment.md` |
| 编辑使用指南 | `src/content/docs/docs/himarket/himarket-usage.md` |
| 编辑可观测文档 | `src/content/docs/docs/himarket/himarket-*-observability*.md` |
| 编辑 AgentScope 文档 | `src/content/docs/docs/himarket/himarket-agentscope-agent.md` |
| 修改文档导航顺序 | `src/content/docs/docs/himarket/_sidebar.json` |
| 编辑英文版文档 | `src/content/docs/en/docs/ai/himarket/` |
| 查看 Astro 站点配置 | `astro.config.mjs` |
| 查看 sidebar 加载逻辑 | `src/utils/sidebarLoader` |

## 本地预览

```bash
cd reference-projects/higress-group.github.io
npm install
npm run dev
# 访问 http://localhost:4321
```
