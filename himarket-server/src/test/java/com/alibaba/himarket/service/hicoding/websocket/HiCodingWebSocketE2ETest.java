package com.alibaba.himarket.service.hicoding.websocket;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 端到端测试：通过 WebSocket 连接后端验证 ACP 握手。
 *
 * <p>依赖运行中的后端服务，使用 {@code mvn test -Dgroups=integration} 显式启用。
 */
@Tag("integration")
class HiCodingWebSocketE2ETest {

    private static final Logger log = LoggerFactory.getLogger(HiCodingWebSocketE2ETest.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String WS_BASE = "ws://localhost:8080/ws/acp";

    record ProviderDef(String key, int timeoutSec) {
        @Override
        public String toString() {
            return key;
        }
    }

    static List<ProviderDef> providers() {
        return List.of(new ProviderDef("qodercli", 15), new ProviderDef("kiro-cli", 15));
    }

    @ParameterizedTest(name = "E2E WebSocket ACP: {0}")
    @MethodSource("providers")
    void testWebSocketAcpInitialize(ProviderDef provider) throws Exception {
        if (!isServerRunning()) {
            Assumptions.assumeTrue(false, "后端服务未在 localhost:8080 运行，跳过 E2E 测试");
        }

        String wsUrl = WS_BASE + "?provider=" + provider.key();
        log.info("[{}] 连接 WebSocket: {}", provider.key(), wsUrl);

        CountDownLatch responseLatch = new CountDownLatch(1);
        AtomicReference<String> responseRef = new AtomicReference<>();

        StandardWebSocketClient wsClient = new StandardWebSocketClient();
        WebSocketSession session =
                wsClient.execute(
                                new TextWebSocketHandler() {
                                    @Override
                                    protected void handleTextMessage(
                                            WebSocketSession s, TextMessage message) {
                                        String payload = message.getPayload();
                                        log.info(
                                                "[{}] 收到消息: {}",
                                                provider.key(),
                                                payload.length() > 300
                                                        ? payload.substring(0, 300) + "..."
                                                        : payload);
                                        if (payload.contains("\"result\"")
                                                && payload.contains("\"id\"")) {
                                            responseRef.set(payload);
                                            responseLatch.countDown();
                                        }
                                    }
                                },
                                new WebSocketHttpHeaders(),
                                URI.create(wsUrl))
                        .get(10, TimeUnit.SECONDS);

        try {
            assertTrue(session.isOpen(), provider.key() + " WebSocket 应已连接");
            log.info("[{}] WebSocket 已连接", provider.key());

            // 发送 ACP initialize 请求
            String initRequest = buildInitializeRequest();
            log.info("[{}] 发送 initialize 请求", provider.key());
            session.sendMessage(new TextMessage(initRequest));

            // 等待响应
            boolean received = responseLatch.await(provider.timeoutSec(), TimeUnit.SECONDS);
            assertTrue(
                    received,
                    provider.key() + " 应在 " + provider.timeoutSec() + " 秒内返回 initialize 响应");

            // 验证响应
            String response = responseRef.get();
            assertNotNull(response, provider.key() + " 响应不应为 null");

            JsonNode root = mapper.readTree(response);
            assertEquals("2.0", root.get("jsonrpc").asText());
            assertEquals(0, root.get("id").asInt());

            JsonNode result = root.get("result");
            assertNotNull(result);
            assertTrue(result.has("protocolVersion"));
            assertEquals(1, result.get("protocolVersion").asInt());

            if (result.has("agentInfo")) {
                log.info(
                        "[{}] ✅ E2E ACP 握手成功! agent={}, version={}",
                        provider.key(),
                        result.get("agentInfo").get("name").asText(),
                        result.get("agentInfo").get("version").asText());
            } else {
                log.info(
                        "[{}] ✅ E2E ACP 握手成功! protocolVersion={}",
                        provider.key(),
                        result.get("protocolVersion"));
            }

        } finally {
            if (session.isOpen()) {
                session.close();
            }
            log.info("[{}] WebSocket 已关闭", provider.key());
        }
    }

    private String buildInitializeRequest() throws Exception {
        var fsNode = mapper.createObjectNode().put("readTextFile", true).put("writeTextFile", true);
        var capNode = mapper.createObjectNode().put("terminal", true);
        capNode.set("fs", fsNode);
        var infoNode =
                mapper.createObjectNode()
                        .put("name", "himarket-e2e-test")
                        .put("title", "HiMarket E2E Test")
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

    @SuppressWarnings("deprecation")
    private boolean isServerRunning() {
        try {
            var conn = new java.net.URL("http://localhost:8080/cli-providers").openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.connect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
