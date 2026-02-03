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

package com.alibaba.himarket.dto.result.api;

import com.alibaba.himarket.support.enums.EndpointType;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tool Import Preview DTO
 *
 * <p>Used for previewing tools imported from various sources (MCP Server, Nacos, Swagger) before
 * creating an APIDefinition with APISpec.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolImportPreviewDTO {

    /** Tool name */
    private String name;

    /** Tool description */
    private String description;

    /** Endpoint type (MCP_TOOL, HTTP_ENDPOINT, etc.) */
    private EndpointType type;

    /** Tool configuration (JSON object) */
    private Map<String, Object> config;

    /** Display order (optional) */
    private Integer sortOrder;
}
