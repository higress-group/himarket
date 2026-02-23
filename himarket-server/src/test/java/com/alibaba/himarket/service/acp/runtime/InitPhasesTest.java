package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.acp.CliSessionConfig;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 五个初始化阶段的单元测试。
 *
 * <p>使用 mock SandboxProvider 测试各阶段的 execute、verify、shouldExecute 逻辑。
 *
 * <p><b>Validates: Requirements 5.1-5.9, 8.1-8.5, 9.1-9.3</b>
 */
class InitPhasesTest {

    private SandboxProvider mockProvider;
    private SandboxInfo stubInfo;

    @BeforeEach
    void setUp() {
        mockProvider = mock(SandboxProvider.class);
        stubInfo =
                new SandboxInfo(
                        SandboxType.LOCAL,
                        "local-8080",
                        "localhost",
                        8080,
                        "/workspace",
                        false,
                        Map.of());
    }

    private InitContext createBasicContext() {
        CliProviderConfig providerConfig = new CliProviderConfig();
        return new InitContext(mockProvider, "test-user", null, null, providerConfig, null, null);
    }

    private InitContext createContextWithSandboxInfo() {
        InitContext context = createBasicContext();
        context.setSandboxInfo(stubInfo);
        return context;
    }

    private InitContext createContextForConfigInjection(List<ConfigFile> configs) {
        CliProviderConfig providerConfig = new CliProviderConfig();
        providerConfig.setSupportsCustomModel(true);
        CliSessionConfig sessionConfig = new CliSessionConfig();
        InitContext context =
                new InitContext(
                        mockProvider, "test-user", null, null, providerConfig, sessionConfig, null);
        context.setSandboxInfo(stubInfo);
        context.setInjectedConfigs(configs);
        return context;
    }

    // =========================================================================
    // SandboxAcquirePhase 测试
    // =========================================================================

    @Nested
    @DisplayName("SandboxAcquirePhase (order=100)")
    class SandboxAcquirePhaseTest {

        private final SandboxAcquirePhase phase = new SandboxAcquirePhase();

        @Test
        @DisplayName("基本属性：name=sandbox-acquire, order=100, retryPolicy=none")
        void basicProperties() {
            assertEquals("sandbox-acquire", phase.name());
            assertEquals(100, phase.order());
            assertEquals(0, phase.retryPolicy().maxRetries());
            assertTrue(phase.shouldExecute(createBasicContext()));
        }

        @Test
        @DisplayName("execute 成功时将 SandboxInfo 存入 InitContext")
        void execute_success_storesSandboxInfo() {
            SandboxConfig config =
                    new SandboxConfig(
                            "user1",
                            SandboxType.LOCAL,
                            "/workspace",
                            Map.of(),
                            null,
                            null,
                            null,
                            0);
            InitContext context =
                    new InitContext(mockProvider, "user1", config, null, null, null, null);
            when(mockProvider.acquire(config)).thenReturn(stubInfo);

            assertDoesNotThrow(() -> phase.execute(context));
            assertNotNull(context.getSandboxInfo());
            assertEquals("localhost", context.getSandboxInfo().host());
            assertEquals("local-8080", context.getSandboxInfo().sandboxId());
        }

        @Test
        @DisplayName("acquire 失败时抛出不可重试的 InitPhaseException")
        void execute_acquireFails_throwsNonRetryableException() {
            SandboxConfig config =
                    new SandboxConfig(
                            "user1",
                            SandboxType.LOCAL,
                            "/workspace",
                            Map.of(),
                            null,
                            null,
                            null,
                            0);
            InitContext context =
                    new InitContext(mockProvider, "user1", config, null, null, null, null);
            when(mockProvider.acquire(config)).thenThrow(new RuntimeException("Pod 创建超时"));

            InitPhaseException ex =
                    assertThrows(InitPhaseException.class, () -> phase.execute(context));
            assertEquals("sandbox-acquire", ex.getPhaseName());
            assertFalse(ex.isRetryable(), "沙箱获取失败不应重试");
            assertTrue(ex.getMessage().contains("Pod 创建超时"));
        }

        @Test
        @DisplayName("verify: sandboxInfo 非空且 host 非空时返回 true")
        void verify_withValidSandboxInfo_returnsTrue() {
            InitContext context = createContextWithSandboxInfo();
            assertTrue(phase.verify(context));
        }

        @Test
        @DisplayName("verify: sandboxInfo 为 null 时返回 false")
        void verify_withNullSandboxInfo_returnsFalse() {
            InitContext context = createBasicContext();
            assertFalse(phase.verify(context));
        }

        @Test
        @DisplayName("verify: host 为空白时返回 false")
        void verify_withBlankHost_returnsFalse() {
            InitContext context = createBasicContext();
            context.setSandboxInfo(
                    new SandboxInfo(
                            SandboxType.LOCAL,
                            "local-8080",
                            "  ",
                            8080,
                            "/workspace",
                            false,
                            Map.of()));
            assertFalse(phase.verify(context));
        }
    }

    // =========================================================================
    // FileSystemReadyPhase 测试
    // =========================================================================

    @Nested
    @DisplayName("FileSystemReadyPhase (order=200)")
    class FileSystemReadyPhaseTest {

        private final FileSystemReadyPhase phase = new FileSystemReadyPhase();

        @Test
        @DisplayName("基本属性：name=filesystem-ready, order=200, retryPolicy=defaultPolicy")
        void basicProperties() {
            assertEquals("filesystem-ready", phase.name());
            assertEquals(200, phase.order());
            assertEquals(3, phase.retryPolicy().maxRetries());
            assertEquals(Duration.ofSeconds(1), phase.retryPolicy().initialDelay());
            assertEquals(2.0, phase.retryPolicy().backoffMultiplier());
            assertTrue(phase.shouldExecute(createBasicContext()));
        }

        @Test
        @DisplayName("execute 成功：healthCheck 返回 true")
        void execute_healthCheckTrue_succeeds() {
            InitContext context = createContextWithSandboxInfo();
            when(mockProvider.healthCheck(stubInfo)).thenReturn(true);

            assertDoesNotThrow(() -> phase.execute(context));
            verify(mockProvider).healthCheck(stubInfo);
        }

        @Test
        @DisplayName("execute 失败：healthCheck 返回 false 时抛出可重试异常")
        void execute_healthCheckFalse_throwsRetryableException() {
            InitContext context = createContextWithSandboxInfo();
            when(mockProvider.healthCheck(stubInfo)).thenReturn(false);

            InitPhaseException ex =
                    assertThrows(InitPhaseException.class, () -> phase.execute(context));
            assertEquals("filesystem-ready", ex.getPhaseName());
            assertTrue(ex.isRetryable(), "健康检查失败应可重试");
            assertTrue(ex.getMessage().contains("健康检查失败"));
        }

        @Test
        @DisplayName("execute 失败：healthCheck 抛出异常时包装为可重试异常")
        void execute_healthCheckThrows_wrapsAsRetryableException() {
            InitContext context = createContextWithSandboxInfo();
            when(mockProvider.healthCheck(stubInfo)).thenThrow(new RuntimeException("连接超时"));

            InitPhaseException ex =
                    assertThrows(InitPhaseException.class, () -> phase.execute(context));
            assertEquals("filesystem-ready", ex.getPhaseName());
            assertTrue(ex.isRetryable());
        }

        @Test
        @DisplayName("verify: 写入并读回临时文件成功时返回 true")
        void verify_writeAndReadBackMatch_returnsTrue() throws IOException {
            InitContext context = createContextWithSandboxInfo();
            // writeFile 不抛异常，readFile 返回匹配内容
            doNothing()
                    .when(mockProvider)
                    .writeFile(eq(stubInfo), eq(".sandbox-health-check"), anyString());
            when(mockProvider.readFile(eq(stubInfo), eq(".sandbox-health-check")))
                    .thenAnswer(
                            invocation -> {
                                // 需要返回与写入相同的内容，但我们无法直接获取
                                // 使用 ArgumentCaptor 来捕获写入的内容
                                return null; // 先返回 null 触发 false
                            });

            // 更精确的方式：捕获写入内容并在读取时返回
            doAnswer(
                            invocation -> {
                                String content = invocation.getArgument(2);
                                when(mockProvider.readFile(stubInfo, ".sandbox-health-check"))
                                        .thenReturn(content);
                                return null;
                            })
                    .when(mockProvider)
                    .writeFile(eq(stubInfo), eq(".sandbox-health-check"), anyString());

            assertTrue(phase.verify(context));
        }

        @Test
        @DisplayName("verify: 读回内容不匹配时返回 false")
        void verify_readBackMismatch_returnsFalse() throws IOException {
            InitContext context = createContextWithSandboxInfo();
            doNothing()
                    .when(mockProvider)
                    .writeFile(eq(stubInfo), eq(".sandbox-health-check"), anyString());
            when(mockProvider.readFile(stubInfo, ".sandbox-health-check"))
                    .thenReturn("different-content");

            assertFalse(phase.verify(context));
        }

        @Test
        @DisplayName("verify: writeFile 抛出 IOException 时返回 false")
        void verify_writeFileThrows_returnsFalse() throws IOException {
            InitContext context = createContextWithSandboxInfo();
            doThrow(new IOException("写入失败"))
                    .when(mockProvider)
                    .writeFile(eq(stubInfo), eq(".sandbox-health-check"), anyString());

            assertFalse(phase.verify(context));
        }

        @Test
        @DisplayName("verify: readFile 抛出 IOException 时返回 false")
        void verify_readFileThrows_returnsFalse() throws IOException {
            InitContext context = createContextWithSandboxInfo();
            doNothing()
                    .when(mockProvider)
                    .writeFile(eq(stubInfo), eq(".sandbox-health-check"), anyString());
            when(mockProvider.readFile(stubInfo, ".sandbox-health-check"))
                    .thenThrow(new IOException("读取失败"));

            assertFalse(phase.verify(context));
        }
    }

    // =========================================================================
    // ConfigInjectionPhase 测试
    // =========================================================================

    @Nested
    @DisplayName("ConfigInjectionPhase (order=300)")
    class ConfigInjectionPhaseTest {

        private final ConfigInjectionPhase phase = new ConfigInjectionPhase();

        @Test
        @DisplayName("基本属性：name=config-injection, order=300, retryPolicy=fileOperation")
        void basicProperties() {
            assertEquals("config-injection", phase.name());
            assertEquals(300, phase.order());
            assertEquals(2, phase.retryPolicy().maxRetries());
            assertEquals(Duration.ofMillis(500), phase.retryPolicy().initialDelay());
        }

        @Test
        @DisplayName("shouldExecute: sessionConfig 和 supportsCustomModel 都满足时返回 true")
        void shouldExecute_conditionsMet_returnsTrue() {
            InitContext context = createContextForConfigInjection(List.of());
            assertTrue(phase.shouldExecute(context));
        }

        @Test
        @DisplayName("shouldExecute: sessionConfig 为 null 时返回 false")
        void shouldExecute_nullSessionConfig_returnsFalse() {
            InitContext context = createBasicContext(); // sessionConfig = null
            assertFalse(phase.shouldExecute(context));
        }

        @Test
        @DisplayName("shouldExecute: supportsCustomModel 为 false 时返回 false")
        void shouldExecute_supportsCustomModelFalse_returnsFalse() {
            CliProviderConfig providerConfig = new CliProviderConfig();
            providerConfig.setSupportsCustomModel(false);
            CliSessionConfig sessionConfig = new CliSessionConfig();
            InitContext context =
                    new InitContext(
                            mockProvider,
                            "test-user",
                            null,
                            null,
                            providerConfig,
                            sessionConfig,
                            null);
            assertFalse(phase.shouldExecute(context));
        }

        @Test
        @DisplayName("execute: 无配置文件时正常返回不抛异常")
        void execute_noConfigs_succeeds() {
            InitContext context = createContextForConfigInjection(List.of());
            assertDoesNotThrow(() -> phase.execute(context));
        }

        @Test
        @DisplayName("execute: 配置文件为 null 时正常返回")
        void execute_nullConfigs_succeeds() {
            InitContext context = createContextForConfigInjection(null);
            context.setInjectedConfigs(null);
            assertDoesNotThrow(() -> phase.execute(context));
        }

        @Test
        @DisplayName("execute: 写入后读回 SHA-256 一致时成功")
        void execute_writeAndReadBackHashMatch_succeeds() throws IOException {
            String content = "{\"model\": \"gpt-4\"}";
            String hash = ConfigInjectionPhase.sha256(content);
            ConfigFile config =
                    new ConfigFile(
                            "settings.json", content, hash, ConfigFile.ConfigType.MODEL_SETTINGS);
            InitContext context = createContextForConfigInjection(List.of(config));

            doNothing().when(mockProvider).writeFile(stubInfo, "settings.json", content);
            when(mockProvider.readFile(stubInfo, "settings.json")).thenReturn(content);

            assertDoesNotThrow(() -> phase.execute(context));
            verify(mockProvider).writeFile(stubInfo, "settings.json", content);
            verify(mockProvider).readFile(stubInfo, "settings.json");
        }

        @Test
        @DisplayName("execute: 写入后读回内容不一致时抛出可重试异常")
        void execute_hashMismatch_throwsRetryableException() throws IOException {
            String content = "{\"model\": \"gpt-4\"}";
            String hash = ConfigInjectionPhase.sha256(content);
            ConfigFile config =
                    new ConfigFile(
                            "settings.json", content, hash, ConfigFile.ConfigType.MODEL_SETTINGS);
            InitContext context = createContextForConfigInjection(List.of(config));

            doNothing().when(mockProvider).writeFile(stubInfo, "settings.json", content);
            when(mockProvider.readFile(stubInfo, "settings.json")).thenReturn("corrupted-content");

            InitPhaseException ex =
                    assertThrows(InitPhaseException.class, () -> phase.execute(context));
            assertEquals("config-injection", ex.getPhaseName());
            assertTrue(ex.isRetryable(), "哈希不一致应可重试");
            assertTrue(ex.getMessage().contains("验证失败"));
        }

        @Test
        @DisplayName("execute: writeFile 抛出 IOException 时抛出可重试异常")
        void execute_writeFileFails_throwsRetryableException() throws IOException {
            String content = "test-content";
            ConfigFile config =
                    new ConfigFile(
                            "settings.json",
                            content,
                            ConfigInjectionPhase.sha256(content),
                            ConfigFile.ConfigType.MODEL_SETTINGS);
            InitContext context = createContextForConfigInjection(List.of(config));

            doThrow(new IOException("Sidecar writeFile 失败"))
                    .when(mockProvider)
                    .writeFile(stubInfo, "settings.json", content);

            InitPhaseException ex =
                    assertThrows(InitPhaseException.class, () -> phase.execute(context));
            assertTrue(ex.isRetryable());
            assertTrue(ex.getMessage().contains("配置注入失败"));
        }

        @Test
        @DisplayName("execute: 多个配置文件全部成功注入")
        void execute_multipleConfigs_allSucceed() throws IOException {
            String content1 = "{\"model\": \"gpt-4\"}";
            String content2 = "{\"servers\": []}";
            ConfigFile config1 =
                    new ConfigFile(
                            "settings.json",
                            content1,
                            ConfigInjectionPhase.sha256(content1),
                            ConfigFile.ConfigType.MODEL_SETTINGS);
            ConfigFile config2 =
                    new ConfigFile(
                            ".kiro/mcp.json",
                            content2,
                            ConfigInjectionPhase.sha256(content2),
                            ConfigFile.ConfigType.MCP_CONFIG);
            InitContext context = createContextForConfigInjection(List.of(config1, config2));

            doNothing().when(mockProvider).writeFile(eq(stubInfo), anyString(), anyString());
            when(mockProvider.readFile(stubInfo, "settings.json")).thenReturn(content1);
            when(mockProvider.readFile(stubInfo, ".kiro/mcp.json")).thenReturn(content2);

            assertDoesNotThrow(() -> phase.execute(context));
            verify(mockProvider).writeFile(stubInfo, "settings.json", content1);
            verify(mockProvider).writeFile(stubInfo, ".kiro/mcp.json", content2);
        }

        @Test
        @DisplayName("verify: 所有配置文件可读回时返回 true")
        void verify_allConfigsReadable_returnsTrue() throws IOException {
            String content = "test";
            ConfigFile config =
                    new ConfigFile(
                            "settings.json",
                            content,
                            ConfigInjectionPhase.sha256(content),
                            ConfigFile.ConfigType.MODEL_SETTINGS);
            InitContext context = createContextForConfigInjection(List.of(config));

            when(mockProvider.readFile(stubInfo, "settings.json")).thenReturn(content);

            assertTrue(phase.verify(context));
        }

        @Test
        @DisplayName("verify: readFile 返回 null 时返回 false")
        void verify_readFileReturnsNull_returnsFalse() throws IOException {
            String content = "test";
            ConfigFile config =
                    new ConfigFile(
                            "settings.json",
                            content,
                            ConfigInjectionPhase.sha256(content),
                            ConfigFile.ConfigType.MODEL_SETTINGS);
            InitContext context = createContextForConfigInjection(List.of(config));

            when(mockProvider.readFile(stubInfo, "settings.json")).thenReturn(null);

            assertFalse(phase.verify(context));
        }

        @Test
        @DisplayName("verify: readFile 抛出 IOException 时返回 false")
        void verify_readFileThrows_returnsFalse() throws IOException {
            String content = "test";
            ConfigFile config =
                    new ConfigFile(
                            "settings.json",
                            content,
                            ConfigInjectionPhase.sha256(content),
                            ConfigFile.ConfigType.MODEL_SETTINGS);
            InitContext context = createContextForConfigInjection(List.of(config));

            when(mockProvider.readFile(stubInfo, "settings.json"))
                    .thenThrow(new IOException("文件不存在"));

            assertFalse(phase.verify(context));
        }

        @Test
        @DisplayName("verify: 无配置文件时返回 true")
        void verify_noConfigs_returnsTrue() {
            InitContext context = createContextForConfigInjection(List.of());
            assertTrue(phase.verify(context));
        }
    }

    // =========================================================================
    // SidecarConnectPhase 测试
    // =========================================================================

    @Nested
    @DisplayName("SidecarConnectPhase (order=400)")
    class SidecarConnectPhaseTest {

        private final SidecarConnectPhase phase = new SidecarConnectPhase();

        @Test
        @DisplayName("基本属性：name=sidecar-connect, order=400, retryPolicy=2次/2s/2.0倍/8s")
        void basicProperties() {
            assertEquals("sidecar-connect", phase.name());
            assertEquals(400, phase.order());
            RetryPolicy policy = phase.retryPolicy();
            assertEquals(2, policy.maxRetries());
            assertEquals(Duration.ofSeconds(2), policy.initialDelay());
            assertEquals(2.0, policy.backoffMultiplier());
            assertEquals(Duration.ofSeconds(8), policy.maxDelay());
            assertTrue(phase.shouldExecute(createBasicContext()));
        }

        @Test
        @DisplayName("execute 成功：将 RuntimeAdapter 存入 InitContext")
        void execute_success_storesRuntimeAdapter() {
            InitContext context = createContextWithSandboxInfo();
            RuntimeAdapter mockAdapter = mock(RuntimeAdapter.class);
            when(mockProvider.connectSidecar(stubInfo, null)).thenReturn(mockAdapter);

            assertDoesNotThrow(() -> phase.execute(context));
            assertSame(mockAdapter, context.getRuntimeAdapter());
        }

        @Test
        @DisplayName("execute 失败：connectSidecar 抛出异常时抛出可重试异常")
        void execute_connectFails_throwsRetryableException() {
            InitContext context = createContextWithSandboxInfo();
            when(mockProvider.connectSidecar(stubInfo, null))
                    .thenThrow(new RuntimeException("WebSocket 连接超时"));

            InitPhaseException ex =
                    assertThrows(InitPhaseException.class, () -> phase.execute(context));
            assertEquals("sidecar-connect", ex.getPhaseName());
            assertTrue(ex.isRetryable(), "Sidecar 连接失败应可重试");
            assertTrue(ex.getMessage().contains("连接失败"));
        }

        @Test
        @DisplayName("verify: adapter 非空且 status=RUNNING 时返回 true")
        void verify_adapterRunning_returnsTrue() {
            InitContext context = createBasicContext();
            RuntimeAdapter mockAdapter = mock(RuntimeAdapter.class);
            when(mockAdapter.getStatus()).thenReturn(RuntimeStatus.RUNNING);
            context.setRuntimeAdapter(mockAdapter);

            assertTrue(phase.verify(context));
        }

        @Test
        @DisplayName("verify: adapter 为 null 时返回 false")
        void verify_nullAdapter_returnsFalse() {
            InitContext context = createBasicContext();
            assertFalse(phase.verify(context));
        }

        @Test
        @DisplayName("verify: adapter status 不是 RUNNING 时返回 false")
        void verify_adapterNotRunning_returnsFalse() {
            InitContext context = createBasicContext();
            RuntimeAdapter mockAdapter = mock(RuntimeAdapter.class);
            when(mockAdapter.getStatus()).thenReturn(RuntimeStatus.ERROR);
            context.setRuntimeAdapter(mockAdapter);

            assertFalse(phase.verify(context));
        }
    }

    // =========================================================================
    // CliReadyPhase 测试
    // =========================================================================

    @Nested
    @DisplayName("CliReadyPhase (order=500)")
    class CliReadyPhaseTest {

        private final CliReadyPhase phase = new CliReadyPhase();

        @Test
        @DisplayName("基本属性：name=cli-ready, order=500, retryPolicy=none")
        void basicProperties() {
            assertEquals("cli-ready", phase.name());
            assertEquals(500, phase.order());
            assertEquals(0, phase.retryPolicy().maxRetries());
            assertTrue(phase.shouldExecute(createBasicContext()));
        }

        @Test
        @DisplayName("execute 成功：收到首条 stdout 消息")
        void execute_receivesFirstMessage_succeeds() {
            InitContext context = createBasicContext();
            RuntimeAdapter mockAdapter = mock(RuntimeAdapter.class);
            when(mockAdapter.stdout()).thenReturn(Flux.just("CLI ready"));
            context.setRuntimeAdapter(mockAdapter);

            assertDoesNotThrow(() -> phase.execute(context));
        }

        @Test
        @DisplayName("execute 失败：RuntimeAdapter 为 null 时抛出不可重试异常")
        void execute_nullAdapter_throwsNonRetryableException() {
            InitContext context = createBasicContext();
            // runtimeAdapter 未设置，为 null

            InitPhaseException ex =
                    assertThrows(InitPhaseException.class, () -> phase.execute(context));
            assertEquals("cli-ready", ex.getPhaseName());
            assertFalse(ex.isRetryable());
            assertTrue(ex.getMessage().contains("RuntimeAdapter 未初始化"));
        }

        @Test
        @DisplayName("execute 失败：stdout 为空且 adapter 已退出时抛出不可重试异常")
        void execute_emptyStdoutAndAdapterDead_throwsProcessExitException() {
            InitContext context = createBasicContext();
            RuntimeAdapter mockAdapter = mock(RuntimeAdapter.class);
            when(mockAdapter.stdout()).thenReturn(Flux.empty());
            when(mockAdapter.isAlive()).thenReturn(false);
            context.setRuntimeAdapter(mockAdapter);

            InitPhaseException ex =
                    assertThrows(InitPhaseException.class, () -> phase.execute(context));
            assertEquals("cli-ready", ex.getPhaseName());
            assertFalse(ex.isRetryable());
            assertTrue(ex.getMessage().contains("CLI 进程已退出"));
        }

        @Test
        @DisplayName("execute 失败：stdout 为空且 adapter 存活时抛出超时异常（15s）")
        void execute_emptyStdoutAndAdapterAlive_throwsTimeoutException() {
            InitContext context = createBasicContext();
            RuntimeAdapter mockAdapter = mock(RuntimeAdapter.class);
            when(mockAdapter.stdout()).thenReturn(Flux.empty());
            when(mockAdapter.isAlive()).thenReturn(true);
            context.setRuntimeAdapter(mockAdapter);

            InitPhaseException ex =
                    assertThrows(InitPhaseException.class, () -> phase.execute(context));
            assertEquals("cli-ready", ex.getPhaseName());
            assertFalse(ex.isRetryable(), "CLI 超时不应重试");
            assertTrue(ex.getMessage().contains("15"));
        }

        @Test
        @DisplayName("execute 失败：stdout 抛出异常时包装为不可重试异常")
        void execute_stdoutThrows_wrapsAsNonRetryableException() {
            InitContext context = createBasicContext();
            RuntimeAdapter mockAdapter = mock(RuntimeAdapter.class);
            when(mockAdapter.stdout()).thenReturn(Flux.error(new RuntimeException("WebSocket 断开")));
            context.setRuntimeAdapter(mockAdapter);

            InitPhaseException ex =
                    assertThrows(InitPhaseException.class, () -> phase.execute(context));
            assertEquals("cli-ready", ex.getPhaseName());
            assertFalse(ex.isRetryable());
        }

        @Test
        @DisplayName("verify: adapter 非空且 isAlive 返回 true")
        void verify_adapterAlive_returnsTrue() {
            InitContext context = createBasicContext();
            RuntimeAdapter mockAdapter = mock(RuntimeAdapter.class);
            when(mockAdapter.isAlive()).thenReturn(true);
            context.setRuntimeAdapter(mockAdapter);

            assertTrue(phase.verify(context));
        }

        @Test
        @DisplayName("verify: adapter 为 null 时返回 false")
        void verify_nullAdapter_returnsFalse() {
            InitContext context = createBasicContext();
            assertFalse(phase.verify(context));
        }

        @Test
        @DisplayName("verify: adapter isAlive 返回 false 时返回 false")
        void verify_adapterDead_returnsFalse() {
            InitContext context = createBasicContext();
            RuntimeAdapter mockAdapter = mock(RuntimeAdapter.class);
            when(mockAdapter.isAlive()).thenReturn(false);
            context.setRuntimeAdapter(mockAdapter);

            assertFalse(phase.verify(context));
        }
    }
}
