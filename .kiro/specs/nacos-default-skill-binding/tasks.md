# 实现任务

## Task 1: 数据库迁移 — nacos_instance 新增 is_default 和 default_namespace
- [x] 创建 `V10__Add_nacos_default_fields.sql`
- [x] ALTER TABLE 新增 is_default（TINYINT(1) NOT NULL DEFAULT 0）和 default_namespace（VARCHAR(128) NOT NULL DEFAULT 'public'）
- [x] 存量数据迁移：将最早创建的实例标记为默认
- [x] 验证迁移脚本

## Task 2: 后端 — NacosInstance 实体和 Repository 扩展
- [x] NacosInstance 实体新增 isDefault 和 defaultNamespace 字段
- [x] NacosInstanceRepository 新增 findByIsDefaultTrue() 方法
- [x] NacosResult DTO 新增 isDefault 和 defaultNamespace 字段
- [x] 验证编译通过

## Task 3: 后端 — NacosService 默认实例管理
- [x] NacosService 接口新增 getDefaultNacosInstance() 和 setDefaultNacosInstance(nacosId) 方法
- [x] NacosServiceImpl 实现：setDefault 时先取消旧默认再设置新默认
- [x] NacosServiceImpl.createNacosInstance 改造：第一个实例自动标记为默认
- [x] NacosServiceImpl.deleteNacosInstance 改造：默认实例不允许删除
- [x] NacosServiceImpl.updateNacosInstance 改造：支持更新 defaultNamespace
- [x] NacosController 新增 GET /nacos/default 和 PUT /nacos/{nacosId}/default 端点
- [x] 验证编译通过

## Task 4: 后端 — ProductService 创建 AGENT_SKILL 自动绑定
- [x] ProductServiceImpl.createProduct 中检测 AGENT_SKILL 类型
- [x] 查找默认 Nacos 实例，写入 feature.skillConfig（nacosId + defaultNamespace）
- [x] 新增 UpdateSkillNacosParam DTO
- [x] ProductController 新增 PUT /products/{productId}/skill-nacos 端点
- [x] ProductServiceImpl 实现 updateSkillNacos 方法
- [x] 验证编译通过

## Task 5: 后端 — SkillController 路径改造（productId 解析）
- [x] SkillController 路径改为 /skills
- [x] 新增通过 productId 解析 Nacos 坐标的私有方法（resolveSkillCoordinate）
- [x] 改造上传接口：POST /skills/{productId}/package
- [x] 改造文件查询接口：GET /skills/{productId}/files、files/{filePath}
- [x] 改造文档和下载接口：GET /skills/{productId}/document、download
- [x] 新增 PUT /skills/{productId}/skill-md 更新 SKILL.md
- [x] 保留 Nacos 直接操作接口（/skills/nacos/...）用于高级管理
- [x] 验证编译通过并重启测试

## Task 6: 前端 — Nacos 管理页面增加默认标记和切换
- [x] NacosConsoles.tsx 列表新增"默认"Tag 列
- [x] 操作列新增"设为默认"按钮（非默认实例显示）
- [x] 确认弹窗文案
- [x] 默认实例删除按钮禁用
- [x] 编辑弹窗新增"默认命名空间"下拉选择
- [x] api.ts 新增 getDefaultNacos 和 setDefaultNacos 接口

## Task 7: 前端 — Agent Skill 详情页新增 Link Nacos tab
- [x] 新建 ApiProductLinkNacos.tsx 组件
- [x] 展示当前关联的 Nacos 实例和命名空间
- [x] 切换关联弹窗（选择 Nacos 实例 + 命名空间）
- [x] ApiProductDetail.tsx 中 AGENT_SKILL 类型新增 Link Nacos tab
- [x] api.ts 新增 updateSkillNacos 接口

## Task 8: 前端 — Skill Package 页面无 Nacos 提示
- [x] ApiProductSkillPackage.tsx 接收 apiProduct prop（需要 skillConfig 信息）
- [x] 检测 skillConfig.nacosId 是否有值
- [x] 无 nacosId 时显示提示卡片和跳转链接
- [x] 上传区域在无 nacosId 时禁用

## Task 9: 端到端验证
- [x] 重启后端验证编译和启动
- [x] 验证默认 Nacos 实例 API（GET /nacos/default）
- [x] 验证创建 AGENT_SKILL 自动绑定（POST /products）
- [x] 验证 updateSkillNacos API（PUT /products/{productId}/skill-nacos）
- [x] 验证 Skill 文件查询（GET /skills/{productId}/files）
- [x] 验证 Nacos 列表返回 isDefault 字段
