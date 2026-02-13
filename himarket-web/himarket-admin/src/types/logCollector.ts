// 日志收集器相关类型定义

// 时间范围
export interface TimeRange {
  start: string; // 格式: YYYY-MM-DD HH:mm:ss
  end: string; // 格式: YYYY-MM-DD HH:mm:ss
}

// 图表查询请求参数
export interface ChartQueryRequest {
  timeRange: TimeRange;
  interval: string; // 例如: "60s"
  scenario: string; // 场景标识
  bizType: string; // 业务类型，如 MODEL_API
}

// 表格查询请求参数
export interface TableQueryRequest {
  tableType: string; // 表格类型，如 method_distribution
  timeRange: TimeRange;
  bizType: string; // 业务类型，如 MODEL_API
}

// KPI 查询请求参数
export interface KpiQueryRequest {
  timeRange: TimeRange;
  bizType: string; // 业务类型，如 MCP_SERVER
}

// 批量查询请求参数
export type BatchQueryRequest =
  | ChartQueryRequest
  | TableQueryRequest
  | KpiQueryRequest;

// 查询响应状态
export type QueryStatus = "success" | "error";

// 方法分布数据项
export interface MethodDistributionItem {
  avg_duration: number; // 平均耗时(ms)
  method: string; // HTTP 方法
  request_count: number; // 请求次数
}

// KPI 数据
export interface KpiData {
  fallback_count: number; // 回退次数
  input_tokens: number; // 输入 tokens
  output_tokens: number; // 输出 tokens
  pv: number; // 页面访问量
  total_tokens: number; // 总 tokens
  uv: number; // 独立访客数
  bytes_received: number; // 接收字节数
  bytes_sent: number; // 发送字节数
}

// 单个查询响应
export interface QueryResponse {
  status: QueryStatus;
  data?:
    | MethodDistributionItem[]
    | KpiData
    | {
        data: {
          input_tokens: number;
          output_tokens: number;
          request_count: number;
          service?: string;
          consumer?: string;
          model?: string;
          total_tokens: number;
        }[];
      };
  error?: string;
}

// 批量查询响应
export interface BatchQueryResponse {
  status: "success";
  data: {
    [key: string]: QueryResponse;
  };
}

// 方法分布响应数据
export interface MethodDistributionResponse {
  data: MethodDistributionItem[];
}

// KPI 响应数据
export interface KpiResponse {
  data: KpiData;
}
