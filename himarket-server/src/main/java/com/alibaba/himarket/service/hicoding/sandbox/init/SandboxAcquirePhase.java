package com.alibaba.himarket.service.hicoding.sandbox.init;

import com.alibaba.himarket.service.hicoding.sandbox.SandboxInfo;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxProvider;

/**
 * Acquires a sandbox instance.
 *
 * <p>Delegates to {@link SandboxProvider#acquire} so this phase does not depend on concrete
 * provider implementations.
 */
public class SandboxAcquirePhase implements InitPhase {

    @Override
    public String name() {
        return "sandbox-acquire";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean shouldExecute(InitContext context) {
        return true;
    }

    @Override
    public void execute(InitContext context) throws InitPhaseException {
        try {
            SandboxProvider provider = context.getProvider();
            SandboxInfo info = provider.acquire(context.getSandboxConfig());
            context.setSandboxInfo(info);
        } catch (Exception e) {
            throw new InitPhaseException(
                    "sandbox-acquire", "Failed to acquire sandbox: " + e.getMessage(), e, false);
        }
    }

    @Override
    public boolean verify(InitContext context) {
        return context.getSandboxInfo() != null
                && context.getSandboxInfo().host() != null
                && !context.getSandboxInfo().host().isBlank();
    }

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.none();
    }
}
