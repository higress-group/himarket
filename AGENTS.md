# AGENTS.md

**ALWAYS RESPOND IN CHINESE-SIMPLIFIED**

## 1. 项目概述

HiMarket 是一个 AI 开放平台，提供 API 产品管理、开发者门户、AI 对话、云 IDE（HiCoding）、MCP Server 托管等功能。

本仓库是前后端一体的 monorepo：`himarket-server/`（Spring Boot 3.2.11 / Java 17）+ `himarket-web/`（React 18 / TypeScript / Vite）。

## 2. 快速命令

```bash
make compile                    # 快速编译（跳过测试和格式检查）
make build                      # 完整构建（编译 + 格式检查 + 测试）
make test                       # 运行单元测试
make lint                       # 代码格式检查（Spotless）
make lint-fix                   # 自动修复代码格式
make run                        # 编译并启动后端服务
```

Demo 凭据：管理员 `admin` / `admin` | 开发者 `user` / `123456` | 后端端口：`8080`

## 3. 后端架构（himarket-server）

### 模块依赖

```
himarket-dal (数据层)  ←  himarket-server (业务层)  ←  himarket-bootstrap (启动配置层)
```

严格单向依赖：上层可以依赖下层，下层不能依赖上层。

### 包结构

```
com.alibaba.himarket
├── config/                 # Spring 配置（ACP、SLS、可观测性）
├── controller/             # 26 REST Controllers，按领域组织
│   ├── AdministratorController   /admins        @AdminAuth
│   ├── DeveloperController       /developers    @DeveloperAuth
│   ├── ProductController         /products      @AdminAuth
│   ├── ConsumerController        /consumers     @DeveloperAuth
│   ├── PortalController          /portals       @AdminAuth
│   ├── GatewayController         /gateways      @AdminAuth
│   ├── NacosController           /nacos         @AdminAuth
│   ├── ChatController            /chats         SSE 流式
│   ├── CodingSessionController                  云 IDE
│   ├── McpServerController       /mcp-servers   MCP 管理
│   ├── SkillController           /skills        技能管理
│   ├── WorkerController          /workers       Worker 管理
│   ├── SandboxController         /sandboxes     沙箱管理
│   └── ...
├── core/                   # 横切关注点
│   ├── advice/             # ResponseAdvice（统一响应包装）、ExceptionAdvice
│   ├── annotation/         # @AdminAuth, @DeveloperAuth, @AdminOrDeveloperAuth, @PublicAccess
│   ├── security/           # JWT 过滤器、SecurityContext
│   ├── exception/          # BusinessException + ErrorCode
│   ├── event/              # Spring Events（ProductDeletingEvent 等）
│   └── utils/              # 工具类
├── dto/                    # 请求/响应 DTO
│   ├── params/             # 请求参数
│   └── result/             # 响应结果
├── service/                # 业务逻辑
│   ├── impl/               # Service 实现
│   ├── hichat/             # AI 对话模块（SSE + 多 LLM 策略）
│   ├── hicoding/           # 云 IDE 模块（WebSocket JSON-RPC 2.0）
│   ├── gateway/            # 网关集成（APIG/Apsara/Higress/MSE）
│   ├── mcp/                # MCP Server 管理
│   ├── sandbox/            # 沙箱管理
│   ├── vendor/             # 供应商集成
│   └── task/               # 异步任务
└── (himarket-dal)
    ├── entity/             # JPA 实体
    ├── repository/         # Spring Data JPA Repository
    ├── converter/          # 类型转换器
    └── support/            # 枚举与支持类
```

### 关键约定

- 响应包装：Controller 直接返回业务对象，`ResponseAdvice` 自动包装为 `{"code":"SUCCESS","data":{...}}`，禁止手动包装
- 业务异常：`throw new BusinessException(ErrorCode.XXX, detail)`，`ExceptionAdvice` 统一处理
- 认证注解：`@AdminAuth`(管理员)、`@DeveloperAuth`(开发者)、`@AdminOrDeveloperAuth`(两者皆可)、`@PublicAccess`(无需认证)
- 安全：无状态 JWT，Token 有效期 7 天
- 数据库：Flyway 迁移（`himarket-bootstrap/src/main/resources/db/migration/`），MariaDB/MySQL
- 代码风格：Spotless + Google Java Format（AOSP），编译阶段自动检查
- 事件驱动：Spring Events 实现模块间松耦合（`ProductDeletingEvent`、`PortalDeletingEvent` 等）

> 后端代码规范详见：`himarket-server/BACKEND_CODING_STANDARDS.md`

## 4. 前端架构（himarket-web）

两个独立前端应用，均基于 React 18 + TypeScript + Vite + Ant Design + Tailwind CSS：

| 应用 | 路径 | 说明 |
|------|------|------|
| 开发者门户 | `himarket-web/himarket-frontend/` | 面向开发者的产品浏览、订阅、AI 对话、HiCoding |
| 管理后台 | `himarket-web/himarket-admin/` | 面向管理员的产品管理、网关配置、用户管理 |

技术栈：React Router、Axios、i18next 国际化、Monaco Editor、react-markdown。

> 前端各自有独立的 `package.json`，在对应目录下 `npm install && npm run dev` 启动。
>
> 前端代码规范详见：
> - 开发者门户：`himarket-web/himarket-frontend/FRONTEND_CODING_STANDARDS.md`
> - 管理后台：`himarket-web/himarket-admin/ADMIN_CODING_STANDARDS.md`

## 5. 本地开发及验证流程

### 5.1 数据库访问

数据库连接信息通过以下方式提供（优先级从高到低）：
- shell 环境变量（直接 export 或写入 `~/.zshrc` / `~/.bashrc`）
- `~/.env` 文件（`scripts/run.sh` 启动时会自动 source）

需要包含以下变量：`DB_HOST`, `DB_PORT`(3306), `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`

```bash
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -e "YOUR_SQL_HERE"
```

注意事项：
- 只执行 SELECT 查询，除非用户明确要求修改数据
- 不要在回复中展示完整的密码、密钥等敏感字段

### 5.2 启动后端服务

```bash
./scripts/run.sh
```

脚本自动完成：加载 `~/.env` → 优雅关闭旧进程 → 编译打包 → 后台启动 jar → 轮询等待就绪。
退出码为 0 表示启动成功，非 0 表示失败。

### 5.3 接口验证

后端运行在 `http://localhost:8080`，接口路径不带 `/portal` 前缀。使用 JWT Bearer Token 认证。

**⚠️ curl 验证规范（必须遵守）**：

1. **每个 curl 调用独立执行**：禁止在一个 shell 命令中串联多个 curl 调用。一个命令只做一件事，分步执行、逐步验证。
2. **用临时文件传递数据**：curl 输出写入 `/tmp/hm_*.json`，后续用 `python3 -c` 单独解析。避免在 shell 命令中内联 Python 解析（zsh glob 会把 `['key']` 当文件匹配导致报错）。
3. **Token 获取模板**：
   ```bash
   # Step 1: 登录，结果写文件
   curl -s -X POST http://localhost:8080/admins/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"admin"}' > /tmp/hm_login.json

   # Step 2: 提取 token（独立命令）
   python3 -c "import json; print(json.load(open('/tmp/hm_login.json'))['data']['access_token'])" > /tmp/hm_token.txt
   ```
4. **业务接口调用模板**：
   ```bash
   TOKEN=$(cat /tmp/hm_token.txt)
   curl -s -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/your-endpoint > /tmp/hm_result.json

   # 独立命令解析结果
   python3 -c "import json; print(json.dumps(json.load(open('/tmp/hm_result.json')), indent=2, ensure_ascii=False))"
   ```
5. **禁止事项**：
   - ❌ 在 shell 命令中用管道 `| python3 -c "..."` 内联解析 JSON（zsh 方括号 glob 问题）
   - ❌ 一个命令块中串联多个 curl（一个失败全部中断）
   - ❌ 在 `python3 -c` 中内联大段代码（超过 3-4 行）——会导致执行卡住。**超过 3 行的脚本必须先写入 `/tmp/hm_*.py` 文件，再用 `python3 /tmp/hm_*.py` 执行**

### 5.4 修改代码后的验证

以下场景建议主动进行"重启 → 接口验证"闭环：
- 用户明确要求调试 bug 或修复接口
- 新增或修改了 REST/WebSocket 接口
- 用户要求端到端验证
- 完成 spec 任务的代码开发后

验证流程：`./scripts/run.sh` → curl 调用接口 → mysql 确认数据 → 失败时读 `~/himarket.log`

### 5.5 应用日志

本地运行时日志文件位于 `~/himarket.log`。排查后端问题时应主动读取该日志。

## 6. 质量检查

任何代码修改后，提交前运行：

```bash
./scripts/code-check.sh
```

覆盖后端 Java Spotless + 两个前端的 Prettier / ESLint / TypeScript。

## 7. 创建 Pull Request

创建 PR 前先检查 `.github/PULL_REQUEST_TEMPLATE.md` 是否存在，如存在则按模板格式填写 PR body。

## 8. 外部项目

`reference-projects/` 下的外部项目由开发者自行管理（目录已在 `.gitignore` 中），分为两类。

快速初始化：

```bash
./scripts/setup-repos.sh          # 克隆所有外部仓库（可编辑项目会提示输入 fork 地址）
./scripts/setup-repos.sh nacos    # 只克隆 nacos（只读）
./scripts/setup-repos.sh higress-doc  # 克隆 higress 文档站（交互式输入 fork 地址）
./scripts/setup-repos.sh higress-doc git@github.com:yourname/higress-group.github.io.git
                                  # 直接指定 fork 地址，跳过交互提示
```

### 只读参考

仅供查阅源码，禁止修改。

| 项目 | 本地路径 | 仓库 | 分支 | 何时参考 |
|------|---------|------|------|---------|
| Nacos | `reference-projects/nacos/` | `alibaba/nacos` | `develop` | 需要理解 `nacos-maintainer-client` API 的服务端实现、排查与 Nacos 交互的问题时 |

### 可编辑关联项目

HiMarket 相关的独立仓库，采用 Fork 工作流：先 fork 上游仓库到自己账号，再 `git clone` 自己的 fork 至本地路径，通过 PR 向上游提交改动。

| 项目 | 本地路径 | 上游仓库 | 分支 | 说明 |
|------|---------|---------|------|------|
| Higress 文档站 | `reference-projects/higress-group.github.io/` | `higress-group/higress-group.github.io` | `ai` | Higress 官网与文档，基于 Astro + Starlight |

> 添加新项目：在对应表格追加行，clone 至 `reference-projects/` 下。只读参考项目可在 `docs/design-docs/` 下新建 `ref-*.md` 提供详细索引。

## 9. 文档导航

| 文档 | 路径 | 内容 |
|------|------|------|
| 系统架构 | `docs/ARCHITECTURE.md` | 模块设计、层级图、关键流程、数据模型 |
| 贡献指南 | `CONTRIBUTING.md` / `CONTRIBUTING_zh.md` | Fork 工作流、提交规范、PR 要求 |
| 用户指南 | `USER_GUIDE.md` / `USER_GUIDE_zh.md` | 产品使用文档 |
| Nacos 源码索引 | `docs/design-docs/ref-nacos.md` | Nacos 模块架构、HiMarket 集成点映射、API 快速查找 |

## 10. 禁止事项

### Shell 命令

- **禁止使用 HEREDOC 语法**（`<<EOF ... EOF`、`<<'EOF' ... EOF`）：Agent 执行环境不支持 HEREDOC，会导致命令永久挂起。需要传递多行文本时，先写入临时文件，再通过文件执行
- **禁止使用交互式命令**：如 `gh pr create` 不带 `--yes` 或必要参数时可能进入交互模式等待输入
