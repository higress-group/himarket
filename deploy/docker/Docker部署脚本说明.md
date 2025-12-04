# HiMarket Docker 部署脚本说明

## 快速开始

### 完整部署（推荐）

在 `docker/scripts` 目录下执行：

```bash
./deploy.sh install
```

该脚本会按顺序部署：
1. **MySQL**（可内置或使用外部数据库）
2. **Nacos**（配置中心）或使用商业化 Nacos 实例
3. **Redis**（用于 Higress MCP）
4. **Higress**（网关 + 控制台 All-in-One）
5. **HiMarket**（frontend、admin、server）
6. **执行初始化钩子**（按序号自动配置数据）

### 仅部署 HiMarket（轻量模式）

如果你已有独立的 Nacos 和 Higress，或只想快速部署 HiMarket 服务：

```bash
./deploy.sh himarket-only
```

该模式会：
- ✅ 部署 HiMarket（frontend、admin、server）
- ✅ 部署内置 MySQL（可选，通过 `.env` 配置）
- ❌ 跳过 Nacos 部署
- ❌ 跳过 Redis 部署
- ❌ 跳过 Higress 部署
- ❌ 跳过所有钩子脚本（数据初始化）

### 卸载

```bash
./deploy.sh uninstall
```

---

## 环境配置

### 配置文件位置

所有配置集中在 `data/.env` 文件中：

```bash
cd data
vi .env
```

### 关键配置项

#### 1. 镜像配置
```bash
# Nacos
NACOS_IMAGE=nacos-registry.cn-hangzhou.cr.aliyuncs.com/nacos/nacos-server:v3.1.1-slim

# Higress
HIGRESS_IMAGE=higress-registry.cn-hangzhou.cr.aliyuncs.com/higress/all-in-one:latest

# HiMarket
HIMARKET_SERVER_IMAGE=opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/himarket-server:latest
HIMARKET_ADMIN_IMAGE=opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/himarket-admin:latest
HIMARKET_FRONTEND_IMAGE=opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/himarket-frontend:latest

# Redis
REDIS_IMAGE=higress-registry.cn-hangzhou.cr.aliyuncs.com/higress/redis-stack-server:7.4.0-v3

# MySQL
MYSQL_IMAGE=opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/mysql:latest
```

#### 2. MySQL 数据库配置
```bash
# MySQL 内部配置
MYSQL_ROOT_PASSWORD=123456
MYSQL_DATABASE=portal_db
MYSQL_USER=portal_user
MYSQL_PASSWORD=portal_pass

# 是否使用内置 MySQL
USE_BUILTIN_MYSQL=true

# HiMarket 数据库连接配置
# 当 USE_BUILTIN_MYSQL=true 时，使用内置 MySQL
# 当 USE_BUILTIN_MYSQL=false 时，需配置外部数据库信息
DB_HOST=mysql
DB_PORT=3306
DB_NAME=portal_db
DB_USERNAME=portal_user
DB_PASSWORD=portal_pass
```

#### 3. Nacos 配置
```bash
# Nacos 认证配置
NACOS_AUTH_IDENTITY_KEY=serverIdentity
NACOS_AUTH_IDENTITY_VALUE=security
NACOS_AUTH_TOKEN=VGhpc0lzTXlDdXN0b21TZWNyZXRLZXkwMTIzNDU2Nzg=

# Nacos 管理员密码
NACOS_ADMIN_PASSWORD=nacos
```

#### 4. Higress 配置
```bash
HIGRESS_USERNAME=admin
HIGRESS_PASSWORD=admin
```

#### 5. HiMarket 默认用户配置
```bash
# 后台管理员
ADMIN_USERNAME=admin
ADMIN_PASSWORD=admin

# 前台开发者
FRONT_USERNAME=demo
FRONT_PASSWORD=demo123
```

#### 6. 商业化 Nacos 配置
```bash
# 商业化 Nacos 开关
# 设置为 true 时使用商业化 Nacos 实例，不部署开源 Nacos
USE_COMMERCIAL_NACOS=false

# 商业化 Nacos 实例配置（当 USE_COMMERCIAL_NACOS=true 时必填）
COMMERCIAL_NACOS_NAME=
COMMERCIAL_NACOS_SERVER_URL=
# AKSK or username/password 选一组必填
COMMERCIAL_NACOS_USERNAME=
COMMERCIAL_NACOS_PASSWORD=
COMMERCIAL_NACOS_ACCESS_KEY=
COMMERCIAL_NACOS_SECRET_KEY=
```

---

## 部署模式对比

| 功能 | 完整部署 (`install`) | 轻量部署 (`himarket-only`) | 商业化 Nacos (`USE_COMMERCIAL_NACOS=true`) |
|------|---------------------|---------------------------|------------------------------------------|
| HiMarket | ✅ | ✅ | ✅ |
| 内置 MySQL | ✅ 可选 | ✅ 可选 | ✅ 可选 |
| 开源 Nacos | ✅ | ❌ | ❌ |
| 商业化 Nacos | ❌ | ❌ | ✅ |
| Redis | ✅ | ❌ | ✅ |
| Higress | ✅ | ❌ | ✅ |
| 初始化钩子 | ✅ | ❌ | ✅ 部分 |

---

## 钩子机制

### 执行时机

钩子在 `scripts/hooks/post_ready.d/` 目录中，在所有组件部署就绪后自动执行。

**注意：**当使用 `himarket-only` 模式时，**不会执行任何钩子**。

### 钩子列表

| 序号 | 脚本名 | 功能 | 开源 Nacos | 商业化 Nacos |
|------|---------|------|-----------|-------------|
| 10 | init-nacos-admin.sh | 初始化 Nacos 管理员密码 | ✅ | ❌ |
| 15 | init-higress-admin.sh | 初始化 Higress 管理员账号 | ✅ | ✅ |
| 20 | init-himarket-admin.sh | 注册 HiMarket 管理员账号 | ✅ | ✅ |
| 25 | init-commercial-nacos.sh | 初始化商业化 Nacos 实例 | ❌ | ✅ |
| 30 | init-higress-mcp.sh | 批量初始化 Higress MCP 服务 | ✅ | ✅ |
| 35 | import-nacos-mcp.sh | 导入 MCP 配置到 Nacos | ✅ | ❌ |
| 40 | init-himarket-mcp.sh | 批量配置 HiMarket 产品发布 | ✅ | ✅ |
| 50 | init-himarket-front.sh | 注册 HiMarket 前台开发者 | ✅ | ✅ |
| 60 | init-portal-developer.sh | 审批开发者并自动订阅 | ✅ | ✅ |

### 钩子规范

1. **放置位置**：`scripts/hooks/<阶段名>.d/`
2. **文件名格式**：`<序号>-<描述>.sh`（如 `10-init-nacos-admin.sh`）
3. **执行权限**：必须设置为可执行（`chmod +x`）
4. **执行顺序**：按文件名字典序排序
5. **环境变量**：自动继承主脚本的所有环境变量（如 `.env` 中的配置）

---

## 目录结构

```
docker/
├── docker-compose.yml                      # Docker Compose 配置文件
├── Docker部署说明.md                    
├── Docker脚本部署说明.md                     # 本文档
└── scripts/                                # 部署脚本目录
    ├── deploy.sh                           # 主部署脚本
    ├── data/                               # 数据文件目录
    │   ├── .env                            # 环境变量配置
    │   ├── higress-mcp.json                # Higress MCP 统一配置文件
    │   ├── nacos-mcp.json                  # Nacos MCP 统一配置文件
    │   └── travel.yaml                     # Travel MCP 的 OpenAPI 定义
    └── hooks/
        └── post_ready.d/                   # 部署就绪后执行的钩子
            ├── 10-init-nacos-admin.sh      # Nacos 管理员密码初始化
            ├── 15-init-higress-admin.sh    # Higress 管理员账号初始化
            ├── 20-init-himarket-admin.sh   # HiMarket 管理员账号注册
            ├── 25-init-commercial-nacos.sh # 商业化 Nacos 实例初始化
            ├── 30-init-higress-mcp.sh      # Higress MCP 统一初始化
            ├── 35-import-nacos-mcp.sh      # Nacos MCP 数据导入
            ├── 40-init-himarket-mcp.sh     # HiMarket MCP 统一初始化
            ├── 50-init-himarket-front.sh   # HiMarket 前台开发者注册
            └── 60-init-portal-developer.sh # Portal 开发者审批与订阅
```

---

## 高级用法

### 使用外部 MySQL 数据库

如果你已有 MySQL 数据库，可以关闭内置 MySQL：

1. **编辑 `.env` 文件**

   ```bash
   cd data
   vi .env
   ```

2. **关闭内置 MySQL 并配置外部数据库**

   ```bash
   # 关闭内置 MySQL
   USE_BUILTIN_MYSQL=false
   
   # 配置外部数据库连接信息
   DB_HOST=your-external-mysql-host
   DB_PORT=3306
   DB_NAME=portal_db
   DB_USERNAME=your_db_user
   DB_PASSWORD=your_db_password
   ```

3. **执行部署**

   ```bash
   cd ..
   ./deploy.sh install
   ```

### 使用商业化 Nacos 实例

如果你已经有阿里云 MSE 服务，可以跳过开源 Nacos 的部署，直接使用商业化实例。

#### 配置步骤

1. **编辑 `.env` 文件**

   ```bash
   cd data
   vi .env
   ```

2. **启用商业化 Nacos 开关**

   ```bash
   USE_COMMERCIAL_NACOS=true
   ```

3. **填写商业化 Nacos 配置**

   ```bash
   COMMERCIAL_NACOS_NAME=my-nacos-instance
   COMMERCIAL_NACOS_SERVER_URL=https://your-nacos-server.aliyuncs.com
   # AKSK 或 username/password 选一组必填
   COMMERCIAL_NACOS_USERNAME=your-username
   COMMERCIAL_NACOS_PASSWORD=your-password
   # 或使用 AKSK
   COMMERCIAL_NACOS_ACCESS_KEY=your-access-key
   COMMERCIAL_NACOS_SECRET_KEY=your-secret-key
   ```

4. **执行部署**

   ```bash
   cd ..
   ./deploy.sh install
   ```

#### 行为变化

当 `USE_COMMERCIAL_NACOS=true` 时：

- ✅ **跳过**开源 Nacos 的 Docker 容器部署
- ✅ **不执行** `10-init-nacos-admin.sh`（开源 Nacos 管理员初始化）
- ✅ **不执行** `35-import-nacos-mcp.sh`（开源 Nacos MCP 导入）
- ✅ **执行** `25-init-commercial-nacos.sh`（商业化 Nacos 初始化）
  - 登录 HiMarket Admin 获取 Token
  - 调用管理 API 创建/更新 Nacos 实例配置
  - 登录商业化 Nacos 获取 accessToken

---

## 配置驱动架构

### 核心配置文件：higress-mcp.json

所有 MCP 服务的配置统一在 `data/higress-mcp.json` 中管理，支持两种类型：

1. **OPEN_API**：基于 OpenAPI YAML 定义的 MCP 服务（如出行助手）
2. **DIRECT_ROUTE**：直接路由方式的 MCP 服务（如基金诊断）

配置示例：

```json
[
  {
    "name": "travel",
    "description": "出行小助手",
    "type": "OPEN_API",
    "higress": {
      "domains": ["travel.assistant.io"],
      "serviceSources": [...],
      "services": [...]
    },
    "openApiConfig": {
      "yamlFile": "travel.yaml"
    },
    "himarket": {
      "product": {
        "name": "travel",
        "description": "出行小助手",
        "type": "MCP_SERVER"
      },
      "publishToPortal": true,
      "portalName": "demo"
    }
  }
]
```

---

## 添加新的 Higress MCP 服务

**完全配置驱动，无需修改脚本代码！**

### 方式一：添加 OpenAPI 类型 MCP

1. **创建 OpenAPI YAML 文件**

   在 `data/` 目录下创建，例如 `weather.yaml`：

   ```yaml
   server:
     name: "weather"
     securitySchemes:
       - defaultCredential:  "你的 API Key" # 用户需配置自己的 API Key  
         type: apiKey
   tools:
     - name: "getWeather"
       description: "获取天气信息"
       # ...
   ```

2. **在 higress-mcp.json 中添加配置**

   ```json
   {
     "name": "weather",
     "description": "天气助手",
     "type": "OPEN_API",
     "higress": {
       "domains": ["weather.assistant.io"],
       "serviceSources": [
         {
           "type": "dns",
           "name": "weather-api",
           "domain": "api.weather.com",
           "port": 443,
           "protocol": "https"
         }
       ],
       "services": [
         {
           "name": "weather-api.dns",
           "port": 443,
           "weight": 100
         }
       ],
       "consumerAuth": {
         "type": "key-auth",
         "enable": false
       }
     },
     "openApiConfig": {
       "yamlFile": "weather.yaml"
     },
     "himarket": {
       "product": {
         "name": "weather",
         "description": "天气助手",
         "type": "MCP_SERVER"
       },
       "publishToPortal": true,
       "portalName": "demo"
     }
   }
   ```

3. **执行部署**

   ```bash
   ./deploy.sh install
   ```

### 方式二：添加 Direct Route 类型 MCP

**更简单，无需创建 YAML 文件！**

直接在 `higress-mcp.json` 中添加：

```json
{
  "name": "my-service",
  "description": "我的服务",
  "type": "DIRECT_ROUTE",
  "higress": {
    "domains": ["my-service.assistant.io"],
    "serviceSources": [
      {
        "type": "dns",
        "name": "backend",
        "domain": "backend.example.com",
        "port": 8080,
        "protocol": "http"
      }
    ],
    "services": [
      {
        "name": "backend.dns",
        "port": 8080,
        "weight": 100
      }
    ],
    "consumerAuth": {
      "type": "key-auth",
      "enable": false
    }
  },
  "directRouteConfig": {
    "path": "/mcp-servers/my-service/sse",
    "transportType": "sse"
  },
  "himarket": {
    "product": {
      "name": "my-service",
      "description": "我的服务",
      "type": "MCP_SERVER"
    },
    "publishToPortal": true,
    "portalName": "demo"
  }
}
```

---

## 常见问题

### Q: 如何选择部署模式？

A: 根据你的场景选择：

- **完整部署** (`./deploy.sh install`)：适合首次部署或一键搭建完整环境
- **轻量部署** (`./deploy.sh himarket-only`)：适合已有 Nacos/Higress 或需要单独管理 HiMarket
- **商业化 Nacos** (`USE_COMMERCIAL_NACOS=true`)：适合生产环境，使用阿里云 MSE

### Q: 部署需要哪些依赖？

A:
- `docker`：Docker 引擎
- `docker-compose`：容器编排工具
- `curl`：HTTP 客户端
- `jq`：JSON 处理工具（必需，用于解析 higress-mcp.json）

安装 jq：
```bash
# macOS
brew install jq

# Ubuntu/Debian
sudo apt-get install jq

# CentOS/RHEL
sudo yum install jq
```

### Q: 如果执行失败如何重试或想单独执行某个钩子脚本？

A: 钩子脚本支持独立执行，手动运行：

```bash
cd scripts/hooks/post_ready.d
./30-init-higress-mcp.sh

# 或者重试失败的钩子
./40-init-himarket-mcp.sh
```

**提示：** 某些钩子脚本有依赖关系，需按序号顺序执行。例如 `40-init-himarket-mcp.sh` 依赖 `20-init-himarket-admin.sh` 先完成。

### Q: 钩子执行失败如何排查？

A:
1. 查看钩子日志输出，所有脚本都有详细的执行步骤和错误信息
2. 检查 `data/.env` 中的环境变量是否正确配置
3. 对于 Higress/HiMarket 相关钩子，确认服务已正常运行并可访问
4. 使用 `docker-compose logs <service-name>` 查看相关容器的日志

### Q: 如何跳过某些 MCP 的 HiMarket 配置？

A: 在 `higress-mcp.json` 中移除或注释掉该 MCP 的 `himarket` 配置项：

```json
{
  "name": "my-mcp",
  "type": "OPEN_API",
  "higress": { ... },
  "openApiConfig": { ... }
  // 不添加 himarket 配置，只会在 Higress 中创建，不会在 HiMarket 中配置
}
```

### Q: 如何禁用某个钩子？

A: 移除脚本的执行权限或重命名为非 `.sh` 后缀：

```bash
chmod -x scripts/hooks/post_ready.d/30-init-higress-mcp.sh
# 或
mv scripts/hooks/post_ready.d/30-init-higress-mcp.sh scripts/hooks/post_ready.d/30-init-higress-mcp.sh.disabled
```

### Q: 如何查看服务状态和日志？

A:
```bash
# 查看所有服务状态
docker-compose ps

# 查看特定服务日志
docker-compose logs -f himarket-server
docker-compose logs -f higress
docker-compose logs -f nacos

# 查看所有服务日志
docker-compose logs -f
```

### Q: 如何停止和重启服务？

A:
```bash
# 停止所有服务
docker-compose stop

# 启动所有服务
docker-compose start

# 重启特定服务
docker-compose restart himarket-server

# 重新部署（删除并重建容器）
docker-compose down
./deploy.sh install
```

### Q: 数据会保存在哪里？

A: 数据持久化在以下目录：
- `data/mysql/` - MySQL 数据库文件（内置 MySQL 时）
- `data/higress/` - Higress 配置数据
- `standalone-logs/` - Nacos 日志文件

卸载时可选择是否保留这些数据。

---

## 服务端口列表

| 服务 | 容器端口 | 主机端口 | 说明 |
|------|---------|---------|------|
| HiMarket Admin | 8000 | 5174 | 管理后台界面 |
| HiMarket Frontend | 8000 | 5173 | 开发者门户界面 |
| HiMarket Server | 8080 | 8081 | 后端 API 服务 |
| Nacos | 8848 | 8080 | Nacos 控制台 |
| Higress Console | 8001 | 8001 | Higress 控制台 |
| Higress Gateway | 8082 | 8082 | 网关 HTTP 入口 |
| Higress Gateway HTTPS | 8443 | 8443 | 网关 HTTPS 入口 |
| MySQL | 3306 | 3306 | 数据库服务 |
| Redis | 6379 | 6379 | Redis 服务 |

---