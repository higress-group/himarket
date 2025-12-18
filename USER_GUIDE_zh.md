# HiMarket 使用指南

本文档面向 HiMarket AI 开放平台的管理员和开发者，提供详细的操作指南和最佳实践。

如果你是**管理员**，本文将指导你完成从导入网关实例、创建门户、发布 API 产品的全流程；如果你是**开发者**，本文将帮助你了解如何注册账号、订阅 API 产品、获取调用凭证并发起实际调用。

通过本指南，你可以快速掌握 HiMarket 的核心功能，实现 AI 能力的高效管理和便捷使用。

## 目录
+ [HiMarket 管理后台](#himarket-管理后台)
    - [注册管理员](#注册管理员)
    - [导入 Higress 实例](#导入-higress-实例)
    - [创建 Portal](#创建-portal)
    - [创建 API Product](#创建-api-product)
    - [关联 API](#关联-api)
    - [编写使用指南](#编写使用指南)
    - [发布到门户](#发布到门户)
+ [HiMarket 开发者门户](#himarket-开发者门户)
    - [访问门户](#访问门户)
    - [注册 Developer 开发者](#注册-developer-开发者)
    - [浏览和订阅 API Product](#浏览和订阅-api-product)
    - [创建 Consumer 消费者](#创建-consumer-消费者)
    - [HiChat](#hichat)

---

## HiMarket 管理后台
HiMarket 管理后台面向管理员和运营人员，用于管理 AI 资源、创建门户、发布 API 产品等。

### 注册管理员
首次访问 HiMarket 管理后台时，需要注册一个管理员账号。

**操作步骤：**

1. 访问管理后台地址：`http://localhost:5174`（以本地开发环境为例）
2. 点击"注册"按钮
3. 填写管理员信息：
    - 用户名
    - 密码
4. 点击"初始化"完成账号创建
5. 使用注册的账号登录管理后台

<div align="center">

![](https://github.com/user-attachments/assets/2caca9f6-5da4-4996-8953-ed6f4647cd12)

</div>

---

### 导入 Higress 实例
在开始创建 API Product 之前，需要先导入 Higress 网关实例，作为 AI 资源的底层网关。

**操作步骤：**

1. 登录管理后台
2. 进入【实例管理】→【网关实例】
3. 点击【导入网关实例】按钮
4. 选择【Higress 网关】
5. 填写 Higress 实例信息：
    - Console地址：Higress 控制台地址（例如：`http://demo.higress.io`）
    - 用户名：Higress 控制台用户名（例如：`admin`）
    - 密码：Higress 控制台密码（例如：`admin`）
    - Gateway地址：Higress 网关服务地址（例如：`http://gateway.higress.io`），MCP Server 和 Model 默认使用域名进行访问，若未配置域名，则使用 Gateway 地址访问
6. 点击【导入】

<div align="center">

![](https://github.com/user-attachments/assets/fca47d51-db11-457c-9f06-91342bb1fdfa)

</div>

**说明：**

+ 支持导入多个 Higress 实例
+ 导入后可在实例列表中查看和管理

<div align="center">

![](https://github.com/user-attachments/assets/61dda900-6f5f-419b-8b61-34a6fd1da514)

</div>

---

### 创建 Portal
Portal 是面向外部开发者的站点，管理员可以创建多个门户，每个门户有独立的配置和 API 产品。

**操作步骤：**

1. 进入【Portal】菜单
2. 点击【创建 Portal】按钮
3. 填写门户基本信息：
    - 门户名称：例如 `himarket-demo`
    - 门户描述：简要描述门户用途
4. 点击【确定】

<div align="center">

![](https://github.com/user-attachments/assets/178d4644-eee2-488e-9cac-f76a7cad9a7f)

</div>

**门户配置说明：**

创建门户后，点击门户卡片进入详细配置页面，主要包含以下功能：

#### 1. Overview（概览）
+ 门户基础信息：名称、描述、登录方式、审批策略等
+ 统计数据：
    - 已发布的 API 产品数量
    - 注册的开发者数量

#### 2. Products（已发布的 API 产品）
+ 管理当前门户中已发布的 API 产品
+ 查看产品详情
+ 下架或更新已发布的产品

#### 3. Developers（开发者管理）
+ 查看和管理在该门户注册的开发者
+ 审批开发者注册申请
+ 管理开发者关联的 Consumer（消费者）
+ 查看开发者的订阅情况

#### 4. Security（安全设置）
用户登录配置：

+ 账号密码登录：传统的用户名密码方式（默认启用）
+ OIDC 登录：支持第三方身份认证（如企业 SSO）

审批流程配置：

+ 开发者注册审批：选择是否自动通过开发者注册申请
    - 自动通过：开发者注册后立即生效
    - 手动审批：需要管理员在后台审批后才能使用
+ API Product 订阅审批：选择是否自动通过订阅申请
    - 自动通过：开发者申请订阅后立即生效
    - 手动审批：需要管理员审批后才能调用 API Product

#### 5. Domain（域名管理）
+ 系统会为每个门户自动分配一个默认域名
+ 可以额外绑定自定义域名（例如本地开发时绑定 `localhost`）

**注意事项：**

+ 域名需要自行配置 DNS 解析或 hosts 文件，指向 himarket-frontend 服务
+ 门户采用多租户设计，通过域名识别不同的门户实例
+ 本地开发时建议绑定 `localhost` 域名便于测试
+ 生产环境建议配置真实的域名和 DNS 解析

---

### 创建 API Product
API Product 是 HiMarket 的核心概念，它将底层的 AI 能力（如 MCP Server、Model、Agent）封装为标准化的 API 产品。

**操作步骤：**

1. 进入【API Products】菜单
2. 点击【创建 API Product】按钮
3. 填写产品信息：
    - 产品名称：例如 `demo-api`
    - 产品类型：选择产品类型（MCP Server、Model API、Agent API 等）
    - 产品描述：简要描述产品功能和用途
    - 产品类别：选择产品类别，类别可用于分类和筛选，支持自定义类别
    - 产品图标：选择产品图标，可使用 URL 链接或上传本地文件
4. 特别说明：Model API 类型的产品可以配置模型参数，如模型名称 Model、Max Tokens、Temperature、是否支持联网搜索等，模型参数用于在 HiMarket 前台调试模型时使用，如果缺失，可能导致模型调用失败
5. 点击【确定】

<div align="center">

![](https://github.com/user-attachments/assets/59f94af9-c28d-4313-b17a-79319bbeda8f)

</div>

6. 创建成功后，进入产品详情页

<div align="center">

![](https://github.com/user-attachments/assets/f10eb08d-3e4e-4c4f-ae8f-e9af04cf1cdf)

</div>

**产品状态说明：**

新创建的 API Product 初始状态为 **"待配置"**，需要完成以下操作才能发布：

1. **关联 API**：将产品与实际的 API 资源关联
2. **配置使用指南**：编写产品的使用文档
3. **发布到门户**：将产品发布到指定的门户供开发者订阅

---

### 关联 API
创建 API Product 后，需要将其与实际的 API 资源关联，建立产品与底层服务的映射关系。

**操作步骤：**

1. 进入 API Product 详情页
2. 点击【Link API】标签页
3. 选择 API 资源类型：
    - MCP Server：来自 Higress 或 AI 网关（企业版 Higress）的 MCP 服务
    - Model API：来自 Higress 或 AI 网关的模型服务
    - Agent API：来自 AI 网关的 Agent 应用
4. 从列表中选择要关联的 API 资源，以 Model API 为例，关联 Higress 上的 AI 路由
5. 点击【关联】

<div align="center">

![](https://github.com/user-attachments/assets/e5bc4963-c7b9-4bbd-9a0e-55eb06cc91c5)

</div>

7. 关联成功后，API Product 状态变为 **"待发布"**，此时 API Product 可以发布到 Portal 上，Link API 标签页下会显示 API 配置信息

<div align="center">

![](https://github.com/user-attachments/assets/845935ee-79c4-45dc-9cd0-e49ab840a505)

</div>

---

### 编写使用指南
为 API Product 编写详细的使用指南，帮助开发者快速理解和使用 API。

**操作步骤：**

1. 进入 API Product 详情页
2. 点击【Usage Guide】标签页
3. 使用 Markdown 编辑器编写使用文档：
    - API Product 简介：API 产品的功能和使用场景
    - 认证方式：如何获取和使用凭证
    - 请求示例：提供完整的请求示例代码
    - 响应示例：展示 API 返回的数据格式
    - 错误码说明：列出常见错误码和处理方法
    - 最佳实践：使用建议和注意事项
4. 支持实时预览，确保格式正确
5. 点击【保存】

<div align="center">

![](https://github.com/user-attachments/assets/5a35a3ce-4dd3-4644-a7b7-f4ba64c5a387)

</div>

---

### 发布到门户
当 API Product 配置完成后，即可发布到指定的门户供开发者使用。

**操作步骤：**

1. 进入 API Product 详情页
2. 确认产品状态为 **"待发布"**（已完成关联 API）
3. 点击【发布到门户】按钮
4. 选择目标门户：
    - 从门户列表中选择一个或多个门户
    - 可以同时发布到多个门户
5. 点击【发布】

<div align="center">

![](https://github.com/user-attachments/assets/6f09d6aa-08f0-4eb9-aab1-1295236ba986)

</div>

**发布后：**

+ 产品会立即在门户中展示
+ 开发者可以浏览产品详情和使用指南
+ 开发者可以申请订阅该产品
+ 管理员可以在后台查看订阅和调用情况

<div align="center">

![](https://github.com/user-attachments/assets/dd43e7f5-d490-45bc-a3b1-1ebdb0df66eb)

</div>

至此，一个完整的 API Product 从创建到发布的流程已完成！

---

## HiMarket 开发者门户
开发者门户是面向外部开发者的自助服务平台，开发者可以在此注册、浏览 API 产品、申请订阅和发起调用。

### 访问门户
HiMarket 门户采用**多租户设计**，通过域名来识别不同的门户实例。

**访问方式：**

1. **使用默认域名**：
    - 每个门户会自动分配一个默认域名
    - 例如：`portal-68ac4564bdb292ee9261ff4a.api.portal.local`
    - 需要配置 DNS 解析或 hosts 文件，将域名指向 himarket-frontend 服务
2. **使用自定义域名**：
    - 管理员在后台绑定的自定义域名
    - 例如本地开发时绑定的 `localhost`
    - 访问：`http://localhost:5173`
3. **使用 himarket-frontend 服务的 IP**：
    - 使用 himarket-frontend 服务的 IP 后会访问到"默认门户"，默认门户是创建的第一个门户
    - 本地开发环境可访问 `http://127.0.0.1:5173`

---

### 注册 Developer 开发者
开发者需要先在门户注册账号，才能浏览和订阅 API 产品。

**注册步骤：**

1. 访问门户首页
2. 点击【注册】按钮
3. 填写开发者信息：
    - 用户名：唯一的开发者标识
    - 密码：设置登录密码
4. 点击【注册】提交申请

<div align="center">

![](https://github.com/user-attachments/assets/c5bb8ab7-c55a-4d19-b20f-99bf65ef655c)

</div>

**注册审批：**

注册后的状态取决于门户的配置：

+ 自动审批模式：注册后立即生效，可以直接登录
+ 手动审批模式：注册后需要等待管理员审批
    - 审批前无法登录门户
    - 管理员在后台【Developers】菜单中审批

<div align="center">

![](https://github.com/user-attachments/assets/4d3743ac-01b3-4770-a1e0-f5a8d0955e4c)

</div>

审批通过后，即可登录门户

<div align="center">

![](https://github.com/user-attachments/assets/47f51797-b92f-4235-8b70-dd96c0d9c51b)

</div>

**注意事项：**

+ 每个门户的开发者账号是独立的
+ 不同门户需要分别注册

---

### 浏览和订阅 API Product
注册并登录后，开发者可以浏览门户中的 API 产品并申请订阅。

**浏览 API 产品：**

1. 登录开发者门户
2. 进入【模型】、【MCP】、【智能体】等产品页面
3. 浏览产品列表：
    - 查看产品名称、描述和版本
    - 筛选和搜索感兴趣的产品
4. 点击产品卡片查看详情：
    - 产品功能介绍
    - API Product 使用指南
    - 调用示例代码（如有）
    - 定价信息（如有）

<div align="center">

![](https://github.com/user-attachments/assets/14a0fb9c-0b09-46c1-a6f3-5e6ffd29d3e2)

</div>

---

### 创建 Consumer 消费者
在 HiMarket 的设计中：

+ Developer（开发者）：代表用户身份，用于登录和管理
+ Consumer（消费者）：代表调用凭证，用于 API Product 认证

一个 Developer 可以创建多个 Consumer，用于不同的应用场景（如开发环境、测试环境、生产环境）。每个 Developer 注册后会自动分配一个 Consumer，支持创建新的 Consumer。

**创建步骤：**

1. 登录开发者门户
2. 点击右上角的头像，进入【消费者管理】界面

<div align="center">

![](https://github.com/user-attachments/assets/6af8fd64-7755-40ec-8abd-3158fae59e8f)

</div>

3. 点击【新增消费者】按钮
4. 填写消费者信息：
    - 消费者名称：用于标识该消费者（例如：`my-app-prod`）
    - 描述：简要说明用途（例如：生产环境应用）
5. 点击【提交】

<div align="center">

![](https://github.com/user-attachments/assets/c3d6cc15-7b10-4f3c-8a67-f5d0a755594e)

</div>

**创建成功后：**

+ 系统会生成一组凭证（如 API Key、Secret）
+ 可以在列表中管理和删除已创建的消费者

<div align="center">

![](https://github.com/user-attachments/assets/cf6522af-46e7-43af-828f-80e7b8cbf6fa)

</div>

**申请产品订阅：**

创建消费者后，可使用消费者订阅 API 产品：

1. 选择要订阅的 API 产品
2. 在产品详情页点击【订阅】按钮
3. 选择要使用的 Consumer
4. 点击【确定订阅】

<div align="center">

![](https://github.com/user-attachments/assets/1d6dc5d3-8a1c-4dc4-9843-59f79b33b583)

</div>

**订阅审批：**

+ 自动审批：提交后立即生效，可以直接调用 API Product
+ 手动审批：需要等待管理员审批通过

**说明：**

+ 订阅状态可在 Consumer 详情的【订阅列表】中查看
+ 订阅成功后才能使用该 Consumer 的凭证调用对应的 API Product
+ 可以随时取消订阅

---

### HiChat
HiMarket 提供了 HiChat AI 创新中心，用于快速应用 AI 资源，例如与模型对话，结合模型调用 MCP Server 等。  
**调用模型与 MCP：**

1. 登录开发者门户
2. 进入【HiChat】页面

<div align="center">

![](https://github.com/user-attachments/assets/f324e384-845e-4201-9ca4-2ebd3ab75350)

</div>

3. 选择需要测试的模型产品，可与该模型进行对话，对话过程中可选择结合 MCP Server
4. 注意：
    - 模型与 MCP Server 需要订阅后才能正常访问，HiMarket HiChat 默认使用 Developer 的默认 Consumer，需要使用默认 Consumer 订阅对应的 API Product
    - Developer 可在【消费者管理】页面自行配置默认 Consumer
5. 操作示例：
    - 选择模型与 MCP Server

<div align="center">

![](https://github.com/user-attachments/assets/d8b0d691-2dc7-47d7-9b42-c2cf8d43f40c)

</div>

+ 指定并订阅 MCP Server，快速订阅会使用默认 Consumer 执行订阅操作

<div align="center">

![](https://github.com/user-attachments/assets/8088532d-b419-4d33-86d2-23c80edbe8ad)

</div>

+ 与模型对话

<div align="center">

![](https://github.com/user-attachments/assets/a396bc08-6d70-47bd-bf75-807486e2c63a)

</div>

至此，您已成功完成 HiMarket 平台的全流程体验！感谢您选择 HiMarket！期待看到您用它构建出精彩的应用。


