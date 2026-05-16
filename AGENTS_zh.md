# AGENTS.md

<p align="center">
  <a href="AGENTS.md">English</a> | <b>简体中文</b>
</p>

**始终使用简体中文回复。**

## 1. 项目概览

HiMarket 是一个 AI 开放平台，提供 API 产品管理、开发者门户、AI 对话、云 IDE（HiCoding）、MCP Server 托管等能力。

本仓库是前后端一体的 monorepo，由后端三层模块、两个前端应用、项目文档和辅助脚本组成。开始修改前，先判断任务涉及哪个模块，再进入对应目录和规范文档。
后端改动通常集中在 `himarket-server/`，但实体、仓储或数据库迁移可能涉及 `himarket-dal/` 和 `himarket-bootstrap/`；前端改动需要先区分管理后台和开发者门户。

| 模块 | 路径 | 职责 |
| ---- | ---- | ---- |
| 后端数据层 | `himarket-dal/` | 存放 Entity、Repository、数据库字段转换器、枚举和数据层支持代码 |
| 后端业务层 | `himarket-server/` | 存放 Controller、Service、DTO、权限、产品导入、网关、Nacos、MCP 等核心业务代码 |
| 后端启动层 | `himarket-bootstrap/` | 存放 Spring Boot 启动入口、运行配置、打包配置和 Flyway 数据库迁移脚本 |
| 管理后台 | `himarket-web/himarket-admin/` | 管理员使用的前端应用，覆盖产品管理、网关配置、用户管理、门户管理等后台能力 |
| 开发者门户 | `himarket-web/himarket-frontend/` | 开发者使用的前端应用，覆盖产品浏览、订阅、AI 对话、HiCoding 等门户能力 |
| 项目文档 | `docs/` | 存放架构设计、专题设计、代码规范和外部源码索引 |
| 辅助脚本 | `scripts/` | 存放本地启动、质量检查、外部仓库初始化等自动化脚本 |

## 2. 重点文档

修改代码时必须遵守 `docs/standards/` 中的对应规范。代码风格、实现约定、接口设计、数据库迁移、前端组织方式和评审标准均以这些规范为准。
如果任务涉及不熟悉的模块，先阅读系统架构或对应规范，再开始修改。这里列出的是最常用入口，专题细则从这些入口继续跳转。

| 文档 | 路径 |
| ---- | ---- |
| 代码规范总入口 | `docs/standards/README.md` |
| 后端规范入口 | `docs/standards/backend/README.md` |
| 前端规范入口 | `docs/standards/frontend/README.md` |
| 系统架构 | `docs/ARCHITECTURE.md` |
| 贡献指南 | `CONTRIBUTING.md` / `CONTRIBUTING_zh.md` |
| 用户指南 | `USER_GUIDE.md` / `USER_GUIDE_zh.md` |

## 3. 执行原则

这些原则用于约束 Agent 的实际操作方式。默认保持改动范围收敛，优先复用现有模式，并在影响行为时完成必要验证。

- 修改前先阅读相关规范和同层现有实现。
- 只修改任务相关文件，不做无关重构。
- 不回滚用户或其他 Agent 的已有改动，除非用户明确要求。
- 发现文档和代码不一致时，以代码和最新规范为准，并在必要时说明差异。
- 涉及接口、数据库、权限、导入、网关、Nacos、MCP、配置解析时，优先做可验证闭环。
- 代码修改完成后，依据 `docs/standards/` 中对应的前端或后端规范检查改动是否合理，并说明仍存在的风险、偏离规范之处或未验证假设。

## 4. 命令指引

以下命令覆盖最常见的编译、启动和质量检查场景。选择命令时按改动范围执行最小必要检查；提交前或跨模块改动后再使用全量检查。

| 命令 | 用途 | 适用场景 |
| ---- | ---- | -------- |
| `make compile` | 快速编译后端，跳过测试和格式检查 | 确认 Java 改动是否能编译 |
| `./scripts/run.sh` | 编译并启动后端服务 | 需要本地接口验证 |
| `./scripts/code-check.sh` | 运行后端和两个前端的完整质量检查 | 提交前或跨模块改动后 |
| `./scripts/code-check.sh backend` | 只检查后端 Java 代码 | 后端单模块改动 |
| `./scripts/code-check.sh frontend` | 只检查开发者门户前端 | `himarket-web/himarket-frontend/` 改动 |
| `./scripts/code-check.sh admin` | 只检查管理后台前端 | `himarket-web/himarket-admin/` 改动 |
| `git diff --check` | 检查 diff 中的空白和格式问题 | 纯文档或轻量改动后 |

更多命令以 `Makefile` 和 `scripts/` 为准。

## 5. 操作限制

以下限制优先级高于一般执行习惯，用于避免破坏用户工作区、泄露敏感信息或让命令卡住。遇到不确定的破坏性操作时，先向用户确认。

- 禁止 HEREDOC（`<<EOF`、`<<'EOF'` 等）。
- 禁止使用可能等待输入的交互式命令。
- 禁止未经用户明确要求执行破坏性命令。
- 禁止未经用户明确要求修改数据库数据。
- 禁止泄露完整密钥、Token、密码、Authorization header 或连接串。
