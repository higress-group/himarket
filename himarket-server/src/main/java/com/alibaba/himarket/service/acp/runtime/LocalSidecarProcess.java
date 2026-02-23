package com.alibaba.himarket.service.acp.runtime;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 本地 Sidecar 进程封装。
 * 封装 Process 实例和端口信息，提供进程存活检查和优雅停止能力。
 */
public record LocalSidecarProcess(Process process, int port, Instant startedAt) {

    /** 检查 Sidecar 进程是否存活 */
    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    /**
     * 优雅停止 Sidecar 进程。
     * 先 destroy()，等待 5 秒，如果仍未退出则 destroyForcibly()。
     */
    public void stop() {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
