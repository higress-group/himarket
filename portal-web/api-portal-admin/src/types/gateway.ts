export interface Gateway {
  gatewayId: string
  gatewayName: string
  gatewayType: 'APIG_API' | 'HIGRESS' | 'APIG_AI' | 'ADP_AI_GATEWAY'
  createAt: string
  apigConfig?: ApigConfig
  higressConfig?: HigressConfig
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
}

export type GatewayType = 'APIG_API' | 'APIG_AI' | 'HIGRESS'
