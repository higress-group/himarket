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

#### 判断是否需要重启

修改 Java 源文件、Spring 配置（`application.yml`）、`pom.xml`、Flyway 迁移文件后需要重启。修改前端、文档、脚本等不需要。

#### 验证流程

1. `./scripts/run.sh` 重启，确认退出码为 0
2. 用 curl 调用相关接口，检查返回结果
3. 如果涉及数据变更，用 mysql CLI 查询确认
4. 验证失败时读取 `~/himarket.log` 排查，修复后重试

#### Spec 任务的端到端验证（建议）

在 spec 任务的代码实现完成后，请评估该任务是否适合通过 curl、websocat、mysql CLI 等工具进行端到端验证。如果判断可以验证（例如涉及 REST 接口、WebSocket 接口、数据持久化等），应在完成代码后主动执行验证闭环：

1. `./scripts/run.sh` 重启后端，确认退出码为 0
2. 用 curl / websocat 验证相关接口的实际行为（覆盖正常和异常路径）
3. 如涉及数据变更，用 mysql CLI 查询确认
4. 验证失败时读取 `~/himarket.log` 排查，修复后重试

如果任务不涉及可直接调用的接口（如纯重构、配置变更、前端改动等），可跳过端到端验证。

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
