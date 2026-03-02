---
name: start-himarket
description: 启动 himarket 本地开发环境，包括后端服务、管理台前端和开发者门户前端。当用户需要启动项目、本地开发调试、运行服务时使用此 skill。
---

# 启动 Himarket 开发环境

## 环境要求

| 依赖 | 版本要求 | 检查命令 |
|------|----------|----------|
| JDK | 17+ | `java -version` |
| Maven | 3.6+ | `mvn -version` |
| Node.js | 20.19+ 或 22.12+ | `node -v` |

## 一、配置数据库连接

启动前需配置以下环境变量，指向你的 MySQL 数据库：

```bash
export DB_HOST=<数据库地址>
export DB_PORT=<端口，默认3306>
export DB_NAME=<数据库名>
export DB_USERNAME=<用户名>
export DB_PASSWORD=<密码>
```

## 二、编译后端

```bash
mvn clean install -DskipTests
```

## 三、启动后端

```bash
java \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  -jar himarket-bootstrap/target/himarket-bootstrap-1.0-SNAPSHOT.jar
```

启动成功标志：
```
Tomcat started on port 8080
Started HiMarketApplication in X.XXX seconds
```

## 四、启动前端

在两个终端分别执行：

```bash
# 终端1：管理台
cd himarket-web/himarket-admin && npm install && npm run dev
```

```bash
# 终端2：开发者门户
cd himarket-web/himarket-frontend && npm install && npm run dev
```

## 服务访问地址

| 服务 | 地址 |
|------|------|
| 后端 API | http://localhost:8080 |
| API 文档 | http://localhost:8080/portal/swagger-ui.html |
| 开发者门户 | http://localhost:5173 |
| 管理台 | http://localhost:5174 |

## 常见问题

### Java 版本不对

多版本共存时指定 Java 17：
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)  # macOS
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk     # Linux
```

### Node.js 版本过低

需要 20.19+ 或 22.12+，使用 nvm 切换：
```bash
nvm use 22
```

### 模块访问错误

启动时必须添加 `--add-opens` 参数，参数已配置在 `.mvn/jvm.config` 中。
