package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SandboxProviderRegistryTest {

    private SandboxProvider mockProvider(SandboxType type) {
        SandboxProvider provider = mock(SandboxProvider.class);
        when(provider.getType()).thenReturn(type);
        return provider;
    }

    // ===== getProvider 正常查找 =====

    @Test
    void getProvider_registeredType_returnsCorrectProvider() {
        SandboxProvider localProvider = mockProvider(SandboxType.LOCAL);
        SandboxProvider k8sProvider = mockProvider(SandboxType.K8S);
        SandboxProviderRegistry registry =
                new SandboxProviderRegistry(List.of(localProvider, k8sProvider));

        assertSame(localProvider, registry.getProvider(SandboxType.LOCAL));
        assertSame(k8sProvider, registry.getProvider(SandboxType.K8S));
    }

    @Test
    void getProvider_singleProvider_returnsIt() {
        SandboxProvider k8sProvider = mockProvider(SandboxType.K8S);
        SandboxProviderRegistry registry = new SandboxProviderRegistry(List.of(k8sProvider));

        assertSame(k8sProvider, registry.getProvider(SandboxType.K8S));
    }

    // ===== getProvider 未注册类型抛异常 =====

    @Test
    void getProvider_unregisteredType_throwsIllegalArgumentException() {
        SandboxProvider localProvider = mockProvider(SandboxType.LOCAL);
        SandboxProviderRegistry registry = new SandboxProviderRegistry(List.of(localProvider));

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> registry.getProvider(SandboxType.K8S));
        assertTrue(ex.getMessage().contains("K8S") || ex.getMessage().contains("不支持"));
    }

    @Test
    void getProvider_emptyRegistry_throwsIllegalArgumentException() {
        SandboxProviderRegistry registry = new SandboxProviderRegistry(List.of());

        assertThrows(IllegalArgumentException.class, () -> registry.getProvider(SandboxType.LOCAL));
    }

    // ===== supportedTypes =====

    @Test
    void supportedTypes_returnsAllRegisteredTypes() {
        SandboxProvider localProvider = mockProvider(SandboxType.LOCAL);
        SandboxProvider k8sProvider = mockProvider(SandboxType.K8S);
        SandboxProviderRegistry registry =
                new SandboxProviderRegistry(List.of(localProvider, k8sProvider));

        Set<SandboxType> types = registry.supportedTypes();
        assertEquals(Set.of(SandboxType.LOCAL, SandboxType.K8S), types);
    }

    @Test
    void supportedTypes_emptyRegistry_returnsEmptySet() {
        SandboxProviderRegistry registry = new SandboxProviderRegistry(List.of());

        assertTrue(registry.supportedTypes().isEmpty());
    }
}
