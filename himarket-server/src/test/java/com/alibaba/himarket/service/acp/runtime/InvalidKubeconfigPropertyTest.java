package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import net.jqwik.api.*;

/**
 * 无效 Kubeconfig 验证属性测试。
 *
 * <p>Feature: sandbox-runtime-strategy, Property 12: 无效 Kubeconfig 验证
 *
 * <p><b>Validates: Requirements 3.10, 10.2</b>
 *
 * <p>对于任意格式错误的 kubeconfig 字符串，K8sConfigService 的注册操作应该返回验证失败错误，
 * 不将无效配置存入缓存。
 *
 * <p>注意：本测试仅验证 kubeconfig 格式解析阶段的拒绝行为（null/空白/语法错误的YAML），
 * 不包含可能通过 Fabric8 解析但触发网络连接的输入（避免测试超时）。
 * 指向不可达集群的 kubeconfig 验证由集成测试覆盖。
 */
class InvalidKubeconfigPropertyTest {

    // ===== 生成器：各种无效 kubeconfig 字符串 =====

    /** 空字符串和空白字符串 */
    @Provide
    Arbitrary<String> emptyOrBlankStrings() {
        return Arbitraries.of("", " ", "  ", "\t", "\n", "\t\n ", "   \t\t\n\n  ");
    }

    /**
     * YAML 语法错误的字符串 — 这些字符串在 YAML 解析阶段就会失败，
     * 不会进入 Fabric8 客户端创建或网络连接阶段。
     */
    @Provide
    Arbitrary<String> syntacticallyInvalidYaml() {
        return Arbitraries.of(
                "not-valid-yaml: [[[",
                "key: [unclosed bracket",
                "random: {key: {nested: [}}}",
                "{{{triple braces}}}",
                "mapping: {\n  broken: [}",
                "- item\n  bad-indent: value\n wrong",
                "key: {a: [1, 2}",
                "seq: [1, 2, {key: ]",
                "bad: [[[nested: unclosed",
                "flow: {a: b, c: [d, e}",
                "apiVersion: v1\nclusters:\n- cluster:\n    server: [[[broken",
                "apiVersion: v1\ncontexts: {[invalid",
                "key: !!invalid-tag [[[",
                "duplicate:\nduplicate:\n  nested: [[[");
    }

    /**
     * 使用 jqwik 组合器生成随机的 YAML 语法错误字符串。
     * 策略：在随机 key 后面附加未闭合的括号/花括号，确保 YAML 解析失败。
     */
    @Provide
    Arbitrary<String> generatedInvalidYaml() {
        Arbitrary<String> keys = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
        Arbitrary<String> brokenValues =
                Arbitraries.of(
                        ": [[[",
                        ": {{{",
                        ": [{[",
                        ": [1, 2, {",
                        ": {a: [}",
                        ":\n  - [[[",
                        ":\n  nested: {[}",
                        ": [unclosed, bracket",
                        ": {unclosed: brace",
                        ": [{mixed: [}");
        return Combinators.combine(keys, brokenValues).as((k, v) -> k + v);
    }

    /**
     * 包含特殊字符和注入攻击的字符串 — 这些不是有效 YAML 或会在解析时失败。
     */
    @Provide
    Arbitrary<String> specialCharacterStrings() {
        return Arbitraries.of(
                "\0\0\0",
                "null\0byte\0injection",
                "\uFFFF\uFFFE\uFFFD",
                "key: value\0hidden: secret",
                "normal: text\0: [[[broken");
    }

    /** 所有无效 kubeconfig 的组合生成器 */
    @Provide
    Arbitrary<String> allInvalidKubeconfigs() {
        return Arbitraries.oneOf(
                emptyOrBlankStrings(),
                syntacticallyInvalidYaml(),
                generatedInvalidYaml(),
                specialCharacterStrings());
    }

    // ===== Property 12: 无效 kubeconfig 被拒绝且不存入缓存 =====

    /**
     * <b>Validates: Requirements 3.10, 10.2</b>
     *
     * <p>对于任意空白或空字符串 kubeconfig，registerConfig 应抛出异常且不存入缓存。
     */
    @Property(tries = 100)
    void registerConfig_rejectsEmptyOrBlankKubeconfig(
            @ForAll("emptyOrBlankStrings") String invalidKubeconfig) {
        K8sConfigService service = new K8sConfigService();

        Exception ex =
                assertThrows(
                        Exception.class,
                        () -> service.registerConfig(invalidKubeconfig),
                        "应拒绝空白 kubeconfig");
        assertNotNull(ex.getMessage(), "错误信息不应为 null");
        assertFalse(service.hasAnyCluster(), "无效配置不应存入缓存");
    }

    /**
     * <b>Validates: Requirements 3.10, 10.2</b>
     *
     * <p>对于任意 YAML 语法错误的字符串，registerConfig 应抛出异常且不存入缓存。
     */
    @Property(tries = 100)
    void registerConfig_rejectsSyntacticallyInvalidYaml(
            @ForAll("syntacticallyInvalidYaml") String invalidKubeconfig) {
        K8sConfigService service = new K8sConfigService();

        assertThrows(
                Exception.class,
                () -> service.registerConfig(invalidKubeconfig),
                "应拒绝语法错误的 YAML: " + truncate(invalidKubeconfig));
        assertFalse(service.hasAnyCluster(), "无效配置不应存入缓存");
    }

    /**
     * <b>Validates: Requirements 3.10, 10.2</b>
     *
     * <p>对于任意随机生成的 YAML 语法错误字符串，registerConfig 应抛出异常且不存入缓存。
     */
    @Property(tries = 200)
    void registerConfig_rejectsGeneratedInvalidYaml(
            @ForAll("generatedInvalidYaml") String invalidKubeconfig) {
        K8sConfigService service = new K8sConfigService();

        assertThrows(
                Exception.class,
                () -> service.registerConfig(invalidKubeconfig),
                "应拒绝生成的无效 YAML: " + truncate(invalidKubeconfig));
        assertFalse(service.hasAnyCluster(), "无效配置不应存入缓存");
    }

    /**
     * <b>Validates: Requirements 3.10, 10.2</b>
     *
     * <p>对于任意包含特殊字符的字符串，registerConfig 应抛出异常且不存入缓存。
     */
    @Property(tries = 100)
    void registerConfig_rejectsSpecialCharacterStrings(
            @ForAll("specialCharacterStrings") String invalidKubeconfig) {
        K8sConfigService service = new K8sConfigService();

        assertThrows(
                Exception.class, () -> service.registerConfig(invalidKubeconfig), "应拒绝特殊字符字符串");
        assertFalse(service.hasAnyCluster(), "无效配置不应存入缓存");
    }

    /**
     * <b>Validates: Requirements 3.10, 10.2</b>
     *
     * <p>综合属性：对于任意无效 kubeconfig（空白、语法错误 YAML、特殊字符），
     * registerConfig 应抛出异常，返回明确的错误信息，且不将无效配置存入缓存。
     */
    @Property(tries = 200)
    void registerConfig_rejectsAllInvalidKubeconfigs_andNeverCaches(
            @ForAll("allInvalidKubeconfigs") String invalidKubeconfig) {
        K8sConfigService service = new K8sConfigService();

        // 1. 应抛出异常
        Exception ex =
                assertThrows(
                        Exception.class,
                        () -> service.registerConfig(invalidKubeconfig),
                        "应拒绝无效 kubeconfig: " + truncate(invalidKubeconfig));

        // 2. 错误信息应明确（非 null 且非空）
        assertNotNull(ex.getMessage(), "错误信息不应为 null");
        assertFalse(ex.getMessage().isBlank(), "错误信息不应为空白");

        // 3. 无效配置不应存入缓存
        assertFalse(service.hasAnyCluster(), "无效配置不应存入缓存");

        // 4. listClusters 应为空
        assertTrue(service.listClusters().isEmpty(), "无效配置后集群列表应为空");
    }

    // ===== 辅助方法 =====

    private static String truncate(String s) {
        if (s == null) return "null";
        String sanitized = s.replace("\0", "\\0").replace("\n", "\\n");
        return sanitized.length() > 80 ? sanitized.substring(0, 80) + "..." : sanitized;
    }
}
