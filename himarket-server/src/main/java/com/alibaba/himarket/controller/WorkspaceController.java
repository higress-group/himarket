package com.alibaba.himarket.controller;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.core.annotation.AdminOrDeveloperAuth;
import com.alibaba.himarket.service.document.DocumentConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    private static final Set<String> TEXT_EXTENSIONS =
            Set.of(".html", ".htm", ".md", ".mdx", ".svg", ".txt", ".css", ".json", ".xml", ".csv");

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp");

    private static final Set<String> BINARY_EXTENSIONS = Set.of(".pdf");

    private static final Set<String> CONVERTIBLE_EXTENSIONS = Set.of(".pptx", ".ppt");

    private static final Set<String> IGNORED_SCAN_DIRECTORIES =
            Set.of(".git", "node_modules", "target", "dist", "__pycache__", ".idea", ".vscode");

    private static final long MAX_SCAN_FILE_SIZE_BYTES = 50L * 1024L * 1024L;

    private final AcpProperties acpProperties;
    private final DocumentConversionService conversionService;
    private final ConcurrentMap<Path, CompletableFuture<Path>> previewConversionTasks =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<Path, String> previewConversionErrors = new ConcurrentHashMap<>();

    @Operation(summary = "Upload file to workspace")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        String userId = getCurrentUserId();
        String sanitized = userId.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path uploadsDir =
                Paths.get(acpProperties.getWorkspaceRoot(), sanitized, "uploads")
                        .toAbsolutePath()
                        .normalize();

        try {
            Files.createDirectories(uploadsDir);

            String originalName = file.getOriginalFilename();
            String safeName =
                    UUID.randomUUID().toString().substring(0, 8)
                            + "_"
                            + sanitizeFileName(originalName);
            Path target = uploadsDir.resolve(safeName).normalize();

            // Prevent path traversal via crafted filename
            if (!target.startsWith(uploadsDir)) {
                log.warn(
                        "Path traversal attempt via filename by user {}: {}", userId, originalName);
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid filename"));
            }

            file.transferTo(target.toFile());
            log.info("File uploaded: user={}, path={}", userId, target);

            return ResponseEntity.ok(Map.of("filePath", target.toAbsolutePath().toString()));
        } catch (IOException e) {
            log.error("Failed to upload file for user {}", userId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to upload file"));
        }
    }

    @Operation(summary = "Read file content from workspace")
    @GetMapping("/file")
    public ResponseEntity<?> readFile(
            @RequestParam String path, @RequestParam(defaultValue = "false") boolean raw) {
        String userId = getCurrentUserId();
        Path workspaceRoot = getWorkspaceRootForUser(userId);
        Path filePath = workspaceRoot.resolve(path).toAbsolutePath().normalize();

        // Prevent path traversal
        if (!filePath.startsWith(workspaceRoot)) {
            log.warn("Path traversal attempt by user {}: {}", userId, path);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid path"));
        }

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            String ext = getExtension(filePath.getFileName().toString());

            if (raw) {
                return readRawFile(filePath, ext);
            }

            if (IMAGE_EXTENSIONS.contains(ext)
                    || BINARY_EXTENSIONS.contains(ext)
                    || CONVERTIBLE_EXTENSIONS.contains(ext)) {
                byte[] bytes = Files.readAllBytes(filePath);
                String base64 = Base64.getEncoder().encodeToString(bytes);
                return ResponseEntity.ok(Map.of("content", base64, "encoding", "base64"));
            }

            // Default: read as text
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            return ResponseEntity.ok(Map.of("content", content, "encoding", "utf-8"));
        } catch (IOException e) {
            log.error("Failed to read file {} for user {}", filePath, userId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to read file"));
        }
    }

    @Operation(summary = "Prepare preview for convertible documents")
    @PostMapping(value = "/preview/prepare", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> preparePreview(@RequestBody PreparePreviewRequest request) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Path is required"));
        }

        String userId = getCurrentUserId();
        Path workspaceRoot = getWorkspaceRootForUser(userId);
        Path filePath = workspaceRoot.resolve(request.path()).toAbsolutePath().normalize();

        if (!filePath.startsWith(workspaceRoot)) {
            log.warn("Path traversal attempt by user {}: {}", userId, request.path());
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid path"));
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }

        String ext = getExtension(filePath.getFileName().toString());
        if (!CONVERTIBLE_EXTENSIONS.contains(ext)) {
            return ResponseEntity.ok(buildPreviewPrepareResponse("unsupported", null, null));
        }

        Path cachedPdf = conversionService.getCachedPdfIfUpToDate(filePath);
        if (cachedPdf != null && Files.exists(cachedPdf)) {
            previewConversionErrors.remove(filePath);
            return ResponseEntity.ok(buildPreviewPrepareResponse("ready", cachedPdf, null));
        }

        CompletableFuture<Path> existingTask = previewConversionTasks.get(filePath);
        if (existingTask != null) {
            if (!existingTask.isDone()) {
                return ResponseEntity.ok(buildPreviewPrepareResponse("converting", null, null));
            }
            return completedPrepareResponse(filePath, existingTask);
        }

        CompletableFuture<Path> newTask =
                CompletableFuture.supplyAsync(() -> conversionService.convertToPdf(filePath));
        CompletableFuture<Path> raced = previewConversionTasks.putIfAbsent(filePath, newTask);
        if (raced == null) {
            newTask.whenComplete(
                    (result, ex) -> {
                        previewConversionTasks.remove(filePath, newTask);
                        if (ex != null || result == null) {
                            previewConversionErrors.put(filePath, getConversionErrorMessage(ex));
                        } else {
                            previewConversionErrors.remove(filePath);
                        }
                    });
            return ResponseEntity.ok(buildPreviewPrepareResponse("converting", null, null));
        }

        if (!raced.isDone()) {
            return ResponseEntity.ok(buildPreviewPrepareResponse("converting", null, null));
        }
        return completedPrepareResponse(filePath, raced);
    }

    @Operation(summary = "List changed files in workspace directory")
    @GetMapping("/changes")
    public ResponseEntity<?> listWorkspaceChanges(
            @RequestParam String cwd,
            @RequestParam long since,
            @RequestParam(defaultValue = "200") int limit) {
        String userId = getCurrentUserId();
        Path workspaceRoot = getWorkspaceRootForUser(userId);
        Path cwdPath = workspaceRoot.resolve(cwd).toAbsolutePath().normalize();

        if (!cwdPath.startsWith(workspaceRoot)) {
            log.warn("Path traversal attempt on cwd by user {}: {}", userId, cwd);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid cwd"));
        }
        if (!Files.exists(cwdPath) || !Files.isDirectory(cwdPath)) {
            return ResponseEntity.badRequest().body(Map.of("error", "cwd does not exist"));
        }

        long effectiveSince = Math.max(0L, since);
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        List<WorkspaceChange> changes = new ArrayList<>();

        try {
            Files.walkFileTree(
                    cwdPath,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path dir, BasicFileAttributes attrs) {
                            if (!dir.equals(cwdPath)
                                    && IGNORED_SCAN_DIRECTORIES.contains(
                                            dir.getFileName().toString())) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (!attrs.isRegularFile()) {
                                return FileVisitResult.CONTINUE;
                            }

                            long size = attrs.size();
                            if (size > MAX_SCAN_FILE_SIZE_BYTES) {
                                return FileVisitResult.CONTINUE;
                            }

                            long mtimeMs = attrs.lastModifiedTime().toMillis();
                            if (mtimeMs <= effectiveSince) {
                                return FileVisitResult.CONTINUE;
                            }

                            String ext = getExtension(file.getFileName().toString());
                            changes.add(
                                    new WorkspaceChange(
                                            file.toAbsolutePath().normalize().toString(),
                                            mtimeMs,
                                            size,
                                            ext));
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to scan workspace changes: user={}, cwd={}", userId, cwdPath, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to scan workspace changes"));
        }

        changes.sort(Comparator.comparingLong(WorkspaceChange::mtimeMs).reversed());
        List<Map<String, Object>> result =
                changes.stream().limit(safeLimit).map(this::toMap).toList();
        return ResponseEntity.ok(Map.of("changes", result));
    }

    private ResponseEntity<?> readRawFile(Path filePath, String ext) throws IOException {
        if (IMAGE_EXTENSIONS.contains(ext)
                || BINARY_EXTENSIONS.contains(ext)
                || CONVERTIBLE_EXTENSIONS.contains(ext)) {
            byte[] bytes = Files.readAllBytes(filePath);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            return ResponseEntity.ok(Map.of("content", base64, "encoding", "base64"));
        }
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        return ResponseEntity.ok(Map.of("content", content, "encoding", "utf-8"));
    }

    private ResponseEntity<?> completedPrepareResponse(
            Path filePath, CompletableFuture<Path> task) {
        try {
            Path previewPath = task.getNow(null);
            if (previewPath != null && Files.exists(previewPath)) {
                previewConversionErrors.remove(filePath);
                return ResponseEntity.ok(buildPreviewPrepareResponse("ready", previewPath, null));
            }
        } catch (CompletionException ex) {
            previewConversionErrors.put(filePath, getConversionErrorMessage(ex));
        }
        String reason =
                previewConversionErrors.getOrDefault(filePath, "Failed to convert document to PDF");
        return ResponseEntity.ok(buildPreviewPrepareResponse("failed", null, reason));
    }

    private Map<String, Object> buildPreviewPrepareResponse(
            String status, Path previewPath, String reason) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", status);
        if (previewPath != null) {
            resp.put("previewPath", previewPath.toAbsolutePath().normalize().toString());
        }
        if (reason != null && !reason.isBlank()) {
            resp.put("reason", reason);
        }
        return resp;
    }

    private static String getConversionErrorMessage(Throwable error) {
        Throwable root = error;
        if (error instanceof CompletionException completionException
                && completionException.getCause() != null) {
            root = completionException.getCause();
        }
        if (root != null && root.getMessage() != null && !root.getMessage().isBlank()) {
            return root.getMessage();
        }
        return "Failed to convert document to PDF";
    }

    private Path getWorkspaceRootForUser(String userId) {
        String sanitized = userId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return Paths.get(acpProperties.getWorkspaceRoot(), sanitized).toAbsolutePath().normalize();
    }

    private Map<String, Object> toMap(WorkspaceChange change) {
        return Map.of(
                "path",
                change.path(),
                "mtimeMs",
                change.mtimeMs(),
                "size",
                change.size(),
                "ext",
                change.ext());
    }

    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String principal) {
            return principal;
        }
        return "anonymous";
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

    private record PreparePreviewRequest(String path) {}

    private record WorkspaceChange(String path, long mtimeMs, long size, String ext) {}
}
