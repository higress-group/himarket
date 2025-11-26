# API 定义规范

本文档描述了项目中 API 层的定义和组织规范。

## 目录结构

```
src/lib/
├── request.ts           # Axios 实例和拦截器配置
└── apis/                # API 定义目录
    ├── index.ts         # 统一导出入口
    ├── product.ts       # 产品相关 API
    ├── consumer.ts      # 消费者相关 API
    ├── developer.ts     # 开发者/认证相关 API
    ├── category.ts      # 分类相关 API
    └── chat.ts          # 聊天/会话相关 API
```

## 基础规范

### 1. 文件组织

- **模块化**: 按照业务领域将 API 分组到独立文件中
- **命名规则**: 文件名使用小写加下划线，如 `product.ts`, `consumer.ts`
- **统一导出**: 通过 `index.ts` 统一导出所有 API，便于外部引用

### 2. 导入规范

每个 API 文件必须导入以下内容：

```typescript
import request, { type RespI } from "../request";
```

- `request`: Axios 实例，已配置拦截器和 baseURL
- `RespI`: 统一响应格式的泛型接口

### 3. 文件头部注释

每个文件顶部必须添加 JSDoc 注释说明模块用途：

```typescript
/**
 * 产品相关接口
 */
```

### 4. 接口命名规范

#### 类型接口命名（建议）

- **普通接口**: 使用 `I` 前缀，如 `IProductDetail`, `IProductIcon`
- **请求参数**: 使用 `Params` 后缀，如 `GetProductsParams`
- **响应数据**: 使用 `Resp` 后缀，如 `GetProductsResp`

示例：

```typescript
// 数据模型
export interface IProductIcon {
  type: 'URL' | 'BASE64';
  value: string;
}

// 请求参数
interface GetProductsParams {
  type: string;
  categoryIds?: string[];
  page?: number;
  size?: number;
}

// 响应数据（内部接口，不导出）
interface GetProductsResp {
  content: IProductDetail[];
  totalElements: number;
  totalPages: number;
}
```

### 5. 函数定义规范

#### 基本结构

```typescript
export function getProducts(params: GetProductsParams) {
  return request.get<RespI<GetProductsResp>, RespI<GetProductsResp>>(
    '/products',
    {
      params: {
        type: params.type,
        categoryIds: params.categoryIds,
        page: params.page || 0,
        size: params.size || 100,
      },
    }
  );
}
```

#### 要点说明

1. **导出**: 使用 `export function` 导出 API 函数
2. **命名**: 使用动词开头的驼峰命名，如 `getProducts`, `createConsumer`, `deleteSession`
3. **泛型声明**: 使用双泛型 `<RespI<T>, RespI<T>>` 确保类型安全
4. **参数处理**: 在函数内部处理默认值和参数转换

### 6. HTTP 方法规范

#### GET 请求

```typescript
export function getConsumers(params: GetConsumersParams) {
  return request.get<RespI<GetConsumersResp>, RespI<GetConsumersResp>>(
    '/consumers',
    {
      params: {
        name: params.name,
        page: params.page || 0,
        size: params.size || 20,
      },
    }
  );
}
```

#### POST 请求

```typescript
export function createConsumer(data: CreateConsumerData) {
  return request.post<RespI<IConsumer>, RespI<IConsumer>>(
    '/consumers',
    data
  );
}
```

#### PUT 请求

```typescript
export function updateSession(sessionId: string, data: UpdateSessionData) {
  return request.put<RespI<ISession>, RespI<ISession>>(
    `/sessions/${sessionId}`,
    data
  );
}
```

#### DELETE 请求

```typescript
export function deleteConsumer(consumerId: string) {
  return request.delete<RespI<void>, RespI<void>>(
    `/consumers/${consumerId}`
  );
}
```

### 7. 响应数据格式

所有 API 响应必须遵循统一格式：

```typescript
export interface RespI<T> {
  code: string;        // 响应码，如 "SUCCESS"
  message?: string;    // 可选的错误消息
  data: T;             // 实际数据
}
```

#### 分页响应

```typescript
interface PaginatedResp<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}
```

### 8. 复杂参数处理

对于需要动态构建 URL 参数的场景：

```typescript
export function getProductSubscriptions(
  productId: string,
  params?: {
    status?: string;
    consumerName?: string;
    page?: number;
    size?: number;
  }
) {
  return request.get<RespI<GetSubscriptionsResp>, RespI<GetSubscriptionsResp>>(
    `/products/${productId}/subscriptions`,
    {
      params: {
        status: params?.status,
        consumerName: params?.consumerName,
        page: params?.page || 0,
        size: params?.size || 20,
      },
    }
  );
}
```

### 9. 特殊场景处理

#### SSE 流式接口

对于需要返回完整 URL 的场景（如 SSE）：

```typescript
export function getChatMessageStreamUrl(): string {
  const baseURL = (request.defaults.baseURL || '') as string;
  return `${baseURL}/chats`;
}
```

#### 复杂业务逻辑

如果 API 需要内部调用其他接口或进行数据转换，使用 `async/await`：

```typescript
export async function getProductSubscriptionStatus(productId: string) {
  try {
    const response = await getProductSubscriptions(productId, { size: 100 });
    const subscriptions = response.data.content || [];

    // 数据转换逻辑...

    return {
      hasSubscription: subscriptions.length > 0,
      subscribedConsumers: transformedData,
    };
  } catch (error) {
    console.error('Failed to get product subscription status:', error);
    throw error;
  }
}
```

### 10. 统一导出

在 `src/lib/apis/index.ts` 中统一导出：

```typescript
import * as product from "./product";
import * as consumer from "./consumer";
import * as developer from "./developer";
import * as category from "./category";
import * as chat from "./chat";

export default {
  ...product,
  ...consumer,
  ...developer,
  ...category,
  ...chat,
}
```

### 11. 使用方式

#### 推荐方式（默认导入）

```typescript
import apis from '@/lib/apis';

// 使用
const products = await apis.getProducts({ type: 'MODEL_API' });
const sessions = await apis.getSessions({ page: 0, size: 20 });
```

#### 命名导入方式

```typescript
import { getProducts, getSessions } from '@/lib/apis';

// 使用
const products = await getProducts({ type: 'MODEL_API' });
const sessions = await getSessions({ page: 0, size: 20 });
```

## 完整示例

```typescript
/**
 * 产品相关接口
 */

import request, { type RespI } from "../request";

// ============ 类型定义 ============

export interface IProductIcon {
  type: 'URL' | 'BASE64';
  value: string;
}

export interface IProductDetail {
  productId: string;
  name: string;
  description: string;
  status: string;
  icon?: IProductIcon;
  categories: ICategory[];
  enabled: boolean;
}

interface GetProductsParams {
  type: string;
  categoryIds?: string[];
  page?: number;
  size?: number;
}

interface GetProductsResp {
  content: IProductDetail[];
  totalElements: number;
  totalPages: number;
}

// ============ API 函数 ============

export function getProducts(params: GetProductsParams) {
  return request.get<RespI<GetProductsResp>, RespI<GetProductsResp>>(
    '/products',
    {
      params: {
        type: params.type,
        categoryIds: params.categoryIds,
        page: params.page || 0,
        size: params.size || 100,
      },
    }
  );
}

export function getProductDetail(productId: string) {
  return request.get<RespI<IProductDetail>, RespI<IProductDetail>>(
    `/products/${productId}`
  );
}
```

## 迁移清单

从旧的 `api.ts` 迁移到新规范时，需要完成以下步骤：

- [ ] 创建对应业务模块的文件（如 `consumer.ts`）
- [ ] 添加文件头部注释
- [ ] 导入 `request` 和 `RespI`
- [ ] 定义类型接口（使用 `I` 前缀）
- [ ] 定义请求参数和响应类型
- [ ] 实现 API 函数（添加泛型类型声明）
- [ ] 在 `index.ts` 中导出
- [ ] 验证类型检查通过

## 注意事项

1. **类型安全**: 必须为所有 API 函数添加正确的泛型类型声明
2. **一致性**: 遵循统一的命名规范和代码风格
3. **文档**: 为复杂接口添加 JSDoc 注释
4. **向后兼容**: 迁移时保留旧的 `api.ts`，避免影响现有代码
5. **渐进式**: 可以逐步迁移，新代码使用新规范，旧代码保持不变
