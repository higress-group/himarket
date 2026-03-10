# 技术设计文档：Nacos 默认实例 + Agent Skill 自动绑定

## 概述

本设计在已完成的 Nacos Skill 集成基础上，引入"默认 Nacos 实例"和"默认命名空间"概念，让 Agent Skill 创建时自动绑定，同时提供手动切换能力。核心改动：

1. **数据库**：nacos_instance 表新增 is_default 和 default_namespace 字段
2. **后端**：NacosService 新增默认实例管理 API，ProductService 创建 AGENT_SKILL 时自动绑定，SkillController 改为通过 productId 解析 Nacos 坐标
3. **前端**：Nacos 管理页面增加默认标记和切换，Agent Skill 详情页新增 Link Nacos tab，Skill Package 页面增加无 Nacos 提示

## 数据库变更

### Flyway V10：nacos_instance 表新增字段

```sql
-- 新增 is_default 和 default_namespace 字段
ALTER TABLE nacos_instance ADD COLUMN is_default TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE nacos_instance ADD COLUMN default_namespace VARCHAR(128) NOT NULL DEFAULT 'public';

-- 存量数据：将最早创建的实例标记为默认
UPDATE nacos_instance SET is_default = 1 
WHERE id = (SELECT min_id FROM (SELECT MIN(id) AS min_id FROM nacos_instance) AS t)
AND (SELECT COUNT(*) FROM nacos_instance) > 0;
```

## 后端改动

### 1. NacosInstance 实体新增字段

```java
@Column(name = "is_default", nullable = false)
@Builder.Default
private Boolean isDefault = false;

@Column(name = "default_namespace", length = 128, nullable = false)
@Builder.Default
private String defaultNamespace = "public";
```

### 2. NacosInstanceRepository 新增方法

```java
Optional<NacosInstance> findByIsDefaultTrue();
```

### 3. NacosService 新增方法

```java
// 获取默认 Nacos 实例
NacosResult getDefaultNacosInstance();

// 设置默认 Nacos 实例
void setDefaultNacosInstance(String nacosId);
```

### 4. NacosController 新增端点

```java
@GetMapping("/default")
public NacosResult getDefaultNacosInstance();

@PutMapping("/{nacosId}/default")
public void setDefaultNacosInstance(@PathVariable String nacosId);
```

### 5. NacosResult DTO 新增字段

```java
private Boolean isDefault;
private String defaultNamespace;
```

### 6. NacosServiceImpl.createNacosInstance 改造

创建时检查是否为第一个实例，如果是则自动标记为默认。

### 7. NacosServiceImpl.deleteNacosInstance 改造

删除前检查是否为默认实例，如果是则拒绝删除。

### 8. ProductService 创建 AGENT_SKILL 改造

创建 AGENT_SKILL 类型 Product 时：
- 查找默认 Nacos 实例
- 如果存在，将 nacosId 和 defaultNamespace 写入 feature.skillConfig
- 如果不存在，skillConfig 中 nacosId 为空

### 9. 新增 API：更新 Skill 的 Nacos 关联

```java
// ProductController
@PutMapping("/{productId}/skill-nacos")
@AdminAuth
public void updateSkillNacos(
    @PathVariable String productId,
    @RequestBody UpdateSkillNacosParam param); // { nacosId, namespace }
```

### 10. SkillController 路径改造

路径从 `/skills/nacos` 改为 `/skills`，接口通过 productId 定位：

- `POST /skills/{productId}/upload` — ZIP 上传（通过 productId 解析 nacosId/namespace）
- `GET /skills/{productId}/files` — 获取文件树（兼容前端现有调用）
- `GET /skills/{productId}/files/content` — 获取单文件内容
- `GET /skills/{productId}/document` — 获取 SKILL.md
- `GET /skills/{productId}/download` — ZIP 下载

内部流程：productId → ProductRepository.findByProductId → feature.skillConfig → nacosId + namespace → SkillService

保留 Nacos 直接操作的接口（用于 admin 高级管理）：
- `GET /skills/nacos` — Nacos 侧分页列表（需要 nacosId query param）
- `POST /skills/nacos` — 直接创建 Skill
- 等等

## 前端改动

### 1. Nacos 管理页面（NacosConsoles.tsx）

- 列表新增"默认"Tag 列
- 操作列新增"设为默认"按钮
- 确认弹窗文案：「设为默认后，新建的 Agent Skill 将自动绑定该 Nacos 实例」
- 编辑弹窗新增"默认命名空间"下拉（动态获取命名空间列表）
- 默认实例的删除按钮禁用

### 2. Agent Skill 详情页（ApiProductDetail.tsx）

- AGENT_SKILL 类型的 menuItems 新增 "Link Nacos" tab（在 Skill Package 之后）
- 新建组件 `ApiProductLinkNacos.tsx`

### 3. Link Nacos 组件（ApiProductLinkNacos.tsx）

- 展示当前关联的 Nacos 实例名称、命名空间
- "切换关联"按钮 → 弹窗选择 Nacos 实例和命名空间
- 调用 `PUT /products/{productId}/skill-nacos` 更新

### 4. Skill Package 页面（ApiProductSkillPackage.tsx）

- 加载时检查 product 的 skillConfig.nacosId
- 如果为空，显示提示卡片引导用户导入 Nacos 或关联 Nacos
- 上传区域在无 nacosId 时禁用

### 5. api.ts 新增接口

```typescript
nacosApi: {
  getDefaultNacos: () => api.get('/nacos/default'),
  setDefaultNacos: (nacosId: string) => api.put(`/nacos/${nacosId}/default`),
}

apiProductApi: {
  updateSkillNacos: (productId: string, data: { nacosId: string; namespace: string }) =>
    api.put(`/products/${productId}/skill-nacos`, data),
}
```
