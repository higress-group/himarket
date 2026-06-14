package com.alibaba.himarket.service.hicoding.runtime;

import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;

/**
 * Standard runtime fault notification format.
 * <p>
 * Sends standardized fault notifications to upper layers when a runtime instance fails, such as
 * health check failure, process crash, or communication interruption.
 *
 * @param faultType fault type, such as PROCESS_CRASHED, HEALTH_CHECK_FAILURE, IDLE_TIMEOUT, or
 *     CONNECTION_LOST
 * @param sandboxType sandbox type, such as LOCAL or K8S
 * @param suggestedAction suggested action, such as RECONNECT, RESTART, or DESTROY
 *
 * Requirements: 8.5
 */
public record RuntimeFaultNotification(
        String faultType, SandboxType sandboxType, String suggestedAction) {

    /**
     * Process crashed.
     */
    public static final String FAULT_PROCESS_CRASHED = "PROCESS_CRASHED";

    /**
     * Health check failed.
     */
    public static final String FAULT_HEALTH_CHECK_FAILURE = "HEALTH_CHECK_FAILURE";

    /**
     * Runtime idle timeout.
     */
    public static final String FAULT_IDLE_TIMEOUT = "IDLE_TIMEOUT";

    /**
     * Connection lost.
     */
    public static final String FAULT_CONNECTION_LOST = "CONNECTION_LOST";

    /**
     * Suggest reconnecting.
     */
    public static final String ACTION_RECONNECT = "RECONNECT";

    /**
     * Suggest restarting.
     */
    public static final String ACTION_RESTART = "RESTART";

    /**
     * Suggest recreating the runtime.
     */
    public static final String ACTION_RECREATE = "RECREATE";
}
