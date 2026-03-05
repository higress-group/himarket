# 设计：HiCoding 沙箱配置后端解析

## 概述

将 HiCoding 的配置解析职责从前端转移到后端。前端只传标识符，后端在沙箱初始化时按需解析完整配置。

## 数据结构变更

### CliSessionConfig.java

```java
@Data
public class CliSessionConfig {
    private CustomModelConfig customModelConfig;  // 现有，前端尽力填充
    private String modelId;                        // 新增：后端兜底用
    private List<McpServerEntry> mcpServers;
    private List<SkillEntry> skills;
    private String authToken;

    @Data
    public static class SkillEntry {
        private String name;
        private String productId;              // 新增：后端按需下载文件
        private String skillMdContent;
        private List<SkillFileEntry> files;
        // SkillFileEntry 不变
    }
}
```

## 后端解析流程

### prepareConfigFiles 增强

在 `AcpWebSocketHandler.prepareConfigFiles()` 中，在调用 generator 之前增加两个解析步骤：

#### 1. Skill 文件解析

```
对于每个 skill in sessionConfig.skills:
  如果 skill.files == null && skill.skillMdContent == null && skill.productId != null:
    调用 skillPackageService.getAllFiles(skill.productId)
    将返回的 SkillFileContentResult 列表转换为 SkillFileEntry 列表
    设置到 skill.files 中
```

#### 2. Model 配置解析

```
如果 sessionConfig.customModelConfig == null && sessionConfig.modelId != null:
  通过 userId 获取当前用户的 consumer
  获取订阅列表，找到包含该 modelId 的产品
  提取 baseUrl、protocolType、apiKey
  构建 CustomModelConfig 并设置到 sessionConfig.customModelConfig
```

这里需要注入 `SkillPackageService`、`ConsumerService`、`ProductService` 到 `AcpWebSocketHandler`。

### Model 解析的服务依赖

复用 `CliProviderController` 中已有的逻辑：
- `ConsumerService.getPrimaryConsumer()` → 获取 consumerId
- `ConsumerService.listConsumerSubscriptions(consumerId)` → 获取订阅
- `ProductService.getProducts(productIds)` → 获取产品详情
- `ConsumerService.getCredential(consumerId)` → 获取 apiKey
- `BaseUrlExtractor.extract()` → 提取 baseUrl
- `ProtocolTypeMapper.map()` → 提取 protocolType

为避免在 Handler 中重复 Controller 的逻辑，抽取一个 `ModelConfigResolver` 服务类。

### ModelConfigResolver（新增）

```java
@Service
public class ModelConfigResolver {
    // 注入 ConsumerService, ProductService
    
    /**
     * 根据 userId 和 modelId 解析完整的模型配置。
     * 返回 null 表示无法解析（用户未订阅该模型等）。
     */
    public CustomModelConfig resolve(String userId, String modelId) {
        // 1. getPrimaryConsumer
        // 2. listConsumerSubscriptions → 筛选 APPROVED
        // 3. getProducts → 筛选 MODEL_API 类型
        // 4. 找到 modelId 匹配的产品
        // 5. 提取 baseUrl, protocolType
        // 6. getCredential → 提取 apiKey
        // 7. 构建 CustomModelConfig 返回
    }
}
```

## 前端变更

### ConfigSidebar.tsx — buildFinalConfig

HiCoding 只支持市场模型，不需要构建 `customModelConfig`。前端只传标识符，后端负责解析。

```typescript
const sessionConfig: Record<string, unknown> = {};

// 模型 — 只传 modelId，后端通过用户订阅解析完整配置
// 不再从 marketModels 内存状态拼装 customModelConfig
if (config.modelId) {
  sessionConfig.modelId = config.modelId;
}

// MCP 配置 — 保持不变（已经传递完整信息）

// Skill 配置 — 传递 productId 供后端解析
const selectedSkillEntries = (config.skills ?? [])
  .map((id) => {
    const skill = skills.find((s) => s.productId === id);
    if (!skill) return null;
    return { name: skill.name, productId: skill.productId };
  })
  .filter((e) => e !== null);
```

移除 `buildFinalConfig` 中对 `marketModels`、`marketModelsApiKey` 的依赖，这些状态变量也可以从 `useCallback` 依赖中移除。

## 防御性修复

### QwenCodeConfigGenerator.generateSkillConfig

在 else 分支（向后兼容路径）增加 null 检查：

```java
} else {
    if (skill.getSkillMdContent() != null) {
        Files.writeString(skillDir.resolve("SKILL.md"), skill.getSkillMdContent());
    } else {
        logger.warn("Skill '{}' has no files and no skillMdContent, skipping", skill.getName());
    }
}
```

## 兼容性

- HiCli 不受影响：它已经传递完整的 `customModelConfig` 和 skill `files`，后端解析逻辑只在缺失时触发
- 旧客户端通过 URL query string 传递配置的方式不受影响
- `CliSessionConfig.customModelConfig` 字段保留，HiCli 仍然使用它；HiCoding 不再填充此字段，改为只传 `modelId`
