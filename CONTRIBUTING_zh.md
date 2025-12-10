# Himarket 贡献指南

感谢你对 Himarket 项目的关注！🎉

我们欢迎各种形式的贡献：Bug 修复、新功能、文档改进等等。

## 🚀 快速开始

### Fork 并 Clone 仓库

1. **Fork** [Himarket 仓库](https://github.com/higress-group/himarket) 到你的 GitHub 账号
2. **Clone** 你的 Fork 到本地：

```bash
git clone https://github.com/YOUR_USERNAME/himarket.git
cd himarket
```

3. **添加上游仓库**以便保持同步：

```bash
git remote add upstream https://github.com/higress-group/himarket.git
```

4. **搭建开发环境**，请按照 [README.md](README.md) 中的说明操作

---

## 🔄 开发流程

### 1. 同步并创建分支

开始工作前，先同步你的 Fork 与上游仓库：

```bash
# 切换到主分支
git checkout main

# 拉取上游最新变更
git pull upstream main

# 推送更新到你的 Fork
git push origin main

# 创建新的功能分支
git checkout -b feat/your-feature-name
```

### 2. 代码修改

编写代码时请遵循我们的[代码规范](#-代码规范)。

### 3. 格式化代码

**提交前**，务必格式化代码以确保符合我们的风格规范：

```bash
# 格式化 Java 代码（必需）
mvn spotless:apply

# 如果修改了前端代码，也需要格式化
cd himarket-web/himarket-admin
npm run format
```

### 4. 提交修改

我们遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范。你的提交信息应该清晰且具有描述性：

```bash
git add .
git commit -m "feat: add user authentication feature"
```

**提交信息格式：**
```
type: 简短描述（50 字符以内）

[可选的详细说明]
```

**常用类型：**
- `feat` - 新功能
- `fix` - Bug 修复
- `docs` - 文档变更
- `style` - 代码格式调整（格式化、缺少分号等）
- `refactor` - 代码重构（不改变功能）
- `perf` - 性能优化
- `test` - 添加或更新测试
- `chore` - 维护任务、依赖更新等

### 5. 推送到你的 Fork

```bash
git push origin feat/your-feature-name
```

### 6. 创建 Pull Request

1. 访问你在 GitHub 上的 Fork
2. 点击 **"New Pull Request"** 按钮
3. 确保基础仓库是 `higress-group/himarket`，基础分支是 `main`
4. 选择你的功能分支作为对比分支
5. 填写 **PR 模板**（自动加载），详细说明你的修改
6. 点击 **"Create Pull Request"**

---

## 📝 Pull Request 规范

### PR 标题

你的 PR 标题必须遵循格式：`type: 简短描述`

**正确示例：**
```
✅ feat: add product feature configuration
✅ fix: resolve pagination issue in product list
✅ docs: update deployment guide in README
✅ refactor: simplify client initialization logic
```

**错误示例：**
```
❌ Add new feature（缺少类型）
❌ feat: Add Feature（描述应该小写）
❌ update code（描述不够清晰）
```

### PR 描述

你的 PR **必须包含**以下部分：

1. **Description**（必填）
   - 清楚说明你做了什么修改以及为什么
   - 使用列表形式以保持清晰
   - 至少 10 个字符

2. **Related Issues**（可选但推荐）
   - 使用关键词关联相关 Issue：`Fix #123`、`Close #456`、`Resolve #789`
   - 这有助于我们追踪哪些问题正在被解决

3. **Checklist**（必填）
   - 确认你已运行 `mvn spotless:apply` 格式化代码
   - 说明你是否添加了测试或更新了文档
   - 勾选适用的项目

**PR 描述示例：**
```markdown
## Description

- 在 Product 实体中添加 feature 配置字段
- 为管理后台创建 ModelFeatureForm 组件
- 实现后端服务以持久化 feature 设置
- 添加 Flyway 迁移脚本以更新数据库架构

## Related Issues

Fix #123
Close #456

## Checklist

- [x] Code has been formatted with `mvn spotless:apply`
- [x] Code is self-reviewed
- [x] Tests added/updated (if applicable)
- [x] Documentation updated (if applicable)
```

### 自动检查

每个 PR 都会自动触发以下检查：

1. **PR Check** - 验证 PR 标题和描述格式（必需 ✅）
2. **Code Format Check** - 运行 `mvn spotless:check` 验证代码格式（必需 ✅）
3. **Checkstyle Check** - 检查代码风格和最佳实践（警告 ⚠️）

**必需检查**必须通过才能合并 PR。**警告检查**不会阻止 PR 合并，但会提供改进建议。

如果检查失败，机器人会评论并说明如何修复。

**更详细的 PR 指南，请参考：**
- [PR_GUIDE.md](.github/PR_GUIDE.md) - English version
- [PR_GUIDE_CN.md](.github/PR_GUIDE_zh) - 中文版本

---

## 💻 代码规范

### Java 代码

**代码格式化（必需）：**
- 提交前运行 `mvn spotless:apply` 自动格式化代码
- 确保项目代码风格一致
- **CI 检查不通过**则无法合并

**代码风格（建议）：**
- 我们使用 Checkstyle 检查最佳实践和风格问题
- 本地运行 `mvn checkstyle:check` 查看建议
- **CI 会警告**但不会阻止合并

**最佳实践：**
- 每行最多 120 个字符
- 为变量、方法和类使用清晰、描述性的名称
- 为公共 API 添加 Javadoc 注释
- 避免魔法数字和空 catch 块
- 保持方法专注且长度合理

### TypeScript/React 代码

- **格式化**：使用 Prettier 格式化代码：`npm run format`
- **风格指南**：遵循 [Airbnb JavaScript Style Guide](https://github.com/airbnb/javascript)
- **类型安全**：始终使用 TypeScript 类型/接口；尽可能避免 `any`
- **组件**：优先使用带 React Hooks 的函数式组件
- **命名**：使用描述性名称；组件用 PascalCase，函数用 camelCase

### 数据库迁移

- **工具**：所有数据库架构变更使用 Flyway
- **位置**：迁移文件放在 `himarket-bootstrap/src/main/resources/db/migration/`
- **命名**：遵循格式 `V{版本}__{描述}.sql`
  - 示例：`V3__Add_product_feature.sql`
- **测试**：提交前务必在干净的数据库上测试你的迁移

