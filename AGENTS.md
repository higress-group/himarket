**ALWAYS RESPOND IN CHINESE-SIMPLIFIED**

## 本地开发环境

### 数据库访问

本地开发时，数据库连接信息可以通过以下任意方式提供（优先级从高到低）：
- shell 环境变量（直接 export 或写入 `~/.zshrc` / `~/.bashrc`）
- `~/.env` 文件（`scripts/run.sh` 启动时会自动 source）

需要包含以下变量：
- `DB_HOST`：数据库地址
- `DB_PORT`：端口（默认 3306）
- `DB_NAME`：数据库名
- `DB_USERNAME`：用户名
- `DB_PASSWORD`：密码

查询数据库时，使用 mysql CLI（环境变量已在 shell 中或通过 `~/.env` 加载）：

```bash
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -e "YOUR_SQL_HERE"
```

注意事项：
- 只执行 SELECT 查询，除非用户明确要求修改数据
- 不要在回复中展示完整的密码、密钥等敏感字段
- 数据库 schema 由 Flyway 管理，迁移文件在 `himarket-bootstrap/src/main/resources/db/migration/`

### 启动后端服务

使用 `scripts/run.sh` 脚本编译并启动 Java 后端：

```bash
./scripts/run.sh
```

脚本会自动完成：加载环境变量 → 优雅关闭旧进程 → 编译打包 → 后台启动 jar → 轮询等待就绪。
脚本退出码为 0 表示启动成功，非 0 表示失败（编译错误或启动超时）。

### 修改代码后的验证

以下场景建议主动进行"重启 → 接口验证"闭环，而不是只改代码就结束：
- 用户明确要求调试某个 bug 或修复接口问题
- 新增或修改了 REST/WebSocket 接口
- 用户要求端到端验证
- 完成 spec 任务的代码开发后，进行端到端功能验证

#### 验证流程

1. `./scripts/run.sh` 重启，确认退出码为 0
2. 用 curl 调用相关接口，检查返回结果
3. 如果涉及数据变更，用 mysql CLI 查询确认
4. 验证失败时读取 `~/himarket.log` 排查，修复后重试

### API 接口测试

后端运行在 `http://localhost:8080`，接口路径不带 `/portal` 前缀。使用 JWT Bearer Token 认证。

接口返回格式为 `{"code":"SUCCESS","data":{...}}`，token 在 `data.access_token` 中。

#### 获取管理员 Token（后台管理）

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/admins/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.data.access_token')
```

#### 获取开发者 Token（前台门户）

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/developers/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"123456"}' | jq -r '.data.access_token')
```

#### 带认证请求示例

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/your-endpoint | jq .
```

#### WebSocket 接口验证

对于 WebSocket 接口，使用 `websocat` 工具：

```bash
websocat -H "Authorization: Bearer $TOKEN" ws://localhost:8080/your-ws-endpoint
```

#### 认证注解说明

接口上的注解决定了需要哪种角色的 token：
- `@AdminAuth`：需要管理员 token
- `@DeveloperAuth`：需要开发者 token
- `@AdminOrDeveloperAuth`：两种都可以
- 无注解：无需认证

Token 有效期为 7 天。Swagger 文档：`http://localhost:8080/portal/swagger-ui.html`

### 应用日志

本地运行时日志文件位于 `~/himarket.log`。排查后端问题时应主动读取该日志。

## OpenSandbox 集成

HiMarket 集成了阿里巴巴开源的 OpenSandbox 项目，用于提供安全的代码执行沙箱环境。

### 项目位置

OpenSandbox 仓库位于 `OpenSandbox/` 目录（本地 clone，不提交到 git）。

**首次设置：**
```bash
cd /Users/xujingfeng/IdeaProjects/himarket
git clone https://github.com/alibaba/OpenSandbox.git
```

该目录已在 `.gitignore` 中配置，不会被提交到版本控制，但 AI Agent 可以正常访问和探索其中的源码和文档。

### 渐进性探索指南

当需要对接或调试 OpenSandbox 相关功能时，按以下顺序探索：

1. **快速了解**：阅读 `OpenSandbox/README.md` 了解项目概述、核心功能和基本用法
2. **开发指导**：
   - `OpenSandbox/CLAUDE.md` - Claude Code 的开发指导（中文）
   - `OpenSandbox/AGENTS.md` - AI Agent 的仓库指南
3. **架构文档**：`OpenSandbox/docs/architecture.md` - 整体架构和设计理念
4. **关键目录**：
   - `OpenSandbox/server/` - Python FastAPI 沙箱生命周期管理服务
   - `OpenSandbox/sdks/` - 多语言 SDK（Python、Java/Kotlin、TypeScript、C#）
   - `OpenSandbox/components/execd/` - Go 执行守护进程
   - `OpenSandbox/examples/` - 集成示例（包括 claude-code、kimi-cli 等）
   - `OpenSandbox/specs/` - OpenAPI 规范文档
   - `OpenSandbox/kubernetes/` - Kubernetes 部署和 Operator

### 何时探索 OpenSandbox

仅在以下场景需要深入探索 OpenSandbox 源码和文档：
- 实现或调试沙箱创建、生命周期管理功能
- 集成代码执行、命令执行、文件操作等沙箱能力
- 排查沙箱相关的错误或性能问题
- 扩展或定制沙箱运行时行为
- 对接 OpenSandbox 的 API 或 SDK

对于其他 HiMarket 功能开发，无需关注 OpenSandbox 目录。

## Nacos 集成

HiMarket 使用阿里巴巴开源的 Nacos 作为服务发现和配置管理基础设施。本地通过符号链接引入了 Nacos 源码仓库，方便 AI Agent 理解 Nacos 内部实现。

### 项目位置

Nacos 源码位于 `nacos/` 目录（本地符号链接，指向 `/Users/xujingfeng/AIProjects/nacos`，不提交到 git）。

**首次设置：**
```bash
cd /Users/xujingfeng/IdeaProjects/himarket
ln -s /Users/xujingfeng/AIProjects/nacos nacos
```

该目录已在 `.gitignore` 中配置，不会被提交到版本控制，但 AI Agent 可以正常访问和探索其中的源码和文档。

### 渐进性探索指南

当需要对接或调试 Nacos 相关功能时，按以下顺序探索：

1. **快速了解**：阅读 `nacos/README.md` 了解项目概述（动态服务发现、配置管理、DNS 服务）
2. **架构文档**：`nacos/doc/` 目录下的设计文档
3. **关键模块**：
   - `nacos/api/` - Nacos 公共 API 定义（SPI 接口、模型类）
   - `nacos/client/` - Java 客户端 SDK（服务注册/发现、配置监听）
   - `nacos/naming/` - 服务注册与发现核心实现
   - `nacos/config/` - 配置管理核心实现
   - `nacos/server/` - Nacos Server 启动入口
   - `nacos/console/` - 管理控制台后端
   - `nacos/console-ui/` - 管理控制台前端
   - `nacos/core/` - 核心通用模块（集群、鉴权、分布式协议）
   - `nacos/consistency/` - 一致性协议（Raft/Distro）
   - `nacos/auth/` - 认证鉴权模块
   - `nacos/plugin/` - 插件体系（鉴权、配置加密、数据源等）
   - `nacos/persistence/` - 持久化层
   - `nacos/distribution/` - 打包和发布配置
4. **高级主题**（特定场景）：
   - `nacos/mcp-registry-adaptor/` - MCP 注册适配器
   - `nacos/istio/` - Istio 集成（MCP/xDS 协议）
   - `nacos/k8s-sync/` - Kubernetes 服务同步
   - `nacos/ai/` - AI 相关能力
   - `nacos/skills/` - Skill 市场能力

### 何时探索 Nacos

仅在以下场景需要深入探索 Nacos 源码和文档：
- 实现或调试 HiMarket 与 Nacos 的服务注册/发现集成
- 对接 Nacos 配置管理能力（动态配置推送、监听）
- 排查 Nacos 客户端连接、心跳、同步等问题
- 理解 Nacos 的一致性协议（Raft/Distro）实现细节
- 扩展 Nacos 插件（鉴权、数据源、配置加密等）
- 对接 Nacos 的 Open API 或使用其 Java SDK

对于其他 HiMarket 功能开发，无需关注 Nacos 目录。

## 创建 Pull Request

创建 PR 前先检查 `.github/PULL_REQUEST_TEMPLATE.md` 是否存在，如存在则按模板格式填写 PR body。
