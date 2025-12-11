// API Endpoint 类型定义

export type EndpointType = 'MCP_TOOL' | 'REST_ROUTE' | 'AGENT' | 'MODEL';

export interface EndpointConfig {
  [key: string]: any;
}

export interface Endpoint {
  endpointId?: string;
  apiDefinitionId?: string;
  type: EndpointType;
  name: string;
  description?: string;
  sortOrder?: number;
  config: EndpointConfig;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateEndpointRequest {
  name: string;
  description?: string;
  type: EndpointType;
  sortOrder?: number;
  config: EndpointConfig;
}

export interface UpdateEndpointRequest {
  name?: string;
  description?: string;
  sortOrder?: number;
  config?: EndpointConfig;
}
