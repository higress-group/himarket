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

import com.alibaba.himarket.dto.params.mcp.RegisterMcpParam;
import com.alibaba.himarket.dto.params.mcp.SaveMcpEndpointParam;
import com.alibaba.himarket.dto.params.mcp.SaveMcpMetaParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.mcp.McpEndpointResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaResult;
import com.alibaba.himarket.dto.result.mcp.MyEndpointResult;
import com.alibaba.himarket.support.chat.mcp.McpTransportConfig;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface McpServerService {

    /**
     * Saves MCP metadata, creating or updating it as needed.
     */
    McpMetaResult saveMeta(SaveMcpMetaParam param);

    /**
     * Registers an MCP Server and creates the related Product, MCP metadata, and ProductRef.
     */
    McpMetaResult registerMcp(RegisterMcpParam param);

    /**
     * Gets MCP metadata by mcpServerId.
     */
    McpMetaResult getMeta(String mcpServerId);

    /**
     * Lists all MCP metadata under a product.
     */
    List<McpMetaResult> listMetaByProduct(String productId);

    /**
     * Lists MCP metadata for multiple products, including public endpoint runtime data.
     */
    List<McpMetaResult> listMetaByProductIds(List<String> productIds);

    /**
     * Deletes MCP metadata and all associated endpoints.
     */
    void deleteMeta(String mcpServerId);

    /**
     * Deletes all MCP metadata, endpoints, and ProductRef records under a product.
     */
    void deleteMetaByProduct(String productId);

    /**
     * Force deletes all MCP resources under a product, skipping publication checks.
     *
     * <p>ProductService calls this after the related publication records have already been cleaned.
     */
    void forceDeleteMetaByProduct(String productId);

    /**
     * Saves an endpoint, creating or updating it as needed.
     */
    McpEndpointResult saveEndpoint(SaveMcpEndpointParam param);

    /**
     * Lists all endpoints for an MCP Server.
     */
    List<McpEndpointResult> listEndpoints(String mcpServerId);

    /**
     * Deletes one endpoint.
     */
    void deleteEndpoint(String endpointId);

    /**
     * Lists published and public MCP Servers for marketplace pages.
     */
    PageResult<McpMetaResult> listPublishedMcpServers(Pageable pageable);

    /**
     * Lists all MCP endpoints owned by the current user.
     */
    List<MyEndpointResult> listMyEndpoints();

    /**
     * Resolves MCP transport configs for the current user and the given product IDs.
     *
     * <p>The resolver prefers endpoints subscribed by the user. When no endpoint is available, it
     * returns an empty list and lets callers decide their fallback behavior.
     */
    List<McpTransportConfig> resolveTransportConfigs(List<String> productIds, String userId);

    /**
     * Gets MCP metadata by mcpName.
     */
    McpMetaResult getMetaByName(String mcpName);

    /**
     * Lists MCP metadata from one origin.
     */
    PageResult<McpMetaResult> listMetaByOrigin(String origin, Pageable pageable);

    /**
     * Lists all MCP metadata.
     */
    PageResult<McpMetaResult> listAllMeta(Pageable pageable);

    /**
     * Lists published MCP metadata from one origin for Open API responses.
     */
    PageResult<McpMetaResult> listPublishedMetaByOrigin(String origin, Pageable pageable);

    /**
     * Lists all published MCP metadata for Open API responses.
     */
    PageResult<McpMetaResult> listAllPublishedMeta(Pageable pageable);

    /**
     * Gets published MCP metadata by mcpServerId for Open API responses.
     *
     * <p>Throws NOT_FOUND when the related product is not published.
     */
    McpMetaResult getPublishedMeta(String mcpServerId);

    /**
     * Gets published MCP metadata by mcpName for Open API responses.
     *
     * <p>Throws NOT_FOUND when the related product is not published.
     */
    McpMetaResult getPublishedMetaByName(String mcpName);

    /**
     * Refreshes tools by calling tools/list on the endpoint and saving meta.toolsConfig.
     */
    McpMetaResult refreshTools(String mcpServerId);

    /**
     * Updates the service introduction.
     */
    McpMetaResult updateServiceIntro(String mcpServerId, String serviceIntro);

    /**
     * Updates the tools config edited manually by an admin.
     */
    McpMetaResult updateToolsConfig(String mcpServerId, String toolsConfig);

    /**
     * Deploys a sandbox endpoint for a saved MCP config.
     */
    McpMetaResult deploySandbox(String mcpServerId, SaveMcpMetaParam param);

    /**
     * Undeploys sandbox hosting by deleting sandbox CRDs and endpoints.
     */
    McpMetaResult undeploySandbox(String mcpServerId);
}
