package com.alibaba.himarket.service.hicoding.sandbox;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.jqwik.api.*;

/**
 * SandboxInfo 构建正确性属性测试。
 *
 * <p>Feature: sandbox-runtime-strategy, Property 11: SandboxInfo 构建正确性
 *
 * <p><b>Validates: Requirements 10.2, 10.4, 10.5</b>
 *
 * <p>对任意 command 和 args 组合，{@code sidecarWsUri()} 构建的 URI 包含正确的 host、port、command 和 URL
 * 编码的 args；REMOTE 类型 host 可为任意可达地址；K8S 类型 metadata 包含
 * podName 和 namespace。
 */
class SandboxInfoPropertyTest {

    // ===== 生成器 =====

    @Provide
    Arbitrary<String> commands() {
        return Arbitraries.of("node", "python", "bash", "cli", "npx", "java", "go", "ruby");
    }

    @Provide
    Arbitrary<String> nonBlankArgs() {
        return Arbitraries.of(
                "--help",
                "--version",
                "-f config.json",
                "run test",
                "--name=hello world",
                "arg1 arg2 arg3",
                "--path=/tmp/test file",
                "中文参数",
                "special&chars=value",
                "a=1&b=2",
                "hello+world",
                "--flag --key=val ue");
    }

    @Provide
    Arbitrary<String> nullOrBlankArgs() {
        return Arbitraries.of(null, "", "   ", "\t", "\n");
    }

    @Provide
    Arbitrary<Integer> sidecarPorts() {
        return Arbitraries.integers().between(1024, 65535);
    }

    @Provide
    Arbitrary<String> k8sPodNames() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(3)
                .ofMaxLength(20)
                .map(s -> "pod-" + s);
    }

    @Provide
    Arbitrary<String> k8sNamespaces() {
        return Arbitraries.of("himarket", "default", "sandbox", "production", "staging");
    }

    @Provide
    Arbitrary<String> k8sHosts() {
        return Arbitraries.integers()
                .between(1, 254)
                .list()
                .ofSize(4)
                .map(
                        octets ->
                                octets.get(0)
                                        + "."
                                        + octets.get(1)
                                        + "."
                                        + octets.get(2)
                                        + "."
                                        + octets.get(3));
    }

    // ===== Property 11: sidecarWsUri 构建正确性（非空 args） =====

    /**
     * <b>Validates: Requirements 10.2</b>
     *
     * <p>对任意 command 和非空 args，sidecarWsUri() 构建的 URI 包含正确的 host、port、command 和 URL 编码的
     * args。
     */
    @Property(tries = 200)
    void sidecarWsUri_containsCorrectComponentsWithArgs(
            @ForAll("commands") String command,
            @ForAll("nonBlankArgs") String args,
            @ForAll("sidecarPorts") int port) {
        SandboxInfo info =
                new SandboxInfo(
                        SandboxType.REMOTE,
                        "sandbox-" + port,
                        "sandbox.example.com",
                        port,
                        "/workspace",
                        false,
                        Map.of());

        URI uri = info.sidecarWsUri(command, args);

        assertEquals("ws", uri.getScheme(), "URI scheme 应为 ws");
        assertEquals("sandbox.example.com", uri.getHost(), "URI host 应与 SandboxInfo.host 一致");
        assertEquals(port, uri.getPort(), "URI port 应与 SandboxInfo.sidecarPort 一致");
        assertEquals("/", uri.getPath(), "URI path 应为 /");

        String query = uri.getRawQuery();
        assertNotNull(query, "URI query 不应为 null");
        assertTrue(query.startsWith("command=" + command), "query 应以 command= 开头");

        String expectedEncodedArgs = URLEncoder.encode(args, StandardCharsets.UTF_8);
        assertTrue(
                query.contains("args=" + expectedEncodedArgs),
                "query 应包含 URL 编码的 args: expected args=" + expectedEncodedArgs + " in " + query);
    }

    // ===== Property 11: sidecarWsUri 构建正确性（null 或空白 args） =====

    /**
     * <b>Validates: Requirements 10.2</b>
     *
     * <p>当 args 为 null 或空白时，sidecarWsUri() 构建的 URI 不包含 args 参数。
     */
    @Property(tries = 100)
    void sidecarWsUri_omitsArgsWhenNullOrBlank(
            @ForAll("commands") String command,
            @ForAll("nullOrBlankArgs") String args,
            @ForAll("sidecarPorts") int port) {
        SandboxInfo info =
                new SandboxInfo(
                        SandboxType.REMOTE,
                        "sandbox-" + port,
                        "sandbox.example.com",
                        port,
                        "/workspace",
                        false,
                        Map.of());

        URI uri = info.sidecarWsUri(command, args);

        String query = uri.getRawQuery();
        assertNotNull(query);
        assertEquals("command=" + command, query, "当 args 为 null/空白时，query 应仅包含 command");
        assertFalse(query.contains("args="), "当 args 为 null/空白时，query 不应包含 args");
    }

    // ===== Property 11: sidecarWsUri 对不同 host 和 port 的正确性 =====

    /**
     * <b>Validates: Requirements 10.2</b>
     *
     * <p>对任意 host 和 port，sidecarWsUri() 构建的 URI 正确反映 SandboxInfo 中的连接信息。
     */
    @Property(tries = 200)
    void sidecarWsUri_reflectsHostAndPort(
            @ForAll("commands") String command,
            @ForAll("k8sHosts") String host,
            @ForAll("sidecarPorts") int port) {
        SandboxInfo info =
                new SandboxInfo(
                        SandboxType.REMOTE, "pod-abc", host, port, "/workspace", false, Map.of());

        URI uri = info.sidecarWsUri(command, null);

        assertEquals(host, uri.getHost());
        assertEquals(port, uri.getPort());
        assertEquals("ws://" + host + ":" + port + "/?command=" + command, uri.toString());
    }

    // ===== Property 11: REMOTE 类型 metadata 包含 podName 和 namespace =====

    /**
     * <b>Validates: Requirements 10.5</b>
     *
     * <p>当 SandboxType 为 REMOTE 时，metadata 应包含 podName 和 namespace。
     */
    @Property(tries = 200)
    void k8sSandboxInfo_metadataContainsPodNameAndNamespace(
            @ForAll("k8sPodNames") String podName,
            @ForAll("k8sNamespaces") String namespace,
            @ForAll("k8sHosts") String host,
            @ForAll("sidecarPorts") int port) {
        Map<String, String> metadata = Map.of("podName", podName, "namespace", namespace);
        SandboxInfo info =
                new SandboxInfo(
                        SandboxType.REMOTE, podName, host, port, "/workspace", false, metadata);

        assertEquals(SandboxType.REMOTE, info.type());
        assertNotNull(info.metadata(), "K8S 类型 metadata 不应为 null");
        assertTrue(info.metadata().containsKey("podName"), "K8S metadata 应包含 podName");
        assertTrue(info.metadata().containsKey("namespace"), "K8S metadata 应包含 namespace");
        assertEquals(podName, info.metadata().get("podName"));
        assertEquals(namespace, info.metadata().get("namespace"));
    }

    // ===== Property 11: sidecarWsUri 返回的 URI 始终可解析 =====

    /**
     * <b>Validates: Requirements 10.2</b>
     *
     * <p>对任意合法输入，sidecarWsUri() 返回的 URI 始终是可解析的有效 URI。
     */
    @Property(tries = 200)
    void sidecarWsUri_alwaysReturnsValidUri(
            @ForAll("commands") String command,
            @ForAll("nonBlankArgs") String args,
            @ForAll("k8sHosts") String host,
            @ForAll("sidecarPorts") int port) {
        SandboxInfo info =
                new SandboxInfo(
                        SandboxType.REMOTE, "pod-test", host, port, "/workspace", false, Map.of());

        URI uri = info.sidecarWsUri(command, args);

        assertNotNull(uri);
        assertDoesNotThrow(uri::getScheme);
        assertDoesNotThrow(uri::getHost);
        assertDoesNotThrow(uri::getPort);
        assertDoesNotThrow(uri::getRawQuery);
        assertEquals("ws", uri.getScheme());
    }
}
