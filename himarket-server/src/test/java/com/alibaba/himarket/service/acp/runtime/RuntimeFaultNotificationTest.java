package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * RuntimeFaultNotification 单元测试。
 * <p>
 * 验证统一异常通知格式包含 faultType、runtimeType、suggestedAction 三个字段。
 * Requirements: 8.5
 */
class RuntimeFaultNotificationTest {

    @Test
    void notification_containsAllRequiredFields() {
        RuntimeFaultNotification notification =
                new RuntimeFaultNotification(
                        RuntimeFaultNotification.FAULT_PROCESS_CRASHED,
                        SandboxType.LOCAL,
                        RuntimeFaultNotification.ACTION_RESTART);

        assertEquals(RuntimeFaultNotification.FAULT_PROCESS_CRASHED, notification.faultType());
        assertEquals(SandboxType.LOCAL, notification.sandboxType());
        assertEquals(RuntimeFaultNotification.ACTION_RESTART, notification.suggestedAction());
    }

    @Test
    void notification_healthCheckFailure_forK8s() {
        RuntimeFaultNotification notification =
                new RuntimeFaultNotification(
                        RuntimeFaultNotification.FAULT_HEALTH_CHECK_FAILURE,
                        SandboxType.K8S,
                        RuntimeFaultNotification.ACTION_RECREATE);

        assertEquals(RuntimeFaultNotification.FAULT_HEALTH_CHECK_FAILURE, notification.faultType());
        assertEquals(SandboxType.K8S, notification.sandboxType());
        assertEquals(RuntimeFaultNotification.ACTION_RECREATE, notification.suggestedAction());
    }

    @Test
    void notification_idleTimeout() {
        RuntimeFaultNotification notification =
                new RuntimeFaultNotification(
                        RuntimeFaultNotification.FAULT_IDLE_TIMEOUT,
                        SandboxType.K8S,
                        RuntimeFaultNotification.ACTION_RECREATE);

        assertEquals(RuntimeFaultNotification.FAULT_IDLE_TIMEOUT, notification.faultType());
    }

    @Test
    void notification_connectionLost() {
        RuntimeFaultNotification notification =
                new RuntimeFaultNotification(
                        RuntimeFaultNotification.FAULT_CONNECTION_LOST,
                        SandboxType.K8S,
                        RuntimeFaultNotification.ACTION_RECONNECT);

        assertEquals(RuntimeFaultNotification.FAULT_CONNECTION_LOST, notification.faultType());
        assertEquals(RuntimeFaultNotification.ACTION_RECONNECT, notification.suggestedAction());
    }

    @Test
    void notification_equality() {
        RuntimeFaultNotification a = new RuntimeFaultNotification("A", SandboxType.LOCAL, "X");
        RuntimeFaultNotification b = new RuntimeFaultNotification("A", SandboxType.LOCAL, "X");
        assertEquals(a, b);
    }

    @Test
    void notification_inequality_differentFaultType() {
        RuntimeFaultNotification a = new RuntimeFaultNotification("A", SandboxType.LOCAL, "X");
        RuntimeFaultNotification b = new RuntimeFaultNotification("B", SandboxType.LOCAL, "X");
        assertNotEquals(a, b);
    }

    @Test
    void notification_inequality_differentRuntimeType() {
        RuntimeFaultNotification a = new RuntimeFaultNotification("A", SandboxType.LOCAL, "X");
        RuntimeFaultNotification b = new RuntimeFaultNotification("A", SandboxType.K8S, "X");
        assertNotEquals(a, b);
    }

    @Test
    void faultTypeConstants_areDistinct() {
        assertNotEquals(
                RuntimeFaultNotification.FAULT_PROCESS_CRASHED,
                RuntimeFaultNotification.FAULT_HEALTH_CHECK_FAILURE);
        assertNotEquals(
                RuntimeFaultNotification.FAULT_HEALTH_CHECK_FAILURE,
                RuntimeFaultNotification.FAULT_IDLE_TIMEOUT);
        assertNotEquals(
                RuntimeFaultNotification.FAULT_IDLE_TIMEOUT,
                RuntimeFaultNotification.FAULT_CONNECTION_LOST);
    }

    @Test
    void actionConstants_areDistinct() {
        assertNotEquals(
                RuntimeFaultNotification.ACTION_RECONNECT, RuntimeFaultNotification.ACTION_RESTART);
        assertNotEquals(
                RuntimeFaultNotification.ACTION_RESTART, RuntimeFaultNotification.ACTION_RECREATE);
    }
}
