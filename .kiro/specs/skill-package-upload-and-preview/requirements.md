# 需求文档：Skill Package Upload & Preview

## 需求概述

为 himarket 的 AGENT_SKILL 类型产品新增 skill 包上传与预览能力，支持管理员上传完整 skill 包，前台开发者可预览文件树并下载完整包，hicli/hiwork/hiCoding 联动时可获取完整文件内容写入沙箱目录。

## 需求列表

### REQ-1：数据库表结构变更

**REQ-1.1** `product` 表 `name` 字段去掉唯一约束，允许同名产品存在（为未来用户自助录入 skill 做准备）。

**REQ-1.2** `product` 表 `description` 字段从 `VARCHAR(256)` 扩展为 `VARCHAR(1000)`，`Product.java` 实体同步更新。

**REQ-1.3** 新增 `skill_file` 表，字段包括：`id`、`product_id`、`path`（相对路径）、`encoding`（`text` | `base64`）、`content`（MEDIUMTEXT）、`size`、`created_at`、`updated_at`，唯一键为 `(product_id, path)`。

**REQ-1.4** 以上变更通过 Flyway 迁移文件 `V8__Add_skill_file_table.sql` 统一执行。

---

### REQ-2：后端——Skill 包上传接口

**REQ-2.1** 新增接口 `POST /skills/{productId}/package`，需要 `@AdminAuth` 认证，接受 `multipart/form-data` 格式，参数名为 `file`，支持 `.zip` 和 `.tar.gz` 格式。

**REQ-2.2** 上传前校验：productId 对应的产品必须存在且 `type = AGENT_SKILL`，否则返回错误。

**REQ-2.3** 解析压缩包时，必须优先找到并读取根目录下的 `SKILL.md` 文件，解析其 YAML front matter（`---` 块），提取 `name` 和 `description` 字段。

**REQ-2.4** 以下情况必须立即终止上传，返回具体错误信息（不写入任何数据）：
- 压缩包中不存在 `SKILL.md`：提示"压缩包中未找到 SKILL.md 文件"
- `SKILL.md` front matter 缺少 `name` 字段：提示"SKILL.md 缺少 name 字段"
- `SKILL.md` front matter 缺少 `description` 字段：提示"SKILL.md 缺少 description 字段"

**REQ-2.5** 解析成功后，用 front matter 中的 `name` 和 `description` 更新 `product` 表对应字段，同时将 `SKILL.md` 全文写入 `product.document`（向后兼容）。

**REQ-2.6** 提取压缩包内所有文件时，过滤以下条目：
- 目录项
- 以 `.` 开头的隐藏文件
- 单文件原始大小超过 5MB 的文件（记录警告日志，不报错）
- 路径包含 `..` 的条目（防路径穿越，记录警告日志）

**REQ-2.7** 文件编码判断：尝试将字节内容以 UTF-8 解码，成功则 `encoding = "text"` 直接存储文本；失败（二进制文件）则 `encoding = "base64"` 存储 Base64 编码后的字符串。

**REQ-2.8** 所有文件批量 upsert 到 `skill_file` 表（基于唯一键 `uk_product_path`，重复上传覆盖旧内容）。

**REQ-2.9** 文件数量上限 500 个，超出部分截断（记录警告日志）。

**REQ-2.10** 压缩包总大小通过 Spring Boot 配置限制：`spring.servlet.multipart.max-file-size=50MB`。

**REQ-2.11** 上传成功响应：`{"code":"SUCCESS","data":{"fileCount":N}}`。

---

### REQ-3：后端——Skill 文件查询接口

**REQ-3.1** 新增接口 `GET /skills/{productId}/files`，无需认证（公开），返回文件树结构（不含文件内容）。树节点包含：`name`、`path`、`type`（`file` | `directory`）、`encoding`（仅 file 节点）、`size`（仅 file 节点）、`children`（仅 directory 节点）。

**REQ-3.2** 新增接口 `GET /skills/{productId}/files/{filePath}`，无需认证，`filePath` 用 `**` PathVariable 捕获支持多级路径，返回单个文件的 `path`、`content`、`encoding`、`size`。

**REQ-3.3** 新增接口 `GET /skills/{productId}/files/all`，无需认证，一次性返回该 skill 所有文件的 `path`、`content`、`encoding`、`size`，按路径排序，供 CliSelector 使用。

**REQ-3.4** 新增接口 `GET /skills/{productId}/package`，无需认证，动态打包所有 `skill_file` 记录为 zip 流输出，`encoding = "text"` 的文件直接写入，`encoding = "base64"` 的文件先 Base64 解码再写入，响应 Content-Type 为 `application/zip`，文件名为 `{skillName}.zip`。

**REQ-3.5** 保留原有 `GET /skills/{productId}/download` 接口不变（向后兼容，返回 `product.document`）。

---

### REQ-4：himarket-admin 改造

**REQ-4.1** 在产品详情页（`ApiProductDetail.tsx`）的 Tab 列表中，当 `productType === 'AGENT_SKILL'` 时动态插入"Skill Package"Tab。

**REQ-4.2** 新增组件 `ApiProductSkillPackage.tsx`，布局为上方上传区 + 下方左树右预览：
- 上传区：Ant Design `Upload.Dragger` 组件，接受 `.zip` 和 `.tar.gz`，上传失败时展示后端返回的具体错误信息（如"SKILL.md 缺少 name 字段"）
- 文件树：展示 `GET /skills/{id}/files` 返回的树形结构，上传成功后自动刷新
- 预览区：根据文件类型选择渲染方式（见 REQ-4.3）

**REQ-4.3** 文件预览规则：
- `encoding = "base64"`：显示"二进制文件，不支持预览"提示
- 路径以 `.md` 结尾：用 `react-markdown` 渲染
- 其他文本文件：用 `react-monaco-editor`（只读），传入 `path` 属性由 Monaco 自动检测语言，不手动维护扩展名映射

**REQ-4.4** 默认选中 `SKILL.md`，加载其内容展示。

**REQ-4.5** 在 `lib/api.ts` 中新增 API 方法：`uploadSkillPackage`、`getSkillFiles`、`getSkillFileContent`。

---

### REQ-5：himarket-frontend 改造

**REQ-5.1** `SkillDetail.tsx` 页面加载时调用 `GET /skills/{id}/files`，若返回非空则在主内容区左侧展示可折叠文件树侧边栏。若 `skill_file` 表无数据（老数据），文件树不显示，保持原有布局不变。

**REQ-5.2** 新增 `SkillFileTree.tsx` 组件（`components/skill/` 目录），展示 skill 文件树，支持点击文件切换预览内容。

**REQ-5.3** 文件预览规则与 REQ-4.3 一致：`encoding = "base64"` 显示不支持预览；`.md` 用 markdown 渲染；其他文本文件用 `@monaco-editor/react` 的 `Editor` 组件（只读），传入 `path` 自动检测语言。

**REQ-5.4** 默认选中 `SKILL.md`，其内容直接使用 `product.document`（已有字段，无需额外请求，向后兼容）。

**REQ-5.5** 右侧信息栏移除 `InstallCommand` 组件，改为"下载 Skill 包"按钮：
- 若 `skill_file` 表有数据：触发 `GET /skills/{id}/package` 下载 zip
- 若无数据（老数据）：fallback 到 `GET /skills/{id}/download` 下载 SKILL.md

**REQ-5.6** 新增 API 方法：`getSkillFiles`、`getSkillFileContent`、`getSkillAllFiles`、`getSkillPackageUrl`。

---

### REQ-6：CliSelector 联动改造

**REQ-6.1** `CliSessionConfig.SkillEntry` 新增 `files` 字段（`List<SkillFileEntry>`），`SkillFileEntry` 包含 `path`、`content`、`encoding`。保留原有 `skillMdContent` 字段（向后兼容）。

**REQ-6.2** 前端 CliSelector 选择 skill 时，调用 `GET /skills/{id}/files/all`：
- 若返回非空：组装 `SkillEntry.files`，写入 `CliSessionConfig`
- 若返回空（老数据）：fallback 使用 `SkillEntry.skillMdContent = product.document`

**REQ-6.3** 后端 `QwenCodeConfigGenerator.generateSkillConfig` 方法更新：
- 若 `skill.getFiles()` 非空：按 `path` 写入完整目录结构，`encoding = "base64"` 的文件先 Base64 解码再以字节写入
- 否则：向后兼容，只写 `SKILL.md`

**REQ-6.4** 前端 `SkillEntry` 类型扩展，新增 `files?: SkillFileEntry[]` 字段，`SkillFileEntry` 包含 `path`、`content`、`encoding`。
