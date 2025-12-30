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

package com.alibaba.himarket.dto.params.sls;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 时序图表数据响应 用于前端时序图表渲染 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeSeriesChartResponse {

    /** 时序数据点列表 */
    private List<TimeSeriesDataPoint> dataPoints;

    /** 图表元数据 */
    private ChartMetadata metadata;

    /** 时序数据点 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesDataPoint {
        /** 时间戳 */
        private String timestamp;

        /** 数值 */
        private Double value;

        /** 分组维度（可选） */
        private Map<String, String> dimensions;
    }

    /** 图表元数据 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartMetadata {
        /** 图表标题 */
        private String title;

        /** X轴标签 */
        private String xAxisLabel;

        /** Y轴标签 */
        private String yAxisLabel;

        /** 数据总数 */
        private Long totalCount;

        /** 时间间隔（秒） */
        private Integer interval;
    }
}
