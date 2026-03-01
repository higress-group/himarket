package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import net.jqwik.api.*;

/**
 * 运行时可用性验证属性测试。
 *
 * <p>Feature: sandbox-runtime-strategy, Property 7: 运行时可用性验证
 *
 * <p><b>Validates: Requirements 5.3, 5.5</b>
 *
 * <p>对于任意运行时类型和环境状态组合（K8s 是否配置），
 * RuntimeSelector 的可用性验证结果应该正确反映实际环境状态：
 * K8s 未配置时 K8S 不可用。
 */
class RuntimeAvailabilityPropertyTest {

    @Provide
    Arbitrary<SandboxType> runtimeTypes() {
        return Arbitraries.of(SandboxType.LOCAL, SandboxType.K8S);
    }

    @Provide
    Arbitrary<Boolean> k8sStates() {
        return Arbitraries.of(true, false);
    }

    private RuntimeSelector buildSelector(boolean k8sAvailable) {
        AcpProperties props = new AcpProperties();
        props.setDefaultRuntime("local");
        K8sConfigService mockK8s =
                new K8sConfigService(null) {
                    @Override
                    public void init() {}

                    @Override
                    public boolean hasAnyCluster() {
                        return k8sAvailable;
                    }

                    @Override
                    public java.util.List<K8sClusterInfo> listClusters() {
                        return java.util.Collections.emptyList();
                    }
                };
        return new RuntimeSelector(props, mockK8s);
    }

    // ===== Property 7a: LOCAL 始终可用 =====

    @Property(tries = 200)
    void localRuntime_alwaysAvailable(@ForAll("k8sStates") boolean k8sAvailable) {
        RuntimeSelector selector = buildSelector(k8sAvailable);
        assertTrue(selector.isSandboxAvailable(SandboxType.LOCAL));
    }

    // ===== Property 7b: K8S 可用性取决于 K8s 配置状态 =====

    @Property(tries = 200)
    void k8sAvailability_matchesK8sState(@ForAll("k8sStates") boolean k8sAvailable) {
        RuntimeSelector selector = buildSelector(k8sAvailable);
        assertEquals(k8sAvailable, selector.isSandboxAvailable(SandboxType.K8S));
    }

    // ===== Property 7d: 可用性验证结果与 RuntimeOption 一致 =====

    @Property(tries = 200)
    void runtimeOption_availabilityMatchesIsRuntimeAvailable(
            @ForAll("runtimeTypes") SandboxType type, @ForAll("k8sStates") boolean k8sAvailable) {

        RuntimeSelector selector = buildSelector(k8sAvailable);
        boolean directAvailability = selector.isSandboxAvailable(type);
        RuntimeOption option = selector.toRuntimeOption(type, true);

        assertEquals(directAvailability, option.available());
    }

    // ===== Property 7e: 不可用运行时返回明确的错误信息 =====

    @Property(tries = 200)
    void unavailableRuntime_returnsErrorMessageWithReason(
            @ForAll("runtimeTypes") SandboxType type, @ForAll("k8sStates") boolean k8sAvailable) {

        RuntimeSelector selector = buildSelector(k8sAvailable);
        RuntimeOption option = selector.toRuntimeOption(type, true);

        if (!option.available()) {
            assertNotNull(option.unavailableReason());
            assertFalse(option.unavailableReason().isBlank());
        } else {
            assertNull(option.unavailableReason());
        }
    }

    // ===== Property 7f: K8S 不可用时错误信息包含配置指引 =====

    @Property(tries = 100)
    void k8sUnavailable_errorMessageContainsConfigGuidance() {
        RuntimeSelector selector = buildSelector(false);
        RuntimeOption option = selector.toRuntimeOption(SandboxType.K8S, true);

        assertFalse(option.available());
        assertNotNull(option.unavailableReason());
        assertTrue(
                option.unavailableReason().contains("K8s")
                        || option.unavailableReason().contains("k8s")
                        || option.unavailableReason().contains("kubeconfig"));
    }

    // ===== Property 7g: 不兼容的运行时标记为不可用 =====

    @Property(tries = 200)
    void incompatibleRuntime_markedUnavailableWithReason(
            @ForAll("runtimeTypes") SandboxType type, @ForAll("k8sStates") boolean k8sAvailable) {

        RuntimeSelector selector = buildSelector(k8sAvailable);
        RuntimeOption option = selector.toRuntimeOption(type, false);

        assertFalse(option.available());
        assertNotNull(option.unavailableReason());
    }
}
