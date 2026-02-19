package com.alibaba.himarket.service.acp.runtime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import net.jqwik.api.*;

/**
 * K8sRuntimeAdapter 复用模式属性测试。
 *
 * <p>Feature: k8s-pod-reuse, Property 6: 复用模式 close 不删除 Pod
 *
 * <p>对于任意处于复用模式（reuseMode=true）的 K8sRuntimeAdapter 实例，
 * 调用 close() 后，对应的 K8s Pod 应仍然存在（不被删除）。
 *
 * <p><b>Validates: Requirements 5.1</b>
 */
class K8sRuntimeAdapterReuseModePropertyTest {

    // ===== 生成器 =====

    @Provide
    Arbitrary<String> podNames() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars('-')
                .ofMinLength(3)
                .ofMaxLength(30)
                .filter(s -> !s.isBlank() && s.matches("^[a-zA-Z].*"));
    }

    @Provide
    Arbitrary<String> namespaces() {
        return Arbitraries.of("himarket", "default", "sandbox-ns", "dev", "staging");
    }

    // ===== 辅助方法 =====

    @SuppressWarnings("unchecked")
    private MockContext buildMockContext(String podName, String namespace) {
        KubernetesClient client = mock(KubernetesClient.class);

        MixedOperation<Pod, PodList, PodResource> podsOp = mock(MixedOperation.class);
        when(client.pods()).thenReturn(podsOp);

        NonNamespaceOperation<Pod, PodList, PodResource> nsOp = mock(NonNamespaceOperation.class);
        when(podsOp.inNamespace(anyString())).thenReturn(nsOp);

        PodResource podResource = mock(PodResource.class);
        when(nsOp.withName(anyString())).thenReturn(podResource);

        return new MockContext(client, podResource);
    }

    private record MockContext(KubernetesClient client, PodResource podResource) {}

    /**
     * 通过反射设置 K8sRuntimeAdapter 的 podName 字段，
     * 模拟已有 Pod 的状态，以便测试 close() 行为。
     */
    private void setPodName(K8sRuntimeAdapter adapter, String podName) {
        try {
            var field = K8sRuntimeAdapter.class.getDeclaredField("podName");
            field.setAccessible(true);
            field.set(adapter, podName);
        } catch (Exception e) {
            throw new RuntimeException("无法设置 podName", e);
        }
    }

    /**
     * 通过反射设置 status 为 RUNNING，确保 close() 不会提前返回。
     */
    private void setStatusRunning(K8sRuntimeAdapter adapter) {
        try {
            var field = K8sRuntimeAdapter.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(adapter, RuntimeStatus.RUNNING);
        } catch (Exception e) {
            throw new RuntimeException("无法设置 status", e);
        }
    }

    // ===== Property 6: 复用模式 close 不删除 Pod =====
    // Feature: k8s-pod-reuse, Property 6: 复用模式 close 不删除 Pod
    //
    // 对于任意处于复用模式（reuseMode=true）的 K8sRuntimeAdapter 实例，
    // 调用 close() 后，对应的 K8s Pod 应仍然存在（不被删除）。
    //
    // 测试策略：创建 K8sRuntimeAdapter 实例并设置 reuseMode=true，
    // 通过反射设置 podName 模拟已有 Pod，调用 close() 后验证
    // K8s client 的 delete() 方法从未被调用。
    //
    // **Validates: Requirements 5.1**

    @Property(tries = 100)
    void reuseModeClose_doesNotDeletePod(
            @ForAll("podNames") String podName, @ForAll("namespaces") String namespace) {

        MockContext ctx = buildMockContext(podName, namespace);
        K8sRuntimeAdapter adapter = new K8sRuntimeAdapter(ctx.client(), namespace, null, 30);

        // 设置复用模式
        adapter.setReuseMode(true);

        // 模拟已有 Pod 的状态
        setPodName(adapter, podName);
        setStatusRunning(adapter);

        // 调用 close()
        adapter.close();

        // 验证：复用模式下 Pod 不应被删除
        verify(ctx.podResource(), never()).delete();
    }

    // ===== 对照测试：非复用模式 close 应删除 Pod =====
    // 作为 Property 6 的对照，验证非复用模式下 close() 确实会删除 Pod。
    // 这确保了测试 mock 设置正确，Property 6 的验证是有意义的。

    @Property(tries = 100)
    void nonReuseModeClose_deletesPod(
            @ForAll("podNames") String podName, @ForAll("namespaces") String namespace) {

        MockContext ctx = buildMockContext(podName, namespace);
        K8sRuntimeAdapter adapter = new K8sRuntimeAdapter(ctx.client(), namespace, null, 30);

        // 非复用模式（默认）
        adapter.setReuseMode(false);

        // 模拟已有 Pod 的状态
        setPodName(adapter, podName);
        setStatusRunning(adapter);

        // 调用 close()
        adapter.close();

        // 验证：非复用模式下 Pod 应被删除
        verify(ctx.podResource()).delete();
    }
}
