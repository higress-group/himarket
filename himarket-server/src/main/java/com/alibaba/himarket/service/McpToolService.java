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

package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.mcp.McpConfigResult;
import com.alibaba.himarket.dto.result.mcp.McpToolListResult;

public interface McpToolService {

    /**
     * Lists MCP tools for a product and the current developer context.
     *
     * @param productId the product ID
     * @param mcpConfig MCP runtime configuration
     * @return MCP tool list
     */
    McpToolListResult listMcpTools(String productId, McpConfigResult mcpConfig);

    /**
     * Fetches and converts MCP tools to the stored tools config JSON.
     *
     * @param mcpConfig MCP runtime configuration
     * @param credential runtime credential for the MCP server
     * @return tools config JSON, or null when tools cannot be fetched
     */
    String fetchToolsConfig(McpConfigResult mcpConfig, CredentialContext credential);
}
