# 需求文档：Nacos 默认实例 + Agent Skill 自动绑定

## 简介

在完成 Nacos Skill 集成重构后，Agent Skill 的管理已完全依赖 Nacos 实例。但当前 SkillController 直接暴露 nacosId/namespace 参数，前端需要手动指定 Nacos 坐标，用户体验不佳。本次改造引入"默认 Nacos 实例"和"默认命名空间"概念，实现 Agent Skill 创建时自动绑定，同时保留手动切换能力。

## 需求

### 需求 1：Nacos 实例默认标记

**用户故事：** 作为管理员，我希望系统有一个"默认 Nacos 实例"的概念，Agent Skill 创建时自动绑定该实例，无需每次手动选择。

#### 验收标准

1. `nacos_instance` 表新增 `is_default` 布尔字段（默认 false）
2. 导入第一个 Nacos 实例时，自动标记为默认实例
3. 存量数据：Flyway 迁移时将最早创建的 Nacos 实例标记为默认
4. 同一时刻只能有一个默认实例，设置新默认时自动取消旧默认
5. NacosResult DTO 返回 `isDefault` 字段
6. 新增 API：`PUT /nacos/{nacosId}/default` 设置默认实例
7. 新增 API：`GET /nacos/default` 获取默认实例（无默认时返回 null）

### 需求 2：Nacos 实例默认命名空间

**用户故事：** 作为管理员，我希望每个 Nacos 实例有一个默认命名空间设置，Agent Skill 创建时自动使用该命名空间。

#### 验收标准

1. `nacos_instance` 表新增 `default_namespace` 字段（默认 "public"）
2. 存量数据：Flyway 迁移时设置为 "public"
3. NacosResult DTO 返回 `defaultNamespace` 字段
4. admin 后台 Nacos 编辑页面新增"默认命名空间"下拉选择（从 Nacos 实例动态获取命名空间列表）
5. 更新 Nacos 实例时可修改默认命名空间

### 需求 3：Admin 后台默认实例切换

**用户故事：** 作为管理员，我希望在 Nacos 实例管理页面能看到哪个是默认实例，并能切换默认。

#### 验收标准

1. Nacos 实例列表中显示"默认"标签（Tag）
2. 操作列新增"设为默认"按钮（非默认实例显示）
3. 点击"设为默认"后弹出确认提示，文案说明：「设为默认后，新建的 Agent Skill 将自动绑定该 Nacos 实例」
4. 默认实例不允许删除（需先切换默认到其他实例）

### 需求 4：Agent Skill 创建时自动绑定默认 Nacos

**用户故事：** 作为管理员，我希望新建 Agent Skill 后自动绑定默认 Nacos 实例和默认命名空间，无需手动操作。

#### 验收标准

1. 创建 AGENT_SKILL 类型 Product 时，后端自动查找默认 Nacos 实例
2. 将默认 Nacos 的 nacosId 和 defaultNamespace 写入 product.feature.skillConfig
3. 如果没有默认 Nacos 实例，创建仍然成功，但 skillConfig 中 nacosId 为空（后续可手动关联）

### 需求 5：无 Nacos 实例时的上传限制

**用户故事：** 作为管理员，当系统没有导入任何 Nacos 实例时，我应该看到明确的提示引导我去导入 Nacos。

#### 验收标准

1. Skill Package 页面检测当前 product 的 skillConfig.nacosId 是否有值
2. 如果 nacosId 为空，显示提示卡片：「请先导入 Nacos 实例并关联到该 Skill」，附带跳转到 Nacos 管理页面的链接
3. nacosId 为空时，上传区域禁用

### 需求 6：Agent Skill 关联 Nacos 页面

**用户故事：** 作为管理员，我希望能在 Agent Skill 详情页手动切换关联的 Nacos 实例和命名空间。

#### 验收标准

1. AGENT_SKILL 类型的 Product 详情页新增 "Link Nacos" tab（替代原来隐藏的 "Link API"）
2. 页面展示当前关联的 Nacos 实例名称和命名空间
3. 提供"切换关联"按钮，弹窗中：
   - 下拉选择 Nacos 实例（从 `/nacos` 列表获取）
   - 下拉选择命名空间（从 `/nacos/{nacosId}/namespaces` 动态获取）
4. 切换关联后更新 product.feature.skillConfig 中的 nacosId 和 namespace
5. 新增后端 API：`PUT /products/{productId}/skill-nacos` 更新 Skill 的 Nacos 关联

### 需求 7：SkillController 路径改造

**用户故事：** 作为前端开发者，我希望 Skill 接口路径不暴露 nacos 细节，通过 productId 自动解析 Nacos 坐标。

#### 验收标准

1. SkillController 路径改为 `/skills`，不包含 `/nacos`
2. 上传、文件查询等接口通过 `productId` 路径参数定位 Skill
3. 后端内部通过 productId → Product → skillConfig → nacosId/namespace 解析 Nacos 坐标
4. 前端 skillApi 的现有调用路径（`/skills/${productId}/files` 等）无需修改
