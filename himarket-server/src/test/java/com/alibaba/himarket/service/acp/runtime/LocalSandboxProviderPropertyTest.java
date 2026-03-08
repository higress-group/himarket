package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.*;
import reactor.core.publisher.Flux;

/**
 * LocalSandboxProvider 属性基测试。
 *
 * <p>Feature: sandbox-runtime-strategy
 *
 * <p>使用 InMemory stub 实现模拟 LocalSandboxProvider 的核心逻辑：
 * 进程生命周期（alive/dead）、端口分配、复用逻辑、文件存储（HashMap），
 * 避免依赖真实 Node.js Sidecar 进程。
 */
class LocalSandboxProviderPropertyTest {

    // ===== InMemory Stub：模拟 LocalSandboxProvider 核心行为 =====

    /**
     * 模拟 LocalSandboxProvider 的核心逻辑，用内存结构替代真实进程和 HTTP 调用。
     * <ul>
     *   <li>进程生命周期：用 StubSidecarProcess 模拟 alive/dead 状态</li>
     *   <li>端口分配：AtomicInteger 自增分配，每个用户独立端口</li>
     *   <li>复用逻辑：同一用户进程存活时复用，返回 reused=true</li>
     *   <li>文件存储：ConcurrentHashMap 模拟 Sidecar HTTP API 的文件读写</li>
     * </ul>
     */
    static class InMemoryLocalSandboxProvider implements SandboxProvider {

        private final ConcurrentHashMap<String, StubSidecarProcess> sidecarProcesses =
                new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> fileStore = new ConcurrentHashMap<>();
        private final AtomicInteger portCounter;

        InMemoryLocalSandboxProvider(int startPort) {
            this.portCounter = new AtomicInteger(startPort);
        }

        @Override
        public SandboxType getType() {
            return SandboxType.LOCAL;
        }

        @Override
        public SandboxInfo acquire(SandboxConfig config) {
            String userId = config.userId();
            String cwd = config.workspacePath();

            // 检查可复用进程
            StubSidecarProcess existing = sidecarProcesses.get(userId);
            if (existing != null && existing.isAlive()) {
                return new SandboxInfo(
                        SandboxType.LOCAL,
                        "local-" + existing.port(),
                        "localhost",
                        existing.port(),
                        cwd,
                        true,
                        Map.of());
            }

            // 启动新进程
            int port =
                    config.localSidecarPort() > 0
                            ? config.localSidecarPort()
                            : portCounter.getAndIncrement();
            StubSidecarProcess process = new StubSidecarProcess(port);
            sidecarProcesses.put(userId, process);

            return new SandboxInfo(
                    SandboxType.LOCAL, "local-" + port, "localhost", port, cwd, false, Map.of());
        }

        @Override
        public void release(SandboxInfo info) {
            String targetPort = info.sandboxId().replace("local-", "");
            sidecarProcesses
                    .entrySet()
                    .removeIf(
                            entry -> {
                                StubSidecarProcess process = entry.getValue();
                                if (String.valueOf(process.port()).equals(targetPort)) {
                                    process.stop();
                                    return true;
                                }
                                return false;
                            });
        }

        @Override
        public boolean healthCheck(SandboxInfo info) {
            String targetPort = info.sandboxId().replace("local-", "");
            for (StubSidecarProcess process : sidecarProcesses.values()) {
                if (String.valueOf(process.port()).equals(targetPort)) {
                    return process.isAlive();
                }
            }
            return false;
        }

        @Override
        public void writeFile(SandboxInfo info, String relativePath, String content)
                throws IOException {
            if (!healthCheck(info)) {
                throw new IOException(
                        "Sidecar writeFile 失败 (Local: " + info.sandboxId() + "): 进程未存活");
            }
            fileStore.put(info.sandboxId() + ":" + relativePath, content);
        }

        @Override
        public String readFile(SandboxInfo info, String relativePath) throws IOException {
            if (!healthCheck(info)) {
                throw new IOException(
                        "Sidecar readFile 失败 (Local: " + info.sandboxId() + "): 进程未存活");
            }
            String content = fileStore.get(info.sandboxId() + ":" + relativePath);
            if (content == null) {
                throw new IOException(
                        "Sidecar readFile 失败 (Local: " + info.sandboxId() + "): 文件不存在");
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

        /** 获取指定用户的 Sidecar 进程（测试辅助） */
        StubSidecarProcess getProcess(String userId) {
            return sidecarProcesses.get(userId);
        }

        /** 获取所有进程映射（测试辅助） */
        ConcurrentHashMap<String, StubSidecarProcess> getProcesses() {
            return sidecarProcesses;
        }
    }

    // ===== Stub Sidecar 进程 =====

    static class StubSidecarProcess {
        private final int port;
        private boolean alive = true;

        StubSidecarProcess(int port) {
            this.port = port;
        }

        boolean isAlive() {
            return alive;
        }

        void stop() {
            alive = false;
        }

        int port() {
            return port;
        }
    }

    // ===== Stub RuntimeAdapter =====

    static class StubAliveRuntimeAdapter implements RuntimeAdapter {
        @Override
        public SandboxType getType() {
            return SandboxType.LOCAL;
        }

        @Override
        public String start(RuntimeConfig config) {
            return "stub-local";
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

    @Provide
    Arbitrary<String> workspacePaths() {
        return Arbitraries.of(
                "/tmp/workspace/user1", "/home/dev/workspace", "/workspace", "/tmp/sandbox-test");
    }

    // =========================================================================
    // Property 7: 本地 Sidecar 进程生命周期
    // =========================================================================

    /**
     * <b>Validates: Requirements 2.1, 2.2</b>
     *
     * <p>Property 7a: acquire 成功后 Sidecar 进程存活且 healthCheck 可响应。
     * 对任意合法 userId 和 workspacePath，acquire 返回的 SandboxInfo 类型为 LOCAL，
     * host 为 "localhost"，sandboxId 格式为 "local-{port}"，且 healthCheck 返回 true。
     */
    @Property(tries = 100)
    void acquire_thenProcessAliveAndHealthCheckResponds(
            @ForAll("userIds") String userId, @ForAll("workspacePaths") String workspace) {

        InMemoryLocalSandboxProvider provider = new InMemoryLocalSandboxProvider(10000);

        SandboxConfig config =
                new SandboxConfig(userId, SandboxType.LOCAL, workspace, Map.of(), null, null, 0);

        SandboxInfo info = provider.acquire(config);

        assertNotNull(info, "acquire 应返回非空 SandboxInfo");
        assertEquals(SandboxType.LOCAL, info.type(), "类型应为 LOCAL");
        assertEquals("localhost", info.host(), "host 应为 localhost");
        assertTrue(
                info.sandboxId().startsWith("local-"),
                "sandboxId 应以 'local-' 开头，实际: " + info.sandboxId());
        assertFalse(info.reused(), "首次 acquire 应返回 reused=false");
        assertEquals(workspace, info.workspacePath(), "workspacePath 应与配置一致");

        // 进程应存活
        StubSidecarProcess process = provider.getProcess(userId);
        assertNotNull(process, "应存在对应的 Sidecar 进程");
        assertTrue(process.isAlive(), "acquire 成功后进程应存活");

        // healthCheck 应返回 true
        assertTrue(provider.healthCheck(info), "acquire 成功后 healthCheck 应返回 true");
    }

    /**
     * <b>Validates: Requirements 2.2</b>
     *
     * <p>Property 7b: 同一用户连续两次 acquire 返回 reused=true 且端口不变。
     * 当第一次 acquire 成功且进程存活时，第二次 acquire 应复用已有进程。
     */
    @Property(tries = 100)
    void sameUser_secondAcquire_returnsReusedWithSamePort(
            @ForAll("userIds") String userId, @ForAll("workspacePaths") String workspace) {

        InMemoryLocalSandboxProvider provider = new InMemoryLocalSandboxProvider(10000);

        SandboxConfig config =
                new SandboxConfig(userId, SandboxType.LOCAL, workspace, Map.of(), null, null, 0);

        SandboxInfo first = provider.acquire(config);
        assertFalse(first.reused(), "首次 acquire 应返回 reused=false");

        SandboxInfo second = provider.acquire(config);
        assertTrue(second.reused(), "同一用户第二次 acquire 应返回 reused=true");
        assertEquals(first.sidecarPort(), second.sidecarPort(), "复用时端口应不变");
        assertEquals(first.sandboxId(), second.sandboxId(), "复用时 sandboxId 应不变");
    }

    /**
     * <b>Validates: Requirements 2.8</b>
     *
     * <p>Property 7c: 不同用户返回不同端口。
     * 对任意两个不同的 userId，acquire 应分配不同的端口。
     */
    @Property(tries = 100)
    void differentUsers_getDifferentPorts(
            @ForAll("userIds") String userId1, @ForAll("userIds") String userId2) {

        Assume.that(!userId1.equals(userId2));

        InMemoryLocalSandboxProvider provider = new InMemoryLocalSandboxProvider(10000);

        SandboxConfig config1 =
                new SandboxConfig(
                        userId1, SandboxType.LOCAL, "/workspace", Map.of(), null, null, 0);
        SandboxConfig config2 =
                new SandboxConfig(
                        userId2, SandboxType.LOCAL, "/workspace", Map.of(), null, null, 0);

        SandboxInfo info1 = provider.acquire(config1);
        SandboxInfo info2 = provider.acquire(config2);

        assertNotEquals(info1.sidecarPort(), info2.sidecarPort(), "不同用户应分配不同端口");
        assertNotEquals(info1.sandboxId(), info2.sandboxId(), "不同用户应有不同 sandboxId");
    }

    /**
     * <b>Validates: Requirements 2.7</b>
     *
     * <p>Property 7d: release 后无其他会话时进程被终止。
     * release 后进程应被停止，healthCheck 应返回 false。
     */
    @Property(tries = 100)
    void release_thenProcessTerminated(
            @ForAll("userIds") String userId, @ForAll("workspacePaths") String workspace) {

        InMemoryLocalSandboxProvider provider = new InMemoryLocalSandboxProvider(10000);

        SandboxConfig config =
                new SandboxConfig(userId, SandboxType.LOCAL, workspace, Map.of(), null, null, 0);

        SandboxInfo info = provider.acquire(config);
        assertTrue(provider.healthCheck(info), "acquire 后 healthCheck 应为 true");

        provider.release(info);

        assertFalse(provider.healthCheck(info), "release 后 healthCheck 应返回 false");
        // 进程映射应被移除
        assertNull(provider.getProcess(userId), "release 后进程映射应被移除");
    }

    /**
     * <b>Validates: Requirements 2.2, 2.7</b>
     *
     * <p>Property 7e: 进程死亡后再次 acquire 应启动新进程（非复用）。
     */
    @Property(tries = 100)
    void deadProcess_reacquire_startsNewProcess(
            @ForAll("userIds") String userId, @ForAll("workspacePaths") String workspace) {

        InMemoryLocalSandboxProvider provider = new InMemoryLocalSandboxProvider(10000);

        SandboxConfig config =
                new SandboxConfig(userId, SandboxType.LOCAL, workspace, Map.of(), null, null, 0);

        SandboxInfo first = provider.acquire(config);
        int firstPort = first.sidecarPort();

        // 模拟进程死亡
        provider.getProcess(userId).stop();
        assertFalse(provider.healthCheck(first), "进程死亡后 healthCheck 应返回 false");

        // 再次 acquire 应启动新进程
        SandboxInfo second = provider.acquire(config);
        assertFalse(second.reused(), "进程死亡后再次 acquire 应返回 reused=false");
        assertNotEquals(firstPort, second.sidecarPort(), "新进程应分配新端口");
        assertTrue(provider.healthCheck(second), "新进程 healthCheck 应返回 true");
    }

    // =========================================================================
    // Property 8: Provider 接口契约一致性（Local）
    // =========================================================================

    /**
     * <b>Validates: Requirements 2.3, 2.4, 2.5, 2.6</b>
     *
     * <p>Property 8a: acquire 成功后 healthCheck 返回 true。
     */
    @Property(tries = 100)
    void local_acquire_thenHealthCheck_returnsTrue(
            @ForAll("userIds") String userId, @ForAll("workspacePaths") String workspace) {

        InMemoryLocalSandboxProvider provider = new InMemoryLocalSandboxProvider(20000);

        SandboxConfig config =
                new SandboxConfig(userId, SandboxType.LOCAL, workspace, Map.of(), null, null, 0);

        SandboxInfo info = provider.acquire(config);

        assertTrue(provider.healthCheck(info), "acquire 成功后 healthCheck 应返回 true");
    }

    /**
     * <b>Validates: Requirements 2.3, 2.4</b>
     *
     * <p>Property 8b: writeFile 后 readFile 返回相同内容（通过 Sidecar HTTP API）。
     * 对任意合法文件内容和路径，写入后读回的内容应完全一致。
     */
    @Property(tries = 200)
    void local_writeFile_thenReadFile_returnsSameContent(
            @ForAll("userIds") String userId,
            @ForAll("fileContents") String content,
            @ForAll("relativePaths") String path)
            throws IOException {

        InMemoryLocalSandboxProvider provider = new InMemoryLocalSandboxProvider(20000);

        SandboxConfig config =
                new SandboxConfig(userId, SandboxType.LOCAL, "/workspace", Map.of(), null, null, 0);

        SandboxInfo info = provider.acquire(config);

        provider.writeFile(info, path, content);
        String readBack = provider.readFile(info, path);

        assertEquals(content, readBack, "writeFile 后 readFile 应返回完全相同的内容");
    }

    /**
     * <b>Validates: Requirements 2.3, 2.4</b>
     *
     * <p>Property 8c: 多次写入同一路径，readFile 返回最后一次写入的内容（覆盖语义）。
     */
    @Property(tries = 100)
    void local_multipleWrites_readReturnsLastWrittenContent(
            @ForAll("userIds") String userId,
            @ForAll("relativePaths") String path,
            @ForAll("fileContents") String content1,
            @ForAll("fileContents") String content2)
            throws IOException {

        InMemoryLocalSandboxProvider provider = new InMemoryLocalSandboxProvider(20000);

        SandboxConfig config =
                new SandboxConfig(userId, SandboxType.LOCAL, "/workspace", Map.of(), null, null, 0);

        SandboxInfo info = provider.acquire(config);

        provider.writeFile(info, path, content1);
        provider.writeFile(info, path, content2);

        String readBack = provider.readFile(info, path);
        assertEquals(content2, readBack, "多次写入后 readFile 应返回最后一次写入的内容");
    }

    /**
     * <b>Validates: Requirements 2.3, 2.4, 2.5, 2.6</b>
     *
     * <p>Property 8d: 完整契约链 — acquire → healthCheck → writeFile → readFile → connectSidecar → release
     * 的完整流程，每一步都满足接口契约。
     */
    @Property(tries = 100)
    void local_fullContractChain_allStepsSatisfyContract(
            @ForAll("userIds") String userId,
            @ForAll("fileContents") String content,
            @ForAll("relativePaths") String path,
            @ForAll("workspacePaths") String workspace)
            throws IOException {

        InMemoryLocalSandboxProvider provider = new InMemoryLocalSandboxProvider(30000);

        // Step 1: acquire
        SandboxConfig config =
                new SandboxConfig(userId, SandboxType.LOCAL, workspace, Map.of(), null, null, 0);
        SandboxInfo info = provider.acquire(config);
        assertNotNull(info, "acquire 应返回非空 SandboxInfo");
        assertEquals(SandboxType.LOCAL, info.type());
        assertEquals("localhost", info.host());

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
        assertNotNull(adapter, "connectSidecar 应返回非空 RuntimeAdapter");
        assertTrue(adapter.isAlive(), "connectSidecar 后 adapter 应存活");

        // Step 5: release
        provider.release(info);
        assertFalse(provider.healthCheck(info), "release 后 healthCheck 应返回 false");
    }

    /**
     * <b>Validates: Requirements 2.5, 2.6</b>
     *
     * <p>Property 8e: 不同用户的文件存储相互隔离。
     * 用户 A 写入的文件，用户 B 无法读取（不同 sandboxId 前缀）。
     */
    @Property(tries = 100)
    void local_differentUsers_fileStoreIsolated(
            @ForAll("userIds") String userId1,
            @ForAll("userIds") String userId2,
            @ForAll("relativePaths") String path,
            @ForAll("fileContents") String content)
            throws IOException {

        Assume.that(!userId1.equals(userId2));

        InMemoryLocalSandboxProvider provider = new InMemoryLocalSandboxProvider(40000);

        SandboxConfig config1 =
                new SandboxConfig(
                        userId1, SandboxType.LOCAL, "/workspace", Map.of(), null, null, 0);
        SandboxConfig config2 =
                new SandboxConfig(
                        userId2, SandboxType.LOCAL, "/workspace", Map.of(), null, null, 0);

        SandboxInfo info1 = provider.acquire(config1);
        SandboxInfo info2 = provider.acquire(config2);

        // 用户 1 写入文件
        provider.writeFile(info1, path, content);

        // 用户 1 可以读取
        String readBack = provider.readFile(info1, path);
        assertEquals(content, readBack);

        // 用户 2 读取同一路径应失败（文件不存在）
        assertThrows(IOException.class, () -> provider.readFile(info2, path), "不同用户的文件存储应相互隔离");
    }
}
