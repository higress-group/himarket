
// 与 Admin 端保持一致的 API 产品配置接口
export interface ApiProductConfig {
  spec: string;
  meta: {
    source: string;
    type: string;
  }
}

export interface ApiProductMcpConfig {
  mcpServerName: string;
  tools: string;
  meta: {
    source: string;
    mcpServerName: string;
    mcpServerConfig: any;
    fromType: string;
    protocol?: string;
  }
  mcpServerConfig: {
    path: string;
    domains: {
      domain: string;
      protocol: string;
    }[];
    rawConfig?: unknown;
  }
}

export interface ApiProductAgentConfig {
  agentAPIConfig: {
    agentProtocols: string[];  // 协议列表，包含 "a2a" 时使用 agentCard
    routes?: Array<{           // HTTP 路由（非 A2A 协议使用）
      domains: Array<{
        domain: string;
        protocol: string;
      }>;
      description: string;
      match: {
        methods: string[] | null;
        path: {
          value: string;
          type: string;
        };
        headers?: Array<{
          name: string;
          type: string;
          value: string;
        }> | null;
        queryParams?: Array<{
          name: string;
          type: string;
          value: string;
        }> | null;
      };
    }>;
    agentCard?: {              // Agent Card 信息（A2A 协议）
      name: string;
      version: string;
      description?: string;
      url?: string;
      preferredTransport?: string;
      protocolVersion?: string; // 协议版本
      skills?: Array<{
        id: string;
        name: string;
        description?: string;
        tags?: string[];
      }>;
      capabilities?: {
        streaming?: boolean;
        [key: string]: any;
      };
      additionalInterfaces?: Array<{  // 附加接口信息（注意：复数形式）
        transport: string;  // 传输协议（HTTP/gRPC/JSONRPC）
        url: string;
        [key: string]: any;
      }>;
      [key: string]: any;      // 支持其他扩展字段
    };
  };
  meta?: {                     // 元数据信息
    source?: string;           // 来源：NACOS / APIG_AI / HIGRESS 等
  };
}

export interface ApiProductModelConfig {
  modelAPIConfig: {
    modelCategory?: string;
    aiProtocols: string[];
    routes: Array<{
      domains: Array<{
        domain: string;
        protocol: string;
      }>;
      description: string;
      builtin?: boolean;
      match: {
        methods: string[] | null;
        path: {
          value: string;
          type: string;
        };
        headers?: Array<{
          name: string;
          type: string;
          value: string;
        }> | null;
        queryParams?: Array<{
          name: string;
          type: string;
          value: string;
        }> | null;
      };
    }>;
  };
}

export interface ApiProduct {
  productId: string;
  name: string;
  description: string;
  type: 'REST_API' | 'MCP_SERVER' | 'AGENT_API' | 'MODEL_API';
  category: string;
  status: 'PENDING' | 'READY' | 'PUBLISHED' | string;
  createAt: string;
  createdAt?: string; // 兼容字段
  enableConsumerAuth?: boolean;
  autoApprove?: boolean;
  apiConfig?: ApiProductConfig;
  mcpConfig?: ApiProductMcpConfig;
  agentConfig?: ApiProductAgentConfig;
  modelConfig?: ApiProductModelConfig;
  document?: string;
  icon?: ProductIcon | null;
  categories?: ProductCategoryData[];
  // 向后兼容
  apiSpec?: string;
}

export const ProductType = {
  REST_API: 'REST_API',
  MCP_SERVER: 'MCP_SERVER',
  AGENT_API: 'AGENT_API',
  MODEL_API: 'MODEL_API',
} as const;
export type ProductType = typeof ProductType[keyof typeof ProductType];

// 产品状态枚举
export const ProductStatus = {
  ENABLE: 'ENABLE',
  DISABLE: 'DISABLE',
} as const;
export type ProductStatus = typeof ProductStatus[keyof typeof ProductStatus];

// 产品类别接口
export interface ProductCategoryData {
  categoryId: string;
  name: string;
  description?: string;
  icon?: ProductIcon;
}

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
  autoApprove?: boolean;
  type: ProductType;
  document: string | null;
  icon: ProductIcon | null;
  productType: ProductType;
  productName: string;
  mcpConfig: any;
  updatedAt: string;
  lastUpdated: string;
  categories?: ProductCategoryData[];
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

// Agent API 产品
export interface AgentApiProduct extends BaseProduct {
  type: 'AGENT_API';
  agentConfig?: ApiProductAgentConfig;
  enabled?: boolean;
}

// Model API 产品
export interface ModelApiProduct extends BaseProduct {
  type: 'MODEL_API';
  modelConfig?: ApiProductModelConfig;
  enabled?: boolean;
}

// 联合类型
export type Product = RestApiProduct | McpServerProduct | AgentApiProduct | ModelApiProduct;

// 产品图标类型（与 Admin 端保持一致）
export interface ProductIcon {
  type: 'URL' | 'BASE64';
  value: string;
}

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
    protocol?: string;
  };
}


export interface IMessageVersion {
  content: string;
  firstTokenTime?: number;
  totalTime?: number;
  inputTokens?: number;
  outputTokens?: number;
}

// MCP 工具调用相关类型
export interface IMcpToolMeta {
  toolName: string;
  toolNameCn?: string | null;
  mcpName: string;
  mcpNameCn?: string | null;
}

export interface IMcpToolCall {
  toolMeta: IMcpToolMeta;
  inputSchema: string;
  input: string;
  id: string;
  type: string;
  name: string;
  arguments: string;
}

export interface IMcpToolResponse {
  toolMeta: IMcpToolMeta;
  output: string;
  id: string;
  name: string;
  responseData: string;
}

export interface IModelConversation {
  sessionId: string;
  id: string;
  name: string;
  conversations: {
    id: string;
    loading: boolean;
    questions: {
      id: string;
      content: string;
      createdAt: string;
      activeAnswerIndex: number;
      mcpToolCalls?: IMcpToolCall[];  // MCP 工具调用列表
      mcpToolResponses?: IMcpToolResponse[];  // MCP 工具响应列表
      isNewQuestion?: boolean;
      answers: {
        errorMsg: string;
        content: string;
        firstTokenTime: number;
        totalTime: number;
        inputTokens: number;
        outputTokens: number;
      }[]
    }[]
  }[]
}