/* eslint-disable react-hooks/exhaustive-deps */
import { Form, DatePicker, Select, Button, Card, Row, Col, Table, message } from 'antd';
import * as echarts from 'echarts';
import React, { useState, useEffect, useMemo, useRef } from 'react';

import { AdminMetricCard, AdminPageHeader } from '@/components/common';
import { useLocale } from '@/contexts/LocaleContext';

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
  getTimeRangeLabel,
  formatNumber,
  DATETIME_FORMAT,
  getPresetTimeRange,
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
  const { t } = useLocale();
  const [loading, setLoading] = useState(false);
  const [timeRangeLabel, setTimeRangeLabel] = useState('');
  const rangePickerPresets = useMemo(
    () => [
      { label: t('monitor.range.last1Minute'), value: getPresetTimeRange('1m') },
      { label: t('monitor.range.last5Minutes'), value: getPresetTimeRange('5m') },
      { label: t('monitor.range.last15Minutes'), value: getPresetTimeRange('15m') },
      { label: t('monitor.range.last1Hour'), value: getPresetTimeRange('1h') },
      { label: t('monitor.range.last4Hours'), value: getPresetTimeRange('4h') },
      { label: t('monitor.range.last1Day'), value: getPresetTimeRange('1d') },
      { label: t('monitor.range.today'), value: getPresetTimeRange('today') },
      { label: t('monitor.range.yesterday'), value: getPresetTimeRange('yesterday') },
      {
        label: t('monitor.range.dayBeforeYesterday'),
        value: getPresetTimeRange('dayBeforeYesterday'),
      },
      { label: t('monitor.range.last1Week'), value: getPresetTimeRange('1w') },
      { label: t('monitor.range.thisWeek'), value: getPresetTimeRange('thisWeek') },
      { label: t('monitor.range.lastWeek'), value: getPresetTimeRange('lastWeek') },
      { label: t('monitor.range.last30Days'), value: getPresetTimeRange('30d') },
      { label: t('monitor.range.thisMonth'), value: getPresetTimeRange('thisMonth') },
      { label: t('monitor.range.lastMonth'), value: getPresetTimeRange('lastMonth') },
      { label: t('monitor.range.thisQuarter'), value: getPresetTimeRange('thisQuarter') },
      { label: t('monitor.range.thisYear'), value: getPresetTimeRange('thisYear') },
    ],
    [t],
  );
  const tableColumnTitles = useMemo(
    () => ({
      backend_status: t('monitor.mcp.backendStatus'),
      cluster_id: t('monitor.common.instanceId'),
      cnt: t('monitor.table.count'),
      consumer: t('monitor.common.consumer'),
      count: t('monitor.table.count'),
      gateway_status: t('monitor.mcp.gatewayStatus'),
      mcp_server: 'MCP Server',
      mcp_tool_name: 'MCP Tool',
      method: t('monitor.mcp.method'),
      route_name: 'MCP Server',
      service: t('monitor.common.service'),
      status: t('monitor.common.status'),
      total: t('monitor.table.total'),
      upstream_cluster: t('monitor.common.service'),
    }),
    [t],
  );

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
    const [start, end] = getPresetTimeRange('1w');
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
                seriesName: t('monitor.common.successRate'),
              })
            : generateEmptyChartOption(t('monitor.common.noData'));
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
            : generateEmptyChartOption(t('monitor.common.noData'));
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
          name: t('monitor.mcp.avgRt'),
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
            : generateEmptyChartOption(t('monitor.common.noData'));
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
        message.warning(t('monitor.common.selectTimeRange'));
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
    <div>
      <AdminPageHeader
        description={t('page.mcpMonitor.description')}
        title={t('page.mcpMonitor.title')}
      />

      {/* 查询表单 */}
      <Card className="mb-6 mt-6" title={t('monitor.common.filters')}>
        <Form form={form} layout="vertical">
          <Row gutter={16}>
            <Col flex="350px">
              <Form.Item
                label={t('monitor.common.timeRange')}
                name="timeRange"
                rules={[{ message: t('monitor.common.selectTimeRange'), required: true }]}
              >
                <RangePicker
                  format={DATETIME_FORMAT}
                  onChange={handleTimeRangeChange}
                  presets={rangePickerPresets}
                  showTime
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
            <Col flex="180px">
              <Form.Item label={t('monitor.common.queryInterval')} name="interval">
                <Select style={{ width: '100%' }}>
                  <Select.Option value={1}>{t('monitor.interval.1s')}</Select.Option>
                  <Select.Option value={15}>{t('monitor.interval.15s')}</Select.Option>
                  <Select.Option value={60}>{t('monitor.interval.60s')}</Select.Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label={t('monitor.common.instanceId')} name="cluster_id">
                <Select
                  mode="tags"
                  options={filterOptions.clusterIds.map((v) => ({
                    label: v,
                    value: v,
                  }))}
                  placeholder={t('monitor.common.selectPlaceholder')}
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label={t('monitor.common.consumer')} name="consumer">
                <Select
                  mode="tags"
                  options={filterOptions.consumers.map((v) => ({
                    label: v,
                    value: v,
                  }))}
                  placeholder={t('monitor.common.selectPlaceholder')}
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label={t('monitor.common.service')} name="upstream_cluster">
                <Select
                  mode="tags"
                  options={filterOptions.upstreamClusters.map((v) => ({
                    label: v,
                    value: v,
                  }))}
                  placeholder={t('monitor.common.selectPlaceholder')}
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
                  placeholder={t('monitor.common.selectPlaceholder')}
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
                  placeholder={t('monitor.common.selectPlaceholder')}
                  style={{ width: '100%' }}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row>
            <Col span={24}>
              <Form.Item>
                <Button loading={loading} onClick={handleQuery} type="primary">
                  {t('monitor.common.query')}
                </Button>
                <Button onClick={handleReset} style={{ marginLeft: 8 }}>
                  {t('monitor.common.reset')}
                </Button>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Card>

      {/* KPI统计卡片 */}
      <div className="mb-6 grid grid-cols-[repeat(auto-fit,minmax(150px,1fr))] gap-3">
        <AdminMetricCard caption={timeRangeLabel} label="PV" value={kpiData.pv} />
        <AdminMetricCard caption={timeRangeLabel} label="UV" value={kpiData.uv} />
        <AdminMetricCard
          caption={timeRangeLabel}
          label={t('monitor.mcp.gatewayInboundTraffic')}
          value={kpiData.bytesReceived}
        />
        <AdminMetricCard
          caption={timeRangeLabel}
          label={t('monitor.mcp.gatewayOutboundTraffic')}
          value={kpiData.bytesSent}
        />
      </div>

      {/* 时序图表 */}
      <Row className="mb-6" gutter={16}>
        <Col span={12}>
          <Card
            extra={
              timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>
            }
            title={<span>{t('monitor.common.successRate')}</span>}
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
            title={<span>{t('monitor.mcp.requestRtMs')}</span>}
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
            title={t('monitor.mcp.methodDistribution')}
          >
            <Table
              columns={generateTableColumns(tableData.methodDistribution, tableColumnTitles)}
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
            title={t('monitor.mcp.gatewayStatusDistribution')}
          >
            <Table
              columns={generateTableColumns(tableData.gatewayStatus, tableColumnTitles)}
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
            title={t('monitor.mcp.backendStatusDistribution')}
          >
            <Table
              columns={generateTableColumns(tableData.backendStatus, tableColumnTitles)}
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
            title={t('monitor.mcp.requestDistribution')}
          >
            <Table
              columns={generateTableColumns(tableData.requestDistribution, tableColumnTitles)}
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
