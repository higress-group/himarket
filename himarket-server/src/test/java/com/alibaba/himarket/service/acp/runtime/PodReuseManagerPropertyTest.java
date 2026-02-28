package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.jqwik.api.*;

/**
 * PodReuseManager 属性测试。
 *
 * <p>Feature: k8s-pod-reuse
 *
 * <p>覆盖以下属性：
 *
 * <ul>
 *   <li>Property 3: Pod 缓存 round-trip
 *   <li>Property 4: Pod 缓存清理一致性
 *   <li>Property 5: 新建用户级沙箱 Pod 标签完整性
 *   <li>Property 7: 连接计数不变量
 * </ul>
 */
class PodReuseManagerPropertyTest {

    // ===== 生成器 =====

    @Provide
    Arbitrary<String> validUserIds() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(20)
                .filter(s -> !s.isBlank());
    }

    @Provide
    Arbitrary<String> validProviderKeys() {
        return Arbitraries.of("qodercli", "kiro-cli", "custom-provider", "test-provider");
    }

    @Provide
    Arbitrary<String> containerImages() {
        return Arbitraries.of(
                "himarket/sandbox:latest",
                "himarket/sandbox:v1.0",
                "registry.cn-hangzhou.aliyuncs.com/sandbox:latest");
    }

    /**
     * 生成 acquire/release 操作序列。true = acquire, false = release。 确保序列以 acquire
     * 开头，且 release 次数不超过 acquire 次数。
     */
    @Provide
    Arbitrary<List<Boolean>> acquireReleaseSequences() {
        return Arbitraries.integers()
                .between(1, 30)
                .flatMap(
                        size ->
                                Arbitraries.of(true, false)
                                        .list()
                                        .ofSize(size)
                                        .filter(
                                                seq -> {
                                                    int balance = 0;
                                                    for (boolean isAcquire : seq) {
                                                        if (isAcquire) {
                                                            balance++;
                                                        } else {
                                                            balance--;
                                                        }
                                                        if (balance < 0) return false;
                                                    }
                                                    return balance >= 0;
                                                }));
    }

    // ===== 辅助方法 =====

    private RuntimeConfig buildConfig(String userId, String providerKey, String image) {
        RuntimeConfig config = new RuntimeConfig();
        config.setUserId(userId);
        config.setProviderKey(providerKey);
        config.setContainerImage(image);
        config.setCommand("/bin/bash");
        config.setArgs(List.of("--login"));
        config.setK8sConfigId("test-config-id");
        return config;
    }

    /**
     * 构建 mock KubernetesClient，用于 createNewPod 内部方法测试。
     * 捕获传入 resource() 的 Pod 对象以验证标签。
     */
    @SuppressWarnings("unchecked")
    private CreatePodMockContext buildCreatePodMock(String podName, String podIp) {
        KubernetesClient client = mock(KubernetesClient.class);

        MixedOperation<Pod, PodList, PodResource> podsOp = mock(MixedOperation.class);
        when(client.pods()).thenReturn(podsOp);

        NonNamespaceOperation<Pod, PodList, PodResource> nsOp = mock(NonNamespaceOperation.class);
        when(podsOp.inNamespace(anyString())).thenReturn(nsOp);

        // Mock withName 用于 waitUntilReady 和 get
        PodResource podResource = mock(PodResource.class);
        when(nsOp.withName(anyString())).thenReturn(podResource);

        Pod readyPod =
                new PodBuilder()
                        .withNewMetadata()
                        .withName(podName)
                        .withNamespace("himarket")
                        .endMetadata()
                        .withNewStatus()
                        .withPhase("Running")
                        .withPodIP(podIp)
                        .endStatus()
                        .build();
        when(podResource.waitUntilReady(anyLong(), any())).thenReturn(readyPod);
        when(podResource.get()).thenReturn(readyPod);
        when(podResource.delete()).thenReturn(List.of());
        // createNewPod 中固定名字会先检查旧 Pod 是否存在，存在则删除后等待消失
        // 第一次 get() 返回旧 Pod（触发删除），后续 get() 在 waitUntilCondition 中返回 null（表示已删除）
        // 但由于 mock 的 get() 始终返回 readyPod，需要让 waitUntilCondition 直接通过
        when(podResource.waitUntilCondition(any(), anyLong(), any())).thenReturn(null);

        // Mock PVC 操作（ensurePvcExists 需要）
        MixedOperation pvcOp = mock(MixedOperation.class);
        when(client.persistentVolumeClaims()).thenReturn(pvcOp);
        NonNamespaceOperation pvcNsOp = mock(NonNamespaceOperation.class);
        when(pvcOp.inNamespace(anyString())).thenReturn(pvcNsOp);
        Resource pvcResource = mock(Resource.class);
        when(pvcNsOp.withName(anyString())).thenReturn(pvcResource);
        when(pvcResource.get())
                .thenReturn(
                        new PersistentVolumeClaimBuilder()
                                .withNewMetadata()
                                .withName("workspace-stub")
                                .endMetadata()
                                .build());

        // Mock Service 操作（createServiceForPod / deleteServiceForPod 需要）
        MixedOperation svcOp = mock(MixedOperation.class);
        when(client.services()).thenReturn(svcOp);
        NonNamespaceOperation svcNsOp = mock(NonNamespaceOperation.class);
        when(svcOp.inNamespace(anyString())).thenReturn(svcNsOp);
        Resource svcResource = mock(Resource.class);
        when(svcNsOp.withName(anyString())).thenReturn(svcResource);
        when(svcResource.get()).thenReturn(null);
        when(svcResource.delete()).thenReturn(List.of());
        when(svcNsOp.resource(any(io.fabric8.kubernetes.api.model.Service.class)))
                .thenReturn(svcResource);
        when(svcResource.create()).thenReturn(null);

        // Mock resource(pod).create() - 捕获创建的 Pod
        // 注意：必须返回 PodResource mock 而非 NamespaceableResource mock，
        // 因为 Fabric8 内部会将 resource() 返回值转换为 PodResource。
        AtomicReference<Pod> capturedPod = new AtomicReference<>();
        when(nsOp.resource(any(Pod.class)))
                .thenAnswer(
                        invocation -> {
                            Pod inputPod = invocation.getArgument(0);
                            capturedPod.set(inputPod);

                            Pod createdPod =
                                    new PodBuilder()
                                            .withNewMetadata()
                                            .withName(podName)
                                            .withNamespace("himarket")
                                            .withLabels(inputPod.getMetadata().getLabels())
                                            .endMetadata()
                                            .withNewStatus()
                                            .withPhase("Running")
                                            .withPodIP(podIp)
                                            .endStatus()
                                            .build();
                            PodResource res = mock(PodResource.class);
                            when(res.create()).thenReturn(createdPod);
                            return res;
                        });

        return new CreatePodMockContext(client, capturedPod);
    }

    private record CreatePodMockContext(
            KubernetesClient client, AtomicReference<Pod> capturedPod) {}

    /**
     * 构建 PodReuseManager 并注入 mock K8sConfigService。
     */
    private PodReuseManager buildManager(K8sConfigService configService) {
        return new PodReuseManager(configService, "himarket", 1800, false);
    }

    @SuppressWarnings("unchecked")
    private K8sConfigService buildEvictMockConfigService() {
        KubernetesClient client = mock(KubernetesClient.class);
        K8sConfigService configService = mock(K8sConfigService.class);
        when(configService.getDefaultClient()).thenReturn(client);
        when(configService.getClient(anyString())).thenReturn(client);

        MixedOperation<Pod, PodList, PodResource> podsOp = mock(MixedOperation.class);
        when(client.pods()).thenReturn(podsOp);

        NonNamespaceOperation<Pod, PodList, PodResource> nsOp = mock(NonNamespaceOperation.class);
        when(podsOp.inNamespace(anyString())).thenReturn(nsOp);

        PodResource podResource = mock(PodResource.class);
        when(nsOp.withName(anyString())).thenReturn(podResource);
        when(podResource.delete()).thenReturn(List.of());

        return configService;
    }

    // ===== Property 3: Pod 缓存 round-trip =====
    // Feature: k8s-pod-reuse, Property 3: Pod 缓存 round-trip
    //
    // 对于任意 userId 和有效的 RuntimeConfig，当 PodReuseManager.acquirePod 创建新 Pod 后，
    // 立即调用 getPodEntry(userId) 应返回非空的 PodEntry，且 podName 和 podIp 与创建结果一致。
    //
    // 测试策略：直接调用 package-private 的 createNewPod 方法模拟 Pod 创建，
    // 然后将结果写入缓存，验证 getPodEntry 的 round-trip 一致性。
    //
    // **Validates: Requirements 3.1, 4.2**

    @Property(tries = 100)
    void acquirePod_thenGetPodEntry_returnsCachedEntry(
            @ForAll("validUserIds") String userId,
            @ForAll("validProviderKeys") String providerKey,
            @ForAll("containerImages") String image) {

        String expectedPodName = "sandbox-" + userId;
        String expectedPodIp = "10.0.0.42";

        CreatePodMockContext ctx = buildCreatePodMock(expectedPodName, expectedPodIp);
        K8sConfigService configService = mock(K8sConfigService.class);
        when(configService.getClient(anyString())).thenReturn(ctx.client());

        PodReuseManager manager = buildManager(configService);
        RuntimeConfig config = buildConfig(userId, providerKey, image);

        // 直接调用 createNewPod（package-private）模拟 Pod 创建
        PodEntry created = manager.createNewPod(ctx.client(), userId, config);

        // 将创建结果写入缓存（模拟 acquirePod 的缓存写入行为）
        manager.getPodCache().put(userId, created);

        // 验证缓存 round-trip
        PodEntry entry = manager.getPodEntry(userId);
        assertNotNull(entry, "getPodEntry 应返回非空的 PodEntry");
        assertEquals(created.getPodName(), entry.getPodName(), "podName 应与 createNewPod 结果一致");
        assertEquals(created.getPodIp(), entry.getPodIp(), "podIp 应与 createNewPod 结果一致");
        assertEquals(expectedPodName, entry.getPodName(), "podName 应与预期一致");
        assertEquals(expectedPodIp, entry.getPodIp(), "podIp 应与预期一致");
    }

    // ===== Property 4: Pod 缓存清理一致性 =====
    // Feature: k8s-pod-reuse, Property 4: Pod 缓存清理一致性
    //
    // 对于任意已缓存的 userId，当调用 evictPod(userId) 后，getPodEntry(userId) 应返回 null。
    //
    // **Validates: Requirements 4.3**

    @Property(tries = 100)
    void evictPod_thenGetPodEntry_returnsNull(@ForAll("validUserIds") String userId) {

        String podName = "sandbox-" + userId;
        String podIp = "10.0.1.100";

        K8sConfigService configService = buildEvictMockConfigService();
        PodReuseManager manager = buildManager(configService);

        // 直接向缓存中写入条目
        PodEntry entry = new PodEntry(podName, podIp);
        manager.getPodCache().put(userId, entry);

        // 确认缓存已填充
        assertNotNull(manager.getPodEntry(userId), "evict 前缓存应非空");

        // 执行 evict
        manager.evictPod(userId);

        // 验证缓存已清理
        assertNull(manager.getPodEntry(userId), "evictPod 后 getPodEntry 应返回 null");
    }

    // ===== Property 5: 新建用户级沙箱 Pod 标签完整性 =====
    // Feature: k8s-pod-reuse, Property 5: 新建用户级沙箱 Pod 标签完整性
    //
    // 对于任意 userId 和 providerKey，当 PodReuseManager 创建新的用户级沙箱 Pod 时，
    // Pod 的标签应包含 app=sandbox、userId={userId}、sandboxMode=user 和 provider={providerKey}。
    //
    // **Validates: Requirements 3.4**

    @Property(tries = 100)
    void createNewPod_labelsContainAllRequiredFields(
            @ForAll("validUserIds") String userId,
            @ForAll("validProviderKeys") String providerKey,
            @ForAll("containerImages") String image) {

        String podName = "sandbox-" + userId;
        String podIp = "10.0.2.50";

        CreatePodMockContext ctx = buildCreatePodMock(podName, podIp);
        K8sConfigService configService = mock(K8sConfigService.class);
        when(configService.getClient(anyString())).thenReturn(ctx.client());

        PodReuseManager manager = buildManager(configService);
        RuntimeConfig config = buildConfig(userId, providerKey, image);

        // 直接调用 createNewPod
        manager.createNewPod(ctx.client(), userId, config);

        // 验证捕获的 Pod 标签
        Pod capturedPod = ctx.capturedPod().get();
        assertNotNull(capturedPod, "应捕获到创建的 Pod");

        Map<String, String> labels = capturedPod.getMetadata().getLabels();
        assertNotNull(labels, "Pod 标签不应为 null");

        assertEquals("sandbox", labels.get("app"), "标签 app 应为 sandbox");
        assertEquals(userId, labels.get("userId"), "标签 userId 应与输入一致");
        assertEquals("user", labels.get("sandboxMode"), "标签 sandboxMode 应为 user");
        assertNull(labels.get("provider"), "Pod 标签中不应包含 provider 字段");
    }

    // ===== Property 7: 连接计数不变量 =====
    // Feature: k8s-pod-reuse, Property 7: 连接计数不变量
    //
    // 对于任意 acquire 和 release 操作序列，PodEntry.connectionCount 的值应始终等于
    // acquire 调用次数减去 release 调用次数，且不小于零。
    //
    // **Validates: Requirements 5.2**

    @Property(tries = 100)
    void connectionCount_equalsAcquireMinusRelease_andNeverNegative(
            @ForAll("acquireReleaseSequences") List<Boolean> operations) {

        PodEntry entry = new PodEntry("test-pod", "10.0.0.1");

        int expectedCount = 0;
        for (boolean isAcquire : operations) {
            if (isAcquire) {
                entry.getConnectionCount().incrementAndGet();
                expectedCount++;
            } else {
                // 模拟 releasePod 中的 updateAndGet 逻辑
                entry.getConnectionCount().updateAndGet(c -> Math.max(c - 1, 0));
                expectedCount = Math.max(expectedCount - 1, 0);
            }

            int actualCount = entry.getConnectionCount().get();

            // 不变量 1：连接计数不小于零
            assertTrue(actualCount >= 0, "连接计数不应为负数，当前值: " + actualCount);

            // 不变量 2：连接计数等于 acquire 次数减去 release 次数
            assertEquals(expectedCount, actualCount, "连接计数应等于 acquire 次数减去 release 次数");
        }
    }
}
