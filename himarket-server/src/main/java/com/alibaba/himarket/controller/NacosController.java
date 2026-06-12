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
import com.alibaba.himarket.dto.params.nacos.CreateNacosParam;
import com.alibaba.himarket.dto.params.nacos.QueryNacosParam;
import com.alibaba.himarket.dto.params.nacos.UpdateNacosParam;
import com.alibaba.himarket.dto.result.agent.NacosAgentResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.mcp.NacosMCPServerResult;
import com.alibaba.himarket.dto.result.nacos.MseNacosResult;
import com.alibaba.himarket.dto.result.nacos.NacosNamespaceResult;
import com.alibaba.himarket.dto.result.nacos.NacosResult;
import com.alibaba.himarket.dto.result.nacos.NacosSkillResult;
import com.alibaba.himarket.service.NacosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Nacos Resource Management",
        description = "Nacos instance and marketplace resource APIs")
@RestController
@RequestMapping("/nacos")
@RequiredArgsConstructor
@AdminAuth
public class NacosController {

    private final NacosService nacosService;

    @Operation(summary = "List Nacos instances")
    @GetMapping
    public PageResult<NacosResult> listNacosInstances(Pageable pageable) {
        return nacosService.listNacosInstances(pageable);
    }

    @Operation(
            summary = "List Nacos clusters from Alibaba Cloud MSE",
            description = "Query available Nacos clusters from Alibaba Cloud MSE")
    @GetMapping("/mse")
    public PageResult<MseNacosResult> fetchNacos(@Valid QueryNacosParam param, Pageable pageable) {
        return nacosService.fetchNacos(param, pageable);
    }

    @Operation(summary = "Get default Nacos instance")
    @GetMapping("/default")
    public NacosResult getDefaultNacosInstance() {
        return nacosService.getDefaultNacosInstance();
    }

    @Operation(
            summary = "Set default Nacos instance",
            description =
                    "New Agent Skill products bind to the default Nacos instance automatically. A"
                            + " default namespace can also be specified.")
    @PutMapping("/{nacosId}/default")
    public void setDefaultNacosInstance(
            @PathVariable String nacosId,
            @RequestParam(value = "namespaceId", required = false) String namespaceId) {
        nacosService.setDefaultNacos(nacosId, namespaceId);
    }

    @Operation(summary = "Get Nacos instance")
    @GetMapping("/{nacosId}")
    public NacosResult getNacosInstance(@PathVariable String nacosId) {
        return nacosService.getNacosInstance(nacosId);
    }

    @Operation(summary = "Create Nacos instance")
    @PostMapping
    public void createNacosInstance(@RequestBody @Valid CreateNacosParam param) {
        nacosService.createNacosInstance(param);
    }

    @Operation(summary = "Update Nacos instance")
    @PutMapping("/{nacosId}")
    public void updateNacosInstance(
            @PathVariable String nacosId, @RequestBody @Valid UpdateNacosParam param) {
        nacosService.updateNacosInstance(nacosId, param);
    }

    @Operation(summary = "Delete Nacos instance")
    @DeleteMapping("/{nacosId}")
    public void deleteNacosInstance(@PathVariable String nacosId) {
        nacosService.deleteNacosInstance(nacosId);
    }

    @Operation(
            summary = "List MCP servers from Nacos",
            description =
                    "List MCP servers from a Nacos instance, optionally filtered by namespace")
    @GetMapping("/{nacosId}/mcp-servers")
    public PageResult<NacosMCPServerResult> fetchMcpServers(
            @PathVariable String nacosId,
            @RequestParam(value = "namespaceId", required = false) String namespaceId,
            Pageable pageable)
            throws Exception {
        return nacosService.fetchMcpServers(nacosId, namespaceId, pageable);
    }

    @Operation(
            summary = "List Nacos namespaces",
            description = "List namespaces from an imported Nacos instance")
    @GetMapping("/{nacosId}/namespaces")
    public PageResult<NacosNamespaceResult> fetchNamespaces(
            @PathVariable String nacosId, Pageable pageable) throws Exception {
        return nacosService.fetchNamespaces(nacosId, pageable);
    }

    // ==================== Agent APIs ====================

    /** Lists Agents from Nacos. Keep this list-only behavior aligned with GatewayController. */
    @Operation(
            summary = "List Agents from Nacos",
            description =
                    "List Agents registered in a Nacos instance, optionally filtered by namespace")
    @GetMapping("/{nacosId}/agents")
    public PageResult<NacosAgentResult> fetchAgents(
            @Parameter(description = "Nacos instance ID", required = true) @PathVariable
                    String nacosId,
            @Parameter(description = "Namespace ID, optional and defaults to public")
                    @RequestParam(value = "namespaceId", required = false)
                    String namespaceId,
            Pageable pageable)
            throws Exception {

        return nacosService.fetchAgents(nacosId, namespaceId, pageable);
    }

    @Operation(
            summary = "List Skills from Nacos",
            description =
                    "List Skills registered in a Nacos instance, optionally filtered by namespace")
    @GetMapping("/{nacosId}/skills")
    public PageResult<NacosSkillResult> fetchSkills(
            @Parameter(description = "Nacos instance ID", required = true) @PathVariable
                    String nacosId,
            @Parameter(description = "Namespace ID, optional and defaults to public")
                    @RequestParam(value = "namespaceId", required = false)
                    String namespaceId,
            Pageable pageable)
            throws Exception {
        return nacosService.fetchSkills(nacosId, namespaceId, pageable);
    }
}
