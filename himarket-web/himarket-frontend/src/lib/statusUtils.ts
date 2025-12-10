// 产品状态映射
export const ProductStatusMap: Record<string, { text: string; color: string }> = {
  PENDING: { text: "待发布", color: "orange" },
  READY: { text: "就绪", color: "blue" },
  PUBLISHED: { text: "已发布", color: "green" },
  DRAFT: { text: "草稿", color: "default" },
  DEPRECATED: { text: "已弃用", color: "red" },
  ENABLE: { text: "活跃", color: "green" },
  DISABLE: { text: "非活跃", color: "red" },
};

// 订阅状态映射
export const SubscriptionStatusMap: Record<string, { text: string; color: string }> = {
  PENDING: { text: "待审批", color: "orange" },
  APPROVED: { text: "已通过", color: "green" },
};

// 产品分类映射
export const ProductCategoryMap: Record<string, { text: string; color: string }> = {
  OFFICIAL: { text: "官方", color: "blue" },
  COMMUNITY: { text: "社区", color: "green" },
  CUSTOM: { text: "自定义", color: "orange" },
  official2: { text: "官方", color: "blue" },
};

// 来源类型映射
export const FromTypeMap: Record<string, string> = {
  HTTP: "HTTP转MCP",
  MCP: "MCP直接代理",
  OPEN_API: "OpenAPI转MCP",
  DIRECT_ROUTE: "直接路由",
  DATABASE: "数据库",
};

// 来源映射
export const SourceMap: Record<string, string> = {
  APIG_AI: "AI网关",
  HIGRESS: "Higress",
  NACOS: "Nacos",
  APIG_API: "API网关",
  ADP_AI_GATEWAY: "专有云AI网关"
};

// 类型映射
export const ProductTypeMap: Record<string, string> = {
  REST_API: 'REST API',
  MCP_SERVER: 'MCP Server',
  AGENT_API: 'Agent API',
  MODEL_API: 'Model API',
};

// 获取状态信息
export const getStatusInfo = (status: string) => {
  return ProductStatusMap[status] || { text: status, color: "default" };
};

// 获取分类信息
export const getCategoryInfo = (category: string) => {
  return ProductCategoryMap[category] || { text: category, color: "default" };
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
  return SubscriptionStatusMap[status] || { text: status, color: "default" };
};

// 获取订阅状态文本
export const getSubscriptionStatusText = (status: string) => {
  return getSubscriptionStatusInfo(status).text;
};

// 获取订阅状态颜色
export const getSubscriptionStatusColor = (status: string) => {
  return getSubscriptionStatusInfo(status).color;
}; 