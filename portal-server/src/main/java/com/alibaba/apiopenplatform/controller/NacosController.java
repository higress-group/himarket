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

package com.alibaba.apiopenplatform.controller;

import com.alibaba.apiopenplatform.core.annotation.AdminAuth;
import com.alibaba.apiopenplatform.dto.params.nacos.CreateNacosParam;
import com.alibaba.apiopenplatform.dto.params.nacos.QueryNacosParam;
import com.alibaba.apiopenplatform.dto.params.nacos.UpdateNacosParam;
import com.alibaba.apiopenplatform.dto.result.nacos.MseNacosResult;
import com.alibaba.apiopenplatform.dto.result.mcp.NacosMCPServerResult;
import com.alibaba.apiopenplatform.dto.result.nacos.NacosNamespaceResult;
import com.alibaba.apiopenplatform.dto.result.nacos.NacosResult;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.dto.result.agent.NacosAgentResult;
import com.alibaba.apiopenplatform.service.NacosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Tag(name = "Nacos资源管理", description = "Nacos实例管理与能力市场统一控制器")
@RestController
@RequestMapping("/nacos")
@RequiredArgsConstructor
@AdminAuth
public class NacosController {

    private final NacosService nacosService;

    @Operation(summary = "获取Nacos实例列表", description = "分页获取Nacos实例列表")
    @GetMapping
    public PageResult<NacosResult> listNacosInstances(Pageable pageable) {
        return nacosService.listNacosInstances(pageable);
    }

    @Operation(summary = "从阿里云MSE获取Nacos集群列表")
    @GetMapping("/mse")
    public PageResult<MseNacosResult> fetchNacos(@Valid QueryNacosParam param,
                                              Pageable pageable) {
        return nacosService.fetchNacos(param, pageable);
    }

    @Operation(summary = "获取Nacos实例详情", description = "根据ID获取Nacos实例详细信息")
    @GetMapping("/{nacosId}")
    public NacosResult getNacosInstance(@PathVariable String nacosId) {
        return nacosService.getNacosInstance(nacosId);
    }

    @Operation(summary = "创建Nacos实例", description = "创建新的Nacos实例")
    @PostMapping
    public void createNacosInstance(@RequestBody @Valid CreateNacosParam param) {
        nacosService.createNacosInstance(param);
    }

    @Operation(summary = "更新Nacos实例", description = "更新指定Nacos实例信息")
    @PutMapping("/{nacosId}")
    public void updateNacosInstance(@PathVariable String nacosId, @RequestBody @Valid UpdateNacosParam param) {
        nacosService.updateNacosInstance(nacosId, param);
    }

    @Operation(summary = "删除Nacos实例", description = "删除指定的Nacos实例")
    @DeleteMapping("/{nacosId}")
    public void deleteNacosInstance(@PathVariable String nacosId) {
        nacosService.deleteNacosInstance(nacosId);
    }

    @Operation(summary = "获取Nacos中的MCP Server列表", description = "获取指定Nacos实例中的MCP Server列表，可按命名空间过滤")
    @GetMapping("/{nacosId}/mcp-servers")
    public PageResult<NacosMCPServerResult> fetchMcpServers(@PathVariable String nacosId,
                                                            @RequestParam(value = "namespaceId", required = false) String namespaceId,
                                                            Pageable pageable) throws Exception {
        return nacosService.fetchMcpServers(nacosId, namespaceId, pageable);
    }

    @Operation(summary = "获取指定Nacos实例的命名空间列表")
    @GetMapping("/{nacosId}/namespaces")
    public PageResult<NacosNamespaceResult> fetchNamespaces(@PathVariable String nacosId,
                                                            Pageable pageable) throws Exception {
        return nacosService.fetchNamespaces(nacosId, pageable);
    }

    // ==================== Agent 相关 ====================

    /**
     * 获取 Nacos 中的 Agent 列表
     * 注意：与 GatewayController 保持一致，只提供列表接口，不提供详情接口
     */
    @Operation(
        summary = "获取 Nacos 中的 Agent 列表", 
        description = "获取指定 Nacos 实例中注册的 Agent 列表，可按命名空间过滤"
    )
    @GetMapping("/{nacosId}/agents")
    public PageResult<NacosAgentResult> fetchAgents(
            @Parameter(description = "Nacos 实例 ID", required = true)
            @PathVariable String nacosId,
            
            @Parameter(description = "命名空间 ID（可选，默认 public）")
            @RequestParam(value = "namespaceId", required = false) String namespaceId,
            
            Pageable pageable) throws Exception {
        
        return nacosService.fetchAgents(nacosId, namespaceId, pageable);
    }

} 