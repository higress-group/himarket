# HiMarket AI 开放平台 Helm 部署指南

## 📋 项目说明

Helm 会部署三个应用，分别为：
- `himarket-server`：HiMarket AI 开放平台的后端服务；
- `himarket-admin`：HiMarket AI 开放平台管理后台，管理员通过此界面配置 Portal；
- `himarket-frontend`：HiMarket AI 开放平台的前台服务，用户通过此界面浏览和使用 API。

服务类型说明：

`himarket-admin` 和 `himarket-frontend` 默认为 LoadBalancer 类型服务，适用于云环境（阿里云 ACK、AWS EKS 等）。如果您的环境不支持 LoadBalancer（如本地 minikube、自建集群），可以使用 NodePort 或端口转发方式访问。后台配置好 HiMarket 后，将域名解析到 himarket-frontend 服务的访问地址，用户就可以通过域名访问前台站点。

**镜像说明：**

默认使用公开镜像仓库的镜像：
- `himarket-server`: `opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/himarket-server:latest`
- `himarket-admin`: `opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/himarket-admin:latest`
- `himarket-frontend`: `opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/himarket-frontend:latest`
- (可选) `mysql`: `opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/mysql:latest`

## 🗄️ 数据库配置

**本项目的后端服务`himarket-server`依赖一个 MariaDB/MySQL 数据库**

### 📋 数据库部署方式

HiMarket 支持两种数据库配置（以 MySQL 为例）：

- **内置 MySQL**（默认）：Helm 自动部署 MySQL 数据库
- **外置 MySQL**（可选）：连接到现有的外部数据库

### ⚙️ 内置 MySQL（默认）

#### 🔧 默认配置

使用内置 MySQL，Helm 会自动生成随机密码并处理所有数据库连接配置。

```yaml
mysql:
  enabled: true                                   # 启用内置 MySQL
  auth:
    database: "himarket_db"
    username: "himarket_user"
    rootPassword: ""                              # 留空自动生成
    password: ""                                  # 留空自动生成
  persistence:
    size: 50Gi
    storageClass: "alicloud-disk-essd"           # 根据环境调整
```

#### 🔐 自定义密码（可选）

如需指定固定密码：

```yaml
mysql:
  auth:
    rootPassword: "MyRootPass123"
    password: "MyAppPass456"
```

#### 🔑 获取自动生成的密码

```bash
# 获取 root 密码
kubectl get secret mysql-secret -n himarket -o jsonpath="{.data.MYSQL_ROOT_PASSWORD}" | base64 -d

# 获取应用密码
kubectl get secret mysql-secret -n himarket -o jsonpath="{.data.MYSQL_PASSWORD}" | base64 -d
```

### 🔗 外置 MySQL

#### 📝 配置方式

```yaml
# 关闭内置 MySQL
mysql:
  enabled: false

# 配置外部数据库
database:
  host: "your-mysql-host"
  port: "3306"
  name: "himarket_db"
  username: "himarket_user"
  password: "your_password"
```

#### 💻 命令行配置

```bash
helm install himarket ./deploy/helm -n himarket --create-namespace \
  --set mysql.enabled=false \
  --set database.host=your-mysql-host \
  --set database.name=himarket_db \
  --set database.username=himarket_user \
  --set database.password=your_password
```

## 🚀 部署步骤

### 1. 部署应用

```bash
# 进入 Helm 目录
cd deploy/helm

# 部署到 Kubernetes
helm install himarket . --namespace himarket --create-namespace
```

### 2. 验证部署

```bash
# 查看 Pod 状态（等待所有 Pod 变为 Running）
kubectl get pods -n himarket

# 获取访问地址
kubectl get svc -n himarket
```

## 📝 服务说明

| 服务 | 类型 | 端口        | 说明         |
|------|------|-----------|------------|
| **himarket-frontend** | LoadBalancer | 80 → 8000 | HiMarket 开发者门户，前端服务 |
| **himarket-admin** | LoadBalancer | 80 → 8000 | HiMarket 管理后台，前端服务  |
| **himarket-server** | ClusterIP | 80 → 8080 | HiMarket 后端服务       |

## 🌐 访问应用

### 方式一：LoadBalancer（云环境推荐）

```bash
# 获取外部 IP 地址
kubectl get svc -n himarket

# 等待 EXTERNAL-IP 分配完成，然后访问：
# Frontend: http://<frontend-external-ip>
# Admin: http://<admin-external-ip>
```

### 方式二：NodePort（本地环境推荐）

如果 LoadBalancer 不可用，可以修改服务类型为 NodePort：

```bash
# 修改服务类型为 NodePort
kubectl patch svc himarket-frontend -n himarket -p '{"spec":{"type":"NodePort"}}'
kubectl patch svc himarket-admin -n himarket -p '{"spec":{"type":"NodePort"}}'

# 获取 NodePort 端口
kubectl get svc -n himarket

# 通过节点 IP + NodePort 访问：
# Frontend: http://<node-ip>:<nodeport>
# Admin: http://<node-ip>:<nodeport>
```

## 🗑️ 清理资源

### 1. 卸载应用

```bash
# 卸载 Helm 部署
helm uninstall himarket -n himarket
```

### 2. 清理持久化存储（可选）

如果不再需要数据库数据，可以删除 PersistentVolume：

```bash
# 查看 PV
kubectl get pv | grep himarket

# 删除 MySQL 的 PV（替换为实际的 PV 名称）
kubectl delete pv <pv-name>
```