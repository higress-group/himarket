# ACP 协议集成指南

本文档记录 HiMarket 与各类 ACP 兼容 CLI 工具的集成细节和差异点。

## 支持的 CLI 工具

| CLI 工具 | 命令 | 参数 | 版本 | 状态 |
|---------|------|------|------|------|
| Qoder CLI | `qodercli` | `--acp` | - | 需要付费 |
| Kiro CLI | `kiro-cli` | `acp` | 1.26.0 | 免费 |
| Qwen Code | `qwen` | `--acp` | 0.10.2 | 免费 |
| Claude Code | `npx` | `@zed-industries/claude-code-acp` | - | 未测试 |
| Codex | `npx` | `@zed-industries/codex-acp` | - | 未测试 |

## 通用 ACP 协议流程

```
initialize → session/new → session/prompt → session/update (流式响应)
```

### 标准错误码

| 错误码 | 含义 | 处理方式 |
|-------|------|---------|
| `-32000` | 认证错误 | 展示认证界面，引导用户完成认证 |
| `-32601` | 方法不存在 | 检查 ACP 协议版本兼容性 |

---

## CLI 差异详情

### 1. Qoder CLI

**基本信息**
- 命令：`qodercli --acp`
- 认证方式：`qodercli-login`（终端登录）
- 特点：需要付费使用

**ACP 行为**
- `initialize`：返回 `authMethods`，包含登录指引
- `session/new`：**直接返回 -32000 认证错误**（不创建 session）
- 错误格式：
  ```json
  {
    "error": {
      "code": -32000,
      "message": "Authentication required"
    }
  }
  ```

**注意事项**
- 未认证时无法创建 session，直接在 `session/new` 阶段返回错误
- 错误响应中**不包含** `authMethods`，需要从 `initialize` 响应中获取

---

### 2. Kiro CLI

**基本信息**
- 命令：`kiro-cli acp`
- 版本：1.26.0
- 认证方式：`kiro-login`（终端执行 `kiro-cli login`）

**ACP 行为**
- `initialize`：正常返回，包含 `authMethods`
- `session/new`：未认证时返回 sessionId，但后续操作需要认证
- 支持的模型：
  - `auto`（自动选择）
  - `claude-opus-4.6`（实验版）
  - `claude-opus-4.5`
  - `claude-sonnet-4.5`
  - `claude-sonnet-4`
  - `claude-haiku-4.5`

**特殊行为**
- **不支持 HOME 隔离**：Kiro CLI **无法**通过指定 HOME 环境变量来实现用户隔离
- 表现：当使用隔离的 HOME 目录启动时，`initialize` 完全无响应（已测试 2 分钟超时仍无输出）
- 原因：Kiro CLI 启动时强依赖用户主目录的配置文件，无法在隔离环境下正常工作
- 影响：多用户场景下无法通过 HOME 隔离实现用户间的会话和认证隔离

**自定义通知**
- 使用 `_kiro.dev/commands/available` 通知可用命令
- 使用 `_kiro.dev/metadata` 通知上下文使用情况

---

### 3. Qwen Code

**基本信息**
- 命令：`qwen --acp`
- 版本：0.10.2
- 认证方式：
  - `openai`：使用 OpenAI API Key（支持 DashScope 兼容模式）
  - `qwen-oauth`：阿里云 OAuth（免费每日额度）

**ACP 行为**
- `initialize`：返回详细的 `authMethods`、`modes`、`agentCapabilities`
- `session/new`：**直接返回 -32000 认证错误**，但错误中包含完整的 `authMethods`
- 支持的模型：
  - `coder-model(qwen-oauth)`：Qwen Coder 模型，上下文 1M tokens
  - `vision-model(qwen-oauth)`：Qwen Vision 模型，上下文 128K tokens
  - `qwen3-coder-plus(openai)`：使用 OpenAI 认证时的模型
- 支持的模式：
  - `plan`：仅分析，不修改文件
  - `default`：需要批准文件编辑和命令执行
  - `auto-edit`：自动批准文件编辑
  - `yolo`：自动批准所有操作

**错误响应格式**
```json
{
  "error": {
    "code": -32000,
    "message": "Authentication required",
    "data": {
      "details": "Use Qwen Code CLI to authenticate first.",
      "authMethods": [...]
    }
  }
}
```

**特点**
- 错误响应中**包含**完整的 `authMethods`，可以直接用于认证流程
- 支持 `available_commands_update` 会话更新，通知可用命令（如 `bug`、`compress`、`init`、`summary`）

**OpenAI 认证方式（通过命令行参数）**

Qwen Code 支持通过命令行参数直接传递 OpenAI 兼容的认证信息，无需在配置文件中预先设置：

```bash
qwen --acp \
  --auth-type openai \
  --openai-api-key <your-api-key> \
  --openai-base-url https://dashscope.aliyuncs.com/compatible-mode/v1
```

**DashScope OpenAI 兼容模式**

DashScope 提供 OpenAI 兼容的 API 接口，可以直接使用：

- Base URL: `https://dashscope.aliyuncs.com/compatible-mode/v1`
- API Key: DashScope API Key（格式：`sk-xxxx`）
- 支持流式响应（`stream: true`）

curl 示例：
```bash
curl -X POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-your-api-key" \
  -d '{
    "model": "qwen-max",
    "messages": [{"role": "user", "content": "你好"}],
    "stream": true
  }'
```

**隔离 HOME + OpenAI 认证流程测试**

在 Java 中使用隔离 HOME 目录 + OpenAI 认证启动 Qwen Code ACP：

```java
// 构建带认证参数的命令行参数
List<String> args = new ArrayList<>();
args.add("--acp");
args.add("--auth-type");
args.add("openai");
args.add("--openai-api-key");
args.add(apiKey);
args.add("--openai-base-url");
args.add("https://dashscope.aliyuncs.com/compatible-mode/v1");

// 使用隔离的 HOME 目录启动进程
Map<String, String> extraEnv = Map.of("HOME", isolatedHome.toString());
AcpProcess process = new AcpProcess("qwen", args, cwd.toString(), extraEnv);
```

**常见错误码**

| 错误码 | 含义 | 处理方式 |
|-------|------|---------|
| `-32000` | 认证错误 | 展示认证界面，引导用户选择 openai 或 qwen-oauth |
| `-32603` | 内部错误（含账户限制） | 检查 `data.details` 是否包含 "free tier exhausted"，提示用户升级 |

---

## 认证流程差异对比

| CLI | 认证时机 | 错误响应包含 authMethods | 隔离 HOME 支持 |
|-----|---------|------------------------|--------------|
| Qoder CLI | session/new | ❌ 否 | ✅ 支持 |
| Kiro CLI | session/prompt | ✅ 是 | ❌ 不支持 |
| Qwen Code | session/new | ✅ 是 | ✅ 支持 |

## 集成建议

### 1. 认证检测

所有 CLI 都使用 `-32000` 错误码表示认证错误，但触发时机不同：

```java
// 统一认证错误检测
if (error.code == -32000) {
    // 从 error.data.authMethods 或 initialize 响应获取认证方式
    List<AuthMethod> authMethods = extractAuthMethods(error);
    showAuthUI(authMethods);
}
```

### 2. 隔离 HOME 测试

测试未认证场景时，使用隔离的 HOME 目录：

```java
Map<String, String> extraEnv = Map.of("HOME", isolatedHome.toString());
AcpProcess process = new AcpProcess(command, args, cwd, extraEnv);
```

**注意**：Kiro CLI 不支持隔离 HOME，无法用于多用户隔离场景。

### 3. 参数格式

`session/prompt` 请求的 `prompt` 字段是 **ContentBlock 数组**，不是字符串：

```json
{
  "method": "session/prompt",
  "params": {
    "sessionId": "...",
    "prompt": [
      {"type": "text", "text": "用户输入"}
    ]
  }
}
```

## 测试覆盖

详见 `himarket-server/src/test/java/com/alibaba/himarket/service/acp/`：

- `AcpProcessMultiCliTest`：initialize 握手测试
- `AcpSessionModelsTest`：session/new models/modes 测试
- `AcpPromptExecutionTest`：完整 prompt 执行测试
- `AcpAuthenticationTest`：认证流程测试（隔离 HOME）
- `QwenCodeAuthFlowTest`：Qwen Code 完整认证流程测试（隔离 HOME + OpenAI 认证）

### QwenCodeAuthFlowTest 详情

测试 Qwen Code 在隔离 HOME 环境下，通过 OpenAI 兼容模式认证的完整 ACP 流程：

1. `testQwenCodeWithOpenAIAuth`：
   - 需要设置环境变量 `DASHSCOPE_API_KEY`
   - 验证 initialize → session/new → session/prompt 完整流程
   - 处理账户额度限制错误（-32603 + "free tier exhausted"）

2. `testQwenCodeWithoutAuth`：
   - 验证未认证时返回 -32000 错误
   - 验证错误响应中包含 authMethods

运行测试：
```bash
DASHSCOPE_API_KEY=sk-xxx mvn test -Dtest=QwenCodeAuthFlowTest -pl himarket-server
```

## 常见问题

### Q: 为什么 Kiro CLI 不支持 HOME 隔离？
A: Kiro CLI 强依赖用户主目录的配置文件和缓存，无法通过指定 HOME 环境变量来实现用户隔离。如需多用户隔离，请选择 Qoder CLI 或 Qwen Code。

### Q: 如何处理不同的认证时机？
A: 统一在收到 `-32000` 错误时触发认证流程，无论从 `session/new` 还是 `session/prompt` 返回。

### Q: 如何获取 authMethods？
A: 优先从错误响应的 `error.data.authMethods` 获取，如果不存在则从 `initialize` 响应获取。
