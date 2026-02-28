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
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.skill.SkillFileContentResult;
import com.alibaba.himarket.dto.result.skill.SkillFileTreeNode;
import com.alibaba.himarket.dto.result.skill.SkillPackageUploadResult;
import com.alibaba.himarket.service.SkillPackageService;
import com.alibaba.himarket.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "技能管理", description = "提供技能下载等功能")
@RestController
@RequestMapping("/skills")
@Slf4j
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;
    private final SkillPackageService skillPackageService;

    @Operation(summary = "下载技能 SKILL.md 内容")
    @GetMapping("/{productId}/download")
    public ResponseEntity<String> downloadSkill(@PathVariable String productId) {
        String content = skillService.downloadSkill(productId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown"))
                .body(content);
    }

    @Operation(summary = "上传 Skill 包（zip 或 tar.gz）")
    @PostMapping("/{productId}/package")
    @AdminAuth
    public SkillPackageUploadResult uploadSkillPackage(
            @PathVariable String productId, @RequestParam("file") MultipartFile file) {
        try {
            return skillPackageService.uploadPackage(productId, file);
        } catch (ParseException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, e.getMessage());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Operation(summary = "获取 Skill 文件树（不含内容）")
    @GetMapping("/{productId}/files")
    public List<SkillFileTreeNode> getSkillFileTree(@PathVariable String productId) {
        return skillPackageService.getFileTree(productId);
    }

    @Operation(summary = "获取所有 Skill 文件（含内容）")
    @GetMapping("/{productId}/files/all")
    public List<SkillFileContentResult> getAllSkillFiles(@PathVariable String productId) {
        return skillPackageService.getAllFiles(productId);
    }

    @Operation(summary = "获取单个 Skill 文件内容")
    @GetMapping("/{productId}/files/**")
    public SkillFileContentResult getSkillFileContent(
            @PathVariable String productId, HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String prefix = "/skills/" + productId + "/files/";
        int idx = requestPath.indexOf(prefix);
        String filePath = idx >= 0 ? requestPath.substring(idx + prefix.length()) : "";
        return skillPackageService.getFileContent(productId, filePath);
    }

    @Operation(summary = "下载完整 Skill 包（zip）")
    @GetMapping("/{productId}/package")
    public void downloadSkillPackage(@PathVariable String productId, HttpServletResponse response)
            throws IOException {
        skillPackageService.downloadPackage(productId, response);
    }

    @Operation(summary = "更新 SKILL.md 内容（同步 skill_file 表和 product.document）")
    @PutMapping("/{productId}/skill-md")
    @AdminAuth
    public void updateSkillMd(@PathVariable String productId, @RequestBody String content) {
        skillPackageService.updateSkillMd(productId, content);
    }
}
