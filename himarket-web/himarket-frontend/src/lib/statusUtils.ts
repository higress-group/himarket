import i18n from '../i18n';

interface StatusInfo {
  color: string;
  text: string;
}

function localizedInfo(color: string, textKey: string): StatusInfo {
  return {
    color,
    get text() {
      return i18n.t(textKey);
    },
  };
}

// 产品状态映射
export const ProductStatusMap: Record<string, { text: string; color: string }> = {
  DEPRECATED: localizedInfo('red', 'status.product.deprecated'),
  DISABLE: localizedInfo('red', 'status.product.disabled'),
  DRAFT: localizedInfo('default', 'status.product.draft'),
  ENABLE: localizedInfo('green', 'status.product.enabled'),
  PENDING: localizedInfo('orange', 'status.product.pending'),
  PUBLISHED: localizedInfo('green', 'status.product.published'),
  READY: localizedInfo('blue', 'status.product.ready'),
};

// 订阅状态映射
export const SubscriptionStatusMap: Record<string, { text: string; color: string }> = {
  APPROVED: localizedInfo('green', 'status.subscription.approved'),
  PENDING: localizedInfo('orange', 'status.subscription.pending'),
};

// 产品分类映射
export const ProductCategoryMap: Record<string, { text: string; color: string }> = {
  COMMUNITY: localizedInfo('green', 'status.category.community'),
  CUSTOM: localizedInfo('orange', 'status.category.custom'),
  OFFICIAL: localizedInfo('blue', 'status.category.official'),
  official2: localizedInfo('blue', 'status.category.official'),
};

// 来源类型映射
export const FromTypeMap: Record<string, string> = {
  get DATABASE() {
    return i18n.t('status.fromType.database');
  },
  get DIRECT_ROUTE() {
    return i18n.t('status.fromType.directRoute');
  },
  get HTTP() {
    return i18n.t('status.fromType.http');
  },
  get MCP() {
    return i18n.t('status.fromType.mcp');
  },
  get OPEN_API() {
    return i18n.t('status.fromType.openApi');
  },
};

// 来源映射
export const SourceMap: Record<string, string> = {
  get ADP_AI_GATEWAY() {
    return i18n.t('status.source.adpAiGateway');
  },
  get APIG_AI() {
    return i18n.t('status.source.apigAi');
  },
  get APIG_API() {
    return i18n.t('status.source.apigApi');
  },
  HIGRESS: 'Higress',
  NACOS: 'Nacos',
};

// 类型映射
export const ProductTypeMap: Record<string, string> = {
  AGENT_API: 'Agent API',
  AGENT_SKILL: 'Agent Skill',
  MCP_SERVER: 'MCP Server',
  MODEL_API: 'Model API',
  REST_API: 'REST API',
  WORKER: 'Worker',
};

// 获取状态信息
export const getStatusInfo = (status: string) => {
  return ProductStatusMap[status] || { color: 'default', text: status };
};

// 获取分类信息
export const getCategoryInfo = (category: string) => {
  return ProductCategoryMap[category] || { color: 'default', text: category };
};

// 获取状态文本
export const getStatusText = (status: string) => {
  return getStatusInfo(status).text;
};

// 获取状态颜色
export const getStatusColor = (status: string) => {
  return getStatusInfo(status).color;
};

// 获取分类文本
export const getCategoryText = (category: string) => {
  return getCategoryInfo(category).text;
};

// 获取分类颜色
export const getCategoryColor = (category: string) => {
  return getCategoryInfo(category).color;
};

// 获取订阅状态信息
export const getSubscriptionStatusInfo = (status: string) => {
  return SubscriptionStatusMap[status] || { color: 'default', text: status };
};

// 获取订阅状态文本
export const getSubscriptionStatusText = (status: string) => {
  return getSubscriptionStatusInfo(status).text;
};

// 获取订阅状态颜色
export const getSubscriptionStatusColor = (status: string) => {
  return getSubscriptionStatusInfo(status).color;
};
