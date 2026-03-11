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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 集成测试：验证 CliProcess 能通过 ACP 协议成功连接多种 CLI 工具。
 *
 * <p>测试逻辑： 1. 启动 CLI 子进程（ACP 模式） 2. 发送 initialize 请求（JSON-RPC 2.0） 3.
 * 验证收到包含 protocolVersion 和 agentCapabilities 的响应 4. 优雅关闭进程
 *
 * <p>仅测试本机已安装的 CLI 工具，未安装的自动跳过。 npx 类型的 provider 首次运行需要下载包，超时设为 90 秒。
 */
class CliProcessMultiCliTest {

    private static final Logger log = LoggerFactory.getLogger(CliProcessMultiCliTest.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /** 本地 CLI 超时（秒） */
    private static final int LOCAL_TIMEOUT = 30;

    /** npx 类型 CLI 超时（秒），首次需要下载包 */
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

    @ParameterizedTest(name = "ACP initialize: {0}")
    @MethodSource("cliProviders")
    void testAcpInitialize(CliDef cli) throws Exception {
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
            log.info(
                    "启动 {} (command={} {}, timeout={}s)",
                    cli.key(),
                    cli.command(),
                    cli.args(),
                    cli.timeout());
            process.start();
            assertTrue(process.isAlive(), cli.key() + " 进程应该处于运行状态");

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> responseRef = new AtomicReference<>();
            AtomicReference<String> errorRef = new AtomicReference<>();

            // 订阅 stdout
            process.stdout()
                    .subscribe(
                            line -> {
                                log.info("[{}] STDOUT: {}", cli.key(), line);
                                if (line.contains("\"result\"") && line.contains("\"id\"")) {
                                    responseRef.set(line);
                                    latch.countDown();
                                } else if (line.contains("\"error\"")) {
                                    errorRef.set(line);
                                    latch.countDown();
                                }
                            });

            // 订阅 stderr 用于调试
            process.stderr().subscribe(line -> log.debug("[{}] STDERR: {}", cli.key(), line));

            // 构建 ACP initialize 请求
            String initRequest = buildInitializeRequest();
            log.info("[{}] 发送 initialize 请求", cli.key());
            process.send(initRequest);

            // 等待响应
            boolean received = latch.await(cli.timeout(), TimeUnit.SECONDS);

            // 如果进程已退出，给出更有用的错误信息
            if (!received && !process.isAlive()) {
                fail(cli.key() + " 进程已意外退出，未返回 initialize 响应");
            }
            assertTrue(received, cli.key() + " 应在 " + cli.timeout() + " 秒内返回 initialize 响应");

            // 检查是否收到错误响应
            if (errorRef.get() != null && responseRef.get() == null) {
                fail(cli.key() + " 返回了错误响应: " + errorRef.get());
            }

            // 解析并验证响应
            String response = responseRef.get();
            assertNotNull(response, cli.key() + " 响应不应为 null");
            log.info(
                    "[{}] 收到响应: {}",
                    cli.key(),
                    response.length() > 500 ? response.substring(0, 500) + "..." : response);

            JsonNode root = mapper.readTree(response);
            assertEquals("2.0", root.get("jsonrpc").asText(), cli.key() + " 响应应为 JSON-RPC 2.0");
            assertEquals(0, root.get("id").asInt(), cli.key() + " 响应 id 应匹配请求 id");

            JsonNode result = root.get("result");
            assertNotNull(result, cli.key() + " 响应应包含 result 字段");
            assertTrue(result.has("protocolVersion"), cli.key() + " result 应包含 protocolVersion");
            assertTrue(
                    result.has("agentCapabilities") || result.has("agentInfo"),
                    cli.key() + " result 应包含 agentCapabilities 或 agentInfo");

            log.info(
                    "[{}] ✅ ACP initialize 握手成功! protocolVersion={}",
                    cli.key(),
                    result.get("protocolVersion"));

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
