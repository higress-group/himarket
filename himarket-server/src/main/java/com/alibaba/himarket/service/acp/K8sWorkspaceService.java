package com.alibaba.himarket.service.acp;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.service.acp.runtime.PodEntry;
import com.alibaba.himarket.service.acp.runtime.PodReuseManager;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * K8s 沙箱文件操作服务。
 *
 * <p>封装通过 Sidecar HTTP API 操作 Pod 内文件的逻辑，
 * 供 WorkspaceController 在 runtime=k8s 时调用。
 *
 * <p>Requirements: 4.1, 4.2, 4.3, 4.4
 */
@Service
public class K8sWorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(K8sWorkspaceService.class);
    private static final int SIDECAR_PORT = 8080;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final String WORKSPACE_ROOT = "/workspace";

    private final PodReuseManager podReuseManager;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public K8sWorkspaceService(PodReuseManager podReuseManager) {
        this.podReuseManager = podReuseManager;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
        this.objectMapper = new ObjectMapper();
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
        validatePath(cwd);
        PodEntry podEntry = podReuseManager.getPodEntry(userId);
        if (podEntry == null) {
            throw new BusinessException(ErrorCode.SANDBOX_NOT_READY, userId);
        }
        String host = resolveAccessHost(podEntry);
        String url = "http://" + host + ":" + SIDECAR_PORT + "/files/list";
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
     * 读取 Pod 内的文件内容。
     *
     * @param userId   用户 ID
     * @param filePath 文件路径
     * @return 文件内容字符串
     * @throws IOException 网络或解析异常
     */
    public String readFile(String userId, String filePath) throws IOException {
        validatePath(filePath);
        PodEntry podEntry = podReuseManager.getPodEntry(userId);
        if (podEntry == null) {
            throw new BusinessException(ErrorCode.SANDBOX_NOT_READY, userId);
        }
        String host = resolveAccessHost(podEntry);
        String url = "http://" + host + ":" + SIDECAR_PORT + "/files/read";
        String body = objectMapper.writeValueAsString(Map.of("path", filePath));

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
        return json.has("content") ? json.get("content").asText() : "";
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
        validatePath(cwd);
        PodEntry podEntry = podReuseManager.getPodEntry(userId);
        if (podEntry == null) {
            throw new BusinessException(ErrorCode.SANDBOX_NOT_READY, userId);
        }
        String host = resolveAccessHost(podEntry);
        String url = "http://" + host + ":" + SIDECAR_PORT + "/files/changes";
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
     * 验证文件操作路径是否位于 /workspace 目录范围内。
     * 将路径规范化后检查是否以 /workspace 开头，防止路径遍历攻击。
     *
     * @param path 待验证的路径
     * @throws IllegalArgumentException 如果路径超出 /workspace 范围
     */
    private void validatePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("路径越界：不允许访问 /workspace 之外的目录");
        }
        Path normalized = Paths.get(WORKSPACE_ROOT).resolve(path).normalize();
        if (!normalized.startsWith(WORKSPACE_ROOT)) {
            throw new IllegalArgumentException("路径越界：不允许访问 /workspace 之外的目录");
        }
    }

    /**
     * 解析 Pod 的访问地址。
     * serviceIp 非空时优先使用，否则使用 podIp。
     *
     * @param podEntry Pod 缓存条目
     * @return 访问地址
     */
    String resolveAccessHost(PodEntry podEntry) {
        if (podEntry.getServiceIp() != null && !podEntry.getServiceIp().isBlank()) {
            return podEntry.getServiceIp();
        }
        return podEntry.getPodIp();
    }

    /**
     * 发送 POST 请求到 Sidecar。
     * 捕获网络连接异常（ConnectException、HttpConnectTimeoutException），
     * 包装为 BusinessException(SANDBOX_CONNECTION_FAILED)。
     */
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
