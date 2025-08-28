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
  }
  mcpServerConfig: {
    path: string;
    domains: {
      domain: string;
      protocol: string;
    }[];
    rawConfig: string;
  }
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
  apiConfig?: ApiProductConfig;
  mcpConfig?: ApiProductMcpConfig;
  document?: string;
  icon?: string;
} 
