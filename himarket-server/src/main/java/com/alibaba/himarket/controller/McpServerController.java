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

@Tag(name = "MCP Server 管理")
@RestController
@RequestMapping("/mcp-servers")
@RequiredArgsConstructor
public class McpServerController {

    private final McpServerService mcpServerService;
    private final ContextHolder contextHolder;

    // ==================== 管理接口（需要 Admin 权限） ====================

    @Operation(summary = "保存 MCP 元信息（创建/更新）")
    @PostMapping("/meta")
    @AdminAuth
    public McpMetaResult saveMeta(@RequestBody @Valid SaveMcpMetaParam param) {
        return mcpServerService.saveMeta(param);
    }

    @Operation(summary = "删除 MCP 元信息及关联 endpoint")
    @DeleteMapping("/meta/{mcpServerId}")
    @AdminAuth
    public void deleteMeta(@PathVariable String mcpServerId) {
        mcpServerService.deleteMeta(mcpServerId);
    }

    @Operation(summary = "删除产品下所有 MCP 配置（meta + endpoint + ref + 重置状态）")
    @DeleteMapping("/meta/by-product/{productId}")
    @AdminAuth
    public void deleteMetaByProduct(@PathVariable String productId) {
        mcpServerService.deleteMetaByProduct(productId);
    }

    @Operation(summary = "保存 endpoint")
    @PostMapping("/endpoints")
    @AdminAuth
    public McpEndpointResult saveEndpoint(@RequestBody @Valid SaveMcpEndpointParam param) {
        return mcpServerService.saveEndpoint(param);
    }

    @Operation(summary = "删除 endpoint")
    @DeleteMapping("/endpoints/{endpointId}")
    @AdminAuth
    public void deleteEndpoint(@PathVariable String endpointId) {
        mcpServerService.deleteEndpoint(endpointId);
    }

    // ==================== 查询接口（Portal 可访问） ====================

    @Operation(summary = "获取 MCP 元信息")
    @GetMapping("/meta/{mcpServerId}")
    public McpMetaResult getMeta(@PathVariable String mcpServerId) {
        McpMetaResult result = mcpServerService.getMeta(mcpServerId);
        return contextHolder.isAdministrator() ? result : result.sanitize();
    }

    @Operation(summary = "获取产品下所有 MCP 元信息")
    @GetMapping("/meta")
    public List<McpMetaResult> listMetaByProduct(@RequestParam String productId) {
        List<McpMetaResult> results = mcpServerService.listMetaByProduct(productId);
        if (!contextHolder.isAdministrator()) {
            results.forEach(McpMetaResult::sanitize);
        }
        return results;
    }

    @Operation(summary = "批量获取多个产品的 MCP 元信息（含公共 endpoint 热数据）")
    @GetMapping("/meta/batch")
    public List<McpMetaResult> listMetaByProductIds(@RequestParam List<String> productIds) {
        List<McpMetaResult> results = mcpServerService.listMetaByProductIds(productIds);
        if (!contextHolder.isAdministrator()) {
            results.forEach(McpMetaResult::sanitize);
        }
        return results;
    }

    @Operation(summary = "批量获取多个产品的 MCP 公开信息（匿名可访问，脱敏）")
    @GetMapping("/meta/batch/public")
    @PublicAccess
    public List<McpMetaPublicResult> listMetaByProductIdsPublic(
            @RequestParam List<String> productIds) {
        return mcpServerService.listMetaByProductIds(productIds).stream()
                .map(McpMetaPublicResult::fromFull)
                .collect(java.util.stream.Collectors.toList());
    }

    @Operation(summary = "刷新工具列表（连接 endpoint 获取 tools/list）")
    @PostMapping("/meta/{mcpServerId}/refresh-tools")
    @AdminAuth
    public McpMetaResult refreshTools(@PathVariable String mcpServerId) {
        return mcpServerService.refreshTools(mcpServerId);
    }

    @Operation(summary = "更新服务介绍")
    @PutMapping("/meta/{mcpServerId}/service-intro")
    @AdminAuth
    public McpMetaResult updateServiceIntro(
            @PathVariable String mcpServerId, @Valid @RequestBody UpdateServiceIntroParam body) {
        return mcpServerService.updateServiceIntro(mcpServerId, body.getServiceIntro());
    }

    @Operation(summary = "更新工具配置（手动编辑）")
    @PutMapping("/meta/{mcpServerId}/tools-config")
    @AdminAuth
    public McpMetaResult updateToolsConfig(
            @PathVariable String mcpServerId, @RequestBody String toolsConfig) {
        return mcpServerService.updateToolsConfig(mcpServerId, toolsConfig);
    }

    @Operation(summary = "管理员手动部署沙箱（为已保存的 MCP 配置部署沙箱 endpoint）")
    @PostMapping("/meta/{mcpServerId}/deploy-sandbox")
    @AdminAuth
    public McpMetaResult deploySandbox(
            @PathVariable String mcpServerId, @Valid @RequestBody DeploySandboxParam body) {
        return mcpServerService.deploySandbox(mcpServerId, body.toSaveMcpMetaParam());
    }

    @Operation(summary = "管理员取消沙箱托管（删除沙箱 CRD 和 endpoint）")
    @DeleteMapping("/meta/{mcpServerId}/deploy-sandbox")
    @AdminAuth
    public McpMetaResult undeploySandbox(@PathVariable String mcpServerId) {
        return mcpServerService.undeploySandbox(mcpServerId);
    }

    @Operation(summary = "获取 MCP Server 的所有 endpoint")
    @GetMapping("/endpoints")
    @AdminAuth
    public List<McpEndpointResult> listEndpoints(@RequestParam String mcpServerId) {
        return mcpServerService.listEndpoints(mcpServerId);
    }

    @Operation(summary = "市场列表：已发布且公开的 MCP Server")
    @GetMapping("/published")
    public PageResult<McpMetaResult> listPublished(Pageable pageable) {
        PageResult<McpMetaResult> page = mcpServerService.listPublishedMcpServers(pageable);
        if (!contextHolder.isAdministrator()) {
            page.getContent().forEach(McpMetaResult::sanitize);
        }
        return page;
    }

    @Operation(summary = "我的 MCP：查询当前用户拥有的所有 endpoint")
    @GetMapping("/my-endpoints")
    public List<MyEndpointResult> listMyEndpoints() {
        return mcpServerService.listMyEndpoints();
    }

    @Operation(summary = "用户注册 MCP Server（Portal 端，登录用户即可调用）")
    @PostMapping("/register")
    public McpMetaResult register(@RequestBody @Valid RegisterMcpParam param) {
        McpMetaResult result = mcpServerService.registerMcp(param);
        return contextHolder.isAdministrator() ? result : result.sanitize();
    }
}
