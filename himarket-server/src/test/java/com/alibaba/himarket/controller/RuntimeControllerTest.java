package com.alibaba.himarket.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.acp.runtime.K8sClusterInfo;
import com.alibaba.himarket.service.acp.runtime.K8sConfigService;
import com.alibaba.himarket.service.acp.runtime.RuntimeOption;
import com.alibaba.himarket.service.acp.runtime.RuntimeSelector;
import com.alibaba.himarket.service.acp.runtime.SandboxType;
import java.util.Collections;
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
        qoder.setCompatibleRuntimes(List.of(SandboxType.LOCAL, SandboxType.K8S));
        providers.put("qodercli", qoder);

        CliProviderConfig claude = new CliProviderConfig();
        claude.setDisplayName("Claude Code");
        claude.setCommand("npx");
        claude.setRuntimeCategory("nodejs");
        claude.setCompatibleRuntimes(List.of(SandboxType.LOCAL, SandboxType.K8S));
        providers.put("claude-code", claude);

        CliProviderConfig localOnly = new CliProviderConfig();
        localOnly.setDisplayName("Local Only CLI");
        localOnly.setCommand("local-cli");
        localOnly.setRuntimeCategory("native");
        localOnly.setCompatibleRuntimes(List.of(SandboxType.LOCAL));
        providers.put("local-only", localOnly);

        properties.setProviders(providers);
    }

    private RuntimeSelector createSelector(boolean k8sAvailable) {
        K8sConfigService mockK8s =
                new K8sConfigService(null) {
                    @Override
                    public void init() {}

                    @Override
                    public boolean hasAnyCluster() {
                        return k8sAvailable;
                    }

                    @Override
                    public List<K8sClusterInfo> listClusters() {
                        return Collections.emptyList();
                    }
                };
        return new RuntimeSelector(properties, mockK8s);
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
                result.stream().filter(r -> r.type() == SandboxType.K8S).findFirst().orElseThrow();
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
                result.stream().filter(r -> r.type() == SandboxType.K8S).findFirst().orElseThrow();
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
