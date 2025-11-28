package com.alibaba.apiopenplatform.controller;

import java.util.List;
import java.util.Map;

import com.alibaba.apiopenplatform.dto.converter.SlsResponseConverter;
import com.alibaba.apiopenplatform.dto.params.sls.GenericSlsQueryRequest;
import com.alibaba.apiopenplatform.dto.params.sls.GenericSlsQueryResponse;
import com.alibaba.apiopenplatform.dto.params.sls.ScenarioQueryResponse;
import com.alibaba.apiopenplatform.dto.params.sls.TimeSeriesChartResponse;
import com.alibaba.apiopenplatform.service.SlsLogService;
import com.alibaba.apiopenplatform.service.gateway.factory.SlsPresetSqlRegistry;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SLS可观测", description = "提供基于日志的可观测大盘查询能力、计量、日志检索")
@RestController
@RequestMapping("/sls")
@Slf4j
@RequiredArgsConstructor
public class SlsController {

    private final SlsLogService slsLogService;

    private final SlsPresetSqlRegistry presetRegistry;


    @PostMapping("/statistics")
    @Operation(summary = "日志转指标聚合查询")
    public ScenarioQueryResponse slsScenarioQuery(@RequestBody @Validated GenericSlsQueryRequest request) {
        SlsPresetSqlRegistry.Preset preset = presetRegistry.getPreset(request.getScenario());
        if (preset == null) {
            log.warn("Scenario not found, returning empty result. scenario: {}", request.getScenario());
            return null;
        }
        // 应用预置SQL
        request.setSql(preset.getSql());
        GenericSlsQueryResponse response = slsLogService.executeQuery(request);
        Integer interval = request.getInterval() != null ? request.getInterval() : 60;
        ScenarioQueryResponse resp;
        switch (preset.getType()) {
            case LINE:
                TimeSeriesChartResponse chart = SlsResponseConverter.toTimeSeriesChart(
                    response,
                    preset.getTimeField() != null ? preset.getTimeField() : "time",
                    preset.getValueField() != null ? preset.getValueField() : "count",
                    interval);
                resp = ScenarioQueryResponse.builder()
                    .type(SlsPresetSqlRegistry.DisplayType.LINE)
                    .timeSeries(chart)
                    .build();
                return resp;
            case CARD:
                Map<String, Object> statistics = SlsResponseConverter.toStatistics(response);
                List<ScenarioQueryResponse.StatisticItem> items = new java.util.ArrayList<>();
                for (Map.Entry<String, Object> e : statistics.entrySet()) {
                    items.add(ScenarioQueryResponse.StatisticItem.builder()
                        .key(e.getKey())
                        .value(String.valueOf(e.getValue()))
                        .build());
                }
                resp = ScenarioQueryResponse.builder()
                    .type(SlsPresetSqlRegistry.DisplayType.CARD)
                    .stats(items)
                    .build();
                return resp;
            case TABLE:
                List<Map<String, String>> table = response.getAggregations() != null ? response.getAggregations() : response.getLogs();
                resp = ScenarioQueryResponse.builder()
                    .type(SlsPresetSqlRegistry.DisplayType.TABLE)
                    .table(table)
                    .build();
                return resp;
            default:
                return null;
        }
    }

}
