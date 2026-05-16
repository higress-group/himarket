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

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.mcp.RegisterMcpParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaDetailResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaSimpleResult;
import com.alibaba.himarket.service.McpServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

/**
 * Public MCP Server APIs for external systems using API Key authentication.
 *
 * <p>Authentication: the X-API-Key request header must match the open-api.api-key configuration.
 *
 * <p>Query APIs do not expose internal fields such as productId:
 * <ul>
 *   <li>List APIs return {@link McpMetaSimpleResult}.</li>
 *   <li>Detail APIs return {@link McpMetaDetailResult} with sensitive fields removed.</li>
 * </ul>
 */
@Tag(name = "Open MCP Server API", description = "API key protected MCP server integration APIs")
@RestController
@RequestMapping("/open-api/mcp-servers")
@RequiredArgsConstructor
public class OpenApiMcpController {

    private final McpServerService mcpServerService;

    @Value("${open-api.api-key:}")
    private String apiKey;

    /**
     * Authenticate every request to this controller before any handler method runs.
     * This ensures new endpoints added under /open-api/mcp-servers are never exposed
     * without API Key verification.
     */
    @ModelAttribute
    private void authenticate(@RequestHeader(value = "X-API-Key", required = false) String key) {
        verifyApiKey(key);
    }

    // ==================== Write APIs ====================

    @Operation(
            summary = "Register MCP server",
            description = "Register MCP metadata through the API key protected integration API")
    @PostMapping("/register")
    public McpMetaDetailResult register(@RequestBody @Valid RegisterMcpParam param) {
        McpMetaResult full = mcpServerService.registerMcp(param);
        return McpMetaDetailResult.fromFull(full);
    }

    // Update APIs are not exposed yet.
    // @PostMapping("/meta")
    // public McpMetaDetailResult saveMeta(...)

    // ==================== Detail query APIs ====================

    @Operation(summary = "Get MCP server by mcpServerId")
    @GetMapping("/meta/{mcpServerId}")
    public McpMetaDetailResult getMeta(@PathVariable String mcpServerId) {
        return McpMetaDetailResult.fromFull(mcpServerService.getPublishedMeta(mcpServerId));
    }

    @Operation(summary = "Get MCP server by mcpName")
    @GetMapping("/meta/by-name/{mcpName}")
    public McpMetaDetailResult getMetaByName(@PathVariable String mcpName) {
        return McpMetaDetailResult.fromFull(mcpServerService.getPublishedMetaByName(mcpName));
    }

    // ==================== List query APIs ====================

    @Operation(
            summary = "List published MCP servers by origin",
            description = "Return published MCP servers using a sanitized list schema")
    @GetMapping("/meta/list")
    public PageResult<McpMetaSimpleResult> listMeta(
            @RequestParam(required = false, defaultValue = "OPEN_API") String origin,
            Pageable pageable) {
        PageResult<McpMetaResult> fullPage =
                mcpServerService.listPublishedMetaByOrigin(origin, pageable);
        return new PageResult<McpMetaSimpleResult>()
                .mapFrom(fullPage, McpMetaSimpleResult::fromFull);
    }

    @Operation(
            summary = "List all published MCP servers",
            description = "Return all published MCP servers using a sanitized list schema")
    @GetMapping("/meta/list-all")
    public PageResult<McpMetaSimpleResult> listAllMeta(Pageable pageable) {
        PageResult<McpMetaResult> fullPage = mcpServerService.listAllPublishedMeta(pageable);
        return new PageResult<McpMetaSimpleResult>()
                .mapFrom(fullPage, McpMetaSimpleResult::fromFull);
    }

    // Delete APIs are not exposed yet.
    // @DeleteMapping("/meta/{mcpServerId}")

    private void verifyApiKey(String key) {
        // Open API is disabled when api-key is not configured
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "Open API is disabled. Set OPEN_API_KEY environment variable to enable it.");
        }
        if (key == null
                || key.length() != apiKey.length()
                || !java.security.MessageDigest.isEqual(
                        apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        key.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid API Key");
        }
    }
}
