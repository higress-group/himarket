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

/**
 * Generic SLS log query request.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GenericSlsQueryRequest {

    /**
     * User ID populated by the controller when STS authentication is enabled.
     */
    @JsonIgnore private String userId;

    /**
     * Start time as a Unix timestamp in seconds.
     */
    private Integer fromTime;

    /**
     * End time as a Unix timestamp in seconds.
     */
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
     * Model API filter values, combined with OR semantics.
     */
    private String[] api;

    /**
     * Model filter values, combined with OR semantics.
     */
    private String[] model;

    /**
     * Consumer filter values, combined with OR semantics.
     */
    private String[] consumer;

    /**
     * Instance ID filter values, mapped to instanceId and combined with OR semantics.
     */
    private String[] clusterId;

    /**
     * Route name filter values, combined with OR semantics.
     */
    private String[] route;

    /**
     * Service name filter values, matched against upstream_cluster with contains semantics.
     */
    private String[] service;

    /**
     * Route name filter used by MCP monitoring requests.
     *
     * <p>Historically, the model dashboard used {@link #route} while the MCP dashboard passed
     * route_name. Both are mapped to access_logs.route_name or the SLS route_name field.
     */
    private String[] routeName;

    /**
     * Upstream service filter used by MCP monitoring requests.
     */
    private String[] upstreamCluster;

    /**
     * MCP tool name filter used by MCP monitoring requests.
     */
    private String[] mcpToolName;

    /**
     * Scenario name, such as pv, uv, or qps_total.
     */
    private String scenario;

    /**
     * SQL query text.
     */
    private String sql;

    /**
     * Page size, defaulting to 1000 with a maximum of 5000.
     */
    @Min(1)
    @Max(5000)
    private Integer pageSize;

    /**
     * Aggregation interval in seconds for time-series charts.
     */
    private Integer interval;

    /**
     * Business type used to distinguish product families, such as MCP_SERVER or MODEL_API.
     */
    private String bizType;
}
