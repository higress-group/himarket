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

package com.alibaba.himarket.support.consumer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Authentication config for MCP sandbox scenarios.
 *
 * <p>The primary consumer credential is stored here and can be passed to sandbox-hosted MCP servers
 * for authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpAuthConfig {

    /**
     * Authentication type, such as none or bearer.
     */
    private String authType;

    /**
     * Authentication source, such as Default, Header, or QueryString.
     */
    private String source;

    /**
     * Header or parameter name, such as Authorization.
     */
    private String headerName;

    /**
     * API key value.
     */
    private String apiKey;

    /**
     * Consumer ID.
     */
    private String consumerId;
}
