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

/**
 * Common SLS SQL query request.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SlsCommonQueryRequest {

    /**
     * User ID populated by the controller when STS authentication is enabled.
     */
    @JsonIgnore private String userId;

    /**
     * Start time as a Unix timestamp in seconds.
     */
    @NotNull(message = "From time cannot be null")
    private Integer fromTime;

    /**
     * End time as a Unix timestamp in seconds.
     */
    @NotNull(message = "To time cannot be null")
    private Integer toTime;

    /**
     * Optional start time string in yyyy-MM-dd HH:mm:ss format.
     */
    private String startTime;

    /**
     * Optional end time string in yyyy-MM-dd HH:mm:ss format.
     */
    private String endTime;

    /**
     * SQL query text.
     */
    @NotNull(message = "SQL cannot be null")
    private String sql;

    /**
     * Page size, defaulting to 1000 with a maximum of 5000.
     */
    @Min(1)
    @Max(5000)
    private Integer pageSize;
}
