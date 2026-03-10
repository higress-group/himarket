# 实现任务

## Task 1: SkillConfig 扩展 — 新增 Nacos 坐标字段
- [x] 在 `himarket-dal/src/main/java/com/alibaba/himarket/support/product/SkillConfig.java` 中新增 `nacosId`、`namespace`、`skillName` 三个 String 字段
- [x] 验证编译通过（getDiagnostics）

## Task 2: NacosService 接口扩展 — 暴露 getAiMaintainerService 和 findNacosInstanceById
- [x] 在 `NacosService` 接口中新增 `AiMaintainerService getAiMaintainerService(String nacosId)` 方法
- [x] 在 `NacosService` 接口中新增 `NacosInstance findNacosInstanceById(String nacosId)` 方法
- [x] 在 `NacosServiceImpl` 中实现这两个方法（复用已有的 `buildDynamicAiService` 和 `findNacosInstance`）
- [x] 验证编译通过

## Task 3: 工具类 — SkillMdBuilder 和 FileTreeBuilder
- [x] 新建 `himarket-server/src/main/java/com/alibaba/himarket/core/skill/SkillMdBuilder.java`
- [x] 新建 `himarket-server/src/main/java/com/alibaba/himarket/core/skill/FileTreeBuilder.java`
- [x] 验证编译通过

## Task 4: 工具类 — SkillZipParser（ZIP 解析）
- [x] 新建 `himarket-server/src/main/java/com/alibaba/himarket/core/skill/SkillZipParser.java`
- [x] 验证编译通过

## Task 5: SkillService 接口与实现 — Nacos SDK 透传
- [x] 重写 `SkillService.java` 接口
- [x] 新建 `SkillServiceImpl.java` 实现
- [x] 验证编译通过

## Task 6: SkillController 重构 — Nacos 操作 API
- [x] 重写 `SkillController.java`：路径 `/skills/nacos`，nacosId/namespace 作为 query parameter
- [x] 实现 11 个端点（创建、ZIP上传、列表、详情、更新、删除、文档、下载、文件树、全量文件、单文件）
- [x] 管理操作使用 @AdminAuth，视图类使用 @AdminOrDeveloperAuth
- [x] 验证编译通过

## Task 7: ResolvedSkillEntry 改造 — 坐标+凭证替代文件内容
- [x] 修改 `ResolvedSessionConfig.ResolvedSkillEntry`：移除 `files` 字段，新增坐标+凭证字段
- [x] 验证编译通过

## Task 8: SessionConfigResolver 改造 — 从 Product 读取坐标 + NacosInstance 提取凭证
- [x] 修改 `SessionConfigResolver`：移除 SkillPackageService 依赖，新增 ProductRepository 和 NacosService 依赖
- [x] 改造 `resolveSkillConfig()`：Product → SkillConfig 坐标 → NacosInstance 凭证 → ResolvedSkillEntry
- [x] 验证编译通过

## Task 9: CliConfigGenerator 改造 — 写入坐标+凭证替代写入文件
- [x] 改造 `QoderCliConfigGenerator.generateSkillConfig()`
- [x] 改造 `QwenCodeConfigGenerator.generateSkillConfig()`
- [x] 改造 `ClaudeCodeConfigGenerator.generateSkillConfig()`
- [x] 改造 `OpenCodeConfigGenerator.generateSkillConfig()`
- [x] 验证编译通过

## Task 10: 废弃代码清理
- [x] 删除 `SkillPackageService` 接口
- [x] 删除 `SkillPackageServiceImpl` 实现
- [x] 删除 `SkillFile` 实体
- [x] 删除 `SkillFileRepository`
- [x] 删除 `SkillPackageParser`（无引用）
- [x] 删除 `SkillPackageUploadResult`（无引用）
- [x] 删除过时测试 `SkillServiceImplTest`
- [x] 更新 `QwenCodeConfigGeneratorSkillTest` 适配新逻辑
- [x] 清理所有对已删除类的 import 引用
- [x] 验证全项目编译通过（`./scripts/run.sh` 退出码 0）
