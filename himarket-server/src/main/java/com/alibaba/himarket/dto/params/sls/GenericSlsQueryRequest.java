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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 通用SLS日志查询请求 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericSlsQueryRequest {

    /** 用户ID（仅当配置为STS认证时需要） 此字段由Controller层从上下文获取并设置，前端不应传入 */
    @JsonIgnore private String userId;

    /** 开始时间（Unix时间戳，秒） */
    private Integer fromTime;

    /** 结束时间（Unix时间戳，秒） */
    private Integer toTime;

    /** 开始时间（字符串：yyyy-MM-dd HH:mm:ss，可选） */
    private String startTime;

    /** 结束时间（字符串：yyyy-MM-dd HH:mm:ss，可选） */
    private String endTime;

    /** Model API（过滤参数，支持多个值OR查询） */
    private String[] api;

    /** Model（过滤参数，支持多个值OR查询） */
    private String[] model;

    /** 消费者（过滤参数，支持多个值OR查询） */
    private String[] consumer;

    /** 实例ID，对应 instanceId（过滤参数，支持多个值OR查询） */
    private String[] clusterId;

    /** 路由名称（过滤参数，支持多个值OR查询） */
    private String[] route;

    /** 服务名（过滤参数，对 upstream_cluster 进行包含匹配，支持多个值OR查询） */
    private String[] service;

    /** 场景名（如: pv, uv, qps_total 等） */
    private String scenario;

    /** SQL查询语句 */
    private String sql;

    /** 单页返回条数（默认1000，上镐5000） */
    @Min(1)
    @Max(5000)
    private Integer pageSize;

    /** 时间间隔（秒，用于时序图表聚合） */
    private Integer interval;
}
