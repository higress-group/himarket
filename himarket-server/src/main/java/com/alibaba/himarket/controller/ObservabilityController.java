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

import com.alibaba.himarket.config.ObservabilityConfig;
import com.alibaba.himarket.dto.params.sls.GenericSlsQueryRequest;
import com.alibaba.himarket.dto.params.sls.ScenarioQueryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "可观测统一入口", description = "根据配置自动路由到 SLS 或 DB 数据源")
@RestController
@RequestMapping("/observability")
@RequiredArgsConstructor
public class ObservabilityController {

    private final ObservabilityConfig observabilityConfig;
    private final SlsController slsController;
    private final DBCollectorController dbCollectorController;

    @PostMapping("/statistics")
    @Operation(summary = "统一日志指标聚合查询（自动路由 SLS / DB）")
    public ScenarioQueryResponse query(@RequestBody @Validated GenericSlsQueryRequest request) {
        if (observabilityConfig.getLogSource() == ObservabilityConfig.LogSource.SLS) {
            return slsController.slsScenarioQuery(request);
        }
        return dbCollectorController.DBCollectorScenarioQuery(request);
    }
}
