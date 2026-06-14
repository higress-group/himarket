package com.alibaba.himarket.service.hicoding.sandbox;

import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeConfig;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpenSandbox provider placeholder.
 *
 * <p>Reserves the OpenSandbox integration surface. Current operations throw
 * {@link UnsupportedOperationException}. Registered in the Spring container only when
 * {@code acp.open-sandbox.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "acp.open-sandbox.enabled", havingValue = "true")
public class OpenSandboxProvider implements SandboxProvider {

    @Override
    public SandboxType getType() {
        return SandboxType.OPEN_SANDBOX;
    }

    @Override
    public SandboxInfo acquire(SandboxConfig config) {
        throw new UnsupportedOperationException("OpenSandbox is not implemented yet");
    }

    @Override
    public void release(SandboxInfo info) {
        // No-op placeholder.
    }

    @Override
    public boolean healthCheck(SandboxInfo info) {
        return false;
    }

    @Override
    public void writeFile(SandboxInfo info, String relativePath, String content)
            throws IOException {
        throw new UnsupportedOperationException("OpenSandbox is not implemented yet");
    }

    @Override
    public String readFile(SandboxInfo info, String relativePath) throws IOException {
        throw new UnsupportedOperationException("OpenSandbox is not implemented yet");
    }

    @Override
    public RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config) {
        throw new UnsupportedOperationException("OpenSandbox is not implemented yet");
    }
}
