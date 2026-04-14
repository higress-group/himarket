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

import com.alibaba.himarket.core.annotation.DeveloperAuth;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.skill.CreateDeveloperSkillParam;
import com.alibaba.himarket.dto.params.skill.UpdateDeveloperSkillParam;
import com.alibaba.himarket.dto.result.skill.DeveloperSkillResult;
import com.alibaba.himarket.service.DeveloperSkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "开发者个人Skill管理", description = "开发者创建、查看、编辑、删除个人Skill")
@RestController
@RequestMapping("/developer/skills")
@DeveloperAuth
@RequiredArgsConstructor
public class DeveloperSkillController {

    private static final Set<String> VALID_TYPES = Set.of("all", "personal", "official");

    private final DeveloperSkillService developerSkillService;
    private final ContextHolder contextHolder;

    @Operation(summary = "获取Skill列表")
    @GetMapping
    public List<DeveloperSkillResult> listSkills(
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(required = false) String tag) {
        if (!VALID_TYPES.contains(type)) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "type must be all/personal/official");
        }
        String developerId = contextHolder.getUser();
        return developerSkillService.listSkills(developerId, type, tag);
    }

    @Operation(summary = "创建个人Skill")
    @PostMapping
    public DeveloperSkillResult createSkill(@Valid @RequestBody CreateDeveloperSkillParam param) {
        String developerId = contextHolder.getUser();
        return developerSkillService.createSkill(developerId, param);
    }

    @Operation(summary = "获取Skill详情")
    @GetMapping("/{productId}")
    public DeveloperSkillResult getSkill(@PathVariable String productId) {
        String developerId = contextHolder.getUser();
        return developerSkillService.getSkill(developerId, productId);
    }

    @Operation(summary = "更新个人Skill")
    @PutMapping("/{productId}")
    public DeveloperSkillResult updateSkill(
            @PathVariable String productId, @Valid @RequestBody UpdateDeveloperSkillParam param) {
        String developerId = contextHolder.getUser();
        return developerSkillService.updateSkill(developerId, productId, param);
    }

    @Operation(summary = "删除个人Skill")
    @DeleteMapping("/{productId}")
    public void deleteSkill(@PathVariable String productId) {
        String developerId = contextHolder.getUser();
        developerSkillService.deleteSkill(developerId, productId);
    }

    @Operation(summary = "上传个人Skill压缩包")
    @PostMapping(value = "/{productId}/package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void uploadPackage(
            @PathVariable String productId, @RequestParam("file") MultipartFile file)
            throws IOException {
        String developerId = contextHolder.getUser();
        developerSkillService.uploadPackage(developerId, productId, file);
    }
}
