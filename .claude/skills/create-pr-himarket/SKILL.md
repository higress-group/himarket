---
name: create-pr-himarket
description: "为 HiMarket 项目创建符合规范的 Pull Request。当用户需要提交代码、推送分支或创建 PR 时使用此 skill，确保 PR 标题和内容符合项目 CI 检查要求。"
---

# 创建 HiMarket Pull Request

## 概述

帮助用户在 [higress-group/himarket](https://github.com/higress-group/himarket) 仓库创建符合规范的 Pull Request，确保通过 PR Title Check、PR Content Check 和 PR Size Check。

## PR 标题格式

### 必需格式

```
type: 简短描述
```

或带范围：

```
type(scope): 简短描述
```

### 允许的 Type

| Type | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat: add user authentication` |
| `fix` | Bug 修复 | `fix: resolve memory leak` |
| `docs` | 文档更新 | `docs: update API documentation` |
| `style` | 代码格式 | `style: format with prettier` |
| `refactor` | 重构 | `refactor: simplify service logic` |
| `perf` | 性能优化 | `perf: optimize queries` |
| `test` | 测试 | `test: add unit tests` |
| `build` | 构建系统 | `build: update dependencies` |
| `ci` | CI/CD | `ci: add workflow` |
| `chore` | 其他变更 | `chore: update gitignore` |
| `revert` | 回滚 | `revert: revert commit abc123` |

### 标题规则

1. 必须包含 type 前缀
2. type 后需要冒号和空格：`feat: ` 而不是 `feat:`
3. 描述必须以**小写字母**开头
4. 保持简短清晰（建议 < 50 字符）

## PR 内容格式（必填）

### 必填部分

#### 1. Description（必填）

必须包含 `## 📝 Description` 部分，且内容至少 10 个字符。

```markdown
## 📝 Description

- 变更点 1
- 变更点 2
- 变更点 3
```

#### 2. Type of Change（必填）

必须至少勾选一项变更类型。

```markdown
## ✅ Type of Change

- [x] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)
- [ ] Breaking change
- [ ] Documentation update
- [ ] Code refactoring
- [ ] Performance improvement
```

### 可选部分

#### 3. Related Issues（推荐）

```markdown
## 🔗 Related Issues

Fix #123
Close #456
```

#### 4. Testing（推荐）

```markdown
## 🧪 Testing

- [x] Unit tests added/updated
- [x] Manual testing completed
- 测试场景描述
```

#### 5. Checklist（推荐）

```markdown
## 📋 Checklist

- [x] Code has been formatted (`mvn spotless:apply` for backend, `npm run lint:fix` for frontend)
- [x] Code is self-reviewed
- [x] No breaking changes
- [x] All CI checks pass
```

## 工作流程

### 步骤 1：检查当前状态

```bash
# 并行执行以下命令
git status
git diff --stat
git log --oneline -5
git branch -a
git remote -v
```

### 步骤 2：确认分支和远程

- 确认当前分支名称
- 确认 origin 指向用户的 fork（如 `lexburner/himarket`）
- 确认 upstream 指向主仓库（`higress-group/himarket`）

### 步骤 3：推送分支

```bash
git push -u origin <branch-name>
```

### 步骤 4：创建 PR

使用 HEREDOC 格式确保内容正确：

```bash
gh pr create --repo higress-group/himarket \
  --base main \
  --head <username>:<branch-name> \
  --title "feat: 简短描述" \
  --body "$(cat <<'EOF'
## 📝 Description

- 变更点 1
- 变更点 2

## 🔗 Related Issues

Fix #123

## ✅ Type of Change

- [ ] Bug fix (non-breaking change)
- [x] New feature (non-breaking change)
- [ ] Breaking change
- [ ] Documentation update
- [ ] Code refactoring
- [ ] Performance improvement

## 🧪 Testing

- [x] Unit tests pass locally
- [x] Manual testing completed

## 📋 Checklist

- [x] Code has been formatted (`mvn spotless:apply` for backend, `npm run lint:fix` for frontend)
- [x] Code is self-reviewed
- [x] No breaking changes
- [x] All CI checks pass
EOF
)"
```

### 步骤 5：验证检查状态

```bash
gh pr checks <pr-number> --repo higress-group/himarket
```

确保以下检查通过：
- PR Title Check
- PR Content Check
- PR Size Check
- PR Validation Summary

## 关键原则

- **标题小写** - 描述部分必须以小写字母开头
- **内容完整** - 必须包含 `## 📝 Description` 和 `## ✅ Type of Change`
- **勾选类型** - Type of Change 必须至少勾选一项 `[x]`
- **关联 Issue** - 推荐使用 `Fix #123` 格式关联 Issue
- **格式化代码** - 提交前运行 `mvn spotless:apply` 或 `npm run lint:fix`
- **不提交图片** - 避免将截图等二进制文件提交到仓库

## 常见错误

### 错误 1：标题首字母大写

```
❌ feat: Add new feature
✅ feat: add new feature
```

### 错误 2：缺少 Description 标题

```markdown
❌ 直接写内容
✅ ## 📝 Description
   内容
```

### 错误 3：未勾选 Type of Change

```markdown
❌ - [ ] New feature
✅ - [x] New feature
```

### 错误 4：Description 内容太短

```markdown
❌ ## 📝 Description
   Fix bug

✅ ## 📝 Description
   Fix pagination bug in product list
```

## 完整示例

**标题：**
```
feat(chat): add tool call support and stop generation feature
```

**内容：**
```markdown
## 📝 Description

- 添加聊天工具调用（Tool Call）支持，工具执行状态按消息顺序内联展示
- 添加停止生成过程功能，支持中断正在进行的 AI 回复
- 优化模型推理时滚动条自由滑动体验

## 🔗 Related Issues

Fix #163
Fix #164
Fix #165

## ✅ Type of Change

- [x] Bug fix (non-breaking change)
- [x] New feature (non-breaking change)
- [ ] Breaking change
- [ ] Documentation update
- [ ] Code refactoring
- [ ] Performance improvement

## 🧪 Testing

- [x] Unit tests pass locally
- [x] Manual testing completed
- 测试停止按钮能否正常中断 SSE 流式请求
- 测试模型推理时滚动条是否可自由滑动

## 📋 Checklist

- [x] Code has been formatted (`mvn spotless:apply` for backend, `npm run lint:fix` for frontend)
- [x] Code is self-reviewed
- [x] No breaking changes
- [x] All CI checks pass
```
