package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.RemoteConfig;
import net.jqwik.api.*;

/**
 * 运行时可用性验证属性测试。
 *
 * <p>Feature: sandbox-runtime-strategy, Property 7: 运行时可用性验证
 *
 * <p><b>Validates: Requirements 5.3, 5.5</b>
 *
 * <p>对于任意运行时类型和环境状态组合（远程沙箱是否配置），
 * RuntimeSelector 的可用性验证结果应该正确反映实际环境状态：
 * 远程沙箱未配置时 REMOTE 不可用。
 */
class RuntimeAvailabilityPropertyTest {

    @Provide
    Arbitrary<SandboxType> runtimeTypes() {
        return Arbitraries.of(SandboxType.REMOTE, SandboxType.OPEN_SANDBOX);
    }

    @Provide
    Arbitrary<Boolean> remoteStates() {
        return Arbitraries.of(true, false);
    }

    private RuntimeSelector buildSelector(boolean remoteAvailable) {
        AcpProperties props = new AcpProperties();
        props.setDefaultRuntime("remote");
        RemoteConfig remoteConfig = new RemoteConfig();
        if (remoteAvailable) {
            remoteConfig.setHost("sandbox.example.com");
            remoteConfig.setPort(8080);
        } else {
            remoteConfig.setHost("");
        }
        props.setRemote(remoteConfig);
        return new RuntimeSelector(props);
    }

    // ===== Property 7b: REMOTE 可用性取决于配置状态 =====

    @Property(tries = 200)
    void remoteAvailability_matchesConfigState(@ForAll("remoteStates") boolean remoteAvailable) {
        RuntimeSelector selector = buildSelector(remoteAvailable);
        assertEquals(remoteAvailable, selector.isSandboxAvailable(SandboxType.REMOTE));
    }

    // ===== Property 7d: 可用性验证结果与 RuntimeOption 一致 =====

    @Property(tries = 200)
    void runtimeOption_availabilityMatchesIsRuntimeAvailable(
            @ForAll("runtimeTypes") SandboxType type,
            @ForAll("remoteStates") boolean remoteAvailable) {

        RuntimeSelector selector = buildSelector(remoteAvailable);
        boolean directAvailability = selector.isSandboxAvailable(type);
        RuntimeOption option = selector.toRuntimeOption(type, true);

        assertEquals(directAvailability, option.available());
    }

    // ===== Property 7e: 不可用运行时返回明确的错误信息 =====

    @Property(tries = 200)
    void unavailableRuntime_returnsErrorMessageWithReason(
            @ForAll("runtimeTypes") SandboxType type,
            @ForAll("remoteStates") boolean remoteAvailable) {

        RuntimeSelector selector = buildSelector(remoteAvailable);
        RuntimeOption option = selector.toRuntimeOption(type, true);

        if (!option.available()) {
            assertNotNull(option.unavailableReason());
            assertFalse(option.unavailableReason().isBlank());
        } else {
            assertNull(option.unavailableReason());
        }
    }

    // ===== Property 7f: REMOTE 不可用时错误信息包含配置指引 =====

    @Property(tries = 100)
    void remoteUnavailable_errorMessageContainsConfigGuidance() {
        RuntimeSelector selector = buildSelector(false);
        RuntimeOption option = selector.toRuntimeOption(SandboxType.REMOTE, true);

        assertFalse(option.available());
        assertNotNull(option.unavailableReason());
        assertTrue(
                option.unavailableReason().contains("远程")
                        || option.unavailableReason().contains("remote")
                        || option.unavailableReason().contains("acp.remote"));
    }

    // ===== Property 7g: 不兼容的运行时标记为不可用 =====

    @Property(tries = 200)
    void incompatibleRuntime_markedUnavailableWithReason(
            @ForAll("runtimeTypes") SandboxType type,
            @ForAll("remoteStates") boolean remoteAvailable) {

        RuntimeSelector selector = buildSelector(remoteAvailable);
        RuntimeOption option = selector.toRuntimeOption(type, false);

        assertFalse(option.available());
        assertNotNull(option.unavailableReason());
    }
}
