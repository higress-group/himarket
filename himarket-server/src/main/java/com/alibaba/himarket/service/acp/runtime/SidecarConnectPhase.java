package com.alibaba.himarket.service.acp.runtime;

import java.time.Duration;

/**
 * 建立到 Sidecar Server 的 WebSocket 连接。
 * 所有沙箱类型都通过 Sidecar WebSocket 桥接 CLI，逻辑完全一致。
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
                    "sidecar-connect", "Sidecar 连接失败: " + e.getMessage(), e, true);
        }
    }

    @Override
    public boolean verify(InitContext context) {
        RuntimeAdapter adapter = context.getRuntimeAdapter();
        return adapter != null && adapter.getStatus() == RuntimeStatus.RUNNING;
    }

    @Override
    public RetryPolicy retryPolicy() {
        return new RetryPolicy(2, Duration.ofSeconds(2), 2.0, Duration.ofSeconds(8));
    }
}
