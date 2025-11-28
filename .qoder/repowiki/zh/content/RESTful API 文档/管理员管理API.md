# 管理员管理API

<cite>
**本文档引用文件**  
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java)
- [AdminCreateParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/AdminCreateParam.java)
- [AdminLoginParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/AdminLoginParam.java)
- [ResetPasswordParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/ResetPasswordParam.java)
- [AdminResult.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/result/AdminResult.java)
- [TokenUtil.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/core/utils/TokenUtil.java)
- [AdministratorService.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/service/AdministratorService.java)
</cite>

## 目录
1. [简介](#简介)
2. [核心功能概览](#核心功能概览)
3. [端点详细说明](#端点详细说明)
4. [数据结构定义](#数据结构定义)
5. [JWT认证机制](#jwt认证机制)
6. [权限控制机制](#权限控制机制)
7. [使用示例](#使用示例)
8. [常见错误码](#常见错误码)
9. [依赖关系图](#依赖关系图)

## 简介
本API文档详细描述了HiMarket系统中管理员账户的管理功能，包括管理员初始化、登录、登出、密码重置等核心操作。所有接口均通过RESTful风格实现，使用JSON格式进行数据交换，并基于JWT（JSON Web Token）实现安全的身份验证机制。

该模块主要由`AdministratorController`类实现，位于`portal-server`模块中，是系统安全访问的入口之一。管理员必须通过认证后才能访问受保护的资源。

## 核心功能概览
管理员管理API提供以下核心功能：
- 系统初始化时创建首个管理员账户
- 管理员登录以获取访问令牌
- 管理员登出并使当前令牌失效
- 修改当前管理员密码
- 获取当前登录管理员信息
- 检查系统是否需要初始化管理员

这些功能确保了系统的安全性与可管理性。

## 端点详细说明

### 管理员登录
**HTTP方法**: `POST`  
**URL路径**: `/admins/login`  
**请求头**: 无特殊要求  
**请求体结构**:
```json
{
  "username": "管理员用户名",
  "password": "管理员密码"
}
```
**响应体格式**:
```json
{
  "token": "JWT令牌字符串",
  "admin": {
    "adminId": "管理员ID",
    "username": "用户名",
    "createAt": "创建时间",
    "updatedAt": "更新时间"
  }
}
```
**功能说明**: 管理员使用用户名和密码进行身份验证。验证成功后返回包含JWT令牌和管理员信息的`AuthResponseResult`对象。

**Section sources**
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L28-L32)

### 初始化管理员
**HTTP方法**: `POST`  
**URL路径**: `/admins/init`  
**请求头**: 无特殊要求  
**请求体结构**:
```json
{
  "username": "管理员用户名",
  "password": "管理员密码"
}
```
**响应体格式**:
```json
{
  "adminId": "管理员ID",
  "username": "用户名",
  "createAt": "创建时间",
  "updatedAt": "更新时间"
}
```
**功能说明**: 在系统首次部署时调用此接口创建第一个管理员账户。仅允许调用一次，后续调用将抛出异常。

**Section sources**
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L50-L55)

### 检查是否需要初始化管理员
**HTTP方法**: `GET`  
**URL路径**: `/admins/need-init`  
**请求头**: 无特殊要求  
**请求体**: 无  
**响应体格式**:
```json
true 或 false
```
**功能说明**: 查询系统中是否已存在管理员账户。若不存在则返回`true`，表示需要初始化；否则返回`false`。

**Section sources**
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L44-L48)

### 管理员登出
**HTTP方法**: `POST`  
**URL路径**: `/admins/logout`  
**请求头**: `Authorization: Bearer <token>`  
**请求体**: 无  
**响应体**: 无（状态码200表示成功）  
**功能说明**: 将当前请求中的JWT令牌加入黑名单，使其失效，实现登出功能。

**Section sources**
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L34-L39)

### 获取当前登录管理员信息
**HTTP方法**: `GET`  
**URL路径**: `/admins`  
**请求头**: `Authorization: Bearer <token>`  
**请求体**: 无  
**响应体格式**:
```json
{
  "adminId": "管理员ID",
  "username": "用户名",
  "createAt": "创建时间",
  "updatedAt": "更新时间"
}
```
**功能说明**: 根据请求头中的JWT令牌自动解析出当前登录的管理员身份，并返回其详细信息。

**Section sources**
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L68-L73)

### 管理员修改密码
**HTTP方法**: `PATCH`  
**URL路径**: `/admins/password`  
**请求头**: `Authorization: Bearer <token>`  
**请求体结构**:
```json
{
  "oldPassword": "旧密码",
  "newPassword": "新密码"
}
```
**响应体**: 无（状态码200表示成功）  
**功能说明**: 当前登录的管理员修改自己的密码。需提供正确的旧密码和符合要求的新密码。

**Section sources**
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L60-L66)

## 数据结构定义

### AdminCreateParam（管理员创建参数）
**用途**: 用于初始化管理员账户的请求参数。
```java
@Data
public class AdminCreateParam {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
```
**字段说明**:
- **username**: 管理员用户名，不能为空
- **password**: 管理员密码，不能为空

**Section sources**
- [AdminCreateParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/AdminCreateParam.java#L1-L41)

### AdminLoginParam（管理员登录参数）
**用途**: 用于管理员登录的请求参数。
```java
@Data
public class AdminLoginParam {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
```
**字段说明**:
- **username**: 登录用户名
- **password**: 登录密码

**Section sources**
- [AdminLoginParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/AdminLoginParam.java#L1-L37)

### ResetPasswordParam（重置密码参数）
**用途**: 用于管理员修改密码的请求参数。
```java
@Data
public class ResetPasswordParam {
    private String oldPassword;
    private String newPassword;
}
```
**字段说明**:
- **oldPassword**: 当前密码
- **newPassword**: 新密码

**Section sources**
- [ResetPasswordParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/ResetPasswordParam.java#L1-L34)

### AdminResult（管理员结果）
**用途**: 返回管理员信息的结果对象。
```java
@Data
public class AdminResult {
    private String adminId;
    private String username;
    private LocalDateTime createAt;
    private LocalDateTime updatedAt;
}
```
**字段说明**:
- **adminId**: 管理员唯一标识
- **username**: 用户名
- **createAt**: 创建时间
- **updatedAt**: 最后更新时间

**Section sources**
- [AdminResult.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/result/AdminResult.java#L1-L38)

## JWT认证机制
系统采用JWT（JSON Web Token）作为身份验证机制。当管理员成功登录后，服务器生成一个加密的JWT令牌并返回给客户端。客户端在后续请求中需在`Authorization`头中携带该令牌（格式为`Bearer <token>`），服务器通过`JwtAuthenticationFilter`拦截请求并验证令牌的有效性。

令牌包含管理员身份信息，并设有有效期。登出操作会调用`TokenUtil.revokeToken()`方法将当前令牌加入黑名单，防止其被再次使用。

```mermaid
sequenceDiagram
participant Client as "客户端"
participant Controller as "AdministratorController"
participant Service as "AdministratorService"
participant TokenUtil as "TokenUtil"
Client->>Controller : POST /admins/login<br/>{username, password}
Controller->>Service : login(username, password)
Service-->>Controller : AuthResponseResult(token, admin)
Controller-->>Client : 返回token和管理员信息
Client->>Controller : GET /admins<br/>Authorization : Bearer token
Controller->>TokenUtil : 验证token有效性
TokenUtil-->>Controller : 验证通过
Controller->>Service : getAdministrator()
Service-->>Controller : AdminResult
Controller-->>Client : 返回管理员信息
```

**Diagram sources**
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L28-L73)
- [TokenUtil.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/core/utils/TokenUtil.java)

## 权限控制机制
系统通过自定义注解`@AdminAuth`实现管理员权限控制。该注解应用于需要管理员身份才能访问的接口上。

**工作原理**:
1. 请求到达带有`@AdminAuth`注解的接口
2. 框架触发权限验证逻辑
3. 从请求头提取JWT令牌
4. 验证令牌是否有效且未过期
5. 检查令牌是否在黑名单中（已登出）
6. 若验证通过，则允许访问；否则返回401未授权错误

此机制确保只有经过身份验证的管理员才能执行敏感操作。

```mermaid
flowchart TD
Start([接收到请求]) --> HasAuthHeader{"是否存在Authorization头?"}
HasAuthHeader --> |否| Return401["返回401 Unauthorized"]
HasAuthHeader --> |是| ExtractToken["提取Bearer Token"]
ExtractToken --> IsValidJWT{"是否为有效JWT?"}
IsValidJWT --> |否| Return401
IsValidJWT --> |是| IsBlacklisted{"令牌是否在黑名单?"}
IsBlacklisted --> |是| Return401
IsBlacklisted --> |否| Proceed["继续执行业务逻辑"]
Return401 --> End([响应结束])
Proceed --> End
```

**Diagram sources**
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L34-L39)
- [TokenUtil.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/core/utils/TokenUtil.java)

## 使用示例

### 创建管理员（初始化）
```bash
curl -X POST http://localhost:8080/admins/init \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

### 管理员登录
```bash
curl -X POST http://localhost:8080/admins/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```
**预期响应**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "admin": {
    "adminId": "1",
    "username": "admin",
    "createAt": "2023-01-01T10:00:00",
    "updatedAt": "2023-01-01T10:00:00"
  }
}
```

### 获取管理员信息（需认证）
```bash
curl -X GET http://localhost:8080/admins \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
```

## 常见错误码
| 状态码 | 错误类型 | 含义 | 处理方式 |
|--------|----------|------|----------|
| 400 | Bad Request | 请求参数无效（如用户名为空） | 检查请求体格式和必填字段 |
| 401 | Unauthorized | 未提供令牌或令牌无效/过期 | 重新登录获取新令牌 |
| 403 | Forbidden | 无权访问（如未初始化时尝试登录） | 先调用`/init`初始化管理员 |
| 409 | Conflict | 用户名已存在（初始化时） | 使用其他用户名或确认是否已初始化 |
| 410 | Gone | 系统已初始化，无法再次初始化 | 不再调用`/init`接口 |
| 422 | Unprocessable Entity | 旧密码错误（修改密码时） | 确认旧密码输入正确 |

## 依赖关系图
```mermaid
classDiagram
class AdministratorController {
+login(AdminLoginParam) AuthResponseResult
+logout(HttpServletRequest) void
+needInit() Boolean
+initAdmin(AdminCreateParam) AdminResult
+resetPassword(ResetPasswordParam) void
+getAdministrator() AdminResult
}
class AdminCreateParam {
+username String
+password String
}
class AdminLoginParam {
+username String
+password String
}
class ResetPasswordParam {
+oldPassword String
+newPassword String
}
class AdminResult {
+adminId String
+username String
+createAt LocalDateTime
+updatedAt LocalDateTime
}
class AuthResponseResult {
+token String
+admin AdminResult
}
class TokenUtil {
+revokeToken(HttpServletRequest) void
}
class AdministratorService {
+login(String, String) AuthResponseResult
+initAdmin(String, String) AdminResult
+needInit() Boolean
+resetPassword(String, String) void
+getAdministrator() AdminResult
}
AdministratorController --> AdministratorService : "依赖"
AdministratorController --> TokenUtil : "调用"
AdministratorController --> AdminLoginParam : "接收"
AdministratorController --> AdminCreateParam : "接收"
AdministratorController --> ResetPasswordParam : "接收"
AdministratorController --> AuthResponseResult : "返回"
AdministratorController --> AdminResult : "返回"
```

**Diagram sources**
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java)
- [AdministratorService.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/service/AdministratorService.java)
- [TokenUtil.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/core/utils/TokenUtil.java)
- [AdminCreateParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/AdminCreateParam.java)
- [AdminLoginParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/AdminLoginParam.java)
- [ResetPasswordParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/ResetPasswordParam.java)
- [AdminResult.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/result/AdminResult.java)