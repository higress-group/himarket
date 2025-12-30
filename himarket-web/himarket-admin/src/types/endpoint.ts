// API Endpoint 类型定义

export type EndpointType = 'MCP_TOOL' | 'REST_ROUTE' | 'AGENT' | 'MODEL' | 'HTTP';

export type MatchType = 'Exact' | 'Prefix' | 'Regex';

export interface HttpMatchConfig {
  path?: {
    type: MatchType;
    value: string;
  };
  methods?: string[];
  headers?: Array<{
    name: string;
    type: MatchType;
    value: string;
  }>;
  queryParams?: Array<{
    name: string;
    type: MatchType;
    value: string;
  }>;
}

export interface HttpEndpointConfig {
  matchConfig?: HttpMatchConfig;
  description?: string;
  [key: string]: any;
}

export interface ModelEndpointConfig extends HttpEndpointConfig {
}

export interface AgentEndpointConfig extends HttpEndpointConfig {
}

export type EndpointConfig = HttpEndpointConfig | ModelEndpointConfig | AgentEndpointConfig | { [key: string]: any };

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
