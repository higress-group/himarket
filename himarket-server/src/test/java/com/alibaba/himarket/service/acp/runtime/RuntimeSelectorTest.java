package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.config.AcpProperties.RemoteConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * RuntimeSelector 单元测试。
 */
class RuntimeSelectorTest {

    private AcpProperties acpProperties;

    @BeforeEach
    void setUp() {
        acpProperties = new AcpProperties();
        acpProperties.setDefaultRuntime("remote");
    }

    /**
     * 创建 RuntimeSelector，通过 RemoteConfig.host 控制远程沙箱可用性。
     * remoteAvailable=true 时设置 host 为非空值，false 时设置为空。
     */
    private RuntimeSelector createSelector(boolean remoteAvailable) {
        RemoteConfig remoteConfig = new RemoteConfig();
        if (remoteAvailable) {
            remoteConfig.setHost("sandbox.example.com");
            remoteConfig.setPort(8080);
        } else {
            remoteConfig.setHost("");
        }
        acpProperties.setRemote(remoteConfig);
        return new RuntimeSelector(acpProperties);
    }

    private CliProviderConfig createProvider(
            String displayName, List<SandboxType> compatibleRuntimes) {
        CliProviderConfig config = new CliProviderConfig();
        config.setDisplayName(displayName);
        config.setCompatibleRuntimes(compatibleRuntimes);
        return config;
    }

    private void registerProvider(String key, CliProviderConfig config) {
        acpProperties.getProviders().put(key, config);
    }

    // ===== getAvailableRuntimes =====

    @Nested
    class GetAvailableRuntimes {

        @Test
        void returnsAllCompatibleRuntimes() {
            registerProvider(
                    "qodercli",
                    createProvider(
                            "Qoder CLI", List.of(SandboxType.REMOTE, SandboxType.OPEN_SANDBOX)));
            RuntimeSelector selector = createSelector(true);

            List<RuntimeOption> options = selector.getAvailableRuntimes("qodercli");

            assertEquals(2, options.size());
            assertEquals(SandboxType.REMOTE, options.get(0).type());
            assertEquals(SandboxType.OPEN_SANDBOX, options.get(1).type());
        }

        @Test
        void returnsEmptyListWhenNoCompatibleRuntimes() {
            CliProviderConfig config = new CliProviderConfig();
            config.setDisplayName("Empty");
            config.setCompatibleRuntimes(List.of());
            registerProvider("empty", config);
            RuntimeSelector selector = createSelector(true);

            List<RuntimeOption> options = selector.getAvailableRuntimes("empty");

            assertTrue(options.isEmpty());
        }

        @Test
        void returnsEmptyListWhenCompatibleRuntimesIsNull() {
            CliProviderConfig config = new CliProviderConfig();
            config.setDisplayName("NullRuntimes");
            registerProvider("null-rt", config);
            RuntimeSelector selector = createSelector(true);

            List<RuntimeOption> options = selector.getAvailableRuntimes("null-rt");

            assertTrue(options.isEmpty());
        }

        @Test
        void throwsForUnknownProvider() {
            RuntimeSelector selector = createSelector(true);

            IllegalArgumentException ex =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> selector.getAvailableRuntimes("nonexistent"));

            assertTrue(ex.getMessage().contains("nonexistent"));
        }

        @Test
        void remoteMarkedUnavailableWhenNotConfigured() {
            registerProvider("qodercli", createProvider("Qoder CLI", List.of(SandboxType.REMOTE)));
            RuntimeSelector selector = createSelector(false);

            List<RuntimeOption> options = selector.getAvailableRuntimes("qodercli");

            RuntimeOption remoteOption =
                    options.stream()
                            .filter(o -> o.type() == SandboxType.REMOTE)
                            .findFirst()
                            .orElseThrow();
            assertFalse(remoteOption.available());
            assertNotNull(remoteOption.unavailableReason());
        }
    }

    // ===== selectDefault =====

    @Nested
    class SelectDefault {

        @Test
        void returnsConfiguredDefaultWhenAvailable() {
            acpProperties.setDefaultRuntime("remote");
            registerProvider(
                    "qodercli",
                    createProvider(
                            "Qoder CLI", List.of(SandboxType.REMOTE, SandboxType.OPEN_SANDBOX)));
            RuntimeSelector selector = createSelector(true);

            SandboxType selected = selector.selectDefault("qodercli");

            assertEquals(SandboxType.REMOTE, selected);
        }

        @Test
        void autoSelectsWhenOnlyOneAvailable() {
            registerProvider("qodercli", createProvider("Qoder CLI", List.of(SandboxType.REMOTE)));
            RuntimeSelector selector = createSelector(true);

            SandboxType selected = selector.selectDefault("qodercli");

            assertEquals(SandboxType.REMOTE, selected);
        }

        @Test
        void autoSelectsOnlyAvailableRuntime() {
            registerProvider(
                    "qodercli",
                    createProvider(
                            "Qoder CLI", List.of(SandboxType.REMOTE, SandboxType.OPEN_SANDBOX)));
            acpProperties.setDefaultRuntime("open-sandbox");
            RuntimeSelector selector = createSelector(true);

            SandboxType selected = selector.selectDefault("qodercli");

            // OPEN_SANDBOX 不可用，只剩 REMOTE
            assertEquals(SandboxType.REMOTE, selected);
        }

        @Test
        void throwsForUnknownProvider() {
            RuntimeSelector selector = createSelector(true);

            assertThrows(
                    IllegalArgumentException.class, () -> selector.selectDefault("nonexistent"));
        }

        @Test
        void throwsWhenNoCompatibleRuntimes() {
            CliProviderConfig config = new CliProviderConfig();
            config.setDisplayName("Empty");
            config.setCompatibleRuntimes(List.of());
            registerProvider("empty", config);
            RuntimeSelector selector = createSelector(true);

            assertThrows(IllegalStateException.class, () -> selector.selectDefault("empty"));
        }

        @Test
        void throwsWhenNoAvailableRuntimes() {
            registerProvider(
                    "remote-only", createProvider("Remote Only", List.of(SandboxType.REMOTE)));
            RuntimeSelector selector = createSelector(false);

            assertThrows(IllegalStateException.class, () -> selector.selectDefault("remote-only"));
        }

        @Test
        void handlesInvalidDefaultRuntimeGracefully() {
            acpProperties.setDefaultRuntime("invalid_type");
            registerProvider(
                    "test",
                    createProvider("Test", List.of(SandboxType.REMOTE, SandboxType.OPEN_SANDBOX)));
            RuntimeSelector selector = createSelector(true);

            SandboxType selected = selector.selectDefault("test");

            // invalid default → fallback to first available = REMOTE
            assertEquals(SandboxType.REMOTE, selected);
        }

        @Test
        void handlesBlankDefaultRuntime() {
            acpProperties.setDefaultRuntime("  ");
            registerProvider(
                    "test",
                    createProvider("Test", List.of(SandboxType.REMOTE, SandboxType.OPEN_SANDBOX)));
            RuntimeSelector selector = createSelector(true);

            SandboxType selected = selector.selectDefault("test");

            // blank default → fallback to first available = REMOTE
            assertEquals(SandboxType.REMOTE, selected);
        }
    }

    // ===== isSandboxAvailable =====

    @Nested
    class IsRuntimeAvailable {

        @Test
        void remoteAvailableWhenConfigured() {
            RuntimeSelector selector = createSelector(true);
            assertTrue(selector.isSandboxAvailable(SandboxType.REMOTE));
        }

        @Test
        void remoteUnavailableWhenNotConfigured() {
            RuntimeSelector selector = createSelector(false);
            assertFalse(selector.isSandboxAvailable(SandboxType.REMOTE));
        }
    }

    // ===== toRuntimeOption =====

    @Nested
    class ToRuntimeOption {

        @Test
        void compatibleAndAvailableOption() {
            RuntimeSelector selector = createSelector(true);

            RuntimeOption option = selector.toRuntimeOption(SandboxType.REMOTE, true);

            assertEquals(SandboxType.REMOTE, option.type());
            assertTrue(option.available());
            assertNull(option.unavailableReason());
            assertNotNull(option.label());
            assertNotNull(option.description());
        }

        @Test
        void incompatibleOptionMarkedUnavailable() {
            RuntimeSelector selector = createSelector(true);

            RuntimeOption option = selector.toRuntimeOption(SandboxType.REMOTE, false);

            assertFalse(option.available());
            assertNotNull(option.unavailableReason());
            assertTrue(option.unavailableReason().contains("不兼容"));
        }

        @Test
        void compatibleButEnvironmentUnavailable() {
            RuntimeSelector selector = createSelector(false);

            RuntimeOption option = selector.toRuntimeOption(SandboxType.REMOTE, true);

            assertFalse(option.available());
            assertNotNull(option.unavailableReason());
        }

        @Test
        void eachTypeHasDistinctLabelAndDescription() {
            RuntimeSelector selector = createSelector(true);

            RuntimeOption remote = selector.toRuntimeOption(SandboxType.REMOTE, true);
            RuntimeOption openSandbox = selector.toRuntimeOption(SandboxType.OPEN_SANDBOX, true);

            assertNotEquals(remote.label(), openSandbox.label());
        }
    }
}
