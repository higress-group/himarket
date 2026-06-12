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

package com.alibaba.himarket.dto.result.mcp;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * "我的MCP" 列表项：endpoint + meta 合并展示。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyEndpointResult {

    // ---- endpoint 字段 ----
    private String endpointId;
    private String mcpServerId;
    private String endpointUrl;
    private String hostingType;
    private String protocol;
    private String hostingInstanceId;
    private String subscribeParams;
    private String status;
    private LocalDateTime endpointCreatedAt;

    // ---- meta 字段（展示用） ----
    private String productId;
    private String displayName;
    private String mcpName;
    private String description;
    private String icon;
    private String tags;
    private String protocolType;
    private String origin;
    private String toolsConfig;
}
