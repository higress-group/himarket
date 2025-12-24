# Pull Request 提交指南

本文档说明 Himarket 项目的 PR 提交规范。

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

## PR 内容格式

### 必填部分

#### 1. Description（必填）📝

必须包含 `## 📝 Description` 部分，且内容至少 10 个字符。

**格式：**
```markdown
## 📝 Description

[你的变更内容 - 至少 10 个字符]
```

**可以使用：**
- 列表形式（推荐）
- 段落形式
- 混合形式

**示例：**

**样式 1：列表形式（推荐）**
```markdown
## 📝 Description

- 在产品 DTO 中添加 feature 字段
- 创建 ModelFeatureForm 组件
- 更新产品服务逻辑
- 添加数据库迁移脚本
```

**样式 2：段落形式**
```markdown
## 📝 Description

此 PR 为 MODEL_API 产品添加了特性配置功能。用户现在可以直接
从管理后台配置模型参数。
```

**样式 3：详细说明**
```markdown
## 📝 Description

### 主要变更
- 重构了 ClientFactory 类
- 添加了 ErrorHandler 工具类
- 更新了配置加载逻辑

### 改进效果
- 提高代码可读性
- 更好的错误提示
- 初始化速度提升 20%
```

#### 2. Type of Change（必填）✅

必须至少勾选一项变更类型。

**格式：**
```markdown
## ✅ Type of Change

- [x] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)
- [ ] Breaking change
- [ ] Documentation update
- [ ] Code refactoring
- [ ] Performance improvement
```

**可选项说明：**
- **Bug fix** - 修复问题，不破坏现有功能
- **New feature** - 添加新功能，无破坏性变更
- **Breaking change** - 会导致现有功能无法正常工作的变更
- **Documentation update** - 仅文档更新
- **Code refactoring** - 代码重构，无功能影响
- **Performance improvement** - 性能优化
- **Build/CI configuration change** - 构建或 CI/CD 配置变更
- **Other** - 其他类型（请描述）

**重要：** 必须至少勾选一项，帮助审查者快速了解变更性质。

#### 3. Related Issues（可选但推荐）🔗

关联相关 Issue，帮助追踪解决了哪些问题。

**格式：**
```markdown
## 🔗 Related Issues

Fix #123
Close #456
```

**支持的关键词：**
- `Fix #123` / `Fixes #123` / `Fixed #123`
- `Close #123` / `Closes #123` / `Closed #123`
- `Resolve #123` / `Resolves #123` / `Resolved #123`

当 PR 合并后，关联的 Issue 会自动关闭。

#### 4. Testing（可选但推荐）🧪

描述如何测试变更，确保质量和可靠性。

**格式：**
```markdown
## 🧪 Testing

- [x] Unit tests added/updated
- [x] Integration tests added/updated
- [x] Manual testing completed
- [x] All tests pass locally
```

**测试指南：**
- 描述执行的测试
- 包含测试结果或验证步骤
- 注明测试的边界情况
- 确认所有测试在本地通过

**示例：**
```markdown
## 🧪 Testing

- 为新的特性配置逻辑添加了单元测试
- 手动测试了各种产品类型
- 验证了与现有产品的向后兼容性
- 本地所有 127 个测试均通过
```

#### 5. Checklist（推荐项）📋

检查清单帮助确保代码质量和完整性。

**格式：**
```markdown
## 📋 Checklist

- [x] Code has been formatted (`mvn spotless:apply` for backend, `npm run lint:fix` for frontend)
- [x] Code is self-reviewed
- [x] Comments added for complex code
- [x] Documentation updated (if applicable)
- [x] No breaking changes (or migration guide provided)
- [x] All CI checks pass
```

**推荐项：**
- ✅ **代码已格式化** - 提交前运行格式化命令
- ✅ **代码已自我审查** - 先自己审查变更
- 为复杂代码添加注释
- 更新文档（如适用）
- 无破坏性变更（或已提供迁移指南）
- 所有 CI 检查通过

**重要提示：** 提交 PR 前：
1. **后端：** 在项目根目录运行 `mvn spotless:apply`
2. **前端：** 在前端目录运行 `npm run lint:fix`
3. 审查自己的代码变更
4. 提交任何格式化产生的变更

#### 6. Test Coverage（可选）📊

如果修改了代码，说明测试覆盖率是否保持或提升。

**格式：**
```markdown
## 📊 Test Coverage

- 添加了 15 个新的单元测试
- 整体覆盖率从 65% 提升到 68%
- 所有关键路径均已覆盖
```

#### 7. Additional Notes（可选）📚

审查者需要知道的任何额外上下文或信息。

**格式：**
```markdown
## 📚 Additional Notes

- 此变更需要数据库迁移
- 性能测试显示提升 20%
- 破坏性变更：API 端点路径已更改
```

---

## 自动检查

每个 PR 会自动触发三类检查：

### 1. PR 标题检查

**验证内容：**
- ✅ type 前缀存在且有效
- ✅ 格式包含冒号和空格
- ✅ 描述以小写字母开头

**检查结果：**
- ✅ 通过：标题格式正确
- ❌ 失败：标题格式错误（附带详细说明）

### 2. PR 内容检查

**必填项（必须通过）：**
- ✅ 存在 `## 📝 Description` 部分
- ✅ 描述内容至少 10 个字符
- ✅ 存在 `## ✅ Type of Change` 部分
- ✅ 至少勾选一项类型（如 `- [x] Bug fix`）

**可选检查（仅建议）：**
- 💡 如果没有关联 Issue，会建议添加（`Fix #123`）
- 💡 建议添加测试信息
- 💡 如果 PR 较大（> 500 或 > 1000 行），会发出警告

**检查结果：**
- ✅ 通过：所有必填项完整且内容有效
- ❌ 失败：缺少描述、内容太短或未选择类型
- 💡 建议：改进建议

### 3. PR 大小检查

**评估内容：**
- 📊 总变更行数（新增 + 删除）
- 📁 变更文件数量

**大小分类：**
- 🟢 **XS**（< 100 行）：非常好 - 易于审查
- 🟢 **S**（100-300 行）：良好 - 合理大小
- 🟡 **M**（300-600 行）：中等 - 确保范围聚焦
- 🟠 **L**（600-1000 行）：较大 - 建议拆分
- 🔴 **XL**（> 1000 行）：超大 - 强烈建议拆分

**检查结果：**
- 始终通过（仅供参考）
- 为大型 PR 提供建议
- 不会阻止 PR 提交

---

## 完整示例

### 示例 1：新功能 PR ✅

**标题：**
```
feat: add product feature configuration
```

**内容：**
```markdown
## 📝 Description

- 在产品 DTO 和数据库架构中添加 feature 字段
- 创建 ModelFeatureForm 组件提供配置界面
- 更新产品服务以持久化特性配置
- 添加新列的数据库迁移脚本

## 🔗 Related Issues

Fix #123
Close #456

## ✅ Type of Change

- [x] New feature (non-breaking change)
- [ ] Bug fix (non-breaking change)

## 🧪 Testing

- [x] Unit tests added/updated
- [x] Manual testing completed
- 已用 100+ 个产品测试，所有配置均正确保存

## 📋 Checklist

- [x] Code has been formatted (`mvn spotless:apply` for backend)
- [x] Code is self-reviewed
- [x] Comments added for complex code
- [x] Documentation updated (if applicable)
```

**检查结果：**
```
✅ pr-title-check: 通过
✅ pr-content-check: 通过
✅ pr-size-check: 通过（250 行 - 大小：S）
```

---

### 示例 2：Bug 修复 PR ✅

**标题：**
```
fix: resolve pagination issue in product list
```

**内容：**
```markdown
## 📝 Description

修复了产品列表分页中的 SQL 注入漏洞，将字符串拼接改为
参数化查询。

## 🔗 Related Issues

Fix #789

## ✅ Type of Change

- [x] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)

## 🧪 Testing

- [x] Unit tests added/updated
- [x] Manual testing completed
- 已用 10,000+ 条记录验证 - 无性能下降
- 安全扫描显示漏洞已解决

## 📋 Checklist

- [x] Code has been formatted (`mvn spotless:apply` for backend)
- [x] Code is self-reviewed
- [x] All CI checks pass
```

**检查结果：**
```
✅ pr-title-check: 通过
✅ pr-content-check: 通过
✅ pr-size-check: 通过（85 行 - 大小：XS）
```

---

### 示例 3：简单重构 ✅

**标题：**
```
refactor: simplify client initialization
```

**内容：**
```markdown
## 📝 Description

- 将初始化逻辑提取到独立方法
- 移除重复代码
- 添加行内文档

## 🔗 Related Issues

None

## ✅ Type of Change

- [ ] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)
- [x] Code refactoring (no functional changes)

## 🧪 Testing

- [x] All tests pass locally
- 无需新测试 - 仅重构
- 已验证现有功能未变

## 📋 Checklist

- [x] Code has been formatted (`mvn spotless:apply` for backend)
- [x] Code is self-reviewed
- [x] No breaking changes
```

**检查结果：**
```
✅ pr-title-check: 通过
✅ pr-content-check: 通过
✅ pr-size-check: 通过（120 行 - 大小：S）
💡 建议：考虑关联相关 Issue
```

---

## 常见错误

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
## 📝 Description

Fix bug
```
（只有 7 个字符）

**正确写法：**
```markdown
## 📝 Description

Fix pagination bug in product list
```

**错误提示：**
```
❌ 缺少变更说明或内容过于简短（至少需要 10 个字符）
```

---

### 错误 5：未选择变更类型

**错误写法：**
```markdown
## 📝 Description

添加新功能

## ✅ Type of Change

- [ ] Bug fix (non-breaking change)
- [ ] New feature (non-breaking change)
- [ ] Breaking change
```

**正确写法：**
```markdown
## 📝 Description

添加新功能

## ✅ Type of Change

- [ ] Bug fix (non-breaking change)
- [x] New feature (non-breaking change)
- [ ] Breaking change
```

**错误提示：**
```
❌ 未选择变更类型
请至少勾选一项 Type of Change 选项
```

**注意：** 必须至少勾选一项，以说明 PR 引入的变更类型。

---

### 错误 6：未确认代码格式化

**错误写法：**
```markdown
## 📝 Description

添加新功能

## 📋 Checklist

- [ ] Code has been formatted with `mvn spotless:apply`  <!-- 未勾选 -->
- [x] Code is self-reviewed
```

**正确写法：**
```markdown
## 📝 Description

添加新功能

## 📋 Checklist

- [x] Code has been formatted (`mvn spotless:apply` for backend)  <!-- 必须勾选 -->
- [x] Code is self-reviewed
```

**注意：** 提交前：
1. **后端：** 在终端运行 `mvn spotless:apply`
2. **前端：** 在前端目录运行 `npm run lint:fix`
3. 提交任何格式化产生的变更
4. 验证检查清单项适用于你的变更

---

## 常见问题

### Q: 是否需要填写所有部分？

**A:** 必填部分：
- ✅ `## 📝 Description`（至少 10 个字符）
- ✅ `## ✅ Type of Change`（至少勾选一项）

可选但推荐：
- 💡 `## 🔗 Related Issues`（关联相关 Issue）
- 💡 `## 🧪 Testing`（描述执行的测试）
- 💡 `## 📋 Checklist`（自我审查项目）
- 💡 `## 📊 Test Coverage`（覆盖率信息）
- 💡 `## 📚 Additional Notes`（额外说明）

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

