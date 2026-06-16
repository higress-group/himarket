/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.dto.converter;

import com.alibaba.himarket.dto.params.sls.GenericSlsQueryResponse;
import com.alibaba.himarket.dto.params.sls.TimeSeriesChartResponse;
import com.alibaba.himarket.support.common.Strings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts generic SLS query responses into frontend-friendly shapes.
 */
@Slf4j
public class SlsResponseConverter {

    /**
     * Converts a query response to time-series chart data.
     *
     * @param response generic query response
     * @param timeField time field name, such as time or __time__
     * @param valueField value field name, such as count or total
     * @param interval time interval in seconds
     * @return time-series chart response
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

        List<Map<String, String>> data =
                response.getAggregations() != null
                        ? response.getAggregations()
                        : response.getLogs();

        if (data == null || data.isEmpty()) {
            return TimeSeriesChartResponse.builder()
                    .dataPoints(new ArrayList<>())
                    .metadata(
                            TimeSeriesChartResponse.ChartMetadata.builder()
                                    .totalCount(0L)
                                    .interval(interval)
                                    .build())
                    .build();
        }

        List<TimeSeriesChartResponse.TimeSeriesDataPoint> dataPoints = new ArrayList<>();

        for (Map<String, String> record : data) {
            String time = record.get(timeField);
            String value = record.get(valueField);

            if (Strings.isBlank(time) || Strings.isBlank(value)) {
                continue;
            }

            try {
                TimeSeriesChartResponse.TimeSeriesDataPoint point =
                        TimeSeriesChartResponse.TimeSeriesDataPoint.builder()
                                .timestamp(time)
                                .value(Double.parseDouble(value))
                                .build();

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
                log.warn(
                        "Failed to parse value as number, value={}, errorMessage={}",
                        value,
                        e.getMessage());
            }
        }

        TimeSeriesChartResponse.ChartMetadata metadata =
                TimeSeriesChartResponse.ChartMetadata.builder()
                        .totalCount((long) dataPoints.size())
                        .interval(interval)
                        .xAxisLabel("Time")
                        .yAxisLabel(valueField)
                        .build();

        return TimeSeriesChartResponse.builder().dataPoints(dataPoints).metadata(metadata).build();
    }

    /**
     * Converts a query response to time-series chart data with default field names.
     *
     * @param response generic query response
     * @param interval time interval in seconds
     * @return time-series chart response
     */
    public static TimeSeriesChartResponse toTimeSeriesChart(
            GenericSlsQueryResponse response, Integer interval) {
        return toTimeSeriesChart(response, "time", "count", interval);
    }

    /**
     * Converts a query response to a raw log list.
     *
     * @param response generic query response
     * @return log rows
     */
    public static List<Map<String, String>> toLogList(GenericSlsQueryResponse response) {
        if (response == null || !response.getSuccess()) {
            return new ArrayList<>();
        }

        return response.getLogs() != null ? response.getLogs() : new ArrayList<>();
    }

    /**
     * Converts a query response to statistics data.
     *
     * @param response generic query response
     * @return statistics data
     */
    public static Map<String, Object> toStatistics(GenericSlsQueryResponse response) {
        Map<String, Object> statistics = new HashMap<>();

        if (response == null || !response.getSuccess()) {
            return statistics;
        }

        statistics.put("count", response.getCount());
        statistics.put("processStatus", response.getProcessStatus());

        if (response.getAggregations() != null && !response.getAggregations().isEmpty()) {
            if (response.getAggregations().size() == 1) {
                Map<String, String> firstAgg = response.getAggregations().get(0);
                for (Map.Entry<String, String> entry : firstAgg.entrySet()) {
                    try {
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
     * Converts a query response to pie chart data.
     *
     * @param response generic query response
     * @param labelField label field name
     * @param valueField value field name
     * @return pie chart rows
     */
    public static List<Map<String, Object>> toPieChart(
            GenericSlsQueryResponse response, String labelField, String valueField) {

        List<Map<String, Object>> pieData = new ArrayList<>();

        if (response == null || !response.getSuccess()) {
            return pieData;
        }

        List<Map<String, String>> data =
                response.getAggregations() != null
                        ? response.getAggregations()
                        : response.getLogs();

        if (data == null) {
            return pieData;
        }

        for (Map<String, String> record : data) {
            String label = record.get(labelField);
            String value = record.get(valueField);

            if (Strings.isBlank(label) || Strings.isBlank(value)) {
                continue;
            }

            try {
                Map<String, Object> item = new HashMap<>();
                item.put("name", label);
                item.put("value", Double.parseDouble(value));
                pieData.add(item);
            } catch (NumberFormatException e) {
                log.warn(
                        "Failed to parse value as number, value={}, errorMessage={}",
                        value,
                        e.getMessage());
            }
        }

        return pieData;
    }
}
