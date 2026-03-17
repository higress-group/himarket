<a name="readme-top"></a>

<div align="center">
  <img width="406" height="96" alt="HiMarket Logo" src="https://github.com/user-attachments/assets/e0956234-1a97-42c6-852d-411fa02c3f01" />

  <h1>HiMarket AI 开放平台</h1>

  <p align="center">
    <a href="README.md">English</a> | <b>简体中文</b>
  </p>

  <p>
    <a href="https://github.com/higress-group/himarket/blob/main/LICENSE">
      <img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License" />
    </a>
    <a href="https://github.com/higress-group/himarket/releases">
      <img src="https://img.shields.io/github/v/release/higress-group/himarket" alt="Release" />
    </a>
    <a href="https://github.com/higress-group/himarket/stargazers">
      <img src="https://img.shields.io/github/stars/higress-group/himarket" alt="Stars" />
    </a>
    <a href="https://deepwiki.com/higress-group/himarket">
      <img src="https://deepwiki.com/badge.svg" alt="Ask DeepWiki" />
    </a>
  </p>
</div>

## HiMarket 是什么？

HiMarket 是基于 Higress AI 网关构建的企业级 AI 开放平台，帮助企业构建私有 AI 能力市场，统一管理和分发 LLM、MCP Server、Agent、Agent Skill 等 AI 资源。平台将分散的 AI 能力封装为标准化的 API 产品，支持多版本管理和灰度发布，内置 Skills 市场供开发者浏览和安装 Agent Skill，提供 HiChat AI 对话和 HiCoding 在线编程等自助式开发者体验，并具备安全管控、观测分析、计量计费等完整的企业级运营能力，让 AI 资源的共享和复用变得高效便捷。

<div align="center">
  <b>核心能力</b>
</div>

| 类别 | 功能 | 说明 |
|------|------|------|
| **AI 能力市场** | Model 市场 | 接入各类 Model，提供内容安全、Token 限流等防护能力 |
| | MCP 市场 | 接入各平台 MCP Server，支持外部 API 转换为标准 MCP Server |
| | Agent 市场 | 打包上架 Agent 应用，对接 AgentScope 等 Agent 构建平台 |
| | Skills 市场 | 上传和分发 Agent Skill，开发者可浏览、订阅和安装 Skill 包 |
| **AI 体验中心** | HiChat 对话调试 | 单模型对话与多模型对比，结合 MCP 进行工具调用测试，支持联网问答等增强功能 |
| | HiCoding 在线编程 | 集成安全沙箱环境，支持 Vibe Coding 和人机协作开发，实时查看文件变更与代码预览 |
| **企业级管理** | 产品管理 | 认证鉴权、流量控制、调用配额等防护能力 |
| | 观测分析 | 全链路监控、调用追踪、热力图、异常告警 |
| | 计量计费 | 基于 Token 调用次数、自动统计成本费用 |
| | 版本管理 | 多版本并行、灰度发布、快速回滚 |
| **灵活定制** | 门户品牌 | 自定义域名、Logo、配色、页面布局 |
| | 身份认证 | 支持接入第三方 OIDC，对接企业用户身份体系 |
| | 审批流程 | 按照订阅、产品订阅等场景可配置自动/人工审批 |
| | 产品目录 | 自定义类别标签，支持浏览、筛选、搜索 |

## 系统架构

<div align="center">
  <img src="https://github.com/user-attachments/assets/4e01fa52-dfb3-41a4-a5b6-7a9cc79528e4" alt="HiMarket 系统架构" width="700px" />
  <br/>
  <b>系统架构</b>
</div>

HiMarket 系统架构分为三层：

1. **基础设施**：由 AI 网关、API 网关、Higress 和 Nacos 组成。HiMarket 基于这些组件对底层 AI 资源进行抽象封装，形成可对外开放的标准 API 产品。
2. **AI 开放平台后台**：面向管理员的管理平台，管理员可以创建和定制门户，管理 MCP Server、Model、Agent、Agent Skill 等 AI 资源，例如设置鉴权策略、订阅审批流程等。后台还提供可观测大盘，帮助管理员实时了解 AI 资源的使用和运行状态。
3. **AI 开放平台前台**：面向外部开发者的门户站点，也称为 AI 市场或 AI 中台，提供一站式自助服务，开发者可以完成身份注册、凭证申请、浏览订阅产品、在线调试等操作，还可以通过 HiChat 与模型和 MCP Server 交互对话，通过 HiCoding 在安全沙箱中进行在线 AI 编程。

<table>
  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/e7a933ea-10bb-457e-a082-550e939a1b58" width="500px" height="200px" alt="HiMarket 管理后台"/>
      <br />
      <b>管理后台</b>
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/41382502-12fe-45c4-9708-8dd7a103cb73" width="500px" height="200px" alt="HiMarket 开发者门户"/>
      <br />
      <b>开发者门户</b>
    </td>
  </tr>
</table>

## 快速开始

<details open>
<summary><b>方式一：本地搭建</b></summary>

<br/>

**环境依赖：** JDK 17、Node.js 18+、Maven 3.6+、MySQL 8.0+

**启动后端：**
```bash
# 构建项目
mvn clean package -DskipTests

# 启动后端服务
java --add-opens java.base/java.util=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     -Ddb.host=${DB_HOST} \
     -Ddb.port=${DB_PORT} \
     -Ddb.name=${DB_NAME} \
     -Ddb.username=${DB_USERNAME} \
     -Ddb.password=${DB_PASSWORD} \
     -jar himarket-bootstrap/target/himarket-bootstrap-1.0-SNAPSHOT.jar

# 后端 API 地址：http://localhost:8080
```

**启动前端：**
```bash
# 启动管理后台
cd himarket-web/himarket-admin
npm install
npm run dev
# 管理后台地址：http://localhost:5174

# 启动开发者门户
cd himarket-web/himarket-frontend
npm install
npm run dev
# 开发者门户地址：http://localhost:5173
```

</details>

<details>
<summary><b>方式二：Docker Compose</b></summary>

<br/>

**环境依赖：** Docker、Docker Compose

**脚本部署：** 使用交互式 `install.sh` 脚本一键部署全栈服务（HiMarket、Higress、Nacos、MySQL），脚本会引导完成所有配置。

```bash
git clone https://github.com/higress-group/himarket.git
cd himarket/deploy/docker
./install.sh
```

**AI 部署（推荐）：** 如果担心部署过程中遇到环境兼容性等问题，推荐使用 Cursor、Qoder、Claude Code 等 AI Coding 工具进行部署，AI 可以自动识别和解决环境问题。clone 项目后在 AI 工具中输入：

> 阅读 deploy 目录下的部署文档，帮我用 Docker Compose 部署 HiMarket

详细文档请参考 [部署文档](./deploy/DEPLOYMENT_zh.md)。

**部署完成后的服务地址：**
- 管理后台：http://localhost:5174
- 开发者门户：http://localhost:5173
- 后端 API：http://localhost:8081

**卸载：**
```bash
./install.sh --uninstall
```

</details>

<details>
<summary><b>方式三：Helm Chart</b></summary>

<br/>

**环境依赖：** kubectl（已连接 K8s 集群）、Helm

**脚本部署：** 使用交互式 `install.sh` 脚本将 HiMarket 部署到 Kubernetes 集群，脚本会引导完成所有配置。

```bash
git clone https://github.com/higress-group/himarket.git
cd himarket/deploy/helm
./install.sh
```

**AI 部署（推荐）：** 如果担心部署过程中遇到环境兼容性等问题，推荐使用 Cursor、Qoder、Claude Code 等 AI Coding 工具进行部署，AI 可以自动识别和解决环境问题。clone 项目后在 AI 工具中输入：

> 阅读 deploy 目录下的部署文档，帮我用 Helm Chart 部署 HiMarket 到 K8s 集群

详细文档请参考 [部署文档](./deploy/DEPLOYMENT_zh.md)。

**卸载：**
```bash
./install.sh --uninstall
```

</details>

<details>
<summary><b>方式四：云平台部署（阿里云）</b></summary>

<br/>

阿里云计算巢支持该项目的开箱即用版本，可一键部署社区版：

[![Deploy on AlibabaCloud ComputeNest](https://service-info-public.oss-cn-hangzhou.aliyuncs.com/computenest.svg)](https://computenest.console.aliyun.com/service/instance/create/cn-hangzhou?type=user&ServiceId=service-b96fefcb748f47b7b958)

</details>

## 文档

详细的使用说明请参考：

[用户指南](./USER_GUIDE_zh.md)

## 社区

### 加入我们

<table>
  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/e74915bb-abf2-4415-99a3-dd7c61a94670" width="200px"  alt="DingTalk Group"/>
      <br />
      <b>钉钉交流群</b>
    </td>
    <td align="center">
      <img src="https://img.alicdn.com/imgextra/i1/O1CN01WnQt0q1tcmqVDU73u_!!6000000005923-0-tps-258-258.jpg" width="200px"  alt="微信公众号"/>
      <br />
      <b>微信公众号</b>
    </td>
  </tr>
</table>

## 贡献者

感谢所有为 HiMarket 做出贡献的开发者！

<a href="https://github.com/higress-group/himarket/graphs/contributors">
  <img alt="contributors" src="https://contrib.rocks/image?repo=higress-group/himarket"/>
</a>

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=higress-group/himarket&type=Date)](https://star-history.com/#higress-group/himarket&Date)

