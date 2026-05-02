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
import com.alibaba.himarket.dto.params.apidefinition.CreateApiDefinitionParam;
import com.alibaba.himarket.dto.params.apidefinition.QueryApiDefinitionParam;
import com.alibaba.himarket.dto.params.apidefinition.UpdateApiDefinitionParam;
import com.alibaba.himarket.dto.result.apidefinition.ApiDefinitionResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.service.ApiDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "API Definition Management")
@RestController
@RequestMapping("/api-definitions")
@RequiredArgsConstructor
public class ApiDefinitionController {

    private final ApiDefinitionService apiDefinitionService;

    @Operation(summary = "Create Api Definition")
    @PostMapping
    @AdminAuth
    public ApiDefinitionResult createApiDefinition(
            @RequestBody @Valid CreateApiDefinitionParam param) {
        return apiDefinitionService.createApiDefinition(param);
    }

    @Operation(summary = "Get Api Definition")
    @GetMapping("/{apiDefinitionId}")
    public ApiDefinitionResult getApiDefinition(@PathVariable String apiDefinitionId) {
        return apiDefinitionService.getApiDefinition(apiDefinitionId);
    }

    @Operation(summary = "List Api Definitions")
    @GetMapping
    public PageResult<ApiDefinitionResult> listApiDefinitions(
            QueryApiDefinitionParam param, Pageable pageable) {
        return apiDefinitionService.listApiDefinitions(param, pageable);
    }

    @Operation(summary = "Update Api Definition")
    @PutMapping("/{apiDefinitionId}")
    @AdminAuth
    public void updateApiDefinition(
            @PathVariable String apiDefinitionId,
            @RequestBody @Valid UpdateApiDefinitionParam param) {
        apiDefinitionService.updateApiDefinition(apiDefinitionId, param);
    }

    @Operation(summary = "Delete Api Definition")
    @DeleteMapping("/{apiDefinitionId}")
    @AdminAuth
    public void deleteApiDefinition(@PathVariable String apiDefinitionId) {
        apiDefinitionService.deleteApiDefinition(apiDefinitionId);
    }
}
