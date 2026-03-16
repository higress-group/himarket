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

## OpenSandbox 集成

HiMarket 集成了阿里巴巴开源的 OpenSandbox 项目，为 AI 应用提供安全的沙箱执行环境。

### 项目位置

OpenSandbox 仓库位于 `OpenSandbox/` 目录（本地 clone，不提交到 git）。

**首次设置：**
```bash
cd /Users/xujingfeng/IdeaProjects/himarket
git clone https://github.com/alibaba/OpenSandbox.git
```

该目录已在 `.gitignore` 中配置，不会被提交到版本控制，但 Claude Code 可以正常访问和探索其中的源码和文档。

### 渐进性探索指南

**仅在需要对接或调试 OpenSandbox 功能时才探索此目录。** 按以下顺序渐进式学习：

#### 第一层：快速了解
- `OpenSandbox/README.md` - 项目概述、核心功能、快速开始

#### 第二层：开发指导
- `OpenSandbox/CLAUDE.md` - Claude Code 的详细开发指导（中文）
- `OpenSandbox/AGENTS.md` - AI Agent 的仓库指南
- `OpenSandbox/docs/architecture.md` - 整体架构和设计理念

#### 第三层：核心组件（按需探索）
- **Server**：`OpenSandbox/server/` - Python FastAPI 沙箱生命周期管理服务
  - 配置：`~/.sandbox.toml`（从 `server/example.config.toml` 复制）
  - 启动：`opensandbox-server` 或 `cd server && uv run python -m src.main`
- **SDKs**：`OpenSandbox/sdks/` - 多语言客户端库
  - `sdks/sandbox/` - 基础沙箱 SDK（生命周期、命令、文件）
  - `sdks/code-interpreter/` - 代码解释器 SDK
  - 支持语言：Python、Java/Kotlin、JavaScript/TypeScript、C#/.NET
- **execd**：`OpenSandbox/components/execd/` - Go 执行守护进程
  - 注入到沙箱容器中，提供代码执行、命令和文件操作
- **Examples**：`OpenSandbox/examples/` - 集成示例
  - `examples/claude-code/` - Claude Code 集成示例
  - `examples/code-interpreter/` - 代码解释器示例
  - `examples/kimi-cli/`、`examples/gemini-cli/` 等 - 其他 AI CLI 集成

#### 第四层：高级主题（特定场景）
- **Kubernetes**：`OpenSandbox/kubernetes/` - K8s 部署和自定义 Operator
- **Specs**：`OpenSandbox/specs/` - OpenAPI 规范（沙箱生命周期 API、执行 API）
- **Components**：`OpenSandbox/components/` - Ingress 网关、Egress 控制
- **OSEPs**：`OpenSandbox/oseps/` - 架构提案和设计文档

### 何时探索 OpenSandbox

仅在以下场景需要深入探索：
- 实现沙箱创建、启动、停止等生命周期管理
- 集成代码执行、命令执行、文件操作等沙箱能力
- 调试沙箱相关的错误或性能问题
- 扩展或定制沙箱运行时行为
- 对接 OpenSandbox 的 REST API 或使用其 SDK

**对于其他 HiMarket 功能开发（产品管理、用户认证、网关配置等），无需关注 OpenSandbox 目录。**

## Nacos 集成

HiMarket 使用阿里巴巴开源的 Nacos 作为服务发现和配置管理基础设施。本地通过符号链接引入了 Nacos 源码仓库，方便理解 Nacos 内部实现。

### 项目位置

Nacos 源码位于 `nacos/` 目录（本地符号链接，指向 `/Users/xujingfeng/AIProjects/nacos`，不提交到 git）。

**首次设置：**
```bash
cd /Users/xujingfeng/IdeaProjects/himarket
ln -s /Users/xujingfeng/AIProjects/nacos nacos
```

该目录已在 `.gitignore` 中配置，不会被提交到版本控制，但 Claude Code 可以正常访问和探索其中的源码和文档。

### 渐进性探索指南

**仅在需要对接或调试 Nacos 功能时才探索此目录。** 按以下顺序渐进式学习：

#### 第一层：快速了解
- `nacos/README.md` - 项目概述、核心功能（动态服务发现、配置管理、DNS 服务）

#### 第二层：架构与设计
- `nacos/doc/` - 设计文档和架构说明

#### 第三层：核心模块（按需探索）
- **API**：`nacos/api/` - 公共 API 定义（SPI 接口、模型类、异常定义）
- **Client**：`nacos/client/` - Java 客户端 SDK
  - 服务注册/发现、配置监听、长连接管理
- **Naming**：`nacos/naming/` - 服务注册与发现核心实现
  - 服务实例管理、健康检查、路由策略
- **Config**：`nacos/config/` - 配置管理核心实现
  - 配置发布/订阅、灰度发布、历史版本
- **Server**：`nacos/server/` - Nacos Server 启动入口
- **Console**：`nacos/console/` + `nacos/console-ui/` - 管理控制台（后端 + 前端）
- **Core**：`nacos/core/` - 核心通用模块（集群管理、鉴权、分布式协议）
- **Consistency**：`nacos/consistency/` - 一致性协议实现（Raft/Distro）
- **Auth**：`nacos/auth/` - 认证鉴权模块
- **Plugin**：`nacos/plugin/` - 插件体系（鉴权、配置加密、数据源等）
- **Persistence**：`nacos/persistence/` - 持久化层

#### 第四层：高级主题（特定场景）
- **MCP 适配**：`nacos/mcp-registry-adaptor/` - MCP 注册适配器
- **Istio 集成**：`nacos/istio/` - Istio MCP/xDS 协议对接
- **K8s 同步**：`nacos/k8s-sync/` - Kubernetes 服务同步
- **AI 能力**：`nacos/ai/` - AI 相关能力
- **Skill 市场**：`nacos/skills/` - Skill 市场能力
- **Distribution**：`nacos/distribution/` - 打包和发布配置

### 何时探索 Nacos

仅在以下场景需要深入探索：
- 实现或调试 HiMarket 与 Nacos 的服务注册/发现集成
- 对接 Nacos 配置管理能力（动态配置推送、监听）
- 排查 Nacos 客户端连接、心跳、同步等问题
- 理解 Nacos 的一致性协议（Raft/Distro）实现细节
- 扩展 Nacos 插件（鉴权、数据源、配置加密等）
- 对接 Nacos 的 Open API 或使用其 Java SDK

**对于其他 HiMarket 功能开发（产品管理、用户认证、网关配置等），无需关注 Nacos 目录。**

## 其他文档

- 用户指南：`USER_GUIDE.md`
