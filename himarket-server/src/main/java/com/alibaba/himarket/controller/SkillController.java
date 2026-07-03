package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.AdminAuth;
import com.alibaba.himarket.core.annotation.PublicAccess;
import com.alibaba.himarket.dto.params.skill.CreateSkillDraftParam;
import com.alibaba.himarket.dto.params.skill.UpdateSkillDraftParam;
import com.alibaba.himarket.dto.params.skill.UpdateSkillVersionParam;
import com.alibaba.himarket.dto.result.cli.CliDownloadInfo;
import com.alibaba.himarket.dto.result.common.FileContentResult;
import com.alibaba.himarket.dto.result.common.FileTreeNode;
import com.alibaba.himarket.dto.result.common.ImportResult;
import com.alibaba.himarket.dto.result.common.SkillDraftResult;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

    @Operation(
            summary = "Update Skill version",
            description =
                    "Update exactly one aspect of a Skill version per request. Use"
                        + " `{\"status\":\"reviewing\"}` to submit the version for review and"
                        + " trigger the Skill package scan. Use `{\"status\":\"online\"}` to"
                        + " publish an approved version, `{\"status\":\"online\",\"force\":true}`"
                        + " to force publish a rejected version, `{\"status\":\"offline\"}` to take"
                        + " the version offline, `{\"latest\":true}` to mark it as latest, or"
                        + " `{\"author\":\"name\"}` to update the version author.")
    @PatchMapping("/{productId}/versions/{version}")
    @AdminAuth
    public void updateVersion(
            @PathVariable String productId,
            @PathVariable String version,
            @RequestBody @Valid UpdateSkillVersionParam param) {
        skillService.updateVersion(productId, version, param);
    }

    @Operation(summary = "Create Skill draft")
    @PostMapping("/{productId}/draft")
    @AdminAuth
    public void createDraft(
            @PathVariable String productId, @RequestBody @Valid CreateSkillDraftParam param) {
        skillService.createDraft(productId, param);
    }

    @Operation(summary = "Get Skill draft")
    @GetMapping("/{productId}/draft")
    @AdminAuth
    public SkillDraftResult getDraft(@PathVariable String productId) {
        return skillService.getDraft(productId);
    }

    @Operation(summary = "Update Skill draft")
    @PutMapping("/{productId}/draft")
    @AdminAuth
    public void updateDraft(
            @PathVariable String productId, @RequestBody @Valid UpdateSkillDraftParam param) {
        skillService.updateDraft(productId, param);
    }

    @Operation(summary = "Delete Skill draft")
    @DeleteMapping("/{productId}/draft")
    @AdminAuth
    public void deleteDraft(@PathVariable String productId) {
        skillService.deleteDraft(productId);
    }

    @ApiResponse(
            responseCode = "200",
            description = "Skill ZIP package",
            content =
                    @Content(
                            mediaType = "application/zip",
                            schema = @Schema(type = "string", format = "binary")))
    @Operation(
            summary = "Download Skill ZIP package",
            description = "Return the Skill package as binary ZIP content")
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
