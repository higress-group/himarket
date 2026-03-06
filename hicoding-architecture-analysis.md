# HiCoding 架构流程梳理与优化建议

## 1. 完整流程概览

```
前端发起连接
    ↓
WebSocket 握手 (AcpHandshakeInterceptor)
    ↓
WebSocket 连接建立 (AcpWebSocketHandler)
    ↓
解析会话配置 (CliSessionConfig → ResolvedSessionConfig)
    ↓
沙箱初始化流水线 (SandboxInitPipeline)
    ├── 获取沙箱 (SandboxAcquirePhase)
    ├── 文件系统检查 (FileSystemReadyPhase)
    ├── 配置注入 (ConfigInjectionPhase)
    ├── CLI 就绪检查 (CliReadyPhase)
    └── Sidecar 连接 (SidecarConnectPhase)
    ↓
双向消息转发 (前端 ↔ Sidecar ↔ CLI)
    ↓
正常对话
```

## 2. 核心组件详解

### 2.1 WebSocket 握手层 (AcpHandshakeInterceptor)

**职责：**
- 认证用户身份（token 验证）
- 提取连接参数（provider, runtime, sandboxMode, cliSessionConfig）
- 将参数存入 WebSocket session attributes

**关键代码路径：**
```
himarket-server/src/main/java/com/alibaba/himarket/service/acp/AcpHandshakeInterceptor.java:32-104
```

**处理的参数：**
- `token` - JWT 认证令牌
- `provider` - CLI 提供者（kiro-cli, qwen-code, opencode, claude-code）
- `runtime` - 运行时类型（local, k8s）
- `sandboxMode` - 沙箱模式（user, session）
- `cliSessionConfig` - JSON 格式的会话配置（URL 编码）

**问题点：**
1. ❌ 参数解析逻辑分散，缺少统一的参数验证
2. ❌ cliSessionConfig 需要 URL 编码/解码，容易出错
3. ❌ 匿名模式（POC mode）的安全性考虑不足

### 2.2 WebSocket 处理层 (AcpWebSocketHandler)

**职责：**
- 接收 WebSocket 连接
- 初始化沙箱环境
- 转发前端和 CLI 之间的消息
- 处理连接关闭和错误

**关键代码路径：**
```
himarket-server/src/main/java/com/alibaba/himarket/service/acp/AcpWebSocketHandler.java:1-800+
```

**核心流程：**
1. `afterConnectionEstablished()` - 连接建立后触发
2. 从 attributes 提取配置参数
3. 构建 `InitConfig` 和 `InitContext`
4. 调用 `SandboxInitPipeline.execute()` 初始化沙箱
5. 建立双向消息转发
6. 处理 `handleTextMessage()` 和 `handleBinaryMessage()`

**问题点：**
1. ❌ 单个类超过 800 行，职责过重
2. ❌ 配置解析逻辑（CliSessionConfig → ResolvedSessionConfig）嵌入在 WebSocket 处理中
3. ❌ 错误处理不够细粒度，难以定位问题
4. ❌ 缺少连接状态管理（连接中、初始化中、就绪、错误）

### 2.3 沙箱初始化流水线 (SandboxInitPipeline)

**职责：**
- 按顺序执行多个初始化阶段（InitPhase）
- 提供重试机制和验证逻辑
- 记录每个阶段的执行时间和事件

**关键代码路径：**
```
himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/SandboxInitPipeline.java:1-176
```

**执行阶段：**
1. **SandboxAcquirePhase** - 获取或创建沙箱实例
2. **FileSystemReadyPhase** - 验证文件系统可用性
3. **ConfigInjectionPhase** - 注入配置文件（模型、MCP、Skill）
4. **CliReadyPhase** - 验证 CLI 工具可用性
5. **SidecarConnectPhase** - 建立 Sidecar WebSocket 连接

**优点：**
- ✅ 清晰的阶段划分
- ✅ 统一的重试和验证机制
- ✅ 详细的事件记录

**问题点：**
1. ❌ 阶段之间的依赖关系不够明确
2. ❌ 配置传递通过 `InitContext` 的 Map，类型不安全
3. ❌ 缺少阶段跳过逻辑（如配置未变更时跳过注入）

### 2.4 沙箱提供者 (SandboxProvider)

**接口定义：**
```java
public interface SandboxProvider {
    SandboxType getType();
    SandboxInfo acquire(SandboxConfig config);
    void release(SandboxInfo info);
    boolean healthCheck(SandboxInfo info);
    void writeFile(SandboxInfo info, String relativePath, String content);
    String readFile(SandboxInfo info, String relativePath);
    int extractArchive(SandboxInfo info, byte[] tarGzBytes);
    RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config);
}
```

#### 2.4.1 K8sSandboxProvider

**职责：**
- 通过 `PodReuseManager` 管理 K8s Pod 生命周期
- 通过 Sidecar HTTP API 操作文件系统
- 通过 Sidecar WebSocket 桥接 CLI 进程

**关键代码路径：**
```
himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/K8sSandboxProvider.java:1-270
```

**核心机制：**
- Pod 复用：通过 `PodReuseManager` 实现用户级别的 Pod 复用
- 文件操作：统一通过 Sidecar HTTP API（`/files/write`, `/files/read`, `/files/extract`）
- 健康检查：HTTP 请求 `/health` 端点（避免 TCP 探测的误判）
- 环境变量注入：通过 WebSocket URI 的 query param 传递（解决 Pod 复用时环境变量无法更新的问题）

**问题点：**
1. ❌ `PodReuseManager` 的复用策略不够灵活（只支持用户级别）
2. ❌ Pod 创建失败时的回退机制不完善
3. ❌ 缺少 Pod 资源配额管理

#### 2.4.2 LocalSandboxProvider

**职责：**
- 启动本地 Sidecar Server 进程（Node.js）
- 通过 Sidecar HTTP API 操作文件系统
- 通过 Sidecar WebSocket 桥接 CLI 进程

**关键代码路径：**
```
himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/LocalSandboxProvider.java:1-515
```

**核心机制：**
- Sidecar 进程管理：每个用户独立的 Sidecar 进程（不同端口）
- 进程复用：首次 `acquire()` 时启动，后续复用存活进程
- Node.js 路径解析：通过 `bash -lc "which node"` 兼容 nvm/fnm
- PATH 继承：通过 `bash -lc "echo $PATH"` 获取完整 PATH

**问题点：**
1. ❌ Sidecar 进程的生命周期管理不够完善（何时清理？）
2. ❌ 端口分配策略简单（`ServerSocket(0)`），可能冲突
3. ❌ 进程启动失败时的错误信息不够详细

### 2.5 配置生成器 (CliConfigGenerator)

**接口定义：**
```java
public interface CliConfigGenerator {
    String supportedProvider();
    Map<String, String> generateConfig(String workingDirectory, CustomModelConfig config);
    void generateMcpConfig(String workingDirectory, List<ResolvedMcpEntry> mcpServers);
    void generateSkillConfig(String workingDirectory, List<ResolvedSkillEntry> skills);
}
```

**实现类：**
- `ClaudeCodeConfigGenerator` - Claude Code CLI
- `QoderCliConfigGenerator` - Qoder CLI
- `QwenCodeConfigGenerator` - Qwen Code CLI
- `OpenCodeConfigGenerator` - Open Code CLI

**职责：**
- 根据 CLI 工具的配置格式生成配置文件
- 生成模型配置（API Key, Base URL, Model Name）
- 生成 MCP Server 配置（连接信息、认证 Skill 配置（文件内容）
- 返回需要注入的环境变量

**问题点：**
1. ❌ 配置文件格式硬编码在各个实现类中，难以维护
2. ❌ 文件写入逻辑重复（每个实现类都有类似的文件操作）
3. ❌ 缺少配置验证逻辑（如 API Key 格式、URL 有效性）
4. ❌ MCP 和 Skill 配置的生成逻辑分散

### 2.6 配置解析 (CliSessionConfig → ResolvedSessionConfig)

**配置对象层次：**
```
CliSessionConfig (前端传入，纯标识符)
    ├── modelProductId: String
    ├── mcpServers: List<McpServerEntry>
    │   └── productId, name
    └── skills: List<SkillEntry>
        └── productId, name

ResolvedSessionConfig (后端解析，完整信息)
    ├── customModelConfig: CustomModelConfig
    │   ├── apiKey, baseUrl, modelName
    │   └── provider, headers
    ├── mcpServers: List<ResolvedMcpEntry>
    │   ├── name, url, transportType
    │   └── headers (认证信息)
    └── skills: List<ResolvedSkillEntry>
        ├── name
        └── files: List<SkillFileContentResult>
```

**解析流程：**
1. 从 `CliSessionConfig` 提取 `modelProductId`
2. 查询数据库获取 Product 详情
3. 解析 Product 的 `modelConfig` JSON 字段
4. 构建 `CustomModelConfig`
5. 对 MCP 和 Skill 执行类似的解析流程

**问题点：**
1. ❌ 解析逻辑嵌入在 `AcpWebSocketHandler` 中，难以测试
2. ❌ 缺少统一的解析服务（ModelConfigResolver, McpConfigResolver）
3. ❌ 错误处理不够细粒度（解析失败时无法定位具体哪个配置项）
4. ❌ 缺少配置缓存机制（每次连接都重新查询数据库）

### 2.7 会话管理 (CodingSessionService)

**职责：**
- 创建、查询、更新、删除会话
- 持久化会话配置到数据库
- 管理会话的生命周期

**关键代码路径：**
```
himarket-server/src/main/java/com/alibaba/himarket/service/impl/CodingSessionServiceImpl.java:1-113
```

**数据模型：**
```java
CodingSession {
    sessionId: String
    userId: String
    sessionName: String
    sessionData: JSON  // 存储 CliSessionConfig
    createdAt: Timestamp
    updatedAt: Timestamp
}
```

**问题点：**
1. ❌ `sessionData` 使用 JSON 存储，缺少 schema 验证
2. ❌ 会话与 WebSocket 连接的关联不明确
3. ❌ 缺少会话状态管理（创建中、活跃、空闲、已关闭）
4. ❌ 缺少会话清理机制（长时间未使用的会话）

## 3. 配置传递链路分析

### 3.1 当前链路

```
前端 UI
    ↓ (HTTP POST /coding-sessions)
创 (CodingSessionService)
    ↓ (存储到数据库)gSession.sessionData (JSON)
    ↓ (前端读取)
前端 UI
    ↓ (WebSocket 连接，query param: cliSessionConfig)
AcpHandshakeInterceptor
    ↓ (URL 解码 + JSON 解析)
CliSessionConfig (attributes)
    ↓ (从 attributes 读取)
AcpWebSocketHandler
    ↓ (查询数据库 + 解析 JSON)
ResolvedSessionConfig
    ↓ (传递给 InitContext)
ConfigInjectionPhase
    ↓ (调用 CliConfigGenerator)
生成配置文件
    ↓ (写入沙箱文件系统)
CLI 工具读取配置
```

**问题点：**
1. ❌ 链路过长，经过 7+ 次转换
2. ❌ 配置在前端和后端之间传递了 3 次（创建 → 存储 → 读取 → 传递）
3. ❌ 每次连接都重新解析配置（无缓存）
4. ❌ 配置格式不统一（JSON → DTO → JSON → DTO）

### 3.2 优化后的链路（建议）

```
前端 UI
    ↓ (HTTP POST /coding-sessions)
创建会话 (CodingSessionService)
    ↓ (立即解析并缓存)
ResolvedSessionConfig (缓存)
    ↓ (WebSocket 连接，只传 sessionId)
AcpHandshakeInterceptor
    ↓ (从缓存读取)
ResolvedSessionConfig
    ↓ (传递给 InitContext)
ConfigInjectionPhase
    ↓ (调用 CliConfigGenerator)
生成配置文件
```

**优化点：**
- ✅ 减少配置传递次数（3 次 → 1 次）
- ✅ 引入配置缓存（避免重复解析）
- ✅ 简化 WebSocket 参数（只传 sessionId）
- ✅ 配置解析前置（创建会话时立即验证）

## 4. 主要问题总结

### 4.1 架构层面

1. **职责不清晰**
   - `AcpWebSocketHandler` 职责过重（800+ 行）
   - 配置解析逻辑分散在多个地方
   - 缺少清晰的分层架构

2. **配置管理混乱**
   - 多种配置对象（CliSessionConfig, ResolvedSessionConfig, SandboxConfig, RuntimeConfig）
   - 配置  - 缺少统一的配置验证和缓存机制

3. **错误处理不完善**
   - 错误信息不够详细，难以定位问题
   - 缺少细粒度的错误分类
   - 前端无法获取详细的初始化进度

### 4.2 代码层面

1. **代码重复**
   - `K8sSandboxProvider` 和 `LocalSandboxProvider` 有大量相似代码
   - 各个 `CliConfigGenerator` 实现有重复的文件操作逻辑
   - 配置解析逻辑在多处重复

2. **类型不安全**
   - `InitContext` 使用 `Map<String, Object>` 传递配置
   - `sessionData` 使用 JSON 存储，缺少 schema 验证
   - 配置对象之间的转换缺少类型检查

3. **测试覆盖不足**
   - 删除了多个测试文件（PodReuseManagerTest, AcpHandshakeInterceptorSandboxModePropertyTest）
   - 缺少端到端的集成测试
   - 缺少配置解析的单元测试

### 4.3 性能层面

1. **无配置缓存**
   - 每次 WebSocket 连接都重新查询数据库
   - 每次都重新解析 JSON 配置
   - MCP 和 Skill 的文件内容重复读取

2. **资源管理不足**
   Sidecar 进程的生命周期管理不完善
   - K8s Pod 的资源配额管理缺失
   - 缺少连接池和资源复用机制

## 5. 优化建议

### 5.1 短期优化（1-2 周）

#### 5.1.1 引入配置解析服务

创建独立的配置解析服务，统一处理配置转换：

```java
@Service
public class SessionConfigResolver {

    public ResolvedSessionConfig resolve(CliSessionConfig config) {
        // 统一的配置解析逻辑
    }

    public ResolvedSessionConfig resolveFromCache(String sessionId) {
        // 从缓存读取已解析的配置
    }
}
```

#### 5.1.2 简化 WebSocket 参数传递

只传递 `sessionId`，后端从缓存读取配置：

```
ws://localhost:8080/acp?token=xxx&sessionId=xxx
```

#### 5.1.3 拆分 AcpWebSocketHandler

将职责拆分为多个类：
- `AcpConnectionManager` - 连接管理
- `AcpMessageRouter` - 消息路由
- `AcpSessionInitializer` - 会话初始化

#### 5.1.4 统一错误处理

定义清晰的错误码和错误消息：

```java
public enum InitErrorCode {
    SANDBOX_ACQUIRE_FAILED("沙箱获取失败"),
    CONFIG_PARSE_FAILED("配置解析失败"),
    FILE_WRITE_FAILED("文件写入失败"),
    CLI_NOT_READY("CLI 工具未就绪");
}
```

### 5.2 中期优化（2-4 周）

#### 5.2.1 引入配置缓存

使用 Caffeine 或 Redis 缓存已解析的配置：

```java
@Cacheable(value = "resolvedConfigs", key = "#sessionId")
public ResolvedSessionConfig getResolvedConfig(String sessionId) {
    // ...
}
```

#### 5.2.2 抽象公共逻辑

创建 `AbstractSandboxProvider` 基类，提取公共逻辑：
- HTTP 客户端封装
- 文件操作封装
- 健康检查逻辑

#### 5.2.3 优化 InitContext

使用类型安全的配置传递：

```java
public class InitContext {
    private ResolvedSessionConfig sessionConfig;
    private SandboxInfo sandboxInfo;
    private RuntimeConfig runtimeConfig;
    // 移除 Map<String, Object>
}
```

#### 5.2.4 增强会话状态管理

引入会话状态机：

```java
public enum SessionState {
    CREATED,
    INITIALIZING,
    READY,
    ACTIVE,
    IDLE,
    CLOSED,
    ERROR
}
```

### 5.3 长期优化（1-2 月）

#### 5.3.1 重构配置生成器

擎（如 Freemarker）生成配置文件：

```java
@Service
public class TemplateBasedConfigGenerator {

    public String generateConfig(String template, Map<String, Object> data) {
        // 使用模板引擎生成配置
    }
}
```

#### 5.3.2 引入事件驱动架构

使用 Spring Events 解耦组件：

```java
@EventListener
public void onSandboxReady(SandboxReadyEvent event) {
    // 处理沙箱就绪事件
}
```

#### 5.3.3 完善监控和可观测性

- 添加详细的 metrics（沙箱创建时间、配置注入时间）
- 添加分布式追踪（OpenTelemetry）
- 添加详细的日志（结构化日志）

#### 5.3.4 增强测试覆盖

- 补充单元测试（配置解析、文件操作）
- 添加集成测试（端到端流程）
- 添加性能测试（并发连接、资源占用）

## 6. 优先级建议

### P0（必须立即优化）

1. ✅ 引入配置解析服务（SessionConfigResolver）
2. ✅ 简化 WebSocket 参数传递（只传 sessionId）
3. ✅ 统一错误处理（定义错误码）

### P1（近期优化）

1. 拆分 AcpWebSocketHandler（职责分离）
2. 引入配置缓存（避免重复解析）
3. 抽象 SandboxProvider 公共逻辑

### P2（中期优化）

1. 优化 InitContext（类型安全）
2. 增强会话状态管理（状态机）
3. 重构配置生成器（模板化）

### P3（长期优化）

1. 引入事件驱动架构
2. 完善监控和可观测性
3. 增强测试覆盖

## 7. 关键文件清单

### 核心流程
- `AcpHandshakeInterceptor.java` - WebSocket 握手
- `AcpWebSocketHandler.java` - WebSocket 处理（800+ 行，需拆分）
- `SandboxInitPipeline.java` - 沙箱初始化流水线

### 沙箱管理
- `SandboxProvider.java` - 沙箱提供者接口
- `K8sSandboxProvider.java` - K8s 沙箱实现
- `LocalSandboxProvider.ja
- `PodReuseManager.java` - Pod 复用管理

### 配置管理
- `CliSessionConfig.java` - 前端传入的配置
- `ResolvedSessionConfig.java` - 后端解析的配置
- `CliConfigGenerator.java` - 配置生成器接口
- `ClaudeCodeConfigGenerator.java` - Claude Code 配置生成
- `QwenCodeConfigGenerator.java` - Qwen Code 配置生成

### 会话管理
- `CodingSessionService.java` - 会话服务接口
- `CodingSessionServiceImpl.java` - 会话服务实现
- `CodingSession.java` - 会话实体

### 初始化阶段
- `SandboxAcquirePhase.java` - 沙箱获取阶段
- `FileSystemReadyPhase.java` - 文件系统检查阶段
- `ConfigInjectionPhase.java` - 配置注入阶段
- `CliReadyPhase.java` - CLI 就绪检查阶段
- `SidecarConnectPhase.java` - Sidecar 连接阶段

## 8. 总结

HiCoding 的架构整体设计合理，采用了流水线模式和策略模式，具有良好的扩展性。但在实现细节上存在以下主要问题：

1. **配置管理混乱** - 多种配置对象，传递链路过长
2. **职责不清晰** - 单个类职责过重，缺少分层
3. **代码重复** - 缺少抽象和复用
4. **错误处理不完善** - 错误信息不够详细
5. **测试覆盖不足** - 缺少端到端测试

建议优先优化配置管理和错误处理，然后逐步重构代码结构，最后完善测试和监控。
