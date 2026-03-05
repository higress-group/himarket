# Bug: HiCoding 沙箱连接时配置传递不完整

## 症状

HiCoding 页面连接沙箱时，后端日志报两个错误：

1. **Skill NPE**：`QwenCodeConfigGenerator.generateSkillConfig` 第 112 行抛出 `NullPointerException`，因为 `skillMdContent` 为 null。前端只传了 `{ name: skill.name }`，没有传 `files` 也没有传 `skillMdContent`。
2. **Model 认证失败**：`session/new` 返回 `Authentication required`。日志显示 `hasModel=false, hasAuthToken=false`，说明 `customModelConfig` 未被正确构建。

## 根因分析

### 对比 HiCli（正常工作）与 HiCoding（异常）的数据流

**HiCli（正确）：**
- `CliSelector` 使用 `MarketModelSelector` 组件 → 返回完整的 `CustomModelFormData`（baseUrl, apiKey, modelId, protocolType）
- `CliSelector` 使用 `MarketSkillSelector` 组件 → 选择时立即调用 `getSkillAllFiles(productId)` 下载完整文件内容
- `handleConnect` 构建完整的 `cliSessionConfig` JSON → 通过 WebSocket `session/config` 发送

**HiCoding（异常）：**
- `ConfigSidebar` 使用简单的 `Select` 选择模型 → 只存储 `modelId` 字符串到 `config.modelId`
- `ConfigSidebar` 使用简单的 `SelectableCard` 选择 Skill → 只存储 `productId` 到 `config.skills[]`
- `buildFinalConfig()` 尝试从内存中的 `marketModels` 数组重建模型配置（脆弱），Skill 只传 `{ name: skill.name }` 没有文件内容
- 结果：后端收到的 skills 没有内容 → NPE；model 配置可能因 `marketModels` 状态不可用而缺失

### 核心问题

`ConfigSidebar.buildFinalConfig()` 在构建 `cliSessionConfig` 时：
- **Skill**：只传了 `name`，没有传 `productId`，也没有下载文件内容。后端无法根据 `name` 反查文件。
- **Model**：依赖组件内存状态 `marketModels` 来查找 `baseUrl`/`apiKey`/`protocolType`，如果状态丢失则 `customModelConfig` 为空。

## 修复方案

**核心思路**：前端只传标识符（`productId` / `modelId`），后端负责解析完整配置。

### 前端（简化）

`ConfigSidebar.buildFinalConfig()`：
- 移除 `customModelConfig` 的构建逻辑（HiCoding 只支持市场模型，不需要前端拼装）
- 改为只在 sessionConfig 顶层传递 `modelId`，后端统一解析
- Skill 条目增加 `productId` 字段（当前只有 `name`）

### 后端（主要工作）

1. **`CliSessionConfig`**：增加顶层 `modelId` 字段；`SkillEntry` 增加 `productId` 字段
2. **`AcpWebSocketHandler.prepareConfigFiles()`**：
   - 当 skill 有 `productId` 但没有 `files`/`skillMdContent` 时，调用 `SkillPackageService.getAllFiles(productId)` 下载文件内容
   - 当 `customModelConfig` 为空但 `modelId` 非空时，通过用户订阅数据解析完整模型配置（baseUrl, apiKey, protocolType）
3. **`QwenCodeConfigGenerator.generateSkillConfig()`**：增加 null 检查，`skillMdContent` 为 null 时跳过而非崩溃

## 影响范围

- `CliSessionConfig.java` — 数据结构变更
- `AcpWebSocketHandler.java` — `prepareConfigFiles` 增加后端解析逻辑
- `QwenCodeConfigGenerator.java` — 防御性 null 检查
- `ConfigSidebar.tsx` — `buildFinalConfig` 传递 `productId` 和 `modelId`
- `coding.ts` — 类型无需变更（`cliSessionConfig` 是序列化后的 JSON 字符串）
