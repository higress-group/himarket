# HiMarket Admin - Coding Standards

Coding standards for the HiMarket Admin Console frontend. Based on actual code patterns found in the project, for developers and AI agents to reference.

Tech Stack: React 18 + TypeScript + Vite + Ant Design + Tailwind CSS

---

## 1. Package Architecture & Directory Responsibilities

```
src/
├── pages/                  # Route-level page components
├── components/             # UI components (organized by business domain)
│   ├── {domain}/           #   Domain component directories
│   │   ├── {Component}.tsx
│   │   ├── hooks/          #   Domain-specific reusable custom hooks
│   │   ├── display/        #   Pure display sub-components
│   │   ├── config-panels/  #   Config detail panels
│   │   ├── {modal-type}/   #   Modal components (e.g., link-api-modal/)
│   │   └── index.ts        #   Barrel export (unified export)
│   ├── common/             #   Generic components (AdvancedSearch, etc.)
│   ├── console/            #   Console-related
│   ├── mcp-creation/       #   MCP creation wizard
│   ├── mcp-vendor/         #   MCP vendor
│   └── portal/             #   Portal management
├── hooks/                  # Global custom hooks (reused across domains)
├── contexts/               # React Context (global state)
├── lib/
│   ├── api.ts              #   Axios instance + all API definitions
│   ├── apis/               #   API type definitions
│   ├── utils.ts            #   Generic utility functions
│   ├── constant.ts         #   Constants and enum mappings
│   └── request.ts          #   Request interceptor config
├── types/                  # Global TypeScript type definitions
│   ├── index.ts            #   Barrel export (unified export)
│   ├── api-product.ts
│   ├── gateway.ts
│   ├── portal.ts
│   └── ...
├── utils/                  # Page-level utility functions
├── constants/              # Constant enums
└── routes/                 # Route configuration
```

### Layer Boundaries

| Layer                        | Responsibility                                               | Forbidden                                             |
| ---------------------------- | ------------------------------------------------------------ | ----------------------------------------------------- |
| `pages/`                     | Route matching, page-level state, composing child components | Defining reusable logic; defining cross-module types  |
| `components/{domain}/`       | Domain UI rendering, local interaction logic                 | Direct route navigation (notify parent via callbacks) |
| `components/{domain}/hooks/` | Domain-reusable state logic                                  | React-external side effects                           |
| `hooks/` (top-level)         | Cross-domain reusable state logic                            | Same as above                                         |
| `lib/api.ts`                 | HTTP request wrappers, request/response types                | UI logic                                              |
| `types/`                     | Cross-module type definitions                                | Runtime code                                          |
| `contexts/`                  | Global shared state (e.g., LoadingContext)                   | Business logic                                        |

### Component Subdirectory Organization

When a domain has 3+ components or clear responsibility layers, create subdirectories:

```
components/api-product/
├── ApiProductOverview.tsx
├── ApiProductFormModal.tsx
├── config-panels/          # Config display panels
│   ├── McpServerConfigPanel.tsx
│   ├── AgentApiConfigPanel.tsx
│   └── index.ts
├── display/                # Pure display components
│   ├── ApiKeyDisplay.tsx
│   └── AuthCredentialPanel.tsx
├── hooks/                  # Domain-reusable hooks
│   ├── useApiList.ts
│   ├── useMcpMeta.ts
│   └── index.ts
├── link-api-modal/         # Modal sub-components
│   ├── LinkApiModal.tsx
│   └── index.ts
└── index.ts                # Barrel export
```

---

## 2. Component Structure

### 2.1 Component Declaration Styles

**Page components**: `export default function` declaration

```tsx
export default function ApiProductDetail() {
  // ...
}
```

**Reusable components**: `export function` named export

```tsx
export function ApiProductOverview({ apiProduct, onEdit }: ApiProductOverviewProps) {
  // ...
}
```

**Barrel Export files**: `index.ts` unified export for domain components

```tsx
// components/api-product/config-panels/index.ts
export { McpServerConfigPanel } from './McpServerConfigPanel';
export { AgentApiConfigPanel } from './AgentApiConfigPanel';
export { ModelApiConfigPanel } from './ModelApiConfigPanel';
export { RestApiConfigPanel } from './RestApiConfigPanel';
```

### 2.2 File Structure Order

```tsx
// 1. External dependency imports
import { useState, useEffect } from 'react';
import { Card, Button } from 'antd';

// 2. Internal module imports (use @/ alias)
import { apiProductApi } from '@/lib/api';
import { formatDateTime } from '@/lib/utils';

// 3. Type imports (use import type)
import type { ApiProduct, ApiProductConfig } from '@/types/api-product';

// 4. Internal sub-components/utilities (optional)
function renderStatusTag(status: string) { ... }

// 5. Props interface definition
interface ApiProductOverviewProps {
  apiProduct: ApiProduct;
  onEdit: () => void;
}

// 6. Main component
export function ApiProductOverview({ apiProduct, onEdit }: ApiProductOverviewProps) {
  // ...
}
```

### 2.3 Component Splitting Guidelines

| Metric                 | Threshold | Recommendation                                           |
| ---------------------- | --------- | -------------------------------------------------------- |
| Single file line count | 800 lines | Consider splitting (orchestration pages may retain more) |
| Single file line count | 600 lines | Worth reviewing                                          |
| Hooks per component    | 10        | Consider splitting                                       |
| Custom hook line count | 300 lines | Worth reviewing                                          |

> Whether to split depends on business complexity.

**Splitting directions**:

- State logic -> Extract to `components/{domain}/hooks/use{Domain}{Action}.ts`
- Modal / dialog -> Extract to `components/{domain}/{modal-name}/`
- Config panel -> Extract to `components/{domain}/config-panels/`
- Pure display -> Extract to `components/{domain}/display/`

---

## 3. Type Definitions

### 3.1 Type Placement

| Scope                   | Location                                 | Example                                  |
| ----------------------- | ---------------------------------------- | ---------------------------------------- |
| Cross-module reuse      | `src/types/`                             | `ApiProduct`, `LinkedService`, `Gateway` |
| API request/response    | `src/lib/apis/typing.ts` or `src/types/` | `ApiResponse<T>`, `PaginatedResponse<T>` |
| Domain reuse            | Inside `src/components/{domain}/`        | `McpMetaItem`, `ApiListItem`             |
| Component-internal only | Inside component file                    | `Props`, local interface                 |

### 3.2 Type Naming

```tsx
// Interfaces: PascalCase, no I prefix (new code)
interface ApiProductConfig { ... }
interface ApiProductMcpConfig { ... }

// Props interface: {ComponentName}Props
interface ApiProductOverviewProps { ... }
interface LinkApiModalProps { ... }

// Constant enums: const as const
export const ProductType = {
  AGENT_API: 'AGENT_API',
  MCP_SERVER: 'MCP_SERVER',
  MODEL_API: 'MODEL_API',
  REST_API: 'REST_API',
} as const;
export type ProductType = (typeof ProductType)[keyof typeof ProductType];
```

### 3.3 Type Exports

`src/types/index.ts` serves as the unified type export:

```tsx
export * from './api-product';
export * from './gateway';
export * from './portal';
export * from './consumer';
// ...
```

Consumers import by need:

```tsx
import type { ApiProduct, LinkedService } from '@/types/api-product';
```

---

## 4. State Management

### 4.1 Strategy Selection

| Scenario                          | Solution               | Example                                 |
| --------------------------------- | ---------------------- | --------------------------------------- |
| Simple component state            | `useState`             | Form input, loading, expand/collapse    |
| Component form state              | `Form.useForm()`       | Ant Design form data and validation     |
| Cross-component sharing (simple)  | Context + `useState`   | `LoadingContext` (global loading state) |
| Cross-component sharing (complex) | Context + `useReducer` | Not currently used                      |

### 4.2 State Variable Naming

```tsx
// Boolean state
const [loading, setLoading] = useState(false);
const [isModalVisible, setIsModalVisible] = useState(false);

// Multiple loading states
const [gatewayLoading, setGatewayLoading] = useState(false);
const [mcpLoading, setMcpLoading] = useState(false);

// Collections
const [gateways, setGateways] = useState<Gateway[]>([]);
const [mcpServers, setMcpServers] = useState<McpServerItem[]>([]);

// Selected item
const [selectedGatewayId, setSelectedGatewayId] = useState<string>('');
const [selectedApi, setSelectedApi] = useState<ApiListItem | null>(null);
```

### 4.3 State Grouping

In complex components, group states by logic with comments:

```tsx
export function LinkApiModal({
  apiProduct,
  linkedService,
  onCancel,
  onOk,
  open,
}: LinkApiModalProps) {
  // ===== Form & selection state =====
  const [form] = Form.useForm();
  const [selectedGatewayId, setSelectedGatewayId] = useState<string>();
  const [selectedApi, setSelectedApi] = useState<ApiListItem>();

  // ===== Data fetching state =====
  const { fetch: fetchMcpMeta, metaList: mcpMetaList } = useMcpMeta();

  // ===== Connection config =====
  const connConfig = useMcpConnectionConfig(
    apiProduct,
    linkedService,
    mcpMetaList,
    selectedDomainIndex,
  );
  // ...
}
```

---

## 5. Custom Hooks

### 5.1 Naming & Responsibilities

```
components/api-product/hooks/
├── useApiList.ts              # Fetch API list by product type
├── useGateways.ts             # Fetch gateway list
├── useMcpMeta.ts              # Fetch MCP metadata
├── useMcpConnectionConfig.ts  # Generate MCP connection config
├── useNacosInstances.ts       # Fetch Nacos instances
├── useParsedMcpTools.ts       # Parse MCP Tools config
└── index.ts                   # Barrel export
```

**Naming rule**: `use{Domain}{Action}`

### 5.2 Hook Design Principles

```tsx
export function useMcpMeta() {
  const [metaList, setMetaList] = useState<McpMetaItem[]>([]);

  const fetch = useCallback(async (productId: string) => {
    const res = await mcpServerApi.getMcpMeta(productId);
    setMetaList(res.data || []);
  }, []);

  return { fetch, metaList };
}
```

- One hook = one data flow or a tightly coupled group of side effects
- Expose state and actions via return values; do not trigger UI feedback directly
- Domain-reusable hooks live in `components/{domain}/hooks/`
- Cross-domain reusable hooks live in top-level `hooks/`

---

## 6. API Integration

### 6.1 API Layer Structure

```
lib/
├── api.ts              # All API namespace objects + Axios instance
├── apis/
│   └── typing.ts       # API-related type definitions
├── utils.ts            # Generic utility functions
├── constant.ts         # Constants and mappings
└── request.ts          # Request interceptor config (optional split)
```

### 6.2 API Namespace Pattern

```tsx
// lib/api.ts
export const apiProductApi = {
  createApiProduct: (data: CreateApiProductRequest) => api.post('/products', data),

  getApiProducts: (params: GetApiProductsParams) => api.get('/products', { params }),

  getApiProductDetail: (id: string) => api.get(`/products/${id}`),

  updateApiProduct: (id: string, data: UpdateApiProductRequest) => api.put(`/products/${id}`, data),

  deleteApiProduct: (id: string) => api.delete(`/products/${id}`),
};

export const gatewayApi = {
  getGateways: (params: GetGatewaysParams) => api.get('/gateways', { params }),

  getGatewayMcpServers: (gatewayId: string, params: { page: number; size: number }) =>
    api.get(`/gateways/${gatewayId}/mcp-servers`, { params }),
  // ...
};
```

### 6.3 API Calling Pattern

```tsx
// Call via namespace objects
import { apiProductApi, gatewayApi } from '@/lib/api';

const res = await apiProductApi.getApiProductDetail(productId);
const servers = await gatewayApi.getGatewayMcpServers(gatewayId, { page: 1, size: 500 });
```

### 6.4 Request Layer Conventions

- Unified response type: backend returns `{ code: string; message?: string; data: T }`
- Automatic token injection: request interceptor reads `access_token` from `localStorage`
- Automatic redirect to login on 401
- Pagination params: `page` (1-based) + `size`

### 6.5 API Request Deduplication (StrictMode Guard)

React 18 `StrictMode` intentionally double-invokes component effects in development (mount → cleanup → remount). `useEffect` containing data requests will fire twice on initial mount, producing duplicate API calls. This clutters backend logs, complicates debugging, and may cause race conditions. **All initialization data requests inside `useEffect` must be guarded.**

#### Scenarios Requiring Deduplication

| Scenario                     | Example                                                           | Why it duplicates                           |
| ---------------------------- | ----------------------------------------------------------------- | ------------------------------------------- |
| List page initial load       | `ProductTable` fetching product list                              | Component mounts twice under StrictMode     |
| Detail page data load        | `ApiProductDetail` fetching product detail                        | Same as above                               |
| Tab-dependent data load      | `ApiProductOverview` fetching tab-specific data                   | Mount + tab prop change both trigger effect |
| Multi-endpoint parallel load | Detail page fetching refs, categories, subscriptions concurrently | Every endpoint fires twice                  |
| Global static data load      | Dropdown options, enum mappings fetched on app start              | `useEffect(() => {...}, [])` runs twice     |
| Conditional data load        | Fetching portal list only when a specific product type is active  | Component remount resets condition state    |

#### Standard Guard Pattern: `useRef`

Use `useRef` to record the last successfully initiated request key. Skip the request when the key has not changed.

**Pattern 1: Single identifier**

```tsx
const lastFetchedIdRef = useRef<string | null>(null);

useEffect(() => {
  if (!productId || lastFetchedIdRef.current === productId) return;
  lastFetchedIdRef.current = productId;
  fetchProductDetail(productId);
}, [productId]);
```

**Pattern 2: Composite identifier**

```tsx
const lastFetchedKeyRef = useRef<string>('');

useEffect(() => {
  if (!productId) return;
  const key = `${productId}-${activeTab}`;
  if (lastFetchedKeyRef.current === key) return;
  lastFetchedKeyRef.current = key;
  fetchTabData(productId, activeTab);
}, [productId, activeTab]);
```

**Pattern 3: Execute once globally**

```tsx
const hasFetchedRef = useRef(false);

useEffect(() => {
  if (hasFetchedRef.current) return;
  hasFetchedRef.current = true;
  fetchGlobalConfig();
}, []);
```

**Pattern 4: List page with search reset**

```tsx
const lastFetchedTypeRef = useRef<string | null>(null);

useEffect(() => {
  if (lastFetchedTypeRef.current === productType) return;
  lastFetchedTypeRef.current = productType;
  setSearchInput('');
  setSelectedIds(new Set());
  fetchProducts(1, 10, '');
}, [productType]);
```

**Pattern 5: Multiple independent endpoints**

```tsx
const lastDetailRef = useRef<string>('');
const lastRefsRef = useRef<string>('');
const lastCategoriesRef = useRef<string>('');

useEffect(() => {
  if (!productId) return;

  if (lastDetailRef.current !== productId) {
    lastDetailRef.current = productId;
    fetchProductDetail(productId);
  }

  if (lastRefsRef.current !== productId) {
    lastRefsRef.current = productId;
    fetchProductRefs(productId);
  }

  if (lastCategoriesRef.current !== productId) {
    lastCategoriesRef.current = productId;
    fetchCategories(productId);
  }
}, [productId]);
```

#### Dependency Array Pitfalls

Never place an **object reference** in a `useEffect` dependency array. A new object reference from parent `setState` will spuriously re-trigger the effect even when the underlying data is unchanged.

```tsx
// ❌ Wrong: apiProduct is an object; new reference triggers extra fetch
useEffect(() => {
  fetchRelatedData();
}, [apiProduct]);

// ✅ Correct: depend on primitive values only
useEffect(() => {
  fetchRelatedData();
}, [apiProduct.productId, apiProduct.type]);
```

#### Custom Hook Deduplication

When encapsulating data fetching in custom hooks, apply the same guard inside the hook so consumers do not need to think about it:

```tsx
export function useApiList(productType: string) {
  const [list, setList] = useState<ApiProduct[]>([]);
  const lastTypeRef = useRef<string | null>(null);

  const fetch = useCallback(
    async (page = 1, size = 10) => {
      if (lastTypeRef.current === productType) return;
      lastTypeRef.current = productType;
      const res = await apiProductApi.getApiProducts({ type: productType, page, size });
      setList(res.data.content);
    },
    [productType],
  );

  return { list, fetch };
}
```

#### Manual Refresh vs Auto Load

The `useRef` guard must **only block automatic initialization loads**. User-initiated actions (clicking Refresh, Search, Submit, pagination changes) must bypass the ref and always execute:

```tsx
// ✅ Auto-load guarded by ref
useEffect(() => {
  if (lastFetchedTypeRef.current === productType) return;
  lastFetchedTypeRef.current = productType;
  fetchProducts(1, 10, '');
}, [productType]);

// ✅ Manual refresh bypasses the ref
const handleRefresh = () => {
  fetchProducts(pagination.current, pagination.pageSize, searchInput);
};

// ✅ Search bypasses the ref
const handleSearch = () => {
  fetchProducts(1, pagination.pageSize, searchInput);
};
```

#### Naming Convention for Refs

Use consistent names so reviewers can instantly recognize deduplication refs:

| Pattern        | Ref name                   |
| -------------- | -------------------------- |
| Single id      | `lastFetched{Entity}IdRef` |
| Composite key  | `lastFetched{Scope}KeyRef` |
| Once-only flag | `hasFetched{Scope}Ref`     |

Examples: `lastFetchedProductIdRef`, `lastFetchedTypeRef`, `lastPublishedKeyRef`, `allPortalsFetchedRef`.

#### Scenarios That Do NOT Need Deduplication

Do not add guards for:

- User-triggered actions (button clicks, search submit, pagination change)
- Form submission callbacks
- Polling intervals (`setInterval`)
- Real-time SSE / WebSocket listeners

---

## 7. Path Aliases

**Always use `@/` alias**. Deep relative paths like `../../../lib/api` are forbidden.

```tsx
// ✅ Correct
import { apiProductApi } from '@/lib/api';
import type { ApiProduct } from '@/types/api-product';
import { McpServerConfigPanel } from '@/components/api-product/config-panels';

// ❌ Wrong
import { apiProductApi } from '../../../lib/api';
```

Configured in `vite.config.ts`:

```tsx
resolve: {
  alias: {
    '@': path.resolve(__dirname, './src'),
  },
}
```

---

## 8. Styling & UI

### 8.1 Styling Priority

1. **Tailwind CSS** (primary): most styling via `className`
2. **Ant Design component built-in styles**: forms, tables, modals, etc.
3. **Standalone CSS files**: only for third-party component overrides (Swagger UI, Xterm, etc.)

### 8.2 Tailwind Conventions

```tsx
// Basic usage
<div className="flex items-center gap-2 p-4 rounded-lg" />

// Conditional classes
<div className={`
  transition-all duration-300
  ${isActive ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-600'}
`} />
```

### 8.3 Skeleton Screens

Complex pages should provide skeleton components:

```tsx
{
  loading ? <DetailSkeleton /> : <ContentHere />;
}
```

---

## 9. Naming Conventions

### 9.1 File Naming

| Type                  | Style                  | Example                                                  |
| --------------------- | ---------------------- | -------------------------------------------------------- |
| Page components       | PascalCase             | `ApiProductDetail.tsx`, `GatewayConsoles.tsx`            |
| Reusable components   | PascalCase             | `ApiProductOverview.tsx`, `LinkApiModal.tsx`             |
| Component directories | kebab-case             | `config-panels/`, `link-api-modal/`, `deploy-mcp-modal/` |
| Custom hooks          | camelCase (use prefix) | `useApiList.ts`, `useMcpMeta.ts`                         |
| Utility files         | camelCase              | `utils.ts`, `constant.ts`                                |
| Type files            | camelCase              | `api-product.ts`, `gateway.ts`                           |
| Barrel export         | `index.ts`             | `components/api-product/index.ts`                        |

### 9.2 Code Naming

```tsx
// Components: PascalCase
export function ApiProductOverview() { ... }

// Props interfaces: {ComponentName}Props
interface ApiProductOverviewProps { ... }

// Event handlers: handle + action
const handleSubmit = () => { ... };
const handleGatewayChange = (gatewayId: string) => { ... };

// Data fetching: fetch + noun
const fetchGateways = async () => { ... };
const fetchMcpServers = async () => { ... };

// Constants: UPPER_SNAKE_CASE
const READ_ONLY_KINDS = new Set(['read', 'search', 'think']);
```

---

## 10. Error Handling

### 10.1 Standard Pattern

```tsx
const [loading, setLoading] = useState(false);
const [error, setError] = useState<string | null>(null);

const fetchData = async () => {
  setLoading(true);
  setError(null);
  try {
    const res = await apiProductApi.getApiProductDetail(id);
    setProduct(res.data);
  } catch {
    setError('Failed to fetch product details, please retry');
  } finally {
    setLoading(false);
  }
};
```

### 10.2 Optional Data Fetching

Non-critical data fetching failures should not affect page display:

```tsx
try {
  const res = await gatewayApi.getGatewayStatus(gatewayId);
  setStatus(res.data);
} catch {
  // Status info is non-critical; ignore error
}
```

---

## 11. Code Reusability Principles

### 11.1 When to Extract for Reuse

- **Same UI pattern appears twice or more** -> Extract to `components/common/`
- **Same state logic appears twice or more** -> Extract to `components/{domain}/hooks/` or `hooks/`
- **Same data transformation appears twice or more** -> Extract to `lib/utils.ts`

### 11.2 Reuse Decision Criteria

Reuse should be based on **consistent business meaning**:

```tsx
// Same business meaning -> should reuse
<McpServerConfigPanel config={config} />   // MCP config displayed in multiple places

// Different business meaning -> should NOT force reuse
<ApiProductFormModal type="MCP_SERVER" />   // MCP product form
<ApiProductFormModal type="REST_API" />     // REST API product form (fields differ significantly)
```

---

## 12. Pre-Commit Checks

```bash
# Run from project root
./scripts/code-check.sh
```

Covers: Prettier formatting + ESLint check/fix + TypeScript type checking. Must pass before PR submission.
