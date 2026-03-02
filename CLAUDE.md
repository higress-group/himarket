# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在处理本仓库代码时提供指导。

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
1. Shell 环境变量
2. `~/.env` 文件（`scripts/run.sh` 自动加载）

必需的环境变量：
```bash
DB_HOST=your_db_host
DB_PORT=3306
DB_NAME=himarket
DB_USERNAME=your_username
DB_PASSWORD=your_password
```

### API 认证

所有 API 端点都需要 JWT Bearer Token 认证（登录/注册端点除外）。

**获取管理员 Token：**
```bash
curl -X POST http://localhost:8080/admins/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
```

**获取开发者 Token：**
```bash
curl -X POST http://localhost:8080/developers/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"123456"}'
```

**使用 Token：**
```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/your-endpoint
```

### 认证注解

控制器方法使用注解来强制认证：
- `@AdminAuth` - 需要管理员 token
- `@DeveloperAuth` - 需要开发者 token
- `@AdminOrDeveloperAuth` - 接受任意一种 token
- 无注解 - 公开端点

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

## 测试

**后端：**
- JUnit 5 用于单元测试
- jqwik 用于基于属性的测试
- 集成测试标记为 `@Tag("integration")`（默认排除）
- 测试需要 JVM 参数：`--add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED`

**前端：**
- Vitest 用于单元测试（开发者门户）

## 部署选项

1. **本地：** `./scripts/run.sh`
2. **Docker Compose：** `deploy/docker/scripts/deploy.sh install`
3. **Helm（K8s）：** `deploy/helm/scripts/deploy.sh install`
4. **阿里云计算巢：** 一键部署

## 其他文档

- 前端特定指南：`himarket-web/himarket-frontend/CLAUDE.md`
- 用户指南：`USER_GUIDE.md`
- Docker 部署：`deploy/docker/Docker部署脚本说明.md`
- Helm 部署：`deploy/helm/Helm部署脚本说明.md`
