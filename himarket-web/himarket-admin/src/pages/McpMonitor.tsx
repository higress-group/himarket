import React, { useState, useEffect, useRef } from 'react';
import { Form, DatePicker, Select, Button, Card, Statistic, Row, Col, Table, message } from 'antd';
import * as echarts from 'echarts';
import dayjs, { Dayjs } from 'dayjs';
import slsApi from '../lib/slsApi';
import { SlsQueryRequest, McpScenarios, QueryInterval } from '../types/sls';
import {
  formatDatetimeLocal,
  rangePresets,
  getTimeRangeLabel,
  formatNumber,
  DATETIME_FORMAT
} from '../utils/dateTimeUtils';
import {
  generateMultiLineChartOption,
  generateLineChartOption,
  generateEmptyChartOption,
  generateTableColumns
} from '../utils/chartUtils';

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
    routeNames: [] as string[],
    mcpToolNames: [] as string[],
    consumers: [] as string[],
    upstreamClusters: [] as string[]
  });

  // KPI数据状态
  const [kpiData, setKpiData] = useState({
    pv: '-',
    uv: '-',
    bytesReceived: '-',
    bytesSent: '-'
  });

  // 表格数据状态
  const [tableData, setTableData] = useState({
    methodDistribution: [] as any[],
    gatewayStatus: [] as any[],
    backendStatus: [] as any[],
    requestDistribution: [] as any[]
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
    const [start, end] = rangePresets.find(p => p.label === '最近1周')?.value || [];
    form.setFieldsValue({
      timeRange: [start, end],
      interval: 15
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
        routeNames: options.route_name || [],
        mcpToolNames: options.mcp_tool_name || [],
        consumers: options.consumer || [],
        upstreamClusters: options.upstream_cluster || []
      });
    } catch (error) {
      console.error('加载过滤选项失败:', error);
    }
  };

  // 监听时间范围变化
  const handleTimeRangeChange = (dates: any) => {
    if (dates && dates.length === 2) {
      const [start, end] = dates;
      const interval = form.getFieldValue('interval') || 15;
      loadFilterOptions(
        formatDatetimeLocal(start),
        formatDatetimeLocal(end),
        interval
      );
    }
  };

  // 查询KPI数据
  const queryKpiData = async (baseParams: Omit<SlsQueryRequest, 'scenario'>) => {
    try {
      const kpiScenarios = [
        McpScenarios.PV,
        McpScenarios.UV,
        McpScenarios.BYTES_RECEIVED,
        McpScenarios.BYTES_SENT
      ];

      const requests = kpiScenarios.map(scenario => ({
        ...baseParams,
        scenario
      }));

      const responses = await slsApi.batchQueryStatistics(requests);

      const getValue = (response: any, key: string) => {
        if (response.type === 'CARD' && response.stats) {
          const stat = response.stats.find((s: any) => s.key === key);
          return stat ? formatNumber(stat.value) : '-';
        }
        return '-';
      };

      setKpiData({
        pv: getValue(responses[0], 'pv'),
        uv: getValue(responses[1], 'uv'),
        bytesReceived: getValue(responses[2], 'received'),
        bytesSent: getValue(responses[3], 'sent')
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
        scenario: McpScenarios.SUCCESS_RATE
      });

      if (successRateChartInstance.current) {
        const dataPoints = successRateResponse.timeSeries?.dataPoints || [];
        const option = dataPoints.length > 0
          ? generateLineChartOption(dataPoints, { isPercentage: true, seriesName: '成功率' })
          : generateEmptyChartOption();
        successRateChartInstance.current.setOption(option, true);
      }

      // QPS趋势图
      const qpsResponse = await slsApi.queryStatistics({
        ...baseParams,
        scenario: McpScenarios.QPS_TOTAL_SIMPLE
      });

      if (qpsChartInstance.current) {
        const dataPoints = qpsResponse.timeSeries?.dataPoints || [];
        const option = dataPoints.length > 0
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
        { ...baseParams, scenario: McpScenarios.RT_P50 }
      ]);

      const rtSeries = [
        { name: '平均RT', dataPoints: rtResponses[0].timeSeries?.dataPoints || [] },
        { name: 'P99', dataPoints: rtResponses[1].timeSeries?.dataPoints || [] },
        { name: 'P95', dataPoints: rtResponses[2].timeSeries?.dataPoints || [] },
        { name: 'P90', dataPoints: rtResponses[3].timeSeries?.dataPoints || [] },
        { name: 'P50', dataPoints: rtResponses[4].timeSeries?.dataPoints || [] }
      ];

      if (rtChartInstance.current) {
        const option = rtSeries[0].dataPoints.length > 0
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
        McpScenarios.REQUEST_DISTRIBUTION
      ];

      const requests = tableScenarios.map(scenario => ({
        ...baseParams,
        scenario
      }));

      const responses = await slsApi.batchQueryStatistics(requests);

      setTableData({
        methodDistribution: responses[0].table || [],
        gatewayStatus: responses[1].table || [],
        backendStatus: responses[2].table || [],
        requestDistribution: responses[3].table || []
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
      const { timeRange, interval, cluster_id, route_name, mcp_tool_name, consumer, upstream_cluster } = values;

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
        startTime: startTimeStr,
        endTime: endTimeStr,
        interval: interval || 15,
        cluster_id,
        route_name,
        mcp_tool_name,
        consumer,
        upstream_cluster
      };

      // 并发查询所有数据
      await Promise.all([
        queryKpiData(baseParams),
        queryChartData(baseParams),
        queryTableData(baseParams)
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
      pv: '-',
      uv: '-',
      bytesReceived: '-',
      bytesSent: '-'
    });
    setTableData({
      methodDistribution: [],
      gatewayStatus: [],
      backendStatus: [],
      requestDistribution: []
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
              <Form.Item name="timeRange" label="时间范围" rules={[{ required: true, message: '请选择时间范围' }]}>
                <RangePicker
                  showTime
                  format={DATETIME_FORMAT}
                  presets={rangePresets}
                  onChange={handleTimeRangeChange}
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
            <Col flex="180px">
              <Form.Item name="interval" label="查询粒度">
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
              <Form.Item name="cluster_id" label="实例ID">
                <Select mode="tags" placeholder="请选择" style={{ width: '100%' }} options={filterOptions.clusterIds.map(v => ({ label: v, value: v }))} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="consumer" label="消费者">
                <Select mode="tags" placeholder="请选择" style={{ width: '100%' }} options={filterOptions.consumers.map(v => ({ label: v, value: v }))} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="upstream_cluster" label="服务">
                <Select mode="tags" placeholder="请选择" style={{ width: '100%' }} options={filterOptions.upstreamClusters.map(v => ({ label: v, value: v }))} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="route_name" label="MCP Server">
                <Select mode="tags" placeholder="请选择" style={{ width: '100%' }} options={filterOptions.routeNames.map(v => ({ label: v, value: v }))} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="mcp_tool_name" label="MCP Tool">
                <Select mode="tags" placeholder="请选择" style={{ width: '100%' }} options={filterOptions.mcpToolNames.map(v => ({ label: v, value: v }))} />
              </Form.Item>
            </Col>
          </Row>

          <Row>
            <Col span={24}>
              <Form.Item>
                <Button type="primary" onClick={handleQuery} loading={loading}>
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
      <Row gutter={16} className="mb-6">
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
      <Row gutter={16} className="mb-6">
        <Col span={12}>
          <Card 
            title={<span>请求成功率</span>}
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <div ref={successRateChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
        <Col span={12}>
          <Card 
            title={<span>QPS</span>}
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <div ref={qpsChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} className="mb-6">
        <Col span={24}>
          <Card 
            title={<span>请求RT/ms</span>}
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <div ref={rtChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
      </Row>

      {/* 统计表格 */}
      <Row gutter={16} className="mb-4">
        <Col span={12}>
          <Card 
            title="Method分布"
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <Table
              dataSource={tableData.methodDistribution}
              columns={generateTableColumns(tableData.methodDistribution)}
              pagination={false}
              rowKey={(_, index) => index?.toString() || '0'}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card 
            title="网关状态码分布"
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <Table
              dataSource={tableData.gatewayStatus}
              columns={generateTableColumns(tableData.gatewayStatus)}
              pagination={false}
              rowKey={(_, index) => index?.toString() || '0'}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} className="mb-4">
        <Col span={12}>
          <Card 
            title="后端服务状态分布"
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <Table
              dataSource={tableData.backendStatus}
              columns={generateTableColumns(tableData.backendStatus)}
              pagination={false}
              rowKey={(_, index) => index?.toString() || '0'}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card 
            title="请求分布"
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <Table
              dataSource={tableData.requestDistribution}
              columns={generateTableColumns(tableData.requestDistribution)}
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
