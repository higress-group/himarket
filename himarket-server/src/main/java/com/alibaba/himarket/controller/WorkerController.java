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

@Tag(name = "Worker Management", description = "Worker package, file, version, and import APIs")
@RestController
@RequestMapping("/workers")
@Slf4j
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    @Operation(
            summary = "Upload Worker ZIP package",
            description = "Upload a multipart Worker package for the product")
    @PostMapping(value = "/{productId}/package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAuth
    public void uploadPackage(
            @PathVariable String productId,
            @Parameter(description = "Worker ZIP package", required = true) @RequestParam("file")
                    MultipartFile file)
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

    @Operation(summary = "Publish Worker version")
    @PostMapping("/{productId}/versions")
    @AdminAuth
    public void publishVersion(
            @PathVariable String productId, @RequestBody @Valid PublishWorkerVersionParam param) {
        workerService.publishVersion(productId, param.getVersion());
    }

    @Operation(summary = "Update Worker version status")
    @PatchMapping("/{productId}/versions/{version}")
    @AdminAuth
    public void updateVersionStatus(
            @PathVariable String productId,
            @PathVariable String version,
            @RequestBody @Valid UpdateWorkerVersionStatusParam param) {
        workerService.changeVersionStatus(productId, version, "online".equals(param.getStatus()));
    }

    @Operation(summary = "Set latest Worker version")
    @PutMapping("/{productId}/versions/latest")
    @AdminAuth
    public void setLatestVersion(
            @PathVariable String productId, @RequestBody @Valid SetLatestWorkerVersionParam param) {
        workerService.setLatestVersion(productId, param.getVersion());
    }

    @Operation(summary = "Delete Worker draft")
    @DeleteMapping("/{productId}/draft")
    @AdminAuth
    public void deleteDraft(@PathVariable String productId) {
        workerService.deleteDraft(productId);
    }

    @Operation(
            summary = "Download Worker ZIP package",
            description = "Return the Worker package as binary ZIP content")
    @ApiResponse(
            responseCode = "200",
            description = "Worker ZIP package",
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
        workerService.downloadPackage(productId, version, response);
    }

    @Operation(summary = "Get Worker CLI download info")
    @GetMapping("/{productId}/cli-info")
    @PublicAccess
    public CliDownloadInfo getCliDownloadInfo(@PathVariable String productId) {
        return workerService.getCliDownloadInfo(productId);
    }

    @Operation(
            summary = "Import Workers from Nacos",
            description = "Import Worker definitions from the selected Nacos instance")
    @PostMapping("/import")
    @AdminAuth
    public ImportResult importFromNacos(
            @RequestParam String nacosId, @RequestParam(required = false) String namespace) {
        return workerService.importFromNacos(nacosId, namespace);
    }
}
