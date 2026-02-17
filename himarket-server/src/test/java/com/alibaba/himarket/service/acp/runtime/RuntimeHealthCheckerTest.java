package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RuntimeHealthChecker 单元测试。
 * <p>
 * 验证统一健康检查服务的注册/注销、状态查询和异常通知分发。
 * Requirements: 8.1, 8.2, 8.3, 8.5
 */
class RuntimeHealthCheckerTest {

    private RuntimeHealthChecker checker;

    @BeforeEach
    void setUp() {
        checker = new RuntimeHealthChecker();
    }

    // ===== 注册/注销测试 =====

    @Test
    void register_increasesMonitoredCount() {
        RuntimeAdapter runtime = mock(RuntimeAdapter.class);
        when(runtime.getType()).thenReturn(RuntimeType.LOCAL);

        checker.register("session-1", runtime);

        assertEquals(1, checker.getMonitoredCount());
    }

    @Test
    void unregister_decreasesMonitoredCount() {
        RuntimeAdapter runtime = mock(RuntimeAdapter.class);
        when(runtime.getType()).thenReturn(RuntimeType.LOCAL);

        checker.register("session-1", runtime);
        checker.unregister("session-1");

        assertEquals(0, checker.getMonitoredCount());
    }

    @Test
    void unregister_nonExistentSession_doesNotThrow() {
        checker.unregister("non-existent"); // should not throw
        assertEquals(0, checker.getMonitoredCount());
    }

    // ===== isAlive 测试 =====

    @Test
    void isAlive_registeredAndAlive_returnsTrue() {
        RuntimeAdapter runtime = mock(RuntimeAdapter.class);
        when(runtime.isAlive()).thenReturn(true);
        when(runtime.getType()).thenReturn(RuntimeType.LOCAL);

        checker.register("session-1", runtime);

        assertTrue(checker.isAlive("session-1"));
    }

    @Test
    void isAlive_registeredAndDead_returnsFalse() {
        RuntimeAdapter runtime = mock(RuntimeAdapter.class);
        when(runtime.isAlive()).thenReturn(false);
        when(runtime.getType()).thenReturn(RuntimeType.LOCAL);

        checker.register("session-1", runtime);

        assertFalse(checker.isAlive("session-1"));
    }

    @Test
    void isAlive_unregisteredSession_returnsFalse() {
        assertFalse(checker.isAlive("non-existent"));
    }

    // ===== getStatus 测试 =====

    @Test
    void getStatus_registeredRuntime_returnsStatus() {
        RuntimeAdapter runtime = mock(RuntimeAdapter.class);
        when(runtime.getStatus()).thenReturn(RuntimeStatus.RUNNING);
        when(runtime.getType()).thenReturn(RuntimeType.LOCAL);

        checker.register("session-1", runtime);

        assertEquals(RuntimeStatus.RUNNING, checker.getStatus("session-1"));
    }

    @Test
    void getStatus_unregisteredSession_returnsNull() {
        assertNull(checker.getStatus("non-existent"));
    }

    // ===== faultListener 注册测试（LocalRuntimeAdapter） =====

    @Test
    void register_localAdapter_registersFaultListener() {
        LocalRuntimeAdapter local = mock(LocalRuntimeAdapter.class);
        when(local.getType()).thenReturn(RuntimeType.LOCAL);

        checker.register("session-1", local);

        verify(local).setFaultListener(any());
    }

    // ===== 全局异常处理器分发测试 =====

    @Test
    void globalFaultHandler_receivesNotification_whenLocalAdapterFaults() {
        AtomicReference<String> capturedSessionId = new AtomicReference<>();
        AtomicReference<RuntimeFaultNotification> capturedNotification = new AtomicReference<>();

        checker.setGlobalFaultHandler(
                (sessionId, notification) -> {
                    capturedSessionId.set(sessionId);
                    capturedNotification.set(notification);
                });

        // 使用真实的 LocalRuntimeAdapter 来捕获 faultListener
        LocalRuntimeAdapter local = new LocalRuntimeAdapter();
        checker.register("session-1", local);

        // 手动触发 fault（模拟健康检查检测到异常后的通知）
        // 通过反射或直接调用 — 这里通过 setFaultListener 的回调链验证
        // 由于 register 已经设置了 faultListener，我们直接模拟通知
        RuntimeFaultNotification testNotification =
                new RuntimeFaultNotification(
                        RuntimeFaultNotification.FAULT_PROCESS_CRASHED,
                        RuntimeType.LOCAL,
                        RuntimeFaultNotification.ACTION_RESTART);

        // 重新设置 listener 来直接触发
        // 更好的方式：验证 register 设置的 listener 能正确转发
        // 我们通过 mock 来验证
        LocalRuntimeAdapter mockLocal = mock(LocalRuntimeAdapter.class);
        when(mockLocal.getType()).thenReturn(RuntimeType.LOCAL);

        // 捕获 register 设置的 faultListener
        AtomicReference<java.util.function.Consumer<RuntimeFaultNotification>> listenerRef =
                new AtomicReference<>();
        doAnswer(
                        invocation -> {
                            listenerRef.set(invocation.getArgument(0));
                            return null;
                        })
                .when(mockLocal)
                .setFaultListener(any());

        checker.register("session-2", mockLocal);

        // 通过捕获的 listener 触发通知
        assertNotNull(listenerRef.get());
        listenerRef.get().accept(testNotification);

        assertEquals("session-2", capturedSessionId.get());
        assertEquals(testNotification, capturedNotification.get());
    }

    @Test
    void globalFaultHandler_notSet_doesNotThrow() {
        // 不设置 globalFaultHandler，触发通知不应抛异常
        LocalRuntimeAdapter mockLocal = mock(LocalRuntimeAdapter.class);
        when(mockLocal.getType()).thenReturn(RuntimeType.LOCAL);

        AtomicReference<java.util.function.Consumer<RuntimeFaultNotification>> listenerRef =
                new AtomicReference<>();
        doAnswer(
                        invocation -> {
                            listenerRef.set(invocation.getArgument(0));
                            return null;
                        })
                .when(mockLocal)
                .setFaultListener(any());

        checker.register("session-1", mockLocal);

        // 触发通知 — 不应抛异常
        RuntimeFaultNotification notification =
                new RuntimeFaultNotification("TEST", RuntimeType.LOCAL, "TEST_ACTION");
        assertDoesNotThrow(() -> listenerRef.get().accept(notification));
    }

    // ===== 多实例管理测试 =====

    @Test
    void multipleRuntimes_trackedIndependently() {
        RuntimeAdapter runtime1 = mock(RuntimeAdapter.class);
        when(runtime1.isAlive()).thenReturn(true);
        when(runtime1.getStatus()).thenReturn(RuntimeStatus.RUNNING);
        when(runtime1.getType()).thenReturn(RuntimeType.LOCAL);

        RuntimeAdapter runtime2 = mock(RuntimeAdapter.class);
        when(runtime2.isAlive()).thenReturn(false);
        when(runtime2.getStatus()).thenReturn(RuntimeStatus.ERROR);
        when(runtime2.getType()).thenReturn(RuntimeType.K8S);

        checker.register("session-1", runtime1);
        checker.register("session-2", runtime2);

        assertEquals(2, checker.getMonitoredCount());
        assertTrue(checker.isAlive("session-1"));
        assertFalse(checker.isAlive("session-2"));
        assertEquals(RuntimeStatus.RUNNING, checker.getStatus("session-1"));
        assertEquals(RuntimeStatus.ERROR, checker.getStatus("session-2"));
    }
}
