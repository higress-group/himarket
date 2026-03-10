# 需求文档：Nacos Skill 集成重构

## 简介

将 HiMarket 现有的 Skill 市场逻辑从本地 `product` + `skill_file` 表存储模式，重构为 **Product 表做门户展示层 + Nacos 做 Skill 文件存储层** 的双层架构。

- **门户展示层**：Product 表（type=AGENT_SKILL）继续作为前台门户（Skill 广场、HiCoding Skill 选择器）和管理后台的数据源，提供 icon、status、分页查询、admin 管理流程。`SkillConfig`（ProductFeature 的子字段）新增 Nacos 坐标（nacosId、namespace、skillName），将 Product 从"存储 Skill 文件"变为"指向 Nacos Skill 的引用"
- **Nacos 存储层**：Skill 元数据（name、description、instruction）和文件内容（resource Map）全部由 Nacos 管理，通过 `nacos-maintainer-client` SDK 透传操作
- **ACP 会话解耦**：不再主动下载 Skill 文件注入沙箱，改为传递 Skill 坐标 + Nacos 凭证，由沙箱内置 nacos-cli 下载

Skill 操作复用 HiMarket 现有的"导入 Nacos 实例"管理链路，通过 `nacos_instance` 表中已导入的 Nacos 实例动态获取 `AiMaintainerService`，不再单独维护 Nacos 连接配置或单例 Bean。

## 术语表

- **Nacos_SDK**: `nacos-maintainer-client` 提供的 `AiMaintainerService` 接口，封装了 Skill CRUD、列表等操作
- **NacosInstance**: HiMarket 中已导入的 Nacos 实例记录，存储在 `nacos_instance` 表中，包含 server_url、username、password、access_key、secret_key 等连接信息
- **NacosService**: HiMarket 中负责 Nacos 实例管理的服务层接口，提供 `getAiMaintainerService(nacosId)` 和 `findNacosInstanceById(nacosId)` 方法
- **nacosId**: `nacos_instance` 表的主键，用于标识具体的 Nacos 实例
- **Product 表（门户展示层）**: type=AGENT_SKILL 的 Product 记录，提供 icon、status、分页查询、skillTags、downloadCount 等门户展示能力，feature.skillConfig 中存储 Nacos 坐标
- **SkillConfig**: ProductFeature 的子字段，已有 skillTags/downloadCount，新增 nacosId/namespace/skillName 三个 Nacos 坐标字段
- **Skill_Coordinate**: Skill 的完整坐标信息，由 nacosId + namespace + skillName 三元组组成，存储在 Product.feature.skillConfig 中
- **SkillController**: HiMarket 中负责 Nacos Skill 操作的 REST API 控制器（路径 `/skills/nacos/...`）
- **SkillService**: HiMarket 中负责 Nacos Skill SDK 透传的服务层接口
- **SKILL_MD**: Skill 包中的 `SKILL.md` 文件，包含 YAML frontmatter（name、description）和 Markdown 指令正文
- **Namespace**: Nacos 命名空间，用于隔离 Skill 数据
- **FileTreeNode**: 文件树节点数据结构，用于展示 Skill 包的目录/文件层级
- **ACP_Session**: HiMarket 的 Agent Coding Platform 会话，通过 WebSocket 连接 CLI 工具
- **nacos-cli**: 沙箱内置的 Nacos 命令行工具，能够根据 Skill 坐标和 Nacos 凭证从 Nacos 服务端下载 Skill 文件

## 需求


### 需求 1：复用现有 Nacos 实例管理链路

**用户故事：** 作为后端开发者，我希望 Skill 操作复用 HiMarket 已有的"导入 Nacos 实例"管理链路，以便通过指定 nacosId 动态获取 `AiMaintainerService`，无需单独维护 Nacos 连接配置。

#### 验收标准

1. THE NacosService SHALL 新增 `getAiMaintainerService(String nacosId)` 公开方法，复用现有的 `buildDynamicAiService()` + ConcurrentHashMap 缓存机制
2. THE NacosService SHALL 新增 `findNacosInstanceById(String nacosId)` 公开方法，用于 SessionConfigResolver 提取 Nacos 凭证
3. THE SkillService SHALL 在所有 Skill 操作方法中接收 `nacosId` 参数，通过 `NacosService.getAiMaintainerService(nacosId)` 获取缓存的 `AiMaintainerService` 实例
4. IF 指定的 nacosId 在 `nacos_instance` 表中不存在，THEN SHALL 返回 RESOURCE_NOT_FOUND 错误
5. IF `getAiMaintainerService()` 创建 `AiMaintainerService` 失败，THEN SHALL 记录错误日志并返回 INTERNAL_ERROR 业务异常
6. THE SkillService SHALL 不创建任何 `@Configuration` 类或单例 Bean 来管理 `AiMaintainerService`，不引入独立配置项

### 需求 2：Skill CRUD 操作透传

**用户故事：** 作为管理员，我希望通过 HiMarket API 对指定 Nacos 实例中的 Skill 进行创建、查询、更新、删除操作，以便管理 Nacos 中的 Skill 资源。

#### 验收标准

1. WHEN 管理员提交创建 Skill 请求（包含 nacosId、namespace、name、description、instruction 和可选 resources），THE SkillService SHALL 通过 nacosId 获取 `AiMaintainerService` 并调用 `registerSkill(namespace, skill)` 在 Nacos 中创建 Skill 并返回 skillName
2. WHEN 管理员请求查询 Skill 详情（指定 nacosId、namespace、skillName），THE SkillService SHALL 调用 `getSkillDetail(namespace, skillName)` 获取完整 Skill 对象并返回
3. WHEN 管理员提交更新 Skill 请求，THE SkillService SHALL 调用 `updateSkill(namespace, skill)` 执行整体覆盖更新
4. WHEN 管理员请求删除 Skill（指定 nacosId、namespace、skillName），THE SkillService SHALL 调用 `deleteSkill(namespace, skillName)` 删除 Nacos 侧数据
5. IF Nacos SDK 调用抛出 `NacosException`，THEN THE SkillService SHALL 将异常转换为 HiMarket BusinessException 并返回对应错误码
6. WHEN 管理员请求 Skill 分页列表（指定 nacosId、namespace），THE SkillService SHALL 调用 `listSkills(namespace, skillName, search, pageNo, pageSize)` 返回分页结果

### 需求 3：Skill ZIP 包上传（本地解析 → SDK 注册）

**用户故事：** 作为管理员，我希望通过上传 ZIP 包到指定 Nacos 实例来创建或更新 Skill，以便快速导入包含 SKILL.md 和资源文件的完整 Skill 包。

#### 验收标准

1. WHEN 管理员上传 ZIP 文件（multipart/form-data，指定 nacosId 和 namespace），THE SkillController SHALL 由 HiMarket 的 `SkillZipParser` 在本地解析 ZIP 包（提取 SKILL.md 和 resource 文件，构建 `Skill` 对象），然后调用 `registerSkill(namespace, skill)` 完成注册
2. THE SkillController SHALL 在上传前校验 ZIP 文件大小不超过 10MB
3. IF ZIP 文件大小超过 10MB，THEN SHALL 返回 INVALID_PARAMETER 错误
4. THE SkillService SHALL 利用 Nacos SDK 的 create-or-replace 语义：同名 Skill 不存在则创建，已存在则整体覆盖
5. THE SkillZipParser SHALL 参考 Nacos 服务端的 `SkillZipParser.parseSkillFromZip()` 实现，支持：根目录或一级子目录中的 SKILL.md 查找、YAML frontmatter 解析（name/description）、instruction 正文提取、文本文件 UTF-8 编码、二进制文件 Base64 编码、macOS 元数据文件（._* 前缀）过滤

### 需求 4：Product 表作为门户展示层（SkillConfig 扩展）

**用户故事：** 作为管理员，我希望在 HiMarket 的 Product 表中维护 Skill 的门户展示信息（icon、tags、状态），并通过 SkillConfig 关联到 Nacos 中的 Skill 数据，以便前台门户能做分页、搜索、分类展示。

#### 验收标准

1. THE SkillConfig SHALL 在已有字段（skillTags、downloadCount）基础上新增 nacosId、namespace、skillName 三个 Nacos 坐标字段
2. THE Nacos 坐标 SHALL 存储在 `product.feature` JSON 字段的 `skillConfig` 子对象中，无需新增数据库列或 Flyway 迁移
3. WHEN 管理员创建 AGENT_SKILL 类型的 Product 时，SHALL 在 feature.skillConfig 中填入对应的 Nacos 坐标（nacosId、namespace、skillName）
4. THE Product.status（PUBLISHED/PENDING）SHALL 作为 Skill 在门户的发布/下线控制，不需要单独的 `skill_publish` 表
5. THE 前台门户（Skill 广场页、HiCoding Skill 选择器）SHALL 继续通过 Product 表查询 AGENT_SKILL 类型产品，复用现有的分页、搜索、分类展示能力
6. THE 管理员创建 Skill 的完整流程 SHALL 为两步：第一步在 Nacos 创建 Skill（通过 SkillController），第二步在 HiMarket 创建 Product 关联（通过 ProductController，feature.skillConfig 含坐标）

### 需求 5：SKILL.md 拼装与获取

**用户故事：** 作为 CLI 工具使用者，我希望获取指定 Nacos 实例中 Skill 的 SKILL.md 文档内容，以便了解 Skill 的使用说明。

#### 验收标准

1. WHEN 请求获取 SKILL.md 文档（指定 nacosId、namespace、skillName），THE SkillService SHALL 从 `getSkillDetail()` 获取 Skill 对象，拼装 YAML frontmatter（name、description）和 instruction 正文生成 SKILL.md 内容
2. THE SkillMdBuilder SHALL 按照 `---\nname: {name}\ndescription: {description}\n---\n\n{instruction}` 格式生成 SKILL.md
3. FOR ALL 有效的 Skill 对象，解析生成的 SKILL.md 再提取 name 和 description SHALL 与原始 Skill 对象的 name 和 description 一致（往返一致性）

### 需求 6：文件树构建

**用户故事：** 作为前端开发者，我希望获取指定 Nacos 实例中 Skill 的文件树结构，以便在 UI 中展示 Skill 包的目录层级。

#### 验收标准

1. WHEN 请求 Skill 文件树（指定 nacosId、namespace、skillName），THE SkillService SHALL 从 `getSkillDetail()` 获取 Skill 对象，从 `resource` Map 构建树形 FileTreeNode 结构
2. THE FileTreeBuilder SHALL 在文件树根节点下添加 SKILL.md 虚拟节点（由 name、description、instruction 拼装生成）
3. THE FileTreeBuilder SHALL 按目录优先、同类型按名称字母序排列文件树节点
4. THE FileTreeBuilder SHALL 为每个文件节点提供 name、path、type（file/directory）和 size 属性

### 需求 7：ZIP 打包下载

**用户故事：** 作为用户，我希望下载指定 Nacos 实例中 Skill 的完整 ZIP 包，以便在本地使用或分发 Skill。

#### 验收标准

1. WHEN 请求下载 Skill ZIP 包（指定 nacosId、namespace、skillName），THE SkillService SHALL 从 `getSkillDetail()` 获取 Skill 对象，生成 SKILL.md 并与所有 resource 文件一起打包为 ZIP 流式返回
2. THE SkillService SHALL 设置 HTTP 响应头 `Content-Type: application/zip` 和 `Content-Disposition: attachment; filename="{skillName}.zip"`
3. THE SkillService SHALL 使用 `StreamingResponseBody` 流式写入 ZIP 内容，避免将整个 ZIP 加载到内存

### 需求 8：单文件与全量文件查询

**用户故事：** 作为前端开发者，我希望查询指定 Nacos 实例中 Skill 的单个文件或所有文件的内容，以便在 UI 中展示文件内容。

#### 验收标准

1. WHEN 请求所有文件内容（指定 nacosId、namespace、skillName），THE SkillService SHALL 将 SKILL.md（虚拟生成）和所有 resource 文件转换为 SkillFileContentResult 列表返回
2. WHEN 请求单个文件内容（指定 nacosId、namespace、skillName 和 path 参数），THE SkillService SHALL 从 Skill 的 resource Map 中按路径查找对应文件并返回内容
3. IF 请求的文件路径为 "SKILL.md"，THEN THE SkillService SHALL 返回拼装生成的 SKILL.md 内容
4. IF 请求的文件路径在 resource Map 中不存在，THEN THE SkillService SHALL 返回 RESOURCE_NOT_FOUND 错误


### 需求 9：REST API 接口层（双层分离）

**用户故事：** 作为前端/CLI 开发者，我希望通过清晰分离的 REST API 分别访问 Nacos Skill 操作和门户展示功能。

#### 验收标准

1. THE SkillController SHALL 提供 Nacos 操作 API，路径为 `/skills/nacos/...`，`nacosId` 和 `namespace` 作为 query parameter：
   - POST `/skills/nacos`（创建 Skill）
   - POST `/skills/nacos/upload`（ZIP 上传）
   - GET `/skills/nacos`（Nacos 侧分页列表）
   - GET `/skills/nacos/{name}`（Nacos 侧详情）
   - PUT `/skills/nacos/{name}`（更新）
   - DELETE `/skills/nacos/{name}`（删除）
   - GET `/skills/nacos/{name}/document`（SKILL.md 文档）
   - GET `/skills/nacos/{name}/download`（ZIP 下载）
   - GET `/skills/nacos/{name}/files/tree`（文件树）
   - GET `/skills/nacos/{name}/files`（全量文件）
   - GET `/skills/nacos/{name}/files/content`（单文件，path 作为 query parameter）
2. THE 门户展示 API SHALL 复用现有 ProductController（`/products/...`），管理 AGENT_SKILL 类型 Product 的 CRUD 和发布状态
3. THE SkillController SHALL 对管理操作接口（创建、上传、更新、删除）使用 `@AdminAuth` 注解
4. THE SkillController SHALL 对视图类接口（文档、下载、文件树、文件内容）不要求认证或使用 `@AdminOrDeveloperAuth`
5. THE SkillController SHALL 使用 Skill 的 `name` 作为路径参数标识符（非自增 ID），name 只允许英文字母、下划线、连字符

### 需求 10：ACP 会话 Skill 坐标传递与 nacos-cli 下载

**用户故事：** 作为 ACP 会话系统，我希望会话中不再主动下载和注入 Skill 文件，而是将 Skill 坐标和 Nacos 凭证写入 CLI 工具配置文件，由沙箱内置的 nacos-cli 负责下载 Skill 文件。

#### 验收标准

1. THE CliSessionConfig.SkillEntry SHALL 继续使用 `productId` 作为 Skill 标识符（前端门户选择的是 Product）
2. WHEN ACP 会话解析 Skill 配置时，THE SessionConfigResolver SHALL 通过 productId 查询 Product 表 → 提取 feature.skillConfig 中的 Nacos 坐标（nacosId、namespace、skillName）→ 通过 nacosId 查询 NacosInstance 提取凭证
3. THE SessionConfigResolver SHALL 不再调用 `SkillPackageService.getAllFiles()` 下载 Skill 文件内容
4. THE ResolvedSessionConfig.ResolvedSkillEntry SHALL 不再包含 `files`（SkillFileContentResult 列表），改为包含 Skill 坐标（nacosId、namespace、skillName）和 Nacos 凭证（serverAddr、username、password、accessKey、secretKey）
5. IF Product 的 feature.skillConfig 中 Nacos 坐标缺失（nacosId/namespace/skillName 为空），THEN THE SessionConfigResolver SHALL 记录错误日志并跳过该 Skill
6. IF 指定的 nacosId 在 `nacos_instance` 表中不存在，THEN THE SessionConfigResolver SHALL 记录错误日志并跳过该 Skill

### 需求 11：CLI Skill 列表（继续使用 Product 表）

**用户故事：** 作为 CLI 工具使用者，我希望获取已发布的 Skill 列表，以便在 CLI 中选择和使用 Skill。

#### 验收标准

1. THE CliProviderController.listMarketSkills() SHALL 继续从 Product 表查询 AGENT_SKILL 类型、PUBLISHED 状态的产品，复用现有逻辑
2. THE MarketSkillInfo SHALL 继续包含 productId、name、description、skillTags 字段（前端通过 productId 创建 ACP 会话）
3. THE 前端 MarketSkillSelector 和 SkillCard SHALL 继续使用 productId 体系，不需要感知 Nacos 坐标（坐标解析在后端 SessionConfigResolver 中完成）

### 需求 12：旧数据模型清理

**用户故事：** 作为后端开发者，我希望清理不再使用的旧 Skill 数据模型和代码，以便保持代码库整洁。

#### 验收标准

1. THE 重构 SHALL 删除 `SkillPackageService` 接口及其实现 `SkillPackageServiceImpl`（功能由新 SkillService 承接）
2. THE 重构 SHALL 删除 `SkillFile` 实体和 `SkillFileRepository`（不再需要）
3. THE `skill_file` 表 SHALL 标记为废弃（保留数据以备回滚），不再有代码引用
4. THE Product 表和 `ProductType.AGENT_SKILL` SHALL 保留，继续作为门户展示层
5. THE SkillConfig SHALL 保留并扩展（新增 nacosId、namespace、skillName），继续存储 skillTags、downloadCount
6. THE 重构 SHALL 不创建 `skill_publish` 表（发布状态由 Product.status 管理）
7. THE 重构 SHALL 不需要新增 Flyway 迁移脚本（Nacos 坐标存储在 JSON 字段中，无 DDL 变更）

### 需求 13：Nacos 凭证与 Skill 坐标写入 CLI 配置文件

**用户故事：** 作为沙箱内的 nacos-cli 工具，我需要从 CLI 配置文件中读取 Nacos 凭证和 Skill 坐标信息，以便在沙箱启动后自动下载指定的 Skill 文件。

#### 验收标准

1. THE CliConfigGenerator.generateSkillConfig() SHALL 将 Skill 坐标和 Nacos 凭证写入 CLI 配置文件（如 `.qoder/settings.json` 的 `skills` 段），而非写入实际的 Skill 文件目录
2. THE 每个 Skill 条目 SHALL 包含完整的 Skill_Coordinate（nacosId、namespace、skillName）和对应的 Nacos 凭证（serverAddr、username、password、accessKey、secretKey）
3. WHEN 会话配置包含多个 Skill 且来自不同 Nacos 实例时，THE CliConfigGenerator SHALL 为每个 Skill 独立写入其对应的 Nacos 凭证信息
4. THE CliConfigGenerator SHALL 确保凭证信息仅写入沙箱内的 CLI 配置文件，不在日志或 API 响应中暴露完整凭证
5. ALL 4 个 CliConfigGenerator 实现（QoderCli、QwenCode、ClaudeCode、OpenCode）的 generateSkillConfig() 逻辑当前完全相同，SHALL 统一改造为写入坐标+凭证（可考虑提取公共逻辑到接口 default 方法）
6. WHEN nacos-cli 读取配置文件中的 Skill 条目后，SHALL 使用凭证连接对应的 Nacos 实例，根据 namespace 和 skillName 下载 Skill 文件到 CLI 工具的 skills 目录
