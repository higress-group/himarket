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

/** 通用SLS查询响应 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericSlsQueryResponse {

    /** 查询是否成功 */
    private Boolean success;

    /** 查询进度（Complete/InComplete） */
    private String processStatus;

    /** 命中日志条数 */
    private Long count;

    /** 查询到的日志列表 */
    private List<Map<String, String>> logs;

    /** 聚合结果（如果是统计查询） */
    private List<Map<String, String>> aggregations;

    /** SQL查询语句（用于调试） */
    private String sql;

    /** 查询耗时（毫秒） */
    private Long elapsedMillis;

    /** 错误信息（如果查询失败） */
    private String errorMessage;

    /** 额外信息 */
    private Map<String, Object> extraInfo;
}
