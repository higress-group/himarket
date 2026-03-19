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
import com.alibaba.himarket.dto.params.sandbox.ClusterInfoParam;
import com.alibaba.himarket.dto.params.sandbox.ImportSandboxParam;
import com.alibaba.himarket.dto.params.sandbox.QuerySandboxParam;
import com.alibaba.himarket.dto.params.sandbox.UpdateSandboxParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.sandbox.ClusterInfoResult;
import com.alibaba.himarket.dto.result.sandbox.SandboxResult;
import com.alibaba.himarket.service.SandboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@Tag(name = "沙箱实例管理")
@RestController
@RequestMapping("/sandboxes")
@RequiredArgsConstructor
public class SandboxController {

    private final SandboxService sandboxService;

    @Operation(summary = "获取所有运行中的沙箱实例（部署用）")
    @GetMapping("/active")
    @AdminAuth
    public java.util.List<com.alibaba.himarket.dto.result.sandbox.SandboxSimpleResult>
            listActiveSandboxes() {
        return sandboxService.listActiveSandboxes();
    }

    @Operation(summary = "获取沙箱实例列表")
    @GetMapping
    @AdminAuth
    public PageResult<SandboxResult> listSandboxes(QuerySandboxParam param, Pageable pageable) {
        return sandboxService.listSandboxes(param, pageable);
    }

    @Operation(summary = "导入沙箱实例")
    @PostMapping
    @AdminAuth
    public void importSandbox(@RequestBody @Valid ImportSandboxParam param) {
        sandboxService.importSandbox(param);
    }

    @Operation(summary = "更新沙箱实例")
    @PutMapping("/{sandboxId}")
    @AdminAuth
    public void updateSandbox(
            @PathVariable String sandboxId, @RequestBody @Valid UpdateSandboxParam param) {
        sandboxService.updateSandbox(sandboxId, param);
    }

    @Operation(summary = "删除沙箱实例")
    @DeleteMapping("/{sandboxId}")
    @AdminAuth
    public void deleteSandbox(@PathVariable String sandboxId) {
        sandboxService.deleteSandbox(sandboxId);
    }

    @Operation(summary = "获取集群信息")
    @PostMapping("/cluster-info")
    @AdminAuth
    public ClusterInfoResult fetchClusterInfo(@RequestBody @Valid ClusterInfoParam param) {
        return sandboxService.fetchClusterInfo(param.getKubeConfig());
    }

    @Operation(summary = "手动触发单个沙箱健康检查")
    @PostMapping("/{sandboxId}/health-check")
    @AdminAuth
    public SandboxResult healthCheck(@PathVariable String sandboxId) {
        return sandboxService.healthCheck(sandboxId);
    }

    @Operation(summary = "获取沙箱集群的 Namespace 列表")
    @GetMapping("/{sandboxId}/namespaces")
    @AdminAuth
    public java.util.List<String> listNamespaces(@PathVariable String sandboxId) {
        return sandboxService.listNamespaces(sandboxId);
    }

    @Operation(summary = "查询沙箱上的活跃 MCP 部署数量")
    @GetMapping("/{sandboxId}/active-deployments")
    @AdminAuth
    public java.util.Map<String, Object> getActiveDeployments(@PathVariable String sandboxId) {
        int count = sandboxService.countActiveDeployments(sandboxId);
        return java.util.Map.of("count", count);
    }
}
