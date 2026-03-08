package com.alibaba.himarket.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.config.AcpProperties.RemoteConfig;
import com.alibaba.himarket.service.acp.runtime.RuntimeOption;
import com.alibaba.himarket.service.acp.runtime.RuntimeSelector;
import com.alibaba.himarket.service.acp.runtime.SandboxType;
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
        properties.setDefaultRuntime("local");

        Map<String, CliProviderConfig> providers = new LinkedHashMap<>();

        CliProviderConfig qoder = new CliProviderConfig();
        qoder.setDisplayName("Qoder CLI");
        qoder.setCommand("qodercli");
        qoder.setCompatibleRuntimes(List.of(SandboxType.LOCAL, SandboxType.REMOTE));
        providers.put("qodercli", qoder);

        CliProviderConfig claude = new CliProviderConfig();
        claude.setDisplayName("Claude Code");
        claude.setCommand("npx");
        claude.setCompatibleRuntimes(List.of(SandboxType.LOCAL, SandboxType.REMOTE));
        providers.put("claude-code", claude);

        CliProviderConfig localOnly = new CliProviderConfig();
        localOnly.setDisplayName("Local Only CLI");
        localOnly.setCommand("local-cli");
        localOnly.setCompatibleRuntimes(List.of(SandboxType.LOCAL));
        providers.put("local-only", localOnly);

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
        // K8s 不可用
        RuntimeSelector selector = createSelector(false);
        controller = new RuntimeController(selector);

        List<RuntimeOption> result = controller.getAvailableRuntimes("qodercli");
        assertEquals(2, result.size());

        RuntimeOption local =
                result.stream()
                        .filter(r -> r.type() == SandboxType.LOCAL)
                        .findFirst()
                        .orElseThrow();
        assertTrue(local.available());

        RuntimeOption k8sOption =
                result.stream()
                        .filter(r -> r.type() == SandboxType.REMOTE)
                        .findFirst()
                        .orElseThrow();
        // K8s 不可用时 K8S 也标记为不可用
        assertFalse(k8sOption.available());
        assertNotNull(k8sOption.unavailableReason());
    }

    @Test
    void testGetAvailableRuntimesForNodejsProvider() {
        RuntimeSelector selector = createSelector(true);
        controller = new RuntimeController(selector);

        List<RuntimeOption> result = controller.getAvailableRuntimes("claude-code");
        assertEquals(2, result.size());

        // 所有运行时都应该可用（K8s 已配置）
        assertTrue(result.stream().allMatch(RuntimeOption::available));
    }

    @Test
    void testGetAvailableRuntimesWithK8sEnabled() {
        RuntimeSelector selector = createSelector(true);
        controller = new RuntimeController(selector);

        List<RuntimeOption> result = controller.getAvailableRuntimes("qodercli");
        RuntimeOption k8sOption =
                result.stream()
                        .filter(r -> r.type() == SandboxType.REMOTE)
                        .findFirst()
                        .orElseThrow();
        assertTrue(k8sOption.available());
        assertNull(k8sOption.unavailableReason());
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
    void testGetAvailableRuntimesForLocalOnlyProvider() {
        RuntimeSelector selector = createSelector(false);
        controller = new RuntimeController(selector);

        List<RuntimeOption> result = controller.getAvailableRuntimes("local-only");
        assertEquals(1, result.size());
        assertEquals(SandboxType.LOCAL, result.get(0).type());
        assertTrue(result.get(0).available());
    }
}
