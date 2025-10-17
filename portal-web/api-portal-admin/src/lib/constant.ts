// Gateway类型中文映射
export const GATEWAY_TYPE_LABELS = {
  APIG_API: 'API网关',
  HIGRESS: 'Higress网关',
  APIG_AI: 'AI网关',
  ADP_AI_GATEWAY: '专有云 AI 网关',
  APSARA_GATEWAY: '飞天企业版 AI 网关'
} as const

// Gateway类型选项
export const GATEWAY_TYPE_OPTIONS = [
  { label: GATEWAY_TYPE_LABELS.APIG_API, value: 'APIG_API' },
  { label: GATEWAY_TYPE_LABELS.HIGRESS, value: 'HIGRESS' },
  { label: GATEWAY_TYPE_LABELS.APIG_AI, value: 'APIG_AI' },
  { label: GATEWAY_TYPE_LABELS.ADP_AI_GATEWAY, value: 'ADP_AI_GATEWAY' },
  { label: GATEWAY_TYPE_LABELS.APSARA_GATEWAY, value: 'APSARA_GATEWAY' }
] as const

// 获取Gateway类型中文标签的工具函数
export const getGatewayTypeLabel = (type: keyof typeof GATEWAY_TYPE_LABELS): string => {
  return GATEWAY_TYPE_LABELS[type] || type
}
