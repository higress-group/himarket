---
name: create-issue-himarket
description: "通过自然语言在 HiMarket 社区创建 Issue。支持 Feature Request（功能请求）和 Bug Report（问题报告）两种类型。当用户想要向 HiMarket 提交功能建议或报告问题时使用此 skill。"
---

# 创建 HiMarket Issue

## 概述

帮助用户通过自然语言描述，在 [higress-group/himarket](https://github.com/higress-group/himarket) 仓库创建规范的 Issue。

## Issue 类型

HiMarket 支持两种 Issue 类型：

### 1. Feature Request（功能请求）

用于提交新功能建议或改进想法。

**必填信息：**
- **Why do you need it?** - 为什么需要这个功能？描述你遇到的问题或痛点
- **How could it be?** - 期望的功能是什么样的？描述输入和输出
- **Other related information** - 其他相关信息、截图或上下文（可选）

### 2. Bug Report（问题报告）

用于报告 Bug 或异常行为。

**必填信息：**
- **Issue Description** - 问题简述
- **What happened** - 发生了什么？包括异常堆栈信息
- **Expected behavior** - 期望的行为是什么
- **Reproduction steps** - 最小化的复现步骤
- **Environment** - 环境信息（可选）

**可选信息：**
- **Anything else** - 其他补充信息
- **Root Cause** - 根因分析（如已定位）
- **Proposed Solution** - 建议的解决方案

## 工作流程

### 步骤 1：确定 Issue 类型

首先询问用户要创建的 Issue 类型：
- 功能请求（Feature Request）
- 问题报告（Bug Report）

### 步骤 2：收集必要信息

根据 Issue 类型，通过对话逐步收集必要信息：

**对于功能请求：**
1. 询问为什么需要这个功能（遇到了什么问题）
2. 询问期望的功能实现方式
3. 询问是否有其他补充信息

**对于问题报告：**
1. 询问问题的简要描述
2. 询问具体发生了什么（包括错误信息）
3. 询问期望的正确行为
4. 询问复现步骤
5. 询问是否已定位根因或有解决方案建议

### 步骤 3：生成 Issue 内容

根据收集的信息，按模板格式生成 Issue 内容，展示给用户确认。

### 步骤 4：创建 Issue

确认后，使用 GitHub CLI 创建 Issue：

```bash
# Feature Request
gh issue create --repo higress-group/himarket \
  --title "[Feature] <标题>" \
  --body "$(cat <<'EOF'
## Why do you need it?
<用户描述的问题/痛点>

## How could it be?
<期望的功能实现>

## Other related information
<其他补充信息>

---
**Type**: Feature Request
EOF
)"

# Bug Report
gh issue create --repo higress-group/himarket \
  --title "[Bug] <标题>" \
  --body "$(cat <<'EOF'
## Issue Description
<问题简述>

## What happened
<发生了什么>

```
<异常堆栈或错误信息>
```

## Expected behavior
<期望行为>

## Reproduction steps
1. <步骤1>
2. <步骤2>
3. <步骤3>

## Root Cause (if known)
<根因分析>

## Proposed Solution (if any)
<建议的解决方案>

## Files Changed (if applicable)
- `path/to/file1`
- `path/to/file2`

---
**Type**: Bug Report  
**Component**: <Frontend/Backend/Infra>
EOF
)"
```

## 关键原则

- **一次一个问题** - 逐步收集信息，不要一次性询问太多
- **提供示例** - 在询问时给出示例帮助用户理解
- **确认重复** - 提醒用户先搜索是否有类似 Issue
- **语言选择** - 支持用户用中文或英文描述
- **预览确认** - 创建前展示完整内容让用户确认
- **返回链接** - 创建成功后返回 Issue URL
- **标题前缀** - Bug 使用 `[Bug]`，功能请求使用 `[Feature]`，也可按组件添加如 `[Frontend]`

## 示例对话

**用户：** 我想给 HiMarket 提一个功能建议

**助手：** 好的，我来帮你创建一个 Feature Request。请问你为什么需要这个功能？遇到了什么问题或痛点？

**用户：** 聊天页面停止按钮点击没反应

**助手：** 明白了，这是一个 Bug Report。请问具体是什么情况？在什么操作下发现的？

**用户：** 模型推理过程中点击停止按钮无法取消

**助手：** 好的，我整理了以下 Issue 内容，请确认：

> **Title:** [Frontend] HiChat 停止按钮无法取消模型推理
>
> ## Issue Description
> HiChat 聊天页面在模型推理过程中，停止按钮点击无响应。
>
> ## Expected behavior
> 点击停止按钮后应中断推理过程。

确认无误后我将创建这个 Issue。
