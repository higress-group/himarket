# Nacos Skill 维护文档

## 概述

Nacos 的 Skill（技能）是 AI 模块中用于管理 Agent 技能卡的核心能力。每个 Skill 由一份主配置（`skill.json`）和若干资源文件（`SkillResource`）组成，底层通过 Nacos 配置中心存储，配置 Key 遵循固定命名规范。

Nacos 提供了两类 SDK 来使用 Skill 能力：

- `nacos-client`：面向 AI Agent 应用，用于加载、订阅和监听 Skill 变更。
- `nacos-maintainer-client`：面向运维管理平台，用于注册、查询、更新、删除以及打包上传 Skill。

---

## 数据模型

### Skill

| 字段          | 类型                         | 说明                                           |
| ------------- | ---------------------------- | ---------------------------------------------- |
| `namespaceId` | `String`                     | 命名空间，Nacos 内部管理字段                   |
| `name`        | `String`                     | Skill 唯一名称，只允许英文字母、下划线、短横线 |
| `description` | `String`                     | Skill 描述                                     |
| `instruction` | `String`                     | 技能指令内容（主体 prompt）                    |
| `resource`    | `Map<String, SkillResource>` | 资源映射，key 为资源名称                       |

### SkillResource

| 字段       | 类型                  | 说明                                                         |
| ---------- | --------------------- | ------------------------------------------------------------ |
| `name`     | `String`              | 资源文件名，包含扩展名，如 `config_check_template.json`      |
| `type`     | `String`              | 资源类型，如 `template`、`data`、`script`，可包含 `/` 表示多层路径 |
| `content`  | `String`              | 资源内容，字符串格式                                         |
| `metadata` | `Map<String, Object>` | 资源元数据（可选）                                           |

资源唯一标识（`resourceIdentifier`）格式：

- 有 `type` 时：`type::name`
- 无 `type` 时：`name`

### SkillBasicInfo（列表返回）

| 字段          | 类型     | 说明           |
| ------------- | -------- | -------------- |
| `namespaceId` | `String` | 命名空间       |
| `name`        | `String` | Skill 名称     |
| `description` | `String` | 描述           |
| `updateTime`  | `Long`   | 最后更新时间戳 |

---

## nacos-client：应用侧使用

### 入口类

`NacosAiService`，实现了 `AiService` 接口，提供以下 Skill 相关方法。

### 1. 一次性加载 Skill

```java
Skill skill = aiService.loadSkill(skillName);
```

**行为说明：**

- 加载指定 Skill 的主配置（`skill.json`），解析后构建 `Skill` 对象。
- 遍历主配置中的资源列表（`resources`），逐个从配置中心拉取资源内容，组装 `Map<String, SkillResource>`。
- 资源加载失败（如 `NOT_FOUND`）时跳过，不中断整体加载。
- 不建立订阅监听，调用一次，取当时的快照。

**使用示例：**

```java
AiService aiService = new NacosAiService(properties);
Skill skill = aiService.loadSkill("my-skill");
String instruction = skill.getInstruction();
```

**参数约束：**

- `skillName` 不能为空，否则抛 `NacosApiException`（错误码 `PARAMETER_MISSING`）。
- 主配置不存在时，抛 `NacosException`（`NOT_FOUND`）。

### 2. 订阅 Skill（监听变更）

```java
Skill skill = aiService.subscribeSkill(skillName, skillListener);
```

**行为说明：**

- 首次调用时，立即加载当前 Skill 并注册对主配置和所有资源配置的监听。
- 任一配置文件发生变更，触发整体 Skill 重新加载，并通过 `NotifyCenter` 发布 `SkillChangedEvent`。
- 监听器在首次订阅时若 Skill 已存在，会立即触发一次初始化回调（`NacosSkillEvent`）。
- 底层使用 `NacosSkillCacheHolder` 维护本地缓存（`skillCache`）和订阅信息（`subscriptionMap`）。
- 当资源列表发生变化（新增/删除 resource）时，自动同步更新监听器注册。
- 变更检测采用 JSON 序列化比对（字段按字母序排序）。

**使用示例：**

```java
Skill skill = aiService.subscribeSkill("my-skill", new AbstractNacosSkillListener() {
    @Override
    public void onChange(NacosSkillEvent event) {
        // 处理 Skill 变更
        System.out.println("Skill updated: " + event.getSkill().getName());
    }
});
```

**参数约束：**

- `skillName` 和 `skillListener` 均不能为空。
- 若 `skillName` 已订阅，直接返回缓存中的当前 Skill，不重复订阅。

### 3. 取消订阅

```java
aiService.unsubscribeSkill(skillName, skillListener);
```

**行为说明：**

- 注销指定 Skill 的监听器。
- 若该 Skill 下所有监听器已全部注销，则移除主配置和所有资源配置的监听，并清除本地缓存。

**使用示例：**

```java
aiService.unsubscribeSkill("my-skill", myListener);
```

### 4. 本地同步（SkillUtils）

`SkillUtils` 提供将 Skill 对象同步到本地文件系统的工具方法：

```java
SkillUtils.syncToLocal(skill, baseDir, ExistingDirectoryStrategy.OVERWRITE);
```

**生成的目录结构：**

```
{baseDir}/
└── {skillName}/
    ├── SKILL.md
    └── {type}/
        └── {resourceName}
```

**SKILL.md 格式：**

```markdown
# {skillName}

{description}

## Instruction

{instruction}
```

**ExistingDirectoryStrategy 策略：**

| 策略        | 说明                                                         |
| ----------- | ------------------------------------------------------------ |
| `OVERWRITE` | 覆盖已有目录（默认）                                         |
| `BACKUP`    | 将旧目录重命名为 `{name}.backup.{timestamp}` 后写入新目录    |
| `FAIL`      | 目录已存在时直接抛出 `FileAlreadyExistsException`            |

> 写入采用原子操作：先写入临时目录 `{name}.tmp.{timestamp}`，全部写入成功后再重命名，保证目录完整性。

### 5. 监听器实现示例

```java
public class MySkillListener extends AbstractNacosSkillListener {

    @Override
    public void onChange(NacosSkillEvent event) {
        Skill skill = event.getSkill();
        // 重新加载指令
        agent.setInstruction(skill.getInstruction());
        // 重新加载资源
        SkillResource template = skill.getResource().get("template::my_template.json");
    }
}
```

---

## nacos-maintainer-client：运维侧使用

### 入口接口

`SkillMaintainerService`，通过 `AiMaintainerService`（继承自 `SkillMaintainerService`）获取实例。实现类为 `NacosAiMaintainerServiceImpl`，底层通过 HTTP 请求调用 Nacos Admin API。

### 1. 注册 Skill

- HTTP Method：`POST`
- 路径：`AI_SKILL_ADMIN_PATH`
- 请求参数：`namespaceId`、`skillCard`（Skill 对象 JSON 序列化）
- 返回值：注册成功后的 Skill 名称

```java
AiMaintainerService maintainer = NacosAiMaintainerServiceFactory.create(serverAddr);
Skill skill = new Skill();
skill.setName("my-skill");
skill.setDescription("示例技能");
skill.setInstruction("你是一个助手...");
String name = maintainer.registerSkill("dev", skill);
```

### 2. 查询 Skill 详情

- HTTP Method：`GET`
- 路径：`AI_SKILL_ADMIN_PATH`
- 请求参数：`namespaceId`、`skillName`
- 返回值：完整的 Skill 对象（含 `instruction` 和 `resource`）

```java
Skill skill = maintainer.getSkillDetail("dev", "my-skill");
System.out.println(skill.getInstruction());
```

### 3. 更新 Skill

- HTTP Method：`PUT`
- 路径：`AI_SKILL_ADMIN_PATH`
- 请求参数：`namespaceId`、`skillCard`（Skill 对象 JSON）
- 返回值：`true` 表示更新成功

```java
skill.setInstruction("更新后的指令...");
boolean ok = maintainer.updateSkill("dev", skill);
```

### 4. 删除 Skill

- HTTP Method：`DELETE`
- 路径：`AI_SKILL_ADMIN_PATH`
- 请求参数：`namespaceId`、`skillName`
- 返回值：`true` 表示删除成功

```java
boolean ok = maintainer.deleteSkill("dev", "my-skill");
```

### 5. 分页查询 Skill 列表

- HTTP Method：`GET`
- 路径：`AI_SKILL_LIST_ADMIN_PATH`
- `search` 参数：`"accurate"`（精确匹配）或 `"blur"`（模糊匹配，默认）

```java
Page<SkillBasicInfo> page = maintainer.listSkills("dev", 1, 20, "my", "blur");
page.getItems().forEach(info -> System.out.println(info.getName()));
```

### 6. 从 Zip 包上传 Skill

- HTTP Method：`POST`
- 路径：`AI_SKILL_UPLOAD_ADMIN_PATH`
- 请求体：`zipBytes` 经 Base64 编码后作为 body
- 返回值：上传成功后的 Skill 名称

Zip 包内部结构应与 `SkillUtils.syncToLocal` 生成的目录结构一致：

```
my-skill/
├── SKILL.md
└── template/
    └── config_check_template.json
```

```java
byte[] zipBytes = Files.readAllBytes(Paths.get("target/my-skill.zip"));
String name = maintainer.uploadSkillFromZip("prod", zipBytes);
System.out.println("Uploaded: " + name);
```

---

## 两个客户端的对比

| 维度       | `nacos-client`                                  | `nacos-maintainer-client`                              |
| ---------- | ----------------------------------------------- | ------------------------------------------------------ |
| 主要用途   | Agent 应用运行时加载和监听 Skill                | 运维平台管理 Skill 生命周期                            |
| 核心类     | `NacosAiService`                                | `NacosAiMaintainerServiceImpl`                         |
| 通信协议   | 基于 Nacos Config SDK（长连接）                 | HTTP REST                                              |
| 实时推送   | 支持（监听配置变更）                            | 不支持                                                 |
| 写操作     | 不支持                                          | 支持（注册/更新/删除/上传）                            |
| 读操作     | `loadSkill`（快照）、`subscribeSkill`（实时）   | `getSkillDetail`（按需查询）、`listSkills`（分页列表） |

---

## 典型使用场景

### 场景一：Agent 启动时加载技能

```java
AiService aiService = new NacosAiService(properties);
Skill skill = aiService.subscribeSkill("my-agent-skill", new AbstractNacosSkillListener() {
    @Override
    public void onChange(NacosSkillEvent event) {
        // 热更新：重新绑定指令
        agent.setInstruction(event.getSkill().getInstruction());
    }
});
// 初始化 Agent 指令
agent.setInstruction(skill.getInstruction());
```

### 场景二：CI/CD 流水线发布新技能

```java
// 打包 Skill 目录为 zip
byte[] zipBytes = Files.readAllBytes(Paths.get("target/my-skill.zip"));
// 上传到 Nacos
AiMaintainerService maintainer = NacosAiMaintainerServiceFactory.create(serverAddr);
String name = maintainer.uploadSkillFromZip("prod", zipBytes);
System.out.println("Published: " + name);
```

### 场景三：订阅后同步到本地文件（结合 IDE 插件）

```java
aiService.subscribeSkill("my-skill", event -> {
    try {
        SkillUtils.syncToLocal(event.getSkill(), "./skills/",
            SkillUtils.ExistingDirectoryStrategy.BACKUP);
    } catch (IOException e) {
        log.error("Sync failed", e);
    }
});
```
