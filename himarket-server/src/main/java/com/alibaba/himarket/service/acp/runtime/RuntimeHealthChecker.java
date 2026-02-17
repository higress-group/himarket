package com.alibaba.himarket.service.acp.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一的运行时健康检查服务。
 * <p>
 * 管理所有活跃运行时实例的健康状态监控，提供统一的异常通知分发机制。
 * 各 RuntimeAdapter 实现自身的健康检查逻辑，本服务负责注册/注销实例
 * 并将异常通知转发给上层（如 AcpWebSocketHandler）。
 * <p>
 * Requirements: 8.1, 8.2, 8.3, 8.5
 */
public class RuntimeHealthChecker {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeHealthChecker.class);

    /** sessionId → RuntimeAdapter 映射 */
    private final Map<String, RuntimeAdapter> monitoredRuntimes = new ConcurrentHashMap<>();

    /** 全局异常通知回调：(sessionId, notification) */
    private volatile BiConsumer<String, RuntimeFaultNotification> globalFaultHandler;

    /**
     * 设置全局异常通知处理器。
     * <p>
     * 当任何被监控的运行时实例发生异常时，通过此处理器通知上层。
     *
     * @param handler 接收 (sessionId, RuntimeFaultNotification) 的处理器
     */
    public void setGlobalFaultHandler(BiConsumer<String, RuntimeFaultNotification> handler) {
        this.globalFaultHandler = handler;
    }

    /**
     * 注册运行时实例进行健康监控。
     * <p>
     * 根据运行时类型自动注册对应的 faultListener，将异常通知转发到全局处理器。
     *
     * @param sessionId 会话 ID
     * @param runtime   运行时适配器实例
     */
    public void register(String sessionId, RuntimeAdapter runtime) {
        monitoredRuntimes.put(sessionId, runtime);

        // 为各类型适配器注册 faultListener，统一转发到全局处理器
        if (runtime instanceof LocalRuntimeAdapter local) {
            local.setFaultListener(notification -> dispatchFault(sessionId, notification));
        } else if (runtime instanceof K8sRuntimeAdapter k8s) {
            k8s.setFaultListener(notification -> dispatchFault(sessionId, notification));
        }

        logger.debug(
                "Registered runtime for health monitoring: sessionId={}, type={}",
                sessionId,
                runtime.getType());
    }

    /**
     * 注销运行时实例，停止健康监控。
     *
     * @param sessionId 会话 ID
     */
    public void unregister(String sessionId) {
        RuntimeAdapter removed = monitoredRuntimes.remove(sessionId);
        if (removed != null) {
            logger.debug("Unregistered runtime from health monitoring: sessionId={}", sessionId);
        }
    }

    /**
     * 检查指定会话的运行时是否存活。
     *
     * @param sessionId 会话 ID
     * @return true 表示存活，false 表示不存活或未注册
     */
    public boolean isAlive(String sessionId) {
        RuntimeAdapter runtime = monitoredRuntimes.get(sessionId);
        return runtime != null && runtime.isAlive();
    }

    /**
     * 获取指定会话的运行时状态。
     *
     * @param sessionId 会话 ID
     * @return 运行时状态，未注册时返回 null
     */
    public RuntimeStatus getStatus(String sessionId) {
        RuntimeAdapter runtime = monitoredRuntimes.get(sessionId);
        return runtime != null ? runtime.getStatus() : null;
    }

    /**
     * 获取当前被监控的运行时实例数量。
     *
     * @return 实例数量
     */
    public int getMonitoredCount() {
        return monitoredRuntimes.size();
    }

    private void dispatchFault(String sessionId, RuntimeFaultNotification notification) {
        logger.warn(
                "Runtime fault detected: sessionId={}, faultType={}, runtimeType={},"
                        + " suggestedAction={}",
                sessionId,
                notification.faultType(),
                notification.runtimeType(),
                notification.suggestedAction());

        BiConsumer<String, RuntimeFaultNotification> handler = globalFaultHandler;
        if (handler != null) {
            try {
                handler.accept(sessionId, notification);
            } catch (Exception e) {
                logger.error(
                        "Error dispatching fault notification for session {}: {}",
                        sessionId,
                        e.getMessage());
            }
        }
    }
}
