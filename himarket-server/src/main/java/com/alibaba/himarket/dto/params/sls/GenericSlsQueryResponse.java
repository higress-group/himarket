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

/**
 * Generic SLS query response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericSlsQueryResponse {

    /**
     * Whether the query succeeded.
     */
    private Boolean success;

    /**
     * Query process status, such as Complete or InComplete.
     */
    private String processStatus;

    /**
     * Matched log count.
     */
    private Long count;

    /**
     * Query result logs.
     */
    private List<Map<String, String>> logs;

    /**
     * Aggregation rows for statistics queries.
     */
    private List<Map<String, String>> aggregations;

    /**
     * SQL query text for debugging.
     */
    private String sql;

    /**
     * Query elapsed time in milliseconds.
     */
    private Long elapsedMillis;

    /**
     * Error message when the query fails.
     */
    private String errorMessage;

    /**
     * Additional response metadata.
     */
    private Map<String, Object> extraInfo;
}
