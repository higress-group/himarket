/**
 * SLS日志查询相关类型定义
 */

/**
 * 查询粒度（秒）
 */
export type QueryInterval = 1 | 15 | 60;

/**
 * 响应数据类型
 */
export type ResponseType = 'LINE' | 'CARD' | 'TABLE';

/**
 * 通用SLS查询请求参数
 */
export interface SlsQueryRequest {
  startTime: string; // 格式: YYYY-MM-DD HH:mm:ss
  endTime: string; // 格式: YYYY-MM-DD HH:mm:ss
  scenario: string; // 场景标识
  interval: QueryInterval; // 查询粒度（秒）
  bizType?: string; // 业务类型（如 MCP_SERVER, MODEL_API 等）
  cluster_id?: string[]; // 实例ID筛选
  api?: string[]; // API筛选（模型监控专用）
  model?: string[]; // 模型筛选（模型监控专用）
  route?: string[]; // 路由筛选（模型监控专用）
  service?: string[]; // 服务筛选
  consumer?: string[]; // 消费者筛选
  route_name?: string[]; // MCP Server筛选（MCP监控专用）
  mcp_tool_name?: string[]; // MCP Tool筛选（MCP监控专用）
  upstream_cluster?: string[]; // 上游服务筛选（MCP监控专用）
}

/**
 * 时序数据点
 */
export interface DataPoint {
  timestamp: string; //格式: YYYY-MM-DD HH:mm:ss
  value: number | string;
  [key: string]: string | number | boolean | undefined; // 支持额外维度字段
}

/**
 * 时序图表响应数据
 */
export interface TimeSeriesChartResponse {
  dataPoints: DataPoint[];
  metadata?: {
    xAxisLabel?: string;
    yAxisLabel?: string;
  };
}

/**
 * 统计项
 */
export interface StatisticItem {
  key: string;
  value: string;
}

/**
 * 场景查询响应（统一响应格式）
 */
export interface ScenarioQueryResponse {
  type: ResponseType;
  timeSeries?: TimeSeriesChartResponse; // type=LINE 时返回
  stats?: StatisticItem[]; // type=CARD 时返回
  table?: Record<string, unknown>[]; // type=TABLE 时返回
}

/**
 * 过滤选项数据
 */
export interface FilterOptions {
  cluster_id?: string[];
  api?: string[];
  model?: string[];
  route?: string[];
  service?: string[];
  consumer?: string[];
  route_name?: string[];
  mcp_tool_name?: string[];
  upstream_cluster?: string[];
  mcp_server?: string[];
}

/**
 * 模型监控场景标识
 */
export const ModelScenarios = {
  CACHE_HIT: 'cache_hit',
  CACHE_MISS: 'cache_miss',
  CACHE_SKIP: 'cache_skip',
  CONSUMER_TOKEN_TABLE: 'consumer_token_table',
  ERROR_REQUESTS_TABLE: 'error_requests_table',
  FALLBACK_COUNT: 'fallback_count',

  FILTER_API_OPTIONS: 'filter_api_options',
  FILTER_CONSUMER_OPTIONS: 'filter_consumer_options',
  FILTER_MODEL_OPTIONS: 'filter_model_options',
  FILTER_ROUTE_OPTIONS: 'filter_route_options',
  // 过滤选项
  FILTER_SERVICE_OPTIONS: 'filter_service_options',
  FILTER_UPSTREAM_OPTIONS: 'filter_upstream_options',
  INPUT_TOKEN_TOTAL: 'input_token_total',
  // 统计表格
  MODEL_TOKEN_TABLE: 'model_token_table',
  OUTPUT_TOKEN_TOTAL: 'output_token_total',
  // KPI卡片
  PV: 'pv',
  QPS_NORMAL: 'qps_normal',
  // 时序图表
  QPS_STREAM: 'qps_stream',
  QPS_TOTAL: 'qps_total',
  RATELIMITED_CONSUMER_TABLE: 'ratelimited_consumer_table',
  RATELIMITED_PER_SEC: 'ratelimited_per_sec',

  RISK_CONSUMER_TABLE: 'risk_consumer_table',
  RISK_LABEL_TABLE: 'risk_label_table',
  RT_AVG_NORMAL: 'rt_avg_normal',
  RT_AVG_STREAM: 'rt_avg_stream',
  RT_AVG_TOTAL: 'rt_avg_total',
  RT_FIRST_TOKEN: 'rt_first_token',
  SERVICE_TOKEN_TABLE: 'service_token_table',

  SUCCESS_RATE: 'success_rate',
  TOKEN_PER_SEC_INPUT: 'token_per_sec_input',
  TOKEN_PER_SEC_OUTPUT: 'token_per_sec_output',
  TOKEN_PER_SEC_TOTAL: 'token_per_sec_total',
  TOKEN_TOTAL: 'token_total',
  UV: 'uv',
} as const;

/**
 * MCP监控场景标识
 */
export const McpScenarios = {
  BACKEND_STATUS_DISTRIBUTION: 'backend_status_distribution',
  BYTES_RECEIVED: 'bytes_received',
  BYTES_SENT: 'bytes_sent',
  FILTER_CONSUMER_OPTIONS: 'filter_consumer_options',

  FILTER_MCP_SERVER_OPTIONS: 'filter_mcp_server_options',
  FILTER_MCP_TOOL_OPTIONS: 'filter_mcp_tool_options',
  FILTER_ROUTE_OPTIONS: 'filter_route_options',
  // 过滤选项
  FILTER_SERVICE_OPTIONS: 'filter_service_options',
  FILTER_UPSTREAM_OPTIONS: 'filter_upstream_options',
  GATEWAY_STATUS_DISTRIBUTION: 'gateway_status_distribution',
  // 统计表格
  METHOD_DISTRIBUTION: 'method_distribution',

  // KPI卡片
  PV: 'pv',
  QPS_TOTAL_SIMPLE: 'qps_total_simple',
  REQUEST_DISTRIBUTION: 'request_distribution',
  RT_AVG: 'rt_avg',

  RT_P50: 'rt_p50',
  RT_P90: 'rt_p90',
  RT_P95: 'rt_p95',
  RT_P99: 'rt_p99',
  // 时序图表
  SUCCESS_RATE: 'success_rate',
  UV: 'uv',
} as const;
