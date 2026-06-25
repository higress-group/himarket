package com.alibaba.himarket.service.hicoding.filesystem;

import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File system adapter backed by the Sidecar HTTP API.
 *
 * <p>Replaces the deprecated PodFileSystemAdapter by using HTTP endpoints exposed by Sidecar for
 * file operations, avoiding unstable kubectl exec channels.
 */
public class SidecarFileSystemAdapter implements FileSystemAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SidecarFileSystemAdapter.class);
    private static final SandboxType SANDBOX_TYPE = SandboxType.REMOTE;
    private static final int SIDECAR_PORT = 8080;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_BASE_PATH = "/workspace";

    private final String host;
    private final String basePath;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * @param host Sidecar access address, such as Pod IP or Service IP
     * @param basePath workspace root directory; defaults to "/workspace"
     */
    public SidecarFileSystemAdapter(String host, String basePath) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be null or blank");
        }
        this.host = host;
        this.basePath = basePath != null ? basePath : DEFAULT_BASE_PATH;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates an adapter with the default base path.
     */
    public SidecarFileSystemAdapter(String host) {
        this(host, DEFAULT_BASE_PATH);
    }

    @Override
    public String readFile(String relativePath) throws IOException {
        String fullPath = resolveAndValidate(relativePath);
        String url = buildUrl("/files/read");
        String body = objectMapper.writeValueAsString(Map.of("path", fullPath));
        HttpResponse<String> response = sendPost(url, body);
        if (response.statusCode() == 404) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.FILE_NOT_FOUND,
                    SANDBOX_TYPE,
                    "File not found: " + relativePath);
        }
        if (response.statusCode() != 200) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "Failed to read file: "
                            + relativePath
                            + " (status="
                            + response.statusCode()
                            + ")");
        }
        JsonNode json = objectMapper.readTree(response.body());
        return json.has("content") ? json.get("content").asText() : "";
    }

    @Override
    public void writeFile(String relativePath, String content) throws IOException {
        String fullPath = resolveAndValidate(relativePath);
        String url = buildUrl("/files/write");
        String body = objectMapper.writeValueAsString(Map.of("path", fullPath, "content", content));
        HttpResponse<String> response = sendPost(url, body);
        if (response.statusCode() != 200) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "Failed to write file: "
                            + relativePath
                            + " (status="
                            + response.statusCode()
                            + ")");
        }
    }

    @Override
    public List<FileEntry> listDirectory(String relativePath) throws IOException {
        String fullPath = resolveAndValidate(relativePath);
        String url = buildUrl("/files/list");
        String body = objectMapper.writeValueAsString(Map.of("path", fullPath, "depth", 1));
        HttpResponse<String> response = sendPost(url, body);
        if (response.statusCode() == 404) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.FILE_NOT_FOUND,
                    SANDBOX_TYPE,
                    "Directory not found: " + relativePath);
        }
        if (response.statusCode() != 200) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "Failed to list directory: "
                            + relativePath
                            + " (status="
                            + response.statusCode()
                            + ")");
        }
        List<Map<String, Object>> items =
                objectMapper.readValue(response.body(), new TypeReference<>() {});
        List<FileEntry> entries = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String name = (String) item.get("name");
            boolean isDir = "dir".equals(item.get("type"));
            long size = item.containsKey("size") ? ((Number) item.get("size")).longValue() : 0L;
            entries.add(new FileEntry(name, isDir, size, 0L));
        }
        return entries;
    }

    @Override
    public void createDirectory(String relativePath) throws IOException {
        String fullPath = resolveAndValidate(relativePath);
        String url = buildUrl("/files/mkdir");
        String body = objectMapper.writeValueAsString(Map.of("path", fullPath));
        HttpResponse<String> response = sendPost(url, body);
        if (response.statusCode() != 200) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "Failed to create directory: "
                            + relativePath
                            + " (status="
                            + response.statusCode()
                            + ")");
        }
    }

    @Override
    public void delete(String relativePath) throws IOException {
        // Sidecar may not expose a delete endpoint yet.
        throw new FileSystemException(
                FileSystemException.ErrorType.IO_ERROR,
                SANDBOX_TYPE,
                "Sidecar does not support delete operations");
    }

    @Override
    public FileInfo getFileInfo(String relativePath) throws IOException {
        // Sidecar may not expose a stat endpoint yet.
        throw new FileSystemException(
                FileSystemException.ErrorType.IO_ERROR,
                SANDBOX_TYPE,
                "Sidecar does not support file info operations");
    }

    // Internal helpers.

    private String resolveAndValidate(String relativePath) throws FileSystemException {
        try {
            PathValidator.validatePath(basePath, relativePath);
        } catch (SecurityException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.PATH_TRAVERSAL, SANDBOX_TYPE, e.getMessage());
        }
        return basePath + "/" + relativePath;
    }

    private String buildUrl(String endpoint) {
        return "http://" + host + ":" + SIDECAR_PORT + endpoint;
    }

    private HttpResponse<String> sendPost(String url, String body) throws IOException {
        try {
            return httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .header("Content-Type", "application/json")
                            .timeout(HTTP_TIMEOUT)
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException | HttpConnectTimeoutException e) {
            logger.error(
                    "Sidecar filesystem endpoint unreachable, url={}, errorMessage={}",
                    url,
                    e.getMessage(),
                    e);
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "Failed to connect to Sidecar service: " + e.getMessage(),
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Sidecar filesystem request interrupted", e);
        }
    }
}
