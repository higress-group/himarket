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
 * Remote sandbox provider.
 *
 * <p>Connects to a remote Sidecar service without depending on the K8s API. The Sidecar can run in
 * a K8s Pod, Docker container, or bare-metal host as long as it is reachable. Users share one
 * Sidecar and are isolated by {@code /workspace/{userId}}.
 *
 * <p>File operations use absolute paths, following the OpenSandbox execd design. This provider
 * converts relative paths into absolute paths rooted at {@code workspacePath}, so the Sidecar does
 * not need user context.
 *
 * <p>File operations are delegated to {@link SandboxHttpClient}, and WebSocket connections reuse
 * {@link RemoteRuntimeAdapter}.
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
            throw new IllegalArgumentException("userId must not be blank");
        }
        String userId = config.userId();
        if (userId.contains("..") || userId.contains("/")) {
            throw new IllegalArgumentException("userId contains invalid characters: " + userId);
        }

        AcpProperties.RemoteConfig remoteConfig = acpProperties.getRemote();
        String host = remoteConfig.getHost();
        int port = remoteConfig.getPort();

        String workspacePath = "/workspace/" + userId;

        logger.info(
                "Acquired remote sandbox, userId={}, host={}, port={}, workspacePath={}",
                userId,
                host,
                port,
                workspacePath);

        return new SandboxInfo(
                SandboxType.REMOTE, "sandbox-remote", host, port, workspacePath, true, Map.of());
    }

    @Override
    public void release(SandboxInfo info) {
        // No-op: the remote Sidecar lifecycle is managed externally.
    }

    @Override
    public boolean healthCheck(SandboxInfo info) {
        return sandboxHttpClient.healthCheckWithLog(sidecarBaseUrl(info), info.sandboxId());
    }

    /**
     * Writes a file to the sandbox after converting the relative path to an absolute workspace path.
     *
     * <p>For example, {@code relativePath=".qwen/settings.json"} and
     * {@code workspacePath="/workspace/dev-xxx"} writes
     * {@code "/workspace/dev-xxx/.qwen/settings.json"}.
     */
    @Override
    public void writeFile(SandboxInfo info, String relativePath, String content)
            throws IOException {
        String absolutePath = toAbsolutePath(info, relativePath);
        sandboxHttpClient.writeFile(sidecarBaseUrl(info), info.sandboxId(), absolutePath, content);
    }

    /**
     * Reads a file from the sandbox after converting the relative path to an absolute workspace
     * path.
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
     * Converts a relative path to an absolute path rooted at workspacePath.
     *
     * <p>Following the OpenSandbox execd design, file operations use absolute paths built by the
     * caller, so the Sidecar no longer depends on WORKSPACE_ROOT for path resolution.
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
            // Absolute paths must stay within workspacePath.
            Path normalized = Paths.get(cleaned).normalize();
            if (!normalized.startsWith(Paths.get(wp).normalize())) {
                throw new SecurityException("Path escapes workspace: " + relativePath);
            }
            return normalized.toString();
        }
        String full = wp.endsWith("/") ? wp + cleaned : wp + "/" + cleaned;
        Path normalized = Paths.get(full).normalize();
        if (!normalized.startsWith(Paths.get(wp).normalize())) {
            throw new SecurityException("Path escapes workspace: " + relativePath);
        }
        return normalized.toString();
    }
}
