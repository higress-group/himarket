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

export interface ModelApiConfig {
  modelApiName: string;
  aiProtocols?: string[];
  basePath?: string;
  domains?: Array<{
    domain: string;
    protocol: string;
  }>;
  services?: Array<{
    modelName?: string | null;
    serviceName: string;
    protocol?: string | null;
    address?: string;
    protocols?: string[];
  }>;
  routes?: Array<{
    name: string;
    methods?: string[];
    paths?: Array<{
      type: string;
      value: string;
    }>;
    ignoreUriCase?: boolean;
  }>;
}

export interface ApiProduct {
  productId: string;
  name: string;
  description: string;
  type: 'REST_API' | 'MCP_SERVER' | 'MODEL_API';
  category: string;
  status: 'PENDING' | 'READY' | 'PUBLISHED' | string;
  createAt: string;
  enableConsumerAuth?: boolean;
  autoApprove?: boolean;
  apiConfig?: ApiProductConfig;
  modelApiConfig?: ModelApiConfig;
  mcpConfig?: ApiProductMcpConfig;
  document?: string;
  icon?: string;
} 
