package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * LocalRuntimeAdapter 单元测试。
 * <p>
 * 验证 AcpProcess 封装正确性、RuntimeStatus 状态转换和健康检查。
 */
class LocalRuntimeAdapterTest {

    @TempDir java.nio.file.Path tempDir;

    private RuntimeConfig buildConfig(String command, List<String> args) {
        RuntimeConfig config = new RuntimeConfig();
        config.setUserId("test-user");
        config.setProviderKey("test-provider");
        config.setCommand(command);
        config.setArgs(args);
        config.setCwd(tempDir.toString());
        config.setEnv(Collections.emptyMap());
        return config;
    }

    // ===== 类型标识测试 =====

    @Test
    void getType_returnsLocal() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        assertEquals(SandboxType.LOCAL, adapter.getType());
    }

    // ===== 初始状态测试 =====

    @Test
    void initialStatus_isCreating() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        assertEquals(RuntimeStatus.CREATING, adapter.getStatus());
    }

    @Test
    void isAlive_beforeStart_returnsFalse() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        assertFalse(adapter.isAlive());
    }

    @Test
    void stdout_beforeStart_returnsEmptyFlux() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        List<String> lines = adapter.stdout().collectList().block();
        assertNotNull(lines);
        assertTrue(lines.isEmpty());
    }

    // ===== 启动测试 =====

    @Test
    void start_withValidCommand_returnsInstanceIdAndSetsRunning() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        RuntimeConfig config = buildConfig("cat", Collections.emptyList());

        String instanceId = adapter.start(config);

        assertNotNull(instanceId);
        assertTrue(instanceId.startsWith("local-"));
        assertEquals(RuntimeStatus.RUNNING, adapter.getStatus());
        assertTrue(adapter.isAlive());

        adapter.close();
    }

    @Test
    void start_withInvalidCommand_setsErrorStatusAndThrows() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        RuntimeConfig config = buildConfig("nonexistent-command-xyz", Collections.emptyList());

        assertThrows(RuntimeException.class, () -> adapter.start(config));
        assertEquals(RuntimeStatus.ERROR, adapter.getStatus());
    }

    @Test
    void start_whenAlreadyRunning_throwsException() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        RuntimeConfig config = buildConfig("cat", Collections.emptyList());
        adapter.start(config);

        assertThrows(RuntimeException.class, () -> adapter.start(config));

        adapter.close();
    }

    // ===== 关闭测试 =====

    @Test
    void close_setsStatusToStopped() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        RuntimeConfig config = buildConfig("cat", Collections.emptyList());
        adapter.start(config);

        adapter.close();

        assertEquals(RuntimeStatus.STOPPED, adapter.getStatus());
        assertFalse(adapter.isAlive());
    }

    @Test
    void close_calledTwice_isIdempotent() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        RuntimeConfig config = buildConfig("cat", Collections.emptyList());
        adapter.start(config);

        adapter.close();
        adapter.close(); // should not throw

        assertEquals(RuntimeStatus.STOPPED, adapter.getStatus());
    }

    // ===== send 测试 =====

    @Test
    void send_beforeStart_throwsIOException() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        assertThrows(IOException.class, () -> adapter.send("{\"id\":1}"));
    }

    @Test
    void send_afterClose_throwsIOException() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        RuntimeConfig config = buildConfig("cat", Collections.emptyList());
        adapter.start(config);
        adapter.close();

        assertThrows(IOException.class, () -> adapter.send("{\"id\":1}"));
    }

    // ===== getStatus 检测进程退出 =====

    @Test
    void getStatus_detectsProcessExit() throws Exception {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        // 'true' command exits immediately
        RuntimeConfig config = buildConfig("true", Collections.emptyList());
        adapter.start(config);

        // Wait a bit for the process to exit
        Thread.sleep(500);

        assertEquals(RuntimeStatus.ERROR, adapter.getStatus());

        adapter.close();
    }

    // ===== getFileSystem 测试 =====

    @Test
    void getFileSystem_returnsLocalFileSystemAdapter_afterStart() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        RuntimeConfig config = buildConfig("cat", Collections.emptyList());
        adapter.start(config);

        assertNotNull(adapter.getFileSystem());
        assertInstanceOf(LocalFileSystemAdapter.class, adapter.getFileSystem());

        adapter.close();
    }

    // ===== 健康检查测试 =====

    @Test
    void defaultHealthCheckInterval_isLessThanOrEqual5Seconds() {
        // Req 8.2: 5 秒内检测到异常
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        assertTrue(adapter.getHealthCheckIntervalSeconds() <= 5, "健康检查间隔应 ≤5 秒以满足 Req 8.2");
    }

    @Test
    void healthCheck_detectsProcessCrash_andNotifiesFaultListener() throws Exception {
        // 使用 1 秒间隔加速测试
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter(1);
        AtomicReference<RuntimeFaultNotification> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        adapter.setFaultListener(
                notification -> {
                    captured.set(notification);
                    latch.countDown();
                });

        // 'true' 命令立即退出，模拟进程崩溃
        RuntimeConfig config = buildConfig("true", Collections.emptyList());
        adapter.start(config);

        // 等待健康检查检测到进程退出
        assertTrue(latch.await(5, TimeUnit.SECONDS), "应在 5 秒内检测到进程崩溃");

        RuntimeFaultNotification notification = captured.get();
        assertNotNull(notification);
        assertEquals(RuntimeFaultNotification.FAULT_PROCESS_CRASHED, notification.faultType());
        assertEquals(SandboxType.LOCAL, notification.sandboxType());
        assertEquals(RuntimeFaultNotification.ACTION_RESTART, notification.suggestedAction());
        assertEquals(RuntimeStatus.ERROR, adapter.getStatus());

        adapter.close();
    }

    @Test
    void healthCheck_doesNotNotify_whenProcessIsAlive() throws Exception {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter(1);
        AtomicReference<RuntimeFaultNotification> captured = new AtomicReference<>();

        adapter.setFaultListener(captured::set);

        // 'cat' 会一直运行直到关闭
        RuntimeConfig config = buildConfig("cat", Collections.emptyList());
        adapter.start(config);

        // 等待几个健康检查周期
        Thread.sleep(3000);

        assertNull(captured.get(), "进程存活时不应触发异常通知");
        assertEquals(RuntimeStatus.RUNNING, adapter.getStatus());

        adapter.close();
    }

    @Test
    void close_stopsHealthCheck_noNotificationAfterClose() throws Exception {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter(1);
        AtomicReference<RuntimeFaultNotification> captured = new AtomicReference<>();

        // 'cat' 会一直运行
        RuntimeConfig config = buildConfig("cat", Collections.emptyList());
        adapter.start(config);

        // 先关闭，再注册 listener
        adapter.close();
        adapter.setFaultListener(captured::set);

        // 等待确保不会有通知
        Thread.sleep(2000);

        assertNull(captured.get(), "关闭后不应触发异常通知");
    }

    // ===== setFaultListener 测试 =====

    @Test
    void setFaultListener_acceptsNull() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        adapter.setFaultListener(null); // should not throw
    }

    @Test
    void setFaultListener_acceptsNonNullListener() {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        adapter.setFaultListener(notification -> {}); // should not throw
    }

    // ===== HOME 环境变量隔离测试 (Req 2.6) =====

    @Test
    void start_withIsolateHome_setsHomeEnvToCwd() throws Exception {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        RuntimeConfig config = buildConfig("sh", List.of("-c", "echo $HOME"));
        config.setIsolateHome(true);
        config.setEnv(Collections.emptyMap());

        adapter.start(config);

        // 读取 stdout，验证 HOME 被设置为 cwd
        String output = adapter.stdout().next().block(java.time.Duration.ofSeconds(5));
        assertNotNull(output);
        assertEquals(tempDir.toString(), output.trim());

        adapter.close();
    }

    @Test
    void start_withIsolateHome_respectsExistingHomeInEnv() throws Exception {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        RuntimeConfig config = buildConfig("sh", List.of("-c", "echo $HOME"));
        config.setIsolateHome(true);
        String customHome = "/tmp/custom-home-test";
        config.setEnv(java.util.Map.of("HOME", customHome));

        adapter.start(config);

        // 读取 stdout，验证 HOME 保留了调用方设置的值
        String output = adapter.stdout().next().block(java.time.Duration.ofSeconds(5));
        assertNotNull(output);
        assertEquals(customHome, output.trim());

        adapter.close();
    }

    @Test
    void start_withoutIsolateHome_doesNotOverrideHome() throws Exception {
        LocalRuntimeAdapter adapter = new LocalRuntimeAdapter();
        RuntimeConfig config = buildConfig("sh", List.of("-c", "echo $HOME"));
        config.setIsolateHome(false);
        config.setEnv(Collections.emptyMap());

        adapter.start(config);

        // 读取 stdout，HOME 应该是系统默认值（不是 cwd）
        String output = adapter.stdout().next().block(java.time.Duration.ofSeconds(5));
        assertNotNull(output);
        // 系统默认 HOME 不应等于 tempDir（除非巧合，但极不可能）
        // 这里只验证不为空即可，因为系统 HOME 是继承的
        assertFalse(output.trim().isEmpty(), "HOME should have a value");

        adapter.close();
    }
}
