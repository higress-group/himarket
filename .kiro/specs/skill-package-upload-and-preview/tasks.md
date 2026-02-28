# 实现任务：Skill Package Upload & Preview

## 任务列表

- [x] 1. 数据库迁移（V8）
  - [x] 1.1 创建 `V8__Add_skill_file_table.sql`，包含：`ALTER TABLE product DROP INDEX uk_name`、`ALTER TABLE product MODIFY COLUMN description VARCHAR(1000)`、`CREATE TABLE skill_file`
  - [x] 1.2 更新 `Product.java` 实体：去掉 `@UniqueConstraint(name = "uk_name")`，`description` 字段 `length` 改为 1000

- [x] 2. 后端 DAL 层——SkillFile 实体与 Repository
  - [x] 2.1 新增 `himarket-dal/src/main/java/com/alibaba/himarket/entity/SkillFile.java`，字段：`id`、`productId`、`path`、`encoding`、`content`（MEDIUMTEXT）、`size`
  - [x] 2.2 新增 `himarket-dal/src/main/java/com/alibaba/himarket/repository/SkillFileRepository.java`，方法：`findByProductId`、`findByProductIdAndPath`、`deleteByProductId`

- [x] 3. 后端 DTO 层
  - [x] 3.1 新增 `himarket-server/.../dto/result/skill/SkillFileTreeNode.java`（含 `name`、`path`、`type`、`encoding`、`size`、`children`）
  - [x] 3.2 新增 `himarket-server/.../dto/result/skill/SkillFileContentResult.java`（含 `path`、`content`、`encoding`、`size`）
  - [x] 3.3 新增 `himarket-server/.../dto/result/skill/SkillPackageUploadResult.java`（含 `fileCount`）

- [x] 4. 后端核心——SkillPackageParser
  - [x] 4.1 新增 `himarket-server/.../core/skill/SkillPackageParser.java`，支持 ZIP（`java.util.zip.ZipInputStream`）和 TAR.GZ（`commons-compress`）解析
  - [x] 4.2 实现 SKILL.md front matter 解析：提取 `---` 块，用 SnakeYAML 解析，取 `name` 和 `description`；缺失时抛出 `ParseException` 携带具体原因
  - [x] 4.3 实现文件过滤：跳过目录项、隐藏文件（`.` 开头）、路径含 `..` 的条目、超过 5MB 的文件
  - [x] 4.4 实现编码判断：尝试 UTF-8 解码，成功 `encoding=text`，失败 `encoding=base64`（Base64 编码存储）
  - [x] 4.5 在 `himarket-server/pom.xml` 中添加 `commons-compress 1.26.1` 依赖

- [x] 5. 后端 Service 层——SkillPackageService
  - [x] 5.1 新增 `SkillPackageService` 接口，方法：`uploadPackage`、`getFileTree`、`getFileContent`、`getAllFiles`、`downloadPackage`
  - [x] 5.2 新增 `SkillPackageServiceImpl`，实现 `uploadPackage`：校验产品类型 → 调用 Parser → 更新 `product.name/description/document` → 批量 upsert `skill_file`
  - [x] 5.3 实现 `getFileTree`：查询 `skill_file` 按路径构建树形结构（按 `/` 分隔路径，递归组装 `SkillFileTreeNode`）
  - [x] 5.4 实现 `getFileContent`：按 `productId + path` 查询单文件
  - [x] 5.5 实现 `getAllFiles`：查询全部文件按路径排序返回
  - [x] 5.6 实现 `downloadPackage`：读取所有文件，`text` 直接写入 zip，`base64` 先解码再写入，流式输出

- [x] 6. 后端 Controller 层——SkillController 扩展
  - [x] 6.1 在 `SkillController` 中新增 `POST /skills/{productId}/package`（`@AdminAuth`），调用 `uploadPackage`，捕获 `ParseException` 返回 `INVALID_ARGUMENT` + 具体 message
  - [x] 6.2 新增 `GET /skills/{productId}/files`（公开），返回文件树
  - [x] 6.3 新增 `GET /skills/{productId}/files/all`（公开），返回所有文件含内容
  - [x] 6.4 新增 `GET /skills/{productId}/files/{filePath}`（公开，`**` PathVariable），返回单文件内容
  - [x] 6.5 新增 `GET /skills/{productId}/package`（公开），流式返回 zip，设置 `Content-Disposition: attachment; filename="{skillName}.zip"`

- [x] 7. 后端——CliSessionConfig & generateSkillConfig 改造
  - [x] 7.1 在 `CliSessionConfig.SkillEntry` 中新增内部类 `SkillFileEntry`（`path`、`content`、`encoding`），并在 `SkillEntry` 中新增 `files: List<SkillFileEntry>` 字段
  - [x] 7.2 更新 `QwenCodeConfigGenerator.generateSkillConfig`：若 `skill.getFiles()` 非空，按 path 写入完整目录结构（`base64` 先解码），否则 fallback 只写 `SKILL.md`

- [x] 8. himarket-admin——Skill Package Tab
  - [x] 8.1 在 `himarket-admin/src/lib/api.ts` 中新增 `uploadSkillPackage`、`getSkillFiles`、`getSkillFileContent` 方法
  - [x] 8.2 新增组件 `himarket-admin/src/components/api-product/ApiProductSkillPackage.tsx`：上方 `Upload.Dragger`（接受 `.zip/.tar.gz`，上传失败展示后端错误信息）+ 下方左树右预览布局
  - [x] 8.3 文件预览逻辑：`encoding=base64` 显示不支持预览；`.md` 用 `react-markdown`；其他用 `react-monaco-editor`（只读，传 `path` 自动检测语言）
  - [x] 8.4 在 `ApiProductDetail.tsx` 的 `menuItems` 中，当 `productType === 'AGENT_SKILL'` 时动态插入 `{ key: "skill-package", label: "Skill Package", icon: InboxOutlined }` Tab，并渲染 `ApiProductSkillPackage` 组件

- [x] 9. himarket-frontend——SkillDetail 改造
  - [x] 9.1 在 frontend API 层新增 `getSkillFiles`、`getSkillFileContent`、`getSkillAllFiles`、`getSkillPackageUrl` 方法
  - [x] 9.2 新增 `himarket-frontend/src/components/skill/SkillFileTree.tsx` 组件，展示文件树，支持点击切换预览
  - [x] 9.3 更新 `SkillDetail.tsx`：页面加载时调用 `getSkillFiles`，非空则在主内容区左侧渲染 `SkillFileTree`（可折叠），空则保持原有布局
  - [x] 9.4 更新 `SkillDetail.tsx` 文件预览逻辑：`encoding=base64` 显示不支持预览；`.md` 用 `MarkdownRender`；其他用 `@monaco-editor/react` Editor（只读，传 `path`）
  - [x] 9.5 更新 `SkillDetail.tsx` 右侧栏：移除 `InstallCommand` 组件，改为"下载 Skill 包"按钮（有 skill_file 数据时触发 `/package` 下载，否则 fallback 到 `/download`）
  - [x] 9.6 扩展前端 `SkillEntry` 类型，新增 `files?: SkillFileEntry[]` 字段（`SkillFileEntry` 含 `path`、`content`、`encoding`）

- [x] 10. himarket-frontend——CliSelector 联动
  - [x] 10.1 更新 CliSelector 中选择 skill 的逻辑：调用 `getSkillAllFiles`，非空时组装 `SkillEntry.files`，空时 fallback 使用 `skillMdContent`
