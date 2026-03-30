// ============ Portal 相关请求类型 ============

export interface CreatePortalRequest {
  name: string;
  description?: string;
  [key: string]: unknown;
}

export interface UpdatePortalRequest {
  [key: string]: unknown;
}

export interface UpdatePortalSettingsRequest {
  builtinAuthEnabled?: boolean;
  autoApproveDevelopers?: boolean;
  autoApproveSubscriptions?: boolean;
  anonymousAccessEnabled?: boolean;
  [key: string]: unknown;
}

// ============ API Product 相关请求类型 ============

export interface GetApiProductsParams {
  page?: number;
  size?: number;
  type?: string;
  name?: string;
  sortBy?: string;
  [key: string]: unknown;
}

export interface CreateApiProductRequest {
  name: string;
  description?: string;
  type: string;
  icon?: { type: string; value: string };
  [key: string]: unknown;
}

export interface UpdateApiProductRequest {
  [key: string]: unknown;
}

export interface CreateApiProductRefRequest {
  [key: string]: unknown;
}

export interface GetApiProductPublicationsParams {
  page?: number;
  size?: number;
  [key: string]: unknown;
}

// ============ Gateway 相关请求类型 ============

export interface GetGatewaysParams {
  page?: number;
  size?: number;
  gatewayType?: string;
  [key: string]: unknown;
}

export interface GetApigGatewayParams {
  regionId?: string;
  gatewayId?: string;
  [key: string]: unknown;
}

export interface GetApsaraGatewaysRequest {
  [key: string]: unknown;
}

export interface GetAdpGatewaysRequest {
  [key: string]: unknown;
}

export interface ImportGatewayRequest {
  [key: string]: unknown;
}

export interface UpdateGatewayRequest {
  [key: string]: unknown;
}

export interface GetGatewayApisParams {
  page?: number;
  size?: number;
  name?: string;
  [key: string]: unknown;
}

// ============ Nacos 相关请求类型 ============

export interface GetNacosParams {
  page?: number;
  size?: number;
  [key: string]: unknown;
}

export interface CreateNacosRequest {
  nacosName: string;
  [key: string]: unknown;
}

export interface UpdateNacosRequest {
  [key: string]: unknown;
}

export interface GetNacosMcpServersParams {
  page?: number;
  size?: number;
  namespaceId?: string;
  [key: string]: unknown;
}
