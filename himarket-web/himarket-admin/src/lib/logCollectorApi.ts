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
// 批量图表查询API
export const batchChartQuery = async (
  requests: ChartQueryRequest[]
): Promise<BatchQueryResponse> => {
  return (await api.post("/batch/chart", requests)).data;
};

// 批量表格查询API
export const batchTableQuery = async (
  requests: TableQueryRequest[]
): Promise<BatchQueryResponse> => {
  return (await api.post("/batch/table", requests)).data;
};

// 批量KPI查询API
export const batchKpiQuery = async (
  requests: KpiQueryRequest[]
): Promise<BatchQueryResponse> => {
  return (await api.post("/batch/kpi", requests)).data;
};

// 日志收集器API导出
export const logCollectorApi = {
  batchChartQuery,
  batchTableQuery,
  batchKpiQuery,
};

export default logCollectorApi;
