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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

@Tag(name = "Skill Management", description = "Skill package, file, version, and import APIs")
@RestController
@RequestMapping("/skills")
@Slf4j
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @Operation(
            summary = "Upload Skill ZIP package",
            description = "Upload a multipart Skill package for the product")
    @PostMapping(value = "/{productId}/package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAuth
    public void uploadPackage(
            @PathVariable String productId,
            @Parameter(description = "Skill ZIP package", required = true) @RequestParam("file")
                    MultipartFile file)
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

    @Operation(summary = "Publish Skill version")
    @PostMapping("/{productId}/versions")
    @AdminAuth
    public void publishVersion(
            @PathVariable String productId, @RequestBody @Valid PublishWorkerVersionParam param) {
        skillService.publishVersion(productId, param.getVersion());
    }

    @Operation(summary = "Publish approved Skill version")
    @PostMapping("/{productId}/versions/{version}/publish")
    @AdminAuth
    public void publishApprovedVersion(
            @PathVariable String productId, @PathVariable String version) {
        skillService.publishApprovedVersion(productId, version);
    }

    @Operation(summary = "Update Skill version status")
    @PatchMapping("/{productId}/versions/{version}")
    @AdminAuth
    public void updateVersionStatus(
            @PathVariable String productId,
            @PathVariable String version,
            @RequestBody @Valid UpdateWorkerVersionStatusParam param) {
        skillService.changeVersionStatus(productId, version, "online".equals(param.getStatus()));
    }

    @Operation(
            summary = "Force-publish Skill version",
            description =
                    "Publish an existing Skill version and optionally update the latest label")
    @PostMapping("/{productId}/versions/{version}/force-publish")
    @AdminAuth
    public void forcePublishVersion(
            @PathVariable String productId,
            @PathVariable String version,
            @RequestParam(required = false, defaultValue = "true") Boolean updateLatestLabel) {
        skillService.forcePublishVersion(productId, version, updateLatestLabel);
    }

    @Operation(summary = "Set latest Skill version")
    @PutMapping("/{productId}/versions/latest")
    @AdminAuth
    public void setLatestVersion(
            @PathVariable String productId, @RequestBody @Valid SetLatestWorkerVersionParam param) {
        skillService.setLatestVersion(productId, param.getVersion());
    }

    @Operation(summary = "Delete Skill draft")
    @DeleteMapping("/{productId}/draft")
    @AdminAuth
    public void deleteDraft(@PathVariable String productId) {
        skillService.deleteDraft(productId);
    }

    @Operation(
            summary = "Download Skill ZIP package",
            description = "Return the Skill package as binary ZIP content")
    @ApiResponse(
            responseCode = "200",
            description = "Skill ZIP package",
            content =
                    @Content(
                            mediaType = "application/zip",
                            schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/{productId}/download")
    public void downloadPackage(
            @PathVariable String productId,
            @RequestParam(required = false) String version,
            HttpServletResponse response)
            throws IOException {
        skillService.downloadPackage(productId, version, response);
    }

    @Operation(summary = "Get Skill CLI download info")
    @GetMapping("/{productId}/cli-info")
    @PublicAccess
    public CliDownloadInfo getCliDownloadInfo(@PathVariable String productId) {
        return skillService.getCliDownloadInfo(productId);
    }

    @Operation(
            summary = "Import Skills from Nacos",
            description = "Import Skill definitions from the selected Nacos instance")
    @PostMapping("/import")
    @AdminAuth
    public ImportResult importFromNacos(
            @RequestParam String nacosId, @RequestParam(required = false) String namespace) {
        return skillService.importFromNacos(nacosId, namespace);
    }
}
