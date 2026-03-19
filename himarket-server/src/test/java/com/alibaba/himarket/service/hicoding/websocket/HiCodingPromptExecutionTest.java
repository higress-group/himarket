package com.alibaba.himarket.service.hicoding.websocket;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 集成测试：验证各 CLI 通过 ACP 协议执行 prompt 的完整流程。
 *
 * <p>依赖真实 CLI 工具，使用 {@code mvn test -Dgroups=integration} 显式启用。
 */
@Tag("integration")
class HiCodingPromptExecutionTest {

    private static final Logger log = LoggerFactory.getLogger(HiCodingPromptExecutionTest.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 60;

    @TempDir Path tempDir;

    record CliDef(String key, String command, List<String> args) {
        @Override
        public String toString() {
            return key;
        }
    }

    static List<CliDef> cliProviders() {
        return List.of(
                new CliDef("kiro-cli", "kiro-cli", List.of("acp")),
                new CliDef("qwen-code", "qwen", List.of("--acp")));
    }

    @ParameterizedTest(name = "ACP prompt execution: {0}")
    @MethodSource("cliProviders")
    void testPromptExecution(CliDef cli) throws Exception {
        if (!isCommandAvailable(cli.command())) {
            log.warn("跳过 {}: 命令 '{}' 未安装", cli.key(), cli.command());
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    false, cli.command() + " not installed, skipping");
        }

        Path cwd = tempDir.resolve(cli.key());
        Files.createDirectories(cwd);

        CliProcess process =
                new CliProcess(cli.command(), cli.args(), cwd.toString(), Collections.emptyMap());

        try {
            process.start();
            assertTrue(process.isAlive(), cli.key() + " 进程应该处于运行状态");

            AtomicInteger requestId = new AtomicInteger(0);
            CountDownLatch initLatch = new CountDownLatch(1);
            CountDownLatch sessionLatch = new CountDownLatch(1);
            CountDownLatch promptLatch = new CountDownLatch(1);

            AtomicReference<String> initResponse = new AtomicReference<>();
            AtomicReference<String> sessionResponse = new AtomicReference<>();
            AtomicReference<String> promptResponse = new AtomicReference<>();
            AtomicReference<String> errorResponse = new AtomicReference<>();

            process.stdout()
                    .subscribe(
                            line -> {
                                log.info("[{}] STDOUT: {}", cli.key(), line);
                                try {
                                    JsonNode node = mapper.readTree(line);
                                    if (node.has("id")) {
                                        int id = node.get("id").asInt();
                                        if (id == 0) {
                                            initResponse.set(line);
                                            initLatch.countDown();
                                        } else if (id == 1) {
                                            sessionResponse.set(line);
                                            sessionLatch.countDown();
                                        } else if (id == 2) {
                                            if (node.has("result") || node.has("error")) {
                                                promptResponse.set(line);
                                                promptLatch.countDown();
                                            }
                                        }
                                    } else if (node.has("error")) {
                                        log.error("[{}] 错误响应: {}", cli.key(), line);
                                        errorResponse.set(line);
                                        initLatch.countDown();
                                        sessionLatch.countDown();
                                        promptLatch.countDown();
                                    }
                                } catch (Exception e) {
                                    // 非 JSON 行，可能是流式输出，记录但不处理
                                    log.debug("[{}] 非 JSON 输出: {}", cli.key(), line);
                                }
                            });

            process.stderr().subscribe(line -> log.debug("[{}] STDERR: {}", cli.key(), line));

            // Step 1: initialize
            log.info("[{}] === Step 1: initialize ===", cli.key());
            process.send(buildInitializeRequest(requestId.getAndIncrement()));
            boolean initReceived = initLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(initReceived, cli.key() + " 应在超时内返回 initialize 响应");
            assertNotNull(initResponse.get(), cli.key() + " initialize 响应不应为 null");
            log.info("[{}] ✅ initialize 成功", cli.key());

            // Step 2: session/new
            log.info("[{}] === Step 2: session/new ===", cli.key());
            process.send(buildSessionNewRequest(requestId.getAndIncrement(), cwd.toString()));
            boolean sessionReceived = sessionLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(sessionReceived, cli.key() + " 应在超时内返回 session/new 响应");
            assertNotNull(sessionResponse.get(), cli.key() + " session/new 响应不应为 null");

            // 解析 sessionId
            JsonNode sessionResult = mapper.readTree(sessionResponse.get()).get("result");
            String sessionId = sessionResult.get("sessionId").asText();
            log.info("[{}] sessionId = {}", cli.key(), sessionId);
            log.info("[{}] ✅ session/new 成功", cli.key());

            // Step 3: session/prompt
            log.info("[{}] === Step 3: session/prompt ===", cli.key());
            String simplePrompt = "Hello, please respond with 'Hi from " + cli.key() + "'";
            process.send(
                    buildSessionPromptRequest(
                            requestId.getAndIncrement(), sessionId, simplePrompt));

            boolean promptReceived = promptLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(promptReceived, cli.key() + " 应在超时内返回 session/prompt 响应");

            // 解析 prompt 响应
            String rawPromptResponse = promptResponse.get();
            assertNotNull(rawPromptResponse, cli.key() + " session/prompt 响应不应为 null");

            JsonNode promptRoot = mapper.readTree(rawPromptResponse);
            log.info("[{}] session/prompt 响应: {}", cli.key(), rawPromptResponse);

            if (promptRoot.has("error")) {
                JsonNode error = promptRoot.get("error");
                int code = error.has("code") ? error.get("code").asInt() : -1;
                String message =
                        error.has("message") ? error.get("message").asText() : "Unknown error";

                // -32000 表示需要认证，这是预期的行为
                if (code == -32000) {
                    log.info("[{}] ⚠️ session/prompt 需要认证 (code=-32000): {}", cli.key(), message);
                    log.info("[{}] ✅ 测试通过 - CLI 正确返回了认证需求", cli.key());
                    return;
                }

                fail(cli.key() + " session/prompt 返回错误: " + message);
            }

            JsonNode promptResult = promptRoot.get("result");
            assertNotNull(promptResult, cli.key() + " session/prompt 应包含 result");

            // 验证响应基本结构 - session/prompt 返回的是 stopReason
            if (promptResult.has("stopReason")) {
                String stopReason = promptResult.get("stopReason").asText();
                log.info("[{}] stopReason: {}", cli.key(), stopReason);
            }

            log.info("[{}] ✅ session/prompt 成功", cli.key());

        } finally {
            process.close();
            log.info("[{}] 进程已关闭", cli.key());
        }
    }

    private String buildInitializeRequest(int id) throws Exception {
        var fsNode = mapper.createObjectNode().put("readTextFile", true).put("writeTextFile", true);
        var capNode = mapper.createObjectNode().put("terminal", true);
        capNode.set("fs", fsNode);
        var infoNode =
                mapper.createObjectNode()
                        .put("name", "himarket-test")
                        .put("title", "HiMarket ACP Test")
                        .put("version", "1.0.0");
        var paramsNode = mapper.createObjectNode().put("protocolVersion", 1);
        paramsNode.set("clientCapabilities", capNode);
        paramsNode.set("clientInfo", infoNode);
        var rootNode =
                mapper.createObjectNode()
                        .put("jsonrpc", "2.0")
                        .put("id", id)
                        .put("method", "initialize");
        rootNode.set("params", paramsNode);
        return mapper.writeValueAsString(rootNode);
    }

    private String buildSessionNewRequest(int id, String cwd) throws Exception {
        var paramsNode = mapper.createObjectNode().put("cwd", cwd);
        paramsNode.set("mcpServers", mapper.createArrayNode());
        var rootNode =
                mapper.createObjectNode()
                        .put("jsonrpc", "2.0")
                        .put("id", id)
                        .put("method", "session/new");
        rootNode.set("params", paramsNode);
        return mapper.writeValueAsString(rootNode);
    }

    private String buildSessionPromptRequest(int id, String sessionId, String text)
            throws Exception {
        // prompt 是 ContentBlock 数组，格式为 [{"type": "text", "text": "..."}]
        var textBlock = mapper.createObjectNode().put("type", "text").put("text", text);
        var promptArray = mapper.createArrayNode().add(textBlock);

        var paramsNode = mapper.createObjectNode();
        paramsNode.put("sessionId", sessionId);
        paramsNode.set("prompt", promptArray);

        var rootNode =
                mapper.createObjectNode()
                        .put("jsonrpc", "2.0")
                        .put("id", id)
                        .put("method", "session/prompt");
        rootNode.set("params", paramsNode);
        return mapper.writeValueAsString(rootNode);
    }

    private boolean isCommandAvailable(String command) {
        try {
            Process p = new ProcessBuilder("which", command).redirectErrorStream(true).start();
            boolean exited = p.waitFor(5, TimeUnit.SECONDS);
            return exited && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
