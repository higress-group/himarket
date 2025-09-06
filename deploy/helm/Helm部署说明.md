# HiMarket AI 开放平台 Helm 部署指南

## 📋 项目说明

Helm 会部署三个应用，分别为：
- `himarket-server`：HiMarket AI 开放平台的后端服务，运行在 8080 端口；
- `himarket-admin`：HiMarket AI 开放平台管理后台，管理员通过此界面配置 Portal；
- `himarket-frontend`：HiMarket AI 开放平台的前台服务，用户通过此界面浏览和使用 API。

服务类型说明：

`himarket-admin` 和 `himarket-frontend` 默认为 LoadBalancer 类型服务，适用于云环境（阿里云 ACK、AWS EKS 等）。如果您的环境不支持 LoadBalancer（如本地 minikube、自建集群），可以使用 NodePort 或端口转发方式访问。后台配置好 HiMarket 后，将域名解析到 himarket-frontend 服务的访问地址，用户就可以通过域名访问前台站点。

**镜像说明：**

默认使用公开镜像仓库的镜像：
- `himarket-server`: `opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/api-portal-server:1.0.0`
- `himarket-admin`: `opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/api-portal-admin:1.0.0`
- `himarket-frontend`: `opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/api-portal-frontend:1.0.0`

如需使用自定义镜像，可修改 `values.yaml` 中的镜像配置，或通过 `--set` 参数指定：
```bash
helm install himarket . --namespace himarket --create-namespace \
  --set server.image.repository=your-custom-server-image \
  --set admin.image.repository=your-custom-admin-image \
  --set frontend.image.repository=your-custom-frontend-image
```

## ⚠️ 重要说明

**本项目使用外置 MySQL 数据库，部署前必须先准备数据库并在 values.yaml 中配置连接信息！**

## 🛠️ 部署前准备

### 数据库要求

准备一个 MySQL 数据库实例，建议配置：
- **数据库名**: 如`portal_db`
- **用户名**: 如`portal_user`  
- **密码**: 自定义密码

## 🚀 部署步骤

### 1. 配置数据库连接

编辑 `values.yaml` 文件，更新数据库配置：

```yaml
database:
  host: "your-mysql-host"                            # 数据库地址
  port: "3306"                                       # 数据库端口
  name: "portal_db"                                  # 数据库名
  username: "portal_user"                            # 数据库用户名
  password: "your_password"                          # 数据库密码
```

或者通过命令行参数直接指定：

```bash
helm install himarket . --namespace himarket --create-namespace \
  --set database.host=your-mysql-host \
  --set database.port=3306 \
  --set database.name=portal_db \
  --set database.username=portal_user \
  --set database.password=your_password
```

### 2. 部署应用

```bash
# 进入 Helm 目录
cd deploy/helm

# 部署到 Kubernetes
helm install himarket . --namespace himarket --create-namespace
```

### 3. 验证部署

```bash
# 查看 Pod 状态（等待所有 Pod 变为 Running）
kubectl get pods -n himarket

# 获取访问地址
kubectl get svc -n himarket
```

## 📝 服务说明

| 服务 | 类型 | 端口          | 说明         |
|------|------|-------------|------------|
| **himarket-frontend** | LoadBalancer | 80 → 8000   | HiMarket 开发者门户，前端服务 |
| **himarket-admin** | LoadBalancer | 80 → 8000   | HiMarket 管理后台，前端服务  |
| **himarket-server** | ClusterIP | 8080 → 8080 | HiMarket 后端服务       |

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