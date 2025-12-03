# HiMarket 部署脚本说明

## 目录结构

```
himarket/
├── Chart.yaml                           # Helm Chart 元数据
├── values.yaml                          # Helm Chart 默认值
├── templates/                           # Helm 模板文件
│   ├── himarket-admin-deployment.yaml
│   ├── himarket-frontend-deployment.yaml
│   ├── himarket-server-deployment.yaml
│   ├── mysql.yaml                      # MySQL StatefulSet（可选）
│   └── ...
└── scripts/                             # 部署脚本目录
    ├── deploy.sh                       # 主部署脚本
    ├── README.md                       # 本文档
    ├── data/                           # 数据文件目录
    │   ├── .env                        # 环境变量配置（版本号、密码、API密钥等）
    │   ├── higress-mcp.json            # Higress MCP 统一配置文件
    │   ├── nacos-mcp.json              # Nacos MCP 统一配置文件
    │   ├── travel.yaml                 # Travel MCP 的 OpenAPI 定义
    │   └── README.md                   # 配置文件详细说明
    └── hooks/                          # 钩子脚本目录
        └── post_ready.d/               # 部署就绪后执行的钩子
            ├── 10-init-nacos-admin.sh      # Nacos 管理员密码初始化（开源版）
            ├── 20-init-himarket-admin.sh   # HiMarket 管理员账号注册
            ├── 25-init-commercial-nacos.sh # 商业化 Nacos 实例初始化
            ├── 30-init-higress-mcp.sh      # Higress MCP 统一初始化
            ├── 35-import-nacos-mcp.sh      # Nacos MCP 数据导入（开源版）
            ├── 40-init-himarket-mcp.sh     # HiMarket MCP 统一初始化
            ├── 50-init-himarket-front.sh   # HiMarket 前台开发者账号注册
            ├── 60-init-portal-developer.sh # Portal 开发者审批与订阅
            └── ...                         # nacos数据初始化进himarket
```

## 使用方式

### 一键部署

在 `helm/scripts` 目录下执行：

```bash
./deploy.sh install
```

该脚本会按顺序执行：
1. 部署 HiMarket（可内置 MySQL 或使用外部数据库）
2. 部署 Nacos（共享 HiMarket 的数据库）**或** 使用商业化 Nacos 实例
3. 部署 Higress（网关）
4. 执行 `post_ready.d/` 下的所有钩子脚本（按序号自动初始化）：
   - 10-init-nacos-admin.sh：初始化 Nacos 管理员密码（开源版）
   - 20-init-himarket-admin.sh：注册 HiMarket 管理员账号
   - 25-init-commercial-nacos.sh：初始化商业化 Nacos 实例（商业化版）
   - 30-init-higress-mcp.sh：**根据 higress-mcp.json 批量初始化所有 MCP 服务**
   - 35-import-nacos-mcp.sh：导入 MCP 配置到 Nacos（开源版）
   - 40-init-himarket-mcp.sh：**根据 higress-mcp.json 批量配置产品和发布**
   - 50-init-himarket-front.sh：注册 HiMarket 前台开发者账号
   - 60-init-portal-developer.sh：审批开发者并自动订阅产品

### 卸载

```bash
./deploy.sh uninstall
```

## 钩子机制

### 执行时机

- `post_ready.d/` - 所有组件部署就绪后执行

### 钩子规范

1. 放置位置：`scripts/hooks/<阶段名>.d/`
2. 文件名格式：`<序号>-<描述>.sh`（如 `10-import-nacos-mcp.sh`）
3. 执行权限：必须设置为可执行（`chmod +x`）
4. 执行顺序：按文件名字典序排序
5. 环境变量：自动继承主脚本的所有环境变量（如 `NS`、`DB_HOST` 等）

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

4. **执行部署**

   ```bash
   cd ..
   ./deploy.sh install
   ```

### 行为变化

当 `USE_COMMERCIAL_NACOS=true` 时：

- ✅ **跳过**开源 Nacos 的 Helm 部署
- ✅ **不执行** `10-init-nacos-admin.sh` （开源 Nacos 管理员初始化）
- ✅ **不执行** `35-import-nacos-mcp.sh` （开源 Nacos MCP 导入）
- ✅ **执行** `25-init-commercial-nacos.sh` （商业化 Nacos 初始化）
  - 登录 HiMarket Admin 获取 Token
  - 调用管理 API 创建/更新 Nacos 实例配置
  - 登录商业化 Nacos 获取 accessToken

### 注意事项

1. **依赖顺序**
   
   商业化 Nacos 初始化脚本（25）在 HiMarket Admin（20）之后执行，因为需要先登录 HiMarket 获取 Token。

2. **网络访问**
   
   确保部署环境能够访问：
   - Nacos 服务地址（一般是公网或 VPC 内网）
   - HiMarket Admin Service（自动获取）

3. **切换回开源版**
   
   如需切换回开源 Nacos，设置：
   ```bash
   USE_COMMERCIAL_NACOS=false
   ```

## 配置驱动架构

### 核心配置文件：higress-mcp.json

所有 MCP 服务的配置统一在 `scripts/data/higress-mcp.json` 中管理，支持两种类型：

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

### 环境变量配置

编辑 `scripts/data/.env`

## 添加新的 MCP 服务

**完全配置驱动，无需修改脚本代码！**

### 方式一：添加 OpenAPI 类型 MCP

1. **创建 OpenAPI YAML 文件**

   在 `scripts/data/` 目录下创建，例如 `weather.yaml`：

   ```yaml
   server:
     name: "weather"
     securitySchemes:
       - defaultCredential: "${WEATHER_API_KEY}"  # 支持环境变量
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

然后执行 `./deploy.sh install` 即可。

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

### Q: 如何添加新的 MCP 服务？

A: 只需在 `scripts/data/higress-mcp.json` 中添加配置，无需修改任何脚本！详见上方「添加新的 MCP 服务」章节。

### Q: 如何单独测试某个钩子脚本？

A: 加载环境变量后手动执行：

```bash
# 加载环境变量
cd scripts/data
set -a
. .env
set +a

# 设置必要的变量
export NS=himarket-dev

# 执行钩子
cd ../hooks/post_ready.d
./30-init-higress-mcp.sh
```

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
