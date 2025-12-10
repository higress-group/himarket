/**
 * SLS日志查询API服务封装
 */
import api from './api';
import {
  SlsQueryRequest,
  ScenarioQueryResponse,
  FilterOptions,
  ModelScenarios,
  McpScenarios
} from '../types/sls';

/**
 * 查询SLS统计数据
 * @param request 查询请求参数
 * @returns 场景查询响应
 */
export async function querySlsStatistics(
  request: SlsQueryRequest
): Promise<ScenarioQueryResponse> {
  const response: any = await api.post('/sls/statistics', request);
  // 解包后端响应，从 { code, message, data } 中提取 data
  return response.data || response;
}

/**
 * 批量查询SLS统计数据
 * @param requests 多个查询请求
 * @returns 多个场景查询响应
 */
export async function batchQuerySlsStatistics(
  requests: SlsQueryRequest[]
): Promise<ScenarioQueryResponse[]> {
  return Promise.all(requests.map(req => querySlsStatistics(req)));
}

/**
 * 数据清洗：过滤无效值
 * @param values 原始值数组
 * @returns 清洗后的值数组
 */
function cleanFilterValues(values: any[]): string[] {
  if (!Array.isArray(values)) return [];
  
  return values
    .filter(v => v != null && v !== '') // 过滤null、undefined、空字符串
    .map(v => String(v).trim()) // trim处理
    .map(v => {
      // 移除两端的引号
      if ((v.startsWith('"') && v.endsWith('"')) || 
          (v.startsWith("'") && v.endsWith("'"))) {
        return v.slice(1, -1);
      }
      return v;
    })
    .filter(v => v !== '' && v !== '-'); // 再次过滤空字符串和占位符
}

/**
 * 查询模型监控的过滤选项
 * @param startTime 开始时间
 * @param endTime 结束时间
 * @param interval 查询粒度
 * @returns 过滤选项
 */
export async function fetchModelFilterOptions(
  startTime: string,
  endTime: string,
  interval: 1 | 15 | 60
): Promise<FilterOptions> {
  const baseParams = { startTime, endTime, interval };
  
  // 并发查询所有过滤选项场景
  const requests: SlsQueryRequest[] = [
    { ...baseParams, scenario: ModelScenarios.FILTER_SERVICE_OPTIONS },
    { ...baseParams, scenario: ModelScenarios.FILTER_API_OPTIONS },
    { ...baseParams, scenario: ModelScenarios.FILTER_MODEL_OPTIONS },
    { ...baseParams, scenario: ModelScenarios.FILTER_ROUTE_OPTIONS },
    { ...baseParams, scenario: ModelScenarios.FILTER_UPSTREAM_OPTIONS },
    { ...baseParams, scenario: ModelScenarios.FILTER_CONSUMER_OPTIONS }
  ];

  const responses = await batchQuerySlsStatistics(requests);

  // 提取并清洗数据
  const extractField = (response: ScenarioQueryResponse, field: string): string[] => {
    if (response.type !== 'TABLE' || !response.table) return [];
    return cleanFilterValues(response.table.map((row: any) => row[field]));
  };

  return {
    cluster_id: extractField(responses[0], 'service'),
    api: extractField(responses[1], 'api'),
    model: extractField(responses[2], 'model'),
    route: extractField(responses[3], 'route_name'),
    service: extractField(responses[4], 'upstream_cluster'),
    consumer: extractField(responses[5], 'consumer')
  };
}

/**
 * 查询MCP监控的过滤选项
 * @param startTime 开始时间
 * @param endTime 结束时间
 * @param interval 查询粒度
 * @returns 过滤选项
 */
export async function fetchMcpFilterOptions(
  startTime: string,
  endTime: string,
  interval: 1 | 15 | 60
): Promise<FilterOptions> {
  const baseParams = { startTime, endTime, interval };
  
  // 并发查询所有过滤选项场景
  const requests: SlsQueryRequest[] = [
    { ...baseParams, scenario: McpScenarios.FILTER_SERVICE_OPTIONS },
    { ...baseParams, scenario: McpScenarios.FILTER_ROUTE_OPTIONS },
    { ...baseParams, scenario: McpScenarios.FILTER_MCP_TOOL_OPTIONS },
    { ...baseParams, scenario: McpScenarios.FILTER_CONSUMER_OPTIONS },
    { ...baseParams, scenario: McpScenarios.FILTER_UPSTREAM_OPTIONS }
  ];

  const responses = await batchQuerySlsStatistics(requests);

  // 提取并清洗数据
  const extractField = (response: ScenarioQueryResponse, field: string): string[] => {
    if (response.type !== 'TABLE' || !response.table) return [];
    return cleanFilterValues(response.table.map((row: any) => row[field]));
  };

  return {
    cluster_id: extractField(responses[0], 'service'),
    route_name: extractField(responses[1], 'route_name'),
    mcp_tool_name: extractField(responses[2], 'mcp_tool_name'),
    consumer: extractField(responses[3], 'consumer'),
    upstream_cluster: extractField(responses[4], 'upstream_cluster')
  };
}

/**
 * SLS API导出
 */
export const slsApi = {
  queryStatistics: querySlsStatistics,
  batchQueryStatistics: batchQuerySlsStatistics,
  fetchModelFilterOptions,
  fetchMcpFilterOptions
};

export default slsApi;
