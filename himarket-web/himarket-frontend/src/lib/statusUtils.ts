// 产品状态映射
export const ProductStatusMap: Record<string, { text: string; color: string }> = {
  DEPRECATED: { color: 'red', text: '已弃用' },
  DISABLE: { color: 'red', text: '非活跃' },
  DRAFT: { color: 'default', text: '草稿' },
  ENABLE: { color: 'green', text: '活跃' },
  PENDING: { color: 'orange', text: '待发布' },
  PUBLISHED: { color: 'green', text: '已发布' },
  READY: { color: 'blue', text: '就绪' },
};

// 订阅状态映射
export const SubscriptionStatusMap: Record<string, { text: string; color: string }> = {
  APPROVED: { color: 'green', text: '已通过' },
  PENDING: { color: 'orange', text: '待审批' },
};

// 产品分类映射
export const ProductCategoryMap: Record<string, { text: string; color: string }> = {
  COMMUNITY: { color: 'green', text: '社区' },
  CUSTOM: { color: 'orange', text: '自定义' },
  OFFICIAL: { color: 'blue', text: '官方' },
  official2: { color: 'blue', text: '官方' },
};

// 来源类型映射
export const FromTypeMap: Record<string, string> = {
  DATABASE: '数据库',
  DIRECT_ROUTE: '直接路由',
  HTTP: 'HTTP转MCP',
  MCP: 'MCP直接代理',
  OPEN_API: 'OpenAPI转MCP',
};

// 来源映射
export const SourceMap: Record<string, string> = {
  ADP_AI_GATEWAY: '专有云AI网关',
  APIG_AI: 'AI网关',
  APIG_API: 'API网关',
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
