# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在处理本仓库代码时提供指导。

**ALWAYS RESPOND IN CHINESE-SIMPLIFIED**

## 项目概述

HiMarket 是基于 Higress AI 网关构建的企业级 AI 开放平台，帮助企业构建私有 AI 能力市场，管理和分发 AI 资源（LLM、MCP Server、Agent、Agent Skill）。

**仓库结构：**
- `himarket-bootstrap/` - Spring Boot 应用入口和配置
- `himarket-server/` - 业务逻辑、REST 控制器、服务
- `himarket-dal/` - 数据访问层（JPA 实体、仓储）
- `himarket-web/himarket-admin/` - 管理门户前端（React + Vite）
- `himarket-web/himarket-frontend/` - 开发者门户前端（React + TypeScript）
- `deploy/` - Docker Compose 和 Helm 部署配置

## 开发命令

### 后端（Java 17 + Maven）

```bash
# 构建所有模块，跳过测试
mvn clean package -DskipTests

# 运行测试（默认排除集成测试）
mvn test

# 运行所有测试，包括集成测试
mvn test -Pintegration

# 检查代码格式（Spotless + Google Java Format）
mvn spotless:check

# 应用代码格式化
mvn spotless:apply

# 本地启动后端（加载 ~/.env，杀掉 8080 端口旧进程，编译，启动 Spring Boot）
./scripts/run.sh
```

**后端运行地址：** `http://localhost:8080`
**Swagger UI：** `http://localhost:8080/portal/swagger-ui.html`
**日志文件：** `~/himarket.log`

### 前端 - 管理门户（`himarket-web/himarket-admin/`）

```bash
npm run dev      # 开发服务器（端口 5174）
npm run build    # 生产环境构建
npm run lint     # ESLint 检查
npm run serve    # 构建并启动生产服务器
```

### 前端 - 开发者门户（`himarket-web/himarket-frontend/`）

```bash
npm run dev         # 开发服务器（端口 5173）
npm run build       # 类型检查和生产环境构建
npm run lint        # ESLint 检查
npm run type-check  # 仅进行 TypeScript 类型检查
npm run test        # 运行 Vitest 测试
npm run preview     # 预览生产环境构建
```

## 本地开发环境设置

### 数据库配置

数据库连接可通过以下方式配置（优先级从高到低）：
1. Shell 环境变量（直接 export 或写入 `~/.zshrc` / `~/.bashrc`）
2. `~/.env` 文件（`scripts/run.sh` 自动加载）

必需的环境变量：
```bash
DB_HOST=your_db_host
DB_PORT=3306
DB_NAME=himarket
DB_USERNAME=your_username
DB_PASSWORD=your_password
```

**查询数据库：**
```bash
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -e "YOUR_SQL_HERE"
```

注意事项：
- 只执行 SELECT 查询，除非用户明确要求修改数据
- 不要在回复中展示完整的密码、密钥等敏感字段
- 数据库 schema 由 Flyway 管理，迁移文件在 `himarket-bootstrap/src/main/resources/db/migration/`

### API 认证

所有 API 端点都需要 JWT Bearer Token 认证（登录/注册端点除外）。

**接口返回格式：**
```json
{"code":"SUCCESS","data":{...}}
```
Token 在 `data.access_token` 中，有效期为 7 天。

**获取管理员 Token：**
```bash
# 基础方式
curl -X POST http://localhost:8080/admins/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

# 自动提取 token（推荐）
TOKEN=$(curl -s -X POST http://localhost:8080/admins/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.data.access_token')
```

**获取开发者 Token：**
```bash
# 基础方式
curl -X POST http://localhost:8080/developers/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"123456"}'

# 自动提取 token（推荐）
TOKEN=$(curl -s -X POST http://localhost:8080/developers/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"123456"}' | jq -r '.data.access_token')
```

**使用 Token：**
```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/your-endpoint | jq .
```

**WebSocket 接口验证：**
```bash
websocat -H "Authorization: Bearer $TOKEN" ws://localhost:8080/your-ws-endpoint
```

### 认证注解

控制器方法使用注解来强制认证：
- `@AdminAuth` - 需要管理员 token
- `@DeveloperAuth` - 需要开发者 token
- `@AdminOrDeveloperAuth` - 接受任意一种 token
- 无注解 - 公开端点

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

**判断是否需要重启：**
修改 Java 源文件、Spring 配置（`application.yml`）、`pom.xml`、Flyway 迁移文件后需要重启。修改前端、文档、脚本等不需要。

**验证流程：**
1. `./scripts/run.sh` 重启，确认退出码为 0
2. 用 curl 调用相关接口，检查返回结果
3. 如果涉及数据变更，用 mysql CLI 查询确认
4. 验证失败时读取 `~/himarket.log` 排查，修复后重试

**Spec 任务的端到端验证（建议）：**
在 spec 任务的代码实现完成后，请评估该任务是否适合通过 curl、websocat、mysql CLI 等工具进行端到端验证。如果判断可以验证（例如涉及 REST 接口、WebSocket 接口、数据持久化等），应在完成代码后主动执行验证闭环：

1. `./scripts/run.sh` 重启后端，确认退出码为 0
2. 用 curl / websocat 验证相关接口的实际行为（覆盖正常和异常路径）
3. 如涉及数据变更，用 mysql CLI 查询确认
4. 验证失败时读取 `~/himarket.log` 排查，修复后重试

如果任务不涉及可直接调用的接口（如纯重构、配置变更、前端改动等），可跳过端到端验证。

### 应用日志

本地运行时日志文件位于 `~/himarket.log`。排查后端问题时应主动读取该日志。

## 高层架构

### 模块依赖

```
himarket-bootstrap (入口点)
    ├── himarket-server (业务逻辑)
    │       └── himarket-dal (数据访问)
    └── himarket-dal
```

### 核心领域实体

**产品管理：**
- `Product` - 代表 API 产品的核心实体（REST_API、MCP_SERVER、AGENT_API、MODEL_API）
- `ProductPublication` - 产品的已发布版本及路由配置
- `ProductSubscription` - 开发者对产品的订阅
- `ProductCategory` / `ProductCategoryRelation` - 产品分类

**身份与访问：**
- `Administrator` - 门户管理的管理员用户
- `Developer` - 订阅 API 的开发者用户
- `DeveloperExternalIdentity` - OIDC/OAuth 关联身份
- `Consumer` / `ConsumerCredential` - 应用及其 API 凭证

**基础设施：**
- `Portal` / `PortalDomain` - 门户配置和自定义域名
- `Gateway` - 网关配置（Higress、APIG 等）
- `NacosInstance` - Nacos 服务发现配置
- `K8sCluster` - Kubernetes 集群配置

**对话式 AI：**
- `ChatSession` / `Chat` / `ChatAttachment` - AI 聊天会话和消息

### 关键服务模式

**网关集成（`service/gateway/`）：**
- `AIGWOperator` - AI 网关操作的抽象接口
- `client/GatewayClient` - 网关客户端实现
- 不同网关实现：Higress、阿里云 APIG、MSE

**事件驱动清理：**
- `DeveloperDeletingEvent` / `PortalDeletingEvent` / `ProductDeletingEvent`
- 事件监听器处理级联删除（凭证、订阅等）

### 产品类型

产品有一个 `type` 字段，类型特定的配置以 JSON 格式存储：

1. **REST_API** - 传统 REST API
   - 配置：`apiConfig.spec`（OpenAPI/Swagger 规范）

2. **MCP_SERVER** - Model Context Protocol 服务器
   - 配置：`mcpConfig`，包含服务器名称、域名、工具

3. **AGENT_API** - AI 智能体 API
   - 配置：`agentConfig.agentAPIConfig`，包含路由和协议

4. **MODEL_API** - AI 模型 API
   - 配置：`modelConfig.modelAPIConfig`，包含模型类别和路由

### 数据库迁移

Flyway 管理 `himarket-bootstrap/src/main/resources/db/migration/` 中的模式迁移：
- `V1__init.sql` - 初始模式
- `V2__*.sql` - 后续迁移

## 代码风格

**Java：**
- Google Java Format（AOSP 风格）通过 Spotless
- 自动导入排序和删除未使用的导入
- 在 `mvn compile` 时自动运行

**前端：**
- ESLint 配合 React hooks 和 refresh 插件
- TypeScript 严格模式
- Prettier 格式化

## 其他文档

- 用户指南：`USER_GUIDE.md`
