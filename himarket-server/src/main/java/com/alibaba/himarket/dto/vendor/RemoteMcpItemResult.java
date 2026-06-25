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

import lombok.Data;

/**
 * Remote MCP item returned to the frontend with a platform existence marker.
 */
@Data
public class RemoteMcpItemResult {

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
     * Whether the platform already contains an MCP server with the same name.
     */
    private boolean existsInPlatform;
}
