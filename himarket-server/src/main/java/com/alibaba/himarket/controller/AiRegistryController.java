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
import com.alibaba.himarket.dto.params.airegistry.CreateAiRegistryParam;
import com.alibaba.himarket.dto.params.airegistry.UpdateAiRegistryParam;
import com.alibaba.himarket.dto.result.airegistry.AiRegistryResult;
import com.alibaba.himarket.dto.result.airegistry.AiRegistrySkillResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.service.AiRegistryService;
import com.alibaba.himarket.service.AiRegistrySkillService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AIRegistry Management", description = "AIRegistry namespace config APIs")
@RestController
@RequestMapping("/airegistries")
@RequiredArgsConstructor
@AdminAuth
public class AiRegistryController {

    private final AiRegistryService aiRegistryService;

    private final AiRegistrySkillService aiRegistrySkillService;

    @Operation(summary = "List AIRegistry configs")
    @GetMapping
    public PageResult<AiRegistryResult> listAiRegistryInstances(Pageable pageable) {
        return aiRegistryService.listAiRegistryInstances(pageable);
    }

    @Operation(summary = "Get default AIRegistry config")
    @GetMapping("/default")
    public AiRegistryResult getDefaultAiRegistryInstance() {
        return aiRegistryService.getDefaultAiRegistryInstance();
    }

    @Operation(summary = "Set default AIRegistry config")
    @PutMapping("/{airegistryId}/default")
    public void setDefaultAiRegistryInstance(
            @PathVariable String airegistryId,
            @RequestParam(value = "namespaceId", required = false) String namespaceId) {
        aiRegistryService.setDefaultAiRegistry(airegistryId, namespaceId);
    }

    @Operation(summary = "Get AIRegistry config")
    @GetMapping("/{airegistryId}")
    public AiRegistryResult getAiRegistryInstance(@PathVariable String airegistryId) {
        return aiRegistryService.getAiRegistryInstance(airegistryId);
    }

    @Operation(summary = "Create AIRegistry config")
    @PostMapping
    public void createAiRegistryInstance(@RequestBody @Valid CreateAiRegistryParam param) {
        aiRegistryService.createAiRegistryInstance(param);
    }

    @Operation(summary = "Update AIRegistry config")
    @PutMapping("/{airegistryId}")
    public void updateAiRegistryInstance(
            @PathVariable String airegistryId, @RequestBody @Valid UpdateAiRegistryParam param) {
        aiRegistryService.updateAiRegistryInstance(airegistryId, param);
    }

    @Operation(summary = "Delete AIRegistry config")
    @DeleteMapping("/{airegistryId}")
    public void deleteAiRegistryInstance(@PathVariable String airegistryId) {
        aiRegistryService.deleteAiRegistryInstance(airegistryId);
    }

    @Operation(summary = "Validate AIRegistry config")
    @PostMapping("/{airegistryId}/validate")
    public void validateAiRegistryInstance(
            @PathVariable String airegistryId,
            @RequestParam(value = "namespaceId", required = false) String namespaceId) {
        aiRegistryService.validateAiRegistry(airegistryId, namespaceId);
    }

    @Operation(summary = "List AIRegistry skills")
    @GetMapping("/{airegistryId}/skills")
    public PageResult<AiRegistrySkillResult> listSkills(
            @PathVariable String airegistryId,
            @RequestParam String namespaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        aiRegistryService.getAiRegistryInstance(airegistryId);
        return aiRegistrySkillService.listSkills(airegistryId, namespaceId, page, size);
    }
}
