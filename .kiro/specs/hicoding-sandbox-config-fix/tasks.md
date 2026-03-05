# 实现任务

## Task 1: CliSessionConfig 数据结构变更
- [ ] 在 `CliSessionConfig` 顶层增加 `private String modelId` 字段
- [ ] 在 `CliSessionConfig.SkillEntry` 增加 `private String productId` 字段
- [ ] 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/CliSessionConfig.java`

## Task 2: QwenCodeConfigGenerator 防御性 null 检查
- [ ] 在 `generateSkillConfig` 的 else 分支增加 `skillMdContent != null` 检查，为 null 时 warn 日志并跳过
- [ ] 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/QwenCodeConfigGenerator.java`

## Task 3: 新增 ModelConfigResolver 服务
- [ ] 创建 `ModelConfigResolver` 类，注入 `ConsumerService`、`ProductService`
- [ ] 实现 `resolve(String userId, String modelId)` 方法：通过用户订阅数据解析完整模型配置
- [ ] 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/ModelConfigResolver.java`

## Task 4: AcpWebSocketHandler 增加后端配置解析
- [ ] 注入 `SkillPackageService` 和 `ModelConfigResolver`
- [ ] 在 `prepareConfigFiles` 方法中，调用 generator 之前增加 skill 文件解析逻辑：当 skill 有 productId 但无 files/skillMdContent 时，调用 `skillPackageService.getAllFiles(productId)` 并转换为 SkillFileEntry
- [ ] 在 `prepareConfigFiles` 方法中，增加 model 配置解析逻辑：当 customModelConfig 为空但 modelId 非空时，调用 `modelConfigResolver.resolve(userId, modelId)` 填充
- [ ] 需要将 `userId` 传入 `prepareConfigFiles` 方法（当前签名中没有）
- [ ] 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/AcpWebSocketHandler.java`

## Task 5: 前端 ConfigSidebar buildFinalConfig 简化
- [ ] 移除 `customModelConfig` 的构建逻辑（不再从 marketModels 内存状态拼装）
- [ ] 改为只在 sessionConfig 顶层传递 `modelId`
- [ ] Skill 条目增加 `productId` 字段传递
- [ ] 移除 `buildFinalConfig` 对 `marketModels`、`marketModelsApiKey` 的依赖
- [ ] 文件：`himarket-web/himarket-frontend/src/components/coding/ConfigSidebar.tsx`
