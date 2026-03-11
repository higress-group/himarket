package com.alibaba.himarket.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.config.AcpProperties.RemoteConfig;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeOption;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeSelector;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 单元测试：验证 RuntimeController 的运行时可用性查询逻辑。
 */
class RuntimeControllerTest {

    private RuntimeController controller;
    private AcpProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AcpProperties();
        properties.setDefaultRuntime("remote");

        Map<String, CliProviderConfig> providers = new LinkedHashMap<>();

        CliProviderConfig qoder = new CliProviderConfig();
        qoder.setDisplayName("Qoder CLI");
        qoder.setCommand("qodercli");
        qoder.setCompatibleRuntimes(List.of(SandboxType.REMOTE, SandboxType.OPEN_SANDBOX));
        providers.put("qodercli", qoder);

        CliProviderConfig claude = new CliProviderConfig();
        claude.setDisplayName("Claude Code");
        claude.setCommand("npx");
        claude.setCompatibleRuntimes(List.of(SandboxType.REMOTE));
        providers.put("claude-code", claude);

        CliProviderConfig remoteOnly = new CliProviderConfig();
        remoteOnly.setDisplayName("Remote Only CLI");
        remoteOnly.setCommand("remote-cli");
        remoteOnly.setCompatibleRuntimes(List.of(SandboxType.REMOTE));
        providers.put("remote-only", remoteOnly);

        properties.setProviders(providers);
    }

    private RuntimeSelector createSelector(boolean remoteAvailable) {
        RemoteConfig remoteConfig = new RemoteConfig();
        if (remoteAvailable) {
            remoteConfig.setHost("sandbox.example.com");
            remoteConfig.setPort(8080);
        } else {
            remoteConfig.setHost("");
        }
        properties.setRemote(remoteConfig);
        return new RuntimeSelector(properties);
    }

    @Test
    void testGetAvailableRuntimesForNativeProvider() {
        // 远程沙箱不可用
        RuntimeSelector selector = createSelector(false);
        controller = new RuntimeController(selector);

        List<RuntimeOption> result = controller.getAvailableRuntimes("qodercli");
        assertEquals(2, result.size());

        RuntimeOption remoteOption =
                result.stream()
                        .filter(r -> r.type() == SandboxType.REMOTE)
                        .findFirst()
                        .orElseThrow();
        // 远程沙箱不可用时 REMOTE 标记为不可用
        assertFalse(remoteOption.available());
        assertNotNull(remoteOption.unavailableReason());
    }

    @Test
    void testGetAvailableRuntimesForNodejsProvider() {
        RuntimeSelector selector = createSelector(true);
        controller = new RuntimeController(selector);

        List<RuntimeOption> result = controller.getAvailableRuntimes("claude-code");
        assertEquals(1, result.size());

        // REMOTE 已配置，应该可用
        RuntimeOption remoteOption = result.get(0);
        assertEquals(SandboxType.REMOTE, remoteOption.type());
        assertTrue(remoteOption.available());
    }

    @Test
    void testGetAvailableRuntimesWithK8sEnabled() {
        RuntimeSelector selector = createSelector(true);
        controller = new RuntimeController(selector);

        List<RuntimeOption> result = controller.getAvailableRuntimes("qodercli");
        RuntimeOption remoteOption =
                result.stream()
                        .filter(r -> r.type() == SandboxType.REMOTE)
                        .findFirst()
                        .orElseThrow();
        assertTrue(remoteOption.available());
        assertNull(remoteOption.unavailableReason());
    }

    @Test
    void testGetAvailableRuntimesForUnknownProvider() {
        RuntimeSelector selector = createSelector(false);
        controller = new RuntimeController(selector);

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.getAvailableRuntimes("unknown-provider"));
    }

    @Test
    void testGetAvailableRuntimesForRemoteOnlyProvider() {
        RuntimeSelector selector = createSelector(true);
        controller = new RuntimeController(selector);

        List<RuntimeOption> result = controller.getAvailableRuntimes("remote-only");
        assertEquals(1, result.size());
        assertEquals(SandboxType.REMOTE, result.get(0).type());
        assertTrue(result.get(0).available());
    }
}
