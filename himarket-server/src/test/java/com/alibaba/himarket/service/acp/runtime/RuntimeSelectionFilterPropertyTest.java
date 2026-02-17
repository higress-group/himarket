package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
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
 * (c) 当 K8s 未配置时，K8S 选项标记为不可用。
 */
class RuntimeSelectionFilterPropertyTest {

    private static final String PROVIDER_KEY = "test-provider";

    // ===== 生成器 =====

    @Provide
    Arbitrary<List<RuntimeType>> nonEmptyCompatibleRuntimes() {
        return Arbitraries.of(RuntimeType.values())
                .list()
                .ofMinSize(1)
                .ofMaxSize(2)
                .uniqueElements()
                .filter(list -> !list.isEmpty());
    }

    @Provide
    Arbitrary<List<RuntimeType>> compatibleRuntimes() {
        return Arbitraries.of(RuntimeType.values())
                .list()
                .ofMinSize(0)
                .ofMaxSize(2)
                .uniqueElements();
    }

    @Provide
    Arbitrary<Boolean> k8sAvailability() {
        return Arbitraries.of(true, false);
    }

    // ===== 辅助方法 =====

    private RuntimeSelector buildSelector(
            List<RuntimeType> compatibleRuntimes, boolean k8sAvailable) {
        AcpProperties props = new AcpProperties();
        props.setDefaultRuntime("local");
        CliProviderConfig config = new CliProviderConfig();
        config.setDisplayName("Test Provider");
        config.setCompatibleRuntimes(compatibleRuntimes);
        props.getProviders().put(PROVIDER_KEY, config);
        return new RuntimeSelector(props, k8sAvailable);
    }

    // ===== Property 6a: 仅展示兼容的运行时选项 =====

    @Property(tries = 200)
    void availableRuntimes_onlyContainCompatibleTypes(
            @ForAll("compatibleRuntimes") List<RuntimeType> compatible,
            @ForAll("k8sAvailability") boolean k8sAvailable) {

        RuntimeSelector selector = buildSelector(compatible, k8sAvailable);
        List<RuntimeOption> options = selector.getAvailableRuntimes(PROVIDER_KEY);

        List<RuntimeType> returnedTypes = options.stream().map(RuntimeOption::type).toList();
        assertEquals(compatible.size(), returnedTypes.size(), "返回选项数量应与兼容列表一致");
        assertTrue(compatible.containsAll(returnedTypes), "返回的类型应全部在兼容列表中");
        assertTrue(returnedTypes.containsAll(compatible), "兼容列表中的类型应全部出现在返回结果中");
    }

    // ===== Property 6b: 单一可用运行时自动选中 =====

    @Property(tries = 200)
    void selectDefault_autoSelectsWhenOnlyOneAvailable(
            @ForAll("nonEmptyCompatibleRuntimes") List<RuntimeType> compatible,
            @ForAll("k8sAvailability") boolean k8sAvailable) {

        RuntimeSelector selector = buildSelector(compatible, k8sAvailable);

        List<RuntimeType> availableTypes =
                compatible.stream().filter(selector::isRuntimeAvailable).toList();

        if (availableTypes.size() == 1) {
            RuntimeType selected = selector.selectDefault(PROVIDER_KEY);
            assertEquals(availableTypes.get(0), selected, "仅有一个可用运行时时应自动选中该运行时");
        }
    }

    // ===== Property 6c: K8s 未配置时 K8S 标记为不可用 =====

    /**
     * <b>Validates: Requirements 10.4</b>
     *
     * <p>对于任意包含 K8S 的 compatibleRuntimes 列表，当 K8s 未配置时，
     * K8S 选项应标记为不可用（available=false），且包含不可用原因。
     */
    @Property(tries = 200)
    void k8sMarkedUnavailable_whenK8sNotConfigured(
            @ForAll("nonEmptyCompatibleRuntimes") List<RuntimeType> compatible) {

        if (!compatible.contains(RuntimeType.K8S)) {
            return;
        }

        RuntimeSelector selector = buildSelector(compatible, false);
        List<RuntimeOption> options = selector.getAvailableRuntimes(PROVIDER_KEY);

        RuntimeOption k8sOption =
                options.stream()
                        .filter(o -> o.type() == RuntimeType.K8S)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("K8S 应出现在兼容列表的返回结果中"));

        assertFalse(k8sOption.available(), "K8s 未配置时 K8S 应标记为不可用");
        assertNotNull(k8sOption.unavailableReason(), "不可用的 K8S 应包含原因说明");
        assertTrue(k8sOption.unavailableReason().contains("K8s"), "不可用原因应提及 K8s 配置");
    }

    // ===== Property 6c 补充: K8s 已配置时 K8S 标记为可用 =====

    @Property(tries = 200)
    void k8sMarkedAvailable_whenK8sConfigured(
            @ForAll("nonEmptyCompatibleRuntimes") List<RuntimeType> compatible) {

        if (!compatible.contains(RuntimeType.K8S)) {
            return;
        }

        RuntimeSelector selector = buildSelector(compatible, true);
        List<RuntimeOption> options = selector.getAvailableRuntimes(PROVIDER_KEY);

        RuntimeOption k8sOption =
                options.stream().filter(o -> o.type() == RuntimeType.K8S).findFirst().orElseThrow();

        assertTrue(k8sOption.available(), "K8s 已配置时 K8S 应标记为可用");
        assertNull(k8sOption.unavailableReason(), "可用的 K8S 不应包含不可用原因");
    }

    // ===== Property 6: selectDefault 返回的运行时必须是兼容且可用的 =====

    @Property(tries = 200)
    void selectDefault_alwaysReturnsCompatibleAndAvailableRuntime(
            @ForAll("nonEmptyCompatibleRuntimes") List<RuntimeType> compatible,
            @ForAll("k8sAvailability") boolean k8sAvailable) {

        RuntimeSelector selector = buildSelector(compatible, k8sAvailable);

        List<RuntimeType> availableTypes =
                compatible.stream().filter(selector::isRuntimeAvailable).toList();

        if (availableTypes.isEmpty()) {
            assertThrows(
                    IllegalStateException.class,
                    () -> selector.selectDefault(PROVIDER_KEY),
                    "没有可用运行时时应抛出 IllegalStateException");
        } else {
            RuntimeType selected = selector.selectDefault(PROVIDER_KEY);
            assertTrue(compatible.contains(selected), "选中的运行时必须在兼容列表中: " + selected);
            assertTrue(selector.isRuntimeAvailable(selected), "选中的运行时必须在当前环境中可用: " + selected);
        }
    }

    // ===== Property 6: LOCAL 始终可用 =====

    @Property(tries = 100)
    void localRuntime_alwaysAvailable(@ForAll("k8sAvailability") boolean k8sAvailable) {

        AcpProperties props = new AcpProperties();
        RuntimeSelector selector = new RuntimeSelector(props, k8sAvailable);

        assertTrue(selector.isRuntimeAvailable(RuntimeType.LOCAL), "LOCAL 运行时应始终可用，无论 K8s 状态如何");

        RuntimeOption option = selector.toRuntimeOption(RuntimeType.LOCAL, true);
        assertTrue(option.available());
        assertNull(option.unavailableReason());
    }
}
