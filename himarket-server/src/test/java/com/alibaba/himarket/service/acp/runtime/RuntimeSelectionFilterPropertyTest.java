package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.config.AcpProperties.RemoteConfig;
import java.util.List;
import net.jqwik.api.*;

/**
 * 运行时选择过滤与自动选择属性测试。
 *
 * <p>Feature: sandbox-runtime-strategy, Property 6: 运行时选择过滤与自动选择
 *
 * <p><b>Validates: Requirements 5.2, 5.4, 10.4</b>
 *
 * <p>对于任意 CLI Provider 及其 compatibleRuntimes 列表和当前环境可用性状态，RuntimeSelector 应该：
 * (a) 仅展示兼容且可用的运行时选项；
 * (b) 当兼容列表中仅有一个可用运行时时，自动选中该运行时；
 * (c) 当远程沙箱未配置时，REMOTE 选项标记为不可用。
 */
class RuntimeSelectionFilterPropertyTest {

    private static final String PROVIDER_KEY = "test-provider";

    // ===== 生成器 =====

    @Provide
    Arbitrary<List<SandboxType>> nonEmptyCompatibleRuntimes() {
        return Arbitraries.of(SandboxType.REMOTE, SandboxType.OPEN_SANDBOX)
                .list()
                .ofMinSize(1)
                .ofMaxSize(2)
                .uniqueElements()
                .filter(list -> !list.isEmpty());
    }

    @Provide
    Arbitrary<List<SandboxType>> compatibleRuntimes() {
        return Arbitraries.of(SandboxType.REMOTE, SandboxType.OPEN_SANDBOX)
                .list()
                .ofMinSize(0)
                .ofMaxSize(2)
                .uniqueElements();
    }

    @Provide
    Arbitrary<Boolean> remoteAvailability() {
        return Arbitraries.of(true, false);
    }

    // ===== 辅助方法 =====

    private RuntimeSelector buildSelector(
            List<SandboxType> compatibleRuntimes, boolean remoteAvailable) {
        AcpProperties props = new AcpProperties();
        props.setDefaultRuntime("remote");
        CliProviderConfig config = new CliProviderConfig();
        config.setDisplayName("Test Provider");
        config.setCompatibleRuntimes(compatibleRuntimes);
        props.getProviders().put(PROVIDER_KEY, config);
        RemoteConfig remoteConfig = new RemoteConfig();
        if (remoteAvailable) {
            remoteConfig.setHost("sandbox.example.com");
            remoteConfig.setPort(8080);
        } else {
            remoteConfig.setHost("");
        }
        props.setRemote(remoteConfig);
        return new RuntimeSelector(props);
    }

    // ===== Property 6a: 仅展示兼容的运行时选项 =====

    @Property(tries = 200)
    void availableRuntimes_onlyContainCompatibleTypes(
            @ForAll("compatibleRuntimes") List<SandboxType> compatible,
            @ForAll("remoteAvailability") boolean remoteAvailable) {

        RuntimeSelector selector = buildSelector(compatible, remoteAvailable);
        List<RuntimeOption> options = selector.getAvailableRuntimes(PROVIDER_KEY);

        List<SandboxType> returnedTypes = options.stream().map(RuntimeOption::type).toList();
        assertEquals(compatible.size(), returnedTypes.size(), "返回选项数量应与兼容列表一致");
        assertTrue(compatible.containsAll(returnedTypes), "返回的类型应全部在兼容列表中");
        assertTrue(returnedTypes.containsAll(compatible), "兼容列表中的类型应全部出现在返回结果中");
    }

    // ===== Property 6b: 单一可用运行时自动选中 =====

    @Property(tries = 200)
    void selectDefault_autoSelectsWhenOnlyOneAvailable(
            @ForAll("nonEmptyCompatibleRuntimes") List<SandboxType> compatible,
            @ForAll("remoteAvailability") boolean remoteAvailable) {

        RuntimeSelector selector = buildSelector(compatible, remoteAvailable);

        List<SandboxType> availableTypes =
                compatible.stream().filter(selector::isSandboxAvailable).toList();

        if (availableTypes.size() == 1) {
            SandboxType selected = selector.selectDefault(PROVIDER_KEY);
            assertEquals(availableTypes.get(0), selected, "仅有一个可用运行时时应自动选中该运行时");
        }
    }

    // ===== Property 6c: 远程沙箱未配置时 REMOTE 标记为不可用 =====

    @Property(tries = 200)
    void remoteMarkedUnavailable_whenNotConfigured(
            @ForAll("nonEmptyCompatibleRuntimes") List<SandboxType> compatible) {

        if (!compatible.contains(SandboxType.REMOTE)) {
            return;
        }

        RuntimeSelector selector = buildSelector(compatible, false);
        List<RuntimeOption> options = selector.getAvailableRuntimes(PROVIDER_KEY);

        RuntimeOption remoteOption =
                options.stream()
                        .filter(o -> o.type() == SandboxType.REMOTE)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("REMOTE 应出现在兼容列表的返回结果中"));

        assertFalse(remoteOption.available(), "远程沙箱未配置时 REMOTE 应标记为不可用");
        assertNotNull(remoteOption.unavailableReason(), "不可用的 REMOTE 应包含原因说明");
    }

    // ===== Property 6c 补充: 远程沙箱已配置时 REMOTE 标记为可用 =====

    @Property(tries = 200)
    void remoteMarkedAvailable_whenConfigured(
            @ForAll("nonEmptyCompatibleRuntimes") List<SandboxType> compatible) {

        if (!compatible.contains(SandboxType.REMOTE)) {
            return;
        }

        RuntimeSelector selector = buildSelector(compatible, true);
        List<RuntimeOption> options = selector.getAvailableRuntimes(PROVIDER_KEY);

        RuntimeOption remoteOption =
                options.stream()
                        .filter(o -> o.type() == SandboxType.REMOTE)
                        .findFirst()
                        .orElseThrow();

        assertTrue(remoteOption.available(), "远程沙箱已配置时 REMOTE 应标记为可用");
        assertNull(remoteOption.unavailableReason(), "可用的 REMOTE 不应包含不可用原因");
    }

    // ===== Property 6: selectDefault 返回的运行时必须是兼容且可用的 =====

    @Property(tries = 200)
    void selectDefault_alwaysReturnsCompatibleAndAvailableRuntime(
            @ForAll("nonEmptyCompatibleRuntimes") List<SandboxType> compatible,
            @ForAll("remoteAvailability") boolean remoteAvailable) {

        RuntimeSelector selector = buildSelector(compatible, remoteAvailable);

        List<SandboxType> availableTypes =
                compatible.stream().filter(selector::isSandboxAvailable).toList();

        if (availableTypes.isEmpty()) {
            assertThrows(
                    IllegalStateException.class,
                    () -> selector.selectDefault(PROVIDER_KEY),
                    "没有可用运行时时应抛出 IllegalStateException");
        } else {
            SandboxType selected = selector.selectDefault(PROVIDER_KEY);
            assertTrue(compatible.contains(selected), "选中的运行时必须在兼容列表中: " + selected);
            assertTrue(selector.isSandboxAvailable(selected), "选中的运行时必须在当前环境中可用: " + selected);
        }
    }
}
