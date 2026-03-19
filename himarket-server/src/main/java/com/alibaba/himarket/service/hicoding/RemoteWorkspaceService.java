package com.alibaba.himarket.service.hicoding;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 远程沙箱文件操作服务。
 *
 * <p>封装通过 Sidecar HTTP API 操作远程沙箱内文件的逻辑，
 * 供 WorkspaceController 在 runtime=remote 时调用。
 *
 * <p>使用 AcpProperties.remote 配置获取 Sidecar 地址。
 */
@Service
public class RemoteWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(RemoteWorkspaceService.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final String WORKSPACE_ROOT = "/workspace";

    private final AcpProperties acpProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RemoteWorkspaceService(AcpProperties acpProperties) {
        this.acpProperties = acpProperties;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取远程 Sidecar 的地址。
     */
    private String getRemoteHost() {
        return acpProperties.getRemote().getHost();
    }

    private int getRemotePort() {
        return acpProperties.getRemote().getPort();
    }

    /**
     * 获取 Pod 内的目录树。
     * 通过 Sidecar HTTP API /files/list 实现，并将返回结果转换为前端期望的树形结构。
     *
     * @param userId 用户 ID
     * @param cwd    工作目录路径
     * @param depth  目录深度
     * @return 前端期望的树形结构 Map
     * @throws IOException 网络或解析异常
     */
    public Map<String, Object> getDirectoryTree(String userId, String cwd, int depth)
            throws IOException {
        validateUserPath(userId, cwd);
        String host = getRemoteHost();
        String url = "http://" + host + ":" + getRemotePort() + "/files/list";
        String body = objectMapper.writeValueAsString(Map.of("path", cwd, "depth", depth));

        HttpResponse<String> response = sendPost(url, body);
        if (response.statusCode() != 200) {
            throw new BusinessException(
                    ErrorCode.SANDBOX_ERROR,
                    "Sidecar /files/list 失败 (status="
                            + response.statusCode()
                            + "): "
                            + response.body());
        }

        // Sidecar 返回: [{"name":"src","type":"dir","children":[...]}]
        List<Map<String, Object>> sidecarItems =
                objectMapper.readValue(response.body(), new TypeReference<>() {});

        // 转换为前端期望的树形结构（根节点为 cwd 目录）
        Map<String, Object> root = new HashMap<>();
        root.put("name", extractDirName(cwd));
        root.put("path", cwd);
        root.put("type", "directory");
        root.put("children", convertChildren(sidecarItems, cwd));
        return root;
    }

    /**
     * 读取 Pod 内的文件内容（UTF-8 文本）。
     *
     * @param userId   用户 ID
     * @param filePath 文件路径
     * @return 文件内容字符串
     * @throws IOException 网络或解析异常
     */
    public String readFile(String userId, String filePath) throws IOException {
        return readFileWithEncoding(userId, filePath, "utf-8").get("content").toString();
    }

    /**
     * 读取 Pod 内的文件内容，支持指定编码。
     *
     * @param userId   用户 ID
     * @param filePath 文件路径
     * @param encoding 编码方式："utf-8" 或 "base64"
     * @return 包含 content 和 encoding 的 Map
     * @throws IOException 网络或解析异常
     */
    public Map<String, Object> readFileWithEncoding(String userId, String filePath, String encoding)
            throws IOException {
        String resolved = resolvePathForUser(userId, filePath);
        String host = getRemoteHost();
        String url = "http://" + host + ":" + getRemotePort() + "/files/read";
        Map<String, Object> params = new HashMap<>();
        params.put("path", resolved);
        params.put("encoding", encoding);
        String body = objectMapper.writeValueAsString(params);

        HttpResponse<String> response = sendPost(url, body);
        if (response.statusCode() != 200) {
            throw new BusinessException(
                    ErrorCode.SANDBOX_ERROR,
                    "Sidecar /files/read 失败 (status="
                            + response.statusCode()
                            + "): "
                            + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        String content = json.has("content") ? json.get("content").asText() : "";
        String respEncoding = json.has("encoding") ? json.get("encoding").asText() : encoding;
        return Map.of("content", content, "encoding", respEncoding);
    }

    /**
     * 从远程沙箱下载文件的原始字节。
     * 优先尝试 Sidecar GET /files/download（原始二进制流，兼容 OpenSandbox execd），
     * 若 404 则降级为 POST /files/read + base64 解码。
     */
    public byte[] readFileBytes(String userId, String filePath) throws IOException {
        String resolved = resolvePathForUser(userId, filePath);
        String host = getRemoteHost();

        // 方案 1：GET /files/download — 直接返回原始字节流，无 JSON 包装
        String encodedPath =
                java.net.URLEncoder.encode(resolved, java.nio.charset.StandardCharsets.UTF_8);
        String downloadUrl =
                "http://" + host + ":" + getRemotePort() + "/files/download?path=" + encodedPath;
        try {
            HttpResponse<byte[]> dlResp =
                    httpClient.send(
                            HttpRequest.newBuilder(URI.create(downloadUrl))
                                    .GET()
                                    .timeout(Duration.ofSeconds(30))
                                    .build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            if (dlResp.statusCode() == 200) {
                return dlResp.body();
            }
            log.info(
                    "Sidecar /files/download status={}, fallback to /files/read",
                    dlResp.statusCode());
        } catch (ConnectException | HttpConnectTimeoutException e) {
            log.error("Sidecar 不可达: {}", downloadUrl, e);
            throw new BusinessException(ErrorCode.SANDBOX_CONNECTION_FAILED, downloadUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Sidecar 请求被中断: " + downloadUrl, e);
        }

        // 方案 2：POST /files/read + base64 解码
        Map<String, Object> result = readFileWithEncoding(userId, filePath, "base64");
        String content = result.get("content").toString();
        String encoding = result.get("encoding").toString();
        if ("base64".equals(encoding)) {
            // 清理空白字符，修复 padding
            String cleaned = content.replaceAll("[\\s\\r\\n]", "");
            int mod = cleaned.length() % 4;
            if (mod != 0) {
                cleaned = cleaned + "=".repeat(4 - mod);
            }
            return Base64.getMimeDecoder().decode(cleaned);
        }
        return content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 获取 Pod 内的文件变更列表。
     *
     * @param userId 用户 ID
     * @param cwd    工作目录路径
     * @param since  时间戳（毫秒），返回此时间之后的变更
     * @return 文件变更列表，每项包含 path、mtimeMs、size、ext
     * @throws IOException 网络或解析异常
     */
    public List<Map<String, Object>> getChanges(String userId, String cwd, long since)
            throws IOException {
        validateUserPath(userId, cwd);
        String host = getRemoteHost();
        String url = "http://" + host + ":" + getRemotePort() + "/files/changes";
        String body = objectMapper.writeValueAsString(Map.of("cwd", cwd, "since", since));

        HttpResponse<String> response = sendPost(url, body);
        if (response.statusCode() != 200) {
            throw new BusinessException(
                    ErrorCode.SANDBOX_ERROR,
                    "Sidecar /files/changes 失败 (status="
                            + response.statusCode()
                            + "): "
                            + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        List<Map<String, Object>> changes = new ArrayList<>();
        JsonNode changesNode = json.has("changes") ? json.get("changes") : json;

        if (changesNode.isArray()) {
            for (JsonNode item : changesNode) {
                Map<String, Object> change = new HashMap<>();
                change.put("path", item.has("path") ? item.get("path").asText() : "");
                change.put("mtimeMs", item.has("mtimeMs") ? item.get("mtimeMs").asLong() : 0L);
                change.put("size", item.has("size") ? item.get("size").asLong() : 0L);
                change.put("ext", item.has("ext") ? item.get("ext").asText() : "");
                changes.add(change);
            }
        }
        return changes;
    }

    /**
     * 将文件路径解析为用户工作空间下的绝对路径。
     *
     * <p>沙箱内每个用户的工作目录为 /workspace/{userId}/，但前端传入的路径可能是
     * 相对路径（如 "skills/foo.html"），需要补全为 /workspace/{userId}/skills/foo.html。
     *
     * @param userId   用户 ID
     * @param filePath 原始文件路径（可能是绝对或相对路径）
     * @return 解析后的绝对路径
     */
    private String resolvePathForUser(String userId, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return filePath;
        }
        // 已经是绝对路径 → 保持原样（可能已包含 userId 或指向共享目录）
        if (filePath.startsWith("/")) {
            return filePath;
        }
        // 相对路径 → 解析到用户工作空间
        return WORKSPACE_ROOT + "/" + userId + "/" + filePath;
    }

    /**
     * 验证文件操作路径是否位于 /workspace/{userId} 目录范围内。
     * 将路径规范化后检查是否以用户专属工作空间开头，防止路径遍历和跨用户访问。
     *
     * @param userId 当前用户 ID
     * @param path   待验证的路径
     * @throws IllegalArgumentException 如果路径超出用户工作空间范围
     */
    private void validateUserPath(String userId, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        String userRoot = WORKSPACE_ROOT + "/" + userId;
        Path normalized = Paths.get(path).normalize();
        if (!normalized.startsWith(Paths.get(userRoot).normalize())) {
            throw new IllegalArgumentException("路径越界：不允许访问其他用户的工作空间");
        }
    }

    /**
     * 上传文件到远程沙箱。
     * 通过 Sidecar HTTP API POST /files/write 实现，文件内容以 base64 编码传输。
     *
     * @param userId           用户 ID
     * @param originalFilename 原始文件名
     * @param fileData         文件内容字节数组
     * @return 文件在沙箱中的绝对路径
     * @throws IOException 网络或解析异常
     */
    public String uploadFile(String userId, String originalFilename, byte[] fileData)
            throws IOException {
        String sanitized = sanitizeUploadFileName(originalFilename);
        String targetPath = WORKSPACE_ROOT + "/" + userId + "/uploads/" + sanitized;
        validateUserPath(userId, targetPath);

        String base64Content = Base64.getEncoder().encodeToString(fileData);
        String url = "http://" + getRemoteHost() + ":" + getRemotePort() + "/files/write";
        String body =
                objectMapper.writeValueAsString(
                        Map.of("path", targetPath, "content", base64Content, "encoding", "base64"));

        HttpResponse<String> response = sendPost(url, body, UPLOAD_TIMEOUT);
        if (response.statusCode() != 200) {
            throw new BusinessException(
                    ErrorCode.SANDBOX_ERROR,
                    "Sidecar /files/write 失败 (status="
                            + response.statusCode()
                            + "): "
                            + response.body());
        }
        log.info("File uploaded to sandbox: user={}, path={}", userId, targetPath);
        return targetPath;
    }

    /**
     * 清洗上传文件名：去除路径分隔符，替换非安全字符。
     */
    private static String sanitizeUploadFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "unnamed";
        }
        String baseName = originalName;
        int lastSlash = Math.max(baseName.lastIndexOf('/'), baseName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            baseName = baseName.substring(lastSlash + 1);
        }
        String sanitized = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "unnamed" : sanitized;
    }

    /**
     * 发送 POST 请求到 Sidecar（使用默认超时）。
     */
    private HttpResponse<String> sendPost(String url, String body) throws IOException {
        return sendPost(url, body, HTTP_TIMEOUT);
    }

    /**
     * 发送 POST 请求到 Sidecar。
     * 捕获网络连接异常（ConnectException、HttpConnectTimeoutException），
     * 包装为 BusinessException(SANDBOX_CONNECTION_FAILED)。
     */
    private HttpResponse<String> sendPost(String url, String body, Duration timeout)
            throws IOException {
        try {
            return httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .header("Content-Type", "application/json")
                            .timeout(timeout)
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException | HttpConnectTimeoutException e) {
            log.error("Sidecar 不可达: {}", url, e);
            throw new BusinessException(ErrorCode.SANDBOX_CONNECTION_FAILED, url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Sidecar 请求被中断: " + url, e);
        }
    }

    /**
     * 将 Sidecar 返回的文件列表转换为前端期望的树形结构。
     *
     * <p>Sidecar 格式: {"name":"src","type":"dir","children":[...]}
     * <p>前端格式: {"name":"src","path":"/workspace/src","type":"directory","children":[...]}
     *
     * @param sidecarItems Sidecar 返回的文件列表
     * @param parentPath   父目录路径
     * @return 转换后的子节点列表
     */
    private List<Map<String, Object>> convertChildren(
            List<Map<String, Object>> sidecarItems, String parentPath) {
        List<Map<String, Object>> children = new ArrayList<>();
        if (sidecarItems == null) {
            return children;
        }

        for (Map<String, Object> item : sidecarItems) {
            String name = (String) item.get("name");
            if (name != null && name.startsWith(".")) {
                continue;
            }
            String type = (String) item.get("type");
            String childPath =
                    parentPath.endsWith("/") ? parentPath + name : parentPath + "/" + name;

            Map<String, Object> node = new HashMap<>();
            node.put("name", name);
            node.put("path", childPath);

            if ("dir".equals(type)) {
                node.put("type", "directory");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> subChildren =
                        (List<Map<String, Object>>) item.get("children");
                node.put("children", convertChildren(subChildren, childPath));
            } else {
                node.put("type", "file");
                node.put("extension", getExtension(name));
                node.put("size", item.getOrDefault("size", 0L));
            }

            children.add(node);
        }
        return children;
    }

    /**
     * 从路径中提取目录名。
     */
    private static String extractDirName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = trimmed.lastIndexOf('/');
        return lastSlash >= 0 ? trimmed.substring(lastSlash + 1) : trimmed;
    }

    /**
     * 获取文件扩展名。
     */
    private static String getExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        return fileName.substring(lastDot).toLowerCase();
    }
}
