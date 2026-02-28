package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PodReuseManager 单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>缓存命中且 Pod 健康 → 直接返回缓存 Pod</li>
 *   <li>缓存未命中 → K8s API 回退查询或创建新 Pod</li>
 *   <li>健康检查失败 → 清理缓存并创建新 Pod</li>
 *   <li>空闲超时触发 Pod 删除</li>
 *   <li>空闲期间新连接取消计时器</li>
 * </ul>
 *
 * <p>Requirements: 3.5, 3.6, 5.3, 5.4, 5.5
 */
class PodReuseManagerTest {

    private KubernetesClient k8sClient;
    private K8sConfigService k8sConfigService;
    private PodReuseManager manager;

    @SuppressWarnings("unchecked")
    private MixedOperation<Pod, PodList, PodResource> podsOp;

    @SuppressWarnings("unchecked")
    private NonNamespaceOperation<Pod, PodList, PodResource> nsOp;

    private PodResource podResource;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        k8sClient = mock(KubernetesClient.class);
        k8sConfigService = mock(K8sConfigService.class);
        when(k8sConfigService.getClient(anyString())).thenReturn(k8sClient);
        when(k8sConfigService.getClient(isNull())).thenReturn(k8sClient);

        podsOp = mock(MixedOperation.class);
        when(k8sClient.pods()).thenReturn(podsOp);

        nsOp = mock(NonNamespaceOperation.class);
        when(podsOp.inNamespace(anyString())).thenReturn(nsOp);

        podResource = mock(PodResource.class);
        when(nsOp.withName(anyString())).thenReturn(podResource);
        when(podResource.delete()).thenReturn(List.of());

        // Mock PVC 操作（ensurePvcExists 需要）
        MixedOperation pvcOp = mock(MixedOperation.class);
        when(k8sClient.persistentVolumeClaims()).thenReturn(pvcOp);
        NonNamespaceOperation pvcNsOp = mock(NonNamespaceOperation.class);
        when(pvcOp.inNamespace(anyString())).thenReturn(pvcNsOp);
        // 默认 PVC 已存在，避免触发创建逻辑
        Resource pvcResource = mock(Resource.class);
        when(pvcNsOp.withName(anyString())).thenReturn(pvcResource);
        when(pvcResource.get())
                .thenReturn(
                        new PersistentVolumeClaimBuilder()
                                .withNewMetadata()
                                .withName("workspace-stub")
                                .endMetadata()
                                .build());

        // 默认使用较短的空闲超时（1秒）方便测试，关闭 Service 访问模式避免 mock Service
        manager = new PodReuseManager(k8sConfigService, "himarket", 1, false);
    }

    private RuntimeConfig buildConfig(String userId) {
        RuntimeConfig config = new RuntimeConfig();
        config.setUserId(userId);
        config.setProviderKey("test-provider");
        config.setContainerImage("himarket/sandbox:latest");
        config.setCommand("/bin/bash");
        config.setArgs(List.of("--login"));
        config.setK8sConfigId("test-config");
        return config;
    }

    private Pod buildRunningPod(String podName, String podIp) {
        return new PodBuilder()
                .withNewMetadata()
                .withName(podName)
                .withNamespace("himarket")
                .endMetadata()
                .withNewStatus()
                .withPhase("Running")
                .withPodIP(podIp)
                .endStatus()
                .build();
    }

    private Pod buildRunningPodWithLabels(
            String podName, String podIp, Map<String, String> labels) {
        return new PodBuilder()
                .withNewMetadata()
                .withName(podName)
                .withNamespace("himarket")
                .withLabels(labels)
                .endMetadata()
                .withNewStatus()
                .withPhase("Running")
                .withPodIP(podIp)
                .endStatus()
                .build();
    }

    @SuppressWarnings("unchecked")
    private void mockCreatePod(String podName, String podIp) {
        AtomicReference<Pod> capturedPod = new AtomicReference<>();
        when(nsOp.resource(any(Pod.class)))
                .thenAnswer(
                        invocation -> {
                            Pod inputPod = invocation.getArgument(0);
                            capturedPod.set(inputPod);
                            Pod createdPod =
                                    buildRunningPodWithLabels(
                                            podName, podIp, inputPod.getMetadata().getLabels());
                            PodResource res = mock(PodResource.class);
                            when(res.create()).thenReturn(createdPod);
                            return res;
                        });

        Pod readyPod = buildRunningPod(podName, podIp);
        when(podResource.waitUntilReady(anyLong(), any())).thenReturn(readyPod);
        when(podResource.get()).thenReturn(readyPod);
        // createNewPod 中固定名字会先检查旧 Pod 是否存在，存在则删除后等待消失
        when(podResource.waitUntilCondition(any(), anyLong(), any())).thenReturn(null);
    }

    @SuppressWarnings("unchecked")
    private void mockLabelSelectorReturnsEmpty() {
        FilterWatchListDeletable<Pod, PodList, PodResource> filtered =
                mock(FilterWatchListDeletable.class);
        when(nsOp.withLabels(anyMap())).thenReturn(filtered);
        when(filtered.list()).thenReturn(new PodListBuilder().withItems(List.of()).build());
    }

    @SuppressWarnings("unchecked")
    private void mockLabelSelectorReturnsPod(String podName, String podIp) {
        FilterWatchListDeletable<Pod, PodList, PodResource> filtered =
                mock(FilterWatchListDeletable.class);
        when(nsOp.withLabels(anyMap())).thenReturn(filtered);
        Pod pod = buildRunningPod(podName, podIp);
        when(filtered.list()).thenReturn(new PodListBuilder().withItems(List.of(pod)).build());
    }

    // ===== 1. 缓存命中场景 =====

    @Test
    @DisplayName("缓存命中且 Pod 健康 → 直接返回缓存 Pod，标记 reused=true")
    void acquirePod_cacheHitAndHealthy_returnsCachedPod() {
        String userId = "user1";
        String podName = "sandbox-user1-abc";
        String podIp = "10.0.0.1";

        // 预填充缓存
        PodEntry entry = new PodEntry(podName, podIp);
        manager.getPodCache().put(userId, entry);

        // Mock 健康检查返回 Running
        when(podResource.get()).thenReturn(buildRunningPod(podName, podIp));

        PodInfo result = manager.acquirePod(userId, buildConfig(userId));

        assertTrue(result.reused(), "应标记为复用");
        assertEquals(podName, result.podName());
        assertEquals(podIp, result.podIp());
        assertEquals(1, entry.getConnectionCount().get(), "连接计数应递增为 1");
    }

    @Test
    @DisplayName("缓存命中且 Pod 健康 → 多次 acquire 连接计数递增")
    void acquirePod_cacheHitMultipleTimes_connectionCountIncrements() {
        String userId = "user2";
        String podName = "sandbox-user2-abc";
        String podIp = "10.0.0.2";

        PodEntry entry = new PodEntry(podName, podIp);
        manager.getPodCache().put(userId, entry);
        when(podResource.get()).thenReturn(buildRunningPod(podName, podIp));

        manager.acquirePod(userId, buildConfig(userId));
        manager.acquirePod(userId, buildConfig(userId));
        manager.acquirePod(userId, buildConfig(userId));

        assertEquals(3, entry.getConnectionCount().get(), "三次 acquire 后连接计数应为 3");
    }

    // ===== 2. 缓存未命中场景 =====

    @Test
    @DisplayName("缓存未命中 + K8s API 查到 Running Pod → 回填缓存并返回")
    void acquirePod_cacheMiss_k8sApiHit_returnsPodAndFillsCache() {
        String userId = "user3";
        String podName = "sandbox-user3-existing";
        String podIp = "10.0.0.3";

        mockLabelSelectorReturnsPod(podName, podIp);

        PodInfo result = manager.acquirePod(userId, buildConfig(userId));

        assertTrue(result.reused(), "K8s API 回退命中应标记为复用");
        assertEquals(podName, result.podName());
        assertEquals(podIp, result.podIp());

        // 验证缓存已回填
        PodEntry cached = manager.getPodEntry(userId);
        assertNotNull(cached, "缓存应已回填");
        assertEquals(podName, cached.getPodName());
        assertEquals(1, cached.getConnectionCount().get());
    }

    @Test
    @DisplayName("缓存未命中 + K8s API 未找到 → 创建新 Pod")
    void acquirePod_cacheMiss_k8sApiMiss_createsNewPod() {
        String userId = "user4";
        String newPodName = "sandbox-user4";
        String newPodIp = "10.0.0.4";

        mockLabelSelectorReturnsEmpty();
        mockCreatePod(newPodName, newPodIp);

        PodInfo result = manager.acquirePod(userId, buildConfig(userId));

        assertFalse(result.reused(), "新创建的 Pod 不应标记为复用");
        assertEquals(newPodName, result.podName());
        assertEquals(newPodIp, result.podIp());

        PodEntry cached = manager.getPodEntry(userId);
        assertNotNull(cached, "新 Pod 应写入缓存");
        assertEquals(1, cached.getConnectionCount().get());
    }

    // ===== 3. 健康检查失败后的清理和重建 =====

    @Test
    @DisplayName("缓存命中但 Pod 不健康 → 清理缓存并创建新 Pod")
    void acquirePod_cacheHitButUnhealthy_cleansUpAndCreatesNew() {
        String userId = "user5";
        String oldPodName = "sandbox-user5-old";
        String oldPodIp = "10.0.0.5";
        String newPodName = "sandbox-user5";
        String newPodIp = "10.0.0.55";

        // 预填充缓存（旧 Pod）
        PodEntry oldEntry = new PodEntry(oldPodName, oldPodIp);
        manager.getPodCache().put(userId, oldEntry);

        // Mock 健康检查：旧 Pod 返回 Failed 状态
        Pod failedPod =
                new PodBuilder()
                        .withNewMetadata()
                        .withName(oldPodName)
                        .endMetadata()
                        .withNewStatus()
                        .withPhase("Failed")
                        .endStatus()
                        .build();
        when(podResource.get())
                .thenAnswer(
                        invocation -> {
                            // 第一次调用返回 Failed（健康检查），后续返回新 Pod
                            return failedPod;
                        });

        // Mock K8s API 回退查询返回空（无可复用 Pod）
        mockLabelSelectorReturnsEmpty();

        // Mock 创建新 Pod
        mockCreatePod(newPodName, newPodIp);
        // 覆盖 podResource.get() 使 createNewPod 中获取到新 Pod
        Pod newReadyPod = buildRunningPod(newPodName, newPodIp);
        when(podResource.get()).thenReturn(failedPod).thenReturn(newReadyPod);

        PodInfo result = manager.acquirePod(userId, buildConfig(userId));

        assertFalse(result.reused(), "健康检查失败后创建的新 Pod 不应标记为复用");
        assertEquals(newPodName, result.podName());

        // 验证旧 Pod 被删除
        verify(podResource, atLeastOnce()).delete();
    }

    @Test
    @DisplayName("缓存命中但 Pod 不存在（get 返回 null）→ 视为不健康，创建新 Pod")
    void acquirePod_cacheHitButPodGone_createsNewPod() {
        String userId = "user6";
        String oldPodName = "sandbox-user6-gone";
        String newPodName = "sandbox-user6";
        String newPodIp = "10.0.0.6";

        PodEntry oldEntry = new PodEntry(oldPodName, "10.0.0.60");
        manager.getPodCache().put(userId, oldEntry);

        // 健康检查：Pod 已不存在
        when(podResource.get()).thenReturn(null);

        mockLabelSelectorReturnsEmpty();
        mockCreatePod(newPodName, newPodIp);
        // 覆盖 get() 使 createNewPod 获取到新 Pod
        when(podResource.get()).thenReturn(null).thenReturn(buildRunningPod(newPodName, newPodIp));

        PodInfo result = manager.acquirePod(userId, buildConfig(userId));

        assertFalse(result.reused());
        assertEquals(newPodName, result.podName());
    }

    // ===== 4. 空闲超时触发 Pod 删除 =====

    @Test
    @DisplayName("连接计数归零后，空闲超时到期应删除 Pod 并清理缓存")
    void releasePod_connectionCountZero_idleTimeoutDeletesPod() throws Exception {
        String userId = "user7";
        String podName = "sandbox-user7-idle";
        String podIp = "10.0.0.7";

        // 预填充缓存，连接计数=1
        PodEntry entry = new PodEntry(podName, podIp);
        entry.getConnectionCount().set(1);
        manager.getPodCache().put(userId, entry);

        // 释放连接 → 计数归零 → 启动空闲超时
        manager.releasePod(userId);

        assertEquals(0, entry.getConnectionCount().get(), "释放后连接计数应为 0");
        assertNotNull(entry.getIdleTimer(), "应启动空闲超时计时器");

        // 等待超时到期（manager 配置 idleTimeout=1s）
        Thread.sleep(2000);

        // 验证 Pod 已从缓存中移除
        assertNull(manager.getPodEntry(userId), "超时后缓存应已清理");

        // 验证 K8s Pod 删除被调用
        verify(podResource, atLeastOnce()).delete();
    }

    @Test
    @DisplayName("releasePod 对不存在的 userId 不抛异常")
    void releasePod_unknownUserId_doesNotThrow() {
        assertDoesNotThrow(() -> manager.releasePod("nonexistent-user"));
    }

    @Test
    @DisplayName("releasePod 连接计数不会降到负数")
    void releasePod_connectionCountNeverNegative() {
        String userId = "user8";
        PodEntry entry = new PodEntry("sandbox-user8", "10.0.0.8");
        entry.getConnectionCount().set(0);
        manager.getPodCache().put(userId, entry);

        manager.releasePod(userId);

        assertEquals(0, entry.getConnectionCount().get(), "连接计数不应为负数");
    }

    // ===== 5. 空闲期间新连接取消计时器 =====

    @Test
    @DisplayName("空闲超时期间有新连接 → 取消计时器，Pod 不被删除")
    void acquirePod_duringIdleTimeout_cancelsTimer() throws Exception {
        String userId = "user9";
        String podName = "sandbox-user9-cancel";
        String podIp = "10.0.0.9";

        // 预填充缓存，连接计数=1
        PodEntry entry = new PodEntry(podName, podIp);
        entry.getConnectionCount().set(1);
        manager.getPodCache().put(userId, entry);

        // 释放连接 → 启动空闲超时
        manager.releasePod(userId);
        assertEquals(0, entry.getConnectionCount().get());
        assertNotNull(entry.getIdleTimer(), "应启动空闲超时计时器");

        // 在超时到期前，新连接到来
        when(podResource.get()).thenReturn(buildRunningPod(podName, podIp));
        PodInfo result = manager.acquirePod(userId, buildConfig(userId));

        assertTrue(result.reused(), "应复用已有 Pod");
        assertEquals(1, entry.getConnectionCount().get(), "新连接后计数应为 1");

        // 等待超过空闲超时时间
        Thread.sleep(2000);

        // Pod 应仍在缓存中（因为连接计数非零，超时回调会跳过删除）
        assertNotNull(manager.getPodEntry(userId), "有活跃连接时 Pod 不应被删除");
    }

    @Test
    @DisplayName("空闲超时期间新连接取消计时器后再次释放 → 重新启动计时器")
    void releasePod_afterCancelledTimer_restartsTimer() throws Exception {
        String userId = "user10";
        String podName = "sandbox-user10-restart";
        String podIp = "10.0.0.10";

        PodEntry entry = new PodEntry(podName, podIp);
        entry.getConnectionCount().set(1);
        manager.getPodCache().put(userId, entry);

        // 第一次释放 → 启动计时器
        manager.releasePod(userId);
        ScheduledFuture<?> firstTimer = entry.getIdleTimer();
        assertNotNull(firstTimer);

        // 新连接到来 → 取消计时器
        when(podResource.get()).thenReturn(buildRunningPod(podName, podIp));
        manager.acquirePod(userId, buildConfig(userId));

        // 再次释放 → 应启动新的计时器
        manager.releasePod(userId);
        ScheduledFuture<?> secondTimer = entry.getIdleTimer();
        assertNotNull(secondTimer, "再次释放后应启动新的空闲计时器");

        // 等待超时
        Thread.sleep(2000);

        assertNull(manager.getPodEntry(userId), "第二次超时后缓存应已清理");
    }

    // ===== 参数校验 =====

    @Test
    @DisplayName("acquirePod userId 为空时抛出 IllegalArgumentException")
    void acquirePod_nullUserId_throwsException() {
        assertThrows(
                IllegalArgumentException.class, () -> manager.acquirePod(null, buildConfig("x")));
        assertThrows(
                IllegalArgumentException.class, () -> manager.acquirePod("", buildConfig("x")));
        assertThrows(
                IllegalArgumentException.class, () -> manager.acquirePod("   ", buildConfig("x")));
    }

    @Test
    @DisplayName("acquirePod config 为空时抛出 IllegalArgumentException")
    void acquirePod_nullConfig_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> manager.acquirePod("user", null));
    }
}
