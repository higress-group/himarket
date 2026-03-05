package com.alibaba.himarket.service.acp.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * K8s Pod 沙箱提供者。
 *
 * <p>复用现有的 PodReuseManager 管理 Pod 生命周期。 文件操作统一通过 Pod 内 Sidecar 的 HTTP API 完成，
 * 不再使用 kubectl exec 写文件，消除了 exec 通道的不稳定性。
 *
 * <p>Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
 */
@Component
public class K8sSandboxProvider implements SandboxProvider {

    private static final Logger logger = LoggerFactory.getLogger(K8sSandboxProvider.class);
    private static final int SIDECAR_PORT = 8080;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final PodReuseManager podReuseManager;
    private final K8sConfigService k8sConfigService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public K8sSandboxProvider(PodReuseManager podReuseManager, K8sConfigService k8sConfigService) {
        this.podReuseManager = podReuseManager;
        this.k8sConfigService = k8sConfigService;
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(HTTP_TIMEOUT)
                        .version(HttpClient.Version.HTTP_1_1)
                        .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public SandboxType getType() {
        return SandboxType.K8S;
    }

    @Override
    public SandboxInfo acquire(SandboxConfig config) {
        RuntimeConfig runtimeConfig = toRuntimeConfig(config);
        PodInfo podInfo = podReuseManager.acquirePod(config.userId(), runtimeConfig);

        String accessHost =
                podInfo.serviceIp() != null && !podInfo.serviceIp().isBlank()
                        ? podInfo.serviceIp()
                        : podInfo.podIp();

        return new SandboxInfo(
                SandboxType.K8S,
                podInfo.podName(),
                accessHost,
                SIDECAR_PORT,
                "/workspace",
                podInfo.reused(),
                Map.of(
                        "podName",
                        podInfo.podName(),
                        "namespace",
                        podReuseManager.getNamespace(),
                        "podIp",
                        podInfo.podIp() != null ? podInfo.podIp() : "",
                        "k8sConfigId",
                        config.k8sConfigId() != null ? config.k8sConfigId() : "default"));
    }

    @Override
    public void release(SandboxInfo info) {
        // Pod 生命周期与 WebSocket 连接无关，不做任何清理
    }

    @Override
    public void writeFile(SandboxInfo info, String relativePath, String content)
            throws IOException {
        String url = sidecarBaseUrl(info) + "/files/write";
        String body =
                objectMapper.writeValueAsString(Map.of("path", relativePath, "content", content));
        try {
            HttpResponse<String> response =
                    httpClient.send(
                            HttpRequest.newBuilder(URI.create(url))
                                    .POST(HttpRequest.BodyPublishers.ofString(body))
                                    .header("Content-Type", "application/json")
                                    .timeout(HTTP_TIMEOUT)
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException(
                        "Sidecar writeFile 失败 (Pod: " + info.sandboxId() + "): " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Sidecar writeFile 被中断 (Pod: " + info.sandboxId() + ")", e);
        }
    }

    @Override
    public String readFile(SandboxInfo info, String relativePath) throws IOException {
        String url = sidecarBaseUrl(info) + "/files/read";
        String body = objectMapper.writeValueAsString(Map.of("path", relativePath));
        try {
            HttpResponse<String> response =
                    httpClient.send(
                            HttpRequest.newBuilder(URI.create(url))
                                    .POST(HttpRequest.BodyPublishers.ofString(body))
                                    .header("Content-Type", "application/json")
                                    .timeout(HTTP_TIMEOUT)
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException(
                        "Sidecar readFile 失败 (Pod: " + info.sandboxId() + "): " + response.body());
            }
            return objectMapper.readTree(response.body()).get("content").asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Sidecar readFile 被中断 (Pod: " + info.sandboxId() + ")", e);
        }
    }

    @Override
    public boolean healthCheck(SandboxInfo info) {
        // HTTP 健康检查：通过 Sidecar 的 /health 端点验证 sidecar 真正可达。
        // 仅 TCP 探测不够——当通过 LoadBalancer Service 访问时，TCP 连接到 SLB 会成功，
        // 但 SLB 后端健康检查可能尚未通过，导致后续 HTTP 请求收到 SLB 的 404。
        String url = sidecarBaseUrl(info) + "/health";
        try {
            HttpResponse<String> response =
                    httpClient.send(
                            HttpRequest.newBuilder(URI.create(url))
                                    .GET()
                                    .timeout(Duration.ofSeconds(5))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return true;
            }
            logger.warn(
                    "[K8sSandboxProvider] healthCheck HTTP 非 200 (Pod: {}, host: {}, status: {},"
                            + " body: {})",
                    info.sandboxId(),
                    info.host(),
                    response.statusCode(),
                    response.body());
            return false;
        } catch (Exception e) {
            logger.warn(
                    "[K8sSandboxProvider] healthCheck HTTP 请求失败 (Pod: {}, host: {}, port: {}): {}"
                            + " - {}",
                    info.sandboxId(),
                    info.host(),
                    info.sidecarPort(),
                    e.getClass().getSimpleName(),
                    e.getMessage() != null ? e.getMessage() : e.toString());
            return false;
        }
    }

    @Override
    public int extractArchive(SandboxInfo info, byte[] tarGzBytes) throws IOException {
        String url = sidecarBaseUrl(info) + "/files/extract";
        try {
            HttpResponse<String> response =
                    httpClient.send(
                            HttpRequest.newBuilder(URI.create(url))
                                    .POST(HttpRequest.BodyPublishers.ofByteArray(tarGzBytes))
                                    .header("Content-Type", "application/gzip")
                                    .timeout(Duration.ofSeconds(30))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException(
                        "Sidecar extractArchive 失败 (Pod: "
                                + info.sandboxId()
                                + ", url: "
                                + url
                                + ", status: "
                                + response.statusCode()
                                + "): "
                                + response.body());
            }
            return objectMapper.readTree(response.body()).get("fileCount").asInt();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Sidecar extractArchive 被中断 (Pod: " + info.sandboxId() + ")", e);
        }
    }

    @Override
    public RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config) {
        String namespace =
                info.metadata().getOrDefault("namespace", podReuseManager.getNamespace());
        K8sRuntimeAdapter adapter =
                new K8sRuntimeAdapter(
                        k8sConfigService.getClient(
                                info.metadata().getOrDefault("k8sConfigId", "default")),
                        namespace);
        adapter.setReuseMode(true);

        String command = config.getCommand();
        String args = config.getArgs() != null ? String.join(" ", config.getArgs()) : null;

        // 构建 WebSocket URI，将 RuntimeConfig 中的环境变量通过 query param 传递给 Sidecar，
        // 以便 Sidecar 在 spawn CLI 子进程时注入（解决 Pod 复用时环境变量无法动态更新的问题）
        URI baseUri = info.sidecarWsUri(command, args != null ? args : "");
        URI wsUri = baseUri;
        if (config.getEnv() != null && !config.getEnv().isEmpty()) {
            try {
                String envJson = objectMapper.writeValueAsString(config.getEnv());
                String encodedEnv =
                        java.net.URLEncoder.encode(
                                envJson, java.nio.charset.StandardCharsets.UTF_8);
                String separator = baseUri.getRawQuery() != null ? "&" : "?";
                wsUri = URI.create(baseUri.toString() + separator + "env=" + encodedEnv);
                logger.info(
                        "[K8sSandboxProvider] 环境变量已添加到 WebSocket URI: {} vars",
                        config.getEnv().size());
            } catch (Exception e) {
                logger.warn("[K8sSandboxProvider] 序列化 env 失败，跳过环境变量传递: {}", e.getMessage());
            }
        }

        PodInfo podInfo =
                new PodInfo(
                        info.sandboxId(),
                        info.metadata().getOrDefault("podIp", info.host()),
                        info.host().equals(info.metadata().getOrDefault("podIp", ""))
                                ? null
                                : info.host(),
                        wsUri,
                        info.reused());

        adapter.prepareForExistingPod(podInfo, config);
        adapter.connectAndStart();
        return adapter;
    }

    @Override
    public URI getSidecarUri(SandboxInfo info, String command, String args) {
        return info.sidecarWsUri(command, args);
    }

    private String sidecarBaseUrl(SandboxInfo info) {
        return "http://" + info.host() + ":" + info.sidecarPort();
    }

    private RuntimeConfig toRuntimeConfig(SandboxConfig config) {
        RuntimeConfig rc = new RuntimeConfig();
        rc.setUserId(config.userId());
        rc.setK8sConfigId(config.k8sConfigId());
        rc.setCwd(config.workspacePath());
        if (config.env() != null) {
            rc.setEnv(config.env());
        }
        return rc;
    }
}
