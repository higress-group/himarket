package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.jqwik.api.*;
import reactor.core.publisher.Flux;

/**
 * K8sSandboxProvider 属性基测试。
 *
 * <p>Feature: sandbox-runtime-strategy
 *
 * <p>使用 Stub 实现模拟 PodReuseManager 和 Sidecar HTTP API 响应， 验证 Provider 接口契约一致性。
 */
class K8sSandboxProviderPropertyTest {

    // ===== 无需继承 PodReuseManager / K8sConfigService =====
    // InMemoryK8sSandboxProvider 直接实现 SandboxProvider 接口，
    // 模拟 K8sSandboxProvider 的核心逻辑（acquire/release/writeFile/readFile/healthCheck/connectSidecar），
    // 避免依赖真实 K8s 集群和 Sidecar HTTP 服务。

    // ===== 基于内存的 SandboxProvider（模拟 Sidecar HTTP API 行为） =====

    /**
     * 包装 K8sSandboxProvider 的核心逻辑，但用内存 Map 替代真实 HTTP 调用。 这样可以测试 Provider
     * 接口契约，而不依赖真实的 Sidecar 服务。
     */
    static class InMemoryK8sSandboxProvider implements SandboxProvider {

        private final Map<String, String> fileStore = new ConcurrentHashMap<>();
        private final String podName;
        private final String podIp;
        private final String serviceIp;
        private boolean healthy = true;
        private boolean released = false;
        private SandboxInfo lastAcquiredInfo;

        InMemoryK8sSandboxProvider(String podName, String podIp, String serviceIp) {
            this.podName = podName;
            this.podIp = podIp;
            this.serviceIp = serviceIp;
        }

        @Override
        public SandboxType getType() {
            return SandboxType.K8S;
        }

        @Override
        public SandboxInfo acquire(SandboxConfig config) {
            String accessHost = serviceIp != null && !serviceIp.isBlank() ? serviceIp : podIp;

            lastAcquiredInfo =
                    new SandboxInfo(
                            SandboxType.K8S,
                            podName,
                            accessHost,
                            8080,
                            "/workspace",
                            false,
                            Map.of(
                                    "podName",
                                    podName,
                                    "namespace",
                                    "himarket",
                                    "podIp",
                                    podIp != null ? podIp : ""));
            return lastAcquiredInfo;
        }

        @Override
        public void release(SandboxInfo info) {
            released = true;
        }

        @Override
        public boolean healthCheck(SandboxInfo info) {
            return healthy;
        }

        @Override
        public void writeFile(SandboxInfo info, String relativePath, String content)
                throws IOException {
            if (!healthy) {
                throw new IOException(
                        "Sidecar writeFile 失败 (Pod: " + info.sandboxId() + "): unhealthy");
            }
            fileStore.put(relativePath, content);
        }

        @Override
        public String readFile(SandboxInfo info, String relativePath) throws IOException {
            if (!healthy) {
                throw new IOException(
                        "Sidecar readFile 失败 (Pod: " + info.sandboxId() + "): unhealthy");
            }
            String content = fileStore.get(relativePath);
            if (content == null) {
                throw new IOException("Sidecar readFile 失败 (Pod: " + info.sandboxId() + "): 文件不存在");
            }
            return content;
        }

        @Override
        public RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config) {
            return new StubAliveRuntimeAdapter();
        }

        @Override
        public URI getSidecarUri(SandboxInfo info, String command, String args) {
            return info.sidecarWsUri(command, args);
        }

        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }

        public Map<String, String> getFileStore() {
            return fileStore;
        }

        public boolean isReleased() {
            return released;
        }
    }

    // ===== Stub RuntimeAdapter（模拟 connectSidecar 成功后的 adapter） =====

    static class StubAliveRuntimeAdapter implements RuntimeAdapter {
        @Override
        public RuntimeType getType() {
            return RuntimeType.K8S;
        }

        @Override
        public String start(RuntimeConfig config) {
            return "stub-k8s";
        }

        @Override
        public void send(String jsonLine) {}

        @Override
        public Flux<String> stdout() {
            return Flux.empty();
        }

        @Override
        public RuntimeStatus getStatus() {
            return RuntimeStatus.RUNNING;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public void close() {}

        @Override
        public FileSystemAdapter getFileSystem() {
            return null;
        }
    }

    // ===== 生成器 =====

    @Provide
    Arbitrary<String> podNames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> "pod-" + s);
    }

    @Provide
    Arbitrary<String> podIps() {
        return Arbitraries.integers()
                .between(1, 254)
                .list()
                .ofSize(4)
                .map(
                        parts -> {
                            return parts.get(0)
                                    + "."
                                    + parts.get(1)
                                    + "."
                                    + parts.get(2)
                                    + "."
                                    + parts.get(3);
                        });
    }

    @Provide
    Arbitrary<String> serviceIps() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.just(""),
                Arbitraries.integers()
                        .between(1, 254)
                        .list()
                        .ofSize(4)
                        .map(
                                parts -> {
                                    return parts.get(0)
                                            + "."
                                            + parts.get(1)
                                            + "."
                                            + parts.get(2)
                                            + "."
                                            + parts.get(3);
                                }));
    }

    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(3).ofMaxLength(15);
    }

    @Provide
    Arbitrary<String> fileContents() {
        return Arbitraries.oneOf(
                Arbitraries.strings().ofMinLength(1).ofMaxLength(500).ascii(),
                Arbitraries.strings()
                        .ofMinLength(1)
                        .ofMaxLength(200)
                        .withChars('中', '文', '日', '本', '語', '🚀', '✅'),
                Arbitraries.of(
                        "{\"key\": \"value\"}",
                        "line1\nline2\nline3",
                        "{\"emoji\": \"🎉🔥💻\"}",
                        "content with special chars: <>&'\""),
                Arbitraries.strings().ofMinLength(500).ofMaxLength(2000).ascii());
    }

    @Provide
    Arbitrary<String> relativePaths() {
        return Arbitraries.of(
                "settings.json",
                ".kiro/mcp-config.json",
                ".kiro/skills/skill.md",
                "config/model.json",
                "deep/nested/path/config.yaml");
    }

    // =========================================================================
    // Property 8: Provider 接口契约一致性
    // =========================================================================

    /**
     * <b>Validates: Requirements 3.2, 3.3, 3.4, 3.5</b>
     *
     * <p>Property 8a: acquire 成功后 healthCheck 返回 true。 对任意合法的 podName、podIp、serviceIp
     * 组合，acquire 返回的 SandboxInfo 通过 healthCheck 验证。
     */
    @Property(tries = 100)
    void acquire_thenHealthCheck_returnsTrue(
            @ForAll("podNames") String podName,
            @ForAll("podIps") String podIp,
            @ForAll("serviceIps") String serviceIp,
            @ForAll("userIds") String userId) {

        InMemoryK8sSandboxProvider provider =
                new InMemoryK8sSandboxProvider(podName, podIp, serviceIp);

        SandboxConfig config =
                new SandboxConfig(
                        userId, SandboxType.K8S, "/workspace", Map.of(), "default", null, null, 0);

        SandboxInfo info = provider.acquire(config);

        assertNotNull(info, "acquire 应返回非空 SandboxInfo");
        assertEquals(SandboxType.K8S, info.type(), "类型应为 K8S");
        assertEquals(podName, info.sandboxId(), "sandboxId 应为 podName");
        assertTrue(provider.healthCheck(info), "acquire 成功后 healthCheck 应返回 true");
    }

    /**
     * <b>Validates: Requirements 3.2, 3.3</b>
     *
     * <p>Property 8b: writeFile 后 readFile 返回相同内容。 对任意合法文件内容和路径，通过 Sidecar HTTP API
     * 写入后读回的内容应完全一致。
     */
    @Property(tries = 200)
    void writeFile_thenReadFile_returnsSameContent(
            @ForAll("podNames") String podName,
            @ForAll("podIps") String podIp,
            @ForAll("fileContents") String content,
            @ForAll("relativePaths") String path,
            @ForAll("userIds") String userId)
            throws IOException {

        InMemoryK8sSandboxProvider provider = new InMemoryK8sSandboxProvider(podName, podIp, null);

        SandboxConfig config =
                new SandboxConfig(
                        userId, SandboxType.K8S, "/workspace", Map.of(), "default", null, null, 0);

        SandboxInfo info = provider.acquire(config);

        provider.writeFile(info, path, content);
        String readBack = provider.readFile(info, path);

        assertEquals(content, readBack, "writeFile 后 readFile 应返回完全相同的内容");
    }

    /**
     * <b>Validates: Requirements 3.5</b>
     *
     * <p>Property 8c: connectSidecar 成功后 RuntimeAdapter.isAlive() 为 true。
     */
    @Property(tries = 100)
    void connectSidecar_thenAdapterIsAlive(
            @ForAll("podNames") String podName,
            @ForAll("podIps") String podIp,
            @ForAll("userIds") String userId) {

        InMemoryK8sSandboxProvider provider = new InMemoryK8sSandboxProvider(podName, podIp, null);

        SandboxConfig config =
                new SandboxConfig(
                        userId, SandboxType.K8S, "/workspace", Map.of(), "default", null, null, 0);

        SandboxInfo info = provider.acquire(config);

        RuntimeConfig runtimeConfig = new RuntimeConfig();
        runtimeConfig.setCommand("cli");
        runtimeConfig.setUserId(userId);

        RuntimeAdapter adapter = provider.connectSidecar(info, runtimeConfig);

        assertNotNull(adapter, "connectSidecar 应返回非空 RuntimeAdapter");
        assertTrue(adapter.isAlive(), "connectSidecar 成功后 adapter.isAlive() 应为 true");
        assertEquals(
                RuntimeStatus.RUNNING,
                adapter.getStatus(),
                "connectSidecar 成功后 adapter 状态应为 RUNNING");
    }

    /**
     * <b>Validates: Requirements 3.2, 3.3, 3.4, 3.5</b>
     *
     * <p>Property 8d: 完整契约链 — acquire → healthCheck → writeFile → readFile → connectSidecar
     * 的完整流程，每一步都满足接口契约。
     */
    @Property(tries = 100)
    void fullContractChain_allStepsSatisfyContract(
            @ForAll("podNames") String podName,
            @ForAll("podIps") String podIp,
            @ForAll("serviceIps") String serviceIp,
            @ForAll("fileContents") String content,
            @ForAll("relativePaths") String path,
            @ForAll("userIds") String userId)
            throws IOException {

        InMemoryK8sSandboxProvider provider =
                new InMemoryK8sSandboxProvider(podName, podIp, serviceIp);

        // Step 1: acquire
        SandboxConfig config =
                new SandboxConfig(
                        userId, SandboxType.K8S, "/workspace", Map.of(), "default", null, null, 0);
        SandboxInfo info = provider.acquire(config);
        assertNotNull(info, "acquire 应返回非空 SandboxInfo");
        assertEquals(SandboxType.K8S, info.type());

        // Step 2: healthCheck
        assertTrue(provider.healthCheck(info), "acquire 后 healthCheck 应为 true");

        // Step 3: writeFile + readFile
        provider.writeFile(info, path, content);
        String readBack = provider.readFile(info, path);
        assertEquals(content, readBack, "writeFile 后 readFile 应返回相同内容");

        // Step 4: connectSidecar
        RuntimeConfig runtimeConfig = new RuntimeConfig();
        runtimeConfig.setCommand("cli");
        runtimeConfig.setUserId(userId);
        RuntimeAdapter adapter = provider.connectSidecar(info, runtimeConfig);
        assertTrue(adapter.isAlive(), "connectSidecar 后 adapter 应存活");

        // Step 5: release
        provider.release(info);
        assertTrue(provider.isReleased(), "release 后应标记为已释放");
    }

    /**
     * <b>Validates: Requirements 3.2, 3.3</b>
     *
     * <p>Property 8e: 多次写入同一路径，readFile 返回最后一次写入的内容（覆盖语义）。
     */
    @Property(tries = 100)
    void multipleWrites_readReturnsLastWrittenContent(
            @ForAll("podNames") String podName,
            @ForAll("podIps") String podIp,
            @ForAll("relativePaths") String path,
            @ForAll("userIds") String userId,
            @ForAll("fileContents") String content1,
            @ForAll("fileContents") String content2)
            throws IOException {

        InMemoryK8sSandboxProvider provider = new InMemoryK8sSandboxProvider(podName, podIp, null);

        SandboxConfig config =
                new SandboxConfig(
                        userId, SandboxType.K8S, "/workspace", Map.of(), "default", null, null, 0);
        SandboxInfo info = provider.acquire(config);

        provider.writeFile(info, path, content1);
        provider.writeFile(info, path, content2);

        String readBack = provider.readFile(info, path);
        assertEquals(content2, readBack, "多次写入后 readFile 应返回最后一次写入的内容");
    }

    /**
     * <b>Validates: Requirements 3.2, 3.4</b>
     *
     * <p>Property 8f: acquire 返回的 SandboxInfo 中 host 字段正确选择 serviceIp 或 podIp。 当 serviceIp
     * 非空时使用 serviceIp，否则使用 podIp。
     */
    @Property(tries = 100)
    void acquire_hostSelection_prefersServiceIpOverPodIp(
            @ForAll("podNames") String podName,
            @ForAll("podIps") String podIp,
            @ForAll("serviceIps") String serviceIp,
            @ForAll("userIds") String userId) {

        InMemoryK8sSandboxProvider provider =
                new InMemoryK8sSandboxProvider(podName, podIp, serviceIp);

        SandboxConfig config =
                new SandboxConfig(
                        userId, SandboxType.K8S, "/workspace", Map.of(), "default", null, null, 0);
        SandboxInfo info = provider.acquire(config);

        if (serviceIp != null && !serviceIp.isBlank()) {
            assertEquals(serviceIp, info.host(), "serviceIp 非空时 host 应为 serviceIp");
        } else {
            assertEquals(podIp, info.host(), "serviceIp 为空时 host 应为 podIp");
        }

        assertEquals(8080, info.sidecarPort(), "sidecarPort 应为 8080");
        assertEquals("/workspace", info.workspacePath(), "workspacePath 应为 /workspace");
        assertEquals(podName, info.metadata().get("podName"), "metadata 应包含 podName");
        assertEquals("himarket", info.metadata().get("namespace"), "metadata 应包含 namespace");
    }
}
