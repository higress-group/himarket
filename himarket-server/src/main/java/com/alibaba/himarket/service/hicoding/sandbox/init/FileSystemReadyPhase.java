package com.alibaba.himarket.service.hicoding.sandbox.init;

import com.alibaba.himarket.service.hicoding.sandbox.SandboxInfo;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies that the sandbox filesystem is accessible.
 *
 * <p>Uses {@link SandboxProvider#healthCheck(SandboxInfo)} for provider-neutral validation. When
 * the health check fails, the error includes host:port to help diagnose sandbox connectivity
 * issues quickly. The phase uses a fail-fast strategy to avoid long waits when the sandbox is
 * unreachable.
 */
public class FileSystemReadyPhase implements InitPhase {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemReadyPhase.class);

    @Override
    public String name() {
        return "filesystem-ready";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public boolean shouldExecute(InitContext context) {
        return true;
    }

    @Override
    public void execute(InitContext context) throws InitPhaseException {
        try {
            SandboxProvider provider = context.getProvider();
            SandboxInfo info = context.getSandboxInfo();
            boolean healthy = provider.healthCheck(info);
            if (!healthy) {
                String detail = buildConnectivityErrorMessage(info);
                throw new InitPhaseException("filesystem-ready", detail, true);
            }
            logger.info("Filesystem health check passed, sandboxId={}", info.sandboxId());
        } catch (InitPhaseException e) {
            throw e;
        } catch (Exception e) {
            throw new InitPhaseException(
                    "filesystem-ready",
                    "Filesystem health check failed: " + e.getMessage(),
                    e,
                    true);
        }
    }

    @Override
    public boolean verify(InitContext context) {
        return true;
    }

    /**
     * Does not retry; failures return immediately.
     */
    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.none();
    }

    /**
     * Builds a friendly error message with connection details.
     */
    private String buildConnectivityErrorMessage(SandboxInfo info) {
        String host = info.host();
        int port = info.sidecarPort();
        return String.format(
                "Sandbox %s (%s:%d) is unreachable. Check that the Sidecar service is running and"
                        + " the address and port are correct.",
                info.sandboxId(), host, port);
    }
}
