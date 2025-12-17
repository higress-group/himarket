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
import com.alibaba.himarket.dto.params.api.CreateAPIDefinitionParam;
import com.alibaba.himarket.dto.params.api.CreateEndpointParam;
import com.alibaba.himarket.dto.params.api.PublishAPIParam;
import com.alibaba.himarket.dto.params.api.QueryAPIDefinitionParam;
import com.alibaba.himarket.dto.params.api.UpdateAPIDefinitionParam;
import com.alibaba.himarket.dto.params.api.UpdateEndpointParam;
import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.dto.result.api.APIEndpointVO;
import com.alibaba.himarket.dto.result.api.APIPublishHistoryVO;
import com.alibaba.himarket.dto.result.api.APIPublishRecordVO;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.service.APIDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API Definition 管理控制器
 * 提供 API Definition 的创建、查询、更新、删除等管理功能
 */
@Tag(name = "API Definition 管理", description = "API Definition 生命周期管理接口")
@RestController
@RequestMapping("/api-definitions")
@Slf4j
@RequiredArgsConstructor
public class APIDefinitionController {

    private final APIDefinitionService apiDefinitionService;

    @Operation(summary = "创建 API Definition")
    @PostMapping
    @AdminAuth
    public APIDefinitionVO createAPIDefinition(@RequestBody @Valid CreateAPIDefinitionParam param) {
        return apiDefinitionService.createAPIDefinition(param);
    }

    @Operation(summary = "获取 API Definition 详情")
    @GetMapping("/{apiDefinitionId}")
    public APIDefinitionVO getAPIDefinition(@PathVariable String apiDefinitionId) {
        return apiDefinitionService.getAPIDefinition(apiDefinitionId);
    }

    @Operation(summary = "查询 API Definition 列表")
    @GetMapping
    public PageResult<APIDefinitionVO> listAPIDefinitions(QueryAPIDefinitionParam param, Pageable pageable) {
        return apiDefinitionService.listAPIDefinitions(param, pageable);
    }

    @Operation(summary = "更新 API Definition")
    @PutMapping("/{apiDefinitionId}")
    @AdminAuth
    public APIDefinitionVO updateAPIDefinition(
            @PathVariable String apiDefinitionId,
            @RequestBody @Valid UpdateAPIDefinitionParam param) {
        return apiDefinitionService.updateAPIDefinition(apiDefinitionId, param);
    }

    @Operation(summary = "删除 API Definition")
    @DeleteMapping("/{apiDefinitionId}")
    @AdminAuth
    public void deleteAPIDefinition(@PathVariable String apiDefinitionId) {
        apiDefinitionService.deleteAPIDefinition(apiDefinitionId);
    }

    @Operation(summary = "获取 API Definition 的端点列表")
    @GetMapping("/{apiDefinitionId}/endpoints")
    public List<APIEndpointVO> listEndpoints(@PathVariable String apiDefinitionId) {
        return apiDefinitionService.listEndpoints(apiDefinitionId);
    }

    @Operation(summary = "创建端点")
    @PostMapping("/{apiDefinitionId}/endpoints")
    @AdminAuth
    public APIEndpointVO createEndpoint(
            @PathVariable String apiDefinitionId,
            @RequestBody @Valid CreateEndpointParam param) {
        return apiDefinitionService.createEndpoint(apiDefinitionId, param);
    }

    @Operation(summary = "更新端点")
    @PutMapping("/{apiDefinitionId}/endpoints/{endpointId}")
    @AdminAuth
    public APIEndpointVO updateEndpoint(
            @PathVariable String apiDefinitionId,
            @PathVariable String endpointId,
            @RequestBody @Valid UpdateEndpointParam param) {
        return apiDefinitionService.updateEndpoint(apiDefinitionId, endpointId, param);
    }

    @Operation(summary = "删除端点")
    @DeleteMapping("/{apiDefinitionId}/endpoints/{endpointId}")
    @AdminAuth
    public void deleteEndpoint(
            @PathVariable String apiDefinitionId,
            @PathVariable String endpointId) {
        apiDefinitionService.deleteEndpoint(apiDefinitionId, endpointId);
    }

    @Operation(summary = "获取发布记录列表")
    @GetMapping("/{apiDefinitionId}/publish-records")
    public PageResult<APIPublishRecordVO> listPublishRecords(
            @PathVariable String apiDefinitionId,
            Pageable pageable) {
        return apiDefinitionService.listPublishRecords(apiDefinitionId, pageable);
    }

    @Operation(summary = "发布 API")
    @PostMapping("/{apiDefinitionId}/publish")
    @AdminAuth
    public APIPublishRecordVO publishAPI(
            @PathVariable String apiDefinitionId,
            @RequestBody @Valid PublishAPIParam param) {
        return apiDefinitionService.publishAPI(apiDefinitionId, param);
    }

    @Operation(summary = "取消发布")
    @DeleteMapping("/{apiDefinitionId}/publish-records/{recordId}")
    @AdminAuth
    public void unpublishAPI(
            @PathVariable String apiDefinitionId,
            @PathVariable String recordId) {
        apiDefinitionService.unpublishAPI(apiDefinitionId, recordId);
    }

    @Operation(summary = "获取发布历史列表")
    @GetMapping("/{apiDefinitionId}/publish-history")
    public PageResult<APIPublishHistoryVO> listPublishHistory(
            @PathVariable String apiDefinitionId,
            Pageable pageable) {
        return apiDefinitionService.listPublishHistory(apiDefinitionId, pageable);
    }
}
