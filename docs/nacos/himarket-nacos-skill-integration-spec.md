# HiMarket 对接 Nacos Skill 管理 — 开发 Spec

> 完全对标 Nacos SDK 能力，Nacos 不具备的能力 HiMarket 侧不做补充。
> 对接方式：直接依赖 `nacos-maintainer-client` SDK，无需自行封装 REST 客户端。

---

## 一、SDK 依赖与初始化

### 1.1 Maven 依赖

```xml
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <artifactId>nacos-maintainer-client</artifactId>
    <version>3.2.04-SNAPSHOT</version>
</dependency>
```

### 1.2 初始化

```java
Properties properties = new Properties();
properties.setProperty("serverAddr", "127.0.0.1:8848");
// 如果 Nacos 开启鉴权
properties.setProperty("username", "nacos");
properties.setProperty("password", "nacos");

AiMaintainerService aiService = AiMaintainerFactory.createAiMaintainerService(properties);
```

### 1.3 Spring Bean 配置

```java
@Configuration
public class NacosSkillConfig {

    @Bean
    public AiMaintainerService aiMaintainerService(NacosSkillProperties props) throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", props.getServerAddr());
        if (StringUtils.hasText(props.getUsername())) {
            properties.setProperty("username", props.getUsername());
        }
        if (StringUtils.hasText(props.getPassword())) {
            properties.setProperty("password", props.getPassword());
        }
        return AiMaintainerFactory.createAiMaintainerService(properties);
    }
}
```

```yaml
nacos:
  skill:
    server-addr: 127.0.0.1:8848
    namespace: public
    username: nacos
    password: nacos
```

### 1.4 基础信息

| 项目 | 值 |
|------|-----|
| SDK 模块 | `nacos-maintainer-client` |
| 核心接口 | `SkillMaintainerService`（由 `AiMaintainerService` 继承） |
| 工厂类 | `AiMaintainerFactory.createAiMaintainerService(properties)` |
| 默认 Namespace | `public` |
| ZIP 上传限制 | 最大 10MB |
| 分页模型 | `Page<T>` — `{ totalCount, pageNumber, pagesAvailable, pageItems[] }` |
| 异常 | 所有方法抛出 `NacosException` |
| 线程安全 | `AiMaintainerService` 实例线程安全，可作为单例 Bean |

---

## 二、Nacos 数据模型

### 2.1 Skill（完整对象）

```java
public class Skill {
    private String namespaceId;       // 命名空间
    private String name;              // 唯一标识（英文字母、下划线、连字符）
    private String description;       // 描述
    private String instruction;       // 指令内容（SKILL.md 的 Markdown body）
    private Map<String, SkillResource> resource;  // 资源文件映射
}
```

必填字段：`name`、`description`、`instruction`

### 2.2 SkillResource（资源文件）

```java
public class SkillResource {
    private String name;                    // 文件名（含扩展名）
    private String type;                    // 类型：template / data / script 等
    private String content;                 // 文件内容（文本原文或 base64）
    private Map<String, Object> metadata;   // 可选元数据
}
```

### 2.3 SkillBasicInfo（列表项）

```java
public class SkillBasicInfo {
    private String namespaceId;
    private String name;
    private String description;
    private Long updateTime;    // 毫秒时间戳
}
```

### 2.4 分页模型

```java
public class Page<E> {
    private int totalCount;
    private int pageNumber;
    private int pagesAvailable;
    private List<E> pageItems;
}
```

---

## 三、SDK 方法清单

### 3.1 创建 Skill（JSON 方式）

```java
String registerSkill(String namespaceId, Skill skill) throws NacosException;
default String registerSkill(Skill skill) throws NacosException; // 默认 namespace
```

```java
Skill skill = new Skill();
skill.setName("my-skill");
skill.setDescription("A useful coding skill");
skill.setInstruction("# My Skill\n\nDetailed instructions...");

Map<String, SkillResource> resources = new HashMap<>();
SkillResource res = new SkillResource();
res.setName("config_template.json");
res.setType("template");
res.setContent("{ \"key\": \"value\" }");
resources.put("config-template", res);
skill.setResource(resources);

String skillName = aiService.registerSkill("public", skill);
```

### 3.2 创建/更新 Skill（ZIP 上传方式）

```java
String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException;
default String uploadSkillFromZip(byte[] zipBytes) throws NacosException;
```

create-or-replace 语义：同名 Skill 不存在则创建，已存在则整体覆盖。

```java
byte[] zipBytes = Files.readAllBytes(Path.of("my-skill.zip"));
String skillName = aiService.uploadSkillFromZip("public", zipBytes);
```

ZIP 包要求：
- 根目录或一级子目录下必须包含 `SKILL.md`
- `SKILL.md` 必须有 YAML frontmatter，包含 `name` 和 `description`
- 自动过滤隐藏文件（`.` 开头）、macOS 元数据文件
- 顶层目录自动剥离；二进制文件自动 base64 编码

> SDK 内部将 zipBytes 做 Base64 编码后发送。接近 10MB 时传输体积会增加约 33%，
> 如遇性能问题可参考 `SkillUploadService` 改用 HTTP multipart 直传。

### 3.3 查询详情

```java
Skill getSkillDetail(String namespaceId, String skillName) throws NacosException;
default Skill getSkillDetail(String skillName) throws NacosException;
```

### 3.4 更新 Skill

```java
boolean updateSkill(String namespaceId, Skill skill) throws NacosException;
default boolean updateSkill(Skill skill) throws NacosException;
```

整体覆盖更新，传入完整 Skill 对象。

### 3.5 删除 Skill

```java
boolean deleteSkill(String namespaceId, String skillName) throws NacosException;
default boolean deleteSkill(String skillName) throws NacosException;
```

### 3.6 分页列表

```java
Page<SkillBasicInfo> listSkills(String namespaceId, String skillName,
                                 String search, int pageNo, int pageSize) throws NacosException;
default Page<SkillBasicInfo> listSkills(String skillName, int pageNo, int pageSize) throws NacosException;
```

| 参数 | 说明 |
|------|------|
| skillName | 按名称筛选（可为空） |
| search | `"accurate"`（精确）或 `"blur"`（模糊） |
| pageNo / pageSize | 分页参数 |

---

## 四、Nacos 能力边界（HiMarket 对齐范围）

以下是 capability-checklist 中列出的能力域，标注 Nacos 实际支持情况。
Nacos 不支持的能力 HiMarket 直接阉割，不做本地补充。

| 序号 | 能力域 | Nacos 支持 | HiMarket 对齐方案 |
|------|--------|-----------|------------------|
| 1 | 元数据 CRUD | ✅ name/description/instruction/resource | 直接透传 SDK |
| 2 | 包上传 | ✅ ZIP 上传，自动解析 SKILL.md | 直接透传 SDK |
| 3 | 包下载 | ⚠️ 无直接 ZIP 下载接口 | HiMarket 从 getSkillDetail 取数据后打包 ZIP |
| 4 | SKILL.md 读写 | ✅ 通过 instruction 字段 | HiMarket 做 frontmatter 拼装/解析 |
| 5 | 文件浏览 | ⚠️ 通过 resource Map 可获取 | HiMarket 从 resource Map 构建文件树 |
| 6 | 列表查询 | ✅ 分页 + 名称筛选（精确/模糊） | 直接透传 SDK |
| 7 | 发布/下线 | ❌ | HiMarket 本地维护发布状态（门户可见性控制） |
| 8 | 状态管理 | ❌ | HiMarket 本地 skill_publish 表，轻量状态：已发布/未发布 |
| 9 | 分类体系 | ❌ | **阉割** |
| 10 | 标签管理 | ❌ | **阉割** |
| 11 | 图标管理 | ❌ | **阉割** |
| 12 | 下载计数 | ❌ | **阉割** |
| 13 | 发布历史 | ❌ | **阉割** |
| 14 | 安全约束 | ✅ ZIP 大小限制、路径穿越防护、隐藏文件过滤 | Nacos 侧处理 |
| 15 | CLI 工具 | ✅ nacos-cli 已实现 | 无需开发 |

---

## 五、HiMarket 服务层设计

### 5.1 架构

```
┌──────────────────────────────────────┐
│         HiMarket Controller          │
│  (REST API — 面向前端/CLI)            │
├──────────────────────────────────────┤
│         HiMarket SkillService        │
│  (SDK 透传 + 发布状态 + 视图构建)      │
├──────────────┬───────────────────────┤
│ AiMaintainer │  skill_publish 表     │
│ Service(SDK) │  (本地发布状态)        │
└──────────────┴───────────────────────┘
```

HiMarket 仅做薄封装：
- 透传 SDK 的 CRUD / 列表 / 上传
- 本地维护发布状态（控制门户可见性）
- 补充 Nacos 没有直接提供的视图层逻辑（文件树构建、ZIP 打包下载、SKILL.md 拼装）

### 5.2 本地数据表

仅一张表，用于记录哪些 Skill 已发布到门户：

```sql
CREATE TABLE skill_publish (
    skill_name  VARCHAR(128) NOT NULL COMMENT '对应 Nacos skill name',
    namespace   VARCHAR(128) NOT NULL DEFAULT 'public',
    published   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '0=未发布 1=已发布',
    published_at DATETIME    NULL COMMENT '最近发布时间',
    unpublished_at DATETIME  NULL COMMENT '最近下线时间',
    PRIMARY KEY (namespace, skill_name)
) COMMENT 'HiMarket 门户发布状态';
```

### 5.3 SkillService 接口

```java
public interface SkillService {

    // ========== CRUD ==========

    /** 创建 Skill（JSON） */
    String createSkill(Skill skill);

    /** 创建/更新 Skill（ZIP 上传） */
    String createOrUpdateSkillFromZip(byte[] zipBytes);

    /** 查询详情 */
    Skill getSkill(String skillName);

    /** 更新 Skill */
    boolean updateSkill(Skill skill);

    /** 删除 Skill（同时清理本地发布状态） */
    boolean deleteSkill(String skillName);

    /** 分页列表 */
    Page<SkillBasicInfo> listSkills(String skillName, String search, int pageNo, int pageSize);

    // ========== 发布/下线（HiMarket 门户可见性） ==========

    /** 发布到门户：校验 Nacos 侧 Skill 存在 → 标记已发布 */
    void publishSkill(String skillName);

    /** 从门户下线 */
    void unpublishSkill(String skillName);

    /** 查询已发布的 Skill 列表（门户展示用） */
    Page<SkillBasicInfo> listPublishedSkills(String skillName, int pageNo, int pageSize);

    /** 查询发布状态 */
    boolean isPublished(String skillName);

    // ========== 文件浏览 ==========

    FileTreeNode getFileTree(String skillName);
    List<SkillFile> getAllFiles(String skillName);
    SkillFile getFileContent(String skillName, String filePath);

    // ========== 包下载 ==========

    String getSkillMd(String skillName);
    StreamingResponseBody downloadSkillZip(String skillName);
}
```

### 5.4 关键业务流程

#### 创建 Skill（JSON）

```
1. 校验参数（name, description, instruction 必填）
2. aiService.registerSkill(namespace, skill)
3. 返回 skillName
```

#### 创建/更新 Skill（ZIP）

```
1. 校验文件大小（≤10MB）
2. aiService.uploadSkillFromZip(namespace, zipBytes)
3. 返回 skillName
```

#### 查询详情

```
1. aiService.getSkillDetail(namespace, skillName)
2. 查询 skill_publish 表补充发布状态
3. 返回 Skill + published 状态
```

#### 发布到门户

```
1. aiService.getSkillDetail(namespace, skillName) — 校验 Nacos 侧存在
2. UPSERT skill_publish 表：published=1, published_at=now()
```

#### 从门户下线

```
1. UPDATE skill_publish 表：published=0, unpublished_at=now()
```

#### 查询已发布列表（门户展示）

```
1. 从 skill_publish 表查出所有 published=1 的 skillName 列表
2. 调用 aiService.listSkills 获取详情，过滤出已发布的
3. 返回分页结果
```

#### 删除 Skill

```
1. aiService.deleteSkill(namespace, skillName)
2. DELETE FROM skill_publish WHERE skill_name = ?
```

#### 文件树构建

```
1. aiService.getSkillDetail(namespace, skillName)
2. 从 resource Map 按路径构建树形结构 + SKILL.md 虚拟节点
3. 目录优先排序
```

#### ZIP 打包下载

```
1. aiService.getSkillDetail(namespace, skillName)
2. 生成 SKILL.md（frontmatter + instruction）
3. 遍历 resource Map，逐个写入 ZipEntry
4. 流式返回
```

---

## 六、HiMarket REST API

### 6.1 接口总览

| 序号 | 方法 | 路径 | 说明 | 数据来源 |
|------|------|------|------|---------|
| 1 | POST | /api/v1/skills | 创建 Skill（JSON） | SDK registerSkill |
| 2 | POST | /api/v1/skills/upload | 创建/更新 Skill（ZIP） | SDK uploadSkillFromZip |
| 3 | GET | /api/v1/skills | 分页列表 | SDK listSkills |
| 4 | GET | /api/v1/skills/{name} | 查询详情 | SDK getSkillDetail + 本地发布状态 |
| 5 | PUT | /api/v1/skills/{name} | 更新 Skill | SDK updateSkill |
| 6 | DELETE | /api/v1/skills/{name} | 删除 Skill | SDK deleteSkill + 清理本地状态 |
| 7 | POST | /api/v1/skills/{name}/publish | 发布到门户 | SDK getSkillDetail（校验）+ 本地 |
| 8 | DELETE | /api/v1/skills/{name}/publish | 从门户下线 | 本地 |
| 9 | GET | /api/v1/skills/published | 已发布列表（门户/CLI） | 本地 + SDK |
| 10 | GET | /api/v1/skills/{name}/document | 获取 SKILL.md | SDK getSkillDetail |
| 11 | GET | /api/v1/skills/{name}/download | 下载 ZIP 包 | SDK getSkillDetail |
| 12 | GET | /api/v1/skills/{name}/files/tree | 文件树 | SDK getSkillDetail |
| 13 | GET | /api/v1/skills/{name}/files | 所有文件（含内容） | SDK getSkillDetail |
| 14 | GET | /api/v1/skills/{name}/files/content?path=x | 单个文件内容 | SDK getSkillDetail |

### 6.2 请求/响应示例

#### 创建 Skill（JSON）

```
POST /api/v1/skills
```
```json
{
  "name": "my-skill",
  "description": "A useful coding skill",
  "instruction": "# My Skill\n\nDetailed instructions...",
  "resources": {
    "config-template": {
      "name": "config_template.json",
      "type": "template",
      "content": "{ \"key\": \"value\" }"
    }
  }
}
```
```json
{ "code": 0, "data": "my-skill" }
```

#### 创建/更新 Skill（ZIP）

```
POST /api/v1/skills/upload
Content-Type: multipart/form-data
file: <zip file>
```
```json
{ "code": 0, "data": "my-skill" }
```

#### 查询详情

```
GET /api/v1/skills/my-skill
```
```json
{
  "code": 0,
  "data": {
    "namespaceId": "public",
    "name": "my-skill",
    "description": "A useful coding skill",
    "instruction": "# My Skill\n\nDetailed instructions...",
    "published": true,
    "publishedAt": "2025-06-01T12:00:00",
    "resource": {
      "config-template": {
        "name": "config_template.json",
        "type": "template",
        "content": "{ \"key\": \"value\" }",
        "metadata": {}
      }
    }
  }
}
```

#### 分页列表

```
GET /api/v1/skills?name=coding&search=blur&pageNo=1&pageSize=20
```
```json
{
  "code": 0,
  "data": {
    "totalCount": 50,
    "pageNumber": 1,
    "pagesAvailable": 3,
    "pageItems": [
      {
        "namespaceId": "public",
        "name": "my-skill",
        "description": "A useful coding skill",
        "updateTime": 1719792000000
      }
    ]
  }
}
```

#### 获取 SKILL.md

```
GET /api/v1/skills/my-skill/document
```
```text
---
name: my-skill
description: A useful coding skill
---

# My Skill

Detailed instructions...
```

#### 下载 ZIP 包

```
GET /api/v1/skills/my-skill/download
Content-Type: application/zip
Content-Disposition: attachment; filename="my-skill.zip"
```

#### 文件树

```
GET /api/v1/skills/my-skill/files/tree
```
```json
{
  "code": 0,
  "data": {
    "name": "my-skill",
    "type": "directory",
    "children": [
      { "name": "SKILL.md", "path": "SKILL.md", "type": "file", "size": 512 },
      {
        "name": "scripts",
        "path": "scripts",
        "type": "directory",
        "children": [
          { "name": "setup.sh", "path": "scripts/setup.sh", "type": "file", "size": 1024 }
        ]
      }
    ]
  }
}
```

#### 发布到门户

```
POST /api/v1/skills/my-skill/publish
```
```json
{ "code": 0, "data": "ok" }
```

#### 从门户下线

```
DELETE /api/v1/skills/my-skill/publish
```
```json
{ "code": 0, "data": "ok" }
```

#### 已发布列表（门户展示）

```
GET /api/v1/skills/published?name=coding&pageNo=1&pageSize=20
```
```json
{
  "code": 0,
  "data": {
    "totalCount": 10,
    "pageNumber": 1,
    "pagesAvailable": 1,
    "pageItems": [
      {
        "namespaceId": "public",
        "name": "my-skill",
        "description": "A useful coding skill",
        "updateTime": 1719792000000
      }
    ]
  }
}
```

---

## 七、HiMarket 需实现的工具逻辑

Nacos SDK 不直接提供但 HiMarket 需要在服务层实现的三块逻辑：

### 7.1 SKILL.md 拼装

从 Skill 对象生成 SKILL.md（参考 Nacos `SkillUtils.toMarkdown()`）：

```java
public static String buildSkillMd(Skill skill) {
    StringBuilder sb = new StringBuilder();
    sb.append("---\n");
    sb.append("name: ").append(escapeYamlValue(skill.getName())).append("\n");
    sb.append("description: ").append(escapeYamlValue(skill.getDescription())).append("\n");
    sb.append("---\n\n");
    if (skill.getInstruction() != null && !skill.getInstruction().isBlank()) {
        sb.append(skill.getInstruction().trim()).append("\n");
    }
    return sb.toString();
}
```

### 7.2 文件树构建

从 `Skill.resource` Map 构建树形结构：

```java
public FileTreeNode buildFileTree(Skill skill) {
    FileTreeNode root = new FileTreeNode(skill.getName(), "", "directory");

    // SKILL.md 虚拟节点
    String md = buildSkillMd(skill);
    root.addFile("SKILL.md", "SKILL.md", md.length());

    // resource 文件
    if (skill.getResource() != null) {
        for (SkillResource res : skill.getResource().values()) {
            root.addFileByPath(res.getName(),
                res.getContent() != null ? res.getContent().length() : 0);
        }
    }
    root.sortChildren(); // 目录优先
    return root;
}
```

### 7.3 ZIP 打包下载

从 Skill 对象重新打包为 ZIP 流式返回：

```java
public StreamingResponseBody downloadAsZip(Skill skill) {
    return out -> {
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            // SKILL.md
            zos.putNextEntry(new ZipEntry("SKILL.md"));
            zos.write(buildSkillMd(skill).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // resource 文件
            if (skill.getResource() != null) {
                for (SkillResource res : skill.getResource().values()) {
                    zos.putNextEntry(new ZipEntry(res.getName()));
                    zos.write(res.getContent().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
        }
    };
}
```

---

## 八、开发任务拆解

| 任务 | 优先级 | 预估 | 说明 |
|------|--------|------|------|
| T1 SDK Bean 配置 | P0 | 0.5d | NacosSkillConfig + Properties |
| T2 SkillService CRUD | P0 | 1d | 透传 SDK 的 6 个方法 |
| T3 SkillController | P0 | 0.5d | 14 个 REST 接口 |
| T4 ZIP 上传接口 | P0 | 0.5d | MultipartFile → byte[] → SDK |
| T5 SKILL.md 拼装 | P0 | 0.5d | frontmatter + instruction |
| T6 skill_publish 表 + 发布/下线 | P0 | 1d | DDL + Repository + 发布/下线/已发布列表 |
| T7 文件树构建 | P1 | 0.5d | resource Map → 树形结构 |
| T8 ZIP 打包下载 | P1 | 0.5d | getSkillDetail → ZipOutputStream |
| T9 单文件/全量文件查询 | P1 | 0.5d | resource Map 按路径查找 |
| T10 集成测试 | P0 | 1d | 对接真实 Nacos 实例 |

总预估：约 6.5 人天

---

## 九、注意事项

1. Nacos 用 `name` 作为 Skill 唯一标识（非自增 ID），HiMarket 的 skillId 直接使用 name
2. name 只允许英文字母、下划线、连字符
3. Nacos 支持 namespace 隔离，HiMarket 配置默认 namespace 即可，大部分场景无需用户感知
4. SDK 鉴权：初始化时传入 username/password，SDK 内部自动处理 token
5. `uploadSkillFromZip` 是 create-or-replace 语义，HiMarket 无需区分创建和更新
6. 发布状态仅存在于 HiMarket 本地 `skill_publish` 表，Nacos 侧无感知。删除 Skill 时需同步清理本地发布记录
7. 本 spec 基于 Nacos 开发分支（3.2.04-SNAPSHOT），需要 Nacos 开启 AI 功能（`ConditionAiEnabled`）
8. `AiMaintainerService` 线程安全，作为单例 Bean 注入即可
