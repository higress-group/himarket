# Docker 部署指南

<cite>
**本文档引用文件**  
- [docker-compose.yml](file://deploy/docker/docker-compose.yml)
- [Docker部署说明.md](file://deploy/docker/Docker部署说明.md)
- [application.yaml](file://portal-bootstrap/src/main/resources/application.yaml)
- [api-portal-admin/Dockerfile](file://portal-web/api-portal-admin/Dockerfile)
- [api-portal-frontend/Dockerfile](file://portal-web/api-portal-frontend/Dockerfile)
</cite>

## 目录
1. [项目说明](#项目说明)  
2. [快速部署](#快速部署)  
3. [服务配置详解](#服务配置详解)  
4. [自定义配置](#自定义配置)  
5. [本地构建与部署](#本地构建与部署)  
6. [环境变量与配置覆盖](#环境变量与配置覆盖)  
7. [日志查看与监控建议](#日志查看与监控建议)  
8. [常见问题排查](#常见问题排查)  

## 项目说明

AI 开放平台采用微服务架构，通过 Docker Compose 实现多容器协同部署。系统由四个核心服务组成，分别为数据库、后端服务、管理后台和前端门户，各服务职责如下：

- **mysql**: 提供持久化数据存储，使用 MariaDB 兼容的 MySQL 镜像；
- **himarket-server**: 后端业务逻辑服务，基于 Spring Boot 构建，提供 RESTful API；
- **himarket-admin**: 管理后台界面，供管理员进行门户配置与管理；
- **himarket-frontend**: 前台用户门户，供开发者浏览 API 产品并申请使用。

所有服务通过 `docker-compose.yml` 文件统一编排，实现依赖管理、网络互通与端口映射。

**Section sources**  
- [Docker部署说明.md](file://deploy/docker/Docker部署说明.md#L1-L10)

## 快速部署

### 使用公开镜像一键部署

#### 1. 创建并配置 `docker-compose.yml`

将以下内容保存至项目 `deploy/docker/` 目录下：

```yaml
version: '3'
services:
  mysql:
    image: opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/mysql:latest
    container_name: mysql
    environment:
      - MYSQL_ROOT_PASSWORD=123456
      - MYSQL_DATABASE=portal_db
      - MYSQL_USER=portal_user
      - MYSQL_PASSWORD=portal_pass
    ports:
      - "3306:3306"
    volumes:
      - ./mysql/data:/var/lib/mysql
    restart: always

  himarket-server:
    image: himarket-server:latest
    container_name: himarket-server
    environment:
      - DB_HOST=mysql
      - DB_PORT=3306
      - DB_NAME=portal_db
      - DB_USERNAME=portal_user
      - DB_PASSWORD=portal_pass
    ports:
      - "8080:8080"
    depends_on:
      - mysql
    restart: always

  himarket-admin:
    image: himarket-admin:latest
    container_name: himarket-admin
    environment:
      - HIMARKET_SERVER=http://himarket-server:8080
    ports:
      - "5174:8000"
    depends_on:
      - himarket-server
    restart: always

  himarket-frontend:
    image: himarket-frontend:latest
    container_name: himarket-frontend
    environment:
      - HIMARKET_SERVER=http://himarket-server:8080
    ports:
      - "5173:8000"
    depends_on:
      - himarket-server
    restart: always
```

#### 2. 启动服务

```bash
cd deploy/docker
docker-compose up -d
```

#### 3. 验证服务状态

```bash
# 查看容器运行状态
docker-compose ps

# 查看实时日志
docker-compose logs -f
```

#### 4. 访问应用

- **管理后台**: http://localhost:5174  
- **前台门户**: http://localhost:5173  
- **后端服务**: http://localhost:8080  

**Section sources**  
- [Docker部署说明.md](file://deploy/docker/Docker部署说明.md#L12-L50)

## 服务配置详解

### mysql 服务

```yaml
mysql:
  image: opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/mysql:latest
  container_name: mysql
  environment:
    - MYSQL_ROOT_PASSWORD=123456
    - MYSQL_DATABASE=portal_db
    - MYSQL_USER=portal_user
    - MYSQL_PASSWORD=portal_pass
  ports:
    - "3306:3306"
  volumes:
    - ./mysql/data:/var/lib/mysql
  restart: always
```

- **镜像版本**: `mysql:latest`（阿里云镜像仓库）
- **端口映射**: 主机 3306 → 容器 3306
- **环境变量**:
  - `MYSQL_ROOT_PASSWORD`: root 用户密码
  - `MYSQL_DATABASE`: 初始化数据库名 `portal_db`
  - `MYSQL_USER` / `MYSQL_PASSWORD`: 应用连接用户凭证
- **数据卷**: 持久化存储数据库文件至本地 `./mysql/data`

### himarket-server 服务

```yaml
himarket-server:
  image: himarket-server:latest
  container_name: himarket-server
  environment:
    - DB_HOST=mysql
    - DB_PORT=3306
    - DB_NAME=portal_db
    - DB_USERNAME=portal_user
    - DB_PASSWORD=portal_pass
  ports:
    - "8080:8080"
  depends_on:
    - mysql
  restart: always
```

- **镜像版本**: `himarket-server:latest`
- **端口映射**: 主机 8080 → 容器 8080
- **环境变量**: 数据库连接参数，通过服务名 `mysql` 访问
- **依赖关系**: 依赖 `mysql` 服务启动完成

### himarket-admin 与 himarket-frontend 服务

两者配置结构一致，区别仅在于容器名与端口：

```yaml
himarket-admin:
  environment:
    - HIMARKET_SERVER=http://himarket-server:8080
  ports:
    - "5174:8000"

himarket-frontend:
  environment:
    - HIMARKET_SERVER=http://himarket-server:8080
  ports:
    - "5173:8000"
```

- **环境变量**: `HIMARKET_SERVER` 指定后端服务地址（容器内网络）
- **端口映射**: 分别映射至主机 5174 和 5173
- **依赖关系**: 依赖 `himarket-server` 启动

**Section sources**  
- [docker-compose.yml](file://deploy/docker/docker-compose.yml#L1-L51)

## 自定义配置

### 使用外部 MySQL 数据库

若已有数据库，可移除内置 `mysql` 服务并修改连接参数：

```yaml
himarket-server:
  environment:
    - DB_HOST=your.external.db.host
    - DB_PORT=3306
    - DB_NAME=portal_db
    - DB_USERNAME=portal_user
    - DB_PASSWORD=your_secure_password
```

> **注意**: 需确保数据库已创建 `portal_db` 并授权用户访问。

### 修改服务端口

调整 `ports` 字段以避免端口冲突：

```yaml
himarket-frontend:
  ports:
    - "80:8000"  # 使用 80 端口访问

himarket-admin:
  ports:
    - "8090:8000" # 管理后台使用 8090 端口
```

**Section sources**  
- [Docker部署说明.md](file://deploy/docker/Docker部署说明.md#L52-L80)

## 本地构建与部署

### 构建镜像

执行项目根目录下的构建脚本：

```bash
./build.sh
```

该脚本将依次构建 `himarket-server`、`himarket-admin`、`himarket-frontend` 镜像。

### 修改 docker-compose.yml 使用本地镜像

```yaml
services:
  himarket-server:
    image: himarket-server:latest  # 本地构建镜像

  himarket-admin:
    image: himarket-admin:latest

  himarket-frontend:
    image: himarket-frontend:latest
```

### 重新部署

```bash
docker-compose down
docker-compose up -d
```

**Section sources**  
- [Docker部署说明.md](file://deploy/docker/Docker部署说明.md#L82-L100)

## 环境变量与配置覆盖

### 后端服务配置 (`application.yaml`)

```yaml
spring:
  datasource:
    url: jdbc:mariadb://${db.host}:${db.port}/${db.name}?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC
    username: ${db.username}
    password: ${db.password}

db:
  host: YourDBHost
  port: 3306
  name: YourDBName
  username: YourDBUser
  password: YourDBPassword

jwt:
  secret: YourJWTSecret
  expiration: 2h

encryption:
  root-key: portalmanagement
```

- **数据库连接**: 通过 `${}` 占位符从环境变量注入
- **JWT 配置**: `jwt.secret` 用于生成和验证 Token
- **加密密钥**: `encryption.root-key` 用于敏感数据加密

### 多环境配置策略

通过环境变量覆盖 `application.yaml` 中的默认值：

| 环境变量 | 覆盖字段 | 示例值 |
|---------|--------|------|
| `DB_HOST` | `db.host` | `mysql` 或 `192.168.1.100` |
| `DB_PORT` | `db.port` | `3306` |
| `DB_NAME` | `db.name` | `portal_db` |
| `DB_USERNAME` | `db.username` | `portal_user` |
| `DB_PASSWORD` | `db.password` | `portal_pass` |
| `JWT_SECRET` | `jwt.secret` | `mysecretpassword123` |

> **生产环境建议**: 将敏感信息通过 `.env` 文件或 Secrets 管理，避免明文暴露。

**Section sources**  
- [application.yaml](file://portal-bootstrap/src/main/resources/application.yaml#L1-L44)

## 日志查看与监控建议

### 查看容器日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f himarket-server
```

日志格式配置（`application.yaml`）：
```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n
```

### 基础监控建议

1. **容器健康检查**: 可在 `docker-compose.yml` 中添加 `healthcheck` 字段；
2. **资源监控**: 使用 `docker stats` 查看 CPU、内存占用；
3. **日志聚合**: 建议接入 ELK 或 Loki 进行集中管理；
4. **API 监控**: 后端启用 SpringDoc，访问 `/portal/swagger-ui.html` 查看接口状态。

**Section sources**  
- [application.yaml](file://portal-bootstrap/src/main/resources/application.yaml#L40-L44)

## 常见问题排查

### 服务启动失败

- **现象**: `docker-compose up` 报错或容器反复重启
- **排查步骤**:
  1. 检查端口是否被占用：`lsof -i :3306` 或 `netstat -an | grep 3306`
  2. 查看日志：`docker-compose logs mysql` 或 `docker-compose logs himarket-server`
  3. 确保镜像存在：`docker images | grep himarket`

### 数据库连接超时

- **现象**: `himarket-server` 启动时报 `Connection refused`
- **原因**: `mysql` 服务未就绪或网络不通
- **解决方案**:
  - 确认 `depends_on` 已配置
  - 检查 `DB_HOST` 是否为 `mysql`（Docker 内部服务名）
  - 手动测试连接：`docker exec -it mysql mysql -uportal_user -pportal_pass`

### 前端资源加载失败

- **现象**: 页面空白或报 404
- **原因**: `himarket-frontend` 静态资源未正确挂载
- **检查项**:
  - 确认 `Dockerfile` 中 `COPY dist/ /app` 路径正确
  - 检查 `nginx.conf` 配置是否正确代理
  - 查看容器内文件：`docker exec -it himarket-frontend ls /app`

### JWT 验证失败

- **现象**: 登录后无法访问受保护接口
- **原因**: `jwt.secret` 不一致
- **解决方案**:
  - 确保 `application.yaml` 与部署环境中的 `JWT_SECRET` 一致
  - 重启 `himarket-server` 服务

**Section sources**  
- [Docker部署说明.md](file://deploy/docker/Docker部署说明.md#L101-L180)