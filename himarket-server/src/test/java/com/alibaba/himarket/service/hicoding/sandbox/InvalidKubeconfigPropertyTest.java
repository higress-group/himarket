package com.alibaba.himarket.service.hicoding.sandbox;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alibaba.himarket.repository.K8sClusterRepository;
import net.jqwik.api.*;

/**
 * 无效 Kubeconfig 验证属性测试。
 *
 * <p>Feature: sandbox-runtime-strategy, Property 12: 无效 Kubeconfig 验证
 *
 * <p><b>Validates: Requirements 3.10, 10.2</b>
 *
 * <p>对于任意格式错误的 kubeconfig 字符串，K8sConfigService 的注册操作应该返回验证失败错误，
 * 不将无效配置存入数据库。
 *
 * <p>注意：本测试仅验证 kubeconfig 格式解析阶段的拒绝行为（null/空白字符串），
 * 不涉及 Fabric8 客户端创建或 K8s 集群网络连接。
 */
class InvalidKubeconfigPropertyTest {

    private K8sConfigService createService() {
        K8sClusterRepository mockRepository = mock(K8sClusterRepository.class);
        when(mockRepository.findAll()).thenReturn(java.util.Collections.emptyList());
        when(mockRepository.count()).thenReturn(0L);
        return new K8sConfigService(mockRepository);
    }

    // ===== 生成器 =====

    /** 空字符串和空白字符串 */
    @Provide
    Arbitrary<String> emptyOrBlankStrings() {
        return Arbitraries.of("", " ", "  ", "\t", "\n", "\t\n ", "   \t\t\n\n  ");
    }

    /** 随机生成的空白字符串（各种空白字符组合） */
    @Provide
    Arbitrary<String> randomBlankStrings() {
        return Arbitraries.strings()
                .withChars(' ', '\t', '\n', '\r')
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    /** null 和空白的组合 */
    @Provide
    Arbitrary<String> allBlankInputs() {
        return Arbitraries.oneOf(emptyOrBlankStrings(), randomBlankStrings());
    }

    // ===== Property 12: 空白 kubeconfig 被拒绝 =====

    /**
     * <b>Validates: Requirements 3.10, 10.2</b>
     *
     * <p>对于任意空白或空字符串 kubeconfig，registerConfig 应抛出 IllegalArgumentException。
     */
    @Property(tries = 100)
    void registerConfig_rejectsEmptyOrBlankKubeconfig(
            @ForAll("emptyOrBlankStrings") String invalidKubeconfig) {
        K8sConfigService service = createService();

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.registerConfig(invalidKubeconfig),
                        "应拒绝空白 kubeconfig");
        assertNotNull(ex.getMessage(), "错误信息不应为 null");
        assertFalse(service.hasAnyCluster(), "无效配置不应存入数据库");
    }

    /**
     * <b>Validates: Requirements 3.10, 10.2</b>
     *
     * <p>对于任意随机生成的空白字符串，registerConfig 应抛出 IllegalArgumentException。
     */
    @Property(tries = 200)
    void registerConfig_rejectsRandomBlankStrings(
            @ForAll("randomBlankStrings") String invalidKubeconfig) {
        K8sConfigService service = createService();

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.registerConfig(invalidKubeconfig),
                        "应拒绝空白 kubeconfig");
        assertNotNull(ex.getMessage(), "错误信息不应为 null");
        assertFalse(ex.getMessage().isBlank(), "错误信息不应为空白");
        assertFalse(service.hasAnyCluster(), "无效配置不应存入数据库");
    }

    /**
     * <b>Validates: Requirements 3.10, 10.2</b>
     *
     * <p>综合属性：对于任意空白输入，registerConfig 应抛出异常，
     * 返回明确的错误信息，且不将无效配置存入数据库。
     */
    @Property(tries = 200)
    void registerConfig_rejectsAllBlankInputs_andNeverPersists(
            @ForAll("allBlankInputs") String invalidKubeconfig) {
        K8sConfigService service = createService();

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.registerConfig(invalidKubeconfig),
                        "应拒绝空白 kubeconfig");

        assertNotNull(ex.getMessage(), "错误信息不应为 null");
        assertFalse(ex.getMessage().isBlank(), "错误信息不应为空白");
        assertFalse(service.hasAnyCluster(), "无效配置不应存入数据库");
        assertTrue(service.listClusters().isEmpty(), "无效配置后集群列表应为空");
    }

    /**
     * registerConfig(null) 应抛出 IllegalArgumentException。
     */
    @Example
    void registerConfig_rejectsNull() {
        K8sConfigService service = createService();

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.registerConfig(null),
                        "应拒绝 null kubeconfig");
        assertNotNull(ex.getMessage());
    }
}
