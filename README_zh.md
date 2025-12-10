<a name="readme-top"></a>

<div align="center">
  <img width="406" height="96" alt="Himarket Logo" src="https://github.com/user-attachments/assets/e0956234-1a97-42c6-852d-411fa02c3f01" />

  <h1>Himarket AI 开放平台</h1>

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

## 💡 Himarket 是什么？

Himarket 是基于 Higress AI 网关构建的企业级 AI 开放平台，帮助企业构建私有 AI 能力市场，统一管理和分发 LLM、MCP Server、Agent 等 AI 资源。平台将分散的 AI 能力封装为标准化的 API 产品，支持多版本管理和灰度发布，提供自助式开发者门户，并具备安全管控、观测分析、计量计费等完整的企业级运营能力，让 AI 资源的共享和复用变得高效便捷。

<div align="center">
  <img src="https://github.com/user-attachments/assets/db49ea33-c914-424d-8e3b-4ba75ec7a746" alt="Himarket 核心能力" width="700px" />
  <br/>
  <b>核心能力</b>
</div>

## 🏗️ 系统架构

<div align="center">
  <img src="https://github.com/user-attachments/assets/4e01fa52-dfb3-41a4-a5b6-7a9cc79528e4" alt="Himarket 系统架构" width="700px" />
  <br/>
  <b>系统架构</b>
</div>

Himarket 系统架构分为三层：

1. **基础设施**：由 AI 网关、API 网关、Higress 和 Nacos 组成。Himarket 基于这些组件对底层 AI 资源进行抽象封装，形成可对外开放的标准 API 产品。
2. **AI 开放平台后台**：面向管理员的管理平台，管理员可以创建和定制门户，管理 MCP Server、Model、Agent 等 AI 资源，例如设置鉴权策略、订阅审批流程等。后台还提供可观测大盘，帮助管理员实时了解 AI 资源的使用和运行状态。
3. **AI 开放平台前台**：面向外部开发者的门户站点，也称为 AI 市场或 AI 中台，提供一站式自助服务，开发者可以完成身份注册、凭证申请、浏览订阅产品、在线调试等操作。

<table>
  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/e7a933ea-10bb-457e-a082-550e939a1b58" width="500px" height="200px" alt="Himarket 管理后台"/>
      <br />
      <b>管理后台</b>
    </td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/ba8eca62-92f8-42b7-b28e-58546e9e8821" width="500px" height="200px" alt="Himarket 开发者门户"/>
      <br />
      <b>开发者门户</b>
    </td>
  </tr>
</table>

## 🚀 快速开始

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

使用 `deploy.sh` 脚本完成 Himarket、Higress、Nacos 全栈部署和数据初始化。

```bash
# 克隆项目
git clone https://github.com/higress-group/himarket.git
cd himarket/deploy/docker

# 部署全栈服务并初始化
./deploy.sh install

# 或仅部署 Himarket 服务（不含 Nacos/Higress）
./deploy.sh himarket-only

# 卸载所有服务
./deploy.sh uninstall

# 服务地址
# 管理后台地址：http://localhost:5174
# 开发者门户地址：http://localhost:5173
# 后端 API 地址：http://localhost:8080
```

> 📝 详细的 Docker 部署说明请参考 [Docker 部署文档](./deploy/docker/Docker部署脚本说明.md)

</details>

<details>
<parameter name="new_string"><details>
<summary><b>方式三：Helm Chart</b></summary>

<br/>

使用 `deploy.sh` 脚本将 Himarket 部署到 Kubernetes 集群。

```bash
# 克隆项目
git clone https://github.com/higress-group/himarket.git
cd himarket/deploy/helm

# 部署全栈服务并初始化
./deploy.sh install

# 或仅部署 Himarket 服务（不含 Nacos/Higress）
./deploy.sh himarket-only

# 卸载
./deploy.sh uninstall
```

> 📝 详细的 Helm 部署说明请参考 [Helm 部署文档](./deploy/helm/Helm部署脚本说明.md)

</details>

<details>
<summary><b>方式四：云平台部署（阿里云）</b></summary>

<br/>

阿里云计算巢支持该项目的开箱即用版本，可一键部署社区版：

[![Deploy on AlibabaCloud ComputeNest](https://service-info-public.oss-cn-hangzhou.aliyuncs.com/computenest.svg)](https://computenest.console.aliyun.com/service/instance/create/cn-hangzhou?type=user&ServiceId=service-b96fefcb748f47b7b958)

</details>

## 📖 文档

详细的使用说明请参考：

📘 [用户指南](./USER_GUIDE_zh.md)

## 🌐 社区

### 加入我们

<table>
  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/2092b427-33bb-462d-a22a-7c369e81c572" width="200px"  alt="钉钉交流群"/>
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

## 🏆 贡献者

感谢所有为 Himarket 做出贡献的开发者！

<a href="https://github.com/higress-group/himarket/graphs/contributors">
  <img alt="contributors" src="https://contrib.rocks/image?repo=higress-group/himarket"/>
</a>

## 📈 Star History

[![Star History Chart](https://api.star-history.com/svg?repos=higress-group/himarket&type=Date)](https://star-history.com/#higress-group/himarket&Date)


