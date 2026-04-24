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
import com.alibaba.himarket.service.WorkerService;
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

@Tag(name = "Worker Management", description = "Worker CRUD and file operations via productId")
@RestController
@RequestMapping("/workers")
@Slf4j
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    @Operation(summary = "Upload Worker from ZIP")
    @PostMapping(value = "/{productId}/package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAuth
    public void uploadPackage(
            @PathVariable String productId, @RequestParam("file") MultipartFile file)
            throws IOException {
        workerService.uploadPackage(productId, file);
    }

    @Operation(summary = "Delete Worker")
    @DeleteMapping("/{productId}")
    @AdminAuth
    public void deleteWorker(@PathVariable String productId) {
        workerService.deleteAgentSpec(productId);
    }

    @Operation(summary = "Get Worker file tree")
    @GetMapping("/{productId}/files")
    @PublicAccess
    public List<FileTreeNode> getFileTree(
            @PathVariable String productId, @RequestParam(required = false) String version) {
        return workerService.getFileTree(productId, version);
    }

    @Operation(summary = "Get Worker file content")
    @GetMapping("/{productId}/files/{*filePath}")
    @PublicAccess
    public FileContentResult getFileContent(
            @PathVariable String productId,
            @PathVariable String filePath,
            @RequestParam(required = false) String version) {
        String path = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        return workerService.getFileContent(productId, path, version);
    }

    @Operation(summary = "List Worker versions")
    @GetMapping("/{productId}/versions")
    @PublicAccess
    public List<VersionResult> listVersions(@PathVariable String productId) {
        return workerService.listVersions(productId);
    }

    @Operation(summary = "Publish (submit for review) a specific Worker version")
    @PostMapping("/{productId}/versions")
    @AdminAuth
    public void publishVersion(
            @PathVariable String productId, @RequestBody @Valid PublishWorkerVersionParam param) {
        workerService.publishVersion(productId, param.getVersion());
    }

    @Operation(summary = "Update status (online/offline) of a specific Worker version")
    @PatchMapping("/{productId}/versions/{version}")
    @AdminAuth
    public void updateVersionStatus(
            @PathVariable String productId,
            @PathVariable String version,
            @RequestBody @Valid UpdateWorkerVersionStatusParam param) {
        workerService.changeVersionStatus(productId, version, "online".equals(param.getStatus()));
    }

    @Operation(summary = "Force-publish a Worker version, bypassing pipeline")
    @PostMapping("/{productId}/versions/{version}/force-publish")
    @AdminAuth
    public void forcePublishVersion(
            @PathVariable String productId,
            @PathVariable String version,
            @RequestParam(required = false, defaultValue = "true") Boolean updateLatestLabel) {
        workerService.forcePublishVersion(productId, version, updateLatestLabel);
    }

    @Operation(summary = "Set a version as latest")
    @PutMapping("/{productId}/versions/latest")
    @AdminAuth
    public void setLatestVersion(
            @PathVariable String productId, @RequestBody @Valid SetLatestWorkerVersionParam param) {
        workerService.setLatestVersion(productId, param.getVersion());
    }

    @Operation(summary = "Delete current editing draft")
    @DeleteMapping("/{productId}/draft")
    @AdminAuth
    public void deleteDraft(@PathVariable String productId) {
        workerService.deleteDraft(productId);
    }

    @Operation(summary = "Download Worker as ZIP")
    @GetMapping("/{productId}/download")
    public void downloadPackage(
            @PathVariable String productId,
            @RequestParam(required = false) String version,
            HttpServletResponse response)
            throws IOException {
        workerService.downloadPackage(productId, version, response);
    }

    @Operation(summary = "Get CLI download info for Worker detail page")
    @GetMapping("/{productId}/cli-info")
    @PublicAccess
    public CliDownloadInfo getCliDownloadInfo(@PathVariable String productId) {
        return workerService.getCliDownloadInfo(productId);
    }

    @Operation(summary = "Import Workers from Nacos")
    @PostMapping("/import")
    @AdminAuth
    public ImportResult importFromNacos(
            @RequestParam String nacosId, @RequestParam(required = false) String namespace) {
        return workerService.importFromNacos(nacosId, namespace);
    }
}
