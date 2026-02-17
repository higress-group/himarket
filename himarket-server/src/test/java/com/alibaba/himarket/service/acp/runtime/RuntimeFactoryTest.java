package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuntimeFactoryTest {

    private K8sConfigService k8sConfigService;
    private RuntimeFactory factory;
    private RuntimeConfig config;

    @BeforeEach
    void setUp() {
        k8sConfigService = mock(K8sConfigService.class);
        factory = new RuntimeFactory(k8sConfigService);
        config = new RuntimeConfig();
        config.setUserId("test-user");
        config.setProviderKey("test-provider");
    }

    // ===== LOCAL 运行时 =====

    @Test
    void create_local_returnsLocalRuntimeAdapter() {
        RuntimeAdapter adapter = factory.create(RuntimeType.LOCAL, config);
        assertNotNull(adapter);
        assertInstanceOf(LocalRuntimeAdapter.class, adapter);
    }

    @Test
    void create_local_adapterHasCorrectType() {
        RuntimeAdapter adapter = factory.create(RuntimeType.LOCAL, config);
        assertEquals(RuntimeType.LOCAL, adapter.getType());
    }

    @Test
    void create_local_returnsNewInstanceEachTime() {
        RuntimeAdapter first = factory.create(RuntimeType.LOCAL, config);
        RuntimeAdapter second = factory.create(RuntimeType.LOCAL, config);
        assertNotSame(first, second);
    }

    // ===== K8S 运行时 =====

    @Test
    void create_k8s_withValidConfig_returnsK8sRuntimeAdapter() {
        KubernetesClient mockClient = mock(KubernetesClient.class);
        config.setK8sConfigId("test-config-id");
        when(k8sConfigService.getClient("test-config-id")).thenReturn(mockClient);

        RuntimeAdapter adapter = factory.create(RuntimeType.K8S, config);

        assertNotNull(adapter);
        assertInstanceOf(K8sRuntimeAdapter.class, adapter);
        assertEquals(RuntimeType.K8S, adapter.getType());
    }

    @Test
    void create_k8s_withoutK8sConfigId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> factory.create(RuntimeType.K8S, config));
    }

    @Test
    void create_k8s_withBlankK8sConfigId_throwsIllegalArgumentException() {
        config.setK8sConfigId("  ");
        assertThrows(IllegalArgumentException.class, () -> factory.create(RuntimeType.K8S, config));
    }

    // ===== null 参数 =====

    @Test
    void create_withNullType_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> factory.create(null, config));
    }
}
