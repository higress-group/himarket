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

package com.alibaba.himarket.controller;

import com.alibaba.himarket.dto.converter.SlsResponseConverter;
import com.alibaba.himarket.dto.params.sls.GenericSlsQueryRequest;
import com.alibaba.himarket.dto.params.sls.GenericSlsQueryResponse;
import com.alibaba.himarket.dto.params.sls.ScenarioQueryResponse;
import com.alibaba.himarket.dto.params.sls.TimeSeriesChartResponse;
import com.alibaba.himarket.service.DBCollectorService;
import com.alibaba.himarket.service.gateway.factory.DBCollectorPresetSqlRegistry;
import com.alibaba.himarket.service.gateway.factory.SlsPresetSqlRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "DBCollector可观测", description = "基于数据库 access_logs 的可观测大盘查询能力、计量、日志检索")
@RestController
@RequestMapping("/db-collector")
@Slf4j
@RequiredArgsConstructor
public class DBCollectorController {

    private final DBCollectorService DBCollectorService;
    private final DBCollectorPresetSqlRegistry presetRegistry;

    @PostMapping("/statistics")
    @Operation(summary = "日志转指标聚合查询（DBCollector 存储）")
    public ScenarioQueryResponse DBCollectorScenarioQuery(
            @RequestBody @Validated GenericSlsQueryRequest request) {
        DBCollectorPresetSqlRegistry.Preset preset = presetRegistry.getPreset(request.getScenario());
        if (preset == null) {
            log.warn(
                    "DBCollector scenario not found, returning empty result. scenario: {}",
                    request.getScenario());
            return buildEmptyResponse(request.getScenario());
        }

        // 应用预置 SQL（MySQL/MariaDB 方言）
        request.setSql(preset.getSqlTemplate());

        try {
            GenericSlsQueryResponse response = DBCollectorService.executeQuery(request);
            Integer interval = request.getInterval() != null ? request.getInterval() : 60;

            switch (preset.getType()) {
                case LINE:
                    TimeSeriesChartResponse chart =
                            SlsResponseConverter.toTimeSeriesChart(
                                    response,
                                    preset.getTimeField() != null ? preset.getTimeField() : "time",
                                    preset.getValueField() != null
                                            ? preset.getValueField()
                                            : "count",
                                    interval);
                    return ScenarioQueryResponse.builder()
                            .type(SlsPresetSqlRegistry.DisplayType.LINE)
                            .timeSeries(
                                    chart != null
                                            ? chart
                                            : TimeSeriesChartResponse.builder()
                                                    .dataPoints(Collections.emptyList())
                                                    .build())
                            .build();
                case CARD:
                    Map<String, Object> statistics = SlsResponseConverter.toStatistics(response);
                    List<ScenarioQueryResponse.StatisticItem> items = new java.util.ArrayList<>();
                    for (Map.Entry<String, Object> e : statistics.entrySet()) {
                        items.add(
                                ScenarioQueryResponse.StatisticItem.builder()
                                        .key(e.getKey())
                                        .value(String.valueOf(e.getValue()))
                                        .build());
                    }
                    return ScenarioQueryResponse.builder()
                            .type(SlsPresetSqlRegistry.DisplayType.CARD)
                            .stats(items)
                            .build();
                case TABLE:
                    List<Map<String, String>> table =
                            response.getAggregations() != null
                                    ? response.getAggregations()
                                    : response.getLogs();
                    return ScenarioQueryResponse.builder()
                            .type(SlsPresetSqlRegistry.DisplayType.TABLE)
                            .table(table != null ? table : Collections.emptyList())
                            .build();
                default:
                    return buildEmptyResponse(request.getScenario());
            }
        } catch (Exception e) {
            log.warn(
                    "DBCollector query failed, returning empty result. scenario: {}, err: {}",
                    request.getScenario(),
                    e.getMessage(),
                    e);
            return buildEmptyResponse(request.getScenario());
        }
    }

    private ScenarioQueryResponse buildEmptyResponse(String scenario) {
        DBCollectorPresetSqlRegistry.Preset preset = presetRegistry.getPreset(scenario);
        if (preset == null) {
            return ScenarioQueryResponse.builder()
                    .type(SlsPresetSqlRegistry.DisplayType.CARD)
                    .stats(Collections.emptyList())
                    .build();
        }

        switch (preset.getType()) {
            case LINE:
                return ScenarioQueryResponse.builder()
                        .type(SlsPresetSqlRegistry.DisplayType.LINE)
                        .timeSeries(
                                TimeSeriesChartResponse.builder()
                                        .dataPoints(Collections.emptyList())
                                        .build())
                        .build();
            case CARD:
                return ScenarioQueryResponse.builder()
                        .type(SlsPresetSqlRegistry.DisplayType.CARD)
                        .stats(Collections.emptyList())
                        .build();
            case TABLE:
                return ScenarioQueryResponse.builder()
                        .type(SlsPresetSqlRegistry.DisplayType.TABLE)
                        .table(Collections.emptyList())
                        .build();
            default:
                return ScenarioQueryResponse.builder()
                        .type(SlsPresetSqlRegistry.DisplayType.CARD)
                        .stats(Collections.emptyList())
                        .build();
        }
    }
}
