# HiMarket 部署脚本说明

## 快速开始

### 完整部署（推荐）

在 `helm/scripts` 目录下执行：

```bash
./deploy.sh install
```

该脚本会按顺序部署：
1. **HiMarket**（可内置 MySQL 或使用外部数据库）
2. **Nacos**（共享 HiMarket 的数据库）或使用商业化 Nacos 实例
3. **Higress**（网关）
4. **执行初始化钩子**（按序号自动配置数据）

### 仅部署 HiMarket（轻量模式）

如果你已有独立的 Nacos 和 Higress，或只想快速部署 HiMarket 服务：

```bash
./deploy.sh himarket-only
```

该模式会：
- ✅ 部署 HiMarket（frontend、admin、server）
- ✅ 部署内置 MySQL（可选，通过 `.env` 配置）
- ❌ 跳过 Nacos 部署
- ❌ 跳过 Higress 部署
- ❌ 跳过所有钩子脚本（数据初始化）


**也可通过环境变量控制：**
```bash
# 方式1：修改 .env 文件
HIMARKET_ONLY=true

# 方式2：临时设置
export HIMARKET_ONLY=true
./deploy.sh install
```

### 卸载

```bash
./deploy.sh uninstall
```

---

## 环境配置

### 配置文件位置

所有配置集中在 `scripts/data/.env` 文件中：

```bash
cd scripts/data
vi .env
```

### 关键配置项

#### 1. 命名空间
```bash
NAMESPACE=himarket-system
```

#### 2. HiMarket 镜像配置
```bash
HIMARKET_HUB=opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group
HIMARKET_IMAGE_TAG=latest
HIMARKET_MYSQL_IMAGE_TAG=latest
```

#### 3. MySQL 数据库配置
```bash
# 是否使用内置 MySQL
HIMARKET_MYSQL_ENABLED=true

# 外部数据库配置（当 HIMARKET_MYSQL_ENABLED=false 时使用）
EXTERNAL_DB_HOST=Your_External_DB_Host
EXTERNAL_DB_PORT=3306
EXTERNAL_DB_NAME=Your_DB_Name
EXTERNAL_DB_USERNAME=Your_DB_Username
EXTERNAL_DB_PASSWORD=Your_DB_Password
```

#### 4. 部署模式控制
```bash
# 仅部署 HiMarket（跳过 Nacos、Higress 和钩子）
HIMARKET_ONLY=false

# 使用商业化 Nacos 实例（跳过开源 Nacos 部署）
USE_COMMERCIAL_NACOS=false
```

#### 5. Nacos 配置
```bash
# 开源 Nacos 版本
NACOS_VERSION=v3.1.1
NACOS_ADMIN_PASSWORD=nacos

# 商业化 Nacos 配置（当 USE_COMMERCIAL_NACOS=true 时必填）
COMMERCIAL_NACOS_NAME=
COMMERCIAL_NACOS_SERVER_URL=
COMMERCIAL_NACOS_USERNAME=
COMMERCIAL_NACOS_PASSWORD=
```

#### 6. Higress 配置
```bash
HIGRESS_USERNAME=admin
HIGRESS_PASSWORD=admin
```

#### 7. Himarket 默认用户配置
```bash
# 后台管理员
ADMIN_USERNAME=admin
ADMIN_PASSWORD=admin

# 前台开发者
FRONT_USERNAME=demo
FRONT_PASSWORD=demo123
```

---

## 部署模式对比

| 功能 | 完整部署 (`install`) | 轻量部署 (`himarket-only`) | 商业化 Nacos (`USE_COMMERCIAL_NACOS=true`) |
|------|---------------------|---------------------------|------------------------------------------|
| HiMarket | ✅ | ✅ | ✅ |
| 内置 MySQL | ✅ 可选 | ✅ 可选 | ✅ 可选 |
| 开源 Nacos | ✅ | ❌ | ❌ |
| 商业化 Nacos | ❌ | ❌ | ✅ |
| Higress | ✅ | ❌ | ✅ |
| 初始化钩子 | ✅ | ❌ | ✅ 部分 |
| Helm Repo | ✅ | ❌ | ✅ |

---

## 钩子机制

### 执行时机

钩子在 `post_ready.d/` 目录中，在所有组件部署就绪后自动执行。

**注意：**当使用 `himarket-only` 模式时，**不会执行任何钩子**。

### 钩子列表

| 序号 | 脚本名 | 功能 | 开源 Nacos | 商业化 Nacos |
|------|---------|------|-----------|-------------|
| 10 | init-nacos-admin.sh | 初始化 Nacos 管理员密码 | ✅ | ❌ |
| 20 | init-himarket-admin.sh | 注册 HiMarket 管理员账号 | ✅ | ✅ |
| 25 | init-commercial-nacos.sh | 初始化商业化 Nacos 实例 | ❌ | ✅ |
| 30 | init-higress-mcp.sh | 批量初始化 Higress MCP 服务（higress-mcp.json） | ✅ | ✅ |
| 35 | import-nacos-mcp.sh | 导入 MCP Server 到 Nacos（mcp.json） | ✅ | ❌ |
| 40 | init-himarket-mcp.sh | 批量配置 HiMarket 产品发布（支持两种 MCP） | ✅ | ✅ |
| 50 | init-himarket-front.sh | 注册 HiMarket 前台开发者 | ✅ | ✅ |
| 60 | init-portal-developer.sh | 审批开发者并自动订阅 | ✅ | ✅ |

### 钩子规范

1. **放置位置**：`scripts/hooks/<阶段名>.d/`
2. **文件名格式**：`<序号>-<描述>.sh`（如 `10-init-nacos-admin.sh`）
3. **执行权限**：必须设置为可执行（`chmod +x`）
4. **执行顺序**：按文件名字典序排序
5. **环境变量**：自动继承主脚本的所有环境变量（如 `NS`、`DB_HOST` 等）

---

## 目录结构

```
himarket/
├── Chart.yaml                           # Helm Chart 元数据
├── values.yaml                          # Helm Chart 默认值
├── templates/                           # Helm 模板文件
└── scripts/                             # 部署脚本目录
    ├── deploy.sh                       # 主部署脚本
    ├── README.md                       # 本文档
    ├── data/                           # 数据文件目录
    │   ├── .env                        # 环境变量配置
    │   ├── higress-mcp.json            # Higress MCP 统一配置文件
    │   ├── mcp.json                    # Nacos MCP Server 统一配置文件
    │   └── travel.yaml                 # Travel MCP 的 OpenAPI 定义
    └── hooks/                          # 钩子脚本目录
        └── post_ready.d/               # 部署就绪后执行的钩子
            ├── 10-init-nacos-admin.sh      # Nacos 管理员密码初始化
            ├── 20-init-himarket-admin.sh   # HiMarket 管理员账号注册
            ├── 25-init-commercial-nacos.sh # 商业化 Nacos 实例初始化
            ├── 30-init-higress-mcp.sh      # Higress MCP 统一初始化（读取 higress-mcp.json）
            ├── 35-import-nacos-mcp.sh      # Nacos MCP Server 导入（读取 mcp.json）
            ├── 40-init-himarket-mcp.sh     # HiMarket MCP 统一初始化（处理两种 MCP）
            ├── 50-init-himarket-front.sh   # HiMarket 前台开发者注册
            └── 60-init-portal-developer.sh # Portal 开发者审批与订阅
```

---

## 高级用法

### 跳过钩子错误

默认情况下，任何钩子失败会阻断流程。如需跳过错误继续执行：

```bash
export SKIP_HOOK_ERRORS=true
./scripts/deploy.sh install
```

## 使用商业化 Nacos 实例

如果你已经有阿里云 MSE 服务，可以跳过开源 Nacos 的部署，直接使用商业化实例。

### 配置步骤

1. **编辑 `.env` 文件**

   ```bash
   cd scripts/data
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

### 行为变化

当 `USE_COMMERCIAL_NACOS=true` 时：

- ✅ **跳过**开源 Nacos 的 Helm 部署
- ✅ **不执行** `10-init-nacos-admin.sh`（开源 Nacos 管理员初始化）
- ✅ **不执行** `35-import-nacos-mcp.sh`（开源 Nacos MCP 导入）
- ✅ **执行** `25-init-commercial-nacos.sh`（商业化 Nacos 初始化）
  - 登录 HiMarket Admin 获取 Token
  - 调用管理 API 创建/更新 Nacos 实例配置
  - 登录商业化 Nacos 获取 accessToken
---

## 配置驱动架构

### 核心配置文件

本部署方案支持两种 MCP 配置方式：

1. **higress-mcp.json**：适用于基于 Higress 网关的 MCP 服务
2. **mcp.json**：适用于基于 Nacos 的标准 MCP Server 服务

---

### Higress MCP 配置：higress-mcp.json

所有基于 Higress 的 MCP 服务配置统一在 `scripts/data/higress-mcp.json` 中管理，支持两种类型：

1. **OPEN_API**：基于 OpenAPI YAML 定义的 MCP 服务（如天气、出行助手）
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

### Nacos MCP 配置：mcp.json

标准 MCP Server 服务的配置统一在 `scripts/data/nacos-mcp.json` 中管理，这些服务会被导入到 Nacos 中。

配置示例：

```json
[
  {
    "serverSpecification": {
      "protocol": "stdio",
      "frontProtocol": "stdio",
      "name": "git",
      "description": "git v1.0.0",
      "packages": [
        {
          "registryType": "pypi",
          "identifier": "mcp-server-git",
          "version": "latest",
          "runtimeHint": "uvx",
          "packageArguments": [
            {
              "type": "positional",
              "format": "string",
              "value": "--repository"
            },
            {
              "type": "positional",
              "format": "string",
              "value": "path/to/git/repo"
            }
          ]
        }
      ],
      "versionDetail": {
        "version": "1.0.0"
      },
      "enabled": true
    },
    "toolSpecification": {
      "tools": [
        {
          "name": "git_status",
          "description": "Shows the working tree status",
          "inputSchema": {
            "type": "object",
            "properties": {
              "repo_path": {
                "type": "string"
              }
            },
            "required": ["repo_path"]
          }
        }
      ]
    },
    "himarket": {
      "product": {
        "name": "git",
        "description": "Git MCP Server",
        "type": "MCP_SERVER"
      },
      "publishToPortal": true,
      "portalName": "demo",
      "namespaceId": "public"
    }
  }
]
```

**配置说明：**

- `serverSpecification`：MCP Server 的基本信息和运行配置
  - `name`：MCP 服务名称（必填）
  - `protocol`：通信协议（stdio/http/sse）
  - `packages`：运行时包配置
  - `enabled`：是否启用
- `toolSpecification`：工具定义（可选）
  - `tools`：MCP 提供的工具列表
  - 每个工具包含名称、描述和输入参数定义
- `himarket`：HiMarket 平台配置（可选）
  - `product`：产品信息
  - `publishToPortal`：是否发布到门户
  - `portalName`：目标门户名称
  - `namespaceId`：Nacos 命名空间（默认 public）

---

### 配置技巧

1. **跳过 HiMarket 配置**：移除 `himarket` 字段,MCP 只会导入到 Nacos
2. **配置 API KEY**：如果有 API Key 或其他认证，需要在 YAML 文件或 json 文件中进行配置

---

### 自定义钩子脚本（高级）

如需添加额外的初始化任务：

1. 在 `scripts/hooks/post_ready.d/` 下创建新脚本
2. 文件名使用递增序号（如 `80-init-custom-data.sh`）
3. 设置可执行权限：`chmod +x 80-init-custom-data.sh`
4. 脚本可直接使用环境变量：`NS`、`NAMESPACE`、`ADMIN_HOST` 等

示例：

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/../../data"

# 从 .env 加载环境变量
if [[ -f "${DATA_DIR}/.env" ]]; then
  set -a
  . "${DATA_DIR}/.env"
  set +a
fi

NS="${NAMESPACE:-himarket}"

log() { echo "[custom-init $(date +'%H:%M:%S')] $*"; }

log "执行自定义初始化..."
log "命名空间: ${NS}"

# 你的初始化逻辑
# ...

log "自定义初始化完成"
```

## 常见问题

### Q: 如何选择部署模式？

A: 根据你的场景选择：

- **完整部署** (`./deploy.sh install`)：适合首次部署或一键搭建完整环境
- **轻量部署** (`./deploy.sh himarket-only`)：适合已有 Nacos/Higress 或需要单独管理 HiMarket
- **商业化 Nacos** (`USE_COMMERCIAL_NACOS=true`)：适合生产环境，使用阿里云 MSE

### Q: 如果执行失败如何重试或想单独执行某个钩子脚本？

A: 钩子脚本支持独立执行，加载环境变量后手动运行：

```bash
cd ../hooks/post_ready.d
./30-init-higress-mcp.sh

# 或者重试失败的钩子
./40-init-himarket-mcp.sh
```

**提示：** 某些钩子脚本有依赖关系，需按序号顺序执行。例如 `40-init-himarket-mcp.sh` 依赖 `20-init-himarket-admin.sh` 先完成。

### Q: 钩子执行失败如何排查？

A: 
1. 查看钩子日志输出，所有脚本都有详细的执行步骤和错误信息
2. 检查 `scripts/data/.env` 中的环境变量是否正确配置
3. 对于 Higress/HiMarket 相关钩子，确认服务已正常运行并可访问
4. 使用 `kubectl logs` 查看相关 Pod 的日志

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

### Q: 脚本需要哪些依赖？

A: 
- `kubectl`：Kubernetes 命令行工具
- `jq`：JSON 处理工具（必需，用于解析 higress-mcp.json）
- `curl`：HTTP 客户端
- `python3` 或 `python`：用于 YAML 内容转义（OpenAPI 类型 MCP 必需）
- `envsubst`（可选）：用于环境变量替换，如无则使用 `sed` 替代

安装 jq：
```bash
# macOS
brew install jq

# Ubuntu/Debian
sudo apt-get install jq

# CentOS/RHEL
sudo yum install jq
```
