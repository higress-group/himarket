# HiMarket 部署指南

## 概述

HiMarket 完整部署包含以下组件：
- **MySQL** - 数据库服务
- **Nacos** - 配置中心
- **Higress** - AI 网关
- **Redis** - 缓存服务
- **HiMarket** - Server + Admin + Frontend + Sandbox

提供两种部署方式：
- **Docker Compose** - 适合本地开发和单机部署
- **Helm（Kubernetes）** - 适合生产环境和集群部署

两种方式均使用交互式引导脚本 `install.sh`，自动完成全部配置和初始化。

## Docker Compose 部署

### 前置条件

- `docker`
- `docker compose`
- `curl`
- `jq`

### 交互式部署（推荐）

```bash
git clone https://github.com/higress-group/himarket.git
cd himarket/deploy/docker
./install.sh
```

脚本会逐步引导您完成所有配置，包括镜像选择、密码设置、AI 模型配置等。

### 非交互模式（CI/CD）

```bash
cp .env.example ~/himarket-install.env
# 编辑 ~/himarket-install.env 按需修改配置
./install.sh -n
```

### 卸载

```bash
./install.sh --uninstall
```

### 升级

重新运行 `./install.sh`，脚本会自动检测已有部署并提供升级选项。

### 服务端口

| 服务 | 主机端口 | 说明 |
|------|---------|------|
| HiMarket Admin | 5174 | 管理后台界面 |
| HiMarket Frontend | 5173 | 开发者门户界面 |
| HiMarket Server | 8081 | 后端 API 服务 |
| Nacos | 8848 | Nacos 控制台 |
| Higress Console | 8001 | Higress 控制台 |
| Higress Gateway | 8082 | 网关 HTTP 入口 |
| MySQL | 3306 | 数据库服务 |
| Redis | 6379 | Redis 服务 |

## Helm 部署（Kubernetes）

### 前置条件

- `kubectl`（已连接 K8s 集群）
- `helm`
- `curl`
- `jq`
- `python3`

### 交互式部署（推荐）

```bash
git clone https://github.com/higress-group/himarket.git
cd himarket/deploy/helm
./install.sh
```

### 非交互模式

```bash
cp .env.example ~/himarket-install.env
# 编辑配置
./install.sh -n
```

### 卸载

```bash
./install.sh --uninstall
```

### 升级

重新运行 `./install.sh`，脚本会自动检测已有部署并提供升级选项。

### 说明

- 默认部署到 `himarket` 命名空间
- Admin 和 Frontend 服务默认使用 LoadBalancer 类型

## 配置说明

- 交互模式下脚本会逐步引导所有配置项（镜像、数据库密码、服务凭证、默认用户、AI 模型等）
- 配置自动保存到 `~/himarket-install.env`，后续升级时自动复用
- `.env.example` 是配置模板，包含所有可配置项及默认值，可用于非交互模式
- 日志文件位于 `~/himarket-install.log`

## 常见问题

### 查看服务日志

Docker 方式：
```bash
docker compose logs -f
```

Helm 方式：
```bash
kubectl logs -n himarket <pod-name>
```

### 部署失败如何排查

检查日志文件：
```bash
cat ~/himarket-install.log
```

### 如何重试初始化钩子

钩子脚本在 `hooks/post_ready.d/` 目录下，可独立执行。

### 如何跳过部分初始化

- 交互模式：脚本会提供跳过选项
- 非交互模式：通过环境变量控制，如 `SKIP_MCP_INIT=true`
