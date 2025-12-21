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
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** SLS通用SQL查询请求 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlsCommonQueryRequest {

    /** 用户ID（仅当配置为STS认证时需要） 此字段由Controller层从上下文获取并设置，前端不应传入 */
    @JsonIgnore private String userId;

    /** 开始时间（Unix时间戳，秒） */
    @NotNull(message = "FromTime cannot be null")
    private Integer fromTime;

    /** 结束时间（Unix时间戳，秒） */
    @NotNull(message = "ToTime cannot be null")
    private Integer toTime;

    /** 开始时间（字符串：yyyy-MM-dd HH:mm:ss，可选） */
    private String startTime;

    /** 结束时间（字符串：yyyy-MM-dd HH:mm:ss，可选） */
    private String endTime;

    /** SQL查询语句 */
    @NotNull(message = "SQL cannot be null")
    private String sql;

    /** 单页返回条数（默认1000，上限5000） */
    @Min(1)
    @Max(5000)
    private Integer pageSize;
}
