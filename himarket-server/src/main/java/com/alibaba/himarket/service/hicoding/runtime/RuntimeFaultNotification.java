package com.alibaba.himarket.service.hicoding.runtime;

import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;

/**
 * 统一的运行时异常通知格式。
 * <p>
 * 当运行时实例发生异常（健康检查失败、进程崩溃、通信中断等）时，
 * 通过此记录向上层发送标准化的故障通知。
 *
 * @param faultType       故障类型（如 PROCESS_CRASHED、HEALTH_CHECK_FAILURE、IDLE_TIMEOUT、CONNECTION_LOST）
 * @param sandboxType     沙箱类型（LOCAL、K8S）
 * @param suggestedAction 建议操作（如 RECONNECT、RESTART、DESTROY）
 *
 * Requirements: 8.5
 */
public record RuntimeFaultNotification(
        String faultType, SandboxType sandboxType, String suggestedAction) {

    /** 进程崩溃 */
    public static final String FAULT_PROCESS_CRASHED = "PROCESS_CRASHED";

    /** 健康检查失败 */
    public static final String FAULT_HEALTH_CHECK_FAILURE = "HEALTH_CHECK_FAILURE";

    /** 空闲超时 */
    public static final String FAULT_IDLE_TIMEOUT = "IDLE_TIMEOUT";

    /** 连接丢失 */
    public static final String FAULT_CONNECTION_LOST = "CONNECTION_LOST";

    /** 建议重新连接 */
    public static final String ACTION_RECONNECT = "RECONNECT";

    /** 建议重新启动 */
    public static final String ACTION_RESTART = "RESTART";

    /** 已销毁，需重新创建 */
    public static final String ACTION_RECREATE = "RECREATE";
}
