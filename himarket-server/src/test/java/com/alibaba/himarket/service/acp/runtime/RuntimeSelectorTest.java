package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
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
        acpProperties.setDefaultRuntime("local");
    }

    private RuntimeSelector createSelector(boolean k8sAvailable) {
        K8sConfigService mockK8sConfigService = new K8sConfigService(null) {
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
        return new RuntimeSelector(acpProperties, mockK8sConfigService);
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
                    createProvider("Qoder CLI", List.of(SandboxType.LOCAL, SandboxType.K8S)));
            RuntimeSelector selector = createSelector(true);

            List<RuntimeOption> options = selector.getAvailableRuntimes("qodercli");

            assertEquals(2, options.size());
            assertEquals(SandboxType.LOCAL, options.get(0).type());
            assertEquals(SandboxType.K8S, options.get(1).type());
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
        void k8sMarkedUnavailableWhenK8sNotConfigured() {
            registerProvider(
                    "qodercli",
                    createProvider("Qoder CLI", List.of(SandboxType.LOCAL, SandboxType.K8S)));
            RuntimeSelector selector = createSelector(false);

            List<RuntimeOption> options = selector.getAvailableRuntimes("qodercli");

            RuntimeOption k8sOption =
                    options.stream()
                            .filter(o -> o.type() == SandboxType.K8S)
                            .findFirst()
                            .orElseThrow();
            assertFalse(k8sOption.available());
            assertNotNull(k8sOption.unavailableReason());
            assertTrue(k8sOption.unavailableReason().contains("K8s"));
        }

        @Test
        void localAlwaysAvailable() {
            registerProvider("test", createProvider("Test", List.of(SandboxType.LOCAL)));
            RuntimeSelector selector = createSelector(false);

            List<RuntimeOption> options = selector.getAvailableRuntimes("test");

            assertEquals(1, options.size());
            assertTrue(options.get(0).available());
            assertNull(options.get(0).unavailableReason());
        }
    }

    // ===== selectDefault =====

    @Nested
    class SelectDefault {

        @Test
        void returnsConfiguredDefaultWhenAvailable() {
            acpProperties.setDefaultRuntime("local");
            registerProvider(
                    "qodercli",
                    createProvider("Qoder CLI", List.of(SandboxType.LOCAL, SandboxType.K8S)));
            RuntimeSelector selector = createSelector(true);

            SandboxType selected = selector.selectDefault("qodercli");

            assertEquals(SandboxType.LOCAL, selected);
        }

        @Test
        void autoSelectsWhenOnlyOneAvailable() {
            registerProvider("qodercli", createProvider("Qoder CLI", List.of(SandboxType.K8S)));
            RuntimeSelector selector = createSelector(true);

            SandboxType selected = selector.selectDefault("qodercli");

            assertEquals(SandboxType.K8S, selected);
        }

        @Test
        void autoSelectsOnlyAvailableRuntime() {
            registerProvider(
                    "qodercli",
                    createProvider("Qoder CLI", List.of(SandboxType.LOCAL, SandboxType.K8S)));
            acpProperties.setDefaultRuntime("k8s");
            RuntimeSelector selector = createSelector(false);

            SandboxType selected = selector.selectDefault("qodercli");

            // K8S 不可用，只剩 LOCAL
            assertEquals(SandboxType.LOCAL, selected);
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
            registerProvider("k8s-only", createProvider("K8s Only", List.of(SandboxType.K8S)));
            RuntimeSelector selector = createSelector(false);

            assertThrows(IllegalStateException.class, () -> selector.selectDefault("k8s-only"));
        }

        @Test
        void handlesInvalidDefaultRuntimeGracefully() {
            acpProperties.setDefaultRuntime("invalid_type");
            registerProvider(
                    "test", createProvider("Test", List.of(SandboxType.LOCAL, SandboxType.K8S)));
            RuntimeSelector selector = createSelector(true);

            SandboxType selected = selector.selectDefault("test");

            assertEquals(SandboxType.LOCAL, selected);
        }

        @Test
        void handlesBlankDefaultRuntime() {
            acpProperties.setDefaultRuntime("  ");
            registerProvider(
                    "test", createProvider("Test", List.of(SandboxType.LOCAL, SandboxType.K8S)));
            RuntimeSelector selector = createSelector(true);

            SandboxType selected = selector.selectDefault("test");

            assertEquals(SandboxType.LOCAL, selected);
        }
    }

    // ===== isSandboxAvailable =====

    @Nested
    class IsRuntimeAvailable {

        @Test
        void localAlwaysAvailable() {
            RuntimeSelector selector = createSelector(false);
            assertTrue(selector.isSandboxAvailable(SandboxType.LOCAL));
        }

        @Test
        void k8sAvailableWhenK8sConfigured() {
            RuntimeSelector selector = createSelector(true);
            assertTrue(selector.isSandboxAvailable(SandboxType.K8S));
        }

        @Test
        void k8sUnavailableWhenK8sNotConfigured() {
            RuntimeSelector selector = createSelector(false);
            assertFalse(selector.isSandboxAvailable(SandboxType.K8S));
        }
    }

    // ===== toRuntimeOption =====

    @Nested
    class ToRuntimeOption {

        @Test
        void compatibleAndAvailableOption() {
            RuntimeSelector selector = createSelector(true);

            RuntimeOption option = selector.toRuntimeOption(SandboxType.LOCAL, true);

            assertEquals(SandboxType.LOCAL, option.type());
            assertTrue(option.available());
            assertNull(option.unavailableReason());
            assertNotNull(option.label());
            assertNotNull(option.description());
        }

        @Test
        void incompatibleOptionMarkedUnavailable() {
            RuntimeSelector selector = createSelector(true);

            RuntimeOption option = selector.toRuntimeOption(SandboxType.K8S, false);

            assertFalse(option.available());
            assertNotNull(option.unavailableReason());
            assertTrue(option.unavailableReason().contains("不兼容"));
        }

        @Test
        void compatibleButEnvironmentUnavailable() {
            RuntimeSelector selector = createSelector(false);

            RuntimeOption option = selector.toRuntimeOption(SandboxType.K8S, true);

            assertFalse(option.available());
            assertNotNull(option.unavailableReason());
            assertTrue(option.unavailableReason().contains("K8s"));
        }

        @Test
        void eachTypeHasDistinctLabelAndDescription() {
            RuntimeSelector selector = createSelector(true);

            RuntimeOption local = selector.toRuntimeOption(SandboxType.LOCAL, true);
            RuntimeOption k8s = selector.toRuntimeOption(SandboxType.K8S, true);

            assertNotEquals(local.label(), k8s.label());
        }
    }
}
