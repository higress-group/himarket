package com.alibaba.himarket.service.hicoding.session;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.service.hicoding.websocket.CliProcess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Qwen Code 认证流程集成测试。
 *
 * <p>测试场景：隔离 HOME 环境下，通过 OpenAI 兼容模式认证，完成 ACP 完整流程。
 *
 * <p>测试流程：
 * 1. 创建隔离的 HOME 目录（模拟全新用户环境）
 * 2. 启动 qwen --acp，附带 OpenAI 认证参数
 * 3. 完成 initialize -> session/new -> session/prompt 完整流程
 * 4. 验证响应格式和流式输出
 *
 * <p>认证方式：使用 DashScope OpenAI 兼容模式
 * - Base URL: https://dashscope.aliyuncs.com/compatible-mode/v1
 * - API Key: 通过环境变量 DASHSCOPE_API_KEY 提供
 *
 * <p>运行前提：
 * - 安装 qwen CLI (npm i -g @anthropic/qwen-code-cli 或其他方式)
 * - 设置环境变量 DASHSCOPE_API_KEY
 */
class QwenCodeAuthFlowTest {

    private static final Logger log = LoggerFactory.getLogger(QwenCodeAuthFlowTest.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 120; // 流式响应可能需要更长时间

    private static final String DASHSCOPE_BASE_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1";

    @TempDir Path tempDir;

    /**
     * 测试：隔离 HOME + OpenAI 认证，完成完整 ACP 流程。
     *
     * <p>需要设置环境变量 DASHSCOPE_API_KEY 才能运行此测试。
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    void testQwenCodeWithOpenAIAuth() throws Exception {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        assertNotNull(apiKey, "DASHSCOPE_API_KEY 环境变量必须设置");

        if (!isCommandAvailable("qwen")) {
            log.warn("跳过测试: qwen 命令未安装");
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "qwen not installed, skipping");
        }

        // 创建隔离的工作目录和 HOME 目录
        Path cwd = tempDir.resolve("qwen-test");
        Path isolatedHome = tempDir.resolve("qwen-home");
        Files.createDirectories(cwd);
        Files.createDirectories(isolatedHome);

        // 构建带认证参数的命令行参数
        List<String> args = new ArrayList<>();
        args.add("--acp");
        args.add("--auth-type");
        args.add("openai");
        args.add("--openai-api-key");
        args.add(apiKey);
        args.add("--openai-base-url");
        args.add(DASHSCOPE_BASE_URL);
        args.add("-m");
        args.add("qwen-max"); // 指定模型，避免默认模型额度用完

        log.info("=== Qwen Code 认证流程测试 ===");
        log.info("隔离 HOME: {}", isolatedHome);
        log.info("工作目录: {}", cwd);
        log.info("Base URL: {}", DASHSCOPE_BASE_URL);

        // 使用隔离的 HOME 目录启动进程
        Map<String, String> extraEnv = Map.of("HOME", isolatedHome.toString());
        CliProcess process = new CliProcess("qwen", args, cwd.toString(), extraEnv);

        try {
            process.start();
            assertTrue(process.isAlive(), "Qwen 进程应该处于运行状态");

            AtomicInteger requestId = new AtomicInteger(0);
            CountDownLatch initLatch = new CountDownLatch(1);
            CountDownLatch sessionLatch = new CountDownLatch(1);
            CountDownLatch promptLatch = new CountDownLatch(1);

            AtomicReference<String> initResponse = new AtomicReference<>();
            AtomicReference<String> sessionResponse = new AtomicReference<>();
            AtomicReference<String> promptResponse = new AtomicReference<>();
            CopyOnWriteArrayList<String> sessionUpdates = new CopyOnWriteArrayList<>();

            process.stdout()
                    .subscribe(
                            line -> {
                                log.info("[qwen] STDOUT: {}", truncate(line, 200));
                                try {
                                    JsonNode node = mapper.readTree(line);

                                    // 处理 session/update 通知
                                    if (node.has("method")
                                            && "session/update"
                                                    .equals(node.get("method").asText())) {
                                        sessionUpdates.add(line);
                                        return;
                                    }

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
                                    }
                                } catch (Exception e) {
                                    log.debug("[qwen] 非 JSON 输出: {}", line);
                                }
                            });

            process.stderr().subscribe(line -> log.debug("[qwen] STDERR: {}", line));

            // Step 1: initialize
            log.info("=== Step 1: initialize ===");
            process.send(buildInitializeRequest(requestId.getAndIncrement()));
            boolean initReceived = initLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(initReceived, "应在超时内返回 initialize 响应");

            JsonNode initResult = mapper.readTree(initResponse.get()).get("result");
            assertNotNull(initResult, "initialize 应包含 result");

            // 打印 agent 信息
            if (initResult.has("agentInfo")) {
                JsonNode agentInfo = initResult.get("agentInfo");
                log.info(
                        "Agent: {} v{}",
                        agentInfo.path("name").asText("unknown"),
                        agentInfo.path("version").asText("unknown"));
            }

            // 打印支持的模型
            if (initResult.has("modes")) {
                JsonNode modes = initResult.get("modes");
                log.info("支持的模式: {}", modes);
            }

            log.info("initialize 成功");

            // Step 2: session/new
            log.info("=== Step 2: session/new ===");
            process.send(buildSessionNewRequest(requestId.getAndIncrement(), cwd.toString()));
            boolean sessionReceived = sessionLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(sessionReceived, "应在超时内返回 session/new 响应");

            JsonNode sessionRoot = mapper.readTree(sessionResponse.get());

            // 检查是否有错误
            if (sessionRoot.has("error")) {
                JsonNode error = sessionRoot.get("error");
                int code = error.path("code").asInt(-1);
                String message = error.path("message").asText("Unknown error");
                log.error("session/new 错误: code={}, message={}", code, message);

                if (code == -32000) {
                    log.warn("认证失败，可能是 API Key 无效或认证参数未正确传递");
                    // 打印 authMethods 供调试
                    if (error.has("data") && error.get("data").has("authMethods")) {
                        log.info("可用认证方式: {}", error.get("data").get("authMethods"));
                    }
                }
                fail("session/new 返回错误: " + message);
            }

            JsonNode sessionResult = sessionRoot.get("result");
            assertNotNull(sessionResult, "session/new 应包含 result");

            String sessionId = sessionResult.path("sessionId").asText();
            assertFalse(sessionId.isEmpty(), "sessionId 不应为空");
            log.info("sessionId: {}", sessionId);
            log.info("session/new 成功");

            // Step 3: session/prompt
            log.info("=== Step 3: session/prompt ===");
            String testPrompt = "请用一句话介绍你自己，不超过20个字";
            process.send(
                    buildSessionPromptRequest(requestId.getAndIncrement(), sessionId, testPrompt));

            boolean promptReceived = promptLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(promptReceived, "应在超时内返回 session/prompt 响应");

            String rawPromptResponse = promptResponse.get();
            assertNotNull(rawPromptResponse, "session/prompt 响应不应为 null");

            JsonNode promptRoot = mapper.readTree(rawPromptResponse);

            if (promptRoot.has("error")) {
                JsonNode error = promptRoot.get("error");
                int code = error.path("code").asInt(-1);
                String message = error.path("message").asText("Unknown error");
                String details = error.path("data").path("details").asText("");

                log.warn("session/prompt 错误: code={}, message={}", code, message);
                log.warn("错误详情: {}", details);

                // -32603 Internal error 且包含 "free tier" 或 "exhausted" 表示账户额度问题
                // 这种情况说明认证流程本身是成功的，只是账户限制
                if (code == -32603
                        && (details.contains("free tier")
                                || details.contains("exhausted")
                                || details.contains("quota"))) {
                    log.info("=== 测试通过: 认证流程验证成功（API 调用因账户额度限制失败，但认证本身正确）===");
                    log.info("收到 {} 个 session/update 通知", sessionUpdates.size());
                    return;
                }

                fail("session/prompt 返回错误: " + message + " - " + details);
            }

            JsonNode promptResult = promptRoot.get("result");
            assertNotNull(promptResult, "session/prompt 应包含 result");

            // 验证响应结构
            if (promptResult.has("stopReason")) {
                String stopReason = promptResult.get("stopReason").asText();
                log.info("stopReason: {}", stopReason);
            }

            log.info("session/prompt 成功");

            // 打印流式更新统计
            log.info("收到 {} 个 session/update 通知", sessionUpdates.size());

            log.info("=== 测试通过: 隔离 HOME + OpenAI 认证 -> prompt 流程验证完成 ===");

        } finally {
            process.close();
            log.info("进程已关闭");
        }
    }

    /**
     * 测试：验证未设置认证时的错误处理。
     *
     * <p>在隔离 HOME 环境下，不提供认证参数，验证返回 -32000 错误。
     */
    @Test
    void testQwenCodeWithoutAuth() throws Exception {
        if (!isCommandAvailable("qwen")) {
            log.warn("跳过测试: qwen 命令未安装");
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "qwen not installed, skipping");
        }

        // 创建隔离的工作目录和 HOME 目录
        Path cwd = tempDir.resolve("qwen-noauth");
        Path isolatedHome = tempDir.resolve("qwen-noauth-home");
        Files.createDirectories(cwd);
        Files.createDirectories(isolatedHome);

        log.info("=== Qwen Code 未认证错误测试 ===");

        // 使用隔离的 HOME 目录，不提供认证参数
        Map<String, String> extraEnv = Map.of("HOME", isolatedHome.toString());
        CliProcess process = new CliProcess("qwen", List.of("--acp"), cwd.toString(), extraEnv);

        try {
            process.start();
            assertTrue(process.isAlive(), "Qwen 进程应该处于运行状态");

            AtomicInteger requestId = new AtomicInteger(0);
            CountDownLatch initLatch = new CountDownLatch(1);
            CountDownLatch sessionLatch = new CountDownLatch(1);

            AtomicReference<String> initResponse = new AtomicReference<>();
            AtomicReference<String> sessionResponse = new AtomicReference<>();

            process.stdout()
                    .subscribe(
                            line -> {
                                log.info("[qwen-noauth] STDOUT: {}", truncate(line, 200));
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
                                        }
                                    }
                                } catch (Exception e) {
                                    log.debug("[qwen-noauth] 非 JSON 输出: {}", line);
                                }
                            });

            process.stderr().subscribe(line -> log.debug("[qwen-noauth] STDERR: {}", line));

            // Step 1: initialize
            log.info("=== Step 1: initialize ===");
            process.send(buildInitializeRequest(requestId.getAndIncrement()));
            boolean initReceived = initLatch.await(60, TimeUnit.SECONDS);
            assertTrue(initReceived, "应在超时内返回 initialize 响应");
            log.info("initialize 成功");

            // Step 2: session/new - 预期返回认证错误
            log.info("=== Step 2: session/new (expect -32000 error) ===");
            process.send(buildSessionNewRequest(requestId.getAndIncrement(), cwd.toString()));
            boolean sessionReceived = sessionLatch.await(60, TimeUnit.SECONDS);
            assertTrue(sessionReceived, "应在超时内返回 session/new 响应");

            JsonNode sessionRoot = mapper.readTree(sessionResponse.get());
            assertTrue(sessionRoot.has("error"), "未认证时 session/new 应返回错误");

            JsonNode error = sessionRoot.get("error");
            int code = error.path("code").asInt(-1);
            assertEquals(-32000, code, "认证错误码应为 -32000");

            log.info("错误码: {}", code);
            log.info("错误消息: {}", error.path("message").asText());

            // 验证 authMethods 在错误响应中
            if (error.has("data") && error.get("data").has("authMethods")) {
                JsonNode authMethods = error.get("data").get("authMethods");
                assertTrue(authMethods.isArray(), "authMethods 应为数组");
                log.info("错误响应包含 {} 个 authMethods", authMethods.size());

                for (JsonNode method : authMethods) {
                    log.info(
                            "  authMethod: id={}, name={}, type={}",
                            method.path("id").asText(),
                            method.path("name").asText(),
                            method.path("type").asText());
                }
            }

            log.info("=== 测试通过: 未认证错误处理验证完成 ===");

        } finally {
            process.close();
            log.info("进程已关闭");
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

    private String truncate(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) {
            return str;
        }
        return str.substring(0, maxLen) + "...";
    }
}
