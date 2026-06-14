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
 * Remote sandbox file operation service.
 *
 * <p>Encapsulates file operations in remote sandboxes through the Sidecar HTTP API. Used by
 * WorkspaceController when runtime=remote.
 *
 * <p>Uses AcpProperties.remote to resolve the Sidecar address.
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
     * Returns the remote Sidecar host.
     */
    private String getRemoteHost() {
        return acpProperties.getRemote().getHost();
    }

    private int getRemotePort() {
        return acpProperties.getRemote().getPort();
    }

    /**
     * Gets the directory tree inside the sandbox.
     * Calls Sidecar HTTP API /files/list and converts the response to the frontend tree shape.
     *
     * @param userId user ID
     * @param cwd working directory path
     * @param depth directory depth
     * @return frontend tree map
     * @throws IOException on network or parsing failures
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
                    "Sidecar /files/list failed (status="
                            + response.statusCode()
                            + "): "
                            + response.body());
        }

        // Sidecar response: [{"name":"src","type":"dir","children":[...]}].
        List<Map<String, Object>> sidecarItems =
                objectMapper.readValue(response.body(), new TypeReference<>() {});

        // Convert to the frontend tree shape with cwd as the root node.
        Map<String, Object> root = new HashMap<>();
        root.put("name", extractDirName(cwd));
        root.put("path", cwd);
        root.put("type", "directory");
        root.put("children", convertChildren(sidecarItems, cwd));
        return root;
    }

    /**
     * Reads file content from the sandbox as UTF-8 text.
     *
     * @param userId user ID
     * @param filePath file path
     * @return file content
     * @throws IOException on network or parsing failures
     */
    public String readFile(String userId, String filePath) throws IOException {
        return readFileWithEncoding(userId, filePath, "utf-8").get("content").toString();
    }

    /**
     * Reads file content from the sandbox with a specified encoding.
     *
     * @param userId user ID
     * @param filePath file path
     * @param encoding encoding, either "utf-8" or "base64"
     * @return map containing content and encoding
     * @throws IOException on network or parsing failures
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
                    "Sidecar /files/read failed (status="
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
     * Downloads raw file bytes from the remote sandbox.
     * Tries Sidecar GET /files/download first, then falls back to POST /files/read with base64
     * decoding on 404.
     */
    public byte[] readFileBytes(String userId, String filePath) throws IOException {
        String resolved = resolvePathForUser(userId, filePath);
        String host = getRemoteHost();

        // Option 1: GET /files/download returns raw bytes without JSON wrapping.
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
            log.error(
                    "Sidecar file download endpoint unreachable, userId={}, path={},"
                            + " errorMessage={}",
                    userId,
                    filePath,
                    e.getMessage(),
                    e);
            throw new BusinessException(ErrorCode.SANDBOX_CONNECTION_FAILED, "Sidecar service", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Sidecar file download request interrupted", e);
        }

        // Option 2: POST /files/read with base64 decoding.
        Map<String, Object> result = readFileWithEncoding(userId, filePath, "base64");
        String content = result.get("content").toString();
        String encoding = result.get("encoding").toString();
        if ("base64".equals(encoding)) {
            // Remove whitespace and fix padding before decoding.
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
     * Gets file changes from the sandbox.
     *
     * @param userId user ID
     * @param cwd working directory path
     * @param since timestamp in milliseconds; changes after this time are returned
     * @return file changes, each containing path, mtimeMs, size, and ext
     * @throws IOException on network or parsing failures
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
                    "Sidecar /files/changes failed (status="
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
     * Resolves a file path to an absolute path under the user's workspace.
     *
     * <p>Each user works under /workspace/{userId}/ in the sandbox. Frontend paths may be relative,
     * such as "skills/foo.html", and are completed to /workspace/{userId}/skills/foo.html.
     *
     * @param userId user ID
     * @param filePath original file path, either absolute or relative
     * @return resolved absolute path
     */
    private String resolvePathForUser(String userId, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return filePath;
        }
        // Absolute paths are passed through as-is.
        if (filePath.startsWith("/")) {
            return filePath;
        }
        // Relative paths resolve under the user's workspace.
        return WORKSPACE_ROOT + "/" + userId + "/" + filePath;
    }

    /**
     * Validates that a file operation path stays under /workspace/{userId}.
     * Normalizes the path before checking the user workspace prefix to prevent traversal and
     * cross-user access.
     *
     * @param userId current user ID
     * @param path path to validate
     * @throws IllegalArgumentException when the path escapes the user workspace
     */
    private void validateUserPath(String userId, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path must not be empty");
        }
        String userRoot = WORKSPACE_ROOT + "/" + userId;
        Path normalized = Paths.get(path).normalize();
        if (!normalized.startsWith(Paths.get(userRoot).normalize())) {
            throw new IllegalArgumentException(
                    "Path escapes the user workspace; cross-user access is not allowed");
        }
    }

    /**
     * Uploads a file to the remote sandbox.
     * Uses Sidecar HTTP API POST /files/write and sends content as base64.
     *
     * @param userId user ID
     * @param originalFilename original file name
     * @param fileData file content bytes
     * @return absolute file path in the sandbox
     * @throws IOException on network or parsing failures
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
                    "Sidecar /files/write failed (status="
                            + response.statusCode()
                            + "): "
                            + response.body());
        }
        log.info("File uploaded to sandbox, userId={}, path={}", userId, targetPath);
        return targetPath;
    }

    /**
     * Sanitizes an uploaded file name by stripping path separators and replacing unsafe characters.
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
     * Sends a POST request to Sidecar with the default timeout.
     */
    private HttpResponse<String> sendPost(String url, String body) throws IOException {
        return sendPost(url, body, HTTP_TIMEOUT);
    }

    /**
     * Sends a POST request to Sidecar.
     * Wraps connection failures as BusinessException(SANDBOX_CONNECTION_FAILED).
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
            log.error(
                    "Sidecar POST endpoint unreachable, url={}, errorMessage={}",
                    url,
                    e.getMessage(),
                    e);
            throw new BusinessException(ErrorCode.SANDBOX_CONNECTION_FAILED, "Sidecar service", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Sidecar POST request interrupted", e);
        }
    }

    /**
     * Converts Sidecar file list output to the frontend tree shape.
     *
     * <p>Sidecar format: {"name":"src","type":"dir","children":[...]}
     * <p>Frontend format: {"name":"src","path":"/workspace/src","type":"directory","children":[...]}
     *
     * @param sidecarItems file list returned by Sidecar
     * @param parentPath parent directory path
     * @return converted child nodes
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
     * Extracts a directory name from a path.
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
     * Gets the file extension.
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
