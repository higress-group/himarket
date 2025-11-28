# API客户端与状态管理

<cite>
**本文档引用的文件**   
- [api.ts](file://portal-web/api-portal-admin/src/lib/api.ts#L1-L252)
- [LoadingContext.tsx](file://portal-web/api-portal-admin/src/contexts/LoadingContext.tsx#L1-L30)
- [utils.ts](file://portal-web/api-portal-admin/src/lib/utils.ts#L1-L100)
- [LayoutWrapper.tsx](file://portal-web/api-portal-admin/src/components/LayoutWrapper.tsx#L1-L46)
</cite>

## 目录
1. [引言](#引言)
2. [API通信策略](#api通信策略)
3. [状态管理机制](#状态管理机制)
4. [数据状态管理与缓存](#数据状态管理与缓存)
5. [API调用与上下文使用示例](#api调用与上下文使用示例)

## 引言
本文档全面阐述前端应用在 `himarket` 项目中如何通过 `axios` 实现与 `portal-server` 的 RESTful API 通信，并利用 React Context 进行全局加载状态管理。重点分析 `lib/api.ts` 中的请求/响应拦截器、JWT 令牌注入、错误处理机制，以及 `LoadingContext.tsx` 如何协调 UI 加载状态，提升用户体验。

## API通信策略

### Axios客户端配置
前端通过 `axios` 封装了一个统一的 API 客户端，集中管理所有与后端 `portal-server` 的通信。该客户端在 `portal-web/api-portal-admin/src/lib/api.ts` 中定义。

```typescript
const api: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
})
```

**配置说明**：
- **baseURL**: 从环境变量 `VITE_API_BASE_URL` 读取，实现开发、测试、生产环境的灵活切换。
- **timeout**: 设置 10 秒超时，防止请求无限等待。
- **headers**: 默认设置 `Content-Type` 为 `application/json`。
- **withCredentials**: 设置为 `true`，确保跨域请求时能携带 Cookie，用于会话保持。

**Section sources**
- [api.ts](file://portal-web/api-portal-admin/src/lib/api.ts#L5-L13)

### 请求拦截器：JWT令牌注入
在请求发送前，拦截器会自动从 `localStorage` 中读取 JWT 令牌，并将其添加到请求头的 `Authorization` 字段中。

```typescript
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)
```

**流程分析**：
1. 调用 `getToken()` 函数（定义于 `utils.ts`）从本地存储获取令牌。
2. 如果令牌存在，则在请求头中设置 `Authorization: Bearer <token>`。
3. 修改后的配置对象返回，继续执行请求。

此机制确保了所有需要身份验证的 API 调用都能自动携带有效的身份凭证。

**Section sources**
- [api.ts](file://portal-web/api-portal-admin/src/lib/api.ts#L15-L24)
- [utils.ts](file://portal-web/api-portal-admin/src/lib/utils.ts#L15-L17)

### 响应拦截器：统一错误处理
响应拦截器负责处理服务器返回的数据和错误，实现统一的错误提示和状态码处理。

```typescript
api.interceptors.response.use(
  (response: AxiosResponse) => {
    return response.data
  },
  (error) => {
    message.error(error.response?.data?.message || '请求发生错误');
    if (error.response?.status === 403 || error.response?.status === 401) {
      removeToken()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)
```

**功能说明**：
- **成功响应**: 直接返回 `response.data`，简化了上层调用的数据处理。
- **错误响应**:
  - 使用 `antd` 的 `message.error` 组件弹出错误提示。优先显示服务器返回的 `message`，否则显示默认提示“请求发生错误”。
  - **身份验证失效处理**: 当收到 `401 (Unauthorized)` 或 `403 (Forbidden)` 状态码时，判定为登录失效或权限不足。此时执行：
    1. 调用 `removeToken()` 清除本地存储中的令牌和用户信息。
    2. 重定向用户至 `/login` 登录页面。

此设计保证了错误处理的一致性，并提供了良好的用户引导。

**Section sources**
- [api.ts](file://portal-web/api-portal-admin/src/lib/api.ts#L26-L38)
- [utils.ts](file://portal-web/api-portal-admin/src/lib/utils.ts#L19-L21)

### 模块化API服务
`api.ts` 文件通过导出多个命名对象（如 `authApi`, `portalApi`, `apiProductApi` 等），将 API 调用按业务模块进行组织，提高了代码的可维护性和可读性。

```typescript
export const portalApi = {
  getPortals: (params?: { page?: number; size?: number }) => {
    return api.get(`/portals`, { params })
  },
  deletePortal: (portalId: string) => {
    return api.delete(`/portals/${portalId}`)
  },
  // ... 其他方法
}
```

**优点**：
- **职责分离**: 每个模块（Portal、API产品、网关等）的 API 调用独立管理。
- **类型安全**: TypeScript 接口定义了参数和返回值类型，增强了开发体验。
- **易于测试和复用**: 每个 API 函数都是独立的，便于单元测试和在不同组件中复用。

**Section sources**
- [api.ts](file://portal-web/api-portal-admin/src/lib/api.ts#L40-L252)

## 状态管理机制

### React Context 全局加载状态
前端应用使用 React Context API 创建了一个名为 `LoadingContext` 的全局状态，用于管理整个应用的加载状态。该上下文定义在 `LoadingContext.tsx` 文件中。

```typescript
const LoadingContext = createContext<LoadingContextType | undefined>(undefined);

export const useLoading = () => {
  const context = useContext(LoadingContext);
  if (context === undefined) {
    throw new Error('useLoading must be used within a LoadingProvider');
  }
  return context;
};

export const LoadingProvider: React.FC<LoadingProviderProps> = ({ children }) => {
  const [loading, setLoading] = useState(false);

  return (
    <LoadingContext.Provider value={{ loading, setLoading }}>
      {children}
    </LoadingContext.Provider>
  );
};
```

**核心组件**：
- **`LoadingContext`**: 创建上下文对象，初始值为 `undefined`。
- **`LoadingProvider`**: 一个 React 组件，使用 `useState` 管理 `loading` 状态（布尔值），并通过 `Provider` 将 `{ loading, setLoading }` 对象提供给其所有子组件。
- **`useLoading`**: 一个自定义 Hook，封装了 `useContext` 调用。它会检查上下文是否被正确提供，如果未在 `LoadingProvider` 内部使用，则抛出错误，避免运行时错误。

**Section sources**
- [LoadingContext.tsx](file://portal-web/api-portal-admin/src/contexts/LoadingContext.tsx#L1-L30)

### 加载状态的应用
`LoadingProvider` 通常在应用的根组件或布局组件中被使用。例如，在 `LayoutWrapper.tsx` 中：

```typescript
const LayoutWrapper: React.FC = () => {
  const { loading, setLoading } = useLoading();
  const location = useLocation();

  useEffect(() => {
    if (!isLoginPage) {
      setLoading(true);
      const timer = setTimeout(() => {
        setLoading(false);
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [location.pathname, setLoading, isLoginPage]);

  // ... 其他逻辑
  return (
    <Layout loading={loading}>
      <Outlet />
    </Layout>
  );
};
```

**工作流程**：
1. `LayoutWrapper` 组件通过 `useLoading` Hook 订阅全局加载状态。
2. 当路由发生变化时（`useEffect` 监听 `location.pathname`），将 `loading` 状态设置为 `true`。
3. 通过一个 `setTimeout` 模拟一个短暂的加载过程（500毫秒），之后将 `loading` 状态设为 `false`。
4. `Layout` 组件接收 `loading` 属性，并据此决定是否显示加载指示器（如旋转图标）。

**优势**：
- **避免重复请求**: 通过集中管理加载状态，可以防止用户在数据加载时重复触发同一操作。
- **提升用户体验**: 在数据获取期间显示加载动画，给用户明确的反馈，避免界面“卡死”的错觉。
- **全局一致性**: 所有需要显示加载状态的组件都可以通过 `useLoading` 访问同一套状态，保证了 UI 的一致性。

**Section sources**
- [LayoutWrapper.tsx](file://portal-web/api-portal-admin/src/components/LayoutWrapper.tsx#L3-L46)

## 数据状态管理与缓存

### 数据获取与状态
前端应用从 API 获取的数据状态主要由各个业务组件自身管理。通常采用以下模式：
1. **组件状态**: 使用 `useState` 存储从 API 获取的数据（如 `portals`, `products`）和错误信息（如 `error`）。
2. **副作用**: 使用 `useEffect` 在组件挂载或依赖项变化时调用 API 函数（如 `portalApi.getPortals()`）。
3. **更新状态**: 在 API 调用成功后，使用 `setState` 更新数据状态；失败时更新错误状态。

### 刷新机制
刷新机制通常由用户交互触发：
- **手动刷新**: 用户点击“刷新”按钮，重新执行 `useEffect` 或直接调用 API 函数。
- **操作后刷新**: 在执行创建、更新、删除等操作后，自动重新获取列表数据，以反映最新状态。

### 错误状态处理
错误处理分为两个层面：
1. **全局层面**: 由 `api.ts` 中的响应拦截器处理网络错误、身份验证失败等通用错误，通过 `message` 组件提示用户。
2. **组件层面**: 组件内部可以捕获特定 API 调用的错误，进行更精细的处理，例如在表单提交失败时，将错误信息显示在对应字段下方。

## API调用与上下文使用示例

### 调用API服务
```typescript
import { portalApi } from '@/lib/api';

// 获取门户列表
const fetchPortals = async (page = 1, size = 10) => {
  try {
    const response = await portalApi.getPortals({ page, size });
    console.log('获取的门户列表:', response);
    // 更新组件状态
    // setPortals(response.data);
  } catch (error) {
    console.error('获取门户列表失败:', error);
    // 错误已被全局拦截器处理，此处可进行额外逻辑
  }
};

// 创建新门户
const createNewPortal = async (portalData) => {
  try {
    const response = await portalApi.createPortal(portalData);
    console.log('创建成功:', response);
    // 刷新列表
    // fetchPortals();
  } catch (error) {
    console.error('创建门户失败:', error);
  }
};
```

### 使用上下文状态
```tsx
import React from 'react';
import { useLoading } from '@/contexts/LoadingContext';

const MyComponent: React.FC = () => {
  const { loading, setLoading } = useLoading();

  const handleExpensiveOperation = async () => {
    setLoading(true); // 开始加载
    try {
      // 模拟一个耗时操作
      await new Promise(resolve => setTimeout(resolve, 2000));
      console.log('操作完成');
    } catch (error) {
      console.error('操作失败:', error);
    } finally {
      setLoading(false); // 结束加载
    }
  };

  return (
    <div>
      <button onClick={handleExpensiveOperation} disabled={loading}>
        {loading ? '处理中...' : '执行操作'}
      </button>
    </div>
  );
};

export default MyComponent;
```