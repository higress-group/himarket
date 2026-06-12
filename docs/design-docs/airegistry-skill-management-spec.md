# Himarket 支持 AIRegistry Skill 管理开发 Spec

## 1. 背景

Himarket 现有 Agent Skill 管理能力基于开源 Nacos：

- Himarket 本地只保存产品元数据和 Skill 引用。
- `product.feature.skillConfig` 保存 `nacosId`、`namespace`、`skillName`。
- Skill ZIP 上传、版本列表、版本详情、发布、上下线、下载等操作都通过 Nacos maintainer SDK 访问后端仓库。

目标是在不重写 Himarket 产品体系的前提下，让同一套 Agent Skill 产品能力支持 AIRegistry 作为后端 Skill 仓库。用户在 AIRegistry 中已创建工作空间后，只需在 Himarket 配置 AIRegistry 工作空间 ID 和访问凭证，就可以在 Himarket 中管理该工作空间里的 Skill。

参考：

- 阿里云 OpenAPI 文档入口：`https://api.aliyun.com/document/AIRegistry/2026-03-17/overview`
- AIRegistry Java SDK：

```xml
<dependency>
  <groupId>com.aliyun</groupId>
  <artifactId>airegistry20260317</artifactId>
  <version>1.0.0</version>
</dependency>
```

- 本地 AIRegistry 控制台代码和 Java SDK 中 Skill 相关 OpenAPI 动作：`ListSkills`、`GetSkillImportFileUrl`、`UploadSkillViaOss`、`GetSkillDetail`、`GetSkillVersionDetail`、`DownloadSkillVersionViaOss`、`SubmitSkillVersion`、`PublishSkillVersion`、`ForcePublishSkillVersion`、`OnlineSkill`、`OfflineSkill`、`UpdateSkillLabels`、`DeleteSkill`。

## 2. 目标

### 2.1 首期目标

1. 管理员可以在 Himarket 中新增、编辑、删除 AIRegistry 连接配置。
2. AIRegistry 连接配置包含：显示名称、Region、Endpoint、NamespaceId、AccessKeyId、AccessKeySecret，可选 SecurityToken。
3. 新建 Agent Skill 产品时，可以选择默认后端来源为 Nacos 或 AIRegistry。
4. Agent Skill 产品绑定 AIRegistry 后，现有 Skill 包管理页面继续可用：
   - 上传 ZIP 包。
   - 删除 Skill。
   - 查看文件树。
   - 查看文件内容。
   - 查看版本列表。
   - 提交发布。
   - 强制发布。
   - 版本上线/下线。
   - 设置 latest。
   - 删除草稿。
   - 下载 ZIP 包。
5. 开发者门户继续使用 Himarket 的 `/skills/{productId}/download` 下载入口，不要求开发者直接持有 AIRegistry 凭证。
6. 可选增强：管理员可以从指定 AIRegistry Namespace 批量导入 Skill 为 Himarket 产品。

### 2.2 非目标

首期不做以下能力：

- 不在 Himarket 中创建、删除 AIRegistry Namespace。
- 不实现 AIRegistry 成员、RAM、Skill 级权限管理。
- 不实现 AIRegistry 市场 Skill 导入。
- 不改变 Himarket 现有产品发布、订阅、门户展示模型。
- 不改变现有 Nacos Skill 产品行为。
- 不新增本地 Skill 内容持久化。

## 3. 验收标准

1. 管理员配置一个有效 AIRegistry Namespace 后，可以通过 Himarket 创建一个 Agent Skill 产品并上传 ZIP。
2. 上传成功后，Himarket 产品的 `skillName` 被写回，刷新页面后仍能读取文件树、文件内容和版本列表。
3. 同一产品可以执行提交、强制发布、上线、下线、设置 latest、下载 ZIP。
4. 原有 Nacos Skill 产品不受影响，现有接口兼容。
5. Himarket API 响应中不返回完整 AccessKeySecret、SecurityToken。
6. 后端通过公开接口测试覆盖 Nacos 与 AIRegistry 两种来源的路由选择、配置校验和错误映射。
7. 可选增强启用时，AIRegistry Namespace 中已有 Skill 可以批量导入为 Himarket Agent Skill 产品，重复产品按现有规则跳过。

## 4. 数据模型

### 4.1 新增枚举

新增 Skill 后端类型，建议放在 `himarket-dal`：

```java
public enum SkillRegistryType {
    NACOS,
    AIREGISTRY
}
```

不要复用 `SourceType` 的现有语义。`SourceType` 当前主要用于产品引用来源，Skill 包管理需要的是 Skill 仓库后端类型。

### 4.2 扩展 `SkillConfig`

`SkillConfig` 增加以下字段：

```java
private SkillRegistryType registryType; // 空值按 NACOS 兼容旧数据
private String airegistryId;            // AIRegistry 连接配置 ID
```

字段解释：

- `nacosId`：仅 `registryType=NACOS` 使用。
- `airegistryId`：仅 `registryType=AIREGISTRY` 使用。
- `namespace`：继续保存目标命名空间。Nacos 场景是 Nacos namespace，AIRegistry 场景是 AIRegistry NamespaceId。
- `skillName`：两种来源共用。

旧数据兼容规则：

- `registryType == null` 时按 `NACOS` 处理。
- 不迁移历史 `skillConfig` JSON，只在读取时兼容。

### 4.3 新增 AIRegistry 连接表

新增实体 `AiRegistryInstance`，表名建议 `airegistry_instance`。

建独立表的原因：

- AI 网关当前是落在统一 `gateway` 表里，通过 `gateway_type` 和 `apig_config`、`adp_ai_gateway_config`、`apsara_gateway_config` 等 JSON 字段区分，因为它们都属于网关资源，后续会参与 API/MCP/Model/Agent 的网关发现、路由和授权。
- Nacos 当前是独立 `nacos_instance` 表，因为它是外部资源仓库连接配置，保存 serverUrl、凭证、默认 namespace，并被 Skill/Worker/MCP/Agent 等资源引用。
- AIRegistry 在本需求中不是网关，不参与 Himarket 的网关路由模型；它是 Skill 仓库后端连接配置，职责更接近 `nacos_instance`。因此首期不复用 `gateway` 表，单独建 `airegistry_instance` 更符合当前边界。
- 不建议为了 Nacos + AIRegistry 预先抽象通用 `registry_instance` 表。当前只新增 AIRegistry 一类后端，抽象成本高于收益，后续如果 Prompt/AgentSpec/更多 Registry 类型一起接入，再考虑收敛。

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | bigint | 自增主键 |
| `airegistry_id` | varchar(64) | Himarket 内部连接 ID，唯一 |
| `name` | varchar(64) | 展示名称 |
| `admin_id` | varchar(64) | 所属管理员 |
| `region_id` | varchar(64) | POP Region |
| `endpoint` | varchar(256) | 可选，自定义 Endpoint |
| `namespace_id` | varchar(128) | 默认 AIRegistry NamespaceId |
| `access_key_id` | varchar(128) | AccessKeyId |
| `access_key_secret` | varchar(512) | 加密存储 |
| `security_token` | varchar(1024) | 可选，加密存储 |
| `description` | varchar(512) | 说明 |
| `is_default` | boolean | 是否当前管理员的默认 AIRegistry 工作空间配置 |
| `created_at` / `updated_at` | datetime | 继承现有 `BaseEntity` |

数据约束：

- `airegistry_id` 全局唯一。
- 列表、详情、默认配置、删除操作都按 `admin_id = contextHolder.getUser()` 过滤。
- 同一个管理员最多一个默认 AIRegistry 配置。设置默认时在同一事务内清理该管理员其他 `is_default=true` 记录。

安全要求：

- `access_key_secret`、`security_token` 必须加密存储。当前项目已有 `EncryptedStringConverter` 和 `Encryptor`，实体字段优先使用 `@Convert(converter = EncryptedStringConverter.class)`；不要像早期 `NacosInstance` 一样新增明文敏感字段。
- 列表和详情接口只返回 `accessKeyId` 的脱敏值，不返回 secret。
- 日志不得打印完整 AK/SK、SecurityToken、Authorization Header。

### 4.4 默认 Skill 仓库类型

仅有“默认 Nacos”和“默认 AIRegistry 工作空间”还不够。新建 Agent Skill 产品时，后端需要知道默认选择哪一种 Skill 仓库。

新增一个管理员配置，复用现有 `AdminSetting` 能力。注意当前 `AdminSetting` 按 `admin_id + setting_key` 保存，不是系统全局配置：

```text
defaultSkillRegistryType=NACOS|AIREGISTRY
```

规则：

- 未配置时默认 `NACOS`，兼容当前行为。
- `defaultSkillRegistryType=NACOS`：新建 Agent Skill 产品沿用默认 Nacos + 默认 namespace。
- `defaultSkillRegistryType=AIREGISTRY`：新建 Agent Skill 产品绑定默认 AIRegistry 工作空间 + 默认 NamespaceId。
- 如果选择 `AIREGISTRY` 但没有默认 AIRegistry 配置，则新建产品不自动绑定仓库，并在前端引导管理员先配置。
- 配置接口使用现有 `/admin-settings/defaultSkillRegistryType`，值写入 `settingValue`。不要为了这个 key 新增单独表。

## 5. 后端设计

### 5.1 新增 AIRegistry 管理接口

新增 `AiRegistryController`，路径建议 `/airegistries`，仅管理员可访问。后端类名可继续使用 `AiRegistryInstance` 对齐 `NacosInstance`，但接口描述和前端文案应表达为“AIRegistry 工作空间配置”或“AIRegistry Namespace 配置”，避免用户误解为底层 Nacos/MSE 实例。

接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/airegistries` | 分页列出连接配置 |
| `GET` | `/airegistries/default` | 获取默认 AIRegistry 配置 |
| `POST` | `/airegistries` | 新增连接配置 |
| `PUT` | `/airegistries/{airegistryId}` | 更新连接配置 |
| `DELETE` | `/airegistries/{airegistryId}` | 删除连接配置 |
| `PUT` | `/airegistries/{airegistryId}/default` | 设置默认连接，可同时更新默认 NamespaceId |
| `POST` | `/airegistries/{airegistryId}/validate` | 用已保存凭证校验 Namespace 可访问 |

`validate` 的最小实现：调用 `ListSkills(namespaceId, pageNo=1, pageSize=1)`。只验证凭证与命名空间可用，不校验 Skill 内容。

删除配置前必须检查引用：

- 检查 `product.feature.skillConfig.registryType=AIREGISTRY` 且 `airegistryId` 等于待删除配置的产品。
- 只检查当前管理员名下产品，避免跨管理员配置互相影响。
- 因为 `feature` 是 JSON 字段，实现时可先沿用 service 层扫描可见产品的微创方案；如果后续数据量增大，再补数据库 JSON 查询。
- 存在引用时拒绝删除，并返回引用中的产品数量或首个产品名称。

### 5.2 抽象 Skill 仓库操作

新增内部接口 `SkillRegistryOperator`，由 `SkillServiceImpl` 调用。接口只表达 Himarket 需要的能力，不暴露 Nacos 或 AIRegistry SDK 类型。

建议方法：

```java
interface SkillRegistryOperator {
    SkillRegistryType type();
    String uploadFromZip(SkillRegistryRef ref, byte[] zipBytes, String fileName, boolean overwrite);
    void deleteSkill(SkillRegistryRef ref);
    SkillDetail loadSkillVersion(SkillRegistryRef ref, String version);
    SkillMetaDetail loadSkillMeta(SkillRegistryRef ref);
    List<SkillSummaryDetail> listSkills(SkillRegistryRef ref, String keyword, int pageNo, int pageSize);
    String submit(SkillRegistryRef ref, String version);
    void forcePublish(SkillRegistryRef ref, String version, Boolean updateLatestLabel);
    void changeVersionStatus(SkillRegistryRef ref, String version, boolean online);
    void updateLabels(SkillRegistryRef ref, Map<String, String> labels);
    void deleteDraft(SkillRegistryRef ref);
    SkillPackageDownload downloadZip(SkillRegistryRef ref, String version);
}
```

`SkillRegistryRef` 字段：

```java
private SkillRegistryType registryType;
private String nacosId;
private String airegistryId;
private String namespace;
private String skillName;
```

`SkillPackageDownload` 至少包含：

```java
private String fileName;
private String contentType;
private byte[] content;
```

当前 Skill ZIP 限制是 10MB，首期用 byte array 返回可接受。不要把 Nacos/AIRegistry 的 SDK response、临时下载 URL 或 `HttpServletResponse` 泄漏到 `SkillServiceImpl` 的产品编排层。

### 5.3 Nacos 实现

新增 `NacosSkillRegistryOperator`，把 `SkillServiceImpl` 中现有 Nacos maintainer SDK 调用迁移进去。

行为保持不变：

- 旧产品默认 `registryType=NACOS`。
- `uploadSkillFromZip(namespace, zipBytes, true)` 继续按现有覆盖策略执行。
- 下载优先走 Nacos HTTP 下载接口以保留下载计数，失败时本地组装 ZIP 的降级逻辑保留在 Nacos operator 内。
- `autoPublishReviewingVersion`、`ensurePublished`、`resolveLatestVersion` 这些 Nacos 兼容逻辑不要丢，迁移到 Nacos operator 或以 operator 能力暴露给 `SkillServiceImpl`。

### 5.4 AIRegistry 实现

新增 `AiRegistrySkillRegistryOperator`，通过 `com.aliyun:airegistry20260317:1.0.0` 创建 POP SDK Client。

Maven 依赖落点：

- 在根 `pom.xml` 的 `dependencyManagement` 增加 `com.aliyun:airegistry20260317:1.0.0`。
- 在 `himarket-server/pom.xml` 增加实际依赖。
- `himarket-dal` 不依赖 AIRegistry SDK，保持实体和枚举纯数据层。

Client 构造：

- 从 `AiRegistryInstance` 读取 `regionId`、`endpoint`、`accessKeyId`、`accessKeySecret`、`securityToken`。
- Client 以 `airegistryId + credential version` 做缓存。更新连接配置后清理缓存。

操作映射：

| Himarket 操作 | AIRegistry OpenAPI |
| --- | --- |
| 上传 ZIP | `GetSkillImportFileUrl` + HTTP PUT + `UploadSkillViaOss` |
| 删除 Skill | `DeleteSkill` |
| Skill 元信息 | `GetSkillDetail` |
| 版本详情 | `GetSkillVersionDetail` |
| 版本列表 | `GetSkillDetail.versions` |
| 提交发布 | `SubmitSkillVersion` |
| 强制发布 | `ForcePublishSkillVersion` |
| 上线版本 | `OnlineSkill(scope=version)` |
| 下线版本 | `OfflineSkill(scope=version)` |
| 设置 latest | `UpdateSkillLabels(labels={"latest": version})` |
| 删除草稿 | SDK 暂未暴露独立删除草稿接口，首期 AIRegistry 来源返回不支持，Nacos 来源保持现有行为 |
| 下载 ZIP | `DownloadSkillVersionViaOss` 返回临时下载 URL，后端下载后再返回给 Himarket 调用方 |
| 批量导入列表 | `ListSkills` |

上传约束：

- 首期沿用 Himarket 现有 10MB ZIP 限制，避免同一 UI 下 Nacos/AIRegistry 行为不一致。
- 上传流程必须按 Java SDK 真实能力实现：
  1. 调用 `GetSkillImportFileUrl(namespaceId, contentType)` 获取 `uploadUrl`、`ossObjectName`、`maxSize`。
  2. 后端使用 HTTP PUT 把 ZIP 字节上传到 `uploadUrl`，请求 `Content-Type` 使用返回值或 `application/zip`。
  3. PUT 成功后调用 `UploadSkillViaOss(namespaceId, ossObjectName, overwrite=true, commitMsg)` 完成导入。
- `UploadSkillViaOss` 返回的 `data` 作为 `skillName` 写入 `product.feature.skillConfig.skillName`。
- 如果 `GetSkillImportFileUrl.maxSize` 小于 Himarket 当前 10MB 限制，以两者较小值做本次请求校验。

下载约束：

- AIRegistry 来源通过 `DownloadSkillVersionViaOss(namespaceId, skillName, skillVersion)` 获取临时下载 URL。
- Himarket 后端自行 GET 临时 URL，把 ZIP 二进制作为 `/skills/{productId}/download` 响应返回。
- 临时下载 URL 和 AIRegistry 凭证不得返回给浏览器或开发者门户。

状态映射：

- AIRegistry 返回的版本状态映射到现有 `VersionResult.status`。
- `latest` 判定仍以 labels 中的 `latest` 为准。
- 如果 AIRegistry 缺少 downloadCount 或 pipeline 字段，返回 null，不在 Himarket 侧编造。

错误映射：

- `ResourceNotFound` → `ErrorCode.NOT_FOUND`
- `ResourceAlreadyExist` / conflict → `ErrorCode.CONFLICT`
- `InvalidParameter` → `ErrorCode.INVALID_PARAMETER`
- 认证失败、权限不足 → `ErrorCode.INVALID_REQUEST` 或新增更明确错误码；错误消息必须去除敏感字段。
- 其他 POP 异常 → `ErrorCode.INTERNAL_ERROR`

### 5.5 调整 `SkillServiceImpl`

`SkillServiceImpl` 只保留产品级编排：

1. 读取 `Product`。
2. 从 `SkillConfig` 解析 `SkillRegistryRef`。
3. 根据 `registryType` 选择 `SkillRegistryOperator`。
4. 调 operator。
5. 更新 `product.feature.skillConfig.skillName`、`ProductStatus` 等 Himarket 本地状态。

尽量不在 `SkillServiceImpl` 中直接出现 Nacos 或 AIRegistry SDK 调用。

需要一起处理的现有 Nacos 耦合点：

- `downloadPackage` 当前直接拼 Nacos HTTP 下载 URL，并在 URL 上追加用户名密码。改造后由 Nacos operator 内部保留该行为和脱敏日志；AIRegistry operator 使用 AIRegistry 下载接口，不复用这段 URL 拼接。
- `getCliDownloadInfo` 当前返回 `nacosHost/nacosPort/namespace/resourceName`，这是 Nacos CLI 专用结构。首期 AIRegistry 来源返回 `null`，前端隐藏 Nacos CLI 命令；不要把 AIRegistry 凭证或下载地址下发给浏览器。
- `getSkillDetail(nacosId, namespace, skillName, version)`、`deleteSkill(nacosId, namespace, skillName)`、`uploadSkillFromZip(nacosId, namespace, zipBytes)` 属于直接 Nacos 管理能力。首期保持现有签名供旧调用方使用，不为它们扩展 AIRegistry 参数，避免接口范围扩大。

### 5.6 产品来源配置

接口扩展边界：

- 不在所有接口上新增 `SkillRegistryType`。它只描述 Agent Skill 包管理使用哪个 Skill 仓库后端。
- AI 网关不是这个模式。现有 AI 网关仍走 `Gateway` 领域模型，用 `gatewayType` 区分 `APIG_AI`、`ADP_AI_GATEWAY`、`APSARA_GATEWAY`、`HIGRESS` 等网关类型，产品引用链路继续使用 `SourceType=GATEWAY`。
- `ProductRef`、网关配置、MCP/Agent/Model 引用接口不加 `SkillRegistryType`。
- `WORKER` 保持现有 Nacos 行为，不新增 AIRegistry 分支。
- Skill 包管理接口如果已经能从 `productId` 读取 `SkillConfig.registryType`，请求参数不再重复传 `SkillRegistryType`。

`UpdateProductSourceParam` 调整为：

```java
private SourceType sourceType; // 可选，兼容旧 Nacos 请求，不再作为 Skill 仓库类型的主判定字段
private SkillRegistryType registryType;
private String nacosId;
private String airegistryId;
private String namespace;
```

校验规则：

- 移除 `sourceType` 的 `@NotNull` 校验，移除 `nacosId`、`namespace` 上无条件的 `@NotBlank` 校验，改为 service 层按 `registryType` 条件校验。
- `registryType=NACOS`：`nacosId`、`namespace` 必填。
- `registryType=AIREGISTRY`：`airegistryId`、`namespace` 必填。
- `registryType=AIREGISTRY` 仅允许 `ProductType.AGENT_SKILL`。`WORKER` 仍只支持 Nacos，避免本次需求扩散。
- 兼容旧请求：如果 `sourceType=NACOS` 且未传 `registryType`，按 `NACOS` 处理。
- `sourceType` 只保留旧请求兼容，不新增 `SourceType.AIREGISTRY`，也不复用 `SourceType.GATEWAY`。

`ProductServiceImpl` 中相关方法也要同步改名或调整语义：

- `validateNacosOnlineVersion` 改成按产品类型/仓库来源校验在线版本，错误消息不要写死 “Nacos”。
- `cleanupNacosResources` 改成按产品类型清理远端资源；`AGENT_SKILL` 走 Skill registry operator，`WORKER` 继续走 Nacos Worker 逻辑。
- `initDefaultFeature` 只在 `ProductType.AGENT_SKILL` 且 `defaultSkillRegistryType=AIREGISTRY` 时绑定默认 AIRegistry；`WORKER` 永远继续使用默认 Nacos。

### 5.7 下载量同步

当前 `DownloadCountSyncTask` 会扫描所有 `ProductType.AGENT_SKILL`，只要 `skillConfig.nacosId != null` 就按 Nacos 分组拉取下载量。支持 AIRegistry 后必须调整：

- `registryType == null` 或 `NACOS`：保持现有 Nacos 同步逻辑。
- `registryType=AIREGISTRY`：按 `airegistryId + namespace` 分组，通过 AIRegistry `ListSkills` 同步 `downloadCount`。
- AIRegistry 单个 Skill 返回的 `downloadCount` 为空时，跳过该 Skill 的本地更新，不把下载量写成 0。
- `WORKER` 下载量同步保持现有 Nacos 逻辑，不引入 AIRegistry。

### 5.8 批量导入（可选增强）

保留现有 `/skills/import`，增加可选参数：

```text
registryType=NACOS|AIREGISTRY
nacosId=...
airegistryId=...
namespace=...
```

兼容规则：

- 不传 `registryType` 时按现有 Nacos 行为。
- `registryType=AIREGISTRY` 时调用 AIRegistry `ListSkills`，为每个 Skill 创建 Himarket `AGENT_SKILL` 产品。

导入产品时：

- `Product.name` 使用 Skill name。
- `Product.description` 使用 Skill description。
- `Product.status` 根据是否存在 online 版本决定 `READY` 或 `PENDING`。
- `Product.feature.skillConfig.registryType=AIREGISTRY`。
- `Product.feature.skillConfig.airegistryId=...`。
- `Product.feature.skillConfig.namespace=namespaceId`。
- `Product.feature.skillConfig.skillName=skillName`。
- 重名产品沿用现有跳过逻辑。

## 6. 前端设计

### 6.1 管理后台配置页

前端需要支持 AIRegistry 工作空间配置。建议按现有 AI 网关 / Nacos 控制台的交互模式做一个独立配置页，放在管理后台左侧导航的“实例管理”分组下。

新增页面建议：

- 路由：`/consoles/airegistry`
- 页面文件：`himarket-web/himarket-admin/src/pages/AiRegistryConsoles.tsx`
- 导航入口：`himarket-web/himarket-admin/src/components/Layout.tsx`
- 路由注册：`himarket-web/himarket-admin/src/routes/index.tsx`
- API 封装：`himarket-web/himarket-admin/src/lib/api.ts` 新增 `airegistryApi`
- 类型定义：`himarket-web/himarket-admin/src/types/gateway.ts` 或新建 `src/types/airegistry.ts`

页面能力对齐 `NacosConsoles.tsx`：

- 列表展示 AIRegistry 工作空间配置。
- 新增配置。
- 编辑配置。
- 删除配置。
- 设置默认配置。
- 配置连接测试。
- 默认 NamespaceId 展示和修改。
- 默认 Skill 仓库类型设置：允许管理员选择新建 Agent Skill 产品默认使用 Nacos 还是 AIRegistry。

表单字段：

- 名称。
- Region。
- Endpoint，可选。
- NamespaceId。
- AccessKeyId。
- AccessKeySecret。
- SecurityToken，可选。
- 描述。
- 是否默认。

展示要求：

- Secret 字段编辑时默认不回显。
- 保存后可点击“连接测试”。
- 默认配置是否用于新建 Agent Skill 产品，取决于 `defaultSkillRegistryType`。
- 删除前端操作前需要二次确认。
- 删除失败时展示后端返回原因，例如仍有 Skill 产品引用。
- 列表中展示 `airegistryId`，支持一键复制，交互可参考 Nacos 配置页。

`airegistryApi` 最小接口：

```ts
export const airegistryApi = {
  create: (data: CreateAiRegistryRequest) => api.post('/airegistries', data),
  delete: (airegistryId: string) => api.delete(`/airegistries/${airegistryId}`),
  getDefault: () => api.get('/airegistries/default'),
  list: (params?: { page?: number; size?: number }) => api.get('/airegistries', { params }),
  setDefault: (airegistryId: string, namespaceId?: string) =>
    api.put(`/airegistries/${airegistryId}/default`, null, {
      params: namespaceId ? { namespaceId } : undefined,
    }),
  update: (airegistryId: string, data: UpdateAiRegistryRequest) =>
    api.put(`/airegistries/${airegistryId}`, data),
  validate: (airegistryId: string, namespaceId?: string) =>
    api.post(`/airegistries/${airegistryId}/validate`, null, { params: { namespaceId } }),
};
```

### 6.2 Agent Skill 包管理页

现有 `ApiProductSkillPackage` 增加后端来源选择：

- Nacos：选择 Nacos 实例和 Namespace。
- AIRegistry：选择 AIRegistry 配置和 NamespaceId。

当前页面已有 Nacos 绑定弹窗，首期建议直接把该弹窗扩展为“配置 Skill 仓库”弹窗，而不是新增第二个入口。

涉及文件：

- `himarket-web/himarket-admin/src/components/api-product/ApiProductSkillPackage.tsx`
- `himarket-web/himarket-admin/src/lib/api.ts`
- `himarket-web/himarket-admin/src/types/api-product.ts`
- `himarket-web/himarket-admin/src/i18n/locales.ts`

`apiProductApi.updateProductSource` 当前类型写死为 `{ sourceType: 'NACOS'; nacosId: string; namespace: string }`，需要改成联合类型：

```ts
type UpdateProductSourceRequest =
  | { registryType: 'NACOS'; sourceType?: 'NACOS'; nacosId: string; namespace: string }
  | { registryType: 'AIREGISTRY'; airegistryId: string; namespace: string };
```

弹窗字段：

| 字段 | Nacos | AIRegistry |
| --- | --- | --- |
| 后端来源 | `NACOS` | `AIREGISTRY` |
| 配置实例 | Nacos 实例下拉 | AIRegistry 配置下拉 |
| Namespace | 调 `/nacos/{nacosId}/namespaces` 后下拉选择 | 默认填充 AIRegistry 配置的 `namespaceId`，允许手填覆盖 |

保存请求：

```ts
apiProductApi.updateProductSource(productId, {
  registryType: 'AIREGISTRY',
  airegistryId: values.airegistryId,
  namespace: values.namespace,
});
```

兼容旧 Nacos 保存请求：

```ts
apiProductApi.updateProductSource(productId, {
  registryType: 'NACOS',
  nacosId: values.nacosId,
  namespace: values.namespace,
  sourceType: 'NACOS',
});
```

产品已绑定 Skill 后，页面顶部展示：

```text
来源：AIRegistry / {配置名称} / {NamespaceId}
```

按钮和版本表不新增首期独有能力，复用现有上传、发布、上线、下载等交互。

显示规则：

- `skillConfig.registryType` 为空时按 `NACOS` 展示。
- `registryType=NACOS` 时沿用当前 `currentNacosName / namespace`。
- `registryType=AIREGISTRY` 时展示 `AIRegistry 配置名称 / namespace`。
- 当前页面用 `hasNacos = !!apiProduct.skillConfig?.nacosId` 控制上传按钮。改造时要替换成 `hasSkillRegistry`：Nacos 来源检查 `nacosId`，AIRegistry 来源检查 `airegistryId`。否则 AIRegistry 产品会被前端误判为未绑定仓库，导致不能上传。
- 未配置任何仓库时，上传按钮保持不可用或引导用户先配置仓库。

### 6.3 导入入口

Agent Skill 列表页的导入动作增加来源选择：

- 从默认 Nacos 导入。
- 从默认 AIRegistry 导入。

涉及文件：

- `himarket-web/himarket-admin/src/pages/ProductTypePage.tsx`
- `himarket-web/himarket-admin/src/lib/api.ts`
- `himarket-web/himarket-admin/src/i18n/locales.ts`

首期可以只支持默认 AIRegistry；多配置选择可以后续补。

导入请求：

```ts
skillApi.importFromRegistry({
  registryType: 'AIREGISTRY',
  airegistryId: defaultAiRegistry.airegistryId,
  namespace: defaultAiRegistry.namespaceId,
});
```

旧 `skillApi.importFromNacos(nacosId, namespace)` 继续保留，避免影响 Nacos 和 Worker 导入。

如果首期不做批量导入，前端不要出现“从默认 AIRegistry 导入”入口，避免用户点击后发现后端不可用。

### 6.4 新建 Agent Skill 产品默认配置

现有新建 Agent Skill 产品会由后端绑定默认 Nacos。支持 AIRegistry 后，前端需要补充默认 Skill 仓库类型配置。

首期采用简单方案：

1. 新建产品表单不强制选择仓库。
2. 后端根据 `defaultSkillRegistryType` 自动绑定默认 Nacos 或默认 AIRegistry 工作空间。
3. 用户需要切换来源时，在 Skill 包管理页修改仓库配置。

暂不在 `ApiProductFormModal` 中增加完整仓库选择表单，避免把新建产品流程复杂化。

### 6.5 前端验收标准

1. 左侧导航可以进入 AIRegistry 配置页。
2. 管理员可以新增 AIRegistry 配置，填写 NamespaceId 和访问凭证。
3. 管理员可以设置默认 AIRegistry 工作空间、默认 NamespaceId 和默认 Skill 仓库类型。
4. Agent Skill 包管理页可以从 Nacos 切换到 AIRegistry 并保存。
5. 保存后页面刷新仍展示 AIRegistry 配置名称和 NamespaceId。
6. AIRegistry 来源下，上传 ZIP、查看版本、下载包仍走原有按钮和接口。
7. 原有 Nacos 配置、Nacos 导入、Worker 页面不受影响。
8. 开发者门户 Skill 详情页通过 Himarket `/skills/{productId}/download` 下载 AIRegistry Skill，不展示 Nacos CLI 专用命令。

## 7. 安全与权限

1. AIRegistry 访问凭证只允许管理员配置。
2. 开发者门户所有 Skill 读取和下载仍经过 Himarket 后端，不把 AIRegistry 凭证下发给浏览器。
3. 日志只打印 `airegistryId`、`namespaceId`、`skillName`，不打印 secret。
4. 删除 AIRegistry 工作空间配置前，检查是否仍有产品引用。存在引用时拒绝删除。
5. 权限以 AIRegistry/POP 返回结果为准，Himarket 不在首期实现 Skill owner 级二次授权。

## 8. 测试计划

测试遵循 TDD 的纵向切片，不先一次性写完所有测试。每个切片只先写一个描述公开行为的失败测试，再补最少实现让它通过，然后进入下一个切片。

测试原则：

- 优先通过 Controller/API 或前端页面交互验证行为，不测试 private 方法。
- 只 mock 外部边界：AIRegistry POP SDK、Nacos maintainer SDK、网络下载。不要 mock Himarket 内部 service/repository 编排。
- 测试名使用业务语言，例如“管理员可以配置 AIRegistry 工作空间并且响应不暴露密钥”。
- 每个红绿循环完成后再做小范围重构，不能在 RED 状态重构。

### 8.1 行为优先级

P0 必测行为：

1. 管理员可以配置 AIRegistry 工作空间，连接测试通过，查询响应不返回完整 secret。
2. 未配置 `defaultSkillRegistryType` 时，新建 Agent Skill 产品仍自动绑定默认 Nacos。
3. 配置 `defaultSkillRegistryType=AIREGISTRY` 后，新建 Agent Skill 产品自动绑定默认 AIRegistry 工作空间和 NamespaceId。
4. Agent Skill 产品可以从 Nacos 切换到 AIRegistry；Worker 切换到 AIRegistry 会被拒绝。
5. AIRegistry 来源的 Agent Skill 可以通过现有 `/skills/{productId}/package` 上传 ZIP，返回的 `skillName` 被保存到产品配置。
6. AIRegistry 来源的 Agent Skill 可以通过现有文件树、文件内容、版本列表、下载接口读取内容。
7. 原有 Nacos Skill 上传、版本、下载链路不回归。
8. AIRegistry 来源产品通过 AIRegistry `ListSkills` 同步下载量，不会被 `DownloadCountSyncTask` 当成 Nacos 产品处理。
9. AIRegistry 来源产品发布前会校验存在 online 版本，删除产品时会清理 AIRegistry 中的 Skill。

P1 可补行为：

1. AIRegistry 异常映射到 Himarket 统一错误码，且错误消息不包含敏感字段。
2. 删除被产品引用的 AIRegistry 工作空间配置会失败，并返回可理解原因。
3. 前端包管理页切换到 AIRegistry 后，刷新仍展示 AIRegistry 配置名称和 NamespaceId。
4. AIRegistry 来源的 `/skills/{productId}/cli-info` 返回 `null`，开发者门户隐藏 Nacos CLI 命令。

P2 可选增强：

1. 从默认 AIRegistry Namespace 批量导入 Skill。
2. 导入时重复产品按现有规则跳过。

### 8.2 TDD 红绿切片

按下面顺序实现，每个切片独立 RED → GREEN → 小重构。

#### Slice 1：AIRegistry 工作空间配置

公开接口：

- `POST /airegistries`
- `GET /airegistries`
- `POST /airegistries/{airegistryId}/validate`

RED：

- 写一个 API 测试：管理员提交 Region、NamespaceId、AK/SK 后可以创建配置；列表能看到配置；响应中不包含完整 `accessKeySecret`。

GREEN：

- 增加实体、Repository、DTO、Controller、Service 和迁移脚本的最少实现。
- AIRegistry SDK 只在 validate 中通过外部边界 mock 验证调用参数。

#### Slice 2：默认 Skill 仓库类型

公开接口：

- 新建产品接口。
- 管理配置接口：`GET /admin-settings/defaultSkillRegistryType`、`PUT /admin-settings/defaultSkillRegistryType`。

RED：

- 写一个 API 测试：不设置 `defaultSkillRegistryType` 时，新建 `AGENT_SKILL` 仍绑定默认 Nacos。
- 再写一个 API 测试：设置为 `AIREGISTRY` 后，新建 `AGENT_SKILL` 绑定默认 AIRegistry 工作空间。

GREEN：

- 在产品创建默认 feature 初始化逻辑中读取 `AdminSetting`。
- 没有默认 AIRegistry 时不自动绑定，并返回现有产品创建成功结果。
- `AdminSetting` 按当前管理员生效，测试需要使用同一个管理员身份保存设置和创建产品。

#### Slice 3：产品来源切换

公开接口：

- `PUT /products/{productId}/source`

RED：

- 写一个 API 测试：Agent Skill 可以保存 `registryType=AIREGISTRY + airegistryId + namespace`。
- 写一个 API 测试：Worker 使用 `registryType=AIREGISTRY` 返回失败。

GREEN：

- 扩展 `UpdateProductSourceParam` 和校验。
- 只改 `AGENT_SKILL` 分支，Worker 继续沿用 Nacos。

#### Slice 4：AIRegistry 上传 ZIP

公开接口：

- `POST /skills/{productId}/package`

RED：

- 写一个 API 测试：AIRegistry 来源产品上传 ZIP 后，外部 AIRegistry client 依次收到 `GetSkillImportFileUrl`、HTTP PUT、`UploadSkillViaOss` 调用，产品 `skillName` 被写回。

GREEN：

- 引入 `SkillRegistryOperator`。
- 先迁移上传所需的 Nacos operator 最小能力，再补 AIRegistry operator 上传能力。
- 保持现有 10MB 校验。

#### Slice 5：AIRegistry 读取与下载

公开接口：

- `GET /skills/{productId}/files`
- `GET /skills/{productId}/files/{filePath}`
- `GET /skills/{productId}/versions`
- `GET /skills/{productId}/download`
- `GET /skills/{productId}/cli-info`
- `POST /products/{productId}/publications`
- `DELETE /products/{productId}`

RED：

- 写一个 API 测试：AIRegistry 返回 `skillMd/resource/versions/labels` 后，Himarket 文件树、文件内容、版本列表、latest 标记符合现有返回结构。
- 写一个 API 测试：下载接口返回 `application/zip`。
- 写一个 API 测试：AIRegistry 来源 `cli-info` 不返回 Nacos 连接信息。
- 写一个 API 测试：没有 online 版本的 AIRegistry Skill 产品不能发布到门户，错误消息不写死 Nacos。
- 写一个 API 测试：删除 AIRegistry Skill 产品时调用 AIRegistry 删除 Skill。

GREEN：

- 补齐 AIRegistry operator 的元信息、版本详情、下载映射。
- 只引入内部 DTO，不让 Nacos SDK 类型泄漏到 AIRegistry 实现。
- AIRegistry 来源暂不支持 Nacos CLI 下载命令，返回 `null`。
- 调整 `ProductServiceImpl` 的发布校验和删除清理，按 Skill registry 来源路由。

#### Slice 6：Nacos 回归

公开接口：

- 现有 `/skills` 包管理接口。

RED：

- 写一个现有 Nacos 产品的 API 回归测试：上传、版本列表、下载仍走 Nacos 路径。
- 写一个定时同步测试：Nacos Skill 产品仍按 Nacos 分组同步下载量，AIRegistry Skill 产品按 `airegistryId + namespace` 分组并调用 AIRegistry `ListSkills` 同步下载量。

GREEN：

- 修正 operator 抽象引入后的兼容问题。
- 确认 `registryType == null` 仍按 Nacos 处理。
- 为 `DownloadCountSyncTask` 增加 AIRegistry 分支，复用 AIRegistry client/operator 的列表能力，不在定时任务里直接散落 POP SDK 细节。

#### Slice 7：前端工作流

公开界面：

- `/consoles/airegistry`
- Agent Skill 包管理页。

RED：

- 写页面级测试或组件测试：管理员可以填写 AIRegistry 工作空间配置。
- 写页面级测试或组件测试：包管理页选择 AIRegistry 后保存请求包含 `registryType=AIREGISTRY`、`airegistryId`、`namespace`。

GREEN：

- 增加 `airegistryApi`、类型、路由、导航、配置页和包管理页来源选择。

#### Slice 8：批量导入（可选）

公开接口：

- `POST /skills/import`

RED：

- 写一个 API 测试：从 AIRegistry `ListSkills` 返回两条 Skill 时，Himarket 创建两个 Agent Skill 产品。
- 写一个 API 测试：同名产品被跳过。

GREEN：

- 扩展导入参数和 service 分支。

### 8.3 手工验收

1. 在 AIRegistry 创建 Namespace。
2. 在 Himarket 新建 AIRegistry 配置并连接测试成功。
3. 设置 `defaultSkillRegistryType=AIREGISTRY` 后新建 Agent Skill 产品，确认自动绑定默认 AIRegistry 工作空间；或在包管理页手动切换到 AIRegistry 来源。
4. 上传一个合法 Skill ZIP。
5. 查看文件树、版本列表。
6. 强制发布版本并设置 latest。
7. 开发者门户下载该 Skill ZIP。
8. 切回原有 Nacos Skill 产品，确认 Nacos 上传和下载仍正常。

## 9. 实施步骤

不要按“先实现后补测试”的横向方式开发。实施顺序直接采用 8.2 的纵向切片，每个切片完成 RED → GREEN → 小重构后再进入下一项。

1. 准备 SDK 依赖：在 Himarket 后端 Maven 依赖中加入 `airegistry20260317:1.0.0`，只验证能编译，不写业务代码。
2. Slice 1：AIRegistry 工作空间配置。
3. Slice 2：默认 Skill 仓库类型。
4. Slice 3：产品来源切换。
5. Slice 4：AIRegistry 上传 ZIP。
6. Slice 5：AIRegistry 读取与下载。
7. Slice 6：Nacos 回归。
8. Slice 7：前端工作流。
9. Slice 8：批量导入，可选；只有 P0/P1 全部通过后再做。
10. 最终手工验收：跑通 AIRegistry 新链路和 Nacos 老链路。

每个切片的提交建议：

- 提交内容只包含当前切片的失败测试、通过实现和必要重构。
- 不提前实现后续切片字段以外的行为。
- 如果某个切片暴露接口设计问题，先更新 spec，再继续下一个切片。

## 10. 风险与待确认点

1. SDK 暂未暴露独立 `DeleteSkillDraft` 方法。首期 AIRegistry 来源可以返回明确“不支持删除草稿”，或确认控制台另有未生成 SDK 的接口后再补齐。
2. SDK artifact 当前需确认 Maven 仓库可访问性；如果构建源缺少该 artifact，需要先补私服源配置。
3. AIRegistry 和 Nacos 的 ZIP 大小上限可能不同，首期先沿用 Himarket 10MB，并同时尊重 `GetSkillImportFileUrl.maxSize`。
4. AIRegistry 的权限模型比 Himarket 当前管理员模型更细。首期只透传 POP 鉴权结果，不在 Himarket 内模拟 Skill owner 权限。
5. 如果 AIRegistry `ListSkills` 默认只返回当前凭证可见资源，批量导入结果也以该可见范围为准。
