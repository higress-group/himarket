# 实现计划：匿名访问支持

## 概述

为 HiMarket 前台门户实现匿名访问能力。后端通过新增 `@PublicAccess` 注解 + 启动时自动扫描注册白名单的方式，将产品浏览类接口开放为公开访问；前端改造请求拦截器、新增登录引导组件和匿名欢迎视图，使未登录用户可以自由浏览产品广场、HiChat、HiCoding 等页面。

## 任务

- [ ] 1. 后端：创建 @PublicAccess 注解和路径扫描器
  - [ ] 1.1 创建 @PublicAccess 注解
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/core/annotation/PublicAccess.java` 创建注解
    - `@Target({ElementType.METHOD, ElementType.TYPE})`，`@Retention(RetentionPolicy.RUNTIME)`
    - 与现有 `@AdminAuth`、`@DeveloperAuth` 注解保持一致的风格
    - _需求: 1.1_

  - [ ] 1.2 实现 PublicAccessPathScanner 路径扫描器
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/core/security/PublicAccessPathScanner.java` 创建组件
    - 实现 `ApplicationContextAware` 接口，在 Spring 容器初始化后扫描所有 `@RestController`/`@Controller` Bean
    - 查找标记 `@PublicAccess` 的方法或类，结合类级 `@RequestMapping` 和方法级映射注解拼接完整路径
    - 当 `@PublicAccess` 标记在类级别时，该类所有未标记认证注解的方法均视为公开
    - 当方法同时标记 `@PublicAccess` 和认证注解（`@AdminAuth`/`@DeveloperAuth`/`@AdminOrDeveloperAuth`）时，认证注解优先，不加入白名单
    - 扫描失败时记录 WARN 日志，不影响应用启动
    - _需求: 1.2, 1.3, 1.4, 2.2, 2.3_

  - [ ]* 1.3 编写 PublicAccessPathScanner 属性测试
    - **属性 1: PublicAccess 扫描器正确性** — 对于任意一组 Controller Bean，扫描结果应恰好包含所有标记 @PublicAccess 且未同时标记认证注解的方法路径
    - **验证需求: 1.2, 1.4, 2.2, 2.3**
    - **属性 2: 认证注解优先级** — 对于任意同时标记 @PublicAccess 和认证注解的方法，扫描器不应将其路径加入白名单
    - **验证需求: 1.3**
    - 使用 jqwik 框架编写属性测试

- [ ] 2. 后端：改造 SecurityConfig 并标记公开接口
  - [ ] 2.1 改造 SecurityConfig 集成 PublicAccessPathScanner
    - 修改 `himarket-bootstrap/src/main/java/com/alibaba/himarket/config/SecurityConfig.java`
    - 注入 `PublicAccessPathScanner`，在 `filterChain()` 方法中将扫描到的路径加入 `permitAll()` 列表
    - 保留现有 `AUTH_WHITELIST`、`SWAGGER_WHITELIST`、`SYSTEM_WHITELIST` 不变
    - 保持 `.anyRequest().authenticated()` 默认策略
    - _需求: 2.1, 2.3, 2.4, 2.5_

  - [ ] 2.2 为产品浏览接口添加 @PublicAccess 注解
    - 修改 `himarket-server/src/main/java/com/alibaba/himarket/controller/ProductController.java`：
      - `listProducts`（GET /products）：添加 `@PublicAccess`
      - `getProduct`（GET /products/{productId}）：添加 `@PublicAccess`
      - `getProductRef`（GET /products/{productId}/ref）：添加 `@PublicAccess`
      - `listMcpTools`（GET /products/{productId}/tools）：将 `@AdminOrDeveloperAuth` 替换为 `@PublicAccess`
    - 修改 `himarket-server/src/main/java/com/alibaba/himarket/controller/ProductCategoryController.java`：
      - `listProductCategories`（GET /product-categories）：将 `@AdminOrDeveloperAuth` 替换为 `@PublicAccess`
      - `getProductCategory`（GET /product-categories/{categoryId}）：将 `@AdminOrDeveloperAuth` 替换为 `@PublicAccess`
    - _需求: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [ ] 2.3 为 Skill 浏览接口添加 @PublicAccess 注解
    - 修改 `himarket-server/src/main/java/com/alibaba/himarket/controller/SkillController.java`：
      - `getFileTreeByProduct`（GET /skills/{productId}/files）：将 `@AdminOrDeveloperAuth` 替换为 `@PublicAccess`
      - `getFileContentByProduct`（GET /skills/{productId}/files/{*filePath}）：将 `@AdminOrDeveloperAuth` 替换为 `@PublicAccess`
    - 确认 Skill 管理类接口（POST /skills/{productId}/package、/skills/nacos/**）保持 `@AdminAuth`
    - _需求: 4.1, 4.2, 4.3, 4.4_

  - [ ]* 2.4 编写默认认证策略属性测试
    - **属性 3: 默认认证策略** — 对于任意未标记 @PublicAccess 且不在现有白名单中的接口路径，无 Token 请求应返回 401
    - **验证需求: 2.1, 2.4**
    - 使用 Spring Boot Test + MockMvc 编写集成测试

- [ ] 3. 后端检查点
  - 确保所有测试通过，如有问题请向用户确认。

- [ ] 4. 前端：创建 useAuth Hook 和改造请求拦截器
  - [ ] 4.1 创建 useAuth Hook
    - 在 `himarket-web/himarket-frontend/src/hooks/useAuth.ts` 创建 Hook
    - 提供 `isLoggedIn`（基于 localStorage access_token 判断）、`login(returnUrl?)`（跳转登录页）、`token` 属性
    - _需求: 6.5, 8.1_

  - [ ] 4.2 改造请求拦截器
    - 修改 `himarket-web/himarket-frontend/src/lib/request.ts`
    - 定义 `PUBLIC_PATHS` 公开页面路径列表：`['/models', '/mcp', '/agents', '/apis', '/skills', '/chat', '/coding']`（含详情页路径匹配）
    - 401/403 处理：当前页面路径匹配 PUBLIC_PATHS 时，不跳转登录页、不清除 Token，静默处理
    - 非公开页面保持现有跳转逻辑
    - 无 Token 时不附加 Authorization 头（现有逻辑已满足，确认即可）
    - _需求: 5.4, 7.1, 7.2, 7.3, 7.4_

  - [ ]* 4.3 编写请求拦截器属性测试
    - **属性 4: 公开页面错误静默处理** — 对于任意 PUBLIC_PATHS 中的页面路径，收到 401/403 时不触发页面跳转
    - **验证需求: 5.4, 7.1, 7.3**
    - **属性 5: 非公开页面 401 跳转** — 对于任意不在 PUBLIC_PATHS 中的页面路径（且非 /login），收到 401 时应跳转登录页并携带 returnUrl
    - **验证需求: 7.2**
    - **属性 6: 无 Token 时不附加 Authorization 头** — 当 localStorage 中不存在 access_token 时，请求头不应包含 Authorization 字段
    - **验证需求: 7.4**
    - 使用 fast-check + Vitest 编写属性测试

- [ ] 5. 前端：创建 LoginPrompt 登录引导组件
  - [ ] 5.1 实现 LoginPrompt 组件
    - 在 `himarket-web/himarket-frontend/src/components/LoginPrompt.tsx` 创建组件
    - Props: `open`、`onClose`、`contextMessage`、`returnUrl?`
    - 使用 Ant Design Modal 实现弹窗
    - 包含"登录"按钮（携带 returnUrl 跳转）和"注册"入口按钮
    - 支持通过 `contextMessage` 传入不同场景的引导文案
    - _需求: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

  - [ ]* 5.2 编写 LoginPrompt 属性测试
    - **属性 7: LoginPrompt 上下文文案渲染** — 对于任意非空字符串作为 contextMessage，组件渲染输出应包含该字符串
    - **验证需求: 6.4, 6.7**
    - **属性 8: LoginPrompt 登录跳转携带 returnUrl** — 对于任意 URL 路径字符串作为 returnUrl，登录按钮的跳转链接应包含 returnUrl 参数
    - **验证需求: 6.5**
    - 使用 fast-check + React Testing Library + Vitest 编写属性测试

- [ ] 6. 前端：创建 WelcomeView 和 EmptyState 组件
  - [ ] 6.1 实现 WelcomeView 组件
    - 在 `himarket-web/himarket-frontend/src/components/WelcomeView.tsx` 创建组件
    - Props: `type: 'chat' | 'coding'`
    - chat 类型：展示 AI 对话功能介绍、示例对话、"登录后开始对话" CTA 按钮、注册入口
    - coding 类型：展示 AI 辅助编程功能介绍、功能亮点、"登录后开始编码" CTA 按钮、注册入口
    - _需求: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [ ] 6.2 实现 EmptyState 组件
    - 在 `himarket-web/himarket-frontend/src/components/EmptyState.tsx` 创建组件
    - Props: `productType: string`
    - 根据产品类型展示对应说明文案、引导文案（"暂无产品，敬请期待"）和视觉占位元素
    - _需求: 10.1, 10.2, 10.3, 10.4_

  - [ ]* 6.3 编写 EmptyState 属性测试
    - **属性 9: 产品广场空状态与列表渲染** — 对于任意产品类型，当产品列表为空时应渲染 EmptyState；非空时应渲染产品列表
    - **验证需求: 10.1, 10.2, 10.5**
    - 使用 fast-check + React Testing Library + Vitest 编写属性测试

- [ ] 7. 前端：改造页面组件集成匿名访问
  - [ ] 7.1 改造 UserInfo 组件
    - 修改 `himarket-web/himarket-frontend/src/components/UserInfo.tsx`
    - 在 `getDeveloperInfo()` 的 catch 中静默处理，展示"登录"按钮（现有 fallback 逻辑已具备，确认 401 不再触发跳转即可）
    - _需求: 8.1, 8.2, 8.3_

  - [ ] 7.2 改造 Chat 页面集成匿名视图
    - 修改 `himarket-web/himarket-frontend/src/pages/Chat.tsx`
    - 使用 `useAuth` 判断登录状态，未登录时展示 `WelcomeView type="chat"`
    - 匿名用户点击消息输入框或发送按钮时弹出 `LoginPrompt`
    - 已登录用户展示正常聊天界面
    - _需求: 9.1, 9.3, 9.6, 9.8_

  - [ ] 7.3 改造 Coding 页面集成匿名视图
    - 修改 `himarket-web/himarket-frontend/src/pages/Coding.tsx`
    - 使用 `useAuth` 判断登录状态，未登录时展示 `WelcomeView type="coding"`
    - 匿名用户点击创建会话按钮时弹出 `LoginPrompt`
    - 已登录用户展示正常编码界面
    - _需求: 9.2, 9.4, 9.7, 9.9_

  - [ ] 7.4 改造 Square 产品广场页面集成空状态
    - 修改 `himarket-web/himarket-frontend/src/pages/Square.tsx`
    - 当产品列表为空时展示 `EmptyState` 组件，传入对应的 `productType`
    - 有产品数据时正常展示产品列表
    - _需求: 10.1, 10.2, 10.5_

  - [ ] 7.5 改造产品详情页集成 LoginPrompt
    - 修改 `McpDetail.tsx`、`ApiDetail.tsx`、`AgentDetail.tsx`、`ModelDetail.tsx`、`SkillDetail.tsx`
    - 使用 `useAuth` 判断登录状态，匿名用户点击"订阅"等需要认证的操作按钮时弹出 `LoginPrompt`，传入对应的上下文说明文案
    - _需求: 6.1, 6.4_

- [ ] 8. 前端检查点
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的子任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- 检查点任务用于阶段性验证，确保增量正确性
- 属性测试验证通用正确性属性，单元测试验证具体示例和边界情况
- 后端使用 jqwik 框架，前端使用 fast-check + Vitest 框架
