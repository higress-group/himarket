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

export type ApiItem = RestAPIItem | HigressMCPItem | APIGAIMCPItem | NacosMCPItem;

// 关联服务配置
export interface LinkedService {
  productId: string;
  gatewayId?: string;
  nacosId?: string;
  sourceType: 'GATEWAY' | 'NACOS';
  apigRefConfig?: RestAPIItem | APIGAIMCPItem;
  higressRefConfig?: HigressMCPItem;
  nacosRefConfig?: NacosMCPItem;
  adpAIGatewayRefConfig?: APIGAIMCPItem;
  apsaraGatewayRefConfig?: APIGAIMCPItem;
}

export interface ApiProduct {
  productId: string;
  name: string;
  description: string;
  type: 'REST_API' | 'MCP_SERVER';
  category: string;
  status: 'PENDING' | 'READY' | 'PUBLISHED' | string;
  createAt: string;
  enableConsumerAuth?: boolean;
  autoApprove?: boolean;
  apiConfig?: ApiProductConfig;
  mcpConfig?: ApiProductMcpConfig;
  document?: string;
  icon?: ProductIcon | null;
} 
