// 日志收集器API服务封装
// 基于OpenAPI定义实现
import axios, { AxiosInstance } from "axios";
import type {
  ChartQueryRequest,
  TableQueryRequest,
  KpiQueryRequest,
  BatchQueryResponse,
} from "../types/logCollector";

const api: AxiosInstance = axios.create({
  baseURL: "/metrics",
  timeout: 10000,
  headers: {
    "Content-Type": "application/json",
  },
  withCredentials: true, // 确保跨域请求时携带 cookie
});

function processRequest(
  request: ChartQueryRequest | TableQueryRequest | KpiQueryRequest
) {
  const filters = {
    ...request.filters,
    mcp_server: request.filters.mcpServer,
    tool_name: request.filters.mcpTool,
  };
  delete filters.mcpServer;
  delete filters.mcpTool;
  return {
    ...request,
    filters,
  };
}

// 批量图表查询API
export const batchChartQuery = async (
  requests: ChartQueryRequest[]
): Promise<BatchQueryResponse> => {
  return (await api.post("/batch/chart", requests.map(processRequest))).data;
};

// 批量表格查询API
export const batchTableQuery = async (
  requests: TableQueryRequest[]
): Promise<BatchQueryResponse> => {
  return (await api.post("/batch/table", requests.map(processRequest))).data;
};

// 批量KPI查询API
export const batchKpiQuery = async (
  requests: KpiQueryRequest[]
): Promise<BatchQueryResponse> => {
  return (await api.post("/batch/kpi", requests.map(processRequest))).data;
};

export const batchLoadOptions = async (): Promise<Record<string, string[]>> => {
  const responses = await Promise.all([
    api.get("/filters/consumers"),
    api.get("/filters/apis"),
    api.get("/filters/models"),
    api.get("/filters/routes"),
    api.get("/filters/services"),
    api.get("/filters/mcp-servers"),
    api.get("/filters/mcp-tools"),
  ]);
  return {
    consumers: responses[0].data.data,
    apis: responses[1].data.data,
    models: responses[2].data.data,
    routes: responses[3].data.data,
    services: responses[4].data.data,
    mcpServers: responses[5].data.data,
    mcpTools: responses[6].data.data,
  };
};

// 日志收集器API导出
export const logCollectorApi = {
  batchChartQuery,
  batchTableQuery,
  batchKpiQuery,
};

export default logCollectorApi;
