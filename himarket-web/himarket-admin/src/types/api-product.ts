import type { ProductCategory } from "./product-category";

export interface ApiProductConfig {
  spec: string;
  meta: {
    source: string;
    type: string;
  }
}

// 产品图标类型
export interface ProductIcon {
  type: 'URL' | 'BASE64';
  value: string;
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

// API 配置相关类型
export interface RestAPIItem {
  apiId: string;
  apiName: string;
}

export interface HigressMCPItem {
  mcpServerName?: string;      // MCP Server 名称（用于 MCP Server 产品）
  modelRouteName?: string;     // Model API 路由名称（用于 Model API 产品）
  fromGatewayType: 'HIGRESS';
}

export interface NacosMCPItem {
  mcpServerName: string;
  fromGatewayType: 'NACOS';
  namespaceId: string;
}

export interface APIGAIMCPItem {
  mcpServerName: string;
  fromGatewayType: 'APIG_AI' | 'ADP_AI_GATEWAY' | 'APSARA_GATEWAY';
  mcpRouteId: string;
  mcpServerId?: string;
  apiId?: string;
  type?: string;
}

export interface AIGatewayAgentItem {
  agentApiId: string;
  agentApiName: string;
  fromGatewayType: 'APIG_AI'; // Agent API 只支持 APIG_AI 网关
}

export interface AIGatewayModelItem {
  modelApiId: string;
  modelApiName: string;
  fromGatewayType: 'APIG_AI'; // Model API 只支持 APIG_AI 网关
}

// Nacos Agent 列表项
export interface NacosAgentItem {
  agentName: string;          // Agent 名称（唯一标识）
  description?: string;       // Agent 描述
  fromGatewayType: 'NACOS';   // 标识来源
  type: string;               // 显示类型，如 "Agent API (public)"
}

export type ApiItem = RestAPIItem | HigressMCPItem | APIGAIMCPItem | NacosMCPItem | AIGatewayAgentItem | AIGatewayModelItem | NacosAgentItem;

// 关联服务配置
export interface LinkedService {
  productId: string;
  gatewayId?: string;
  nacosId?: string;
  sourceType: 'GATEWAY' | 'NACOS';
  apigRefConfig?: RestAPIItem | APIGAIMCPItem | AIGatewayAgentItem | AIGatewayModelItem;
  higressRefConfig?: HigressMCPItem;
  nacosRefConfig?: NacosMCPItem | NacosAgentItem;  // 扩展支持 Agent
  adpAIGatewayRefConfig?: APIGAIMCPItem;
  apsaraGatewayRefConfig?: APIGAIMCPItem;
}

// Product Feature Types
export interface ModelFeature {
  model?: string;
  maxTokens?: number;
  temperature?: number;
  streaming?: boolean;
  webSearch?: boolean;
}

export interface ProductFeature {
  modelFeature?: ModelFeature;
}

export interface ApiProduct {
  productId: string;
  name: string;
  description: string;
  type: 'REST_API' | 'MCP_SERVER' | 'AGENT_API' | 'MODEL_API';
  status: 'PENDING' | 'READY' | 'PUBLISHED' | string;
  createAt: string;
  enableConsumerAuth?: boolean;
  autoApprove?: boolean;
  apiConfig?: ApiProductConfig;
  mcpConfig?: ApiProductMcpConfig;
  agentConfig?: ApiProductAgentConfig;
  modelConfig?: ApiProductModelConfig;
  document?: string;
  icon?: ProductIcon | null;
  categories?: ProductCategory[];
  feature?: ProductFeature;
}