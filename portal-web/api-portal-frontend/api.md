# 接口文档

接口返回数据格式, 数据都在 data 内返回

```json
{
  "code": "string",
  "data": {},
  "message": "string"
}
```

## 获取模型分类

### GET /product-categories

请求参数：

```json
{
  "productType": "string" // 枚举值： MCP_SERVER、MODEL_API、REST_API、AGENT_API, 分别对应页面上的四个 Tab
}
```

响应示例：

```json
{
  "content": [
    {
      "categoryId": "string",
      "name": "string",
      "description": "string",
      "icon": {
        "type": "URL",
        "value": "string"
      },
      "createAt": "2025-11-21T09:10:36.735Z",
      "updatedAt": "2025-11-21T09:10:36.735Z"
    }
  ],
  "number": 0,
  "size": 0,
  "totalElements": 0
}
```

## 获取模型列表

### GET /products

请求参数：

```json
{
  "type": "MODEL_API", // 枚举值： MCP_SERVER、MODEL_API、REST_API、AGENT_API, 分别对应页面上的四个 Tab
  "categoryIds": ["string"], // 可选参数，类别 id
  "page": 0,
  "size": 100
}
```

响应示例：

```json
{
  "code": "SUCCESS",
  "message": null,
  "data": {
    "content": [
      {
        "productId": "product-6915c88f1096603f3d38a659",
        "name": "OpenAI",
        "description": "open ai",
        "status": "PUBLISHED",
        "enableConsumerAuth": false,
        "type": "MODEL_API",
        "document": null,
        "icon": null,
        "categories": [],
        "autoApprove": null,
        "createAt": "2025-11-13T20:01:19.568",
        "updatedAt": "2025-11-21T05:57:05.605",
        "apiConfig": null,
        "mcpConfig": null,
        "agentConfig": null,
        "modelConfig": {
          "modelAPIConfig": {
            "modelCategory": "Text",
            "aiProtocols": [
              "OpenAI/v1"
            ],
            "routes": [
              {
                "domains": [
                  {
                    "domain": "autoapi.oidcai.1758849267929.com",
                    "protocol": "HTTP",
                    "networkType": null
                  },
                  {
                    "domain": "env-d2r4j0em1hkgipeic1sg-cn-hangzhou.alicloudapi.com",
                    "protocol": "HTTP",
                    "networkType": "Internet"
                  },
                  {
                    "domain": "env-d2r4j0em1hkgipeic1sg-cn-hangzhou.vpc.alicloudapi.com",
                    "protocol": "HTTP",
                    "networkType": "Intranet"
                  }
                ],
                "description": "Creates a model response for the given chat conversation.",
                "match": {
                  "methods": [
                    "POST"
                  ],
                  "path": {
                    "value": "/aaa/v1/chat/completions",
                    "type": "Exact"
                  },
                  "headers": null,
                  "queryParams": null
                },
                "backend": {
                  "scene": "SingleService",
                  "services": [
                    {
                      "name": "ai-service.ai",
                      "port": null,
                      "protocol": null,
                      "weight": 100
                    }
                  ]
                },
                "builtin": true
              },
              {
                "domains": [
                  {
                    "domain": "autoapi.oidcai.1758849267929.com",
                    "protocol": "HTTP",
                    "networkType": null
                  },
                  {
                    "domain": "env-d2r4j0em1hkgipeic1sg-cn-hangzhou.alicloudapi.com",
                    "protocol": "HTTP",
                    "networkType": "Internet"
                  },
                  {
                    "domain": "env-d2r4j0em1hkgipeic1sg-cn-hangzhou.vpc.alicloudapi.com",
                    "protocol": "HTTP",
                    "networkType": "Intranet"
                  }
                ],
                "description": "Creates a completion for the provided prompt and parameters.",
                "match": {
                  "methods": [
                    "POST"
                  ],
                  "path": {
                    "value": "/aaa/v1/completions",
                    "type": "Exact"
                  },
                  "headers": null,
                  "queryParams": null
                },
                "backend": {
                  "scene": "SingleService",
                  "services": [
                    {
                      "name": "ai-service.ai",
                      "port": null,
                      "protocol": null,
                      "weight": 100
                    }
                  ]
                },
                "builtin": true
              },
              {
                "domains": [
                  {
                    "domain": "autoapi.oidcai.1758849267929.com",
                    "protocol": "HTTP",
                    "networkType": null
                  },
                  {
                    "domain": "env-d2r4j0em1hkgipeic1sg-cn-hangzhou.alicloudapi.com",
                    "protocol": "HTTP",
                    "networkType": "Internet"
                  },
                  {
                    "domain": "env-d2r4j0em1hkgipeic1sg-cn-hangzhou.vpc.alicloudapi.com",
                    "protocol": "HTTP",
                    "networkType": "Intranet"
                  }
                ],
                "description": "Creates a model response. Provide text or image inputs to generate text or JSON outputs.",
                "match": {
                  "methods": [
                    "POST"
                  ],
                  "path": {
                    "value": "/aaa/v1/responses",
                    "type": "Exact"
                  },
                  "headers": null,
                  "queryParams": null
                },
                "backend": {
                  "scene": "SingleService",
                  "services": [
                    {
                      "name": "ai-service.ai",
                      "port": null,
                      "protocol": null,
                      "weight": 100
                    }
                  ]
                },
                "builtin": true
              }
            ],
            "services": null
          }
        },
        "enabled": true
      }
    ],
    "number": 1,
    "size": 100,
    "totalElements": 1
  }
}
```

## 创建会话

### POST /sessions

调用时机：用户首次输入问题后，自动创建会话
请求参数:

```json
{
  "talkType": "MODEL", //必填
  "name": "string", // 会话名称（必填）
  "products": [] // 对话的 Model 列表（必填）， 多个 ModelId 表示多 Model 对比
}
```

响应示例：

```json
{
    "sessionId": "session-12345",
    "name": "新的对话",
    "status": "NORMAL",
    "createAt": "2024-01-15T10:30:00Z",
    "updateAt": "2024-01-15T10:30:00Z"
}
```

## 获取会话列表

### GET /sessions?page={page}&size={size}

调用时机：显示用户的历史会话列表

响应示例：

```json
{
    "totalElements": 50,
    "content": [
        {
            "sessionId": "session-12345",
            "name": "AI技术讨论",
            "products": ["gpt-4"],
            "createAt": "2024-01-15T10:30:00Z",
            "updateAt": "2024-01-15T15:30:00Z"
        }
    ],
    "number": 1,
    "size": 1
}
```

## 更新会话名称

### PUT /sessions/{sessionId}

调用时机：用户重命名会话时

```json
{
    "name": "更新的会话标题"
}
```

## 删除会话

### DELETE /sessions/{sessionId}

调用时机：删除会话时

## 聊天接口

### POST /chats

调用时机：用户发送消息的任何场景

请求参数：

```json
{
    "productId": "string",            // 模型 ID （必填）
    "conversationId": "string",       // 对话 ID (必填，前端生成)
    "questionId": "string",           // 问题 ID (必填，前端生成)
    "question": "string",             // 问题内容 (必填)
    "attachments": [],                // 多模态附件 (可选)
    "stream": true,                   // 是否流式响应 (可选，默认 true)
    "needMemory": true,               // 是否使用历史记忆 (可选，默认 true)
    "enableThinking": true,           // 是否开启思考模式（可选，默认 false）
    "serarchType": "string",           // 为空时表示关闭搜索功能，BING/GOOGLE/KNOWLEDGE 代表开启搜索以及采用的搜索类型
    "sessionId": "string"              // 会话 ID
}
```

流式响应格式（暂定）
当 stream: true 时，接口返回 Server-Sent Events (SSE) 格式。

响应头：

```text
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
```

响应事件：

- 开始事件

```text
data: {"status":"start","chatId":"chat-001"}
```

- 内容流事件

```text
data:{"id":"chatcmpl-0a0297d5-87cc-4190-a69b-da411faa65c8","object":"chat.completion.chunk","created":1763705190,"model":"qwen-max","choices":[{"delta":{"content":"你好"},"index":0}]}

data:{"id":"chatcmpl-0a0297d5-87cc-4190-a69b-da411faa65c8","object":"chat.completion.chunk","created":1763705190,"model":"qwen-max","choices":[{"delta":{"content":"！有什么可以帮助"},"index":0}]}

data:{"id":"chatcmpl-0a0297d5-87cc-4190-a69b-da411faa65c8","object":"chat.completion.chunk","created":1763705190,"model":"qwen-max","choices":[{"delta":{"content":"你的吗？"},"index":0}]}

data:{"id":"chatcmpl-0a0297d5-87cc-4190-a69b-da411faa65c8","object":"chat.completion.chunk","created":1763705190,"model":"qwen-max","choices":[{"delta":{"content":""},"index":0,"finish_reason":"stop"}]}
```

- 完成事件

```text
data:{"id":"chatcmpl-0a0297d5-87cc-4190-a69b-da411faa65c8","object":"chat.completion.chunk","created":1763705190,"usage":{"prompt_tokens":10,"completion_tokens":7,"total_tokens":17,"prompt_tokens_details":{"cached_tokens":0}},"model":"qwen-max","choices":[]}
```

- 结束标志

```text
data: [DONE]
```

- 错误事件

```text
data: {"status":"error","chatId":"chat-001","message":"模型调用失败","code":"MODEL_ERROR"}
```

块式响应格式（暂定）
当 stream: false 时，接口返回标准 JSON 响应

```json
{
  "success": true,
  "chatId":"chat-001",
  "question": "请介绍人工智能",
  "answer": "人工智能是计算机科学的一个重要分支...",
  "status": "COMPLETED"
}
```

三种操作场景参数说明

场景 1：新对话

```json
{
  "conversationId": "conversation-001",     // 新的对话 ID
  "questionId": "question-001",             // 新的问题 ID
  "question": "请介绍人工智能"
}
```

conversationId、questionId由前端生成，格式分别为conversation-{xxx}和question-{xxx}，xxx 表示一个长度为 24 的由小写字母和数字组合的 UUID（与后端的 UUID 逻辑相同）。

场景 2：编辑问题

```json
{
  "conversationId": "conversation-001",     // 相同的对话 ID
  "questionId": "question-001",             // 新的问题 ID
  "question": "请详细介绍人工智能的发展历史"
}
```

编辑问题时，conversationId不变，questionId重新生成，因为这是一个新版本的问题。

场景3：再来一次

```json
{
  "conversationId": "conversation-001",     // 相同的对话 ID
  "questionId": "question-001",             // 相同的问题 ID
  "question": "请介绍人工智能"                // 相同的问题内容
}
```

再来一次时，conversationId和questionId均不变，因为是同一个问题。

## 多 Product 对比

多 Product 对比时，输入问题后，每个 Product 都需要调用一次聊天接口，前端分别展示对应 Product 的响应。

## 历史聊天记录

历史聊天记录指的是点开会话后，查看会话下的聊天消息列表。

### GET /sessions/{sessionId}/conversations

调用时机：用户打开会话时，显示历史消息

响应示例：

```json
[
    {
        "conversationId": "conversation-001",
        "questions": [
            {
                "questionId": "question-001",
                "content": "用户的问题内容",
                "attachments": [

                ],
                "answers": [
                    {
                        "results": [
                            {
                                "answerId": "answer-001",
                                "productId": "gpt-4",
                                "content": "AI的回答内容"
                            }
                        ]
                    }
                ]
            }
        ]
    }
]
```

数据层级说明：

- conversationId: 对话标识；

- questions: 每个对话下的问题版本列表；

- answers: 每个问题的回答轮次列表，有多少个元素，就表示进行了多少轮，“再来一次”时可以根据元素数量；

- results: 每轮次的 Model API 的回答列表，用列表结构是因为可以设置多模型对比，results下存储的是多个 Model API 的回答。

