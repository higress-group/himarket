package com.alibaba.himarket.service.hicoding.sandbox.init;

import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeStatus;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxProvider;

/**
 * Establishes the WebSocket connection to the Sidecar Server.
 *
 * <p>All sandbox types bridge the CLI through the Sidecar WebSocket, so the logic is shared.
 */
public class SidecarConnectPhase implements InitPhase {

    @Override
    public String name() {
        return "sidecar-connect";
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public boolean shouldExecute(InitContext context) {
        return true;
    }

    @Override
    public void execute(InitContext context) throws InitPhaseException {
        try {
            SandboxProvider provider = context.getProvider();
            RuntimeAdapter adapter =
                    provider.connectSidecar(context.getSandboxInfo(), context.getRuntimeConfig());
            context.setRuntimeAdapter(adapter);
        } catch (Exception e) {
            throw new InitPhaseException(
                    "sidecar-connect",
                    "Failed to connect to Sidecar service: " + e.getMessage(),
                    e,
                    true);
        }
    }

    @Override
    public boolean verify(InitContext context) {
        RuntimeAdapter adapter = context.getRuntimeAdapter();
        return adapter != null && adapter.getStatus() == RuntimeStatus.RUNNING;
    }

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.none();
    }
}
