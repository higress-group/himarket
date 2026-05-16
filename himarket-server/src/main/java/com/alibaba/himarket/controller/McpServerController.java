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

package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.AdminAuth;
import com.alibaba.himarket.core.annotation.PublicAccess;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.mcp.DeploySandboxParam;
import com.alibaba.himarket.dto.params.mcp.RegisterMcpParam;
import com.alibaba.himarket.dto.params.mcp.SaveMcpEndpointParam;
import com.alibaba.himarket.dto.params.mcp.SaveMcpMetaParam;
import com.alibaba.himarket.dto.params.mcp.UpdateServiceIntroParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.mcp.McpEndpointResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaPublicResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaResult;
import com.alibaba.himarket.dto.result.mcp.MyEndpointResult;
import com.alibaba.himarket.service.McpServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "MCP Server Management",
        description = "MCP metadata, endpoint, tool, sandbox deployment, and registration APIs")
@RestController
@RequestMapping("/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;
    private final ContextHolder contextHolder;

    // ==================== Management APIs (Admin required) ====================

    @Operation(summary = "Save MCP metadata")
    @PostMapping("/meta")
    @AdminAuth
    public McpMetaResult saveMeta(@RequestBody @Valid SaveMcpMetaParam param) {
        return mcpServerService.saveMeta(param);
    }

    @Operation(summary = "Delete MCP metadata and endpoints")
    @DeleteMapping("/meta/{mcpServerId}")
    @AdminAuth
    public void deleteMeta(@PathVariable String mcpServerId) {
        mcpServerService.deleteMeta(mcpServerId);
    }

    @Operation(summary = "Delete all MCP configuration for a product")
    @DeleteMapping("/meta/by-product/{productId}")
    @AdminAuth
    public void deleteMetaByProduct(@PathVariable String productId) {
        mcpServerService.deleteMetaByProduct(productId);
    }

    @Operation(summary = "Save MCP endpoint")
    @PostMapping("/endpoints")
    @AdminAuth
    public McpEndpointResult saveEndpoint(@RequestBody @Valid SaveMcpEndpointParam param) {
        return mcpServerService.saveEndpoint(param);
    }

    @Operation(summary = "Delete MCP endpoint")
    @DeleteMapping("/endpoints/{endpointId}")
    @AdminAuth
    public void deleteEndpoint(@PathVariable String endpointId) {
        mcpServerService.deleteEndpoint(endpointId);
    }

    // ==================== Query APIs (Portal accessible) ====================

    @Operation(summary = "Get MCP metadata")
    @GetMapping("/meta/{mcpServerId}")
    public McpMetaResult getMeta(@PathVariable String mcpServerId) {
        McpMetaResult result = mcpServerService.getMeta(mcpServerId);
        return contextHolder.isAdministrator() ? result : result.sanitize();
    }

    @Operation(summary = "List MCP metadata for product")
    @GetMapping("/meta")
    public List<McpMetaResult> listMetaByProduct(@RequestParam String productId) {
        List<McpMetaResult> results = mcpServerService.listMetaByProduct(productId);
        if (!contextHolder.isAdministrator()) {
            results.forEach(McpMetaResult::sanitize);
        }
        return results;
    }

    @Operation(summary = "Batch get MCP metadata for products")
    @GetMapping("/meta/batch")
    public List<McpMetaResult> listMetaByProductIds(@RequestParam List<String> productIds) {
        List<McpMetaResult> results = mcpServerService.listMetaByProductIds(productIds);
        if (!contextHolder.isAdministrator()) {
            results.forEach(McpMetaResult::sanitize);
        }
        return results;
    }

    @Operation(summary = "Batch get public MCP metadata for products")
    @GetMapping("/meta/batch/public")
    @PublicAccess
    public List<McpMetaPublicResult> listMetaByProductIdsPublic(
            @RequestParam List<String> productIds) {
        return mcpServerService.listMetaByProductIds(productIds).stream()
                .map(McpMetaPublicResult::fromFull)
                .collect(java.util.stream.Collectors.toList());
    }

    @Operation(
            summary = "Refresh MCP tool list",
            description = "Synchronize tool metadata from the configured MCP connection")
    @PostMapping("/meta/{mcpServerId}/refresh-tools")
    @AdminAuth
    public McpMetaResult refreshTools(@PathVariable String mcpServerId) {
        return mcpServerService.refreshTools(mcpServerId);
    }

    @Operation(summary = "Update service introduction")
    @PutMapping("/meta/{mcpServerId}/service-intro")
    @AdminAuth
    public McpMetaResult updateServiceIntro(
            @PathVariable String mcpServerId, @Valid @RequestBody UpdateServiceIntroParam body) {
        return mcpServerService.updateServiceIntro(mcpServerId, body.getServiceIntro());
    }

    @Operation(
            summary = "Update tool configuration",
            description = "Replace the tool configuration JSON for the MCP server")
    @PutMapping("/meta/{mcpServerId}/tools-config")
    @AdminAuth
    public McpMetaResult updateToolsConfig(
            @PathVariable String mcpServerId, @RequestBody String toolsConfig) {
        return mcpServerService.updateToolsConfig(mcpServerId, toolsConfig);
    }

    @Operation(
            summary = "Deploy MCP sandbox endpoint",
            description = "Create or update a sandbox deployment endpoint for the MCP server")
    @PostMapping("/meta/{mcpServerId}/deploy-sandbox")
    @AdminAuth
    public McpMetaResult deploySandbox(
            @PathVariable String mcpServerId, @Valid @RequestBody DeploySandboxParam body) {
        return mcpServerService.deploySandbox(mcpServerId, body.toSaveMcpMetaParam());
    }

    @Operation(
            summary = "Undeploy MCP sandbox endpoint",
            description = "Remove the active sandbox deployment endpoint for the MCP server")
    @DeleteMapping("/meta/{mcpServerId}/deploy-sandbox")
    @AdminAuth
    public McpMetaResult undeploySandbox(@PathVariable String mcpServerId) {
        return mcpServerService.undeploySandbox(mcpServerId);
    }

    @Operation(summary = "List MCP server endpoints")
    @GetMapping("/endpoints")
    @AdminAuth
    public List<McpEndpointResult> listEndpoints(@RequestParam String mcpServerId) {
        return mcpServerService.listEndpoints(mcpServerId);
    }

    @Operation(summary = "List public MCP servers")
    @GetMapping("/published")
    public PageResult<McpMetaResult> listPublished(Pageable pageable) {
        PageResult<McpMetaResult> page = mcpServerService.listPublishedMcpServers(pageable);
        if (!contextHolder.isAdministrator()) {
            page.getContent().forEach(McpMetaResult::sanitize);
        }
        return page;
    }

    @Operation(summary = "List my MCP endpoints")
    @GetMapping("/my-endpoints")
    public List<MyEndpointResult> listMyEndpoints() {
        return mcpServerService.listMyEndpoints();
    }

    @Operation(
            summary = "Register MCP server",
            description =
                    "Register MCP metadata and return a sanitized response for non-admin users")
    @PostMapping("/register")
    public McpMetaResult register(@RequestBody @Valid RegisterMcpParam param) {
        McpMetaResult result = mcpServerService.registerMcp(param);
        return contextHolder.isAdministrator() ? result : result.sanitize();
    }
}
