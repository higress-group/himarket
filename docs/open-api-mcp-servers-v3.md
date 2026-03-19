# HiMarket MCP Server 开放接口文档 v3

> v3 变更说明：
> - 新增 `extraParams.position` 参数路由机制说明（header/query/env 分流）
> - 补充沙箱托管部署时参数注入行为的详细描述
> - 完善 `sandboxRequired` 字段的自动推断规则说明

## 概述

本文档描述 HiMarket 对外开放的 MCP Server 管理接口，供外部系统通过 API 注册和查询 MCP Server。

- 基础路径：`/open-api/mcp-servers`
- 鉴权方式：请求头 `X-API-Key`
- 响应格式：统一包装为 `{ "code": "SUCCESS", "data": T }`
- 接口数量：5 个（1 个写入 + 4 个查询）

---

## 鉴权

所有接口均需在请求头中携带 API Key：

```
X-API-Key: your-api-key-here
```

API Key 由 HiMarket 管理员分配，对应服务端配置项 `open-api.api-key`。

鉴权失败返回：

```json
{
  "code": "UNAUTHORIZED",
  "message": "Invalid API Key"
}
```

---

## 错误处理

所有错误统一返回以下格式：

```json
{
  "code": "错误码",
  "message": "错误描述"
}
```

| 错误码 | HTTP 状态码 | 说明 |
|--------|------------|------|
| `UNAUTHORIZED` | 401 | API Key 无效 |
| `NOT_FOUND` | 404 | 资源不存在（如 mcpServerId 或 mcpName 查不到） |
| `INVALID_REQUEST` | 400 | 请求参数校验失败（如必填字段为空、格式不合法） |

参数校验失败时，`message` 会包含具体的字段错误信息，例如：

```json
{
  "code": "INVALID_REQUEST",
  "message": "MCP 英文名称不能为空"
}
```

---

## 分页说明

分页接口支持以下查询参数：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | 0 | 页码，从 0 开始 |
| `size` | int | 20 | 每页条数 |
| `sort` | string | - | 排序，如 `createAt,desc` |

分页响应结构：

```json
{
  "code": "SUCCESS",
  "data": {
    "content": [ ... ],
    "number": 1,
    "size": 20,
    "totalElements": 100
  }
}
```

| 字段 | 说明 |
|------|------|
| `content` | 当前页数据列表 |
| `number` | 当前页码（从 1 开始） |
| `size` | 每页条数 |
| `totalElements` | 总记录数 |

---

## 接口列表总览

| # | 方法 | 路径 | 说明 |
|---|------|------|------|
| 1 | POST | `/open-api/mcp-servers/register` | 注册 MCP Server |
| 2 | GET | `/open-api/mcp-servers/meta/{mcpServerId}` | 按 ID 查询详情 |
| 3 | GET | `/open-api/mcp-servers/meta/by-name/{mcpName}` | 按名称查询详情 |
| 4 | GET | `/open-api/mcp-servers/meta/list` | 按来源分页查询列表 |
| 5 | GET | `/open-api/mcp-servers/meta/list-all` | 分页查询所有列表 |

---

## 接口详情

### 1. 注册 MCP Server

注册一个新的 MCP Server。系统会自动创建关联资源，注册后状态为 `PENDING`，需管理员审核发布后才会在市场中展示。

```
POST /open-api/mcp-servers/register
```

#### 请求头

| 名称 | 必填 | 说明 |
|------|------|------|
| `Content-Type` | ✅ | `application/json` |
| `X-API-Key` | ✅ | API Key |

#### 请求参数

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| `mcpName` | string | ✅ | 最长 63 字符 | MCP 英文标识名，全局唯一 |
| `displayName` | string | ✅ | 最长 128 字符 | 展示名称 |
| `protocolType` | string | ✅ | 枚举值，逗号分隔 | 协议类型，支持多值，见下方说明 |
| `connectionConfig` | string | ✅ | 合法 JSON 字符串 | 连接配置，见下方说明。非 stdio 协议时必须包含有效的连接地址（url） |
| `origin` | string | | 枚举值 | 来源标识，默认 `OPEN_API`，见下方说明 |
| `createdBy` | string | | | 外部系统的用户ID，用于标识注册者 |
| `description` | string | | | 简短描述 |
| `repoUrl` | string | | | 仓库或主页地址 |
| `tags` | string | | 逗号分隔 | 标签，如 `"天气,查询,工具"` |
| `icon` | string | | JSON 字符串 | 图标，见下方说明 |
| `extraParams` | string | | JSON 字符串 | 用户订阅时需填写的额外参数定义，见下方说明 |
| `serviceIntro` | string | | Markdown | 服务详细介绍，支持 Markdown 格式 |
| `visibility` | string | | 枚举值 | `PUBLIC`（默认）或 `PRIVATE` |
| `toolsConfig` | string | | JSON 字符串 | 工具列表定义 |
| `sandboxRequired` | boolean | | | 是否需要沙箱托管，见下方自动推断规则 |

#### protocolType 取值

支持单个或多个协议，多个时用逗号分隔，如 `"stdio,sse"` 或 `"sse,http"`。

| 值 | 说明 |
|----|------|
| `stdio` | 标准输入输出，需通过沙箱运行 |
| `sse` | Server-Sent Events 协议 |
| `http` | Streamable HTTP 协议 |

示例：
- 仅 SSE：`"sse"`
- 同时支持 stdio 和 SSE：`"stdio,sse"`
- 同时支持 SSE 和 Streamable HTTP：`"sse,http"`

#### origin 取值

| 值 | 说明 |
|----|------|
| `OPEN_API` | 默认值，通过开放接口注册 |
| `AGENTRUNTIME` | 由 AgentRuntime 注册 |

#### sandboxRequired 自动推断规则

如果未显式传入 `sandboxRequired`，系统会根据以下规则自动推断：

| 条件 | sandboxRequired |
|------|----------------|
| protocolType 包含 `stdio` | 强制 `true`（不可覆盖） |
| origin 为 `GATEWAY` 或 `NACOS` | 默认 `false` |
| 其他情况（SSE/HTTP 自定义注册等） | 默认 `true` |

当 `sandboxRequired=true` 时，用户订阅该 MCP 必须通过沙箱部署（选择沙箱实例 + 传输类型），不能直接连接。

#### connectionConfig 格式

connectionConfig 是一个 JSON 字符串，格式取决于 protocolType。`mcpServers` 下可包含多个 server 配置，对应不同协议。

**stdio 类型：**

```json
{
  "mcpServers": {
    "my-mcp": {
      "command": "npx",
      "args": ["-y", "@example/mcp-server"],
      "env": {
        "API_KEY": "your-key"
      }
    }
  }
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `command` | ✅ | 启动命令，如 `npx`、`uvx`、`node` |
| `args` | | 命令参数数组 |
| `env` | | 环境变量键值对 |

**SSE 类型：**

```json
{
  "mcpServers": {
    "my-mcp": {
      "type": "sse",
      "url": "https://mcp.example.com/sse"
    }
  }
}
```

**Streamable HTTP 类型：**

```json
{
  "mcpServers": {
    "my-mcp": {
      "url": "https://mcp.example.com/mcp"
    }
  }
}
```

**多协议示例（protocolType 为 `"stdio,sse"`）：**

```json
{
  "mcpServers": {
    "my-mcp-stdio": {
      "command": "npx",
      "args": ["-y", "@example/mcp-server"],
      "env": { "API_KEY": "your-key" }
    },
    "my-mcp-sse": {
      "type": "sse",
      "url": "https://mcp.example.com/sse"
    }
  }
}
```

> 注意：connectionConfig 作为字符串传入时需要转义双引号。

> 校验规则：当 protocolType 不包含 `stdio`（即纯 `sse` 或 `http`）时，系统会校验 connectionConfig 中是否包含有效的连接地址（url）。如果缺少连接地址或格式无法解析，注册会返回 `INVALID_REQUEST` 错误。stdio 协议不做此校验，因为 stdio 类型通过沙箱部署运行，不需要预设连接地址。

#### toolsConfig 格式

```json
{
  "tools": [
    {
      "name": "get_weather",
      "description": "查询指定城市的天气信息",
      "inputSchema": {
        "type": "object",
        "properties": {
          "city": {
            "type": "string",
            "description": "城市名称"
          }
        },
        "required": ["city"]
      }
    }
  ]
}
```

每个 tool 的字段：

| 字段 | 必填 | 说明 |
|------|------|------|
| `name` | ✅ | 工具名称 |
| `description` | | 工具描述 |
| `inputSchema` | | JSON Schema 格式的参数定义 |

#### extraParams 格式

定义用户订阅该 MCP 时需要填写的额外参数。参数的 `position` 字段决定了沙箱部署时参数的注入位置。

```json
[
  {
    "key": "api_key",
    "name": "X-API-Key",
    "position": "header",
    "required": true,
    "description": "第三方服务的 API Key",
    "example": "sk-xxxx"
  },
  {
    "key": "region",
    "name": "region",
    "position": "query",
    "required": false,
    "description": "服务区域",
    "example": "cn-hangzhou"
  },
  {
    "key": "db_password",
    "name": "DB_PASSWORD",
    "position": "env",
    "required": true,
    "description": "数据库密码",
    "example": "your-password"
  }
]
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `key` | ✅ | 参数标识 |
| `name` | ✅ | 参数显示名称（同时作为注入时的 key） |
| `position` | | 参数注入位置，默认 `env`，见下方路由规则 |
| `required` | | 是否必填，默认 false |
| `description` | | 参数说明 |
| `example` | | 示例值 |

#### extraParams.position 参数路由规则（v3 新增）

当用户通过沙箱订阅 SSE/HTTP 类型的 MCP 时，系统会根据 `position` 将用户填写的参数值注入到不同位置：

| position | 注入位置 | 说明 |
|----------|---------|------|
| `header` | mcpServers JSON 的 `headers` 字段 | 作为 HTTP 请求头传递给 MCP Server |
| `query` | mcpServers JSON 的 `url` query string | URL 编码后追加到连接地址的查询参数 |
| `env` | CRD `spec.env` 环境变量 | 作为容器环境变量注入（默认行为） |

> stdio 类型不走分流，所有参数统一作为 `env` 处理。

**示例：混合参数注入**

extraParams 定义：
```json
[
  {"name": "X-API-Key", "position": "header", "required": true},
  {"name": "region", "position": "query"},
  {"name": "DB_PASSWORD", "position": "env", "required": true}
]
```

用户提交的参数值：
```json
{"X-API-Key": "key-123", "region": "cn-hangzhou", "DB_PASSWORD": "secret"}
```

原始 connectionConfig 中的 mcpServers：
```json
{"mcpServers": {"data-mcp": {"url": "https://data.example.com/mcp"}}}
```

部署后实际效果：
- mcpServers JSON（下发到 CRD）：
```json
{"mcpServers": {"data-mcp": {"url": "https://data.example.com/mcp?region=cn-hangzhou", "headers": {"X-API-Key": "key-123"}}}}
```
- CRD spec.env：
```yaml
env:
- name: DB_PASSWORD
  value: secret
```

#### icon 格式

```json
{"type": "URL", "url": "https://example.com/icon.png"}
```

#### 完整请求示例

```bash
curl -X POST 'https://himarket.example.com/open-api/mcp-servers/register' \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: your-api-key' \
  -d '{
    "mcpName": "weather-mcp",
    "displayName": "天气查询 MCP",
    "description": "提供全球天气查询能力",
    "protocolType": "sse",
    "connectionConfig": "{\"mcpServers\":{\"weather-mcp\":{\"type\":\"sse\",\"url\":\"https://weather.example.com/sse\"}}}",
    "tags": "天气,查询,工具",
    "extraParams": "[{\"name\":\"X-API-Key\",\"position\":\"header\",\"required\":true,\"description\":\"天气服务 API Key\",\"example\":\"sk-xxxx\"}]",
    "serviceIntro": "## 天气查询 MCP\n\n支持全球主要城市天气实时查询，包括温度、湿度、风速等信息。",
    "toolsConfig": "{\"tools\":[{\"name\":\"get_weather\",\"description\":\"查询指定城市天气\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"城市名称\"}},\"required\":[\"city\"]}}]}"
  }'
```

#### 成功响应

```json
{
  "code": "SUCCESS",
  "data": {
    "mcpServerId": "mcp-a1b2c3d4",
    "mcpName": "weather-mcp",
    "displayName": "天气查询 MCP",
    "description": "提供全球天气查询能力",
    "repoUrl": null,
    "icon": null,
    "protocolType": "sse",
    "connectionConfig": "{\"mcpServers\":{\"weather-mcp\":{\"type\":\"sse\",\"url\":\"https://weather.example.com/sse\"}}}",
    "origin": "OPEN_API",
    "tags": "天气,查询,工具",
    "extraParams": "[{\"name\":\"X-API-Key\",\"position\":\"header\",\"required\":true,...}]",
    "serviceIntro": "## 天气查询 MCP\n\n支持全球主要城市天气实时查询...",
    "visibility": "PUBLIC",
    "publishStatus": "PENDING",
    "toolsConfig": "{\"tools\":[...]}",
    "sandboxRequired": true,
    "createdBy": null,
    "createAt": "2026-03-14T10:30:00"
  }
}
```

> 请保存返回的 `mcpServerId`，后续查询需要用到。

#### 失败响应示例

mcpName 为空：
```json
{ "code": "INVALID_REQUEST", "message": "MCP 英文名称不能为空" }
```

非 stdio 协议缺少连接地址：
```json
{ "code": "INVALID_REQUEST", "message": "非 stdio 协议必须提供 connectionConfig（包含连接地址）" }
```

connectionConfig 格式错误：
```json
{ "code": "INVALID_REQUEST", "message": "connectionConfig 格式错误或缺少连接地址: ..." }
```

---

### 2. 按 mcpServerId 查询详情

```
GET /open-api/mcp-servers/meta/{mcpServerId}
```

#### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `mcpServerId` | string | ✅ | 注册时返回的 MCP Server ID |

#### 请求示例

```bash
curl -X GET 'https://himarket.example.com/open-api/mcp-servers/meta/mcp-a1b2c3d4' \
  -H 'X-API-Key: your-api-key'
```

#### 成功响应

```json
{
  "code": "SUCCESS",
  "data": {
    "mcpServerId": "mcp-a1b2c3d4",
    "mcpName": "weather-mcp",
    "displayName": "天气查询 MCP",
    "description": "提供全球天气查询能力",
    "repoUrl": null,
    "icon": null,
    "protocolType": "sse",
    "connectionConfig": "{\"mcpServers\":{\"weather-mcp\":{\"type\":\"sse\",\"url\":\"https://weather.example.com/sse\"}}}",
    "origin": "OPEN_API",
    "tags": "天气,查询,工具",
    "extraParams": "[{\"name\":\"X-API-Key\",\"position\":\"header\",...}]",
    "serviceIntro": "## 天气查询 MCP\n\n...",
    "visibility": "PUBLIC",
    "publishStatus": "PENDING",
    "toolsConfig": "{\"tools\":[...]}",
    "sandboxRequired": true,
    "createdBy": null,
    "createAt": "2026-03-14T10:30:00"
  }
}
```

#### 失败响应

```json
{ "code": "NOT_FOUND", "message": "MCP_SERVER_META not found: mcp-a1b2c3d4" }
```

---

### 3. 按 mcpName 查询详情

```
GET /open-api/mcp-servers/meta/by-name/{mcpName}
```

#### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `mcpName` | string | ✅ | MCP 英文名称 |

#### 请求示例

```bash
curl -X GET 'https://himarket.example.com/open-api/mcp-servers/meta/by-name/weather-mcp' \
  -H 'X-API-Key: your-api-key'
```

#### 响应

同接口 2，返回完整详情。不存在时返回 `NOT_FOUND`。

---

### 4. 分页查询指定来源的 MCP 列表

返回精简信息，不含 toolsConfig、serviceIntro 等详细字段。

```
GET /open-api/mcp-servers/meta/list
```

#### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `origin` | string | | `OPEN_API` | 来源筛选 |
| `page` | int | | 0 | 页码（从 0 开始） |
| `size` | int | | 20 | 每页条数 |

origin 取值：

| 值 | 说明 |
|----|------|
| `OPEN_API` | 通过本接口注册的 |
| `AGENTRUNTIME` | 由 AgentRuntime 注册的 |
| `GATEWAY` | 从网关导入的 |
| `NACOS` | 从 Nacos 导入的 |
| `ADMIN` | 管理员手动创建的 |

#### 请求示例

```bash
# 查询自己注册的 MCP（默认 origin=OPEN_API）
curl -X GET 'https://himarket.example.com/open-api/mcp-servers/meta/list?page=0&size=10' \
  -H 'X-API-Key: your-api-key'

# 查询所有网关导入的 MCP
curl -X GET 'https://himarket.example.com/open-api/mcp-servers/meta/list?origin=GATEWAY&page=0&size=10' \
  -H 'X-API-Key: your-api-key'
```

#### 成功响应

```json
{
  "code": "SUCCESS",
  "data": {
    "content": [
      {
        "mcpServerId": "mcp-a1b2c3d4",
        "mcpName": "weather-mcp",
        "displayName": "天气查询 MCP",
        "description": "提供全球天气查询能力",
        "icon": null,
        "protocolType": "sse",
        "origin": "OPEN_API",
        "tags": "天气,查询,工具",
        "publishStatus": "PENDING",
        "sandboxRequired": true,
        "createAt": "2026-03-14T10:30:00"
      }
    ],
    "number": 1,
    "size": 10,
    "totalElements": 1
  }
}
```

> 列表返回精简字段。需要 toolsConfig、serviceIntro 等完整信息，请用接口 2 或 3 查询详情。

---

### 5. 分页查询所有 MCP 列表

不区分来源，查询系统中所有 MCP Server，返回精简信息。

```
GET /open-api/mcp-servers/meta/list-all
```

#### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `page` | int | | 0 | 页码（从 0 开始） |
| `size` | int | | 20 | 每页条数 |

#### 请求示例

```bash
curl -X GET 'https://himarket.example.com/open-api/mcp-servers/meta/list-all?page=0&size=20' \
  -H 'X-API-Key: your-api-key'
```

#### 响应

同接口 4，返回精简分页结构。

---

## 返回数据结构

### 详情接口返回字段（接口 1、2、3）

| 字段 | 类型 | 说明 |
|------|------|------|
| `mcpServerId` | string | MCP Server 唯一 ID（系统生成） |
| `mcpName` | string | MCP 英文名称 |
| `displayName` | string | 展示名称 |
| `description` | string | 描述信息 |
| `repoUrl` | string | 仓库/主页地址 |
| `icon` | string | 图标配置 JSON |
| `protocolType` | string | 协议类型：`stdio` / `sse` / `http` |
| `connectionConfig` | string | 连接配置 JSON 字符串 |
| `origin` | string | 来源：`OPEN_API` / `GATEWAY` / `NACOS` / `ADMIN` |
| `tags` | string | 标签（逗号分隔） |
| `extraParams` | string | 额外参数定义 JSON |
| `serviceIntro` | string | 服务介绍（Markdown） |
| `visibility` | string | 可见性：`PUBLIC` / `PRIVATE` |
| `publishStatus` | string | 发布状态（见下方说明） |
| `toolsConfig` | string | 工具列表配置 JSON |
| `sandboxRequired` | boolean | 是否需要沙箱托管 |
| `createdBy` | string | 创建者 |
| `createAt` | string | 创建时间，格式 `yyyy-MM-ddTHH:mm:ss` |

### 列表接口返回字段（接口 4、5）

| 字段 | 类型 | 说明 |
|------|------|------|
| `mcpServerId` | string | MCP Server 唯一 ID |
| `mcpName` | string | MCP 英文名称 |
| `displayName` | string | 展示名称 |
| `description` | string | 描述信息 |
| `icon` | string | 图标配置 JSON |
| `protocolType` | string | 协议类型 |
| `origin` | string | 来源 |
| `tags` | string | 标签 |
| `publishStatus` | string | 发布状态 |
| `sandboxRequired` | boolean | 是否需要沙箱托管 |
| `createAt` | string | 创建时间 |

### publishStatus 状态说明

| 状态 | 说明 |
|------|------|
| `PENDING` | 待审核（新注册的默认状态） |
| `DRAFT` | 草稿 |
| `PUBLISHED` | 已发布（在市场中可见） |

---

## 典型使用流程

```
1. 调用注册接口，提交 MCP Server 信息
   POST /open-api/mcp-servers/register
   → 保存返回的 mcpServerId

2. 等待管理员审核发布
   可通过查询接口轮询 publishStatus 是否变为 PUBLISHED

3. 查询已注册的 MCP
   - 查单个详情：GET /meta/{mcpServerId} 或 GET /meta/by-name/{mcpName}
   - 查自己注册的列表：GET /meta/list（默认 origin=OPEN_API）
   - 查所有 MCP 列表：GET /meta/list-all
```

---

## 注意事项

1. 所有查询接口不返回 `productId`，这是系统内部字段
2. 注册后的 MCP 状态为 `PENDING`，需管理员审核发布后才会在市场中展示
3. `mcpName` 全局唯一，重复注册会更新已有记录（按 mcpName 匹配）
4. `connectionConfig` 和 `toolsConfig` 等 JSON 字段，作为请求参数时需要以字符串形式传入（注意转义双引号）
5. 分页页码从 0 开始，但响应中的 `number` 从 1 开始
6. `extraParams` 中的 `position` 字段仅在沙箱部署时生效；直连订阅（非沙箱）不涉及参数注入
7. `sandboxRequired` 未传时系统自动推断，stdio 强制为 true 不可覆盖
