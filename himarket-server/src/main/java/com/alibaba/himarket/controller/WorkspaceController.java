package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.AdminOrDeveloperAuth;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.service.hicoding.RemoteWorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Workspace", description = "Read files from user workspace")
@RestController
@RequestMapping("/workspace")
@RequiredArgsConstructor
@Slf4j
@AdminOrDeveloperAuth
public class WorkspaceController {

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp");

    private static final Set<String> BINARY_EXTENSIONS = Set.of(".pdf");

    private static final Set<String> CONVERTIBLE_EXTENSIONS = Set.of(".pptx", ".ppt");

    private final RemoteWorkspaceService remoteWorkspaceService;

    @Operation(summary = "Upload file to workspace")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件大小不能超过 5MB"));
        }
        String userId = getCurrentUserId();
        try {
            String filePath =
                    remoteWorkspaceService.uploadFile(
                            userId, file.getOriginalFilename(), file.getBytes());
            return ResponseEntity.ok(Map.of("filePath", filePath));
        } catch (IOException e) {
            log.error("Failed to upload file to sandbox: user={}", userId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "文件上传失败"));
        }
    }

    @Operation(summary = "Read file content from workspace")
    @GetMapping("/file")
    public ResponseEntity<?> readFile(
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean raw,
            @RequestParam(required = false) String runtime) {
        String userId = getCurrentUserId();

        try {
            String ext = getExtension(Paths.get(path).getFileName().toString());
            boolean isBinary =
                    IMAGE_EXTENSIONS.contains(ext)
                            || BINARY_EXTENSIONS.contains(ext)
                            || CONVERTIBLE_EXTENSIONS.contains(ext);
            String encoding = isBinary ? "base64" : "utf-8";
            Map<String, Object> result =
                    remoteWorkspaceService.readFileWithEncoding(userId, path, encoding);
            return ResponseEntity.ok(
                    Map.of(
                            "content", result.get("content"),
                            "encoding", result.get("encoding")));
        } catch (IOException e) {
            log.error("Failed to read file from sandbox: user={}, path={}", userId, path, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to read file from sandbox"));
        }
    }

    @Operation(summary = "Download file from workspace as binary stream")
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFile(
            @RequestParam String path, @RequestParam(required = false) String runtime) {
        String userId = getCurrentUserId();
        String fileName = Paths.get(path).getFileName().toString();
        String ext = getExtension(fileName);
        String mime = getMimeType(ext);

        try {
            byte[] bytes = remoteWorkspaceService.readFileBytes(userId, path);
            return ResponseEntity.ok()
                    .header(
                            "Content-Disposition",
                            "attachment; filename=\"" + sanitizeFileName(fileName) + "\"")
                    .contentType(MediaType.parseMediaType(mime))
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (IOException e) {
            log.error("Failed to download file {} for user {}", path, userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private static String getMimeType(String ext) {
        return switch (ext) {
            case ".pptx" ->
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case ".ppt" -> "application/vnd.ms-powerpoint";
            case ".docx" ->
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".doc" -> "application/msword";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".xls" -> "application/vnd.ms-excel";
            case ".pdf" -> "application/pdf";
            case ".zip" -> "application/zip";
            case ".mp4" -> "video/mp4";
            case ".mp3" -> "audio/mpeg";
            default -> "application/octet-stream";
        };
    }

    @Operation(summary = "List changed files in workspace directory")
    @GetMapping("/changes")
    public ResponseEntity<?> listWorkspaceChanges(
            @RequestParam String cwd,
            @RequestParam long since,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(required = false) String runtime) {
        String userId = getCurrentUserId();

        try {
            List<Map<String, Object>> changes =
                    remoteWorkspaceService.getChanges(userId, cwd, since);
            return ResponseEntity.ok(Map.of("changes", changes));
        } catch (IOException e) {
            log.error("Failed to get changes from sandbox: user={}, cwd={}", userId, cwd, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get changes from sandbox"));
        }
    }

    // ======================== Directory Tree API ========================

    @Operation(summary = "Get directory tree for workspace")
    @GetMapping("/tree")
    public ResponseEntity<?> getDirectoryTree(
            @RequestParam String cwd,
            @RequestParam(defaultValue = "3") int depth,
            @RequestParam(required = false) String runtime) {
        String userId = getCurrentUserId();

        try {
            Map<String, Object> tree = remoteWorkspaceService.getDirectoryTree(userId, cwd, depth);
            return ResponseEntity.ok(tree);
        } catch (IOException e) {
            log.error("Failed to get directory tree from sandbox: user={}, cwd={}", userId, cwd, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get directory tree from sandbox"));
        }
    }

    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String principal) {
            return principal;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未认证");
    }

    private static String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0) return "";
        return fileName.substring(lastDot).toLowerCase();
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        // Strip path separators and keep only the filename part
        String baseName = name;
        int lastSlash = Math.max(baseName.lastIndexOf('/'), baseName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            baseName = baseName.substring(lastSlash + 1);
        }
        // Replace any remaining dangerous characters
        return baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
