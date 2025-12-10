import React, { useState, useEffect, useRef } from 'react';
import { Form, DatePicker, Select, Button, Card, Statistic, Row, Col, Table, message } from 'antd';
import * as echarts from 'echarts';
import dayjs, { Dayjs } from 'dayjs';
import slsApi from '../lib/slsApi';
import { SlsQueryRequest, ModelScenarios, QueryInterval } from '../types/sls';
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
 * 模型监控页面
 */
const ModelDashboard: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [timeRangeLabel, setTimeRangeLabel] = useState('');

  // 过滤选项状态
  const [filterOptions, setFilterOptions] = useState({
    clusterIds: [] as string[],
    apis: [] as string[],
    models: [] as string[],
    routes: [] as string[],
    services: [] as string[],
    consumers: [] as string[]
  });

  // KPI数据状态
  const [kpiData, setKpiData] = useState({
    pv: '-',
    uv: '-',
    fallbackCount: '-',
    inputToken: '-',
    outputToken: '-',
    totalToken: '-'
  });

  // 表格数据状态
  const [tableData, setTableData] = useState({
    modelToken: [] as any[],
    consumerToken: [] as any[],
    serviceToken: [] as any[],
    errorRequests: [] as any[],
    ratelimitedConsumer: [] as any[],
    riskLabel: [] as any[],
    riskConsumer: [] as any[]
  });

  // ECharts实例引用
  const qpsChartRef = useRef<HTMLDivElement>(null);
  const successRateChartRef = useRef<HTMLDivElement>(null);
  const tokenPerSecChartRef = useRef<HTMLDivElement>(null);
  const rtChartRef = useRef<HTMLDivElement>(null);
  const ratelimitedChartRef = useRef<HTMLDivElement>(null);
  const cacheChartRef = useRef<HTMLDivElement>(null);

  const qpsChartInstance = useRef<echarts.ECharts | null>(null);
  const successRateChartInstance = useRef<echarts.ECharts | null>(null);
  const tokenPerSecChartInstance = useRef<echarts.ECharts | null>(null);
  const rtChartInstance = useRef<echarts.ECharts | null>(null);
  const ratelimitedChartInstance = useRef<echarts.ECharts | null>(null);
  const cacheChartInstance = useRef<echarts.ECharts | null>(null);

  // 初始化ECharts实例
  useEffect(() => {
    if (qpsChartRef.current) {
      qpsChartInstance.current = echarts.init(qpsChartRef.current);
    }
    if (successRateChartRef.current) {
      successRateChartInstance.current = echarts.init(successRateChartRef.current);
    }
    if (tokenPerSecChartRef.current) {
      tokenPerSecChartInstance.current = echarts.init(tokenPerSecChartRef.current);
    }
    if (rtChartRef.current) {
      rtChartInstance.current = echarts.init(rtChartRef.current);
    }
    if (ratelimitedChartRef.current) {
      ratelimitedChartInstance.current = echarts.init(ratelimitedChartRef.current);
    }
    if (cacheChartRef.current) {
      cacheChartInstance.current = echarts.init(cacheChartRef.current);
    }

    // 组件卸载时销毁实例
    return () => {
      qpsChartInstance.current?.dispose();
      successRateChartInstance.current?.dispose();
      tokenPerSecChartInstance.current?.dispose();
      rtChartInstance.current?.dispose();
      ratelimitedChartInstance.current?.dispose();
      cacheChartInstance.current?.dispose();
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
      const options = await slsApi.fetchModelFilterOptions(startTime, endTime, interval);
      setFilterOptions({
        clusterIds: options.cluster_id || [],
        apis: options.api || [],
        models: options.model || [],
        routes: options.route || [],
        services: options.service || [],
        consumers: options.consumer || []
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
        ModelScenarios.PV,
        ModelScenarios.UV,
        ModelScenarios.FALLBACK_COUNT,
        ModelScenarios.INPUT_TOKEN_TOTAL,
        ModelScenarios.OUTPUT_TOKEN_TOTAL,
        ModelScenarios.TOKEN_TOTAL
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
        fallbackCount: getValue(responses[2], 'cnt'),
        inputToken: getValue(responses[3], 'input_token'),
        outputToken: getValue(responses[4], 'output_token'),
        totalToken: getValue(responses[5], 'token')
      });
    } catch (error) {
      console.error('查询KPI数据失败:', error);
    }
  };

  // 查询图表数据
  const queryChartData = async (baseParams: Omit<SlsQueryRequest, 'scenario'>) => {
    try {
      // QPS趋势图
      const qpsResponses = await slsApi.batchQueryStatistics([
        { ...baseParams, scenario: ModelScenarios.QPS_STREAM },
        { ...baseParams, scenario: ModelScenarios.QPS_NORMAL },
        { ...baseParams, scenario: ModelScenarios.QPS_TOTAL }
      ]);

      const qpsSeries = [
        { name: '流式QPS', dataPoints: qpsResponses[0].timeSeries?.dataPoints || [] },
        { name: '请求QPS', dataPoints: qpsResponses[1].timeSeries?.dataPoints || [] },
        { name: '总QPS', dataPoints: qpsResponses[2].timeSeries?.dataPoints || [] }
      ];

      if (qpsChartInstance.current) {
        const option = qpsSeries[0].dataPoints.length > 0
          ? generateMultiLineChartOption(qpsSeries)
          : generateEmptyChartOption();
        qpsChartInstance.current.setOption(option, true);
      }

      // 成功率趋势图
      const successRateResponse = await slsApi.queryStatistics({
        ...baseParams,
        scenario: ModelScenarios.SUCCESS_RATE
      });

      if (successRateChartInstance.current) {
        const dataPoints = successRateResponse.timeSeries?.dataPoints || [];
        const option = dataPoints.length > 0
          ? generateLineChartOption(dataPoints, { isPercentage: true, seriesName: '成功率' })
          : generateEmptyChartOption();
        successRateChartInstance.current.setOption(option, true);
      }

      // Token/s趋势图
      const tokenPerSecResponses = await slsApi.batchQueryStatistics([
        { ...baseParams, scenario: ModelScenarios.TOKEN_PER_SEC_INPUT },
        { ...baseParams, scenario: ModelScenarios.TOKEN_PER_SEC_OUTPUT },
        { ...baseParams, scenario: ModelScenarios.TOKEN_PER_SEC_TOTAL }
      ]);

      const tokenSeries = [
        { name: '输入token/s', dataPoints: tokenPerSecResponses[0].timeSeries?.dataPoints || [] },
        { name: '输出token/s', dataPoints: tokenPerSecResponses[1].timeSeries?.dataPoints || [] },
        { name: '总token/s', dataPoints: tokenPerSecResponses[2].timeSeries?.dataPoints || [] }
      ];

      if (tokenPerSecChartInstance.current) {
        const option = tokenSeries[0].dataPoints.length > 0
          ? generateMultiLineChartOption(tokenSeries)
          : generateEmptyChartOption();
        tokenPerSecChartInstance.current.setOption(option, true);
      }

      // 响应时间趋势图
      const rtResponses = await slsApi.batchQueryStatistics([
        { ...baseParams, scenario: ModelScenarios.RT_AVG_TOTAL },
        { ...baseParams, scenario: ModelScenarios.RT_AVG_STREAM },
        { ...baseParams, scenario: ModelScenarios.RT_AVG_NORMAL },
        { ...baseParams, scenario: ModelScenarios.RT_FIRST_TOKEN }
      ]);

      const rtSeries = [
        { name: '整体RT', dataPoints: rtResponses[0].timeSeries?.dataPoints || [] },
        { name: '流式RT', dataPoints: rtResponses[1].timeSeries?.dataPoints || [] },
        { name: '非流式RT', dataPoints: rtResponses[2].timeSeries?.dataPoints || [] },
        { name: '首包RT', dataPoints: rtResponses[3].timeSeries?.dataPoints || [] }
      ];

      if (rtChartInstance.current) {
        const option = rtSeries[0].dataPoints.length > 0
          ? generateMultiLineChartOption(rtSeries)
          : generateEmptyChartOption();
        rtChartInstance.current.setOption(option, true);
      }

      // 限流请求趋势图
      const ratelimitedResponse = await slsApi.queryStatistics({
        ...baseParams,
        scenario: ModelScenarios.RATELIMITED_PER_SEC
      });

      if (ratelimitedChartInstance.current) {
        const dataPoints = ratelimitedResponse.timeSeries?.dataPoints || [];
        const option = dataPoints.length > 0
          ? generateLineChartOption(dataPoints, { seriesName: '限流请求数' })
          : generateEmptyChartOption();
        ratelimitedChartInstance.current.setOption(option, true);
      }

      // 缓存命中趋势图
      const cacheResponses = await slsApi.batchQueryStatistics([
        { ...baseParams, scenario: ModelScenarios.CACHE_HIT },
        { ...baseParams, scenario: ModelScenarios.CACHE_MISS },
        { ...baseParams, scenario: ModelScenarios.CACHE_SKIP }
      ]);

      const cacheSeries = [
        { name: '命中', dataPoints: cacheResponses[0].timeSeries?.dataPoints || [] },
        { name: '未命中', dataPoints: cacheResponses[1].timeSeries?.dataPoints || [] },
        { name: '跳过', dataPoints: cacheResponses[2].timeSeries?.dataPoints || [] }
      ];

      if (cacheChartInstance.current) {
        const option = cacheSeries[0].dataPoints.length > 0
          ? generateMultiLineChartOption(cacheSeries)
          : generateEmptyChartOption();
        cacheChartInstance.current.setOption(option, true);
      }
    } catch (error) {
      console.error('查询图表数据失败:', error);
    }
  };

  // 查询表格数据
  const queryTableData = async (baseParams: Omit<SlsQueryRequest, 'scenario'>) => {
    try {
      const tableScenarios = [
        ModelScenarios.MODEL_TOKEN_TABLE,
        ModelScenarios.CONSUMER_TOKEN_TABLE,
        ModelScenarios.SERVICE_TOKEN_TABLE,
        ModelScenarios.ERROR_REQUESTS_TABLE,
        ModelScenarios.RATELIMITED_CONSUMER_TABLE,
        ModelScenarios.RISK_LABEL_TABLE,
        ModelScenarios.RISK_CONSUMER_TABLE
      ];

      const requests = tableScenarios.map(scenario => ({
        ...baseParams,
        scenario
      }));

      const responses = await slsApi.batchQueryStatistics(requests);

      setTableData({
        modelToken: responses[0].table || [],
        consumerToken: responses[1].table || [],
        serviceToken: responses[2].table || [],
        errorRequests: responses[3].table || [],
        ratelimitedConsumer: responses[4].table || [],
        riskLabel: responses[5].table || [],
        riskConsumer: responses[6].table || []
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
      const { timeRange, interval, cluster_id, api, model, route, service, consumer } = values;

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
        api,
        model,
        route,
        service,
        consumer
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
      fallbackCount: '-',
      inputToken: '-',
      outputToken: '-',
      totalToken: '-'
    });
    setTableData({
      modelToken: [],
      consumerToken: [],
      serviceToken: [],
      errorRequests: [],
      ratelimitedConsumer: [],
      riskLabel: [],
      riskConsumer: []
    });

    // 清空图表
    qpsChartInstance.current?.clear();
    successRateChartInstance.current?.clear();
    tokenPerSecChartInstance.current?.clear();
    rtChartInstance.current?.clear();
    ratelimitedChartInstance.current?.clear();
    cacheChartInstance.current?.clear();
  };

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">模型监控</h1>

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
              <Form.Item name="api" label="API">
                <Select mode="tags" placeholder="请选择" style={{ width: '100%' }} options={filterOptions.apis.map(v => ({ label: v, value: v }))} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="model" label="模型">
                <Select mode="tags" placeholder="请选择" style={{ width: '100%' }} options={filterOptions.models.map(v => ({ label: v, value: v }))} />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="consumer" label="消费者">
                <Select mode="tags" placeholder="请选择" style={{ width: '100%' }} options={filterOptions.consumers.map(v => ({ label: v, value: v }))} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="route" label="路由">
                <Select mode="tags" placeholder="请选择" style={{ width: '100%' }} options={filterOptions.routes.map(v => ({ label: v, value: v }))} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="service" label="服务">
                <Select mode="tags" placeholder="请选择" style={{ width: '100%' }} options={filterOptions.services.map(v => ({ label: v, value: v }))} />
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
        <Col span={4}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">PV</div>
              {timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
            </div>
            <div className="text-center text-2xl font-medium">{kpiData.pv}</div>
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">UV</div>
              {timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
            </div>
            <div className="text-center text-2xl font-medium">{kpiData.uv}</div>
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">Fallback请求数</div>
              {timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
            </div>
            <div className="text-center text-2xl font-medium">{kpiData.fallbackCount}</div>
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">输入Token数</div>
              {timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
            </div>
            <div className="text-center text-2xl font-medium">{kpiData.inputToken}</div>
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">输出Token数</div>
              {timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
            </div>
            <div className="text-center text-2xl font-medium">{kpiData.outputToken}</div>
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">Token总数</div>
              {timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
            </div>
            <div className="text-center text-2xl font-medium">{kpiData.totalToken}</div>
          </Card>
        </Col>
      </Row>

      {/* 时序图表 */}
      <Row gutter={16} className="mb-6">
        <Col span={12}>
          <Card 
            title={<span>QPS</span>}
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <div ref={qpsChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
        <Col span={12}>
          <Card 
            title={<span>请求成功率</span>}
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <div ref={successRateChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} className="mb-6">
        <Col span={12}>
          <Card 
            title={<span>token消耗数/s</span>}
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <div ref={tokenPerSecChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
        <Col span={12}>
          <Card 
            title={<span>请求平均RT/ms</span>}
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <div ref={rtChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} className="mb-6">
        <Col span={12}>
          <Card 
            title={<span>限流请求数/s</span>}
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <div ref={ratelimitedChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
        <Col span={12}>
          <Card 
            title={<span>缓存命中情况/s</span>}
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <div ref={cacheChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
      </Row>

      {/* 统计表格 */}
      {/* 第一行：模型token使用统计、消费者token使用统计 */}
      <Row gutter={16} className="mb-4">
        <Col span={12}>
          <Card 
            title="模型token使用统计"
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <Table
              dataSource={tableData.modelToken}
              columns={generateTableColumns(tableData.modelToken)}
              pagination={false}
              rowKey={(_, index) => index?.toString() || '0'}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card 
            title="消费者token使用统计"
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <Table
              dataSource={tableData.consumerToken}
              columns={generateTableColumns(tableData.consumerToken)}
              pagination={false}
              rowKey={(_, index) => index?.toString() || '0'}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          </Card>
        </Col>
      </Row>

      {/* 第二行：服务token使用统计、错误请求统计 */}
      <Row gutter={16} className="mb-4">
        <Col span={12}>
          <Card 
            title="服务token使用统计"
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <Table
              dataSource={tableData.serviceToken}
              columns={generateTableColumns(tableData.serviceToken)}
              pagination={false}
              rowKey={(_, index) => index?.toString() || '0'}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card 
            title="错误请求统计"
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <Table
              dataSource={tableData.errorRequests}
              columns={generateTableColumns(tableData.errorRequests)}
              pagination={false}
              rowKey={(_, index) => index?.toString() || '0'}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          </Card>
        </Col>
      </Row>

      {/* 第三行：限流消费者统计、风险类型统计、风险消费者统计 */}
      <Row gutter={16} className="mb-4">
        <Col span={8}>
          <Card 
            title="限流消费者统计"
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <Table
              dataSource={tableData.ratelimitedConsumer}
              columns={generateTableColumns(tableData.ratelimitedConsumer)}
              pagination={false}
              rowKey={(_, index) => index?.toString() || '0'}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card 
            title="风险类型统计"
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <Table
              dataSource={tableData.riskLabel}
              columns={generateTableColumns(tableData.riskLabel)}
              pagination={false}
              rowKey={(_, index) => index?.toString() || '0'}
              scroll={{ x: 'max-content' }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card 
            title="风险消费者统计"
            extra={timeRangeLabel && <span className="text-xs text-gray-400">{timeRangeLabel}</span>}
          >
            <Table
              dataSource={tableData.riskConsumer}
              columns={generateTableColumns(tableData.riskConsumer)}
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

export default ModelDashboard;
