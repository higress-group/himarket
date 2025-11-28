package com.alibaba.apiopenplatform.dto.params.sls;

import java.util.List;
import java.util.Map;

import com.alibaba.apiopenplatform.service.gateway.factory.SlsPresetSqlRegistry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 场景化查询统一响应封装
 * 保留丰富格式：
 * - LINE：使用已定义的 TimeSeriesChartResponse
 * - CARD：使用轻量 POJO 列表而非 Map，提高类型明确性
 * - TABLE：保留 List<Map<String, String>> 以支持动态列
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioQueryResponse {

    /** 展示类型 */
    private SlsPresetSqlRegistry.DisplayType type;

    /** 线图数据（当 type=LINE 时） */
    private TimeSeriesChartResponse timeSeries;

    /** 统计卡片数据（当 type=CARD 时） */
    private List<StatisticItem> stats;

    /** 表格数据（当 type=TABLE 时） */
    private List<Map<String, String>> table;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticItem {
        private String key;
        private String value;
    }
}
