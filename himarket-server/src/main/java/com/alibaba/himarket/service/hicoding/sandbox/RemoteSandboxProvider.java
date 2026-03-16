package com.alibaba.himarket.service.hicoding.sandbox;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.service.hicoding.runtime.RemoteRuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeConfig;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 远程沙箱提供者。
 *
 * <p>连接远程 Sidecar 服务，不依赖 K8s API。 Sidecar 可以部署在 K8s Pod、Docker 容器或裸机上，只要地址可达即可。
 * 所有用户共用同一个 Sidecar，通过 {@code /workspace/{userId}} 实现工作目录隔离。
 *
 * <p>文件操作使用绝对路径（参考 OpenSandbox execd 设计），由本 Provider 负责将相对路径
 * 转换为基于 {@code workspacePath} 的绝对路径，Sidecar 端不再需要知道用户上下文。
 *
 * <p>文件操作委托给 {@link SandboxHttpClient}，WebSocket 连接复用 {@link RemoteRuntimeAdapter}。
 */
@Component
public class RemoteSandboxProvider implements SandboxProvider {

    private static final Logger logger = LoggerFactory.getLogger(RemoteSandboxProvider.class);

    private final SandboxHttpClient sandboxHttpClient;
    private final AcpProperties acpProperties;

    public RemoteSandboxProvider(SandboxHttpClient sandboxHttpClient, AcpProperties acpProperties) {
        this.sandboxHttpClient = sandboxHttpClient;
        this.acpProperties = acpProperties;
    }

    @Override
    public SandboxType getType() {
        return SandboxType.REMOTE;
    }

    @Override
    public SandboxInfo acquire(SandboxConfig config) {
        if (config.userId() == null || config.userId().isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        String userId = config.userId();
        if (userId.contains("..") || userId.contains("/")) {
            throw new IllegalArgumentException("userId 包含非法字符: " + userId);
        }

        AcpProperties.RemoteConfig remoteConfig = acpProperties.getRemote();
        String host = remoteConfig.getHost();
        int port = remoteConfig.getPort();

        String workspacePath = "/workspace/" + userId;

        logger.info(
                "[RemoteSandboxProvider] acquire: userId={}, host={}:{}, workspacePath={}",
                userId,
                host,
                port,
                workspacePath);

        return new SandboxInfo(
                SandboxType.REMOTE, "sandbox-remote", host, port, workspacePath, true, Map.of());
    }

    @Override
    public void release(SandboxInfo info) {
        // 空操作：远程 Sidecar 生命周期由外部管理
    }

    @Override
    public boolean healthCheck(SandboxInfo info) {
        return sandboxHttpClient.healthCheckWithLog(sidecarBaseUrl(info), info.sandboxId());
    }

    /**
     * 写入文件到沙箱。将相对路径转换为基于 workspacePath 的绝对路径。
     *
     * <p>例如：relativePath=".qwen/settings.json", workspacePath="/workspace/dev-xxx"
     * → 实际写入 "/workspace/dev-xxx/.qwen/settings.json"
     */
    @Override
    public void writeFile(SandboxInfo info, String relativePath, String content)
            throws IOException {
        String absolutePath = toAbsolutePath(info, relativePath);
        sandboxHttpClient.writeFile(sidecarBaseUrl(info), info.sandboxId(), absolutePath, content);
    }

    /**
     * 从沙箱读取文件。将相对路径转换为基于 workspacePath 的绝对路径。
     */
    @Override
    public String readFile(SandboxInfo info, String relativePath) throws IOException {
        String absolutePath = toAbsolutePath(info, relativePath);
        return sandboxHttpClient.readFile(sidecarBaseUrl(info), info.sandboxId(), absolutePath);
    }

    @Override
    public ExecResult exec(
            SandboxInfo info,
            String command,
            java.util.List<String> args,
            java.time.Duration timeout)
            throws IOException {
        return sandboxHttpClient.exec(
                sidecarBaseUrl(info), info.sandboxId(), command, args, timeout);
    }

    @Override
    public RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config) {
        RemoteRuntimeAdapter adapter = new RemoteRuntimeAdapter(info.host(), info.sidecarPort());

        String command = config.getCommand();
        String args = config.getArgs() != null ? String.join(" ", config.getArgs()) : null;

        URI wsUri =
                info.sidecarWsUri(
                        command, args != null ? args : "", config.getEnv(), info.workspacePath());

        adapter.connect(wsUri);
        return adapter;
    }

    private String sidecarBaseUrl(SandboxInfo info) {
        return "http://" + info.host() + ":" + info.sidecarPort();
    }

    /**
     * 将相对路径转换为基于 workspacePath 的绝对路径。
     *
     * <p>参考 OpenSandbox execd 设计：文件操作使用绝对路径，由调用方负责构建。
     * Sidecar 端不再依赖 WORKSPACE_ROOT 做路径解析。
     */
    private String toAbsolutePath(SandboxInfo info, String relativePath) {
        String wp = info.workspacePath();
        if (wp == null || wp.isEmpty()) {
            return relativePath;
        }
        String cleaned = relativePath;
        if (cleaned.startsWith("./")) {
            cleaned = cleaned.substring(2);
        } else if (cleaned.startsWith("/")) {
            // 绝对路径：校验是否在 workspacePath 范围内
            Path normalized = Paths.get(cleaned).normalize();
            if (!normalized.startsWith(Paths.get(wp).normalize())) {
                throw new SecurityException("路径越界: " + relativePath);
            }
            return normalized.toString();
        }
        String full = wp.endsWith("/") ? wp + cleaned : wp + "/" + cleaned;
        Path normalized = Paths.get(full).normalize();
        if (!normalized.startsWith(Paths.get(wp).normalize())) {
            throw new SecurityException("路径越界: " + relativePath);
        }
        return normalized.toString();
    }
}
