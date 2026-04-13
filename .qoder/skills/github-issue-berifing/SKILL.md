---
name: github-issue-briefing
description: "从 GitHub Issues 收集用户反馈并生成简报。抓取 higress-group/himarket 的 issues，按类型分类、去重（排除已处理的重复内容），生成包含趋势分析和优先级建议的简报。当用户想了解社区反馈、issue 概况、用户需求趋势时使用此 skill。"
---

# GitHub Issue 反馈简报

从 [higress-group/himarket](https://github.com/higress-group/himarket) 仓库收集 GitHub Issues，整理为结构化简报，并提供优先级建议。

## 前置条件

- `gh` CLI 已安装并完成认证（`gh auth status` 验证）
- 对 `higress-group/himarket` 仓库有读取权限

## 工作流程

### 步骤 1：确定参数

使用 AskUserQuestion 询问用户（如未通过参数指定）：

- **时间范围**：最近 7 天 / 30 天 / 90 天 / 自定义天数（默认 7 天）
- **是否包含 PR**：默认仅 Issue，不含 PR

将用户选择的天数记为 `$DAYS`。

### 步骤 2：抓取数据

依次执行以下命令，收集 open 和 recently closed 的 issues：

```bash
# 获取 open issues（最多 50 条）
gh issue list --repo higress-group/himarket \
  --state open --limit 50 \
  --json number,title,labels,createdAt,updatedAt,author,body,comments,url

# 获取最近关闭的 issues（用于去重比对）
gh issue list --repo higress-group/himarket \
  --state closed --limit 50 \
  --json number,title,labels,createdAt,closedAt,updatedAt,author,body,comments,url
```

### 步骤 3：过滤与分类

**时间过滤：** 只保留 `createdAt` 在最近 `$DAYS` 天内的 open issues 作为主要分析对象。

**按类型分类**（基于 labels 和内容关键词判断）：

| 类别 | 识别方式 |
|------|----------|
| Bug Report | label 包含 `bug`，或标题/内容含 `[Bug]`、`报错`、`异常`、`crash`、`error`、`fix` |
| Feature Request | label 包含 `enhancement`/`feature`，或标题/内容含 `[Feature]`、`建议`、`希望`、`支持` |
| Question | label 包含 `question`/`help`，或标题/内容含 `如何`、`怎么`、`how to`、`?` |
| Documentation | label 包含 `documentation`/`docs` |
| 其他 | 不符合以上分类的 |

### 步骤 4：去重处理

对 open issues 进行去重，标记以下情况：

1. **已有关闭的同类 issue**：在 closed issues 中搜索标题相似度高的（关键词重叠 ≥ 50%），标记为"历史已处理"
2. **open issues 之间重复**：多个 open issue 描述同一问题，合并为一条并注明重复 issue 编号
3. **判断依据**：标题关键词匹配 + 问题描述核心内容对比

去重结果中标注：
- `[新]` - 全新的反馈
- `[重复-已关闭 #N]` - 与已关闭的 #N 相似，可能需要确认是否复现
- `[重复-open #N]` - 与 open 的 #N 是同一问题

### 步骤 5：生成简报

按以下模板生成简报内容：

```markdown
# HiMarket 社区 Issue 简报

> 统计周期：YYYY-MM-DD ~ YYYY-MM-DD
> 数据来源：https://github.com/higress-group/himarket/issues
> 生成时间：YYYY-MM-DD HH:MM

## 概览

| 指标 | 数量 |
|------|------|
| 周期内新增 open issues | N |
| 其中去重后有效 issues | N |
| Bug Report | N |
| Feature Request | N |
| Question / 咨询 | N |
| 其他 | N |

## 🔴 Bug 报告

### 高优先级
> 影响核心功能、有多人反馈、或影响面广的 bug

| # | 标题 | 报告者 | 日期 | 状态 |
|---|------|--------|------|------|
| #N | 标题 | @user | YYYY-MM-DD | [新] |

### 一般 Bug

| # | 标题 | 报告者 | 日期 | 状态 |
|---|------|--------|------|------|
| #N | 标题 | @user | YYYY-MM-DD | [新] |

## 💡 功能请求

### 高票需求
> 多人请求或与项目方向一致的需求

| # | 标题 | 报告者 | 日期 | 👍 | 状态 |
|---|------|--------|------|----|------|
| #N | 标题 | @user | YYYY-MM-DD | N | [新] |

### 其他需求

| # | 标题 | 报告者 | 日期 | 状态 |
|---|------|--------|------|------|
| #N | 标题 | @user | YYYY-MM-DD | [新] |

## ❓ 问题与咨询

| # | 标题 | 报告者 | 日期 | 已回复 |
|---|------|--------|------|--------|
| #N | 标题 | @user | YYYY-MM-DD | ✅/❌ |

## 🔁 去重记录

以下 issue 与历史已关闭或其他 open issue 存在重复：

| 当前 Issue | 重复类型 | 关联 Issue | 说明 |
|------------|----------|------------|------|
| #N | 已关闭重复 | #M | 简述相似点 |
| #N | open 重复 | #M | 简述相似点 |

## 📊 趋势分析

- **最活跃领域**：哪个模块/功能收到最多反馈
- **反馈趋势**：与前一周期相比，issue 数量是上升还是下降（如有数据）
- **社区活跃度**：新贡献者数量、issue 回复率

## 🎯 建议与优先级

### 建议立即处理
1. **#N - 标题**：原因说明（如影响核心流程、多人反馈）

### 建议近期规划
1. **#N - 标题**：原因说明（如社区呼声高、实现成本低）

### 建议长期关注
1. **#N - 标题**：原因说明（如方向性需求、需要架构调整）

### 可关闭/归档
1. **#N - 标题**：原因说明（如与已关闭 issue 重复、已过时）
```

### 步骤 6：输出

1. **终端输出**：直接在对话中展示完整简报
2. **保存文件**：将简报保存到 `reports/issue-briefing-YYYY-MM-DD.md`（相对项目根目录）

```bash
mkdir -p reports
```

告知用户文件保存路径。

## 优先级判断依据

按以下维度综合评估 issue 优先级：

| 维度 | 高优 | 低优 |
|------|------|------|
| 影响面 | 核心功能 / 大量用户 | 边缘功能 / 个例 |
| 反馈量 | 多人反馈相同问题 | 单人反馈 |
| 严重度 | 崩溃/数据丢失/安全 | UI 瑕疵/体验优化 |
| 实现成本 | 已有方案 / 改动小 | 需大规模重构 |
| 👍 反应数 | reactions 数量多 | 无反应 |
| 与路线图一致性 | 符合项目方向 | 偏离主线 |

## 注意事项

- Issue body 可能很长，分析时关注标题、前 500 字和 labels
- `gh` 命令的 `--json` 输出可能很大，用 `jq` 提取必要字段
- 若 issue 数量超过 200，提醒用户缩小时间范围
- 简报中的 issue 链接使用完整 URL 格式便于点击
- 去重判断宜宽松，宁可标记再让用户确认，不要遗漏
- 中英文 issue 都需要处理，分类时兼顾两种语言的关键词
