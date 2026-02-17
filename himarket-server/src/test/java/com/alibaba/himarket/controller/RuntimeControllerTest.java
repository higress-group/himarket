package com.alibaba.himarket.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.acp.runtime.RuntimeOption;
import com.alibaba.himarket.service.acp.runtime.RuntimeSelector;
import com.alibaba.himarket.service.acp.runtime.RuntimeType;
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
        qoder.setRuntimeCategory("native");
        qoder.setCompatibleRuntimes(List.of(RuntimeType.LOCAL, RuntimeType.K8S));
        providers.put("qodercli", qoder);

        CliProviderConfig claude = new CliProviderConfig();
        claude.setDisplayName("Claude Code");
        claude.setCommand("npx");
        claude.setRuntimeCategory("nodejs");
        claude.setCompatibleRuntimes(List.of(RuntimeType.LOCAL, RuntimeType.K8S));
        providers.put("claude-code", claude);

        CliProviderConfig localOnly = new CliProviderConfig();
        localOnly.setDisplayName("Local Only CLI");
        localOnly.setCommand("local-cli");
        localOnly.setRuntimeCategory("native");
        localOnly.setCompatibleRuntimes(List.of(RuntimeType.LOCAL));
        providers.put("local-only", localOnly);

        properties.setProviders(providers);
    }

    @Test
    void testGetAvailableRuntimesForNativeProvider() {
        // K8s 不可用
        RuntimeSelector selector = new RuntimeSelector(properties, false);
        controller = new RuntimeController(selector);

        List<RuntimeOption> result = controller.getAvailableRuntimes("qodercli");
        assertEquals(2, result.size());

        RuntimeOption local =
                result.stream()
                        .filter(r -> r.type() == RuntimeType.LOCAL)
                        .findFirst()
                        .orElseThrow();
        assertTrue(local.available());

        RuntimeOption k8sOption =
                result.stream().filter(r -> r.type() == RuntimeType.K8S).findFirst().orElseThrow();
        // K8s 不可用时 K8S 也标记为不可用
        assertFalse(k8sOption.available());
        assertNotNull(k8sOption.unavailableReason());
    }

    @Test
    void testGetAvailableRuntimesForNodejsProvider() {
        RuntimeSelector selector = new RuntimeSelector(properties, true);
        controller = new RuntimeController(selector);

        List<RuntimeOption> result = controller.getAvailableRuntimes("claude-code");
        assertEquals(2, result.size());

        // 所有运行时都应该可用（K8s 已配置）
        assertTrue(result.stream().allMatch(RuntimeOption::available));
    }

    @Test
    void testGetAvailableRuntimesWithK8sEnabled() {
        RuntimeSelector selector = new RuntimeSelector(properties, true);
        controller = new RuntimeController(selector);

        List<RuntimeOption> result = controller.getAvailableRuntimes("qodercli");
        RuntimeOption k8sOption =
                result.stream().filter(r -> r.type() == RuntimeType.K8S).findFirst().orElseThrow();
        assertTrue(k8sOption.available());
        assertNull(k8sOption.unavailableReason());
    }

    @Test
    void testGetAvailableRuntimesForUnknownProvider() {
        RuntimeSelector selector = new RuntimeSelector(properties, false);
        controller = new RuntimeController(selector);

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.getAvailableRuntimes("unknown-provider"));
    }

    @Test
    void testGetAvailableRuntimesForLocalOnlyProvider() {
        RuntimeSelector selector = new RuntimeSelector(properties, false);
        controller = new RuntimeController(selector);

        List<RuntimeOption> result = controller.getAvailableRuntimes("local-only");
        assertEquals(1, result.size());
        assertEquals(RuntimeType.LOCAL, result.get(0).type());
        assertTrue(result.get(0).available());
    }
}
