package com.alibaba.himarket.service.hicoding.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
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
                        SandboxType.REMOTE,
                        RuntimeFaultNotification.ACTION_RESTART);

        assertEquals(RuntimeFaultNotification.FAULT_PROCESS_CRASHED, notification.faultType());
        assertEquals(SandboxType.REMOTE, notification.sandboxType());
        assertEquals(RuntimeFaultNotification.ACTION_RESTART, notification.suggestedAction());
    }

    @Test
    void notification_healthCheckFailure_forK8s() {
        RuntimeFaultNotification notification =
                new RuntimeFaultNotification(
                        RuntimeFaultNotification.FAULT_HEALTH_CHECK_FAILURE,
                        SandboxType.REMOTE,
                        RuntimeFaultNotification.ACTION_RECREATE);

        assertEquals(RuntimeFaultNotification.FAULT_HEALTH_CHECK_FAILURE, notification.faultType());
        assertEquals(SandboxType.REMOTE, notification.sandboxType());
        assertEquals(RuntimeFaultNotification.ACTION_RECREATE, notification.suggestedAction());
    }

    @Test
    void notification_idleTimeout() {
        RuntimeFaultNotification notification =
                new RuntimeFaultNotification(
                        RuntimeFaultNotification.FAULT_IDLE_TIMEOUT,
                        SandboxType.REMOTE,
                        RuntimeFaultNotification.ACTION_RECREATE);

        assertEquals(RuntimeFaultNotification.FAULT_IDLE_TIMEOUT, notification.faultType());
    }

    @Test
    void notification_connectionLost() {
        RuntimeFaultNotification notification =
                new RuntimeFaultNotification(
                        RuntimeFaultNotification.FAULT_CONNECTION_LOST,
                        SandboxType.REMOTE,
                        RuntimeFaultNotification.ACTION_RECONNECT);

        assertEquals(RuntimeFaultNotification.FAULT_CONNECTION_LOST, notification.faultType());
        assertEquals(RuntimeFaultNotification.ACTION_RECONNECT, notification.suggestedAction());
    }

    @Test
    void notification_equality() {
        RuntimeFaultNotification a = new RuntimeFaultNotification("A", SandboxType.REMOTE, "X");
        RuntimeFaultNotification b = new RuntimeFaultNotification("A", SandboxType.REMOTE, "X");
        assertEquals(a, b);
    }

    @Test
    void notification_inequality_differentFaultType() {
        RuntimeFaultNotification a = new RuntimeFaultNotification("A", SandboxType.REMOTE, "X");
        RuntimeFaultNotification b = new RuntimeFaultNotification("B", SandboxType.REMOTE, "X");
        assertNotEquals(a, b);
    }

    @Test
    void notification_inequality_differentRuntimeType() {
        RuntimeFaultNotification a = new RuntimeFaultNotification("A", SandboxType.REMOTE, "X");
        RuntimeFaultNotification b =
                new RuntimeFaultNotification("A", SandboxType.OPEN_SANDBOX, "X");
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
