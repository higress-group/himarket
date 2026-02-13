import React, { useState, useEffect, useRef } from "react";
import {
  Form,
  DatePicker,
  Select,
  Button,
  Card,
  Row,
  Col,
  Table,
  message,
} from "antd";
import * as echarts from "echarts";
import dayjs, { type Dayjs } from "dayjs";
import { SlsQueryRequest, QueryInterval } from "../types/sls";
import {
  formatDatetimeLocal,
  rangePresets,
  getTimeRangeLabel,
  formatNumber,
  DATETIME_FORMAT,
} from "../utils/dateTimeUtils";

import {
  batchKpiQuery,
  batchTableQuery,
  batchChartQuery,
} from "@/lib/logCollectorApi";
import type { KpiData } from "@/types/logCollector";

const { RangePicker } = DatePicker;

interface TableColumn {
  title: string;
  dataIndex: string;
  key: string;
  render?: (text: unknown) => React.ReactNode;
}

/**
 * 为日志收集器专门生成表格列定义
 * 将 model、consumer、service 等关键字段放在第一行显示
 * @param data 表格数据
 * @returns Ant Design Table的列定义
 */
const generateTableColumns = (
  data: Record<string, unknown>[]
): TableColumn[] => {
  if (!data || data.length === 0) return [];

  const firstRow = data[0];
  const keys = Object.keys(firstRow);

  // 定义优先级高的字段（放在前面）
  const priorityFields = ["model", "consumer", "service", "api", "route"];

  // 将优先字段排在前面，其他字段按字母顺序排列
  const sortedKeys = [
    ...priorityFields.filter(field => keys.includes(field)),
    ...keys.filter(key => !priorityFields.includes(key)).sort(),
  ];

  return sortedKeys.map(key => ({
    title: key,
    dataIndex: key,
    key: key,
    render: (text: unknown) => {
      // 如果是数字，格式化显示
      if (typeof text === "number") {
        return text.toLocaleString("en-US");
      }
      // 如果是空值，显示 '-'
      if (text === null || text === undefined || text === "") {
        return "-";
      }
      return text as React.ReactNode;
    },
  }));
};

/** 图表接口返回的时序结构：timestamps + values（多指标） */
interface ChartSeriesPayload {
  timestamps: number[];
  values: Record<string, number[]>;
}

function getChartPayload(res: unknown): ChartSeriesPayload | null {
  if (!res || typeof res !== "object" || !("data" in res)) return null;
  const obj = res as {
    data?: { timestamps?: number[]; values?: Record<string, number[]> };
  };
  const d = obj.data;
  if (!d?.timestamps?.length || !d?.values) return null;
  return { timestamps: d.timestamps, values: d.values };
}

/** 生成时序图 x 轴为时间的 ECharts 通用 option 基础 */
function buildTimeSeriesOption(
  timestamps: number[],
  series: { name: string; data: number[]; unit?: string }[],
  option: { title?: string; yAxisName?: string; yAxisUnit?: string } = {}
) {
  const xData = timestamps.map(ts => dayjs(ts).format("MM-DD HH:mm"));
  const yFormatter =
    option.yAxisUnit === "%"
      ? (v: number) => `${v}%`
      : (v: number) => String(v);
  return {
    tooltip: {
      trigger: "axis" as const,
      axisPointer: { type: "cross" as const },
    },
    legend: { data: series.map(s => s.name), bottom: 0 },
    grid: {
      left: "3%",
      right: "4%",
      bottom: "15%",
      top: "8%",
      containLabel: true,
    },
    xAxis: { type: "category" as const, boundaryGap: false, data: xData },
    yAxis: {
      type: "value" as const,
      name: option.yAxisName,
      axisLabel: { formatter: yFormatter },
    },
    series: series.map(s => ({
      name: s.name,
      type: "line" as const,
      smooth: true,
      data: s.data,
    })),
  };
}

/** 无数据时显示的占位 option */
const emptyChartOption = {
  graphic: {
    type: "text" as const,
    left: "center",
    top: "middle",
    style: { text: "暂无数据", fontSize: 14, fill: "#999" },
  },
};

/**
 * 模型监控页面
 */
const ModelDashboardForLogCollector: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [timeRangeLabel, setTimeRangeLabel] = useState("");

  // 过滤选项状态
  const [filterOptions, setFilterOptions] = useState({
    clusterIds: [] as string[],
    apis: [] as string[],
    models: [] as string[],
    routes: [] as string[],
    services: [] as string[],
    consumers: [] as string[],
  });

  // KPI数据状态
  const [kpiData, setKpiData] = useState({
    pv: "-",
    uv: "-",
    fallbackCount: "-",
    inputToken: "-",
    outputToken: "-",
    totalToken: "-",
  });

  // 表格数据状态
  const [tableData, setTableData] = useState({
    modelToken: [] as unknown[],
    consumerToken: [] as unknown[],
    serviceToken: [] as unknown[],
    errorRequests: [] as unknown[],
    ratelimitedConsumer: [] as unknown[],
    riskLabel: [] as unknown[],
    riskConsumer: [] as unknown[],
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
      successRateChartInstance.current = echarts.init(
        successRateChartRef.current
      );
    }
    if (tokenPerSecChartRef.current) {
      tokenPerSecChartInstance.current = echarts.init(
        tokenPerSecChartRef.current
      );
    }
    if (rtChartRef.current) {
      rtChartInstance.current = echarts.init(rtChartRef.current);
    }
    if (ratelimitedChartRef.current) {
      ratelimitedChartInstance.current = echarts.init(
        ratelimitedChartRef.current
      );
    }
    if (cacheChartRef.current) {
      cacheChartInstance.current = echarts.init(cacheChartRef.current);
    }

    const onResize = () => {
      qpsChartInstance.current?.resize();
      successRateChartInstance.current?.resize();
      tokenPerSecChartInstance.current?.resize();
      rtChartInstance.current?.resize();
      ratelimitedChartInstance.current?.resize();
      cacheChartInstance.current?.resize();
    };
    window.addEventListener("resize", onResize);

    // 组件卸载时销毁实例
    return () => {
      window.removeEventListener("resize", onResize);
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
    const [start, end] =
      rangePresets.find(p => p.label === "最近1周")?.value || [];
    form.setFieldsValue({
      timeRange: [start, end],
      interval: 15,
    });
    // 自动触发一次查询
    handleQuery();
  }, []);

  // 加载过滤选项
  const loadFilterOptions = async (
    startTime: string,
    endTime: string,
    interval: QueryInterval
  ) => {
    try {
      console.log("Loading filter options...", startTime, endTime, interval);
      const options = {
        cluster_id: [],
        api: [],
        model: [],
        route: [],
        service: [],
        consumer: [],
      };
      setFilterOptions({
        clusterIds: options.cluster_id || [],
        apis: options.api || [],
        models: options.model || [],
        routes: options.route || [],
        services: options.service || [],
        consumers: options.consumer || [],
      });
    } catch (error) {
      console.error("加载过滤选项失败:", error);
    }
  };

  // 监听时间范围变化
  const handleTimeRangeChange = (
    dates: [Dayjs | null, Dayjs | null] | null,
    dateStrings: [string, string]
  ) => {
    if (dates && dates[0] && dates[1]) {
      const [start, end] = dateStrings;
      const interval = form.getFieldValue("interval") || 15;
      loadFilterOptions(
        formatDatetimeLocal(start),
        formatDatetimeLocal(end),
        interval
      );
    }
  };

  // 查询KPI数据
  const queryKpiData = async (
    baseParams: Omit<SlsQueryRequest, "scenario">
  ) => {
    try {
      const {
        data: {
          query_0: { data },
        },
      } = await batchKpiQuery([
        {
          timeRange: {
            start: baseParams.startTime,
            end: baseParams.endTime,
          },
          bizType: "MODEL_API",
        },
      ]);

      // 类型断言：确保 data 符合 KpiData 类型
      const kpiData = data as KpiData;

      setKpiData({
        pv: formatNumber(kpiData.pv),
        uv: formatNumber(kpiData.uv),
        fallbackCount: formatNumber(kpiData.fallback_count),
        inputToken: formatNumber(kpiData.input_tokens),
        outputToken: formatNumber(kpiData.output_tokens),
        totalToken: formatNumber(kpiData.total_tokens),
      });
    } catch (error) {
      console.error("查询KPI数据失败:", error);
    }
  };

  // 查询图表数据

  const queryChartData = async (baseParams: {
    startTime: string;
    endTime: string;
    interval: QueryInterval;
  }) => {
    // 注意这地方有三条线 流式qps、请求qps、总qps
    // 请求成功率
    // 输入token/s、输出token/s、总token/s
    const { data } = await batchChartQuery([
      {
        timeRange: {
          start: baseParams.startTime,
          end: baseParams.endTime,
        },
        interval: baseParams.interval + "s",
        scenario: "qps_total_simple",
        bizType: "MODEL_API",
      },
      {
        timeRange: {
          start: baseParams.startTime,
          end: baseParams.endTime,
        },
        interval: baseParams.interval + "s",
        scenario: "success_rate",
        bizType: "MODEL_API",
      },
      {
        timeRange: {
          start: baseParams.startTime,
          end: baseParams.endTime,
        },
        interval: baseParams.interval + "s",
        scenario: "rt_distribution",
        bizType: "MODEL_API",
      },
      {
        timeRange: {
          start: baseParams.startTime,
          end: baseParams.endTime,
        },
        interval: baseParams.interval + "s",
        scenario: "token_rate",
        bizType: "MODEL_API",
      },
    ]);

    try {
      const q0 = getChartPayload(data?.query_0);
      const q1 = getChartPayload(data?.query_1);
      const q2 = getChartPayload(data?.query_2);

      // QPS 趋势图：请求 QPS、流式 QPS、总 QPS
      if (q0?.values) {
        const series = [
          { name: "请求QPS", data: q0.values.request_qps ?? [] },
          { name: "流式QPS", data: q0.values.stream_qps ?? [] },
          { name: "总QPS", data: q0.values.total_qps ?? [] },
        ].filter(s => s.data.length > 0);
        if (series.length > 0) {
          qpsChartInstance.current?.setOption(
            buildTimeSeriesOption(q0.timestamps, series),
            {
              notMerge: true,
            }
          );
        } else {
          qpsChartInstance.current?.setOption(emptyChartOption, {
            notMerge: true,
          });
        }
      } else {
        qpsChartInstance.current?.setOption(emptyChartOption, {
          notMerge: true,
        });
      }

      // 成功率趋势图
      if (q1?.values?.success_rate) {
        successRateChartInstance.current?.setOption(
          buildTimeSeriesOption(
            q1.timestamps,
            [{ name: "成功率", data: q1.values.success_rate }],
            { yAxisName: "成功率", yAxisUnit: "%" }
          ),
          {
            notMerge: true,
          }
        );
      } else {
        successRateChartInstance.current?.setOption(emptyChartOption, {
          notMerge: true,
        });
      }

      // 响应时间趋势图：平均 RT、P50/P90/P95/P99
      if (q2?.values) {
        const series = [
          { name: "平均RT", data: q2.values.avg_rt ?? [] },
          { name: "P50", data: q2.values.p50_rt ?? [] },
          { name: "P90", data: q2.values.p90_rt ?? [] },
          { name: "P95", data: q2.values.p95_rt ?? [] },
          { name: "P99", data: q2.values.p99_rt ?? [] },
        ].filter(s => s.data.length > 0);
        if (series.length > 0) {
          rtChartInstance.current?.setOption(
            buildTimeSeriesOption(q2.timestamps, series, { yAxisName: "ms" }),
            {
              notMerge: true,
            }
          );
        } else {
          rtChartInstance.current?.setOption(emptyChartOption, {
            notMerge: true,
          });
        }
      } else {
        rtChartInstance.current?.setOption(emptyChartOption, {
          notMerge: true,
        });
      }

      // Token/s、限流、缓存 当前接口未返回，显示暂无数据
      tokenPerSecChartInstance.current?.setOption(emptyChartOption, {
        notMerge: true,
      });
      ratelimitedChartInstance.current?.setOption(emptyChartOption, {
        notMerge: true,
      });
      cacheChartInstance.current?.setOption(emptyChartOption, {
        notMerge: true,
      });
    } catch (error) {
      console.error("查询图表数据失败:", error);
    }
  };

  // 查询表格数据
  const queryTableData = async (baseParams: {
    startTime: string;
    endTime: string;
    interval: QueryInterval;
  }) => {
    try {
      const { data } = await batchTableQuery([
        {
          timeRange: {
            start: baseParams.startTime,
            end: baseParams.endTime,
          },
          tableType: "model_token_stats",
          bizType: "MODEL_API",
        },
        {
          timeRange: {
            start: baseParams.startTime,
            end: baseParams.endTime,
          },
          tableType: "consumer_token_stats",
          bizType: "MODEL_API",
        },
        {
          timeRange: {
            start: baseParams.startTime,
            end: baseParams.endTime,
          },
          tableType: "service_token_stats",
          bizType: "MODEL_API",
        },
        // {
        //   timeRange: {
        //     start: baseParams.startTime,
        //     end: baseParams.endTime,
        //   },
        //   tableType: "error_requests_stats",
        //   bizType: "MODEL_API",
        // },
        // {
        //   timeRange: {
        //     start: baseParams.startTime,
        //     end: baseParams.endTime,
        //   },
        //   tableType: "ratelimited_consumer_stats",
        //   bizType: "MODEL_API",
        // },
        // {
        //   timeRange: {
        //     start: baseParams.startTime,
        //     end: baseParams.endTime,
        //   },
        //   tableType: "risk_label_stats",
        //   bizType: "MODEL_API",
        // },
        // {
        //   timeRange: {
        //     start: baseParams.startTime,
        //     end: baseParams.endTime,
        //   },
        //   tableType: "risk_consumer_stats",
        //   bizType: "MODEL_API",
        // },
      ]);

      // 安全地访问嵌套数据，使用类型守卫处理联合类型
      const getDataArray = (queryData: unknown): unknown[] => {
        if (
          queryData &&
          typeof queryData === "object" &&
          "data" in queryData &&
          queryData.data !== null
        ) {
          const dataObj = queryData as { data: unknown };
          return Array.isArray(dataObj.data) ? dataObj.data : [];
        }
        return [];
      };

      const modelTokenData = getDataArray(data.query_0?.data);
      const consumerTokenData = getDataArray(data.query_1?.data);
      const serviceTokenData = getDataArray(data.query_2?.data);

      setTableData({
        modelToken: modelTokenData,
        consumerToken: consumerTokenData,
        serviceToken: serviceTokenData,
        errorRequests: [], // 暂时为空数组
        ratelimitedConsumer: [], // 暂时为空数组
        riskLabel: [], // 暂时为空数组
        riskConsumer: [], // 暂时为空数组
      });
    } catch (error) {
      console.error("查询表格数据失败:", error);
    }
  };

  // 查询按钮处理
  const handleQuery = async () => {
    try {
      await form.validateFields();
      const values = form.getFieldsValue();
      const {
        timeRange,
        interval,
        cluster_id,
        api,
        model,
        route,
        service,
        consumer,
      } = values;

      if (!timeRange || timeRange.length !== 2) {
        message.warning("请选择时间范围");
        return;
      }

      setLoading(true);

      const [startTime, endTime] = timeRange;
      const startTimeStr = formatDatetimeLocal(startTime);
      const endTimeStr = formatDatetimeLocal(endTime);

      // 设置时间范围标签
      setTimeRangeLabel(getTimeRangeLabel(startTimeStr, endTimeStr));

      const baseParams: Omit<SlsQueryRequest, "scenario"> = {
        startTime: startTimeStr,
        endTime: endTimeStr,
        interval: interval || 15,
        cluster_id,
        api,
        model,
        route,
        service,
        consumer,
      };

      // 并发查询所有数据
      await Promise.all([
        queryKpiData(baseParams),
        queryChartData(baseParams),
        queryTableData(baseParams),
      ]);

      // 查询成功后刷新过滤选项
      await loadFilterOptions(startTimeStr, endTimeStr, interval || 15);

      message.success("查询成功");
    } catch (error) {
      console.error("查询失败:", error);
    } finally {
      setLoading(false);
    }
  };

  // 重置按钮处理
  const handleReset = () => {
    form.resetFields();
    setTimeRangeLabel("");
    setKpiData({
      pv: "-",
      uv: "-",
      fallbackCount: "-",
      inputToken: "-",
      outputToken: "-",
      totalToken: "-",
    });
    setTableData({
      modelToken: [],
      consumerToken: [],
      serviceToken: [],
      errorRequests: [],
      ratelimitedConsumer: [],
      riskLabel: [],
      riskConsumer: [],
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
              <Form.Item
                name="timeRange"
                label="时间范围"
                rules={[{ required: true, message: "请选择时间范围" }]}
              >
                <RangePicker
                  showTime
                  format={DATETIME_FORMAT}
                  presets={rangePresets}
                  onChange={handleTimeRangeChange}
                  style={{ width: "100%" }}
                />
              </Form.Item>
            </Col>
            <Col flex="180px">
              <Form.Item name="interval" label="查询粒度">
                <Select style={{ width: "100%" }}>
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
                <Select
                  mode="tags"
                  placeholder="请选择"
                  style={{ width: "100%" }}
                  options={filterOptions.clusterIds.map(v => ({
                    label: v,
                    value: v,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="api" label="API">
                <Select
                  mode="tags"
                  placeholder="请选择"
                  style={{ width: "100%" }}
                  options={filterOptions.apis.map(v => ({
                    label: v,
                    value: v,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="model" label="模型">
                <Select
                  mode="tags"
                  placeholder="请选择"
                  style={{ width: "100%" }}
                  options={filterOptions.models.map(v => ({
                    label: v,
                    value: v,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="consumer" label="消费者">
                <Select
                  mode="tags"
                  placeholder="请选择"
                  style={{ width: "100%" }}
                  options={filterOptions.consumers.map(v => ({
                    label: v,
                    value: v,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="route" label="路由">
                <Select
                  mode="tags"
                  placeholder="请选择"
                  style={{ width: "100%" }}
                  options={filterOptions.routes.map(v => ({
                    label: v,
                    value: v,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="service" label="服务">
                <Select
                  mode="tags"
                  placeholder="请选择"
                  style={{ width: "100%" }}
                  options={filterOptions.services.map(v => ({
                    label: v,
                    value: v,
                  }))}
                />
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
              {timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )}
            </div>
            <div className="text-center text-2xl font-medium">{kpiData.pv}</div>
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">UV</div>
              {timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )}
            </div>
            <div className="text-center text-2xl font-medium">{kpiData.uv}</div>
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">Fallback请求数</div>
              {timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )}
            </div>
            <div className="text-center text-2xl font-medium">
              {kpiData.fallbackCount}
            </div>
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">输入Token数</div>
              {timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )}
            </div>
            <div className="text-center text-2xl font-medium">
              {kpiData.inputToken}
            </div>
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">输出Token数</div>
              {timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )}
            </div>
            <div className="text-center text-2xl font-medium">
              {kpiData.outputToken}
            </div>
          </Card>
        </Col>
        <Col span={4}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">Token总数</div>
              {timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )}
            </div>
            <div className="text-center text-2xl font-medium">
              {kpiData.totalToken}
            </div>
          </Card>
        </Col>
      </Row>

      {/* 时序图表 */}
      <Row gutter={16} className="mb-6">
        <Col span={12}>
          <Card
            title={<span>QPS</span>}
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <div ref={qpsChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
        <Col span={12}>
          <Card
            title={<span>请求成功率</span>}
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <div ref={successRateChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} className="mb-6">
        <Col span={12}>
          <Card
            title={<span>token消耗数/s</span>}
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <div ref={tokenPerSecChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
        <Col span={12}>
          <Card
            title={<span>请求平均RT/ms</span>}
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <div ref={rtChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} className="mb-6">
        <Col span={12}>
          <Card
            title={<span>限流请求数/s</span>}
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <div ref={ratelimitedChartRef} style={{ height: 300 }} />
          </Card>
        </Col>
        <Col span={12}>
          <Card
            title={<span>缓存命中情况/s</span>}
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
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
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <Table
              dataSource={tableData.modelToken}
              columns={generateTableColumns(
                tableData.modelToken as Record<string, string>[]
              )}
              pagination={false}
              rowKey={(_, index) => index?.toString() || "0"}
              scroll={{ x: "max-content" }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card
            title="消费者token使用统计"
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <Table
              dataSource={tableData.consumerToken}
              columns={generateTableColumns(
                tableData.consumerToken as Record<string, string>[]
              )}
              pagination={false}
              rowKey={(_, index) => index?.toString() || "0"}
              scroll={{ x: "max-content" }}
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
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <Table
              dataSource={tableData.serviceToken}
              columns={generateTableColumns(
                tableData.serviceToken as Record<string, string>[]
              )}
              pagination={false}
              rowKey={(_, index) => index?.toString() || "0"}
              scroll={{ x: "max-content" }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card
            title="错误请求统计"
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <Table
              dataSource={tableData.errorRequests}
              columns={generateTableColumns(
                tableData.errorRequests as Record<string, string>[]
              )}
              pagination={false}
              rowKey={(_, index) => index?.toString() || "0"}
              scroll={{ x: "max-content" }}
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
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <Table
              dataSource={tableData.ratelimitedConsumer}
              columns={generateTableColumns(
                tableData.ratelimitedConsumer as Record<string, string>[]
              )}
              pagination={false}
              rowKey={(_, index) => index?.toString() || "0"}
              scroll={{ x: "max-content" }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card
            title="风险类型统计"
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <Table
              dataSource={tableData.riskLabel}
              columns={generateTableColumns(
                tableData.riskLabel as Record<string, string>[]
              )}
              pagination={false}
              rowKey={(_, index) => index?.toString() || "0"}
              scroll={{ x: "max-content" }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card
            title="风险消费者统计"
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <Table
              dataSource={tableData.riskConsumer}
              columns={generateTableColumns(
                tableData.riskConsumer as Record<string, string>[]
              )}
              pagination={false}
              rowKey={(_, index) => index?.toString() || "0"}
              scroll={{ x: "max-content" }}
              size="small"
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default ModelDashboardForLogCollector;
