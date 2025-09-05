<a name="readme-top"></a>
<h1 align="center">
    <img width="406" height="96" alt="image" src="https://github.com/user-attachments/assets/e0956234-1a97-42c6-852d-411fa02c3f01" />
  <br>
  HiMarket AI 开放平台
</h1>

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/higress-group/himarket)

## HiMarket 是什么？

HiMarket 是一个开箱即用的 AI 开放平台解决方案，可以用于构建企业级的 AI 能力市场与开发者生态中心。

它由三大核心组件构成，完美匹配企业内不同角色的需求：

1. AI 开放平台管理后台 (for 管理员/运营)：在这里将底层的模型服务、MCP Server、Agent 等多样化的 AI 能力，以 API 的形式轻松打包成标准化的“AI 产品”，并配上完善的文档、示例，最终一键发布到门户。
2. AI 开放平台门户 (for 开发者/企业内部用户)：门户是面向内外开发者的“店面”。开发者可以在此完成开发者注册、创建消费者、获取凭证、浏览和订阅 AI 产品、在线测试，并清晰地监控自己的调用状态和成本。
3. AI 网关：作为 Higress 社区的子项目，Higress AI 网关承载所有 AI 调用的认证、安全、流控、协议转换以及可观测性等能力。

## 快速入门
### 准备工作

1. **安装 Git**
   https://git-scm.com/downloads
2. **安装 JDK 8 或者更高版本**
3. **安装 Node.js（建议 v20 及以上）**
   https://nodejs.org/
4. **安装 Maven**
   https://maven.apache.org/download.cgi
5. **准备数据库**
   后端服务依赖外部数据库。你需要本地启动数据库（如 MySQL/MariaDB 等），并根据 portal-bootstrap 中的 application.yaml 文件配置数据库连接参数。

### 克隆项目代码

```bash
git clone https://github.com/higress-group/himarket.git
cd himarket
```

### 启动后端 portal-bootstrap

后端服务依赖外部数据库。请参考 `portal-bootstrap/src/main/resources/application.yaml` 配置数据库相关参数。也可以在启动时通过命令行参数覆盖这些配置。

#### 启动方式

先打包：

```bash
mvn clean package
```

再启动：

```bash
java -Ddb.host=xxxx.mysql.rds.aliyuncs.com \
     -Ddb.port=3306 \
     -Ddb.name=xxx \
     -Ddb.username=xxx \
     -Ddb.password=xxx \
     -jar portal-bootstrap/target/portal-bootstrap-1.0-SNAPSHOT.jar
```

### 启动前端项目

#### 1. portal-web/api-portal-admin

```bash
cd portal-web/api-portal-admin
npm install
npm run dev
```

本地访问地址参考控制台输出，一般为 http://localhost:5174

#### 2. portal-web/api-portal-frontend

```bash
cd portal-web/api-portal-frontend
npm install
npm run dev
```

本地访问地址同上，一般为 http://${portal_frontend_domain}:5173

在本地开发过程中，portal_frontend_domain 需要配置域名解析（本地修改 /etc/hosts 文件），在 HiMarket 的设计中，门户域名是解析到对应门户 ID 的依据。

### HiMarket 后台管理

#### 注册管理员

访问 `http://localhost:5174`，首次访问注册一个管理员账号。

![](https://github.com/user-attachments/assets/9e5c8350-5a82-489e-827c-c809f5a16d29)

#### 导入 Higress 实例

选择【实例管理】-【网关实例】-【导入网关实例】-【Higress 网关】，导入 Higress 网关实例，以 http://demo.higress.io	为例进行导入，用户名和密码均是 admin。

![](https://github.com/user-attachments/assets/a4876341-595c-497f-bc59-51f36f5ed18a)

#### 创建 Portal 门户

选择【Portal】-【创建 Portal】，创建一个名为 himarket-demo 的门户。

![](https://github.com/user-attachments/assets/5d0b466f-d6d8-4f24-bb23-1133311911f8)

点击门户卡片，进入门户配置，其他配置保留默认选项即可，在 【Setting】-【域名管理】-【绑定域名】中，绑定一个 localhost 域名，用于开发自测。其他菜单在快速入门中可以先不用关注，这里简单介绍下他们的功能：

- Published API Products。管理门户中发布的 API Product。
- Developers。管理门户的 Developer，以及 Developer 关联的 Consumer。
- Settings。
  - 配置门户的基本信息。
  - 控制门户中 Developer 的注册审批是否自动通过、API Product 订阅是否自动通过。
  - 门户支持的三方登录。支持标准的 OIDC 配置，如 Aliyun、Google、Github 等。

#### 创建 API Product

选择【API Products】-【创建 API Product】，创建一个 demo-api 的 API Product。

![](https://github.com/user-attachments/assets/d3d1f0fe-124b-4397-855d-c704e64c1f32)

API Product 的初始状态为“待配置”，需要进行 Link API、发布到门户等操作。

#### 关联 API

![](https://github.com/user-attachments/assets/3431e4cc-683c-4cb5-9adb-e463bc540c1d)

关联一个网关的 MCP 服务，数据源来自于 Higress MCP 服务管理。API Config 也会自动同步 Higress 中的配置。

#### Usage Guide

![](https://github.com/user-attachments/assets/bf4bd230-57ab-4b91-b309-1e0cbf88ef21)

可以在使用指南中编辑自定义的文档信息。

#### 发布到门户

在 API Product 准备就绪后，可以选择发布到指定的门户。

![](https://github.com/user-attachments/assets/acccaf9e-baa5-46d4-a6c3-c2386845941e)

至此，一个 Higress 的 MCP Server 成功发布到了门户。

### HiMarket 门户

HiMarket 门户是多租户设计，通过域名来识别不同的门户实例。

门户会有一个默认分配的域名，但域名解析需要用户自己完成，例如自动分配了 portal-68ac4564bdb292ee9261ff4a.api.portal.local 域名，需要将其解析到 api-portal-frontend 对应的 IP 上。

由于刚刚已经额外配置了 localhost 域名给测试门户，所以也可以直接通过 localhost:5173 访问前台。

#### 注册 Developer 开发者

![](https://github.com/user-attachments/assets/6d071297-4f9e-4518-919a-34591c3a9fb4)

由于门户之前在设置中未打开自动审批，注册账号后需要等待管理员后台审批开发者通过，审批通过后，方可使用注册的账号在前台登录。

![](https://github.com/user-attachments/assets/79d5abaa-3eaf-4d58-aa84-58aed5784050)

访问 MCP 门户可以看到刚刚发布的 MCP Server

![](https://github.com/user-attachments/assets/d50d0f61-d7b0-4a4b-93b0-7fe259950992)

![](https://github.com/user-attachments/assets/7eabf879-f155-4fd1-b516-9072915a182f)

#### 创建 Consumer 消费者

在 AI 开放平台的设计中，消费者 Developer 代表一般的用户身份，而用户需要持有对应的凭证才可以申请订阅 API Product，而凭证这一概念，在 AI 开放平台中称之为 Consumer 消费者，Developer 与 Consumer 是一对多的关联。

![](https://github.com/user-attachments/assets/e98be911-1889-4aa7-973b-9f2222d14c3f)

创建消费者之后，即可申请 API Product 的订阅

![](https://github.com/user-attachments/assets/17d79309-3b58-42f1-8f96-163b6df23853)

门户的默认配置中，订阅的审批是默认关闭的，即开发者申请后会自动审批通过。

#### 发起调用

携带消费者的凭证，配置门户中 MCP Server 的连接地址，即可发起对 MCP Server 的调用。

### 阿里云开箱即用
阿里云计算巢支持了该项目开箱即用版本，可以使用下面链接一键部署社区版：

[![Deploy on AlibabaCloud ComputeNest](https://service-info-public.oss-cn-hangzhou.aliyuncs.com/computenest.svg)](https://computenest.console.aliyun.com/service/instance/create/cn-hangzhou?type=user&ServiceName=HiMarket%20AI%20%E5%BC%80%E6%94%BE%E5%B9%B3%E5%8F%B0)

## 社区

### 交流群

![image](https://github.com/user-attachments/assets/2092b427-33bb-462d-a22a-7c369e81c572)

### 技术分享

微信公众号：

![](https://img.alicdn.com/imgextra/i1/O1CN01WnQt0q1tcmqVDU73u_!!6000000005923-0-tps-258-258.jpg)

### 关联仓库

- Higress：https://github.com/alibaba/higress
- Higress 控制台：https://github.com/higress-group/higress-console

### 贡献者

<a href="https://github.com/higress-group/himarket/graphs/contributors">
  <img alt="contributors" src="https://contrib.rocks/image?repo=higress-group/himarket"/>
</a>

### Star History

[![Star History](https://api.star-history.com/svg?repos=higress-group/himarket&type=Date)](https://star-history.com/#higress-group/himarket&Date)

<p align="right" style="font-size: 14px; color: #555; margin-top: 20px;">
    <a href="#readme-top" style="text-decoration: none; color: #007bff; font-weight: bold;">
        ↑ 返回顶部 ↑
    </a>
</p>

