# API 迁移指南

本文档提供从旧的 `api.ts` 迁移到新的 `apis/` 目录结构的指南。

## 目录结构对比

### 旧结构
```
src/lib/
└── api.ts              # 所有 API 定义在一个文件中
```

### 新结构
```
src/lib/
├── request.ts          # Axios 实例配置（保持不变）
└── apis/               # API 模块化目录
    ├── index.ts        # 统一导出
    ├── product.ts      # 产品相关 API
    ├── consumer.ts     # 消费者相关 API
    ├── developer.ts    # 开发者/认证相关 API
    ├── category.ts     # 分类相关 API
    └── chat.ts         # 聊天/会话相关 API
```

## 新旧 API 对照表

### Consumer 相关 API

| 旧 API (api.ts) | 新 API (apis/consumer.ts) | 说明 |
|----------------|---------------------------|------|
| `getConsumers(param, pageable)` | `getConsumers(params)` | 参数合并为一个对象 |
| `createConsumer(data)` | `createConsumer(data)` | 无变化 |
| `deleteConsumer(consumerId)` | `deleteConsumer(consumerId)` | 无变化 |
| `subscribeProduct(consumerId, productId)` | `subscribeProduct(consumerId, productId)` | 无变化 |
| `getConsumerSubscriptions(consumerId, searchParams)` | `getConsumerSubscriptions(consumerId, params)` | 参数名更规范 |
| `unsubscribeProduct(consumerId, productId)` | `unsubscribeProduct(consumerId, productId)` | 无变化 |
| `getProductSubscriptions(productId, params)` | `getProductSubscriptions(productId, params)` | 无变化 |
| `getProductSubscriptionStatus(productId)` | `getProductSubscriptionStatus(productId)` | 无变化 |

### Developer/OIDC 相关 API

| 旧 API (api.ts) | 新 API (apis/developer.ts) | 说明 |
|----------------|----------------------------|------|
| `getOidcProviders()` | `getOidcProviders()` | 返回类型更明确 |
| `handleOidcCallback(code, state)` | `handleOidcCallback({ code, state })` | 参数改为对象形式 |
| `developerLogout()` | `developerLogout()` | 无变化 |

### Category 相关 API

| 旧 API (api.ts) | 新 API (apis/category.ts) | 说明 |
|----------------|---------------------------|------|
| `categoryApi.getCategoriesByProductType(productType)` | `getCategoriesByProductType({ productType })` | 扁平化导出，参数改为对象形式 |

### Product 相关 API

| 旧 API (api.ts) | 新 API (apis/product.ts) | 说明 |
|----------------|--------------------------|------|
| `getProducts(params)` | `getProducts(params)` | 已存在，无变化 |

### Chat/Session 相关 API

| 旧 API (api.ts) | 新 API (apis/chat.ts) | 说明 |
|----------------|----------------------|------|
| `createSession(data)` | `createSession(data)` | 无变化 |
| `getSessions(params)` | `getSessions(params)` | 无变化 |
| `updateSession(sessionId, data)` | `updateSession(sessionId, data)` | 无变化 |
| `deleteSession(sessionId)` | `deleteSession(sessionId)` | 无变化 |
| `sendChatMessage(message)` | `sendChatMessage(message)` | 无变化 |
| `getChatMessageStreamUrl()` | `getChatMessageStreamUrl()` | 无变化 |
| `getConversations(sessionId)` | `getConversations(sessionId)` | 无变化 |

## 使用方式对比

### 方式 1: 默认导入（推荐）

**新方式**:
```typescript
import apis from '@/lib/apis';

// 使用
const products = await apis.getProducts({ type: 'MODEL_API' });
const sessions = await apis.getSessions({ page: 0, size: 20 });
const consumers = await apis.getConsumers({ page: 0, size: 20 });
```

**旧方式**:
```typescript
import { getProducts, getSessions, getConsumers } from '@/lib/api';

const products = await getProducts({ type: 'MODEL_API' });
const sessions = await getSessions({ page: 0, size: 20 });
const consumers = await getConsumers({ name: 'test' }, { page: 0, size: 20 });
```

### 方式 2: 命名导入

**新方式**:
```typescript
import { getProducts, getSessions, getConsumers } from '@/lib/apis';

const products = await getProducts({ type: 'MODEL_API' });
const sessions = await getSessions({ page: 0, size: 20 });
const consumers = await getConsumers({ page: 0, size: 20 });
```

### 方式 3: 分模块导入

**新方式** (更清晰的模块化):
```typescript
import { getProducts } from '@/lib/apis/product';
import { createSession, getSessions } from '@/lib/apis/chat';
import { getConsumers, subscribeProduct } from '@/lib/apis/consumer';
```

## 主要改进点

### 1. 参数统一化

**旧方式** - 分离的参数：
```typescript
getConsumers(
  { name: 'test', email: 'test@example.com' },
  { page: 0, size: 20 }
)
```

**新方式** - 统一的参数对象：
```typescript
getConsumers({
  name: 'test',
  email: 'test@example.com',
  page: 0,
  size: 20
})
```

### 2. 类型定义优化

**新方式** 使用 `I` 前缀标识接口类型：
```typescript
import type { IConsumer, ISession, IProduct } from '@/lib/apis';

const consumer: IConsumer = {
  consumerId: '123',
  name: 'Test Consumer',
  description: 'Test'
};
```

### 3. 响应类型明确

**新方式** 使用泛型明确响应类型：
```typescript
// 编辑器可以自动推断返回类型
const response = await apis.getProducts({ type: 'MODEL_API' });
// response: RespI<GetProductsResp>
// response.data.content: IProductDetail[]
```

### 4. API 分组清晰

新的目录结构让 API 按业务领域分组，更易于维护和查找：

- **product.ts**: 产品相关操作
- **consumer.ts**: 消费者和订阅管理
- **developer.ts**: 认证和授权
- **category.ts**: 分类管理
- **chat.ts**: 聊天和会话

## 迁移步骤

### 逐步迁移（推荐）

1. **保留旧 API**: 不删除 `src/lib/api.ts`
2. **新代码使用新 API**: 新功能使用 `src/lib/apis/`
3. **逐步替换**: 在维护旧代码时，逐步将导入替换为新 API
4. **最终清理**: 所有代码迁移完成后，删除 `api.ts`

### 快速查找替换

使用 IDE 的"查找替换"功能：

**示例 1**: 替换 consumer API 导入
```bash
# 查找
from '@/lib/api'

# 替换为
from '@/lib/apis'
```

**示例 2**: 修复参数结构
```typescript
// 查找并手动修复
getConsumers({ name: 'test' }, { page: 0, size: 20 })

// 替换为
getConsumers({ name: 'test', page: 0, size: 20 })
```

## 注意事项

### 1. 参数格式变化

部分 API 的参数格式有变化，需要手动调整：

- `getConsumers`: 参数合并
- `handleOidcCallback`: 参数改为对象
- `getCategoriesByProductType`: 参数改为对象

### 2. 类型导入

如果使用 TypeScript 类型，需要更新导入：

```typescript
// 旧
import type { Product, Session, Consumer } from '@/lib/api';

// 新
import type { IProduct, ISession, IConsumer } from '@/lib/apis';
```

### 3. 兼容性

新旧 API 可以共存，不会相互冲突。可以渐进式迁移。

## 常见问题

### Q: 必须立即迁移所有代码吗？
A: 不需要。旧的 `api.ts` 会保留，可以逐步迁移。

### Q: 新旧 API 有性能差异吗？
A: 没有。底层实现相同，只是组织方式不同。

### Q: 如何选择导入方式？
A: 推荐使用默认导入 `import apis from '@/lib/apis'`，代码更简洁。

### Q: 类型定义在哪里？
A: 所有类型定义都在对应的模块文件中，也可以从 `@/lib/apis` 导入。

## 示例代码对比

### 完整示例：获取并订阅产品

**旧方式**:
```typescript
import {
  getProducts,
  getConsumers,
  subscribeProduct
} from '@/lib/api';

async function subscribeToProduct() {
  // 获取产品
  const products = await getProducts({ type: 'MODEL_API' });

  // 获取消费者
  const consumers = await getConsumers({}, { page: 0, size: 20 });

  // 订阅
  await subscribeProduct(consumers.data.content[0].consumerId, products.data.content[0].productId);
}
```

**新方式**:
```typescript
import apis from '@/lib/apis';

async function subscribeToProduct() {
  // 获取产品
  const products = await apis.getProducts({ type: 'MODEL_API' });

  // 获取消费者
  const consumers = await apis.getConsumers({ page: 0, size: 20 });

  // 订阅
  await apis.subscribeProduct(
    consumers.data.content[0].consumerId,
    products.data.content[0].productId
  );
}
```

## 总结

新的 API 结构提供了：

✅ **更好的组织**: 按业务领域分组，易于维护
✅ **类型安全**: 完整的 TypeScript 类型定义
✅ **一致性**: 统一的参数和命名规范
✅ **灵活性**: 支持多种导入方式
✅ **向后兼容**: 可以与旧 API 共存

建议新代码优先使用新的 API 结构，逐步迁移现有代码。
