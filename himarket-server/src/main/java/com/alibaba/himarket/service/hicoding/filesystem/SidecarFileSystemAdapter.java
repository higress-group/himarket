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
 * 基于 Sidecar HTTP API 的文件系统适配器。
 * <p>
 * 替代已废弃的 PodFileSystemAdapter，通过 Pod 内 Sidecar 提供的 HTTP 端点
 * 实现文件读写操作，避免使用不稳定的 kubectl exec 通道。
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
     * @param host     Sidecar 访问地址（Pod IP 或 Service IP）
     * @param basePath 工作空间根目录（默认 "/workspace"）
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
     * 使用默认基础路径构造适配器。
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
                    "文件不存在: " + relativePath);
        }
        if (response.statusCode() != 200) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "读取文件失败: " + relativePath + " (status=" + response.statusCode() + ")");
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
                    "写入文件失败: " + relativePath + " (status=" + response.statusCode() + ")");
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
                    "目录不存在: " + relativePath);
        }
        if (response.statusCode() != 200) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "列举目录失败: " + relativePath + " (status=" + response.statusCode() + ")");
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
                    "创建目录失败: " + relativePath + " (status=" + response.statusCode() + ")");
        }
    }

    @Override
    public void delete(String relativePath) throws IOException {
        // Sidecar 可能没有 delete 端点，暂时抛出不支持异常
        throw new FileSystemException(
                FileSystemException.ErrorType.IO_ERROR, SANDBOX_TYPE, "Sidecar 不支持删除操作");
    }

    @Override
    public FileInfo getFileInfo(String relativePath) throws IOException {
        // Sidecar 可能没有 stat 端点，暂时抛出不支持异常
        throw new FileSystemException(
                FileSystemException.ErrorType.IO_ERROR, SANDBOX_TYPE, "Sidecar 不支持获取文件信息操作");
    }

    // ===== 内部辅助方法 =====

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
            logger.error("Sidecar 不可达: {}", url, e);
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "Sidecar 连接失败: " + url,
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Sidecar 请求被中断: " + url, e);
        }
    }
}
