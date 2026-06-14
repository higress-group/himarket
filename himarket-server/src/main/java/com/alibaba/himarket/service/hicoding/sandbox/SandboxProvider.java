package com.alibaba.himarket.service.hicoding.sandbox;

import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeConfig;
import java.io.IOException;
import java.net.URI;

/**
 * Unified sandbox provider interface.
 *
 * <p>Abstracts differences between sandbox environments, such as local development, K8s Pods, and
 * E2B, and provides a unified operation contract for {@code SandboxInitPipeline}.
 *
 * <h3>OpenSandbox Integration Notes</h3>
 *
 * <p>To integrate <a href="https://github.com/alibaba/OpenSandbox">OpenSandbox</a>, implement this
 * interface in {@code OpenSandboxProvider}. Key adaptation points:
 *
 * <ul>
 *   <li><b>acquire()</b>: call the OpenSandbox Python FastAPI Server
 *       ({@code POST /sandboxes}) to create a sandbox instance, then wrap the returned sandboxId
 *       and host in {@link SandboxInfo}</li>
 *   <li><b>release()</b>: call {@code DELETE /sandboxes/{id}} to destroy the sandbox</li>
 *   <li><b>writeFile / readFile / healthCheck / exec</b>:
 *       OpenSandbox execd exposes compatible {@code /files/*} HTTP APIs, so
 *       {@link SandboxHttpClient} can be reused directly</li>
 *   <li><b>connectSidecar()</b>: OpenSandbox bridges CLI sessions over HTTP + SSE instead of
 *       WebSocket, so adapt {@link RuntimeAdapter#stdout()} to SSE events and implement
 *       {@code send()} with {@code HTTP POST /command}</li>
 * </ul>
 *
 * @see SandboxHttpClient reusable HTTP client compatible with OpenSandbox execd /files/* APIs
 * @see SandboxType sandbox type enum with the OPEN_SANDBOX value
 */
public interface SandboxProvider {

    /**
     * Returns the sandbox type identifier.
     */
    SandboxType getType();

    /**
     * Acquires or creates a sandbox instance.
     */
    SandboxInfo acquire(SandboxConfig config);

    /**
     * Releases sandbox resources.
     */
    void release(SandboxInfo info);

    /**
     * Checks filesystem health through the Sidecar HTTP API.
     */
    boolean healthCheck(SandboxInfo info);

    /**
     * Writes a file to the sandbox workspace through the Sidecar HTTP API.
     */
    void writeFile(SandboxInfo info, String relativePath, String content) throws IOException;

    /**
     * Reads a file from the sandbox workspace through the Sidecar HTTP API.
     */
    String readFile(SandboxInfo info, String relativePath) throws IOException;

    /**
     * Executes a command inside the sandbox.
     *
     * <p>The default implementation throws {@link UnsupportedOperationException}.
     *
     * @param info sandbox information
     * @param command command to execute
     * @param args command argument list
     * @param timeout execution timeout
     * @return command execution result
     */
    default ExecResult exec(
            SandboxInfo info,
            String command,
            java.util.List<String> args,
            java.time.Duration timeout)
            throws java.io.IOException {
        throw new UnsupportedOperationException("exec not implemented");
    }

    /**
     * Establishes a WebSocket connection to the Sidecar.
     */
    RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config);

    /**
     * Returns the Sidecar WebSocket URI without environment variables.
     */
    default URI getSidecarUri(SandboxInfo info, String command, String args) {
        return getSidecarUri(info, command, args, null);
    }

    /**
     * Returns the Sidecar WebSocket URI with environment variables.
     */
    default URI getSidecarUri(
            SandboxInfo info, String command, String args, java.util.Map<String, String> env) {
        return info.sidecarWsUri(command, args, env);
    }
}
