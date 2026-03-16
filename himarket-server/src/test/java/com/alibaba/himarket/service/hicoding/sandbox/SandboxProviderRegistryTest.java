package com.alibaba.himarket.service.hicoding.sandbox;

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
        SandboxProvider openSandboxProvider = mockProvider(SandboxType.OPEN_SANDBOX);
        SandboxProvider remoteProvider = mockProvider(SandboxType.REMOTE);
        SandboxProviderRegistry registry =
                new SandboxProviderRegistry(List.of(openSandboxProvider, remoteProvider));

        assertSame(openSandboxProvider, registry.getProvider(SandboxType.OPEN_SANDBOX));
        assertSame(remoteProvider, registry.getProvider(SandboxType.REMOTE));
    }

    @Test
    void getProvider_singleProvider_returnsIt() {
        SandboxProvider remoteProvider = mockProvider(SandboxType.REMOTE);
        SandboxProviderRegistry registry = new SandboxProviderRegistry(List.of(remoteProvider));

        assertSame(remoteProvider, registry.getProvider(SandboxType.REMOTE));
    }

    // ===== getProvider 未注册类型抛异常 =====

    @Test
    void getProvider_unregisteredType_throwsIllegalArgumentException() {
        SandboxProvider openSandboxProvider = mockProvider(SandboxType.OPEN_SANDBOX);
        SandboxProviderRegistry registry =
                new SandboxProviderRegistry(List.of(openSandboxProvider));

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> registry.getProvider(SandboxType.REMOTE));
        assertTrue(ex.getMessage().contains("REMOTE") || ex.getMessage().contains("不支持"));
    }

    @Test
    void getProvider_emptyRegistry_throwsIllegalArgumentException() {
        SandboxProviderRegistry registry = new SandboxProviderRegistry(List.of());

        assertThrows(
                IllegalArgumentException.class, () -> registry.getProvider(SandboxType.REMOTE));
    }

    // ===== supportedTypes =====

    @Test
    void supportedTypes_returnsAllRegisteredTypes() {
        SandboxProvider openSandboxProvider = mockProvider(SandboxType.OPEN_SANDBOX);
        SandboxProvider remoteProvider = mockProvider(SandboxType.REMOTE);
        SandboxProviderRegistry registry =
                new SandboxProviderRegistry(List.of(openSandboxProvider, remoteProvider));

        Set<SandboxType> types = registry.supportedTypes();
        assertEquals(Set.of(SandboxType.OPEN_SANDBOX, SandboxType.REMOTE), types);
    }

    @Test
    void supportedTypes_emptyRegistry_returnsEmptySet() {
        SandboxProviderRegistry registry = new SandboxProviderRegistry(List.of());

        assertTrue(registry.supportedTypes().isEmpty());
    }
}
