# 需求文档

## 简介

HiMarket 在 POC 阶段引入了若干临时性简化逻辑，包括 WebSocket 匿名访问模式和前端 POC 测试文案。本次清理旨在移除这些残留代码，消除安全风险并提升用户体验。清理范围包括：P0 安全修复（移除匿名访问模式）和 P2 体验优化（清理前端 POC 标识）。

## 术语表

- **AcpHandshakeInterceptor**：WebSocket 握手认证拦截器，负责在 WebSocket 连接建立前验证客户端提供的 JWT token
- **WorkspaceController**：工作空间文件管理控制器，提供文件上传、读取、预览等接口
- **BusinessException**：系统统一业务异常类，用于抛出带有错误码的业务错误
- **SecurityContextHolder**：Spring Security 提供的认证上下文持有者，存储当前请求的认证信息
- **RuntimeSelector**：前端运行时选择器组件，用于选择代码运行环境
- **JWT_Token**：JSON Web Token，用于客户端身份认证的令牌

## 需求

### 需求 1：WebSocket 握手拒绝无 token 连接

**用户故事：** 作为系统管理员，我希望所有 WebSocket 连接必须携带有效的认证 token，以确保系统不存在匿名访问的安全漏洞。

#### 验收标准

1. WHEN 客户端发起 WebSocket 握手且未携带 token，THEN AcpHandshakeInterceptor SHALL 返回 false 拒绝该连接
2. WHEN 客户端发起 WebSocket 握手且携带有效 JWT_Token，THEN AcpHandshakeInterceptor SHALL 允许连接并将解析出的 userId 存入会话属性
3. WHEN 客户端发起 WebSocket 握手且携带无效 JWT_Token，THEN AcpHandshakeInterceptor SHALL 返回 false 拒绝该连接
4. WHEN AcpHandshakeInterceptor 拒绝无 token 连接时，THEN AcpHandshakeInterceptor SHALL 记录 warn 级别日志，包含 "missing token" 信息

### 需求 2：WorkspaceController 未认证访问防御

**用户故事：** 作为系统管理员，我希望 WorkspaceController 在认证信息缺失时抛出异常而非回退到匿名用户，以防止未认证用户访问其他用户的工作空间数据。

#### 验收标准

1. WHEN SecurityContextHolder 中存在有效认证信息，THEN WorkspaceController SHALL 返回认证主体中的 userId
2. WHEN SecurityContextHolder 中不存在有效认证信息，THEN WorkspaceController SHALL 抛出 BusinessException，错误码为 UNAUTHORIZED
3. THE WorkspaceController SHALL 确保 getCurrentUserId() 方法在任何情况下都不返回 "anonymous" 字符串

### 需求 3：前端 POC 文案清理

**用户故事：** 作为用户，我希望界面中不再出现 POC 相关的临时文案，以获得专业的产品体验。

#### 验收标准

1. THE RuntimeSelector 测试数据 SHALL 将 "POC 本地启动" 标签替换为 "本地运行"
2. WHEN RuntimeSelector 测试数据中的标签更新后，THEN RuntimeSelector 测试 SHALL 同步更新对应的 description 为正式描述
3. WHEN RuntimeSelector 测试数据中的标签更新后，THEN RuntimeSelector 测试 SHALL 同步更新所有相关的 expect 断言
