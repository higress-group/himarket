
export interface ApiProduct {
  productId: string;
  name: string;
  description: string;
  type: 'REST_API' | 'MCP_SERVER';
  category: string;
  status: 'PENDING' | 'READY' | 'PUBLISHED' | string;
  createdAt: string;
  enableConsumerAuth?: boolean;
  apiSpec: string;
}

export const ProductType = {
  REST_API: 'REST_API',
  MCP_SERVER: 'MCP_SERVER',
} as const;
export type ProductType = typeof ProductType[keyof typeof ProductType];

// 产品状态枚举
export const ProductStatus = {
  ENABLE: 'ENABLE',
  DISABLE: 'DISABLE',
} as const;
export type ProductStatus = typeof ProductStatus[keyof typeof ProductStatus];

// 产品分类
export const ProductCategory = {
  OFFICIAL: 'official',
  COMMUNITY: 'community',
  CUSTOM: 'custom',
} as const;
export type ProductCategory = typeof ProductCategory[keyof typeof ProductCategory];

// 基础产品接口
export interface BaseProduct {
  productId: string;
  name: string;
  description: string;
  status: ProductStatus;
  enableConsumerAuth: boolean | null;
  type: ProductType;
  document: string | null;
  icon: string | null;
  category: ProductCategory;
  productType: ProductType;
  productName: string;
  mcpConfig: any;
  updatedAt: string;
  lastUpdated: string;
}

// REST API 产品
export interface RestApiProduct extends BaseProduct {
  apiSpec: string | null;
  mcpSpec: null;
}

// MCP Server 产品
// @ts-ignore
export interface McpServerProduct extends BaseProduct {
  apiSpec: null;
  mcpSpec?: McpServerConfig; // 保持向后兼容
  mcpConfig?: McpConfig; // 新的nacos格式
  enabled?: boolean;
}

// 联合类型
export type Product = RestApiProduct | McpServerProduct;

// API 响应结构
export interface ApiResponse<T> {
  code: string;
  message: string | null;
  data: T;
}

// 分页响应结构
export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

// MCP 配置解析后的结构 (旧格式，保持向后兼容)
export interface McpServerConfig {
  mcpRouteId?: string;
  mcpServerName?: string;
  fromType?: string;
  fromGatewayType?: string;
  domains?: Array<{
    domain: string;
    protocol: string;
  }>;
  mcpServerConfig?: string; // YAML配置字符串
  enabled?: boolean;
  server?: {
    name: string;
    config: Record<string, unknown>;
    allowTools: string[];
  };
  tools?: Array<{
    name: string;
    description: string;
    args: Array<{
      name: string;
      description: string;
      type: string;
      required: boolean;
      position: string;
      default?: string;
      enum?: string[];
    }>;
    requestTemplate: {
      url: string;
      method: string;
      headers: Array<{
        key: string;
        value: string;
      }>;
    };
    responseTemplate: {
      body: string;
    };
  }>;
}

// 新的nacos格式MCP配置
export interface McpConfig {
  mcpServerName: string;
  mcpServerConfig: {
    path: string;
    domains: Array<{
      domain: string;
      protocol: string;
    }>;
    rawConfig?: string;
  };
  tools: string; // YAML格式的tools配置字符串
  meta: {
    source: string;
    fromType: string;
  };
}
