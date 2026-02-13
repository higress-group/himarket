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
import {
  formatDatetimeLocal,
  rangePresets,
  getTimeRangeLabel,
  DATETIME_FORMAT,
  formatNumber,
} from "../utils/dateTimeUtils";
import dayjs from "dayjs";
import { KpiData } from "@/types/logCollector";
import {
  batchKpiQuery,
  batchTableQuery,
  batchChartQuery,
} from "@/lib/logCollectorApi";

const { RangePicker } = DatePicker;

// 表格列定义
interface TableColumn {
  title: string;
  dataIndex: string;
  key: string;
  render?: (text: unknown) => React.ReactNode;
}

// 表格行数据类型
interface TableRowData {
  [key: string]: string | number;
}

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

/** 生成时序图 x 轴为时间的 ECharts 通用 option */
function buildTimeSeriesOption(
  timestamps: number[],
  series: { name: string; data: number[] }[],
  option: { yAxisName?: string; yAxisUnit?: string } = {}
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
 * 为 MCP 监控表格生成列定义，method、status_code 优先排在第一列
 */
const generateTableColumns = (
  data: Record<string, unknown>[]
): TableColumn[] => {
  if (!data || data.length === 0) return [];

  const firstRow = data[0];
  const keys = Object.keys(firstRow);

  const priorityFields = ["method", "status_code"];
  const sortedKeys = [
    ...priorityFields.filter(field => keys.includes(field)),
    ...keys.filter(key => !priorityFields.includes(key)).sort(),
  ];

  return sortedKeys.map(key => ({
    title: key,
    dataIndex: key,
    key: key,
    render: (text: unknown) => {
      if (typeof text === "number") {
        return text.toLocaleString("en-US");
      }
      if (text === null || text === undefined || text === "") {
        return "-";
      }
      return text as React.ReactNode;
    },
  }));
};

// 表格数据状态类型
interface TableDataState {
  methodDistribution: TableRowData[];
  gatewayStatus: TableRowData[];
  backendStatus: TableRowData[];
  requestDistribution: TableRowData[];
}

/**
 * MCP监控页面
 */
const McpMonitorForLogCollector: React.FC = () => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [timeRangeLabel, setTimeRangeLabel] = useState("");

  // 过滤选项状态
  const [filterOptions] = useState({
    clusterIds: [] as string[],
    routeNames: [] as string[],
    mcpToolNames: [] as string[],
    consumers: [] as string[],
    upstreamClusters: [] as string[],
  });

  // KPI数据状态
  const [kpiData, setKpiData] = useState({
    pv: "-",
    uv: "-",
    bytesReceived: "-",
    bytesSent: "-",
  });

  // 表格数据状态
  const [tableData, setTableData] = useState<TableDataState>({
    methodDistribution: [],
    gatewayStatus: [],
    backendStatus: [],
    requestDistribution: [],
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
      successRateChartInstance.current = echarts.init(
        successRateChartRef.current
      );
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
    const loadData = async () => {
      const [start, end] =
        rangePresets.find(p => p.label === "最近1周")?.value || [];
      form.setFieldsValue({
        timeRange: [start, end],
        interval: 15,
      });
      // 自动触发一次查询
      await handleQuery();
    };

    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 加载过滤选项
  const loadFilterOptions = async (
    startTime: string,
    endTime: string,
    interval: string
  ) => {
    try {
      console.log("loadFilterOptions", startTime, endTime, interval);
      // const options = await slsApi.fetchMcpFilterOptions(
      //   startTime,
      //   endTime,
      //   interval
      // );
      // setFilterOptions({
      //   clusterIds: options.cluster_id || [],
      //   routeNames: options.route_name || [],
      //   mcpToolNames: options.mcp_tool_name || [],
      //   consumers: options.consumer || [],
      //   upstreamClusters: options.upstream_cluster || [],
      // });
    } catch (error) {
      console.error("加载过滤选项失败:", error);
    }
  };

  // 监听时间范围变化
  const handleTimeRangeChange = (
    dates: [dayjs.Dayjs | null, dayjs.Dayjs | null] | null,
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    _dateStrings: [string, string]
  ) => {
    if (dates && dates.length === 2) {
      const [start, end] = dates;
      // 类型保护：确保 start 和 end 都不为 null
      if (start && end) {
        const interval = form.getFieldValue("interval") || 15;
        loadFilterOptions(
          formatDatetimeLocal(start),
          formatDatetimeLocal(end),
          interval
        );
      }
    }
  };

  // 查询KPI数据
  const queryKpiData = async (baseParams: {
    startTime: string;
    endTime: string;
    interval: string;
  }) => {
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
          bizType: "MCP_SERVER",
        },
      ]);

      // 类型断言：确保 data 符合 KpiData 类型
      const kpiData = data as KpiData;

      setKpiData({
        pv: formatNumber(kpiData.pv),
        uv: formatNumber(kpiData.uv),
        bytesReceived: formatNumber(kpiData.bytes_received),
        bytesSent: formatNumber(kpiData.bytes_sent),
      });
    } catch (error) {
      console.error("查询KPI数据失败:", error);
    }
  };

  // 查询图表数据：QPS、成功率、响应时间（参考 ModelDashboardForLogCollector）
  const queryChartData = async (baseParams: {
    startTime: string;
    endTime: string;
    interval: string;
  }) => {
    try {
      const intervalStr =
        typeof baseParams.interval === "number"
          ? `${baseParams.interval}s`
          : baseParams.interval;
      const { data } = await batchChartQuery([
        {
          timeRange: {
            start: baseParams.startTime,
            end: baseParams.endTime,
          },
          interval: intervalStr,
          scenario: "qps_total_simple",
          bizType: "MCP_SERVER",
        },
        {
          timeRange: {
            start: baseParams.startTime,
            end: baseParams.endTime,
          },
          interval: intervalStr,
          scenario: "success_rate",
          bizType: "MCP_SERVER",
        },
        {
          timeRange: {
            start: baseParams.startTime,
            end: baseParams.endTime,
          },
          interval: intervalStr,
          scenario: "rt_distribution",
          bizType: "MCP_SERVER",
        },
      ]);

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
            { notMerge: true }
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
          { notMerge: true }
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
            { notMerge: true }
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
    } catch (error) {
      console.error("查询图表数据失败:", error);
      successRateChartInstance.current?.setOption(emptyChartOption, {
        notMerge: true,
      });
      qpsChartInstance.current?.setOption(emptyChartOption, {
        notMerge: true,
      });
      rtChartInstance.current?.setOption(emptyChartOption, {
        notMerge: true,
      });
    }
  };

  // 查询表格数据
  const queryTableData = async (baseParams: {
    startTime: string;
    endTime: string;
    interval: string;
  }) => {
    try {
      const { data } = await batchTableQuery([
        {
          timeRange: {
            start: baseParams.startTime,
            end: baseParams.endTime,
          },
          tableType: "method_distribution",
          bizType: "MCP_SERVER",
        },
        {
          timeRange: {
            start: baseParams.startTime,
            end: baseParams.endTime,
          },
          tableType: "status_code_distribution",
          bizType: "MCP_SERVER",
        },
        {
          timeRange: {
            start: baseParams.startTime,
            end: baseParams.endTime,
          },
          tableType: "upstream_status_distribution",
          bizType: "MCP_SERVER",
        },
      ]);

      // 安全地访问嵌套数据，使用类型守卫处理联合类型（参考 ModelDashboardForLogCollector）
      const getDataArray = (queryData: unknown): TableRowData[] => {
        if (Array.isArray(queryData)) return queryData as TableRowData[];
        if (
          queryData &&
          typeof queryData === "object" &&
          "data" in queryData &&
          (queryData as { data: unknown }).data !== null
        ) {
          const dataObj = queryData as { data: unknown };
          return Array.isArray(dataObj.data)
            ? (dataObj.data as TableRowData[])
            : [];
        }
        return [];
      };

      const methodDistributionData = getDataArray(data?.query_0?.data);
      const gatewayStatusData = getDataArray(data?.query_1?.data);
      const backendStatusData = getDataArray(data?.query_2?.data);

      setTableData({
        methodDistribution: methodDistributionData,
        gatewayStatus: gatewayStatusData,
        backendStatus: backendStatusData,
        requestDistribution: [], // 当前仅 3 个 table 请求，无第 4 项
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
        route_name,
        mcp_tool_name,
        consumer,
        upstream_cluster,
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

      const baseParams: {
        startTime: string;
        endTime: string;
        interval: string;
        cluster_id: string;
        route_name: string;
        mcp_tool_name: string;
        consumer: string;
        upstream_cluster: string;
      } = {
        startTime: startTimeStr,
        endTime: endTimeStr,
        interval: interval || 15,
        cluster_id,
        route_name,
        mcp_tool_name,
        consumer,
        upstream_cluster,
      };

      // 并发查询所有数据
      queryKpiData(baseParams);
      queryChartData(baseParams);
      queryTableData(baseParams);
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
      bytesReceived: "-",
      bytesSent: "-",
    });
    setTableData({
      methodDistribution: [],
      gatewayStatus: [],
      backendStatus: [],
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
              <Form.Item name="upstream_cluster" label="服务">
                <Select
                  mode="tags"
                  placeholder="请选择"
                  style={{ width: "100%" }}
                  options={filterOptions.upstreamClusters.map(v => ({
                    label: v,
                    value: v,
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="route_name" label="MCP Server">
                <Select
                  mode="tags"
                  placeholder="请选择"
                  style={{ width: "100%" }}
                  options={filterOptions.routeNames.map(v => ({
                    label: v,
                    value: v,
                  }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="mcp_tool_name" label="MCP Tool">
                <Select
                  mode="tags"
                  placeholder="请选择"
                  style={{ width: "100%" }}
                  options={filterOptions.mcpToolNames.map(v => ({
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
        <Col span={6}>
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
        <Col span={6}>
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
        <Col span={6}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">网关入流量</div>
              {timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )}
            </div>
            <div className="text-center text-2xl font-medium">
              {kpiData.bytesReceived}
            </div>
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <div className="flex justify-between items-center mb-2">
              <div className="text-sm text-gray-500">网关出流量</div>
              {timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )}
            </div>
            <div className="text-center text-2xl font-medium">
              {kpiData.bytesSent}
            </div>
          </Card>
        </Col>
      </Row>

      {/* 时序图表 */}
      <Row gutter={16} className="mb-6">
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
      </Row>

      <Row gutter={16} className="mb-6">
        <Col span={24}>
          <Card
            title={<span>请求RT/ms</span>}
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

      {/* 统计表格 */}
      <Row gutter={16} className="mb-4">
        <Col span={12}>
          <Card
            title="Method分布"
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <Table
              dataSource={tableData.methodDistribution}
              columns={generateTableColumns(tableData.methodDistribution)}
              pagination={false}
              rowKey={(_, index) => index?.toString() || "0"}
              scroll={{ x: "max-content" }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card
            title="网关状态码分布"
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <Table
              dataSource={tableData.gatewayStatus}
              columns={generateTableColumns(tableData.gatewayStatus)}
              pagination={false}
              rowKey={(_, index) => index?.toString() || "0"}
              scroll={{ x: "max-content" }}
              size="small"
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} className="mb-4">
        <Col span={12}>
          <Card
            title="后端服务状态分布"
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <Table
              dataSource={tableData.backendStatus}
              columns={generateTableColumns(tableData.backendStatus)}
              pagination={false}
              rowKey={(_, index) => index?.toString() || "0"}
              scroll={{ x: "max-content" }}
              size="small"
            />
          </Card>
        </Col>
        <Col span={12}>
          <Card
            title="请求分布"
            extra={
              timeRangeLabel && (
                <span className="text-xs text-gray-400">{timeRangeLabel}</span>
              )
            }
          >
            <Table
              dataSource={tableData.requestDistribution}
              columns={generateTableColumns(tableData.requestDistribution)}
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

export default McpMonitorForLogCollector;
