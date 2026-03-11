package com.alibaba.himarket.service.hicoding.sandbox.init;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeStatus;
import com.alibaba.himarket.service.hicoding.sandbox.ConfigFile;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxConfig;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxInfo;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxProvider;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import com.alibaba.himarket.service.hicoding.session.CliSessionConfig;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
                        SandboxType.REMOTE,
                        "remote-8080",
                        "sandbox.example.com",
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
                            "user1", SandboxType.REMOTE, "/workspace", Map.of(), null, null);
            InitContext context =
                    new InitContext(mockProvider, "user1", config, null, null, null, null);
            when(mockProvider.acquire(config)).thenReturn(stubInfo);

            assertDoesNotThrow(() -> phase.execute(context));
            assertNotNull(context.getSandboxInfo());
            assertEquals("sandbox.example.com", context.getSandboxInfo().host());
            assertEquals("remote-8080", context.getSandboxInfo().sandboxId());
        }

        @Test
        @DisplayName("acquire 失败时抛出不可重试的 InitPhaseException")
        void execute_acquireFails_throwsNonRetryableException() {
            SandboxConfig config =
                    new SandboxConfig(
                            "user1", SandboxType.REMOTE, "/workspace", Map.of(), null, null);
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
                            SandboxType.REMOTE,
                            "remote-8080",
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
        @DisplayName("基本属性：name=filesystem-ready, order=200, retryPolicy=none")
        void basicProperties() {
            assertEquals("filesystem-ready", phase.name());
            assertEquals(200, phase.order());
            assertEquals(0, phase.retryPolicy().maxRetries());
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
            assertTrue(ex.getMessage().contains("不可达"), "错误消息应包含'不可达'关键字");
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
        @DisplayName("verify: 始终返回 true（不再执行文件系统验证）")
        void verify_alwaysReturnsTrue() {
            InitContext context = createContextWithSandboxInfo();
            assertTrue(phase.verify(context));
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
        @DisplayName("基本属性：name=config-injection, order=300, retryPolicy=none")
        void basicProperties() {
            assertEquals("config-injection", phase.name());
            assertEquals(300, phase.order());
            assertEquals(0, phase.retryPolicy().maxRetries());
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
        @DisplayName("execute: writeFile 逐个写入成功")
        void execute_writeAndReadBackHashMatch_succeeds() throws IOException {
            String content = "{\"model\": \"gpt-4\"}";
            ConfigFile config =
                    new ConfigFile(
                            "settings.json", content, null, ConfigFile.ConfigType.MODEL_SETTINGS);
            InitContext context = createContextForConfigInjection(List.of(config));

            assertDoesNotThrow(() -> phase.execute(context));
            verify(mockProvider).writeFile(stubInfo, "settings.json", content);
        }

        @Test
        @DisplayName("execute: writeFile 抛出 IOException 时抛出可重试异常")
        void execute_writeFileFails_throwsRetryableException() throws IOException {
            String content = "test-content";
            ConfigFile config =
                    new ConfigFile(
                            "settings.json", content, null, ConfigFile.ConfigType.MODEL_SETTINGS);
            InitContext context = createContextForConfigInjection(List.of(config));

            doThrow(new IOException("Sidecar writeFile 失败"))
                    .when(mockProvider)
                    .writeFile(eq(stubInfo), anyString(), anyString());

            InitPhaseException ex =
                    assertThrows(InitPhaseException.class, () -> phase.execute(context));
            assertTrue(ex.isRetryable());
            assertTrue(ex.getMessage().contains("配置注入失败"));
        }

        @Test
        @DisplayName("execute: 多个配置文件逐个 writeFile 成功")
        void execute_multipleConfigs_allSucceed() throws IOException {
            String content1 = "{\"model\": \"gpt-4\"}";
            String content2 = "{\"servers\": []}";
            ConfigFile config1 =
                    new ConfigFile(
                            "settings.json", content1, null, ConfigFile.ConfigType.MODEL_SETTINGS);
            ConfigFile config2 =
                    new ConfigFile(
                            ".kiro/mcp.json", content2, null, ConfigFile.ConfigType.MCP_CONFIG);
            InitContext context = createContextForConfigInjection(List.of(config1, config2));

            assertDoesNotThrow(() -> phase.execute(context));
            verify(mockProvider).writeFile(stubInfo, "settings.json", content1);
            verify(mockProvider).writeFile(stubInfo, ".kiro/mcp.json", content2);
        }

        @Test
        @DisplayName("verify: 始终返回 true（不再执行文件读回验证）")
        void verify_alwaysReturnsTrue() {
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
        @DisplayName("基本属性：name=sidecar-connect, order=400, retryPolicy=none")
        void basicProperties() {
            assertEquals("sidecar-connect", phase.name());
            assertEquals(400, phase.order());
            assertEquals(0, phase.retryPolicy().maxRetries());
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
}
