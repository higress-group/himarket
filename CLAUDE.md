**ALWAYS RESPOND IN CHINESE-SIMPLIFIED**

## 1. 项目概览

HiMarket 是一个 AI 开放平台，基于 Java 17 + Spring Boot 3.2.11 构建。采用 Maven 多模块分层架构，提供 API 产品管理、开发者门户、AI 对话、云 IDE（HiCoding）等功能。

### 模块依赖

```
himarket-dal (数据层)  ←  himarket-server (业务层)  ←  himarket-bootstrap (启动配置层)
```

| 模块 | 职责 | 关键包 |
|------|------|--------|
| `himarket-dal` | 实体、Repository、枚举、转换器 | `entity/`, `repository/`, `support/` |
| `himarket-server` | Controller、Service、DTO、核心基础设施 | `controller/`, `service/`, `dto/`, `core/` |
| `himarket-bootstrap` | Spring Boot 入口、安全/WS/Swagger/Flyway 配置 | `config/`, `filter/` |

### 请求处理流

```
HTTP → SecurityFilterChain → JwtAuthenticationFilter → Controller (@XxxAuth)
    → Service → ServiceImpl → Repository → MariaDB/MySQL
    → ResponseAdvice (统一包装) → Client
```

## 2. 代码导航

### 核心基础设施 (`himarket-server/…/core/`)

- **认证注解**: `annotation/` — `@AdminAuth`, `@DeveloperAuth`, `@AdminOrDeveloperAuth`, `@PublicAccess`
- **安全**: `security/` — `JwtAuthenticationFilter`, `ContextHolder`, `PublicAccessPathScanner`
- **异常**: `exception/` — `BusinessException`, `ErrorCode` 枚举
- **响应**: `response/Response`, `advice/ResponseAdvice`, `advice/ExceptionAdvice`
- **事件**: `event/` — `ProductDeletingEvent`, `PortalDeletingEvent` 等 6 个 Domain Event
- **工具**: `utils/` — `TokenUtil`, `PasswordHasher`, `IdGenerator`, `CacheUtil`

### 主要业务模块

| 业务域 | Controller | Service | Entity |
|--------|-----------|---------|--------|
| 管理员 | `AdministratorController` `/admins` | `AdministratorService` | `Administrator` |
| 开发者 | `DeveloperController` `/developers` | `DeveloperService` | `Developer` |
| 产品 | `ProductController` `/products` | `ProductService` | `Product`, `ProductPublication` |
| 消费者 | `ConsumerController` `/consumers` | `ConsumerService` | `Consumer`, `ConsumerCredential` |
| 门户 | `PortalController` `/portals` | `PortalService` | `Portal`, `PortalDomain` |
| 网关 | `GatewayController` `/gateways` | `GatewayService` | `Gateway` |
| Nacos | `NacosController` `/nacos` | `NacosService` | `NacosInstance` |
| AI 对话 | `ChatController` `/chats` (SSE) | `service/hichat/` | `Chat`, `ChatSession` |
| 云 IDE | `CodingSessionController` | `service/hicoding/` | `CodingSession` |

### WebSocket 端点

- `/ws/acp` — HiCoding 编程助手（`HiCodingWebSocketHandler`）
- `/ws/terminal` — 远程终端（`TerminalWebSocketHandler`）

## 3. 本地开发环境

### 数据库访问

数据库连接信息通过以下方式提供（优先级从高到低）：
- shell 环境变量（直接 export 或写入 `~/.zshrc` / `~/.bashrc`）
- `~/.env` 文件（`scripts/run.sh` 启动时会自动 source）

需要包含以下变量：`DB_HOST`, `DB_PORT`(3306), `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`

```bash
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -e "YOUR_SQL_HERE"
```

注意事项：
- 只执行 SELECT 查询，除非用户明确要求修改数据
- 不要在回复中展示完整的密码、密钥等敏感字段
- 数据库 schema 由 Flyway 管理，迁移文件在 `himarket-bootstrap/src/main/resources/db/migration/`

### 构建、测试与代码检查

```bash
# 编译（跳过测试和格式检查，用于快速验证编译）
./mvnw -pl himarket-bootstrap -am package -DskipTests -Dspotless.check.skip=true -q

# 运行单元测试
./mvnw test

# 代码格式检查（Spotless + Google Java Format AOSP）
./mvnw spotless:check

# 代码格式自动修复
./mvnw spotless:apply

# 完整构建（编译 + 格式检查 + 测试）
./mvnw clean verify
```

### 启动后端服务

```bash
./scripts/run.sh
```

脚本自动完成：加载 `~/.env` → 优雅关闭旧进程 → 编译打包 → 后台启动 jar → 轮询等待就绪。
退出码为 0 表示启动成功，非 0 表示失败。

### 应用日志

本地运行时日志文件位于 `~/himarket.log`。排查后端问题时应主动读取该日志。

## 4. API 接口测试

后端运行在 `http://localhost:8080`，接口路径不带 `/portal` 前缀。使用 JWT Bearer Token 认证。

接口返回格式为 `{"code":"SUCCESS","data":{...}}`，token 在 `data.access_token` 中。

```bash
# 管理员 Token
TOKEN=$(curl -s -X POST http://localhost:8080/admins/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.data.access_token')

# 开发者 Token
TOKEN=$(curl -s -X POST http://localhost:8080/developers/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"123456"}' | jq -r '.data.access_token')

# 带认证请求
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/your-endpoint | jq .

# WebSocket 验证
websocat -H "Authorization: Bearer $TOKEN" ws://localhost:8080/your-ws-endpoint
```

认证注解：`@AdminAuth`(管理员), `@DeveloperAuth`(开发者), `@AdminOrDeveloperAuth`(两者皆可), 无注解(无需认证)。Token 有效期 7 天。Swagger: `http://localhost:8080/portal/swagger-ui.html`

### 修改代码后的验证

以下场景建议主动进行"重启 → 接口验证"闭环：
- 用户明确要求调试 bug 或修复接口
- 新增或修改了 REST/WebSocket 接口
- 用户要求端到端验证
- 完成 spec 任务的代码开发后

验证流程：`./scripts/run.sh` → curl 调用接口 → mysql 确认数据 → 失败时读 `~/himarket.log`

## 5. 创建 Pull Request

创建 PR 前先检查 `.github/PULL_REQUEST_TEMPLATE.md` 是否存在，如存在则按模板格式填写 PR body。

## 6. 详细文档索引

| 文档 | 路径 | 内容 |
|------|------|------|
| 系统架构 | `docs/ARCHITECTURE.md` | 模块设计、层级图、关键流程、数据模型 |
| 贡献指南 | `CONTRIBUTING.md` / `CONTRIBUTING_zh.md` | Fork 工作流、提交规范、PR 要求 |
| 用户指南 | `USER_GUIDE.md` / `USER_GUIDE_zh.md` | 产品使用文档 |
| DB 迁移 | `himarket-bootstrap/src/main/resources/db/migration/` | Flyway SQL 迁移（V1-V15） |
| 部署配置 | `deploy/` | Docker Compose、Helm Chart |
| ACP 协议 | `docs/acp/` | Agent Client Protocol 参考文档 |
| Nacos 源码索引 | `docs/NACOS_SOURCE_INDEX.md` | Nacos 模块架构、HiMarket 集成点映射、API 快速查找 |

### Nacos 源码参考

根目录下的 `nacos/` 是 Nacos 源码的符号链接（不提交 git），用于开发时参考。HiMarket 通过 `nacos-maintainer-client` 与 Nacos 交互，主要集成点：

- **Skill 管理**: `NacosServiceImpl` / `SkillServiceImpl` → `SkillMaintainerService` → Nacos `ai/service/skills/`
- **Worker 管理**: `WorkerServiceImpl` → `AgentSpecMaintainerService` → Nacos `ai/service/agentspecs/`
- **MCP 服务器**: `NacosServiceImpl` → `McpMaintainerService` → Nacos `ai/service/`
- **命名空间**: `NacosServiceImpl` → `NamingMaintainerService` → Nacos `core/namespace/`

详细映射见 `docs/NACOS_SOURCE_INDEX.md`。

## Active Technologies
- Java 17 + TypeScript (React) + Spring Boot 3.2.11, Spring Data JPA, MariaDB/MySQL, React + Vite (001-developer-personal-skill)
- MariaDB（Product 表扩展）+ Nacos（Skill 文件存储，现有机制复用） (001-developer-personal-skill)

## Recent Changes
- 001-developer-personal-skill: Added Java 17 + TypeScript (React) + Spring Boot 3.2.11, Spring Data JPA, MariaDB/MySQL, React + Vite
