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
 * MCP Server 开放接口 — 供外部系统通过 API Key 调用。
 *
 * <p>鉴权方式：请求头 X-API-Key，值需与配置项 open-api.api-key 一致。
 *
 * <p>查询接口不暴露 productId 等内部字段：
 * <ul>
 *   <li>列表接口返回 {@link McpMetaSimpleResult}（精简）</li>
 *   <li>详情接口返回 {@link McpMetaDetailResult}（完整但脱敏）</li>
 * </ul>
 */
@Tag(name = "MCP Server 开放接口")
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

    // ==================== 写入接口 ====================

    @Operation(summary = "注册 MCP Server（自动创建 Product + Meta + ProductRef）")
    @PostMapping("/register")
    public McpMetaDetailResult register(@RequestBody @Valid RegisterMcpParam param) {
        McpMetaResult full = mcpServerService.registerMcp(param);
        return McpMetaDetailResult.fromFull(full);
    }

    // 更新接口暂不对外开放
    // @PostMapping("/meta")
    // public McpMetaDetailResult saveMeta(...)

    // ==================== 查询接口（详情） ====================

    @Operation(summary = "按 mcpServerId 查询 MCP Server 详情")
    @GetMapping("/meta/{mcpServerId}")
    public McpMetaDetailResult getMeta(@PathVariable String mcpServerId) {
        return McpMetaDetailResult.fromFull(mcpServerService.getPublishedMeta(mcpServerId));
    }

    @Operation(summary = "按 mcpName 查询 MCP Server 详情")
    @GetMapping("/meta/by-name/{mcpName}")
    public McpMetaDetailResult getMetaByName(@PathVariable String mcpName) {
        return McpMetaDetailResult.fromFull(mcpServerService.getPublishedMetaByName(mcpName));
    }

    // ==================== 查询接口（列表，精简） ====================

    @Operation(summary = "分页查询指定来源的 MCP Server 列表（精简，仅已发布）")
    @GetMapping("/meta/list")
    public PageResult<McpMetaSimpleResult> listMeta(
            @RequestParam(required = false, defaultValue = "OPEN_API") String origin,
            Pageable pageable) {
        PageResult<McpMetaResult> fullPage =
                mcpServerService.listPublishedMetaByOrigin(origin, pageable);
        return new PageResult<McpMetaSimpleResult>()
                .mapFrom(fullPage, McpMetaSimpleResult::fromFull);
    }

    @Operation(summary = "分页查询所有 MCP Server 列表（精简，仅已发布）")
    @GetMapping("/meta/list-all")
    public PageResult<McpMetaSimpleResult> listAllMeta(Pageable pageable) {
        PageResult<McpMetaResult> fullPage = mcpServerService.listAllPublishedMeta(pageable);
        return new PageResult<McpMetaSimpleResult>()
                .mapFrom(fullPage, McpMetaSimpleResult::fromFull);
    }

    // DELETE 接口暂不对外开放
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
