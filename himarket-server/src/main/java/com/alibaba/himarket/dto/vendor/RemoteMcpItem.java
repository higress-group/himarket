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

package com.alibaba.himarket.dto.vendor;

import com.alibaba.himarket.support.api.spec.McpConnection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unified adapter output for one MCP server returned by a vendor API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoteMcpItem {

    /**
     * Vendor-side unique ID.
     */
    private String remoteId;

    /**
     * Converted MCP name that follows platform naming rules.
     */
    private String mcpName;

    /**
     * Display name.
     */
    private String displayName;

    /**
     * Description.
     */
    private String description;

    /**
     * Protocol type, such as sse, streamable-http, or stdio.
     */
    private String protocolType;

    /**
     * Connection configuration in JSON format.
     */
    private String connectionConfig;

    /**
     * Platform-standard MCP connection converted from connectionConfig by the vendor adapter.
     */
    private McpConnection connection;

    /**
     * Tags in JSON array format.
     */
    private String tags;

    /**
     * Icon in JSON format.
     */
    private String icon;

    /**
     * Source repository URL.
     */
    private String repoUrl;

    /**
     * Extra parameter definitions in JSON format, such as env_schema or configSchema.
     */
    private String extraParams;

    /**
     * Service introduction in Markdown format.
     */
    private String serviceIntro;
}
