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
    agentProtocols: string[];
    routes: Array<{
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
  mcpServerName: string;
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

export type ApiItem = RestAPIItem | HigressMCPItem | APIGAIMCPItem | NacosMCPItem | AIGatewayAgentItem | AIGatewayModelItem;

// 关联服务配置
export interface LinkedService {
  productId: string;
  gatewayId?: string;
  nacosId?: string;
  sourceType: 'GATEWAY' | 'NACOS';
  apigRefConfig?: RestAPIItem | APIGAIMCPItem | AIGatewayAgentItem | AIGatewayModelItem;
  higressRefConfig?: HigressMCPItem;
  nacosRefConfig?: NacosMCPItem;
  adpAIGatewayRefConfig?: APIGAIMCPItem;
  apsaraGatewayRefConfig?: APIGAIMCPItem;
}

// Product Feature Types
export interface ModelFeature {
  model?: string;
  maxTokens?: number;
  temperature?: number;
  streaming?: boolean;
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