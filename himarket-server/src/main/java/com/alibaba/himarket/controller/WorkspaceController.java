package com.alibaba.himarket.controller;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.core.annotation.AdminOrDeveloperAuth;
import com.alibaba.himarket.service.document.DocumentConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    private static final Set<String> TEXT_EXTENSIONS =
            Set.of(".html", ".htm", ".md", ".mdx", ".svg", ".txt", ".css", ".json", ".xml", ".csv");

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp");

    private static final Set<String> BINARY_EXTENSIONS = Set.of(".pdf");

    private static final Set<String> CONVERTIBLE_EXTENSIONS = Set.of(".pptx", ".ppt");

    private final AcpProperties acpProperties;
    private final DocumentConversionService conversionService;

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
    public ResponseEntity<?> readFile(@RequestParam String path) {
        String userId = getCurrentUserId();
        String sanitized = userId.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path workspaceRoot =
                Paths.get(acpProperties.getWorkspaceRoot(), sanitized).toAbsolutePath().normalize();
        Path filePath = workspaceRoot.resolve(path).normalize();

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

            if (IMAGE_EXTENSIONS.contains(ext) || BINARY_EXTENSIONS.contains(ext)) {
                byte[] bytes = Files.readAllBytes(filePath);
                String base64 = Base64.getEncoder().encodeToString(bytes);
                return ResponseEntity.ok(Map.of("content", base64, "encoding", "base64"));
            }

            if (CONVERTIBLE_EXTENSIONS.contains(ext)) {
                Path pdfPath = conversionService.convertToPdf(filePath);
                if (pdfPath == null || !Files.exists(pdfPath)) {
                    return ResponseEntity.internalServerError()
                            .body(Map.of("error", "Failed to convert document to PDF"));
                }
                byte[] bytes = Files.readAllBytes(pdfPath);
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
}
