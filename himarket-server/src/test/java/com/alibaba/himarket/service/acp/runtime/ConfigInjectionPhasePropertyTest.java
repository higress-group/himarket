package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.acp.CliSessionConfig;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.jqwik.api.*;
import reactor.core.publisher.Flux;

/**
 * ConfigInjectionPhase 属性基测试。
 *
 * <p>Feature: sandbox-runtime-strategy
 *
 * <p>验证配置注入阶段在各种输入组合下满足正确性属性。
 */
class ConfigInjectionPhasePropertyTest {

    // ===== 辅助：基于 HashMap 的 Stub SandboxProvider =====

    /**
     * 使用 HashMap 模拟文件系统的 SandboxProvider。 writeFile 存入 map，readFile 从 map 读取。
     */
    static class InMemorySandboxProvider implements SandboxProvider {

        private final Map<String, String> fileStore = new ConcurrentHashMap<>();

        @Override
        public SandboxType getType() {
            return SandboxType.LOCAL;
        }

        @Override
        public SandboxInfo acquire(SandboxConfig config) {
            return stubSandboxInfo();
        }

        @Override
        public void release(SandboxInfo info) {}

        @Override
        public boolean healthCheck(SandboxInfo info) {
            return true;
        }

        @Override
        public void writeFile(SandboxInfo info, String relativePath, String content)
                throws IOException {
            fileStore.put(relativePath, content);
        }

        @Override
        public String readFile(SandboxInfo info, String relativePath) throws IOException {
            String content = fileStore.get(relativePath);
            if (content == null) {
                throw new IOException("文件不存在: " + relativePath);
            }
            return content;
        }

        @Override
        public RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config) {
            return new StubRuntimeAdapter();
        }

        @Override
        public URI getSidecarUri(SandboxInfo info, String command, String args) {
            return URI.create("ws://localhost:8080/?command=" + command);
        }

        public Map<String, String> getFileStore() {
            return fileStore;
        }
    }

    /**
     * 可控制失败次数的 SandboxProvider，用于测试重试幂等性。 前 N 次 writeFile 抛出 IOException，之后正常写入。
     */
    static class FailNTimesSandboxProvider implements SandboxProvider {

        private final Map<String, String> fileStore = new ConcurrentHashMap<>();
        private final Map<String, Integer> writeAttempts = new ConcurrentHashMap<>();
        private final int failCount;

        FailNTimesSandboxProvider(int failCount) {
            this.failCount = failCount;
        }

        @Override
        public SandboxType getType() {
            return SandboxType.LOCAL;
        }

        @Override
        public SandboxInfo acquire(SandboxConfig config) {
            return stubSandboxInfo();
        }

        @Override
        public void release(SandboxInfo info) {}

        @Override
        public boolean healthCheck(SandboxInfo info) {
            return true;
        }

        @Override
        public void writeFile(SandboxInfo info, String relativePath, String content)
                throws IOException {
            int attempt = writeAttempts.compute(relativePath, (k, v) -> v == null ? 1 : v + 1);
            if (attempt <= failCount) {
                throw new IOException("模拟写入失败: 第 " + attempt + " 次");
            }
            fileStore.put(relativePath, content);
        }

        @Override
        public String readFile(SandboxInfo info, String relativePath) throws IOException {
            String content = fileStore.get(relativePath);
            if (content == null) {
                throw new IOException("文件不存在: " + relativePath);
            }
            return content;
        }

        @Override
        public RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config) {
            return new StubRuntimeAdapter();
        }

        @Override
        public URI getSidecarUri(SandboxInfo info, String command, String args) {
            return URI.create("ws://localhost:8080/?command=" + command);
        }

        public Map<String, String> getFileStore() {
            return fileStore;
        }
    }

    /** Stub RuntimeAdapter */
    static class StubRuntimeAdapter implements RuntimeAdapter {
        @Override
        public RuntimeType getType() {
            return RuntimeType.LOCAL;
        }

        @Override
        public String start(RuntimeConfig config) {
            return "stub";
        }

        @Override
        public void send(String jsonLine) {}

        @Override
        public Flux<String> stdout() {
            return Flux.empty();
        }

        @Override
        public RuntimeStatus getStatus() {
            return RuntimeStatus.RUNNING;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public void close() {}

        @Override
        public FileSystemAdapter getFileSystem() {
            return null;
        }
    }

    // ===== 辅助方法 =====

    private static SandboxInfo stubSandboxInfo() {
        return new SandboxInfo(
                SandboxType.LOCAL, "local-8080", "localhost", 8080, "/workspace", false, Map.of());
    }

    private InitContext createContext(SandboxProvider provider, List<ConfigFile> configs) {
        CliProviderConfig providerConfig = new CliProviderConfig();
        providerConfig.setSupportsCustomModel(true);
        CliSessionConfig sessionConfig = new CliSessionConfig();
        InitContext context =
                new InitContext(
                        provider, "test-user", null, null, providerConfig, sessionConfig, null);
        context.setSandboxInfo(stubSandboxInfo());
        context.setInjectedConfigs(configs);
        return context;
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    // ===== 生成器 =====

    @Provide
    Arbitrary<String> configContents() {
        return Arbitraries.oneOf(
                // 普通 JSON 配置
                Arbitraries.strings().ofMinLength(1).ofMaxLength(500).ascii(),
                // 含 Unicode 字符
                Arbitraries.strings()
                        .ofMinLength(1)
                        .ofMaxLength(200)
                        .withChars('中', '文', '日', '本', '語', '한', '국', '어', '🚀', '✅', '❌'),
                // 含特殊字符
                Arbitraries.of(
                        "{\"key\": \"value with \\\"quotes\\\"\"}",
                        "{\"path\": \"C:\\\\Users\\\\test\"}",
                        "line1\nline2\nline3",
                        "tab\there\ttoo",
                        "{\"emoji\": \"🎉🔥💻\"}",
                        "空格 和\t制表符\n换行符",
                        "{\"unicode\": \"日本語テスト\"}",
                        "content with null char: \0 end",
                        "{\"special\": \"<>&'\\\"\"}"),
                // 较大内容
                Arbitraries.strings().ofMinLength(1000).ofMaxLength(5000).ascii());
    }

    @Provide
    Arbitrary<String> relativePaths() {
        return Arbitraries.of(
                "settings.json",
                ".kiro/mcp-config.json",
                ".kiro/skills/skill.md",
                "config/model.json",
                "deep/nested/path/config.yaml");
    }

    @Provide
    Arbitrary<ConfigFile.ConfigType> configTypes() {
        return Arbitraries.of(ConfigFile.ConfigType.values());
    }

    @Provide
    Arbitrary<List<ConfigFile>> configFileLists() {
        return Combinators.combine(relativePaths(), configContents(), configTypes())
                .as((path, content, type) -> new ConfigFile(path, content, sha256(content), type))
                .list()
                .ofMinSize(1)
                .ofMaxSize(5)
                .filter(
                        list -> {
                            // 确保路径不重复
                            long distinctPaths =
                                    list.stream().map(ConfigFile::relativePath).distinct().count();
                            return distinctPaths == list.size();
                        });
    }

    // =========================================================================
    // Property 2: 配置写入往返一致性
    // =========================================================================

    /**
     * <b>Validates: Requirements 5.4, 5.6, 8.2</b>
     *
     * <p>Property 2: 配置写入往返一致性 — 对任意合法配置内容（含特殊字符、Unicode）， writeFile 后 readFile
     * 读回内容 SHA-256 哈希相等。
     */
    @Property(tries = 200)
    void configInjection_writeAndReadBack_sha256HashesMatch(
            @ForAll("configContents") String content,
            @ForAll("relativePaths") String path,
            @ForAll("configTypes") ConfigFile.ConfigType type) {
        InMemorySandboxProvider provider = new InMemorySandboxProvider();
        ConfigFile configFile = new ConfigFile(path, content, sha256(content), type);
        InitContext context = createContext(provider, List.of(configFile));

        ConfigInjectionPhase phase = new ConfigInjectionPhase();
        // execute 内部会 writeFile 然后 readFile 并比较 SHA-256
        assertDoesNotThrow(() -> phase.execute(context), "配置注入应成功完成，写入和读回的 SHA-256 哈希应一致");

        // 额外验证：手动读回并比较哈希
        String readBack = provider.getFileStore().get(path);
        assertNotNull(readBack, "文件应已写入存储");
        assertEquals(sha256(content), sha256(readBack), "写入内容和读回内容的 SHA-256 哈希应相等");
        assertEquals(content, readBack, "写入内容和读回内容应完全一致");
    }

    /**
     * <b>Validates: Requirements 5.4, 5.6, 8.2</b>
     *
     * <p>Property 2 补充: 多个配置文件同时注入时，每个文件的写入往返一致性都成立。
     */
    @Property(tries = 100)
    void configInjection_multipleFiles_allHashesMatch(
            @ForAll("configFileLists") List<ConfigFile> configs) {
        InMemorySandboxProvider provider = new InMemorySandboxProvider();
        InitContext context = createContext(provider, configs);

        ConfigInjectionPhase phase = new ConfigInjectionPhase();
        assertDoesNotThrow(() -> phase.execute(context), "多文件配置注入应成功完成");

        // 验证每个文件的写入往返一致性
        for (ConfigFile config : configs) {
            String readBack = provider.getFileStore().get(config.relativePath());
            assertNotNull(readBack, "文件 " + config.relativePath() + " 应已写入存储");
            assertEquals(
                    sha256(config.content()),
                    sha256(readBack),
                    "文件 " + config.relativePath() + " 的 SHA-256 哈希应一致");
        }
    }

    // =========================================================================
    // Property 5: 重试幂等性
    // =========================================================================

    /**
     * <b>Validates: Requirements 4.4, 8.3</b>
     *
     * <p>Property 5: 重试幂等性 — 对支持重试的阶段，重试执行的最终效果与首次成功执行一致， 多次 writeFile
     * 同一文件不产生重复内容。
     */
    @Property(tries = 100)
    void configInjection_multipleWritesToSameFile_producesIdenticalResult(
            @ForAll("configContents") String content,
            @ForAll("relativePaths") String path,
            @ForAll("configTypes") ConfigFile.ConfigType type,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 2, max = 5) int writeCount) {
        InMemorySandboxProvider provider = new InMemorySandboxProvider();
        ConfigFile configFile = new ConfigFile(path, content, sha256(content), type);

        ConfigInjectionPhase phase = new ConfigInjectionPhase();

        // 多次执行 execute，模拟重试场景
        for (int i = 0; i < writeCount; i++) {
            InitContext context = createContext(provider, List.of(configFile));
            assertDoesNotThrow(() -> phase.execute(context), "第 " + (i + 1) + " 次执行应成功");
        }

        // 验证最终文件内容与首次写入一致（幂等性）
        String finalContent = provider.getFileStore().get(path);
        assertNotNull(finalContent, "文件应存在于存储中");
        assertEquals(content, finalContent, "多次写入后文件内容应与原始内容一致");
        assertEquals(sha256(content), sha256(finalContent), "多次写入后 SHA-256 哈希应与原始内容一致");
    }

    /**
     * <b>Validates: Requirements 4.4, 8.3</b>
     *
     * <p>Property 5 补充: 当 writeFile 前几次失败后成功时，最终文件内容与直接成功写入一致。
     */
    @Property(tries = 50)
    void configInjection_afterRetrySuccess_fileContentMatchesDirectWrite(
            @ForAll("configContents") String content,
            @ForAll("relativePaths") String path,
            @ForAll("configTypes") ConfigFile.ConfigType type) {
        // 场景 1：直接成功写入
        InMemorySandboxProvider directProvider = new InMemorySandboxProvider();
        ConfigFile configFile = new ConfigFile(path, content, sha256(content), type);
        InitContext directContext = createContext(directProvider, List.of(configFile));

        ConfigInjectionPhase phase = new ConfigInjectionPhase();
        assertDoesNotThrow(() -> phase.execute(directContext));
        String directContent = directProvider.getFileStore().get(path);

        // 场景 2：前 1 次失败，第 2 次成功（模拟重试后成功）
        FailNTimesSandboxProvider retryProvider = new FailNTimesSandboxProvider(1);
        ConfigFile configFile2 = new ConfigFile(path, content, sha256(content), type);
        InitContext retryContext = createContext(retryProvider, List.of(configFile2));

        // 第一次执行会因 writeFile 失败而抛出异常
        assertThrows(
                InitPhaseException.class,
                () -> phase.execute(retryContext),
                "第一次执行应因 writeFile 失败而抛出异常");

        // 第二次执行应成功（failCount=1，第二次 writeFile 成功）
        InitContext retryContext2 = createContext(retryProvider, List.of(configFile2));
        assertDoesNotThrow(() -> phase.execute(retryContext2), "重试后执行应成功");

        String retryContent = retryProvider.getFileStore().get(path);

        // 验证两种场景的最终结果一致
        assertNotNull(retryContent, "重试后文件应存在");
        assertEquals(directContent, retryContent, "重试成功后的文件内容应与直接成功写入的内容一致");
        assertEquals(sha256(directContent), sha256(retryContent), "重试成功后的 SHA-256 哈希应与直接成功写入一致");
    }
}
