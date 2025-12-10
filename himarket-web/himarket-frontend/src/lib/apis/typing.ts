export interface IProductIcon {
  type: 'URL' | 'BASE64';
  value: string;
}

export interface IAgentConfig {
  agentAPIConfig: {
    agentProtocols: string[];
    routes?: {
      domains: {
        domain: string;
        protocol: string;
        networkType: string;
      }[],
      description: string;
      match: {
        methods: string[],
        path: {
          value: string;
          type: string;
        },
        headers: {
          name: string;
          type: string;
          value: string;
        }[];
        queryParams: {
          name: string;
          type: string;
          value: string;
        }[];
      },
      backend: {
        scene: string;
        services: {
          name: string;
          port: number;
          protocol: string;
          weight: number;
        }[]
      },
      builtin: boolean
    }[];
    agentCard?: {
      name: string;
      version: string;
      description?: string;
      url?: string;
      preferredTransport?: string;
      protocolVersion?: string;
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
      additionalInterfaces?: Array<{
        transport: string;
        url: string;
        [key: string]: any;
      }>;
      [key: string]: any;
    };
  };
  meta?: {  // 元数据信息
    source?: string;  // 来源：NACOS / APIG_AI / HIGRESS 等
  };
}

export interface IAPIConfig {
  spec: string;
  meta: {
    source: string;
    type: string;
  }
}

export interface IMCPConfig {
  mcpServerName: string;
  mcpServerConfig: {
    path: string;
    domains: {
      domain: string;
      protocol: string;
      networkType: string;
    }[];
    rawConfig: Record<string, string>;
    transportMode: string;
  },
  tools: string;
  meta: {
    source: string;
    createFromType: string;
    protocol: string;
  }
}

export interface IModelAPIConfig {
  modelCategory: string;
  aiProtocols: string[];
  routes: IRoute[];
  services: IService[];
}

export interface IRoute {
  domains: IDomain[];
  description: string;
  match: IRouteMatch;
  backend: IBackend;
  builtin: boolean;
}

export interface IDomain {
  domain: string;
  protocol: string;
  networkType: string;
}

export interface IRouteMatch {
  methods: string[];
  path: IMatchPath;
  headers: IMatchHeader[];
  queryParams: IMatchQueryParam[];
}

export interface IMatchPath {
  value: string;
  type: string;
}

export interface IMatchHeader {
  name: string;
  type: string;
  value: string;
}

export interface IMatchQueryParam {
  name: string;
  type: string;
  value: string;
}

export interface IBackend {
  scene: string;
  services: IService[];
}

export interface IService {
  name: string;
  port: number;
  protocol: string;
  weight: number;
}

export interface IModelConfig {
  modelAPIConfig: IModelAPIConfig;
}

export interface IInputSchema {
  id?: string;
  type: "string" | "number" | "boolean" | "array" | "object";
  name?: string;
  properties?: IInputSchema[];
  items?: IInputSchema[];
  required?: string[];
  description?: string;
  error?: string;
}

export interface ITool {
  name: string;
  description: string;
  inputSchema: IInputSchema;
}