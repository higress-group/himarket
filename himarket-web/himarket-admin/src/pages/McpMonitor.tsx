/* eslint-disable react-hooks/exhaustive-deps */
import { Form, DatePicker, Select, Button, Card, Row, Col, Table, message } from 'antd';
import * as echarts from 'echarts';
import React, { useState, useEffect, useRef } from 'react';

import slsApi from '../lib/slsApi';
import { McpScenarios } from '../types/sls';
import {
  generateMultiLineChartOption,
  generateLineChartOption,
  generateEmptyChartOption,
  generateTableColumns,
} from '../utils/chartUtils';
import {
  formatDatetimeLocal,
  rangePresets,
  getTimeRangeLabel,
  formatNumber,
  DATETIME_FORMAT,
} from '../utils/dateTimeUtils';

import type {
  SlsQueryRequest,
  QueryInterval,
  ScenarioQueryResponse,
  StatisticItem,
} from '../types/sls';
import type { Dayjs } from 'dayjs';

const { RangePicker } = DatePicker;

/**
 * MCP监控页面
 */
const McpMonitor: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [timeRangeLabel, setTimeRangeLabel] = useState('');

  // 过滤选项状态
  const [filterOptions, setFilterOptions] = useState({
    clusterIds: [] as string[],
    consumers: [] as string[],
    mcpServers: [] as string[],
    mcpToolNames: [] as string[],
    routeNames: [] as string[],
    upstreamClusters: [] as string[],
  });

  // KPI数据状态
  const [kpiData, setKpiData] = useState({
    bytesReceived: '-',
    bytesSent: '-',
    pv: '-',
    uv: '-',
  });

  // 表格数据状态
  const [tableData, setTableData] = useState({
    backendStatus: [] as Array<Record<string, unknown>>,
    gatewayStatus: [] as Array<Record<string, unknown>>,
    methodDistribution: [] as Array<Record<string, unknown>>,
    requestDistribution: [] as Array<Record<string, unknown>>,
  });

  // ECharts实例引用
  const successRateChartRef = useRef<HTMLDivElement>(null);
  const qpsChartRef = useRef<HTMLDivElement>(null);
  const rtChartRef = useRef<HTMLDivElement>(null);

  const successRateChartInstance = useRef<echarts.ECharts | null>(null);
  const qpsChartInstance = useRef<echarts.ECharts | null>(null);
  const rtChartInstance = useRef<echarts.ECharts | null>(null);

  // 初始化ECharts实例
  useEffect(() => {
    if (successRateChartRef.current) {
      successRateChartInstance.current = echarts.init(successRateChartRef.current);
    }
    if (qpsChartRef.current) {
      qpsChartInstance.current = echarts.init(qpsChartRef.current);
    }
    if (rtChartRef.current) {
      rtChartInstance.current = echarts.init(rtChartRef.current);
    }

    // 组件卸载时销毁实例
    return () => {
      successRateChartInstance.current?.dispose();
      qpsChartInstance.current?.dispose();
      rtChartInstance.current?.dispose();
    };
  }, []);

  // 初始化默认值
  useEffect(() => {
    const [start, end] = rangePresets.find((p) => p.label === '最近1周')?.value || [];
    form.setFieldsValue({
      interval: 15,
      timeRange: [start, end],
    });
    // 自动触发一次查询
    handleQuery();
  }, []);

  // 加载过滤选项
  const loadFilterOptions = async (startTime: string, endTime: string, interval: QueryInterval) => {
    try {
      const options = await slsApi.fetchMcpFilterOptions(startTime, endTime, interval);
      setFilterOptions({
        clusterIds: options.cluster_id || [],
        consumers: options.consumer || [],
        mcpServers: options.mcp_server || [],
        mcpToolNames: options.mcp_tool_name || [],
        routeNames: options.route_name || [],
        upstreamClusters: options.upstream_cluster || [],
      });
    } catch (error) {
      console.error('加载过滤选项失败:', error);
    }
  };

  // 监听时间范围变化
  const handleTimeRangeChange = (dates: unknown) => {
    if (Array.isArray(dates) && dates.length === 2) {
      const [start, end] = dates as [Dayjs, Dayjs];
      const interval = form.getFieldValue('interval') || 15;
      loadFilterOptions(formatDatetimeLocal(start), formatDatetimeLocal(end), interval);
    }
  };

  // 查询KPI数据
  const queryKpiData = async (baseParams: Omit<SlsQueryRequest, 'scenario'>) => {
    try {
      const kpiScenarios = [
        McpScenarios.PV,
        McpScenarios.UV,
        McpScenarios.BYTES_RECEIVED,
        McpScenarios.BYTES_SENT,
      ];

      const requests = kpiScenarios.map((scenario) => ({
        ...baseParams,
        scenario,
      }));

      const responses = await slsApi.batchQueryStatistics(requests);

      const getValue = (response: ScenarioQueryResponse, key: string) => {
        if (!isCardResponse(response)) return '-';
        const stat = response.stats.find((s: StatisticItem) => s.key === key);
        return stat ? formatNumber(stat.value) : '-';
      };

      const [r0, r1, r2, r3] = responses;
      setKpiData({
        bytesReceived: r2 !== undefined ? getValue(r2, 'received') : '-',
        bytesSent: r3 !== undefined ? getValue(r3, 'sent') : '-',
        pv: r0 !== undefined ? getValue(r0, 'pv') : '-',
        uv: r1 !== undefined ? getValue(r1, 'uv') : '-',
      });
    } catch (error) {
      console.error('查询KPI数据失败:', error);
    }
  };

  // 查询图表数据
  const queryChartData = async (baseParams: Omit<SlsQueryRequest, 'scenario'>) => {
    try {
      // 请求成功率趋势图
      const successRateResponse = await slsApi.queryStatistics({
        ...baseParams,
        scenario: McpScenarios.SUCCESS_RATE,
      });

      if (successRateChartInstance.current) {
        const dataPoints = successRateResponse.timeSeries?.dataPoints || [];
        const option =
          dataPoints.length > 0
            ? generateLineChartOption(dataPoints, {
                isPercentage: true,
                seriesName: '成功率',
              })
            : generateEmptyChartOption();
        successRateChartInstance.current.setOption(option, true);
      }

      // QPS趋势图
      const qpsResponse = await slsApi.queryStatistics({
        ...baseParams,
        scenario: McpScenarios.QPS_TOTAL_SIMPLE,
      });

      if (qpsChartInstance.current) {
        const dataPoints = qpsResponse.timeSeries?.dataPoints || [];
        const option =
          dataPoints.length > 0
            ? generateLineChartOption(dataPoints, { seriesName: 'QPS' })
            : generateEmptyChartOption();
        qpsChartInstance.current.setOption(option, true);
      }

      // 响应时间趋势图
      const rtResponses = await slsApi.batchQueryStatistics([
        { ...baseParams, scenario: McpScenarios.RT_AVG },
        { ...baseParams, scenario: McpScenarios.RT_P99 },
        { ...baseParams, scenario: McpScenarios.RT_P95 },
        { ...baseParams, scenario: McpScenarios.RT_P90 },
        { ...baseParams, scenario: McpScenarios.RT_P50 },
      ]);

      const rtSeries = [
        {
          dataPoints: rtResponses[0]?.timeSeries?.dataPoints || [],
          name: '平均RT',
        },
        {
          dataPoints: rtResponses[1]?.timeSeries?.dataPoints || [],
          name: 'P99',
        },
        {
          dataPoints: rtResponses[2]?.timeSeries?.dataPoints || [],
          name: 'P95',
        },
        {
          dataPoints: rtResponses[3]?.timeSeries?.dataPoints || [],
          name: 'P90',
        },
        {
          dataPoints: rtResponses[4]?.timeSeries?.dataPoints || [],
          name: 'P50',
        },
      ];

      if (rtChartInstance.current) {
        const option =
          (rtSeries[0]?.dataPoints.length ?? 0) > 0
            ? generateMultiLineChartOption(rtSeries)
            : generateEmptyChartOption();
        rtChartInstance.current.setOption(option, true);
      }
    } catch (error) {
      console.error('查询图表数据失败:', error);
    }
  };

  // 查询表格数据
  const queryTableData = async (baseParams: Omit<SlsQueryRequest, 'scenario'>) => {
    try {
      const tableScenarios = [
        McpScenarios.METHOD_DISTRIBUTION,
        McpScenarios.GATEWAY_STATUS_DISTRIBUTION,
        McpScenarios.BACKEND_STATUS_DISTRIBUTION,
        McpScenarios.REQUEST_DISTRIBUTION,
      ];

      const requests = tableScenarios.map((scenario) => ({
        ...baseParams,
        scenario,
      }));

      const responses = await slsApi.batchQueryStatistics(requests);

      setTableData({
        backendStatus: responses[2]?.table || [],
        gatewayStatus: responses[1]?.table || [],
        methodDistribution: responses[0]?.table || [],
        requestDistribution: responses[3]?.table || [],
      });
    } catch (error) {
      console.error('查询表格数据失败:', error);
    }
  };

  // 查询按钮处理
  const handleQuery = async () => {
    try {
      await form.validateFields();
      const values = form.getFieldsValue();
      const {
        cluster_id,
        consumer,
        interval,
        mcp_tool_name,
        route_name,
        timeRange,
        upstream_cluster,
      } = values;

      if (!timeRange || timeRange.length !== 2) {
        message.warning('请选择时间范围');
        return;
      }

      setLoading(true);

      const [startTime, endTime] = timeRange;
      const startTimeStr = formatDatetimeLocal(startTime);
      const endTimeStr = formatDatetimeLocal(endTime);

      // 设置时间范围标签
      setTimeRangeLabel(getTimeRangeLabel(startTimeStr, endTimeStr));

      const baseParams: Omit<SlsQueryRequest, 'scenario'> = {
        bizType: 'MCP_SERVER',
        cluster_id,
        consumer,
        endTime: endTimeStr,
        interval: interval || 15,
        mcp_tool_name,
        route_name,
        startTime: startTimeStr,
        upstream_cluster,
      };

      // 并发查询所有数据
      await Promise.all([
        queryKpiData(baseParams),
        queryChartData(baseParams),
        queryTableData(baseParams),
      ]);

      // 查询成功后刷新过滤选项
      await loadFilterOptions(startTimeStr, endTimeStr, interval || 15);

      message.success('查询成功');
    } catch (error) {
      console.error('查询失败:', error);
    } finally {
      setLoading(false);
    }
  };

  // 重置按钮处理
  const handleReset = () => {
    form.resetFields();
    setTimeRangeLabel('');
    setKpiData({
      bytesReceived: '-',
      bytesSent: '-',
      pv: '-',
      uv: '-',
    });
    setTableData({
      backendStatus: [],
      gatewayStatus: [],
      methodDistribution: [],
      requestDistribution: [],
    });

    // 清空图表
    successRateChartInstance.current?.clear();
    qpsChartInstance.current?.clear();
    rtChartInstance.current?.clear();
  };

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">MCP监控</h1>

      {/* 查询表单 */}
      <Card className="mb-6" title="过滤条件">
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col flex="350px">
              <Form.Item
                label="时间范围"
                name="timeRange"
                rules={[{ message: '请选择时间范围', required: true }]}
              >
                <RangePicker
                  format={DATETIME_FORMAT}
                  onChange={handleTimeRangeChange}
                  presets={rangePresets}
                  showTime
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
            <Col flex="180px">
              <Form.Item label="查询粒度" name="interval">
                <Select style={{ width: '100%' }}>
                  <Select.Option value={1}>1秒</Select.Option>
                  <Select.Option value={15}>15秒</Select.Option>
                  <Select.Option value={60}>60秒</Select.Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="实例ID" name="cluster_id">
                <Select
                  mode="tags"
                  options={filterOptions.clusterIds.map((v) => ({
                    label: v,
                    value: v,
                  }))}
                  placeholder="请选择"
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="消费者" name="consumer">
                <Select
                  mode="tags"
                  options={filterOptions.consumers.map((v) => ({
                    label: v,
                    value: v,
                  }))}
                  placeholder="请选择"
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="服务" name="upstream_cluster">
                <Select
                  mode="tags"
                  options={filterOptions.upstreamClusters.map((v) => ({
                    label: v,
                    value: v,
                  }))}
                  placeholder="请选择"
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="MCP Server" name="route_name">
                <Select
                  mode="tags"
                  options={filterOptions.mcpServers.map((v) => ({
                    label: v,
                    value: v,
                  }))}
                  placeholder="请选择"
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="MCP Tool" name="mcp_tool_name">
                <Select
                  mode="tags"
                  options={filterOptions.mcpToolNames.map((v) => ({
                    label: v,
                    value: v,
                  }))}
                  placeholder="请选择"
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row>
            <Col span={24}>
              <Form.Item>
                <Button loading={loading} onClick={handleQuery} type="primary">
                  查询
                </Button>
                <Button onClick={handleReset} style={{ marginLeft: 8 }}>
                  重置
                </Button>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Card>

      {/* KPI统计卡片 */}
      <Row className="mb-6" gutter={16}>
        <Col span={6}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">PV</div>
              {timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
            </div>
            <div className="text-center text-2xl font-medium">{kpiData.pv}</div>
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">UV</div>
              {timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
            </div>
            <div className="text-center text-2xl font-medium">{kpiData.uv}</div>
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">网关入流量</div>
              {timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
            </div>
            <div className="text-center text-2xl font-medium">{kpiData.bytesReceived}</div>
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">网关出流量</div>
              {timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
            </div>
            <div className="text-center text-2xl font-medium">{kpiData.bytesSent}</div>
          </Card>
        </Col>
      </Row>

      {/* 时序图表 */}
      <Row className="mb-6" gutter={16}>
        <Col span={12}>
          <Card
            extra={
              timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>
            }
            title={<span>请求成功率</span>}
          >
            <div ref={successRateChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
        <Col span={12}>
          <Card
            extra={
              timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>
            }
            title={<span>QPS</span>}
          >
            <div ref={qpsChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
      </Row>

      <Row className="mb-6" gutter={16}>
        <Col span={24}>
          <Card
            extra={
              timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>
            }
            title={<span>请求RT/ms</span>}
          >
            <div ref={rtChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
      </Row>

      {/* 统计表格 */}
      <Row className="mb-4" gutter={16}>
        <Col span={12}>
          <Card
            extra={
              timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>
            }
            title="Method分布"
          >
            <Table
              columns={generateTableColumns(tableData.methodDistribution)}
              dataSource={tableData.methodDistribution}
              pagination={false}
              rowKey={(_, index) => index?.toString() || '0'}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card
            extra={
              timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>
            }
            title="网关状态码分布"
          >
            <Table
              columns={generateTableColumns(tableData.gatewayStatus)}
              dataSource={tableData.gatewayStatus}
              pagination={false}
              rowKey={(_, index) => index?.toString() || '0'}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          </Card>
        </Col>
      </Row>

      <Row className="mb-4" gutter={16}>
        <Col span={12}>
          <Card
            extra={
              timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>
            }
            title="后端服务状态分布"
          >
            <Table
              columns={generateTableColumns(tableData.backendStatus)}
              dataSource={tableData.backendStatus}
              pagination={false}
              rowKey={(_, index) => index?.toString() || '0'}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card
            extra={
              timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>
            }
            title="请求分布"
          >
            <Table
              columns={generateTableColumns(tableData.requestDistribution)}
              dataSource={tableData.requestDistribution}
              pagination={false}
              rowKey={(_, index) => index?.toString() || '0'}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default McpMonitor;

function isCardResponse(response: ScenarioQueryResponse): response is ScenarioQueryResponse & {
  type: 'CARD';
  stats: StatisticItem[];
} {
  return response.type === 'CARD' && Array.isArray(response.stats);
}
