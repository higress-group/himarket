package com.alibaba.himarket.service.acp.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
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
 * <p>HTTP 调用委托给 {@link SandboxHttpClient}，消除与 LocalSandboxProvider 的代码重复。
 *
 * <p>Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
 */
@Component
public class K8sSandboxProvider implements SandboxProvider {

    private static final Logger logger = LoggerFactory.getLogger(K8sSandboxProvider.class);
    private static final int SIDECAR_PORT = 8080;

    private final PodReuseManager podReuseManager;
    private final K8sConfigService k8sConfigService;
    private final SandboxHttpClient sandboxHttpClient;
    private final ObjectMapper objectMapper;

    public K8sSandboxProvider(
            PodReuseManager podReuseManager,
            K8sConfigService k8sConfigService,
            SandboxHttpClient sandboxHttpClient,
            ObjectMapper objectMapper) {
        this.podReuseManager = podReuseManager;
        this.k8sConfigService = k8sConfigService;
        this.sandboxHttpClient = sandboxHttpClient;
        this.objectMapper = objectMapper;
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
        sandboxHttpClient.writeFile(sidecarBaseUrl(info), info.sandboxId(), relativePath, content);
    }

    @Override
    public String readFile(SandboxInfo info, String relativePath) throws IOException {
        return sandboxHttpClient.readFile(sidecarBaseUrl(info), info.sandboxId(), relativePath);
    }

    @Override
    public boolean healthCheck(SandboxInfo info) {
        return sandboxHttpClient.healthCheckWithLog(sidecarBaseUrl(info), info.sandboxId());
    }

    @Override
    public int extractArchive(SandboxInfo info, byte[] tarGzBytes) throws IOException {
        return sandboxHttpClient.extractArchive(sidecarBaseUrl(info), info.sandboxId(), tarGzBytes);
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
