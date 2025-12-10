# API 集成实现说明

## 已完成功能

### 1. 环境配置
- ✅ 配置临时 API 地址和 Token（`.env`）
- ✅ 更新开发环境代理配置（`vite.config.ts`）
- ✅ API 请求拦截器自动添加 Authorization header

### 2. Square 页面（体验中心）
- ✅ 从真实 API 获取模型列表（`GET /products?type=MODEL_API`）
- ✅ 添加加载状态和错误处理
- ✅ 点击"立即体验"跳转到 Chat 页面并传递产品信息

### 3. Chat 页面（聊天功能）

#### 会话管理
- ✅ 首次发送消息时自动创建会话（`POST /sessions`）
- ✅ 使用问题前 20 个字符作为会话名称
- ✅ 自动生成符合后端要求的 UUID（24位小写字母+数字）

#### 消息发送
- ✅ 支持流式响应（SSE）
- ✅ 支持非流式响应（普通 JSON）
- ✅ 正确处理 conversationId、questionId、answerIndex
- ✅ 自动管理会话上下文（needMemory）

#### 流式响应
- ✅ 实时显示 AI 回复（逐字显示）
- ✅ 处理 SSE 事件（start、chunk、complete、error）
- ✅ 显示统计信息（总耗时、输入/输出 tokens）

#### 历史记录
- ✅ 加载会话的历史聊天记录（`GET /sessions/{sessionId}/conversations`）
- ✅ 将后端数据结构转换为前端消息格式
- ✅ 支持多轮对话历史

### 4. Sidebar（会话列表）
- ✅ 从真实 API 获取会话列表（`GET /sessions`）
- ✅ 按时间分类显示（今天、近7天、近30天）
- ✅ 点击会话加载历史记录
- ✅ 显示加载状态和空状态

## 技术实现

### API 接口
所有接口都在 `src/lib/api.ts` 中定义：

```typescript
// 获取模型列表
getProducts({ type: "MODEL_API", page: 0, size: 100 })

// 创建会话
createSession({ talkType: "MODEL", name: "...", productIds: [...] })

// 获取会话列表
getSessions({ page: 0, size: 50 })

// 更新会话名称
updateSession(sessionId, { name: "..." })

// 发送聊天消息（非流式）
sendChatMessage(sessionId, { conversationId, questionId, ... })

// 获取历史聊天记录
getConversations(sessionId)
```

### 流式响应处理
使用 SSE (Server-Sent Events) 处理流式响应，实现在 `src/lib/sse.ts`：

```typescript
handleSSEStream(url, options, {
  onStart: (chatId) => { /* 开始接收 */ },
  onChunk: (content, chatId) => { /* 接收内容块 */ },
  onComplete: (fullContent, chatId) => { /* 接收完成 */ },
  onError: (error, code) => { /* 错误处理 */ }
})
```

### UUID 生成
符合后端要求的 UUID 格式（`src/lib/uuid.ts`）：

```typescript
// conversation-{24位小写字母+数字}
generateConversationId()

// question-{24位小写字母+数字}
generateQuestionId()
```

## 配置说明

### 环境变量（`.env`）
```bash
VITE_API_BASE_URL=/api/v1
VITE_TEMP_API_URL=http://nlb-3h99tgwx37r9q3pzu1.cn-shanghai.nlb.aliyuncsslb.com
VITE_TEMP_AUTH_TOKEN=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...
```

### 代理配置（`vite.config.ts`）
开发环境的请求会自动代理到临时 API 地址：
- 前端：`http://0.0.0.0:5173`
- 后端：临时地址（从环境变量读取）
- API 前缀：`/api/v1`

## 数据流程

### 1. 新对话流程
```
用户输入问题
  ↓
创建会话（如果是首次）
  ↓
生成 conversationId 和 questionId
  ↓
发送消息（流式/非流式）
  ↓
接收 AI 回复
  ↓
显示在界面上
```

### 2. 加载历史记录流程
```
点击 Sidebar 中的会话
  ↓
调用 getConversations API
  ↓
解析后端数据结构
  ↓
转换为前端消息格式
  ↓
显示在聊天区域
```

### 3. 流式响应流程
```
发送消息请求（stream: true）
  ↓
建立 SSE 连接
  ↓
接收 start 事件（获取 chatId）
  ↓
接收 chunk 事件（逐字显示）
  ↓
接收 complete 事件（显示统计信息）
  ↓
或接收 error 事件（错误处理）
```

## 后端数据结构

### 会话数据（Session）
```json
{
  "sessionId": "session-12345",
  "name": "新的对话",
  "productIds": ["product-xxx"],
  "talkType": "MODEL",
  "status": "NORMAL",
  "createAt": "2024-01-15T10:30:00Z",
  "updateAt": "2024-01-15T10:30:00Z"
}
```

### 历史对话数据（Conversation）
```json
{
  "conversationId": "conversation-001",
  "questions": [
    {
      "questionId": "question-001",
      "content": "用户的问题内容",
      "attachments": [],
      "answers": [
        {
          "results": [
            {
              "answerId": "answer-001",
              "productId": "product-xxx",
              "content": "AI的回答内容"
            }
          ]
        }
      ]
    }
  ]
}
```

### SSE 事件格式
```
data: {"status":"start","chatId":"chat-001"}
data: {"status":"chunk","chatId":"chat-001","content":"人工智能"}
data: {"status":"chunk","chatId":"chat-001","content":"是计算机科学"}
data: {"status":"complete","chatId":"chat-001","fullContent":"人工智能是计算机科学..."}
data: [DONE]
```

## 启动项目

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 类型检查
npm run type-check

# 构建生产版本
npm run build
```

## 注意事项

1. **临时配置**：当前 API 地址和 token 都是临时的，后续需要替换
2. **流式响应**：默认开启流式响应（`useStream: true`）
3. **错误处理**：所有 API 调用都有错误处理和用户提示
4. **Token 统计**：当前使用的是估算值，后续需要从 API 响应中获取真实值
5. **多模型对比**：前端界面已支持，但后端集成需要进一步调整

## 待优化项

- [ ] 从 API 响应中获取真实的 token 统计信息
- [ ] 实现多模型对比的真实 API 调用
- [ ] 添加会话重命名功能
- [ ] 添加删除会话功能
- [ ] 优化流式响应的性能
- [ ] 添加更详细的错误提示
- [ ] 支持多模态附件上传
