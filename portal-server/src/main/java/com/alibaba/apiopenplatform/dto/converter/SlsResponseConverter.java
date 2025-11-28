package com.alibaba.apiopenplatform.dto.converter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.apiopenplatform.dto.params.sls.GenericSlsQueryResponse;
import com.alibaba.apiopenplatform.dto.params.sls.TimeSeriesChartResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * SLS查询响应转换工具类
 * 用于将通用查询结果转换为前端所需的各种格式
 *
 */
@Slf4j
public class SlsResponseConverter {

    /**
     * 转换为时序图表数据
     *
     * @param response    通用查询响应
     * @param timeField   时间字段名（如: time, __time__）
     * @param valueField  数值字段名（如: count, total）
     * @param interval    时间间隔（秒）
     * @return 时序图表响应
     */
    public static TimeSeriesChartResponse toTimeSeriesChart(
            GenericSlsQueryResponse response,
            String timeField,
            String valueField,
            Integer interval) {

        if (response == null || !response.getSuccess()) {
            log.warn("Cannot convert failed query response to time series chart");
            return null;
        }

        List<Map<String, String>> data = response.getAggregations() != null
                ? response.getAggregations()
                : response.getLogs();

        if (data == null || data.isEmpty()) {
            return TimeSeriesChartResponse.builder()
                    .dataPoints(new ArrayList<>())
                    .metadata(TimeSeriesChartResponse.ChartMetadata.builder()
                            .totalCount(0L)
                            .interval(interval)
                            .build())
                    .build();
        }

        List<TimeSeriesChartResponse.TimeSeriesDataPoint> dataPoints = new ArrayList<>();

        for (Map<String, String> record : data) {
            String time = record.get(timeField);
            String value = record.get(valueField);

            if (!StringUtils.hasText(time) || !StringUtils.hasText(value)) {
                continue;
            }

            try {
                TimeSeriesChartResponse.TimeSeriesDataPoint point = TimeSeriesChartResponse.TimeSeriesDataPoint.builder()
                        .timestamp(time)
                        .value(Double.parseDouble(value))
                        .build();

                // 添加其他维度字段
                Map<String, String> dimensions = new HashMap<>();
                for (Map.Entry<String, String> entry : record.entrySet()) {
                    String key = entry.getKey();
                    if (!key.equals(timeField) && !key.equals(valueField)) {
                        dimensions.put(key, entry.getValue());
                    }
                }
                if (!dimensions.isEmpty()) {
                    point.setDimensions(dimensions);
                }

                dataPoints.add(point);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse value as number: {}", value);
            }
        }

        TimeSeriesChartResponse.ChartMetadata metadata = TimeSeriesChartResponse.ChartMetadata.builder()
                .totalCount((long) dataPoints.size())
                .interval(interval)
                .xAxisLabel("Time")
                .yAxisLabel(valueField)
                .build();

        return TimeSeriesChartResponse.builder()
                .dataPoints(dataPoints)
                .metadata(metadata)
                .build();
    }

    /**
     * 转换为时序图表数据（使用默认字段名）
     *
     * @param response 通用查询响应
     * @param interval 时间间隔（秒）
     * @return 时序图表响应
     */
    public static TimeSeriesChartResponse toTimeSeriesChart(
            GenericSlsQueryResponse response,
            Integer interval) {
        return toTimeSeriesChart(response, "time", "count", interval);
    }

    /**
     * 转换为通用日志列表（直接返回原始数据）
     *
     * @param response 通用查询响应
     * @return 日志列表
     */
    public static List<Map<String, String>> toLogList(GenericSlsQueryResponse response) {
        if (response == null || !response.getSuccess()) {
            return new ArrayList<>();
        }

        return response.getLogs() != null ? response.getLogs() : new ArrayList<>();
    }

    /**
     * 转换为统计数据（聚合结果）
     *
     * @param response 通用查询响应
     * @return 统计数据
     */
    public static Map<String, Object> toStatistics(GenericSlsQueryResponse response) {
        Map<String, Object> statistics = new HashMap<>();

        if (response == null || !response.getSuccess()) {
            return statistics;
        }

        statistics.put("count", response.getCount());
        statistics.put("processStatus", response.getProcessStatus());

        if (response.getAggregations() != null && !response.getAggregations().isEmpty()) {
            // 如果只有一条聚合结果，直接展开字段
            if (response.getAggregations().size() == 1) {
                Map<String, String> firstAgg = response.getAggregations().get(0);
                for (Map.Entry<String, String> entry : firstAgg.entrySet()) {
                    try {
                        // 尝试转换为数字
                        statistics.put(entry.getKey(), Double.parseDouble(entry.getValue()));
                    } catch (NumberFormatException e) {
                        statistics.put(entry.getKey(), entry.getValue());
                    }
                }
            } else {
                statistics.put("aggregations", response.getAggregations());
            }
        }

        return statistics;
    }

    /**
     * 转换为饼图数据
     *
     * @param response   通用查询响应
     * @param labelField 标签字段名
     * @param valueField 数值字段名
     * @return 饼图数据
     */
    public static List<Map<String, Object>> toPieChart(
            GenericSlsQueryResponse response,
            String labelField,
            String valueField) {

        List<Map<String, Object>> pieData = new ArrayList<>();

        if (response == null || !response.getSuccess()) {
            return pieData;
        }

        List<Map<String, String>> data = response.getAggregations() != null
                ? response.getAggregations()
                : response.getLogs();

        if (data == null) {
            return pieData;
        }

        for (Map<String, String> record : data) {
            String label = record.get(labelField);
            String value = record.get(valueField);

            if (!StringUtils.hasText(label) || !StringUtils.hasText(value)) {
                continue;
            }

            try {
                Map<String, Object> item = new HashMap<>();
                item.put("name", label);
                item.put("value", Double.parseDouble(value));
                pieData.add(item);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse value as number: {}", value);
            }
        }

        return pieData;
    }
}
