# HiMarket Frontend - Coding Standards

Coding standards for the HiMarket Developer Portal frontend. Based on actual code patterns found in the project, for developers and AI agents to reference.

Tech Stack: React 18 + TypeScript + Vite + Ant Design + Tailwind CSS + i18next

---

## 1. Package Architecture & Directory Responsibilities

```
src/
├── pages/                  # Route-level page components
├── components/             # UI components (organized by domain)
│   ├── {domain}/           #   Domain components: chat/, coding/, consumer/, card/, square/
│   ├── common/             #   Generic components (business-agnostic)
│   ├── loading/            #   Skeleton / loading state components
│   └── icon/               #   Icon-related components
├── hooks/                  # Global custom hooks (state logic reused across pages)
├── context/                # React Context and Providers (global state management)
├── lib/                    # Infrastructure layer
│   ├── apis/               #   API wrappers (split by domain, unified export)
│   ├── utils/              #   Pure utility functions (no side effects)
│   ├── request.ts          #   Axios instance and interceptors
│   ├── sse.ts              #   SSE streaming request wrapper
│   └── styles.ts           #   Style utilities
├── types/                  # Global TypeScript type definitions
├── constants/              # Constants and enums
├── locales/                # i18n resources (zh-CN/, en-US/)
└── assets/                 # Static assets (images, etc.)
```

### Layer Boundaries

| Layer         | Responsibility                                                 | Forbidden                                             |
| ------------- | -------------------------------------------------------------- | ----------------------------------------------------- |
| `pages/`      | Route matching, data orchestration, composing child components | Defining reusable logic; defining cross-module types  |
| `components/` | Reusable UI rendering, local interaction logic                 | Direct route navigation (notify parent via callbacks) |
| `hooks/`      | Encapsulate reusable state logic and side effects              | DOM manipulation; UI feedback (message/Modal)         |
| `context/`    | Cross-component shared state (no business logic)               | Business logic; heavy computations                    |
| `lib/apis/`   | HTTP request wrappers, request/response types                  | UI logic; React dependencies                          |
| `lib/utils/`  | Pure utility functions                                         | React dependencies; DOM access; global state          |
| `types/`      | Cross-module type definitions                                  | Runtime code                                          |

### Domain Component Subdirectory Organization

When a domain has more than 3 component files, group them into subdirectories:

```
components/coding/
├── ChatStream.tsx            # Chat streaming component
├── CodingInput.tsx           # Input box
├── ConfigDropdowns.tsx       # Config dropdowns
├── renderers/                # Renderer sub-components
│   ├── ArtifactRenderer.tsx
│   ├── FileRenderer.tsx
│   └── MarkdownRenderer.tsx
└── ...
```

---

## 2. Component Structure

### 2.1 Component Declaration Styles

**Page components**: `function` declaration + `export default`

```tsx
function McpDetail() {
  // ...
}
export default McpDetail;
```

**Reusable components**: `export function` named export

```tsx
export function Sidebar({ currentSessionId, onNewChat }: SidebarProps) {
  // ...
}
```

**Small internal components**: Defined at the top of the file (outside the main component), not exported

```tsx
function ResizeHandle({ direction, isDragging, onMouseDown }: ResizeHandleProps) {
  return <div ... />;
}

// Main component
export function CodingContent() {
  return <ResizeHandle ... />;
}
```

### 2.2 File Structure Order

```tsx
// 1. External dependency imports
import { useState, useEffect } from 'react';
import { Card, Button } from 'antd';

// 2. Internal module imports
import { Layout } from '../components/Layout';
import APIs from '../lib/apis';

// 3. Type imports (use import type)
import type { IProductDetail } from '../lib/apis';

// 4. Constants and utility functions (outside component)
const EXT_TO_LANG: Record<string, string> = { ... };
function inferLanguage(fileName: string): string { ... }

// 5. Interface definitions (Props / local types)
interface McpDetailProps { ... }

// 6. Internal sub-components (not exported)
function ToolCard({ tool }: { tool: McpTool }) { ... }

// 7. Main component
function McpDetail() { ... }

// 8. Export
export default McpDetail;
```

### 2.3 Component Splitting Guidelines

| Metric                 | Threshold | Recommendation                                           |
| ---------------------- | --------- | -------------------------------------------------------- |
| Single file line count | 800 lines | Consider splitting (orchestration pages may retain more) |
| Single file line count | 600 lines | Worth reviewing                                          |
| Hooks per component    | 10        | Consider splitting                                       |
| Custom hook line count | 300 lines | Worth reviewing                                          |

> Whether to split depends on business complexity. Orchestration pages (e.g., Coding.tsx) may naturally have more lines because they compose many child components; components with dense business logic and state management should be prioritized for splitting.

**Splitting directions**:

- State logic -> Extract to `hooks/use{Domain}{Action}.ts`
- Modal / dialog -> Extract to standalone component
- Config panel -> Extract to standalone component
- Pure display block -> Extract to display component

---

## 3. Type Definitions

### 3.1 Type Placement

| Scope                   | Location                     | Example                                     |
| ----------------------- | ---------------------------- | ------------------------------------------- |
| Cross-module reuse      | `src/types/`                 | `ApiProduct`, `ProductType`, `IMcpToolCall` |
| API request/response    | `src/lib/apis/typing.ts`     | `IProductDetail`, `RespI<T>`                |
| Context state           | `src/context/{name}Types.ts` | `CodingState`, `CodingAction`               |
| Domain reuse            | `src/components/{domain}/`   | `McpServerItem`                             |
| Component-internal only | Inside component file        | `Props`, local interface                    |

### 3.2 Type Naming

```tsx
// Interfaces: PascalCase
interface ApiProductConfig { ... }

// Legacy code uses I-prefix (IProductDetail, IMcpMeta); new code omits it
// Stay consistent within a single module

// Props interface: {ComponentName}Props
interface SidebarProps { ... }
interface ProductHeaderProps { ... }

// Constant enums: const as const
export const ProductType = {
  AGENT_API: 'AGENT_API',
  MCP_SERVER: 'MCP_SERVER',
  MODEL_API: 'MODEL_API',
  REST_API: 'REST_API',
} as const;
export type ProductType = (typeof ProductType)[keyof typeof ProductType];
```

### 3.3 Type Usage Principles

- Use `import type` for pure type imports, separate from runtime imports
- Avoid `as` type assertions; prefer conditional rendering or type guards
- Use `?:` for optional fields, not `| undefined`
- API responses use `RespI<T>` generic wrapper

---

## 4. State Management

### 4.1 Strategy Selection

| Scenario                                          | Solution                         | Example                                 |
| ------------------------------------------------- | -------------------------------- | --------------------------------------- |
| Simple component state                            | `useState`                       | Form input, loading, expand/collapse    |
| Complex component state (multiple related states) | `useReducer`                     | Chat message state (`useChatReducer`)   |
| Cross-component sharing (medium complexity)       | Context + `useState` + `useMemo` | Portal config (`PortalConfigContext`)   |
| Cross-component sharing (high complexity)         | Context + `useReducer`           | Coding session (`CodingSessionContext`) |
| Global event notification                         | `useSyncExternalStore`           | Auth state (`useAuth`)                  |

### 4.2 State Variable Naming

```tsx
// Boolean state: is / has / gerund
const [loading, setLoading] = useState(false);
const [isCollapsed, setIsCollapsed] = useState(false);
const [subscribing, setSubscribing] = useState(false);

// Multiple loading states: {domain}Loading
const [consumersLoading, setConsumersLoading] = useState(false);
const [subscriptionListLoading, setSubscriptionListLoading] = useState(false);

// Collections: plural nouns
const [consumers, setConsumers] = useState<Consumer[]>([]);
const [sessions, setSessions] = useState<ChatSession[]>([]);

// Selected item: selected{Item}
const [selectedConsumerId, setSelectedConsumerId] = useState<string>('');
const [activeTab, setActiveTab] = useState<string>('intro');
```

### 4.3 State Grouping

Group states by logic with comments in complex components:

```tsx
export function AuthConfig({ consumerId }: AuthConfigProps) {
  // ===== Auth config state =====
  const [currentSource, setCurrentSource] = useState<string>('Default');
  const [currentKey, setCurrentKey] = useState<string>('Authorization');
  const [currentConfig, setCurrentConfig] = useState<ConsumerCredentialResult | null>(null);

  // ===== Credential management state =====
  const [credentialType, setCredentialType] = useState<'API_KEY' | 'HMAC'>('API_KEY');
  const [credentialModalVisible, setCredentialModalVisible] = useState(false);

  // ===== Form instances =====
  const [sourceForm] = Form.useForm();
  const [credentialForm] = Form.useForm();

  // ...
}
```

---

## 5. Custom Hooks

### 5.1 Naming & Responsibilities

```
hooks/
├── useAuth.ts                 # Auth state (login check, redirect to login)
├── useChatSession.ts          # Chat session (SSE connection, message I/O)
├── useChatReducer.ts          # Chat message state machine
├── useCodingSession.ts        # Coding session (WebSocket connection, command I/O)
├── useCodingWebSocket.ts      # Coding WebSocket low-level wrapper
├── useCodingConfig.ts         # Coding config (runtime, model selection)
├── useTerminalWebSocket.ts    # Terminal WebSocket connection
├── useResizable.ts            # Draggable resize
├── useDebounce.ts             # Debounce
├── useProducts.ts             # Product list fetching
├── useCategories.ts           # Category data fetching
```

**Naming rule**: `use{Domain}{Action}`

### 5.2 Hook Design Principles

```tsx
// Return an object so callers can destructure what they need
export function useMcpMeta() {
  const [metaList, setMetaList] = useState<McpMetaItem[]>([]);

  const fetch = useCallback(async (productId: string) => {
    // ...
  }, []);

  return { fetch, metaList };
}
```

- One hook = one data flow or a tightly coupled group of side effects
- Expose state and actions via return values; do not trigger UI feedback directly
- For complex callback logic, use factory functions extracted outside the hook:

```tsx
// Factory function at top of file
function createSSECallbacks(ctx: SSEContext): SSEOptions {
  return {
    onChunk: (chunk) => { ... },
    onComplete: (content) => { ... },
    onError: (msg) => { ... },
  };
}

// Hook calls the factory
export function useChatSession({ ... }) {
  const callbacks = createSSECallbacks({ dispatch, ... });
  // ...
}
```

---

## 6. API Integration

### 6.1 API Layer Structure

```
lib/
├── request.ts              # Axios instance and interceptors
├── api.ts                  # Re-export of request (unified entry)
├── sse.ts                  # SSE streaming request wrapper
└── apis/
    ├── index.ts            # Barrel export: aggregates all API modules
    ├── typing.ts           # API-related type definitions
    ├── product.ts          # Product APIs
    ├── consumer.ts         # Consumer APIs
    ├── chat.ts             # Chat APIs
    ├── codingSession.ts    # Coding session APIs
    ├── developer.ts        # Developer APIs
    ├── category.ts         # Category APIs
    ├── portal.ts           # Portal APIs
    └── cliProvider.ts      # CLI Provider APIs
```

### 6.2 API Function Signatures

```tsx
// Use object parameter form (easy to extend and read)
export function getProducts(params: {
  type: string;
  categoryIds?: string[];
  name?: string;
  page?: number;
  size?: number;
}) {
  return request.get<RespI<GetProductsResp>, RespI<GetProductsResp>>('/products', {
    params: {
      categoryIds: params.categoryIds,
      name: params.name,
      page: params.page || 0,
      size: params.size || 100,
      type: params.type,
    },
  });
}

// Simple params can use inline objects
export function getProduct(params: { id: string }) {
  return request.get<RespI<IProductDetail>, RespI<IProductDetail>>('/products/' + params.id);
}
```

### 6.3 API Calling Patterns

```tsx
// Pattern 1: Namespace (recommended for page-level calls, intuitive)
import APIs from '../lib/apis';
const res = await APIs.getProducts({ type: 'MCP_SERVER' });

// Pattern 2: Named import (recommended for components/hooks, explicit dependencies)
import { getProduct, getProductMcpMeta } from '../lib/apis/product';
const res = await getProduct({ id: productId });
```

### 6.4 Request Layer Conventions

- Unified response type `RespI<T>`: `{ code: string; message?: string; data: T }`
- Automatic token injection: request interceptor reads `access_token` from `localStorage`
- Automatic redirect to login on 401 (except public pages)
- Pagination params: `page` (0-based) + `size`

### 6.5 API Request Deduplication (StrictMode Guard)

React 18 `StrictMode` intentionally double-invokes component effects in development (mount → cleanup → remount). `useEffect` containing data requests will fire twice on initial mount, producing duplicate API calls. This clutters backend logs, complicates debugging, and may cause race conditions. **All initialization data requests inside `useEffect` must be guarded.**

#### Scenarios Requiring Deduplication

| Scenario                     | Example                                                     | Why it duplicates                           |
| ---------------------------- | ----------------------------------------------------------- | ------------------------------------------- |
| List page initial load       | `ProductTable` fetching product list                        | Component mounts twice under StrictMode     |
| Detail page data load        | `McpDetail` fetching product detail                         | Same as above                               |
| Tab-dependent data load      | Overview panel fetching tab-specific data                   | Mount + tab prop change both trigger effect |
| Multi-endpoint parallel load | Detail page fetching meta, subscriptions, docs concurrently | Every endpoint fires twice                  |
| Global static data load      | Dropdown options, enum mappings fetched on app start        | `useEffect(() => {...}, [])` runs twice     |
| Conditional data load        | Fetching subscription status only when user is logged in    | Component remount resets condition state    |

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
const lastMetaRef = useRef<string>('');
const lastCategoriesRef = useRef<string>('');

useEffect(() => {
  if (!productId) return;

  if (lastDetailRef.current !== productId) {
    lastDetailRef.current = productId;
    fetchProductDetail(productId);
  }

  if (lastMetaRef.current !== productId) {
    lastMetaRef.current = productId;
    fetchProductMeta(productId);
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
// ❌ Wrong: product is an object; new reference triggers extra fetch
useEffect(() => {
  fetchRelatedData();
}, [product]);

// ✅ Correct: depend on primitive values only
useEffect(() => {
  fetchRelatedData();
}, [product.id, product.type]);
```

#### Custom Hook Deduplication

When encapsulating data fetching in custom hooks, apply the same guard inside the hook so consumers do not need to think about it:

```tsx
export function useProducts(productType: string) {
  const [products, setProducts] = useState<Product[]>([]);
  const lastTypeRef = useRef<string | null>(null);

  const fetch = useCallback(
    async (page = 0, size = 100) => {
      if (lastTypeRef.current === productType) return;
      lastTypeRef.current = productType;
      const res = await APIs.getProducts({ type: productType, page, size });
      setProducts(res.data.content);
    },
    [productType],
  );

  return { products, fetch };
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

Examples: `lastFetchedProductIdRef`, `lastFetchedTypeRef`, `lastPublishedKeyRef`, `hasFetchedConfigRef`.

#### Scenarios That Do NOT Need Deduplication

Do not add guards for:

- User-triggered actions (button clicks, search submit, pagination change)
- Form submission callbacks
- Polling intervals (`setInterval`)
- Real-time SSE / WebSocket listeners

---

## 7. Styling & UI Consistency

### 7.1 Styling Priority

1. **Tailwind CSS** (primary): most styling via `className`
2. **Ant Design built-in styles**: forms, tables, modals, etc.
3. **Standalone CSS files**: only for third-party component overrides (Swagger UI, Xterm, etc.)

### 7.2 Tailwind Conventions

```tsx
// Basic usage
<div className="flex items-center gap-2 p-4 rounded-lg" />

// Conditional classes
<nav className={`
  sticky top-0 z-50 transition-all duration-300
  ${isScrolled ? 'bg-gray-100/90 shadow-sm' : 'bg-transparent'}
`} />

// Arbitrary values supported
<div className="animate-[fadeIn_0.8s_ease-out_0.2s_both]" />
<div className="w-[140%] mt-[20%]" />
```

### 7.3 CSS File Usage Scenarios

Only create standalone CSS files for:

- Overriding Ant Design default styles (needs `!important` or deep selectors)
- Complex animations and transitions
- Third-party library style customization (Swagger UI, Xterm, etc.)

### 7.4 Skeleton Screens

All detail pages should provide a corresponding skeleton component in `components/loading/`:

```tsx
// Use Tailwind animate-pulse
<div className="animate-pulse">
  <div className="h-7 bg-gray-200 rounded-md w-1/3 mb-3" />
  <div className="h-4 bg-gray-200 rounded-md w-full" />
</div>;

// Usage in pages
{
  loading ? <DetailSkeleton /> : <ContentHere />;
}
```

---

## 8. Naming Conventions

### 8.1 File Naming

| Type                  | Style                  | Example                                      |
| --------------------- | ---------------------- | -------------------------------------------- |
| Page components       | PascalCase             | `McpDetail.tsx`, `AgentDetail.tsx`           |
| Reusable components   | PascalCase             | `Sidebar.tsx`, `SelectableCard.tsx`          |
| Component directories | kebab-case             | `coding/`, `mcp-creation/`, `config-panels/` |
| Custom hooks          | camelCase (use prefix) | `useChatSession.ts`, `useAuth.ts`            |
| Utility files         | camelCase              | `iconUtils.ts`, `filterUtils.ts`             |
| Type files            | camelCase              | `coding-protocol.ts`, `consumer.ts`          |
| CSS files             | Same as component      | `Sidebar.css`, `UserInfo.css`                |

### 8.2 Code Naming

```tsx
// Components: PascalCase
function McpDetail() { ... }
export function Sidebar() { ... }

// Props interfaces: {ComponentName}Props
interface SidebarProps { ... }

// Event handlers: handle + verb/action
const handleSearch = () => { ... };
const handleTryNow = () => { ... };
const handleCopy = (text: string) => { ... };

// Data fetching: fetch + noun
const fetchCategories = async () => { ... };
const fetchDetail = async () => { ... };

// Sync computation: get / is / has
const getIconUrl = (icon?: IProductIcon): string => { ... };
const isActiveTab = (path: string) => { ... };

// Constants: UPPER_SNAKE_CASE
const READ_ONLY_KINDS = new Set(['read', 'search', 'think']);
const EXT_TO_LANG: Record<string, string> = { ... };
```

---

## 9. Error Handling

### 9.1 Standard Data Fetching Pattern

```tsx
// Standard pattern: try/catch + loading + error state
const [loading, setLoading] = useState(true);
const [error, setError] = useState('');

useEffect(() => {
  const fetchData = async () => {
    try {
      const res = await APIs.getProduct({ id: productId });
      if (res.code === 'SUCCESS' && res.data) {
        setProduct(res.data);
      } else {
        setError('Product not found');
      }
    } catch (e: unknown) {
      const err = e as { message?: string };
      setError(err?.message || 'Failed to load');
    } finally {
      setLoading(false);
    }
  };
  fetchData();
}, [productId]);
```

### 9.2 Optional Data Fetching

For non-critical data (failure does not affect page display):

```tsx
// Pattern 1: Independent try/catch with comment explaining why error is ignored
try {
  const metaRes = await getProductMcpMeta(productId);
  if (metaRes.code === 'SUCCESS') {
    setMeta(metaRes.data[0]);
  }
} catch {
  // Meta may not exist; does not affect page rendering
}

// Pattern 2: Promise.catch
getProductSubscriptionStatus(id)
  .then(setStatus)
  .catch(() => {
    // Not logged in or request failed; does not affect page
  });
```

### 9.3 Error Logging

- Use `console.error` / `console.warn` for error logging in development
- Error messages in Chinese for easier debugging

---

## 10. Internationalization (i18n)

### 10.1 Resource Organization

```
locales/
├── zh-CN/
│   ├── common.json          # General text
│   ├── header.json          # Navigation header
│   ├── login.json           # Login page
│   ├── square.json          # Marketplace
│   ├── skillDetail.json     # Skill detail
│   └── workerDetail.json    # Worker detail
└── en-US/
    └── ... (same structure)
```

### 10.2 Usage

```tsx
import { useTranslation } from 'react-i18next';

function WorkerDetail() {
  const { t } = useTranslation('workerDetail');
  return <h1>{t('title')}</h1>;
}
```

- Organize translation files by namespace, one JSON per functional module
- Default namespace is `common`
- Create corresponding `zh-CN` and `en-US` files when adding new pages
- Language selection persisted to `localStorage`

---

## 11. Code Reusability Principles

### 11.1 When to Extract for Reuse

- **Same UI pattern appears twice or more** -> Extract to `components/common/`
- **Same state logic appears twice or more** -> Extract to `hooks/`
- **Same data transformation appears twice or more** -> Extract to `lib/utils/`

### 11.2 Reuse Decision Criteria

Reuse should be based on **consistent business meaning**, not superficial code similarity:

```tsx
// Same business meaning -> should reuse
<CommonCard to="/mcp/xxx">...</CommonCard>   // MCP card
<CommonCard to="/model/xxx">...</CommonCard> // Model card

// Different business meaning -> should NOT force reuse
<DetailSkeleton />              // Generic product detail skeleton
<SkillWorkerDetailSkeleton />   // Skill/Worker-specific skeleton (different layout)
```

### 11.3 Existing Reusable Components

| Component             | Location              | Purpose                                   |
| --------------------- | --------------------- | ----------------------------------------- |
| `SelectableCard`      | `components/common/`  | Card with selection state                 |
| `AdvancedSearch`      | `components/common/`  | Advanced search panel                     |
| `Market*Selector`     | `components/common/`  | MCP/Model/Skill marketplace selectors     |
| `CommonCard`          | `components/card/`    | Card container with link and hover effect |
| `DetailSkeleton`      | `components/loading/` | Detail page skeleton                      |
| `EmptyState`          | `components/`         | Empty state placeholder                   |
| `ProductDetailLayout` | `components/`         | Unified product detail page layout        |
| `ProductHeader`       | `components/`         | Product detail page header                |
| `Layout`              | `components/`         | Global page layout                        |

---

## 12. Build & Code Splitting

Vite build config uses `manualChunks` for vendor splitting:

```tsx
manualChunks: {
  'vendor-react': ['react', 'react-dom', 'react-router-dom'],
  'vendor-antd': ['antd', '@ant-design/icons'],
  'vendor-markdown': ['react-markdown', 'remark-gfm', 'rehype-highlight', 'highlight.js'],
  'vendor-swagger': ['swagger-ui-react'],
  'vendor-xterm': ['@xterm/xterm', '@xterm/addon-fit'],
}
```

When adding large third-party dependencies, consider whether they should be added to `manualChunks` for independent splitting.

---

## 13. Pre-Commit Checks

```bash
# Run from project root
./scripts/code-check.sh
```

Covers: Prettier formatting + ESLint check/fix + TypeScript type checking. Must pass before PR submission.
