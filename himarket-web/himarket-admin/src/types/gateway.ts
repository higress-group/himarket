export interface Gateway {
  gatewayId: string
  gatewayName: string
  description?: string
  gatewayType: 'APIG_API' | 'HIGRESS' | 'APIG_AI' | 'ADP_AI_GATEWAY' | 'APSARA_GATEWAY' | 'SOFA_HIGRESS'
  createAt: string
  apigConfig?: ApigConfig
  higressConfig?: HigressConfig
  adpAIGatewayConfig?: AdpAIGatewayConfig
  apsaraGatewayConfig?: ApsaraGatewayConfig
  sofaHigressConfig?: SofaHigressConfig
}

export interface ApigConfig {
  region: string
  accessKey: string
  secretKey: string
}

export interface HigressConfig {
  username: string
  address: string
  password: string
  gatewayAddress?: string
}

export interface AdpAIGatewayConfig {
  baseUrl: string
  port: number
  authType: 'Seed' | 'Header'
  authSeed?: string
  authHeaders?: Array<{ key: string; value: string }>
}

export interface ApsaraGatewayConfig {
  regionId: string
  accessKeyId: string
  accessKeySecret?: string
  securityToken?: string
  domain: string
  product: string
  version: string
  xAcsOrganizationId: string
  xAcsCallerSdkSource?: string
  xAcsResourceGroupId?: string
  xAcsCallerType?: string
}

export interface SofaHigressConfig {
  address: string
  accessKey: string
  secretKey: string
  tenantId: string
  workspaceId: string
}

export interface NacosInstance {
  nacosId: string
  nacosName: string
  serverUrl: string
  username: string
  password?: string
  accessKey?: string
  secretKey?: string
  description: string
  adminId: string
  createAt?: string | number
}

export type GatewayType = 'APIG_API' | 'APIG_AI' | 'HIGRESS' | 'ADP_AI_GATEWAY' | 'APSARA_GATEWAY' | 'SOFA_HIGRESS'

export interface DomainResult {
  domain: string
  port?: number
  protocol?: string
  networkType?: string
}
