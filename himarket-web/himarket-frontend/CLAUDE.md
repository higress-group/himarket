# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Himarket API Portal Frontend - A React-based developer portal for managing API products, MCP servers, AI agents, and AI models. The portal allows developers to browse, subscribe to, and manage various API products with OAuth/OIDC authentication.

## Tech Stack

- **Framework**: React 18 + TypeScript
- **Build Tool**: Vite 4.5
- **UI Library**: Ant Design 5.15
- **Routing**: React Router DOM v6
- **HTTP Client**: Axios
- **Styling**: TailwindCSS + PostCSS
- **Code Editor**: Monaco Editor (for viewing/editing specs)
- **Markdown**: react-markdown with GFM support
- **API Documentation**: Swagger UI React

## Development Commands

```bash
# Install dependencies
npm install

# Start development server (runs on http://0.0.0.0:5173)
npm run dev

# Type checking without building
npm run type-check

# Build for production
npm run build

# Lint code
npm run lint

# Preview production build
npm run preview

# Build and serve
npm run serve
```

## Architecture

### Core Structure

- **`src/router.tsx`**: Centralized route definitions using React Router v6
- **`src/lib/api.ts`**: Axios instance with request/response interceptors, all API endpoints
- **`src/types/index.ts`**: Shared TypeScript types for products (REST_API, MCP_SERVER, AGENT_API, MODEL_API)
- **`src/types/consumer.ts`**: Consumer and subscription-related types
- **`src/App.tsx`**: Root component with Ant Design ConfigProvider (Chinese locale + Aliyun theme)

### Authentication Flow

1. **OIDC Providers**: Backend provides list of OAuth/OIDC providers (Aliyun, Google, GitHub, etc.)
2. **Token Storage**: Access tokens stored in `localStorage` with key `access_token`
3. **Request Interceptor**: Automatically adds `Authorization: Bearer <token>` header to all API requests
4. **Auth Endpoints**:
   - `GET /developers/oidc/providers` - Get available OIDC providers
   - `GET /developers/oidc/callback` - Handle OAuth callback
   - `POST /developers/logout` - Logout

### Product Types

The portal manages 4 product types (defined in `src/types/index.ts`):

1. **REST_API**: Traditional REST APIs with OpenAPI specs
   - Config: `apiConfig.spec` (OpenAPI/Swagger spec)

2. **MCP_SERVER**: Model Context Protocol servers
   - Config: `mcpConfig` with server name, domains, tools (YAML format)
   - Two config formats supported: legacy and new nacos format

3. **AGENT_API**: AI agent APIs
   - Config: `agentConfig.agentAPIConfig` with routes and protocols

4. **MODEL_API**: AI model APIs
   - Config: `modelConfig.modelAPIConfig` with model category and routes

### Key Components

**Layout Components**:
- `src/components/Layout.tsx`: Main layout wrapper with loading skeleton
- `src/components/Navigation.tsx`: Top navigation bar with user info
- `src/components/ProductHeader.tsx`: Product detail page header

**Consumer Components** (in `src/components/consumer/`):
- `ConsumerBasicInfo.tsx`: Display consumer details
- `CredentialManager.tsx`: Manage API credentials
- `SubscriptionManager.tsx`: Subscribe/unsubscribe from products with advanced search
- `AuthConfig.tsx`: Authentication configuration

**Common Components**:
- `src/components/common/AdvancedSearch.tsx`: Reusable search component with filters

### Pages

- **Home**: Landing page showcasing all product types
- **APIs**: Browse REST API products
- **MCP**: Browse MCP server products
- **Agents**: Browse AI agent products
- **Models**: Browse AI model products
- **Consumers**: Manage consumers (applications)
- **Profile**: User profile and settings
- **Detail Pages**: Product-specific views with subscription management

### API Proxy Configuration

Development proxy configured in `vite.config.ts`:
- Frontend: `http://0.0.0.0:5173`
- Backend: `http://localhost:8080`
- API prefix: `/api/v1` (from `.env`)
- Proxy rewrites `/api/v1/*` to backend root

### Subscription Workflow

1. **Check Status**: `getProductSubscriptionStatus(productId)` checks if current developer has subscribed
2. **Subscribe**: `subscribeProduct(consumerId, productId)` creates subscription request
3. **Approval**: Backend may auto-approve based on product settings (`autoApprove` flag)
4. **Status Management**: Subscriptions can be PENDING, APPROVED, or REJECTED
5. **Unsubscribe**: `unsubscribeProduct(consumerId, productId)` removes subscription

### Utility Functions

- **`src/lib/statusUtils.ts`**: Status badge colors and text mapping for subscriptions
- **`src/lib/utils.ts`**: Date formatting and other utilities

## Important Patterns

### Product Icon Handling

Product icons support two types (defined in `ProductIcon` interface):
- `URL`: Direct image URL
- `BASE64`: Base64-encoded image data

### Type Safety

The codebase uses discriminated unions for product types. When working with products, check the `type` field to access type-specific configs:

```typescript
if (product.type === 'REST_API') {
  // Access product.apiConfig
} else if (product.type === 'MCP_SERVER') {
  // Access product.mcpConfig
}
```

### API Response Structure

All API responses follow this pattern:
```typescript
{
  code: string;
  message: string | null;
  data: T;
}
```

Paginated responses include: `content[]`, `totalElements`, `totalPages`, `size`, `number`, `first`, `last`

### Error Handling

The response interceptor in `src/lib/api.ts` has commented-out error handling for 401/403/404/500. Currently errors are passed through for component-level handling.

## Docker Deployment

Build platform-specific image:
```bash
docker buildx build --platform linux/amd64 -t api-portal-frontend:latest .
```

The application uses nginx for serving (see `nginx.conf` and `Dockerfile`).

## Code Style

- Uses ESLint with React hooks and React refresh plugins
- TypeScript strict mode enabled
- Tailwind utility classes for styling
- Ant Design components with custom Aliyun theme token
- Chinese locale for Ant Design (zhCN)
