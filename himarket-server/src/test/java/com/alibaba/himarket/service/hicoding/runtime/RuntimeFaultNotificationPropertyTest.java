package com.alibaba.himarket.service.hicoding.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import net.jqwik.api.*;

/**
 * 运行时异常通知一致性属性测试。
 *
 * <p>Feature: sandbox-runtime-strategy, Property 9: 运行时异常通知一致性
 *
 * <p><b>Validates: Requirements 7.6, 8.5</b>
 *
 * <p>对于任意运行时类型，当运行时实例状态变为异常（通信中断或健康检查失败）时，
 * 发送给客户端的通知应该包含故障类型（faultType）、运行时类型（runtimeType）
 * 和建议操作（suggestedAction）字段。
 */
class RuntimeFaultNotificationPropertyTest {

    // ===== 生成器 =====

    /** 生成随机 SandboxType 值（REMOTE、OPEN_SANDBOX） */
    @Provide
    Arbitrary<SandboxType> runtimeTypes() {
        return Arbitraries.of(SandboxType.REMOTE, SandboxType.OPEN_SANDBOX);
    }

    /** 生成随机故障类型，包含预定义常量和随机自定义类型 */
    @Provide
    Arbitrary<String> faultTypes() {
        Arbitrary<String> predefined =
                Arbitraries.of(
                        RuntimeFaultNotification.FAULT_PROCESS_CRASHED,
                        RuntimeFaultNotification.FAULT_HEALTH_CHECK_FAILURE,
                        RuntimeFaultNotification.FAULT_IDLE_TIMEOUT,
                        RuntimeFaultNotification.FAULT_CONNECTION_LOST);
        Arbitrary<String> custom =
                Arbitraries.strings()
                        .alpha()
                        .ofMinLength(3)
                        .ofMaxLength(30)
                        .map(String::toUpperCase);
        return Arbitraries.frequencyOf(Tuple.of(3, predefined), Tuple.of(1, custom));
    }

    /** 生成随机建议操作，包含预定义常量和随机自定义操作 */
    @Provide
    Arbitrary<String> suggestedActions() {
        Arbitrary<String> predefined =
                Arbitraries.of(
                        RuntimeFaultNotification.ACTION_RECONNECT,
                        RuntimeFaultNotification.ACTION_RESTART,
                        RuntimeFaultNotification.ACTION_RECREATE);
        Arbitrary<String> custom =
                Arbitraries.strings()
                        .alpha()
                        .ofMinLength(3)
                        .ofMaxLength(20)
                        .map(String::toUpperCase);
        return Arbitraries.frequencyOf(Tuple.of(3, predefined), Tuple.of(1, custom));
    }

    // ===== 属性测试 =====

    /**
     * Property 9(a): 所有 RuntimeFaultNotification 实例包含非空的必需字段。
     *
     * <p><b>Validates: Requirements 7.6, 8.5</b>
     *
     * <p>对于任意 faultType、runtimeType 和 suggestedAction 组合，
     * 创建的 RuntimeFaultNotification 实例的三个字段均不为 null。
     */
    @Property(tries = 100)
    void allNotifications_containNonNullFields(
            @ForAll("faultTypes") String faultType,
            @ForAll("runtimeTypes") SandboxType runtimeType,
            @ForAll("suggestedActions") String suggestedAction) {

        RuntimeFaultNotification notification =
                new RuntimeFaultNotification(faultType, runtimeType, suggestedAction);

        assertNotNull(notification.faultType(), "faultType 不应为 null");
        assertNotNull(notification.sandboxType(), "runtimeType 不应为 null");
        assertNotNull(notification.suggestedAction(), "suggestedAction 不应为 null");
    }

    /**
     * Property 9(b): 通知正确携带运行时类型。
     *
     * <p><b>Validates: Requirements 7.6, 8.5</b>
     *
     * <p>对于任意运行时类型和异常场景，创建的通知中 runtimeType 字段
     * 应与传入的运行时类型完全一致。
     */
    @Property(tries = 100)
    void notification_carriesCorrectRuntimeType(
            @ForAll("faultTypes") String faultType,
            @ForAll("runtimeTypes") SandboxType runtimeType,
            @ForAll("suggestedActions") String suggestedAction) {

        RuntimeFaultNotification notification =
                new RuntimeFaultNotification(faultType, runtimeType, suggestedAction);

        assertEquals(runtimeType, notification.sandboxType(), "通知中的 runtimeType 应与传入值一致");
    }

    /**
     * Property 9(c): 通知正确携带故障类型和建议操作。
     *
     * <p><b>Validates: Requirements 7.6, 8.5</b>
     *
     * <p>对于任意 faultType 和 suggestedAction，创建的通知应原样保留这些值。
     */
    @Property(tries = 100)
    void notification_carriesCorrectFaultTypeAndAction(
            @ForAll("faultTypes") String faultType,
            @ForAll("runtimeTypes") SandboxType runtimeType,
            @ForAll("suggestedActions") String suggestedAction) {

        RuntimeFaultNotification notification =
                new RuntimeFaultNotification(faultType, runtimeType, suggestedAction);

        assertEquals(faultType, notification.faultType(), "faultType 应与传入值一致");
        assertEquals(suggestedAction, notification.suggestedAction(), "suggestedAction 应与传入值一致");
    }

    /**
     * Property 9(d): 不同运行时类型的通知结构一致。
     *
     * <p><b>Validates: Requirements 7.6, 8.5</b>
     *
     * <p>对于任意故障类型和建议操作，为每种运行时类型创建的通知
     * 都应具有相同的结构（三个非空字段），且仅 runtimeType 字段不同。
     */
    @Property(tries = 100)
    void structuralConsistency_acrossRuntimeTypes(
            @ForAll("faultTypes") String faultType,
            @ForAll("suggestedActions") String suggestedAction) {

        for (SandboxType type : SandboxType.values()) {
            RuntimeFaultNotification notification =
                    new RuntimeFaultNotification(faultType, type, suggestedAction);

            assertNotNull(notification.faultType());
            assertNotNull(notification.sandboxType());
            assertNotNull(notification.suggestedAction());

            assertEquals(faultType, notification.faultType());
            assertEquals(type, notification.sandboxType());
            assertEquals(suggestedAction, notification.suggestedAction());
        }
    }

    /**
     * Property 9(e): 相同输入产生相等的通知（record 语义一致性）。
     *
     * <p><b>Validates: Requirements 8.5</b>
     *
     * <p>对于任意相同的 faultType、runtimeType 和 suggestedAction，
     * 两次创建的 RuntimeFaultNotification 应相等（equals/hashCode 一致）。
     */
    @Property(tries = 100)
    void sameInputs_produceEqualNotifications(
            @ForAll("faultTypes") String faultType,
            @ForAll("runtimeTypes") SandboxType runtimeType,
            @ForAll("suggestedActions") String suggestedAction) {

        RuntimeFaultNotification a =
                new RuntimeFaultNotification(faultType, runtimeType, suggestedAction);
        RuntimeFaultNotification b =
                new RuntimeFaultNotification(faultType, runtimeType, suggestedAction);

        assertEquals(a, b, "相同输入应产生相等的通知");
        assertEquals(a.hashCode(), b.hashCode(), "相等的通知应有相同的 hashCode");
    }

    /**
     * Property 9(f): 不同运行时类型产生不相等的通知。
     *
     * <p><b>Validates: Requirements 7.6</b>
     *
     * <p>对于任意故障类型和建议操作，如果两个通知的 runtimeType 不同，
     * 则这两个通知不应相等。这确保通信中断事件正确区分运行时来源。
     */
    @Property(tries = 100)
    void differentRuntimeTypes_produceUnequalNotifications(
            @ForAll("faultTypes") String faultType,
            @ForAll("suggestedActions") String suggestedAction) {

        SandboxType[] types = SandboxType.values();
        for (int i = 0; i < types.length; i++) {
            for (int j = i + 1; j < types.length; j++) {
                RuntimeFaultNotification a =
                        new RuntimeFaultNotification(faultType, types[i], suggestedAction);
                RuntimeFaultNotification b =
                        new RuntimeFaultNotification(faultType, types[j], suggestedAction);

                assertNotEquals(
                        a, b, String.format("不同运行时类型 (%s vs %s) 的通知不应相等", types[i], types[j]));
            }
        }
    }
}
