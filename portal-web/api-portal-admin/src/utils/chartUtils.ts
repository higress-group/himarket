import * as echarts from 'echarts';
import { DataPoint } from '../types/sls';

/**
 * ECharts通用配置选项
 */
export interface ChartBaseOptions {
  title?: string;
  xAxisLabel?: string;
  yAxisLabel?: string;
  isPercentage?: boolean;
}

/**
 * 生成基础的折线图配置
 * @param dataPoints 数据点数组
 * @param options 配置选项
 * @returns ECharts配置对象
 */
export function generateLineChartOption(
  dataPoints: DataPoint[],
  options: ChartBaseOptions & { seriesName?: string } = {}
): echarts.EChartsOption {
  const { title, xAxisLabel, yAxisLabel, isPercentage = false, seriesName } = options;

  // 提取时间戳和值
  const timestamps = dataPoints.map(p => p.timestamp);
  const values = dataPoints.map(p => {
    const val = typeof p.value === 'string' ? parseFloat(p.value) : p.value;
    const numVal = isNaN(val) ? 0 : val;
    // 如果是百分比，将0-1的小数转换为0-100的百分比
    return isPercentage ? numVal * 100 : numVal;
  });

  return {
    title: title ? { text: title, left: 'center' } : undefined,
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        if (!Array.isArray(params) || params.length === 0) return '';
        const param = params[0];
        const value = isPercentage
          ? `${param.value.toFixed(2)}%`
          : param.value.toLocaleString();
        return `${param.axisValue}<br/>${param.marker}${param.seriesName}: ${value}`;
      }
    },
    legend: {
      top: 'top',
      left: 'center'
    },
    grid: {
      left: 40,
      right: 56,
      top: 40,
      bottom: 32,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: timestamps,
      name: xAxisLabel,
      axisLabel: {
        rotate: 45,
        formatter: (value: string) => {
          // 简化时间显示：只显示时分秒
          const parts = value.split(' ');
          return parts.length > 1 ? parts[1] : value;
        }
      }
    },
    yAxis: {
      type: 'value',
      name: yAxisLabel,
      axisLabel: {
        formatter: isPercentage ? '{value}%' : '{value}'
      }
    },
    series: [
      {
        name: seriesName || '数值',
        type: 'line' as const,
        smooth: true,
        showSymbol: false,
        areaStyle: {},
        data: values
      }
    ]
  };
}

/**
 * 生成多序列折线图配置
 * @param seriesData 多个序列的数据
 * @param options 配置选项
 * @returns ECharts配置对象
 */
export function generateMultiLineChartOption(
  seriesData: { name: string; dataPoints: DataPoint[] }[],
  options: ChartBaseOptions = {}
): echarts.EChartsOption {
  const { title, xAxisLabel, yAxisLabel, isPercentage = false } = options;

  if (seriesData.length === 0) {
    return {};
  }

  // 使用第一个序列的时间戳作为X轴
  const timestamps = seriesData[0]?.dataPoints.map(p => p.timestamp) || [];

  const series = seriesData.map(s => {
    const values = s.dataPoints.map(p => {
      const val = typeof p.value === 'string' ? parseFloat(p.value) : p.value;
      const numVal = isNaN(val) ? 0 : val;
      // 如果是百分比，将0-1的小数转换为0-100的百分比
      return isPercentage ? numVal * 100 : numVal;
    });

    return {
      name: s.name,
      type: 'line' as const,
      smooth: true,
      showSymbol: false,
      areaStyle: {},
      data: values
    };
  });

  return {
    title: title ? { text: title, left: 'center' } : undefined,
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        if (!Array.isArray(params) || params.length === 0) return '';
        let result = `${params[0].axisValue}<br/>`;
        params.forEach((param: any) => {
          const value = isPercentage
            ? `${param.value.toFixed(2)}%`
            : param.value.toLocaleString();
          result += `${param.marker}${param.seriesName}: ${value}<br/>`;
        });
        return result;
      }
    },
    legend: {
      top: 'top',
      left: 'center'
    },
    grid: {
      left: 40,
      right: 56,
      top: 40,
      bottom: 32,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: timestamps,
      name: xAxisLabel,
      axisLabel: {
        rotate: 45,
        formatter: (value: string) => {
          const parts = value.split(' ');
          return parts.length > 1 ? parts[1] : value;
        }
      }
    },
    yAxis: {
      type: 'value',
      name: yAxisLabel,
      axisLabel: {
        formatter: isPercentage ? '{value}%' : '{value}'
      }
    },
    series: series as any
  };
}

/**
 * 生成空状态图表配置
 * @param message 提示消息
 * @returns ECharts配置对象
 */
export function generateEmptyChartOption(message: string = '暂无数据'): echarts.EChartsOption {
  return {
    title: {
      text: message,
      left: 'center',
      top: 'middle',
      textStyle: {
        color: '#999',
        fontSize: 14
      }
    },
    xAxis: { show: false },
    yAxis: { show: false }
  };
}

/**
 * 从表格数据动态生成列定义
 * @param data 表格数据
 * @returns Ant Design Table的列定义
 */
export function generateTableColumns(data: Record<string, any>[]): any[] {
  if (!data || data.length === 0) return [];

  const firstRow = data[0];
  const keys = Object.keys(firstRow);

  return keys.map(key => ({
    title: key,
    dataIndex: key,
    key: key,
    render: (text: any) => {
      // 如果是数字，格式化显示
      if (typeof text === 'number') {
        return text.toLocaleString('en-US');
      }
      return text;
    }
  }));
}
