package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.AdminAuth;
import com.alibaba.himarket.core.annotation.PublicAccess;
import com.alibaba.himarket.dto.params.worker.PublishWorkerVersionParam;
import com.alibaba.himarket.dto.params.worker.SetLatestWorkerVersionParam;
import com.alibaba.himarket.dto.params.worker.UpdateWorkerVersionStatusParam;
import com.alibaba.himarket.dto.result.cli.CliDownloadInfo;
import com.alibaba.himarket.dto.result.common.FileContentResult;
import com.alibaba.himarket.dto.result.common.FileTreeNode;
import com.alibaba.himarket.dto.result.common.ImportResult;
import com.alibaba.himarket.dto.result.common.VersionResult;
import com.alibaba.himarket.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Skill Management", description = "Skill CRUD and file operations via productId")
@RestController
@RequestMapping("/skills")
@Slf4j
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @Operation(summary = "Upload Skill from ZIP")
    @PostMapping(value = "/{productId}/package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAuth
    public void uploadPackage(
            @PathVariable String productId, @RequestParam("file") MultipartFile file)
            throws IOException {
        skillService.uploadPackage(productId, file);
    }

    @Operation(summary = "Delete Skill")
    @DeleteMapping("/{productId}")
    @AdminAuth
    public void deleteSkill(@PathVariable String productId) {
        skillService.deleteSkill(productId);
    }

    @Operation(summary = "Get Skill file tree")
    @GetMapping("/{productId}/files")
    @PublicAccess
    public List<FileTreeNode> getFileTree(
            @PathVariable String productId, @RequestParam(required = false) String version) {
        return skillService.getFileTree(productId, version);
    }

    @Operation(summary = "Get Skill file content")
    @GetMapping("/{productId}/files/{*filePath}")
    @PublicAccess
    public FileContentResult getFileContent(
            @PathVariable String productId,
            @PathVariable String filePath,
            @RequestParam(required = false) String version) {
        String path = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        return skillService.getFileContent(productId, path, version);
    }

    @Operation(summary = "List Skill versions")
    @GetMapping("/{productId}/versions")
    @PublicAccess
    public List<VersionResult> listVersions(@PathVariable String productId) {
        return skillService.listVersions(productId);
    }

    @Operation(summary = "Publish (submit for review) a specific Skill version")
    @PostMapping("/{productId}/versions")
    @AdminAuth
    public void publishVersion(
            @PathVariable String productId, @RequestBody @Valid PublishWorkerVersionParam param) {
        skillService.publishVersion(productId, param.getVersion());
    }

    @Operation(summary = "Update status (online/offline) of a specific Skill version")
    @PatchMapping("/{productId}/versions/{version}")
    @AdminAuth
    public void updateVersionStatus(
            @PathVariable String productId,
            @PathVariable String version,
            @RequestBody @Valid UpdateWorkerVersionStatusParam param) {
        skillService.changeVersionStatus(productId, version, "online".equals(param.getStatus()));
    }

    @Operation(summary = "Force-publish a Skill version, bypassing pipeline")
    @PostMapping("/{productId}/versions/{version}/force-publish")
    @AdminAuth
    public void forcePublishVersion(
            @PathVariable String productId,
            @PathVariable String version,
            @RequestParam(required = false, defaultValue = "true") Boolean updateLatestLabel) {
        skillService.forcePublishVersion(productId, version, updateLatestLabel);
    }

    @Operation(summary = "Set a version as latest")
    @PutMapping("/{productId}/versions/latest")
    @AdminAuth
    public void setLatestVersion(
            @PathVariable String productId, @RequestBody @Valid SetLatestWorkerVersionParam param) {
        skillService.setLatestVersion(productId, param.getVersion());
    }

    @Operation(summary = "Delete current editing draft")
    @DeleteMapping("/{productId}/draft")
    @AdminAuth
    public void deleteDraft(@PathVariable String productId) {
        skillService.deleteDraft(productId);
    }

    @Operation(summary = "Download Skill as ZIP")
    @GetMapping("/{productId}/download")
    public void downloadPackage(
            @PathVariable String productId,
            @RequestParam(required = false) String version,
            HttpServletResponse response)
            throws IOException {
        skillService.downloadPackage(productId, version, response);
    }

    @Operation(summary = "Get CLI download info for Skill detail page")
    @GetMapping("/{productId}/cli-info")
    @PublicAccess
    public CliDownloadInfo getCliDownloadInfo(@PathVariable String productId) {
        return skillService.getCliDownloadInfo(productId);
    }

    @Operation(summary = "Import Skills from Nacos")
    @PostMapping("/import")
    @AdminAuth
    public ImportResult importFromNacos(
            @RequestParam String nacosId, @RequestParam(required = false) String namespace) {
        return skillService.importFromNacos(nacosId, namespace);
    }
}
