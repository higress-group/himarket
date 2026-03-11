package com.alibaba.himarket.service.hicoding.session;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.service.hicoding.websocket.CliProcess;
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
 * 集成测试：验证各 CLI 通过 ACP 协议 session/new 返回的 models 和 modes。
 *
 * <p>依赖真实 CLI 工具，使用 {@code mvn test -Dgroups=integration} 显式启用。
 */
@Tag("integration")
class SessionModelsTest {

    private static final Logger log = LoggerFactory.getLogger(SessionModelsTest.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int LOCAL_TIMEOUT = 30;
    private static final int NPX_TIMEOUT = 90;

    @TempDir Path tempDir;

    record CliDef(String key, String command, List<String> args) {
        @Override
        public String toString() {
            return key;
        }

        boolean isNpx() {
            return "npx".equals(command);
        }

        int timeout() {
            return isNpx() ? NPX_TIMEOUT : LOCAL_TIMEOUT;
        }
    }

    static List<CliDef> cliProviders() {
        return List.of(
                new CliDef("kiro-cli", "kiro-cli", List.of("acp")),
                new CliDef("qwen-code", "qwen", List.of("--acp")));
    }

    @ParameterizedTest(name = "session/new models & modes: {0}")
    @MethodSource("cliProviders")
    void testSessionNewModelsAndModes(CliDef cli) throws Exception {
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

            AtomicInteger expectedId = new AtomicInteger(0);
            CountDownLatch initLatch = new CountDownLatch(1);
            CountDownLatch sessionLatch = new CountDownLatch(1);
            AtomicReference<String> initResponse = new AtomicReference<>();
            AtomicReference<String> sessionResponse = new AtomicReference<>();

            process.stdout()
                    .subscribe(
                            line -> {
                                log.info("[{}] STDOUT: {}", cli.key(), line);
                                try {
                                    JsonNode node = mapper.readTree(line);
                                    if (node.has("id") && node.has("result")) {
                                        int id = node.get("id").asInt();
                                        if (id == 0) {
                                            initResponse.set(line);
                                            initLatch.countDown();
                                        } else if (id == 1) {
                                            sessionResponse.set(line);
                                            sessionLatch.countDown();
                                        }
                                    } else if (node.has("error")) {
                                        log.error("[{}] 错误响应: {}", cli.key(), line);
                                        initLatch.countDown();
                                        sessionLatch.countDown();
                                    }
                                } catch (Exception e) {
                                    // 非 JSON 行，忽略
                                }
                            });

            process.stderr().subscribe(line -> log.debug("[{}] STDERR: {}", cli.key(), line));

            // Step 1: initialize
            log.info("[{}] 发送 initialize 请求", cli.key());
            process.send(buildInitializeRequest());
            boolean initReceived = initLatch.await(cli.timeout(), TimeUnit.SECONDS);
            assertTrue(initReceived, cli.key() + " 应在超时内返回 initialize 响应");
            assertNotNull(initResponse.get(), cli.key() + " initialize 响应不应为 null");

            // Step 2: session/new
            log.info("[{}] 发送 session/new 请求", cli.key());
            process.send(buildSessionNewRequest(cwd.toString()));
            boolean sessionReceived = sessionLatch.await(cli.timeout(), TimeUnit.SECONDS);
            assertTrue(sessionReceived, cli.key() + " 应在超时内返回 session/new 响应");

            String rawSession = sessionResponse.get();
            assertNotNull(rawSession, cli.key() + " session/new 响应不应为 null");

            // 解析 session/new 结果
            JsonNode root = mapper.readTree(rawSession);
            JsonNode result = root.get("result");
            assertNotNull(result, cli.key() + " session/new 应包含 result");

            // sessionId
            assertTrue(result.has("sessionId"), cli.key() + " session/new result 应包含 sessionId");
            String sessionId = result.get("sessionId").asText();
            assertFalse(sessionId.isBlank(), cli.key() + " sessionId 不应为空");
            log.info("[{}] sessionId = {}", cli.key(), sessionId);

            // models
            log.info("[{}] ===== Models =====", cli.key());
            if (result.has("models") && !result.get("models").isNull()) {
                JsonNode models = result.get("models");
                if (models.has("availableModels")) {
                    JsonNode availableModels = models.get("availableModels");
                    assertTrue(availableModels.isArray(), cli.key() + " availableModels 应为数组");
                    log.info("[{}] 可用模型数量: {}", cli.key(), availableModels.size());
                    for (JsonNode m : availableModels) {
                        String modelId = m.has("modelId") ? m.get("modelId").asText() : "N/A";
                        String name = m.has("name") ? m.get("name").asText() : "N/A";
                        log.info("[{}]   model: id={}, name={}", cli.key(), modelId, name);
                    }
                }
                if (models.has("currentModelId")) {
                    log.info("[{}] 当前模型: {}", cli.key(), models.get("currentModelId").asText());
                }
            } else {
                log.info("[{}] 无 models 字段", cli.key());
            }

            // modes
            log.info("[{}] ===== Modes =====", cli.key());
            if (result.has("modes") && !result.get("modes").isNull()) {
                JsonNode modes = result.get("modes");
                if (modes.has("availableModes")) {
                    JsonNode availableModes = modes.get("availableModes");
                    assertTrue(availableModes.isArray(), cli.key() + " availableModes 应为数组");
                    log.info("[{}] 可用模式数量: {}", cli.key(), availableModes.size());
                    for (JsonNode m : availableModes) {
                        String modeId = m.has("id") ? m.get("id").asText() : "N/A";
                        String name = m.has("name") ? m.get("name").asText() : "N/A";
                        String desc = m.has("description") ? m.get("description").asText() : "";
                        log.info(
                                "[{}]   mode: id={}, name={}, desc={}",
                                cli.key(),
                                modeId,
                                name,
                                desc);
                    }
                }
                if (modes.has("currentModeId")) {
                    log.info("[{}] 当前模式: {}", cli.key(), modes.get("currentModeId").asText());
                }
            } else {
                log.info("[{}] 无 modes 字段", cli.key());
            }

            log.info("[{}] ✅ session/new 握手成功!", cli.key());

        } finally {
            process.close();
            log.info("[{}] 进程已关闭", cli.key());
        }
    }

    private String buildInitializeRequest() throws Exception {
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
                        .put("id", 0)
                        .put("method", "initialize");
        rootNode.set("params", paramsNode);
        return mapper.writeValueAsString(rootNode);
    }

    private String buildSessionNewRequest(String cwd) throws Exception {
        var paramsNode = mapper.createObjectNode().put("cwd", cwd);
        paramsNode.set("mcpServers", mapper.createArrayNode());
        var rootNode =
                mapper.createObjectNode()
                        .put("jsonrpc", "2.0")
                        .put("id", 1)
                        .put("method", "session/new");
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
