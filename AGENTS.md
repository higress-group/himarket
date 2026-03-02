**ALWAYS RESPOND IN CHINESE-SIMPLIFIED**

## 本地开发环境

### 数据库访问

本地开发时，数据库连接信息可以通过以下任意方式提供（优先级从高到低）：
- shell 环境变量（直接 export 或写入 `~/.zshrc` / `~/.bashrc`）
- `~/.env` 文件（`scripts/run.sh` 启动时会自动 source）

需要包含以下变量：
- `DB_HOST`：数据库地址
- `DB_PORT`：端口（默认 3306）
- `DB_NAME`：数据库名
- `DB_USERNAME`：用户名
- `DB_PASSWORD`：密码

查询数据库时，使用 mysql CLI（环境变量已在 shell 中或通过 `~/.env` 加载）：

```bash
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -e "YOUR_SQL_HERE"
```

注意事项：
- 只执行 SELECT 查询，除非用户明确要求修改数据
- 不要在回复中展示完整的密码、密钥等敏感字段
- 数据库 schema 由 Flyway 管理，迁移文件在 `himarket-bootstrap/src/main/resources/db/migration/`

### 启动后端服务

使用 `scripts/run.sh` 脚本编译并启动 Java 后端：

```bash
./scripts/run.sh
```

脚本会自动完成：加载环境变量 → 优雅关闭旧进程 → 编译打包 → 后台启动 jar → 轮询等待就绪。
脚本退出码为 0 表示启动成功，非 0 表示失败（编译错误或启动超时）。

### 修改代码后的验证（可选）

以下场景建议主动进行"重启 → 接口验证"闭环，而不是只改代码就结束：
- 用户明确要求调试某个 bug 或修复接口问题
- 新增或修改了 REST/WebSocket 接口
- 用户要求端到端验证

#### 判断是否需要重启

修改 Java 源文件、Spring 配置（`application.yml`）、`pom.xml`、Flyway 迁移文件后需要重启。修改前端、文档、脚本等不需要。

#### 验证流程

1. `./scripts/run.sh` 重启，确认退出码为 0
2. 用 curl 调用相关接口，检查返回结果
3. 如果涉及数据变更，用 mysql CLI 查询确认
4. 验证失败时读取 `~/himarket.log` 排查，修复后重试

### API 接口测试

后端运行在 `http://localhost:8080`，接口路径不带 `/portal` 前缀。使用 JWT Bearer Token 认证。

接口返回格式为 `{"code":"SUCCESS","data":{...}}`，token 在 `data.access_token` 中。

#### 获取管理员 Token（后台管理）

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/admins/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.data.access_token')
```

#### 获取开发者 Token（前台门户）

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/developers/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"123456"}' | jq -r '.data.access_token')
```

#### 带认证请求示例

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/your-endpoint | jq .
```

#### WebSocket 接口验证

对于 WebSocket 接口，使用 `websocat` 工具：

```bash
websocat -H "Authorization: Bearer $TOKEN" ws://localhost:8080/your-ws-endpoint
```

#### 认证注解说明

接口上的注解决定了需要哪种角色的 token：
- `@AdminAuth`：需要管理员 token
- `@DeveloperAuth`：需要开发者 token
- `@AdminOrDeveloperAuth`：两种都可以
- 无注解：无需认证

Token 有效期为 7 天。Swagger 文档：`http://localhost:8080/portal/swagger-ui.html`

### 应用日志

本地运行时日志文件位于 `~/himarket.log`。排查后端问题时应主动读取该日志。
