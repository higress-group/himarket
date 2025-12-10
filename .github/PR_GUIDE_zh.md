# Pull Request 提交指南

本文档说明 Himarket 项目的 PR 提交规范。

## 🎯 PR 标题格式

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

1. ✅ 必须包含 type 前缀
2. ✅ type 后需要冒号和空格：`feat: ` 而不是 `feat:`
3. ✅ 描述必须以小写字母开头
4. ✅ 保持简短清晰（建议 < 50 字符）

### ✅ 正确示例

```
✅ feat: add product feature configuration
✅ fix: resolve pagination issue in product list
✅ docs: update deployment guide
✅ feat(product): add feature configuration support
✅ refactor(api): simplify product service
✅ perf: optimize database query performance
```

### ❌ 错误示例

```
❌ Add product feature                  (缺少 type)
❌ feat: Add Feature                    (首字母大写)
❌ featadd feature                      (缺少冒号和空格)
❌ feature: add feature                 (type 错误，应该是 feat)
❌ feat:add feature                     (冒号后缺少空格)
```

---

## 📝 PR 内容格式

### 必填部分

#### 1. Description（必填）

必须包含 `## Description` 部分，且内容至少 10 个字符。

**格式：**
```markdown
## Description

[你的变更内容 - 至少 10 个字符]
```

**可以使用：**
- 列表形式（推荐）
- 段落形式
- 混合形式

**示例：**

**样式 1：列表形式（推荐）**
```markdown
## Description

- 在产品 DTO 中添加 feature 字段
- 创建 ModelFeatureForm 组件
- 更新产品服务逻辑
- 添加数据库迁移脚本
```

**样式 2：段落形式**
```markdown
## Description

此 PR 为 MODEL_API 产品添加了特性配置功能。用户现在可以直接
从管理后台配置模型参数。
```

**样式 3：详细说明**
```markdown
## Description

### 主要变更
- 重构了 ClientFactory 类
- 添加了 ErrorHandler 工具类
- 更新了配置加载逻辑

### 改进效果
- 提高代码可读性
- 更好的错误提示
- 初始化速度提升 20%
```

#### 2. Related Issues（可选但推荐）

关联相关 Issue，帮助追踪解决了哪些问题。

**格式：**
```markdown
## Related Issues

Fix #123
Close #456
```

**支持的关键词：**
- `Fix #123` / `Fixes #123` / `Fixed #123`
- `Close #123` / `Closes #123` / `Closed #123`
- `Resolve #123` / `Resolves #123` / `Resolved #123`

当 PR 合并后，关联的 Issue 会自动关闭。

#### 3. Checklist（必填：代码格式化）

检查清单帮助确保代码质量和完整性。

**格式：**
```markdown
## Checklist

- [x] Code has been formatted with `mvn spotless:apply`
- [x] Code is self-reviewed
- [ ] Tests added/updated (if applicable)
- [ ] Documentation updated (if applicable)
- [ ] No breaking changes (or migration guide provided)
```

**必填项：**
- ✅ **Code has been formatted with `mvn spotless:apply`** - 此项必须勾选

**可选项：**
- Code is self-reviewed（代码已自我审查）
- Tests added/updated (if applicable)（已添加/更新测试，如适用）
- Documentation updated (if applicable)（已更新文档，如适用）
- No breaking changes (or migration guide provided)（无破坏性变更，或已提供迁移指南）

**重要提示：** 提交 PR 前，你必须：
1. 在项目根目录运行 `mvn spotless:apply`
2. 勾选 Checklist 中的"代码已格式化"选项
3. 提交任何格式化产生的变更

---

## 🔍 自动检查

每个 PR 会自动触发两项检查：

### 1. PR 标题检查

**验证内容：**
- ✅ type 前缀存在且有效
- ✅ 格式包含冒号和空格
- ✅ 描述以小写字母开头

**检查结果：**
- ✅ 通过：标题格式正确
- ❌ 失败：标题格式错误（附带详细说明）

### 2. PR 内容检查

**验证内容：**
- ✅ 存在 `## Description` 部分
- ✅ 描述内容至少 10 个字符
- ✅ 存在 `## Checklist` 部分
- ✅ "Code has been formatted with `mvn spotless:apply`" 已勾选

**可选检查（仅建议）：**
- 💡 如果没有关联 Issue，会建议添加
- 💡 如果描述很短（< 50 字符），会建议补充

**检查结果：**
- ✅ 通过：所有必填项完整且代码格式化已确认
- ❌ 失败：缺少描述、内容太短或未确认代码格式化
- 💡 建议：改进建议

---

## 📚 完整示例

### 示例 1：新功能 PR ✅

**标题：**
```
feat: add product feature configuration
```

**内容：**
```markdown
## Description

- 在产品 DTO 和数据库架构中添加 feature 字段
- 创建 ModelFeatureForm 组件提供配置界面
- 更新产品服务以持久化特性配置
- 添加新列的数据库迁移脚本

## Related Issues

Fix #123
Close #456

## Checklist

- [x] Code has been formatted with `mvn spotless:apply`
- [x] Code is self-reviewed
- [x] Tests added/updated (if applicable)
- [x] Documentation updated (if applicable)
```

**检查结果：**
```
✅ pr-title-check: 通过
✅ pr-content-check: 通过
```

---

### 示例 2：Bug 修复 PR ✅

**标题：**
```
fix: resolve pagination issue in product list
```

**内容：**
```markdown
## Description

修复了产品列表分页中的 SQL 注入漏洞，将字符串拼接改为
参数化查询。

测试：已用 10,000+ 条记录验证。

## Related Issues

Fix #789

## Checklist

- [x] Code has been formatted with `mvn spotless:apply`
- [x] Code is self-reviewed
- [x] Tests added/updated (if applicable)
```

**检查结果：**
```
✅ pr-title-check: 通过
✅ pr-content-check: 通过
```

---

### 示例 3：简单重构 ✅

**标题：**
```
refactor: simplify client initialization
```

**内容：**
```markdown
## Description

- 将初始化逻辑提取到独立方法
- 移除重复代码
- 添加行内文档

## Related Issues

None

## Checklist

- [x] Code has been formatted with `mvn spotless:apply`
- [x] Code is self-reviewed
```

**检查结果：**
```
✅ pr-title-check: 通过
✅ pr-content-check: 通过
💡 建议：考虑关联相关 Issue
```

---

## ❌ 常见错误

### 错误 1：标题格式错误

**错误写法：**
```
Add new feature
```

**正确写法：**
```
feat: add new feature
```

**错误提示：**
```
❌ PR 标题格式不正确！
缺少 type 前缀。期望格式：type: description
```

---

### 错误 2：描述首字母大写

**错误写法：**
```
feat: Add New Feature
```

**正确写法：**
```
feat: add new feature
```

**错误提示：**
```
❌ PR 标题格式不正确！
描述必须以小写字母开头
```

---

### 错误 3：缺少 Description 部分

**错误写法：**
```markdown
此 PR 添加了新功能。

## Related Issues
Fix #123
```

**正确写法：**
```markdown
## Description

此 PR 添加了新功能。

## Related Issues
Fix #123
```

**错误提示：**
```
❌ 缺少变更说明或内容过于简短（至少需要 10 个字符）
```

---

### 错误 4：描述内容太短

**错误写法：**
```markdown
## Description

Fix bug
```
（只有 7 个字符）

**正确写法：**
```markdown
## Description

Fix pagination bug in product list
```

**错误提示：**
```
❌ 缺少变更说明或内容过于简短（至少需要 10 个字符）
```

---

### 错误 5：未确认代码格式化

**错误写法：**
```markdown
## Description

添加新功能

## Checklist

- [ ] Code has been formatted with `mvn spotless:apply`  <!-- 未勾选 -->
- [x] Code is self-reviewed
```

**正确写法：**
```markdown
## Description

添加新功能

## Checklist

- [x] Code has been formatted with `mvn spotless:apply`  <!-- 必须勾选 -->
- [x] Code is self-reviewed
```

**错误提示：**
```
❌ Please confirm code has been formatted with `mvn spotless:apply`
```

**注意：** 你必须：
1. 在终端运行 `mvn spotless:apply`
2. 提交任何格式化产生的变更
3. 勾选 Checklist 中的选项

---

## ❓ 常见问题

### Q: 是否需要填写所有部分？

**A:** 只有 `## Description` 是必填的。`## Related Issues` 是可选的但建议填写。

---

### Q: 描述可以用中文吗？

**A:** 可以，但我们建议使用英文以便更好的协作。标题必须遵循英文格式。

---

### Q: 如果我的 PR 没有关联任何 Issue 怎么办？

**A:** 没关系！你可以在 Related Issues 部分写 "None" 或留空，不会导致检查失败。

---

### Q: 描述可以用段落形式吗？

**A:** 当然可以！任何格式都可以，只要清晰且至少 10 个字符。列表只是推荐格式。

---

### Q: 如果检查失败会怎样？

**A:** 
1. 你会在 PR 上看到 ❌ 标记
2. 机器人会评论具体的错误信息
3. 编辑 PR 标题或描述来修复问题
4. 检查会自动重新运行

---

### Q: 可以跳过检查吗？

**A:** 不可以，但如果有正当理由，项目维护者可以覆盖检查。通常遵循指南很快很简单。

---

### Q: 为什么标题必须以小写开头？

**A:** 这是广泛采用的约定（Conventional Commits）。它能保持提交历史的整洁和一致性。

---

### Q: 如果我做了多个不相关的变更怎么办？

**A:** 建议拆分成多个 PR。如果必须放在一起，请在 Description 中清楚地描述所有变更。

---

