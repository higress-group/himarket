package com.alibaba.himarket.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 单元测试：验证 CliProviderController 的 provider 列表和命令可用性检测逻辑。
 */
class CliProviderControllerTest {

    private AcpProperties properties;
    private CliProviderController controller;

    @BeforeEach
    void setUp() {
        properties = new AcpProperties();
        properties.setDefaultProvider("qodercli");

        Map<String, CliProviderConfig> providers = new LinkedHashMap<>();

        CliProviderConfig qoder = new CliProviderConfig();
        qoder.setDisplayName("Qoder CLI");
        qoder.setCommand("qodercli");
        qoder.setArgs("--acp");
        qoder.setCompatibleRuntimes(List.of(SandboxType.REMOTE, SandboxType.OPEN_SANDBOX));
        providers.put("qodercli", qoder);

        CliProviderConfig kiro = new CliProviderConfig();
        kiro.setDisplayName("Kiro CLI");
        kiro.setCommand("kiro-cli");
        kiro.setArgs("acp");
        kiro.setCompatibleRuntimes(List.of(SandboxType.REMOTE));
        providers.put("kiro-cli", kiro);

        // npx 通常在安装了 Node.js 的机器上可用
        CliProviderConfig claude = new CliProviderConfig();
        claude.setDisplayName("Claude Code");
        claude.setCommand("npx");
        claude.setArgs("@zed-industries/claude-agent-acp");
        claude.setCompatibleRuntimes(List.of(SandboxType.REMOTE));
        providers.put("claude-code", claude);

        // 一个肯定不存在的命令
        CliProviderConfig fake = new CliProviderConfig();
        fake.setDisplayName("Fake CLI");
        fake.setCommand("this-command-definitely-does-not-exist-xyz");
        fake.setArgs("--acp");
        // 不设置 compatibleRuntimes，确保不会因为 canRunInSandbox 短路为 available
        providers.put("fake-cli", fake);

        properties.setProviders(providers);
        controller = new CliProviderController(properties, null, null, null);
    }

    @Test
    void testListProvidersReturnsAllProviders() {
        List<CliProviderController.CliProviderInfo> result = controller.listProviders();
        assertEquals(4, result.size());
    }

    @Test
    void testDefaultProviderIsMarked() {
        List<CliProviderController.CliProviderInfo> result = controller.listProviders();
        long defaultCount =
                result.stream().filter(CliProviderController.CliProviderInfo::isDefault).count();
        assertEquals(1, defaultCount);

        CliProviderController.CliProviderInfo defaultProvider =
                result.stream()
                        .filter(CliProviderController.CliProviderInfo::isDefault)
                        .findFirst()
                        .orElseThrow();
        assertEquals("qodercli", defaultProvider.key());
    }

    @Test
    void testFakeCommandIsNotAvailable() {
        List<CliProviderController.CliProviderInfo> result = controller.listProviders();
        CliProviderController.CliProviderInfo fake =
                result.stream().filter(p -> p.key().equals("fake-cli")).findFirst().orElseThrow();
        assertFalse(fake.available(), "不存在的命令应该标记为不可用");
    }

    @Test
    void testIsCommandAvailableWithExistingCommand() {
        // 'ls' 在所有 Unix 系统上都存在
        assertTrue(CliProviderController.isCommandAvailable("ls"));
    }

    @Test
    void testIsCommandAvailableWithNonExistingCommand() {
        assertFalse(
                CliProviderController.isCommandAvailable(
                        "this-command-definitely-does-not-exist-xyz"));
    }

    @Test
    void testIsCommandAvailableWithNull() {
        assertFalse(CliProviderController.isCommandAvailable(null));
    }

    @Test
    void testIsCommandAvailableWithBlank() {
        assertFalse(CliProviderController.isCommandAvailable("   "));
    }

    @Test
    void testDisplayNameFallsBackToKey() {
        CliProviderConfig noName = new CliProviderConfig();
        noName.setCommand("ls");
        noName.setArgs("--help");
        // displayName 不设置，应该 fallback 到 key
        properties.getProviders().put("no-name-cli", noName);

        List<CliProviderController.CliProviderInfo> result = controller.listProviders();
        CliProviderController.CliProviderInfo noNameInfo =
                result.stream()
                        .filter(p -> p.key().equals("no-name-cli"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("no-name-cli", noNameInfo.displayName());
    }

    @Test
    void testAvailableFieldIncludedInResponse() {
        List<CliProviderController.CliProviderInfo> result = controller.listProviders();
        // 每个 provider 都应该有 available 字段（不管 true 还是 false）
        for (CliProviderController.CliProviderInfo info : result) {
            assertNotNull(info.key());
            assertNotNull(info.displayName());
            // available 是 boolean 基本类型，不会为 null
        }
    }

    @Test
    void testCompatibleRuntimesIncludedInResponse() {
        List<CliProviderController.CliProviderInfo> result = controller.listProviders();

        CliProviderController.CliProviderInfo qoder =
                result.stream().filter(p -> p.key().equals("qodercli")).findFirst().orElseThrow();
        assertEquals(
                List.of(SandboxType.REMOTE, SandboxType.OPEN_SANDBOX), qoder.compatibleRuntimes());

        CliProviderController.CliProviderInfo claude =
                result.stream()
                        .filter(p -> p.key().equals("claude-code"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(List.of(SandboxType.REMOTE), claude.compatibleRuntimes());
    }

    @Test
    void testProviderWithNullCompatibleRuntimes() {
        CliProviderConfig noRuntime = new CliProviderConfig();
        noRuntime.setDisplayName("No Runtime CLI");
        noRuntime.setCommand("ls");
        // 不设置 compatibleRuntimes
        properties.getProviders().put("no-runtime-cli", noRuntime);

        List<CliProviderController.CliProviderInfo> result = controller.listProviders();
        CliProviderController.CliProviderInfo noRuntimeInfo =
                result.stream()
                        .filter(p -> p.key().equals("no-runtime-cli"))
                        .findFirst()
                        .orElseThrow();
        assertNull(noRuntimeInfo.compatibleRuntimes());
    }
}
