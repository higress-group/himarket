# 需求文档：匿名访问支持

## 简介

HiMarket 前台门户当前要求所有操作均需用户登录。本需求旨在支持匿名（未登录）用户浏览前台所有模块（HiChat、HiCoding、智能体、MCP、模型、API、Skills），同时确保涉及用户数据的操作（如订阅、购买、发送消息等）仍需登录认证。后端采用"默认认证 + 显式公开"策略：所有接口默认 `.anyRequest().authenticated()`，公开接口必须显式标记 @PublicAccess 注解。

## 术语表

- **HiMarket_Portal**：HiMarket 前台门户系统，面向开发者的产品浏览和使用入口
- **Anonymous_User**：未登录的匿名用户，未携带有效 JWT Token 访问系统
- **Authenticated_User**：已登录的认证用户，携带有效 JWT Token 访问系统
- **Security_Filter**：Spring Security 中的 JWT 认证过滤器（JwtAuthenticationFilter），负责解析和验证请求中的 Token
- **Auth_Annotation**：接口认证注解，包括 @AdminAuth、@DeveloperAuth、@AdminOrDeveloperAuth，用于声明接口所需的认证级别
- **PublicAccess**：新增的公开访问注解，标记无需任何认证即可访问的接口
- **Security_Config**：Spring Security 安全配置类（SecurityConfig），定义 URL 级别的访问控制规则
- **Product**：HiMarket 中的产品实体，包含 MCP Server、REST API、Agent API、Model API、Agent Skill 等类型
- **Subscription**：开发者对产品的订阅关系，需要登录后才能创建
- **Login_Prompt**：登录引导提示，当匿名用户尝试执行需要认证的操作时展示的引导界面，包含操作上下文说明、登录按钮和注册入口
- **Welcome_View**：匿名欢迎视图，在 HiChat 和 HiCoding 页面为匿名用户展示的功能介绍和登录引导界面，替代空白页面
- **Empty_State**：空状态视图，当产品广场页面无产品数据时展示的友好提示界面，包含说明文案和引导操作

## 需求

### 需求 1：后端公开访问注解

**用户故事：** 作为后端开发者，我希望有一个 @PublicAccess 注解来标记公开接口，以便明确区分公开接口和需要认证的接口。

#### 验收标准

1. THE HiMarket_Portal SHALL 提供 @PublicAccess 注解，用于标记无需任何认证即可访问的 Controller 方法或类
2. WHEN 一个接口标记了 @PublicAccess 注解，THE Security_Config SHALL 允许该接口在无 JWT Token 的情况下被访问
3. WHEN 一个接口同时标记了 @PublicAccess 和 @DeveloperAuth 注解，THE Security_Config SHALL 以 @DeveloperAuth 为准，要求认证（认证注解优先级高于公开访问注解）
4. THE Security_Config SHALL 在应用启动时扫描所有 @PublicAccess 注解的接口，并将其路径加入白名单

### 需求 2：Security 配置重构

**用户故事：** 作为后端开发者，我希望 SecurityConfig 能基于注解自动识别公开接口，以便减少手动维护白名单的工作量。

#### 验收标准

1. THE Security_Config SHALL 保持默认访问策略为 `.anyRequest().authenticated()`，所有接口默认需要认证
2. THE Security_Config SHALL 在应用启动时自动扫描所有标记 @PublicAccess 的接口路径
3. WHEN 一个接口标记了 @PublicAccess，THE Security_Config SHALL 将该接口路径加入 `permitAll()` 列表
4. WHEN 一个接口未标记 @PublicAccess 注解，THE Security_Config SHALL 保持原有的 `.authenticated()` 要求
5. THE Security_Config SHALL 保留现有的 AUTH_WHITELIST、SWAGGER_WHITELIST、SYSTEM_WHITELIST 白名单配置

### 需求 3：产品浏览接口公开化

**用户故事：** 作为匿名用户，我希望无需登录即可浏览所有类型的产品列表和详情，以便在注册前了解平台提供的服务。

#### 验收标准

1. WHEN Anonymous_User 请求产品列表接口（GET /products），THE ProductController SHALL 返回产品列表数据
2. WHEN Anonymous_User 请求产品详情接口（GET /products/{productId}），THE ProductController SHALL 返回产品详情数据
3. WHEN Anonymous_User 请求产品关联的 API 或 MCP Server 信息（GET /products/{productId}/ref），THE ProductController SHALL 返回关联信息
4. WHEN Anonymous_User 请求产品类别列表（GET /product-categories），THE ProductCategoryController SHALL 返回类别列表
5. WHEN Anonymous_User 请求产品类别详情（GET /product-categories/{categoryId}），THE ProductCategoryController SHALL 返回类别详情
6. WHEN Anonymous_User 请求 MCP 服务的工具详情（GET /products/{productId}/tools），THE ProductController SHALL 返回工具列表

### 需求 4：Skill 市场匿名访问

**用户故事：** 作为匿名用户，我希望无需登录即可查看和下载 Skill，以便快速获取所需的 Skill 资源。

#### 验收标准

1. WHEN Anonymous_User 请求 Skill 文件树（GET /skills/{productId}/files），THE SkillController SHALL 返回文件树数据
2. WHEN Anonymous_User 请求 Skill 单文件内容（GET /skills/{productId}/files/{filePath}），THE SkillController SHALL 返回文件内容
3. WHEN Anonymous_User 请求 Skill 下载（GET /skills/{productId}/download），THE SkillController SHALL 提供 ZIP 文件下载（该接口已在白名单中）
4. THE SkillController 的管理类接口（POST /skills/{productId}/package、/skills/nacos/**）SHALL 保持 @AdminAuth 认证要求

### 需求 5：前端路由匿名访问

**用户故事：** 作为匿名用户，我希望能直接访问前台所有浏览页面而不被重定向到登录页，以便自由浏览平台内容。

#### 验收标准

1. WHEN Anonymous_User 访问产品广场页面（/models、/mcp、/agents、/apis、/skills），THE HiMarket_Portal 前端 SHALL 正常渲染页面内容
2. WHEN Anonymous_User 访问产品详情页面（/skills/:id、/mcp/:id、/apis/:id、/agents/:id、/models/:id），THE HiMarket_Portal 前端 SHALL 正常渲染详情内容
3. WHEN Anonymous_User 访问首页（/），THE HiMarket_Portal 前端 SHALL 正常渲染首页内容
4. WHEN 后端接口返回 401 状态码且当前页面为公开浏览页面，THE HiMarket_Portal 前端 SHALL 不自动跳转到登录页

### 需求 6：前端登录引导

**用户故事：** 作为匿名用户，我希望在尝试执行需要登录的操作时看到包含上下文说明的登录引导，以便我了解登录后能获得的能力并快速完成登录。

#### 验收标准

1. WHEN Anonymous_User 在 MCP 详情页点击"订阅"按钮，THE Login_Prompt SHALL 展示操作上下文说明（如"登录后即可订阅此 MCP 服务，获取 API Key 并开始使用"）
2. WHEN Anonymous_User 在 HiChat 页面尝试发送消息，THE Login_Prompt SHALL 展示操作上下文说明（如"登录后即可与 AI 模型对话，体验智能问答能力"）
3. WHEN Anonymous_User 在 HiCoding 页面尝试创建编码会话，THE Login_Prompt SHALL 展示操作上下文说明（如"登录后即可创建编码会话，使用 AI 辅助编程"）
4. WHEN Anonymous_User 在产品详情页点击需要认证的操作按钮，THE Login_Prompt SHALL 根据操作类型展示对应的上下文说明，说明登录后能获得的具体能力
5. THE Login_Prompt SHALL 包含登录按钮，点击后跳转到登录页面，并在登录成功后返回原页面
6. THE Login_Prompt SHALL 包含注册入口，供未注册用户快速注册
7. THE Login_Prompt SHALL 支持通过参数传入上下文说明文案，不同触发场景展示不同的引导文案

### 需求 7：前端请求拦截器改造

**用户故事：** 作为前端开发者，我希望请求拦截器能区分公开接口和认证接口的 401 响应处理，以便匿名用户浏览公开页面时不会被强制跳转到登录页。

#### 验收标准

1. WHEN 后端返回 401 状态码且当前请求为公开浏览类接口，THE 请求拦截器 SHALL 不执行跳转登录页的逻辑
2. WHEN 后端返回 401 状态码且当前请求为需要认证的操作类接口，THE 请求拦截器 SHALL 执行现有的跳转登录页逻辑
3. WHEN 后端返回 403 状态码且当前请求为公开浏览类接口，THE 请求拦截器 SHALL 不执行跳转登录页的逻辑
4. THE 请求拦截器 SHALL 在未持有 Token 时仍正常发送请求（不附加 Authorization 头）

### 需求 8：导航栏匿名状态展示

**用户故事：** 作为匿名用户，我希望导航栏能清晰展示我的未登录状态并提供登录入口，以便我随时可以选择登录。

#### 验收标准

1. WHEN Anonymous_User 访问任意前台页面，THE 导航栏 SHALL 在用户信息区域展示"登录"按钮替代用户头像和名称
2. WHEN Anonymous_User 点击导航栏的"登录"按钮，THE HiMarket_Portal 前端 SHALL 跳转到登录页面
3. WHEN Authenticated_User 访问任意前台页面，THE 导航栏 SHALL 保持现有的用户头像和名称展示

### 需求 9：HiChat 和 HiCoding 匿名浏览

**用户故事：** 作为匿名用户，我希望进入 HiChat 和 HiCoding 页面时看到功能介绍和登录引导，而不是空白页面，以便我了解平台的 AI 对话和编码能力并被引导登录使用。

#### 验收标准

1. WHEN Anonymous_User 访问 HiChat 页面（/chat），THE HiMarket_Portal 前端 SHALL 展示 Welcome_View，包含欢迎文案、功能介绍说明（如 AI 对话、多模型支持等核心能力）和示例对话展示
2. WHEN Anonymous_User 访问 HiCoding 页面（/coding），THE HiMarket_Portal 前端 SHALL 展示 Welcome_View，包含欢迎文案、功能介绍说明（如 AI 辅助编程、代码生成等核心能力）和功能亮点展示
3. THE HiChat 页面的 Welcome_View SHALL 包含明显的"登录后开始对话"CTA 按钮，点击后跳转到登录页面
4. THE HiCoding 页面的 Welcome_View SHALL 包含明显的"登录后开始编码"CTA 按钮，点击后跳转到登录页面
5. THE HiChat 和 HiCoding 页面的 Welcome_View SHALL 同时提供"注册"入口按钮，供未注册用户快速注册
6. WHEN Anonymous_User 在 HiChat 页面点击消息输入框或发送按钮，THE HiMarket_Portal 前端 SHALL 展示 Login_Prompt
7. WHEN Anonymous_User 在 HiCoding 页面点击创建会话按钮，THE HiMarket_Portal 前端 SHALL 展示 Login_Prompt
8. WHEN Authenticated_User 访问 HiChat 页面，THE HiMarket_Portal 前端 SHALL 展示正常的聊天界面，不展示 Welcome_View
9. WHEN Authenticated_User 访问 HiCoding 页面，THE HiMarket_Portal 前端 SHALL 展示正常的编码界面，不展示 Welcome_View

### 需求 10：产品广场空状态展示

**用户故事：** 作为匿名用户，我希望在产品广场页面没有产品数据时看到友好的空状态提示，而不是空白页面，以便我了解该类别的用途并知道平台正在建设中。

#### 验收标准

1. WHEN Anonymous_User 访问产品广场页面（/models、/mcp、/agents、/apis、/skills）且该类别下无产品数据，THE HiMarket_Portal 前端 SHALL 展示 Empty_State 视图
2. THE Empty_State SHALL 包含该产品类别的说明文案（如"/mcp 页面展示：这里是 MCP 服务市场，开发者可以发现和订阅各类 MCP 服务"）
3. THE Empty_State SHALL 包含引导文案（如"暂无产品，敬请期待"或"成为第一个发布者"）
4. THE Empty_State SHALL 包含视觉占位元素（如插图或图标），避免页面显得空白单调
5. WHEN 产品广场页面有产品数据时，THE HiMarket_Portal 前端 SHALL 正常展示产品列表，不展示 Empty_State
