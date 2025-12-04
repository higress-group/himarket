# RESTful API 文档

<cite>
**本文档引用的文件**  
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L1-L87)
- [DeveloperController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/DeveloperController.java#L1-L121)
- [ProductController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/ProductController.java#L1-L122)
- [AdminLoginParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/AdminLoginParam.java)
- [AdminResult.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/result/AdminResult.java)
- [ErrorCode.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/core/exception/ErrorCode.java)
</cite>

## 目录
1. [简介](#简介)
2. [认证机制](#认证机制)
3. [错误处理](#错误处理)
4. [管理员管理API](#管理员管理api)
5. [开发者管理API](#开发者管理api)
6. [API产品管理API](#api产品管理api)
7. [curl调用示例](#curl调用示例)

## 简介
本文档为Himarket平台的RESTful API提供详尽说明。API基于Spring Boot构建，采用JWT进行身份认证，通过Controller类暴露HTTP端点。主要功能模块包括管理员管理、开发者管理以及API产品管理。所有接口均使用JSON格式进行请求和响应数据交换。

**本文档引用的文件**  
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L1-L87)
- [DeveloperController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/DeveloperController.java#L1-L121)
- [ProductController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/ProductController.java#L1-L122)

## 认证机制
系统采用JWT（JSON Web Token）进行身份认证。管理员和开发者在登录成功后会收到一个JWT令牌，后续请求需在HTTP头中携带该令牌。

**: 认证方式**
- **类型**: Bearer Token
- **请求头**: `Authorization: Bearer <token>`
- **有效期**: 由`TokenUtil`类管理
- **登出机制**: 登出时调用`TokenUtil.revokeToken()`将当前Token加入黑名单

管理员接口需使用`@AdminAuth`注解进行权限校验，开发者接口需使用`@DeveloperAuth`注解。

**本文档引用的文件**  
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L1-L87)
- [DeveloperController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/DeveloperController.java#L1-L121)
- [TokenUtil.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/core/utils/TokenUtil.java)

## 错误处理
系统通过`ErrorCode`枚举类统一管理错误码，所有错误响应均遵循统一的响应格式。

**: 错误响应结构**
```json
{
  "code": "ERROR_CODE",
  "message": "错误描述信息",
  "timestamp": "2023-10-01T12:00:00Z"
}
```

**: 常见错误码**
- `AUTH_001`: 认证失败
- `AUTH_002`: 令牌无效
- `AUTH_003`: 令牌已过期
- `USER_001`: 用户不存在
- `USER_002`: 密码错误
- `PERMISSION_001`: 权限不足
- `VALIDATION_001`: 参数验证失败

错误处理由`ExceptionAdvice`类统一拦截和处理。

**本文档引用的文件**  
- [ErrorCode.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/core/exception/ErrorCode.java)
- [ExceptionAdvice.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/core/advice/ExceptionAdvice.java)

## 管理员管理API
提供管理员账户的初始化、登录、登出及密码管理等功能。

### 管理员登录
**: 端点信息**
- **HTTP方法**: POST
- **URL路径**: `/admins/login`
- **认证要求**: 无需认证

**: 请求头**
```
Content-Type: application/json
```

**: 请求体 (JSON Schema)**
```json
{
  "username": "admin",
  "password": "password123"
}
```
基于`AdminLoginParam`类，包含以下字段：
- `username` (string): 管理员用户名
- `password` (string): 管理员密码

**: 响应体 (JSON Schema)**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "admin123",
    "username": "admin",
    "createdAt": "2023-10-01T10:00:00Z"
  }
}
```
基于`AuthResponseResult`类。

**本文档引用的文件**  
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L25-L30)
- [AdminLoginParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/AdminLoginParam.java)
- [AuthResponseResult.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/result/AuthResponseResult.java)

### 检查是否需要初始化管理员
**: 端点信息**
- **HTTP方法**: GET
- **URL路径**: `/admins/need-init`
- **认证要求**: 无需认证

**: 响应体 (JSON Schema)**
```json
true
```
返回布尔值，`true`表示需要初始化，`false`表示已存在管理员账户。

**本文档引用的文件**  
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L37-L41)

### 初始化管理员
**: 端点信息**
- **HTTP方法**: POST
- **URL路径**: `/admins/init`
- **认证要求**: 无需认证

**: 请求体 (JSON Schema)**
```json
{
  "username": "admin",
  "password": "password123"
}
```
基于`AdminCreateParam`类。

**: 响应体 (JSON Schema)**
```json
{
  "id": "admin123",
  "username": "admin",
  "createdAt": "2023-10-01T10:00:00Z"
}
```
基于`AdminResult`类。

**本文档引用的文件**  
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L43-L48)
- [AdminCreateParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/AdminCreateParam.java)
- [AdminResult.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/result/AdminResult.java)

### 管理员登出
**: 端点信息**
- **HTTP方法**: POST
- **URL路径**: `/admins/logout`
- **认证要求**: 需要管理员认证

**: 请求头**
```
Authorization: Bearer <token>
```

**: 响应体**
无响应体，HTTP状态码为200。

**本文档引用的文件**  
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L32-L35)
- [TokenUtil.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/core/utils/TokenUtil.java)

### 获取当前登录管理员信息
**: 端点信息**
- **HTTP方法**: GET
- **URL路径**: `/admins`
- **认证要求**: 需要管理员认证

**: 响应体 (JSON Schema)**
```json
{
  "id": "admin123",
  "username": "admin",
  "createdAt": "2023-10-01T10:00:00Z"
}
```
基于`AdminResult`类。

**本文档引用的文件**  
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L70-L75)
- [AdminResult.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/result/AdminResult.java)

### 管理员修改密码
**: 端点信息**
- **HTTP方法**: PATCH
- **URL路径**: `/admins/password`
- **认证要求**: 需要管理员认证

**: 请求体 (JSON Schema)**
```json
{
  "oldPassword": "oldpassword123",
  "newPassword": "newpassword456"
}
```
基于`ResetPasswordParam`类。

**: 响应体**
无响应体，HTTP状态码为200。

**本文档引用的文件**  
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L56-L61)
- [ResetPasswordParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/ResetPasswordParam.java)

## 开发者管理API
提供开发者账户的注册、登录、信息管理等功能。

### 开发者注册
**: 端点信息**
- **HTTP方法**: POST
- **URL路径**: `/developers`
- **认证要求**: 无需认证

**: 请求体 (JSON Schema)**
```json
{
  "username": "devuser",
  "password": "devpassword123",
  "email": "dev@example.com"
}
```
基于`DeveloperCreateParam`类。

**: 响应体 (JSON Schema)**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "dev123",
    "username": "devuser",
    "email": "dev@example.com",
    "status": "PENDING"
  }
}
```
基于`AuthResponseResult`类。

**本文档引用的文件**  
- [DeveloperController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/DeveloperController.java#L25-L30)
- [DeveloperCreateParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/developer/DeveloperCreateParam.java)

### 开发者登录
**: 端点信息**
- **HTTP方法**: POST
- **URL路径**: `/developers/login`
- **认证要求**: 无需认证

**: 请求体 (JSON Schema)**
```json
{
  "username": "devuser",
  "password": "devpassword123"
}
```
基于`DeveloperLoginParam`类。

**: 响应体 (JSON Schema)**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": "dev123",
    "username": "devuser",
    "email": "dev@example.com",
    "status": "APPROVED"
  }
}
```
基于`AuthResponseResult`类。

**本文档引用的文件**  
- [DeveloperController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/DeveloperController.java#L32-L37)
- [DeveloperLoginParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/developer/DeveloperLoginParam.java)

### 获取当前开发者信息
**: 端点信息**
- **HTTP方法**: GET
- **URL路径**: `/developers/profile`
- **认证要求**: 需要开发者认证

**: 响应体 (JSON Schema)**
```json
{
  "id": "dev123",
  "username": "devuser",
  "email": "dev@example.com",
  "avatarUrl": "https://example.com/avatar.jpg",
  "status": "APPROVED",
  "createdAt": "2023-10-01T11:00:00Z"
}
```
基于`DeveloperResult`类。

**本文档引用的文件**  
- [DeveloperController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/DeveloperController.java#L59-L64)
- [DeveloperResult.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/result/DeveloperResult.java)

### 开发者修改密码
**: 端点信息**
- **HTTP方法**: PATCH
- **URL路径**: `/developers/password`
- **认证要求**: 需要开发者认证

**: 请求体 (JSON Schema)**
```json
{
  "oldPassword": "oldpassword123",
  "newPassword": "newpassword456"
}
```
基于`ResetPasswordParam`类。

**: 响应体**
```json
"修改密码成功"
```

**本文档引用的文件**  
- [DeveloperController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/DeveloperController.java#L66-L71)
- [ResetPasswordParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/admin/ResetPasswordParam.java)

### 开发者更新个人信息
**: 端点信息**
- **HTTP方法**: PUT
- **URL路径**: `/developers/profile`
- **认证要求**: 需要开发者认证

**: 请求体 (JSON Schema)**
```json
{
  "username": "newusername",
  "email": "newemail@example.com",
  "avatarUrl": "https://example.com/newavatar.jpg"
}
```
基于`UpdateDeveloperProfileParam`类。

**: 响应体**
```json
"更新个人信息成功"
```

**本文档引用的文件**  
- [DeveloperController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/DeveloperController.java#L73-L78)
- [UpdateDeveloperProfileParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/developer/UpdateDeveloperProfileParam.java)

## API产品管理API
提供API产品的创建、发布、更新、删除等全生命周期管理功能。

### 创建API产品
**: 端点信息**
- **HTTP方法**: POST
- **URL路径**: `/products`
- **认证要求**: 需要管理员认证

**: 请求体 (JSON Schema)**
```json
{
  "name": "天气API",
  "description": "提供天气预报服务",
  "type": "API",
  "icon": "cloud"
}
```
基于`CreateProductParam`类。

**: 响应体 (JSON Schema)**
```json
{
  "id": "prod123",
  "name": "天气API",
  "description": "提供天气预报服务",
  "type": "API",
  "icon": "cloud",
  "status": "DRAFT",
  "createdAt": "2023-10-01T13:00:00Z",
  "updatedAt": "2023-10-01T13:00:00Z"
}
```
基于`ProductResult`类。

**本文档引用的文件**  
- [ProductController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/ProductController.java#L25-L30)
- [CreateProductParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/product/CreateProductParam.java)
- [ProductResult.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/result/ProductResult.java)

### 获取API产品列表
**: 端点信息**
- **HTTP方法**: GET
- **URL路径**: `/products`
- **认证要求**: 无需认证

**: 查询参数**
- `name` (可选): 产品名称关键字
- `type` (可选): 产品类型
- `status` (可选): 产品状态

**: 分页参数**
- `page`: 页码（从0开始）
- `size`: 每页数量
- `sort`: 排序字段

**: 响应体 (JSON Schema)**
```json
{
  "content": [
    {
      "id": "prod123",
      "name": "天气API",
      "description": "提供天气预报服务",
      "type": "API",
      "icon": "cloud",
      "status": "PUBLISHED",
      "createdAt": "2023-10-01T13:00:00Z",
      "updatedAt": "2023-10-01T13:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```
基于`PageResult<ProductResult>`类。

**本文档引用的文件**  
- [ProductController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/ProductController.java#L32-L37)
- [QueryProductParam.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/params/product/QueryProductParam.java)
- [PageResult.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/dto/result/PageResult.java)

### 发布API产品
**: 端点信息**
- **HTTP方法**: POST
- **URL路径**: `/products/{productId}/publications/{portalId}`
- **认证要求**: 需要管理员认证

**: 路径参数**
- `productId`: API产品ID
- `portalId`: 门户ID

**: 响应体**
无响应体，HTTP状态码为200。

**本文档引用的文件**  
- [ProductController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/ProductController.java#L50-L55)

### 下线API产品
**: 端点信息**
- **HTTP方法**: DELETE
- **URL路径**: `/products/{productId}/publications/{portalId}`
- **认证要求**: 需要管理员认证

**: 路径参数**
- `productId`: API产品ID
- `portalId`: 门户ID

**: 响应体**
无响应体，HTTP状态码为200。

**本文档引用的文件**  
- [ProductController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/ProductController.java#L68-L73)

### 删除API产品
**: 端点信息**
- **HTTP方法**: DELETE
- **URL路径**: `/products/{productId}`
- **认证要求**: 需要管理员认证

**: 路径参数**
- `productId`: API产品ID

**: 响应体**
无响应体，HTTP状态码为200。

**本文档引用的文件**  
- [ProductController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/ProductController.java#L62-L66)

## curl调用示例

### 管理员登录示例
```bash
curl -X POST "http://localhost:8080/admins/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "password123"
  }'
```

### 创建产品示例
```bash
curl -X POST "http://localhost:8080/products" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "name": "天气API",
    "description": "提供天气预报服务",
    "type": "API",
    "icon": "cloud"
  }'
```

### 发布产品示例
```bash
curl -X POST "http://localhost:8080/products/prod123/publications/portal456" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**本文档引用的文件**  
- [AdministratorController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/AdministratorController.java#L25-L30)
- [ProductController.java](file://portal-server/src/main/java/com/alibaba/apiopenplatform/controller/ProductController.java#L25-L30)