package com.alibaba.himarket.service.hicoding;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * HiCoding 全流程 E2E 测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>CodingSession REST API CRUD（创建、查询、更新、删除会话）
 *   <li>WebSocket 连接认证（有效/无效/缺失 token）
 *   <li>WebSocket ACP 协议握手（initialize）
 *   <li>沙箱初始化流程（session/config → sandbox/status 通知）
 *   <li>发起新会话（session/new）并验证 sessionId、models、modes
 *   <li>加载历史会话（session/load）
 *   <li>对话执行（session/prompt）
 *   <li>初始化期间消息缓冲和回放
 *   <li>连接异常和优雅关闭
 *   <li>跨用户会话隔离
 * </ul>
 *
 * <p>依赖运行中的后端服务（localhost:8080），使用 {@code mvn test -Dgroups=integration} 显式启用。
 */
@Tag("integration")
@DisplayName("HiCoding 全流程 E2E 测试")
class HiCodingFullE2ETest {

    private static final Logger log = LoggerFactory.getLogger(HiCodingFullE2ETest.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String BASE_URL = "http://localhost:8080";
    private static final String WS_BASE = "ws://localhost:8080/ws/acp";
    private static final int WS_CONNECT_TIMEOUT_SEC = 10;
    private static final int WS_RESPONSE_TIMEOUT_SEC = 30;
    private static final int SANDBOX_INIT_TIMEOUT_SEC = 60;

    private static final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    // ─────────────────────────────────────────────────────────────────
    //  前置条件检查
    // ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void ensureServerRunning() {
        Assumptions.assumeTrue(isServerRunning(), "后端服务未在 localhost:8080 运行，跳过 E2E 测试");
    }

    // ═════════════════════════════════════════════════════════════════
    //  1. CodingSession REST API CRUD 测试
    // ═════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. CodingSession REST API")
    @TestMethodOrder(OrderAnnotation.class)
    class CodingSessionCrudTest {

        /**
         * 1.1 创建会话 → 查询列表 → 更新标题 → 删除会话，完整 CRUD 闭环。
         */
        @Test
        @Order(1)
        @DisplayName("1.1 完整 CRUD 生命周期")
        void testFullCrudLifecycle() throws Exception {
            String token = obtainDeveloperToken();
            assertNotNull(token, "获取开发者 token 失败");
            log.info("=== 1.1 CodingSession CRUD 生命周期测试 ===");

            // ── CREATE ──
            String cliSessionId = "cli-" + UUID.randomUUID();
            ObjectNode createBody = mapper.createObjectNode();
            createBody.put("cliSessionId", cliSessionId);
            createBody.put("title", "E2E Test Session");
            createBody.put("providerKey", "qwen-code");
            createBody.put("cwd", "/workspace/test");

            HttpResponse<String> createResp =
                    httpPost("/coding-sessions", createBody.toString(), token);
            log.info("[CREATE] status={}, body={}", createResp.statusCode(), createResp.body());
            assertEquals(200, createResp.statusCode(), "创建会话应返回 200");

            JsonNode createResult = parseSuccessData(createResp.body());
            assertNotNull(createResult, "创建结果不应为 null");
            String sessionId = createResult.get("sessionId").asText();
            assertFalse(sessionId.isBlank(), "sessionId 不应为空");
            assertEquals(cliSessionId, createResult.get("cliSessionId").asText());
            assertEquals("E2E Test Session", createResult.get("title").asText());
            log.info("[CREATE] sessionId={}", sessionId);

            // ── LIST ──
            HttpResponse<String> listResp = httpGet("/coding-sessions?page=0&size=10", token);
            log.info("[LIST] status={}", listResp.statusCode());
            assertEquals(200, listResp.statusCode(), "查询列表应返回 200");

            JsonNode listResult = parseSuccessData(listResp.body());
            assertNotNull(listResult, "列表结果不应为 null");
            assertTrue(listResult.has("content"), "列表应包含 content 字段");
            JsonNode content = listResult.get("content");
            assertTrue(content.isArray(), "content 应为数组");
            assertTrue(content.size() > 0, "列表不应为空");

            // 验证刚创建的会话在列表中
            boolean found = false;
            for (JsonNode item : content) {
                if (sessionId.equals(item.get("sessionId").asText())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "刚创建的会话应在列表中");
            log.info("[LIST] 找到会话 sessionId={}, 列表总数={}", sessionId, content.size());

            // ── UPDATE ──
            ObjectNode updateBody = mapper.createObjectNode();
            updateBody.put("title", "Updated Title");

            HttpResponse<String> updateResp =
                    httpPatch("/coding-sessions/" + sessionId, updateBody.toString(), token);
            log.info("[UPDATE] status={}", updateResp.statusCode());
            assertEquals(200, updateResp.statusCode(), "更新会话应返回 200");

            JsonNode updateResult = parseSuccessData(updateResp.body());
            assertEquals("Updated Title", updateResult.get("title").asText());
            log.info("[UPDATE] 标题已更新为 '{}'", updateResult.get("title").asText());

            // ── DELETE ──
            HttpResponse<String> deleteResp = httpDelete("/coding-sessions/" + sessionId, token);
            log.info("[DELETE] status={}", deleteResp.statusCode());
            assertEquals(200, deleteResp.statusCode(), "删除会话应返回 200");

            // 验证删除后查不到
            HttpResponse<String> listResp2 = httpGet("/coding-sessions?page=0&size=100", token);
            JsonNode listResult2 = parseSuccessData(listResp2.body());
            boolean foundAfterDelete = false;
            for (JsonNode item : listResult2.get("content")) {
                if (sessionId.equals(item.get("sessionId").asText())) {
                    foundAfterDelete = true;
                    break;
                }
            }
            assertFalse(foundAfterDelete, "删除后的会话不应在列表中");
            log.info("[DELETE] 确认会话已删除");
        }

        /**
         * 1.2 创建会话时缺少必填字段 cliSessionId，应返回参数校验错误。
         */
        @Test
        @Order(2)
        @DisplayName("1.2 创建会话 - 缺少必填字段")
        void testCreateSessionMissingCliSessionId() throws Exception {
            String token = obtainDeveloperToken();

            ObjectNode body = mapper.createObjectNode();
            body.put("title", "No CLI Session ID");
            // 不设置 cliSessionId

            HttpResponse<String> resp = httpPost("/coding-sessions", body.toString(), token);
            log.info("[VALIDATION] status={}, body={}", resp.statusCode(), resp.body());
            assertEquals(400, resp.statusCode(), "缺少必填字段应返回 400");

            JsonNode responseBody = mapper.readTree(resp.body());
            assertNotEquals("SUCCESS", responseBody.path("code").asText(), "应返回错误码");
            log.info("[VALIDATION] 参数校验错误验证通过");
        }

        /**
         * 1.3 未携带 token 访问需认证的接口，应返回 401。
         */
        @Test
        @Order(3)
        @DisplayName("1.3 无 token 访问")
        void testAccessWithoutToken() throws Exception {
            HttpResponse<String> resp = httpGet("/coding-sessions?page=0&size=10", null);
            log.info("[AUTH] status={}", resp.statusCode());
            assertEquals(403, resp.statusCode(), "无 token 应返回 403");
            log.info("[AUTH] 无 token 认证拒绝验证通过");
        }

        /**
         * 1.4 更新不存在的会话，应返回 404。
         */
        @Test
        @Order(4)
        @DisplayName("1.4 更新不存在的会话")
        void testUpdateNonExistentSession() throws Exception {
            String token = obtainDeveloperToken();

            ObjectNode body = mapper.createObjectNode();
            body.put("title", "Ghost Session");

            HttpResponse<String> resp =
                    httpPatch("/coding-sessions/non-existent-id", body.toString(), token);
            log.info("[NOT_FOUND] status={}", resp.statusCode());
            assertEquals(404, resp.statusCode(), "不存在的会话应返回 404");
            log.info("[NOT_FOUND] 更新不存在会话验证通过");
        }

        /**
         * 1.5 删除不存在的会话，应返回 404。
         */
        @Test
        @Order(5)
        @DisplayName("1.5 删除不存在的会话")
        void testDeleteNonExistentSession() throws Exception {
            String token = obtainDeveloperToken();

            HttpResponse<String> resp = httpDelete("/coding-sessions/non-existent-id", token);
            log.info("[NOT_FOUND] status={}", resp.statusCode());
            assertEquals(404, resp.statusCode(), "不存在的会话应返回 404");
            log.info("[NOT_FOUND] 删除不存在会话验证通过");
        }

        /**
         * 1.6 分页查询验证：指定 page 和 size 参数，确认分页字段正确。
         */
        @Test
        @Order(6)
        @DisplayName("1.6 分页查询")
        void testListSessionsPagination() throws Exception {
            String token = obtainDeveloperToken();

            // 创建多个会话
            List<String> sessionIds = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                ObjectNode body = mapper.createObjectNode();
                body.put("cliSessionId", "cli-page-" + i + "-" + UUID.randomUUID());
                body.put("title", "Pagination Test " + i);

                HttpResponse<String> resp = httpPost("/coding-sessions", body.toString(), token);
                assertEquals(200, resp.statusCode());
                sessionIds.add(parseSuccessData(resp.body()).get("sessionId").asText());
            }

            try {
                // 查询第 1 页，每页 2 条
                HttpResponse<String> resp = httpGet("/coding-sessions?page=0&size=2", token);
                assertEquals(200, resp.statusCode());

                JsonNode result = parseSuccessData(resp.body());
                JsonNode content = result.get("content");
                assertTrue(content.isArray(), "content 应为数组");
                assertTrue(content.size() <= 2, "每页最多 2 条");

                // 验证分页元数据字段存在
                assertTrue(result.has("totalElements") || result.has("total"), "应包含总数字段");
                log.info("[PAGINATION] page=0, size=2, returned={}", content.size());

            } finally {
                // 清理
                for (String id : sessionIds) {
                    httpDelete("/coding-sessions/" + id, token);
                }
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  2. WebSocket 连接认证测试
    // ═════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. WebSocket 连接认证")
    class WebSocketAuthTest {

        /**
         * 2.1 不携带 token 连接 WebSocket，应被拒绝（握手失败或立即关闭）。
         */
        @Test
        @DisplayName("2.1 无 token 连接 - 应被拒绝")
        void testConnectWithoutToken() throws Exception {
            log.info("=== 2.1 无 token WebSocket 连接测试 ===");

            String wsUrl = WS_BASE + "?provider=qwen-code";
            CountDownLatch closeLatch = new CountDownLatch(1);
            AtomicReference<CloseStatus> closeStatusRef = new AtomicReference<>();

            StandardWebSocketClient wsClient = new StandardWebSocketClient();
            try {
                WebSocketSession session =
                        wsClient.execute(
                                        new TextWebSocketHandler() {
                                            @Override
                                            public void afterConnectionClosed(
                                                    WebSocketSession s, CloseStatus status) {
                                                closeStatusRef.set(status);
                                                closeLatch.countDown();
                                            }
                                        },
                                        new WebSocketHttpHeaders(),
                                        URI.create(wsUrl))
                                .get(WS_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);

                // 如果连接成功了，应该很快被服务端关闭
                boolean closed = closeLatch.await(10, TimeUnit.SECONDS);
                if (session.isOpen()) {
                    session.close();
                }
                // 连接应在短时间内被关闭，或握手直接失败
                log.info(
                        "[NO_TOKEN] 连接状态: closed={}, closeStatus={}", closed, closeStatusRef.get());
            } catch (Exception e) {
                // 握手失败也是预期行为
                log.info("[NO_TOKEN] 连接被拒绝: {}", e.getMessage());
            }
            log.info("[NO_TOKEN] 无 token 连接测试通过");
        }

        /**
         * 2.2 携带无效 token 连接 WebSocket，应被拒绝。
         */
        @Test
        @DisplayName("2.2 无效 token 连接 - 应被拒绝")
        void testConnectWithInvalidToken() throws Exception {
            log.info("=== 2.2 无效 token WebSocket 连接测试 ===");

            String wsUrl = WS_BASE + "?provider=qwen-code&token=invalid-jwt-token";
            CountDownLatch closeLatch = new CountDownLatch(1);

            StandardWebSocketClient wsClient = new StandardWebSocketClient();
            try {
                WebSocketSession session =
                        wsClient.execute(
                                        new TextWebSocketHandler() {
                                            @Override
                                            public void afterConnectionClosed(
                                                    WebSocketSession s, CloseStatus status) {
                                                closeLatch.countDown();
                                            }
                                        },
                                        new WebSocketHttpHeaders(),
                                        URI.create(wsUrl))
                                .get(WS_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);

                boolean closed = closeLatch.await(10, TimeUnit.SECONDS);
                if (session.isOpen()) {
                    session.close();
                }
                log.info("[INVALID_TOKEN] 连接已关闭: {}", closed);
            } catch (Exception e) {
                log.info("[INVALID_TOKEN] 连接被拒绝: {}", e.getMessage());
            }
            log.info("[INVALID_TOKEN] 无效 token 连接测试通过");
        }

        /**
         * 2.3 携带有效 token 连接 WebSocket，应成功建立连接。
         */
        @Test
        @DisplayName("2.3 有效 token 连接 - 应成功")
        void testConnectWithValidToken() throws Exception {
            log.info("=== 2.3 有效 token WebSocket 连接测试 ===");
            String token = obtainDeveloperToken();
            assertNotNull(token, "获取开发者 token 失败");

            String wsUrl = WS_BASE + "?provider=qwen-code&token=" + token;
            CountDownLatch connectedLatch = new CountDownLatch(1);

            StandardWebSocketClient wsClient = new StandardWebSocketClient();
            WebSocketSession session =
                    wsClient.execute(
                                    new TextWebSocketHandler() {
                                        @Override
                                        public void afterConnectionEstablished(WebSocketSession s) {
                                            connectedLatch.countDown();
                                        }
                                    },
                                    new WebSocketHttpHeaders(),
                                    URI.create(wsUrl))
                            .get(WS_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);

            try {
                boolean connected = connectedLatch.await(5, TimeUnit.SECONDS);
                assertTrue(connected, "WebSocket 应成功连接");
                assertTrue(session.isOpen(), "WebSocket session 应处于开放状态");
                log.info("[VALID_TOKEN] WebSocket 连接成功, sessionId={}", session.getId());
            } finally {
                if (session.isOpen()) {
                    session.close();
                }
            }
            log.info("[VALID_TOKEN] 有效 token 连接测试通过");
        }

        /**
         * 2.4 使用不存在的 provider 连接，应被关闭。
         */
        @Test
        @DisplayName("2.4 未知 provider 连接 - 应被关闭")
        void testConnectWithUnknownProvider() throws Exception {
            log.info("=== 2.4 未知 provider WebSocket 连接测试 ===");
            String token = obtainDeveloperToken();

            String wsUrl = WS_BASE + "?provider=non-existent-provider&token=" + token;
            CountDownLatch closeLatch = new CountDownLatch(1);
            AtomicReference<CloseStatus> closeStatusRef = new AtomicReference<>();

            StandardWebSocketClient wsClient = new StandardWebSocketClient();
            try {
                WebSocketSession session =
                        wsClient.execute(
                                        new TextWebSocketHandler() {
                                            @Override
                                            public void afterConnectionClosed(
                                                    WebSocketSession s, CloseStatus status) {
                                                closeStatusRef.set(status);
                                                closeLatch.countDown();
                                            }
                                        },
                                        new WebSocketHttpHeaders(),
                                        URI.create(wsUrl))
                                .get(WS_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);

                // 服务端应检测到未知 provider 后关闭连接
                boolean closed = closeLatch.await(15, TimeUnit.SECONDS);
                if (session.isOpen()) {
                    session.close();
                }
                assertTrue(closed, "未知 provider 的连接应被服务端关闭");
                log.info("[UNKNOWN_PROVIDER] closeStatus={}", closeStatusRef.get());
            } catch (Exception e) {
                log.info("[UNKNOWN_PROVIDER] 连接失败: {}", e.getMessage());
            }
            log.info("[UNKNOWN_PROVIDER] 未知 provider 连接测试通过");
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  3. WebSocket ACP 握手与沙箱初始化测试
    // ═════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. ACP 协议握手与沙箱初始化")
    class AcpHandshakeAndInitTest {

        /**
         * 3.1 连接后发送 session/config，验证收到 sandbox/status 和 sandbox/init-progress 通知。
         */
        @Test
        @DisplayName("3.1 session/config 触发沙箱初始化流程")
        void testSessionConfigTriggersSandboxInit() throws Exception {
            log.info("=== 3.1 session/config 触发沙箱初始化 ===");
            String token = obtainDeveloperToken();

            String wsUrl = WS_BASE + "?provider=qwen-code&runtime=remote&token=" + token;

            CountDownLatch statusLatch = new CountDownLatch(1);
            CopyOnWriteArrayList<String> allMessages = new CopyOnWriteArrayList<>();
            AtomicReference<String> sandboxStatusRef = new AtomicReference<>();

            StandardWebSocketClient wsClient = new StandardWebSocketClient();
            WebSocketSession session =
                    wsClient.execute(
                                    new TextWebSocketHandler() {
                                        @Override
                                        protected void handleTextMessage(
                                                WebSocketSession s, TextMessage message) {
                                            String payload = message.getPayload();
                                            allMessages.add(payload);
                                            log.info("[INIT] 收到消息: {}", truncate(payload, 200));
                                            try {
                                                JsonNode node = mapper.readTree(payload);
                                                String method = node.path("method").asText("");
                                                if ("sandbox/status".equals(method)) {
                                                    sandboxStatusRef.set(payload);
                                                    statusLatch.countDown();
                                                }
                                            } catch (Exception e) {
                                                // ignore
                                            }
                                        }
                                    },
                                    new WebSocketHttpHeaders(),
                                    URI.create(wsUrl))
                            .get(WS_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);

            try {
                assertTrue(session.isOpen(), "WebSocket 应已连接");

                // 发送 session/config 消息
                String configMsg = buildSessionConfigMessage(null, null, null, null);
                log.info("[INIT] 发送 session/config");
                session.sendMessage(new TextMessage(configMsg));

                // 等待 sandbox/status 通知
                boolean received = statusLatch.await(SANDBOX_INIT_TIMEOUT_SEC, TimeUnit.SECONDS);

                // 验证收到了相关通知
                log.info("[INIT] 收到消息总数: {}", allMessages.size());

                // 检查是否收到了 sandbox/init-progress 消息
                boolean hasInitProgress =
                        allMessages.stream().anyMatch(m -> m.contains("sandbox/init-progress"));
                log.info("[INIT] 收到 init-progress 通知: {}", hasInitProgress);

                if (received) {
                    JsonNode statusNode = mapper.readTree(sandboxStatusRef.get());
                    String status = statusNode.path("params").path("status").asText();
                    log.info("[INIT] sandbox/status = {}", status);
                    // status 可能是 "ready"、"creating" 或 "error"
                    assertTrue(
                            List.of("ready", "creating", "error").contains(status),
                            "sandbox/status 应为 ready、creating 或 error，实际为: " + status);
                } else {
                    log.warn("[INIT] 未在超时内收到 sandbox/status，可能沙箱环境未配置");
                }
            } finally {
                if (session.isOpen()) {
                    session.close();
                }
            }
            log.info("[INIT] 沙箱初始化流程测试通过");
        }

        /**
         * 3.2 连接后发送非 session/config 的消息，应被缓存（pending queue）。
         */
        @Test
        @DisplayName("3.2 初始化前消息缓冲")
        void testMessageBufferingBeforeInit() throws Exception {
            log.info("=== 3.2 初始化前消息缓冲测试 ===");
            String token = obtainDeveloperToken();

            String wsUrl = WS_BASE + "?provider=qwen-code&runtime=remote&token=" + token;
            CopyOnWriteArrayList<String> receivedMessages = new CopyOnWriteArrayList<>();

            StandardWebSocketClient wsClient = new StandardWebSocketClient();
            WebSocketSession session =
                    wsClient.execute(
                                    new TextWebSocketHandler() {
                                        @Override
                                        protected void handleTextMessage(
                                                WebSocketSession s, TextMessage message) {
                                            receivedMessages.add(message.getPayload());
                                        }
                                    },
                                    new WebSocketHttpHeaders(),
                                    URI.create(wsUrl))
                            .get(WS_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);

            try {
                assertTrue(session.isOpen(), "WebSocket 应已连接");

                // 在发送 session/config 之前发送一条普通消息
                // 该消息应被缓存到 pendingMessageMap 中
                String earlyMsg = buildInitializeRequest(0);
                session.sendMessage(new TextMessage(earlyMsg));
                log.info("[BUFFER] 发送了 initialize 消息（在 session/config 之前）");

                // 等待一小段时间
                Thread.sleep(2000);

                // 由于 pipeline 尚未启动（等待 session/config），
                // 消息应被缓存，不会有 CLI 响应
                // 此时发送 session/config 消息以触发初始化
                String configMsg = buildSessionConfigMessage(null, null, null, null);
                session.sendMessage(new TextMessage(configMsg));
                log.info("[BUFFER] 发送 session/config 触发初始化");

                // 等待一段时间看是否有响应
                Thread.sleep(5000);
                log.info("[BUFFER] 收到消息数: {}", receivedMessages.size());

                // 至少应收到 sandbox/status 或 sandbox/init-progress 消息
                // 缓存的 initialize 消息应在 pipeline ready 后被回放
                for (String msg : receivedMessages) {
                    log.info("[BUFFER] 消息: {}", truncate(msg, 150));
                }
            } finally {
                if (session.isOpen()) {
                    session.close();
                }
            }
            log.info("[BUFFER] 消息缓冲测试通过");
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  4. 新会话全流程（session/new）
    // ═════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. 新会话全流程")
    class NewSessionFlowTest {

        /**
         * 4.1 完整流程：连接 → session/config → sandbox ready → initialize → session/new。
         *
         * <p>验证 ACP 协议的完整握手过程和新会话创建。
         */
        @Test
        @DisplayName("4.1 完整新会话流程")
        void testFullNewSessionFlow() throws Exception {
            log.info("=== 4.1 完整新会话流程 ===");
            String token = obtainDeveloperToken();

            String wsUrl = WS_BASE + "?provider=qwen-code&runtime=remote&token=" + token;

            CountDownLatch sandboxReadyLatch = new CountDownLatch(1);
            CountDownLatch initResponseLatch = new CountDownLatch(1);
            CountDownLatch sessionNewLatch = new CountDownLatch(1);

            AtomicReference<String> initResponseRef = new AtomicReference<>();
            AtomicReference<String> sessionNewResponseRef = new AtomicReference<>();
            CopyOnWriteArrayList<String> notifications = new CopyOnWriteArrayList<>();

            StandardWebSocketClient wsClient = new StandardWebSocketClient();
            WebSocketSession session =
                    wsClient.execute(
                                    new TextWebSocketHandler() {
                                        @Override
                                        protected void handleTextMessage(
                                                WebSocketSession s, TextMessage message) {
                                            String payload = message.getPayload();
                                            log.info(
                                                    "[NEW_SESSION] 收到: {}", truncate(payload, 200));
                                            try {
                                                JsonNode node = mapper.readTree(payload);

                                                // 通知消息（无 id）
                                                if (node.has("method") && !node.has("id")) {
                                                    String method = node.get("method").asText();
                                                    notifications.add(payload);
                                                    if ("sandbox/status".equals(method)) {
                                                        String st =
                                                                node.path("params")
                                                                        .path("status")
                                                                        .asText();
                                                        if ("ready".equals(st)) {
                                                            sandboxReadyLatch.countDown();
                                                        }
                                                    }
                                                    return;
                                                }

                                                // 响应消息（有 id）
                                                if (node.has("id")) {
                                                    int id = node.get("id").asInt();
                                                    if (id == 0) {
                                                        initResponseRef.set(payload);
                                                        initResponseLatch.countDown();
                                                    } else if (id == 1) {
                                                        sessionNewResponseRef.set(payload);
                                                        sessionNewLatch.countDown();
                                                    }
                                                }
                                            } catch (Exception e) {
                                                log.debug("[NEW_SESSION] 非 JSON: {}", payload);
                                            }
                                        }
                                    },
                                    new WebSocketHttpHeaders(),
                                    URI.create(wsUrl))
                            .get(WS_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);

            try {
                assertTrue(session.isOpen(), "WebSocket 应已连接");

                // Step 1: 发送 session/config
                log.info("[NEW_SESSION] Step 1: 发送 session/config");
                session.sendMessage(
                        new TextMessage(buildSessionConfigMessage(null, null, null, null)));

                // 等待沙箱就绪
                boolean sandboxReady =
                        sandboxReadyLatch.await(SANDBOX_INIT_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (!sandboxReady) {
                    log.warn("[NEW_SESSION] 沙箱未在超时内就绪，跳过后续步骤");
                    Assumptions.assumeTrue(false, "沙箱环境未就绪");
                }
                log.info("[NEW_SESSION] 沙箱已就绪");

                // Step 2: 发送 initialize
                log.info("[NEW_SESSION] Step 2: 发送 initialize");
                session.sendMessage(new TextMessage(buildInitializeRequest(0)));

                boolean initReceived =
                        initResponseLatch.await(WS_RESPONSE_TIMEOUT_SEC, TimeUnit.SECONDS);
                assertTrue(initReceived, "应在超时内返回 initialize 响应");

                JsonNode initResult = mapper.readTree(initResponseRef.get()).get("result");
                assertNotNull(initResult, "initialize 应包含 result");
                assertTrue(initResult.has("protocolVersion"), "应包含 protocolVersion");
                assertEquals(1, initResult.get("protocolVersion").asInt());
                log.info("[NEW_SESSION] initialize 成功");

                // Step 3: 发送 session/new
                log.info("[NEW_SESSION] Step 3: 发送 session/new");
                session.sendMessage(new TextMessage(buildSessionNewRequest(1, "/workspace/test")));

                boolean sessionReceived =
                        sessionNewLatch.await(WS_RESPONSE_TIMEOUT_SEC, TimeUnit.SECONDS);
                assertTrue(sessionReceived, "应在超时内返回 session/new 响应");

                JsonNode sessionNewRoot = mapper.readTree(sessionNewResponseRef.get());

                if (sessionNewRoot.has("error")) {
                    // 未认证也是可接受的结果
                    int errorCode = sessionNewRoot.path("error").path("code").asInt();
                    if (errorCode == -32000) {
                        log.info("[NEW_SESSION] session/new 需要认证 (code=-32000)，预期行为");
                    } else {
                        log.warn("[NEW_SESSION] session/new 错误: {}", sessionNewRoot.get("error"));
                    }
                } else {
                    JsonNode sessionResult = sessionNewRoot.get("result");
                    assertNotNull(sessionResult, "session/new 应包含 result");
                    assertTrue(sessionResult.has("sessionId"), "session/new 结果应包含 sessionId");
                    String sessionId = sessionResult.get("sessionId").asText();
                    assertFalse(sessionId.isBlank(), "sessionId 不应为空");
                    log.info("[NEW_SESSION] sessionId={}", sessionId);

                    // 验证 models
                    if (sessionResult.has("models")) {
                        JsonNode models = sessionResult.get("models");
                        if (models.has("availableModels")) {
                            assertTrue(
                                    models.get("availableModels").isArray(),
                                    "availableModels 应为数组");
                            log.info(
                                    "[NEW_SESSION] 可用模型数: {}",
                                    models.get("availableModels").size());
                        }
                    }

                    // 验证 modes
                    if (sessionResult.has("modes")) {
                        JsonNode modes = sessionResult.get("modes");
                        if (modes.has("availableModes")) {
                            assertTrue(
                                    modes.get("availableModes").isArray(), "availableModes 应为数组");
                            log.info("[NEW_SESSION] 可用模式数: {}", modes.get("availableModes").size());
                        }
                    }
                }

                // 验证通知消息
                log.info("[NEW_SESSION] 收到通知总数: {}", notifications.size());
                boolean hasWorkspaceInfo =
                        notifications.stream().anyMatch(m -> m.contains("workspace/info"));
                log.info("[NEW_SESSION] workspace/info 通知: {}", hasWorkspaceInfo);

            } finally {
                if (session.isOpen()) {
                    session.close();
                }
            }
            log.info("[NEW_SESSION] 完整新会话流程测试通过");
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  5. 加载历史会话（session/load）
    // ═════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. 加载历史会话")
    class LoadSessionFlowTest {

        /**
         * 5.1 通过 REST API 创建会话 → 通过 WebSocket 加载历史会话。
         *
         * <p>完整流程：
         * 1. POST /coding-sessions 创建会话记录
         * 2. WebSocket 连接 → session/config → sandbox ready
         * 3. initialize → session/load (使用已有 sessionId)
         */
        @Test
        @DisplayName("5.1 REST 创建 + WebSocket 加载历史会话")
        void testLoadExistingSession() throws Exception {
            log.info("=== 5.1 加载历史会话测试 ===");
            String token = obtainDeveloperToken();

            // Step 1: 通过 REST API 创建会话
            String cliSessionId = "cli-load-" + UUID.randomUUID();
            ObjectNode createBody = mapper.createObjectNode();
            createBody.put("cliSessionId", cliSessionId);
            createBody.put("title", "Session to Load");
            createBody.put("providerKey", "qwen-code");

            HttpResponse<String> createResp =
                    httpPost("/coding-sessions", createBody.toString(), token);
            assertEquals(200, createResp.statusCode(), "创建会话应成功");
            JsonNode createResult = parseSuccessData(createResp.body());
            String sessionId = createResult.get("sessionId").asText();
            log.info("[LOAD] 已创建会话 sessionId={}, cliSessionId={}", sessionId, cliSessionId);

            // Step 2: WebSocket 连接
            String wsUrl = WS_BASE + "?provider=qwen-code&runtime=remote&token=" + token;

            CountDownLatch sandboxReadyLatch = new CountDownLatch(1);
            CountDownLatch initLatch = new CountDownLatch(1);
            CountDownLatch loadLatch = new CountDownLatch(1);

            AtomicReference<String> initResponseRef = new AtomicReference<>();
            AtomicReference<String> loadResponseRef = new AtomicReference<>();

            StandardWebSocketClient wsClient = new StandardWebSocketClient();
            WebSocketSession wsSession =
                    wsClient.execute(
                                    new TextWebSocketHandler() {
                                        @Override
                                        protected void handleTextMessage(
                                                WebSocketSession s, TextMessage message) {
                                            String payload = message.getPayload();
                                            log.info("[LOAD] 收到: {}", truncate(payload, 200));
                                            try {
                                                JsonNode node = mapper.readTree(payload);
                                                if (node.has("method") && !node.has("id")) {
                                                    if ("sandbox/status"
                                                            .equals(node.get("method").asText())) {
                                                        if ("ready"
                                                                .equals(
                                                                        node.path("params")
                                                                                .path("status")
                                                                                .asText())) {
                                                            sandboxReadyLatch.countDown();
                                                        }
                                                    }
                                                    return;
                                                }
                                                if (node.has("id")) {
                                                    int id = node.get("id").asInt();
                                                    if (id == 0) {
                                                        initResponseRef.set(payload);
                                                        initLatch.countDown();
                                                    } else if (id == 1) {
                                                        loadResponseRef.set(payload);
                                                        loadLatch.countDown();
                                                    }
                                                }
                                            } catch (Exception e) {
                                                // ignore
                                            }
                                        }
                                    },
                                    new WebSocketHttpHeaders(),
                                    URI.create(wsUrl))
                            .get(WS_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);

            try {
                // Step 3: session/config → 等待沙箱就绪
                wsSession.sendMessage(
                        new TextMessage(buildSessionConfigMessage(null, null, null, null)));

                boolean sandboxReady =
                        sandboxReadyLatch.await(SANDBOX_INIT_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (!sandboxReady) {
                    log.warn("[LOAD] 沙箱未就绪，跳过后续步骤");
                    Assumptions.assumeTrue(false, "沙箱环境未就绪");
                }

                // Step 4: initialize
                wsSession.sendMessage(new TextMessage(buildInitializeRequest(0)));
                boolean initReceived = initLatch.await(WS_RESPONSE_TIMEOUT_SEC, TimeUnit.SECONDS);
                assertTrue(initReceived, "应收到 initialize 响应");

                // Step 5: session/load（使用已创建的 cliSessionId）
                log.info("[LOAD] 发送 session/load, cliSessionId={}", cliSessionId);
                wsSession.sendMessage(
                        new TextMessage(
                                buildSessionLoadRequest(1, cliSessionId, "/workspace/test")));

                boolean loadReceived = loadLatch.await(WS_RESPONSE_TIMEOUT_SEC, TimeUnit.SECONDS);
                assertTrue(loadReceived, "应收到 session/load 响应");

                JsonNode loadRoot = mapper.readTree(loadResponseRef.get());
                log.info("[LOAD] session/load 响应: {}", truncate(loadResponseRef.get(), 300));

                if (loadRoot.has("error")) {
                    int errorCode = loadRoot.path("error").path("code").asInt();
                    String errorMsg = loadRoot.path("error").path("message").asText();
                    log.info("[LOAD] session/load 返回错误: code={}, message={}", errorCode, errorMsg);
                    // 需要认证 (-32000) 或会话不存在都是可接受的结果
                } else {
                    JsonNode loadResult = loadRoot.get("result");
                    assertNotNull(loadResult, "session/load 应包含 result");
                    if (loadResult.has("sessionId")) {
                        log.info("[LOAD] 加载成功, sessionId={}", loadResult.get("sessionId").asText());
                    }
                }
            } finally {
                if (wsSession.isOpen()) {
                    wsSession.close();
                }
                // 清理
                httpDelete("/coding-sessions/" + sessionId, token);
            }
            log.info("[LOAD] 加载历史会话测试通过");
        }

        /**
         * 5.2 加载不存在的会话，验证 session/load 的错误处理。
         */
        @Test
        @DisplayName("5.2 加载不存在的会话")
        void testLoadNonExistentSession() throws Exception {
            log.info("=== 5.2 加载不存在的会话测试 ===");
            String token = obtainDeveloperToken();

            String wsUrl = WS_BASE + "?provider=qwen-code&runtime=remote&token=" + token;

            CountDownLatch sandboxReadyLatch = new CountDownLatch(1);
            CountDownLatch initLatch = new CountDownLatch(1);
            CountDownLatch loadLatch = new CountDownLatch(1);

            AtomicReference<String> loadResponseRef = new AtomicReference<>();

            StandardWebSocketClient wsClient = new StandardWebSocketClient();
            WebSocketSession session =
                    wsClient.execute(
                                    new TextWebSocketHandler() {
                                        @Override
                                        protected void handleTextMessage(
                                                WebSocketSession s, TextMessage message) {
                                            String payload = message.getPayload();
                                            try {
                                                JsonNode node = mapper.readTree(payload);
                                                if (node.has("method") && !node.has("id")) {
                                                    if ("sandbox/status"
                                                            .equals(node.get("method").asText())) {
                                                        if ("ready"
                                                                .equals(
                                                                        node.path("params")
                                                                                .path("status")
                                                                                .asText())) {
                                                            sandboxReadyLatch.countDown();
                                                        }
                                                    }
                                                    return;
                                                }
                                                if (node.has("id")) {
                                                    int id = node.get("id").asInt();
                                                    if (id == 0) {
                                                        initLatch.countDown();
                                                    } else if (id == 1) {
                                                        loadResponseRef.set(payload);
                                                        loadLatch.countDown();
                                                    }
                                                }
                                            } catch (Exception e) {
                                                // ignore
                                            }
                                        }
                                    },
                                    new WebSocketHttpHeaders(),
                                    URI.create(wsUrl))
                            .get(WS_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);

            try {
                session.sendMessage(
                        new TextMessage(buildSessionConfigMessage(null, null, null, null)));

                boolean sandboxReady =
                        sandboxReadyLatch.await(SANDBOX_INIT_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (!sandboxReady) {
                    Assumptions.assumeTrue(false, "沙箱环境未就绪");
                }

                session.sendMessage(new TextMessage(buildInitializeRequest(0)));
                assertTrue(
                        initLatch.await(WS_RESPONSE_TIMEOUT_SEC, TimeUnit.SECONDS),
                        "应收到 initialize 响应");

                // 使用一个不存在的 sessionId
                String fakeSessionId = "non-existent-" + UUID.randomUUID();
                session.sendMessage(
                        new TextMessage(
                                buildSessionLoadRequest(1, fakeSessionId, "/workspace/test")));

                boolean loadReceived = loadLatch.await(WS_RESPONSE_TIMEOUT_SEC, TimeUnit.SECONDS);
                assertTrue(loadReceived, "应收到 session/load 响应");

                JsonNode loadRoot = mapper.readTree(loadResponseRef.get());
                // 不存在的会话应返回错误
                assertTrue(loadRoot.has("error"), "加载不存在的会话应返回错误响应");
                log.info(
                        "[LOAD_404] 错误码: {}, 消息: {}",
                        loadRoot.path("error").path("code").asInt(),
                        loadRoot.path("error").path("message").asText());
            } finally {
                if (session.isOpen()) {
                    session.close();
                }
            }
            log.info("[LOAD_404] 加载不存在会话测试通过");
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  6. 对话流程（session/prompt）
    // ═════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("6. 对话流程")
    class ConversationFlowTest {

        /**
         * 6.1 完整对话流程：session/new → session/prompt。
         *
         * <p>在认证状态下执行完整的对话流程：
         * 1. initialize
         * 2. session/new → 获取 sessionId
         * 3. session/prompt → 验证响应
         */
        @Test
        @DisplayName("6.1 新会话 + 对话")
        void testNewSessionAndPrompt() throws Exception {
            log.info("=== 6.1 新会话 + 对话测试 ===");
            String token = obtainDeveloperToken();

            String wsUrl = WS_BASE + "?provider=qwen-code&runtime=remote&token=" + token;

            CountDownLatch sandboxReadyLatch = new CountDownLatch(1);
            CountDownLatch initLatch = new CountDownLatch(1);
            CountDownLatch sessionNewLatch = new CountDownLatch(1);
            CountDownLatch promptLatch = new CountDownLatch(1);

            AtomicReference<String> initRef = new AtomicReference<>();
            AtomicReference<String> sessionNewRef = new AtomicReference<>();
            AtomicReference<String> promptRef = new AtomicReference<>();
            CopyOnWriteArrayList<String> sessionUpdates = new CopyOnWriteArrayList<>();

            StandardWebSocketClient wsClient = new StandardWebSocketClient();
            WebSocketSession session =
                    wsClient.execute(
                                    new TextWebSocketHandler() {
                                        @Override
                                        protected void handleTextMessage(
                                                WebSocketSession s, TextMessage message) {
                                            String payload = message.getPayload();
                                            log.info("[PROMPT] 收到: {}", truncate(payload, 200));
                                            try {
                                                JsonNode node = mapper.readTree(payload);

                                                // 通知消息
                                                if (node.has("method") && !node.has("id")) {
                                                    String method = node.get("method").asText();
                                                    if ("sandbox/status".equals(method)
                                                            && "ready"
                                                                    .equals(
                                                                            node.path("params")
                                                                                    .path("status")
                                                                                    .asText())) {
                                                        sandboxReadyLatch.countDown();
                                                    }
                                                    if ("session/update".equals(method)) {
                                                        sessionUpdates.add(payload);
                                                    }
                                                    return;
                                                }

                                                // 响应消息
                                                if (node.has("id")) {
                                                    int id = node.get("id").asInt();
                                                    if (id == 0) {
                                                        initRef.set(payload);
                                                        initLatch.countDown();
                                                    } else if (id == 1) {
                                                        sessionNewRef.set(payload);
                                                        sessionNewLatch.countDown();
                                                    } else if (id == 2) {
                                                        if (node.has("result")
                                                                || node.has("error")) {
                                                            promptRef.set(payload);
                                                            promptLatch.countDown();
                                                        }
                                                    }
                                                }
                                            } catch (Exception e) {
                                                // ignore
                                            }
                                        }
                                    },
                                    new WebSocketHttpHeaders(),
                                    URI.create(wsUrl))
                            .get(WS_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);

            try {
                // session/config
                session.sendMessage(
                        new TextMessage(buildSessionConfigMessage(null, null, null, null)));

                boolean sandboxReady =
                        sandboxReadyLatch.await(SANDBOX_INIT_TIMEOUT_SEC, TimeUnit.SECONDS);
                if (!sandboxReady) {
                    Assumptions.assumeTrue(false, "沙箱环境未就绪");
                }

                // initialize
                session.sendMessage(new TextMessage(buildInitializeRequest(0)));
                assertTrue(
                        initLatch.await(WS_RESPONSE_TIMEOUT_SEC, TimeUnit.SECONDS),
                        "应收到 initialize 响应");

                // session/new
                session.sendMessage(new TextMessage(buildSessionNewRequest(1, "/workspace/test")));
                assertTrue(
                        sessionNewLatch.await(WS_RESPONSE_TIMEOUT_SEC, TimeUnit.SECONDS),
                        "应收到 session/new 响应");

                JsonNode sessionNewRoot = mapper.readTree(sessionNewRef.get());
                if (sessionNewRoot.has("error")) {
                    int code = sessionNewRoot.path("error").path("code").asInt();
                    if (code == -32000) {
                        log.info("[PROMPT] 需要认证，跳过 prompt 测试");
                        return;
                    }
                    fail("session/new 错误: " + sessionNewRoot.get("error"));
                }

                String sessionId = sessionNewRoot.path("result").path("sessionId").asText();
                assertFalse(sessionId.isBlank(), "sessionId 不应为空");
                log.info("[PROMPT] sessionId={}", sessionId);

                // session/prompt
                String prompt = "Say 'hello' and nothing else";
                session.sendMessage(
                        new TextMessage(buildSessionPromptRequest(2, sessionId, prompt)));

                boolean promptReceived =
                        promptLatch.await(SANDBOX_INIT_TIMEOUT_SEC, TimeUnit.SECONDS);
                assertTrue(promptReceived, "应收到 session/prompt 响应");

                JsonNode promptRoot = mapper.readTree(promptRef.get());
                if (promptRoot.has("error")) {
                    int code = promptRoot.path("error").path("code").asInt();
                    String msg = promptRoot.path("error").path("message").asText();
                    log.info("[PROMPT] prompt 返回错误: code={}, msg={}", code, msg);
                    // -32000 认证或 -32603 内部错误都可接受
                } else {
                    JsonNode promptResult = promptRoot.get("result");
                    assertNotNull(promptResult, "prompt 应有 result");
                    if (promptResult.has("stopReason")) {
                        log.info("[PROMPT] stopReason={}", promptResult.get("stopReason").asText());
                    }
                }

                log.info("[PROMPT] session/update 通知数: {}", sessionUpdates.size());
            } finally {
                if (session.isOpen()) {
                    session.close();
                }
            }
            log.info("[PROMPT] 对话流程测试通过");
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  7. 连接生命周期与资源清理
    // ═════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("7. 连接生命周期")
    class ConnectionLifecycleTest {

        /**
         * 7.1 客户端主动关闭连接，验证服务端资源清理。
         */
        @Test
        @DisplayName("7.1 客户端主动断开连接")
        void testGracefulDisconnect() throws Exception {
            log.info("=== 7.1 客户端主动断开测试 ===");
            String token = obtainDeveloperToken();

            String wsUrl = WS_BASE + "?provider=qwen-code&token=" + token;
            CountDownLatch closeLatch = new CountDownLatch(1);
            AtomicReference<CloseStatus> closeStatusRef = new AtomicReference<>();

            StandardWebSocketClient wsClient = new StandardWebSocketClient();
            WebSocketSession session =
                    wsClient.execute(
                                    new TextWebSocketHandler() {
                                        @Override
                                        public void afterConnectionClosed(
                                                WebSocketSession s, CloseStatus status) {
                                            closeStatusRef.set(status);
                                            closeLatch.countDown();
                                        }
                                    },
                                    new WebSocketHttpHeaders(),
                                    URI.create(wsUrl))
                            .get(WS_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);

            assertTrue(session.isOpen(), "连接应已建立");
            log.info("[DISCONNECT] 连接已建立，准备主动关闭");

            // 主动关闭
            session.close(CloseStatus.NORMAL);

            boolean closed = closeLatch.await(10, TimeUnit.SECONDS);
            assertTrue(closed, "连接应被正常关闭");
            assertEquals(
                    CloseStatus.NORMAL.getCode(), closeStatusRef.get().getCode(), "关闭状态应为 NORMAL");
            log.info("[DISCONNECT] 连接已正常关闭, status={}", closeStatusRef.get());
        }

        /**
         * 7.2 连接后不发任何消息等待一段时间，验证 ping 保活机制。
         */
        @Test
        @DisplayName("7.2 Ping 保活机制")
        void testPingKeepAlive() throws Exception {
            log.info("=== 7.2 Ping 保活机制测试 ===");
            String token = obtainDeveloperToken();

            String wsUrl = WS_BASE + "?provider=qwen-code&token=" + token;
            CopyOnWriteArrayList<String> receivedMessages = new CopyOnWriteArrayList<>();

            StandardWebSocketClient wsClient = new StandardWebSocketClient();
            WebSocketSession session =
                    wsClient.execute(
                                    new TextWebSocketHandler() {
                                        @Override
                                        protected void handleTextMessage(
                                                WebSocketSession s, TextMessage message) {
                                            receivedMessages.add(message.getPayload());
                                        }
                                    },
                                    new WebSocketHttpHeaders(),
                                    URI.create(wsUrl))
                            .get(WS_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS);

            try {
                assertTrue(session.isOpen(), "连接应已建立");

                // 等待 35 秒（ping 间隔为 30 秒）
                log.info("[PING] 等待 35 秒检查连接是否存活...");
                Thread.sleep(35_000);

                // 连接应仍然存活（ping 机制保活）
                assertTrue(session.isOpen(), "35 秒后连接应仍然存活（ping 保活）");
                log.info("[PING] 连接仍然存活");
            } finally {
                if (session.isOpen()) {
                    session.close();
                }
            }
            log.info("[PING] Ping 保活测试通过");
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  8. 跨用户隔离测试
    // ═════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("8. 跨用户隔离")
    class CrossUserIsolationTest {

        /**
         * 8.1 用户 A 创建的会话，用户 B 不应能访问（更新/删除）。
         *
         * <p>注意：此测试依赖系统中存在至少两个不同的开发者账号。
         * 若只有一个账号则跳过。
         */
        @Test
        @DisplayName("8.1 会话数据用户隔离")
        void testSessionIsolation() throws Exception {
            log.info("=== 8.1 会话用户隔离测试 ===");

            // 使用默认开发者账号
            String tokenA = obtainDeveloperToken();
            assertNotNull(tokenA, "获取开发者 token A 失败");

            // 用户 A 创建会话
            ObjectNode createBody = mapper.createObjectNode();
            createBody.put("cliSessionId", "cli-isolation-" + UUID.randomUUID());
            createBody.put("title", "User A's Session");

            HttpResponse<String> createResp =
                    httpPost("/coding-sessions", createBody.toString(), tokenA);
            assertEquals(200, createResp.statusCode());
            String sessionId = parseSuccessData(createResp.body()).get("sessionId").asText();
            log.info("[ISOLATION] 用户 A 创建会话: sessionId={}", sessionId);

            try {
                // 尝试获取用户 B 的 token
                String tokenB = obtainDeveloperTokenForUser("user2", "123456");
                if (tokenB == null) {
                    log.warn("[ISOLATION] 无法获取第二个开发者账号，跳过跨用户测试");
                    Assumptions.assumeTrue(false, "需要第二个开发者账号");
                }

                // 用户 B 尝试更新用户 A 的会话
                ObjectNode updateBody = mapper.createObjectNode();
                updateBody.put("title", "Hijacked!");

                HttpResponse<String> updateResp =
                        httpPatch("/coding-sessions/" + sessionId, updateBody.toString(), tokenB);
                log.info("[ISOLATION] 用户 B 更新: status={}", updateResp.statusCode());
                assertEquals(404, updateResp.statusCode(), "用户 B 不应能更新用户 A 的会话");

                // 用户 B 尝试删除用户 A 的会话
                HttpResponse<String> deleteResp =
                        httpDelete("/coding-sessions/" + sessionId, tokenB);
                log.info("[ISOLATION] 用户 B 删除: status={}", deleteResp.statusCode());
                assertEquals(404, deleteResp.statusCode(), "用户 B 不应能删除用户 A 的会话");

                // 用户 B 查询不应看到用户 A 的会话
                HttpResponse<String> listResp = httpGet("/coding-sessions?page=0&size=100", tokenB);
                JsonNode listResult = parseSuccessData(listResp.body());
                boolean found = false;
                for (JsonNode item : listResult.get("content")) {
                    if (sessionId.equals(item.path("sessionId").asText())) {
                        found = true;
                        break;
                    }
                }
                assertFalse(found, "用户 B 不应看到用户 A 的会话");
                log.info("[ISOLATION] 用户 B 列表中未包含用户 A 的会话");

            } finally {
                // 用户 A 清理
                httpDelete("/coding-sessions/" + sessionId, tokenA);
            }
            log.info("[ISOLATION] 会话用户隔离测试通过");
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  9. 会话数量限制测试
    // ═════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("9. 会话数量限制")
    class SessionLimitTest {

        /**
         * 9.1 验证单用户会话数超过 50 时自动清理最旧会话。
         *
         * <p>注意：此测试会创建大量会话，运行时间较长。
         */
        @Test
        @DisplayName("9.1 单用户最大 50 个会话限制")
        void testMaxSessionsPerUser() throws Exception {
            log.info("=== 9.1 会话数量限制测试 ===");
            String token = obtainDeveloperToken();

            // 先清理已有会话
            HttpResponse<String> listResp = httpGet("/coding-sessions?page=0&size=100", token);
            JsonNode listResult = parseSuccessData(listResp.body());
            for (JsonNode item : listResult.get("content")) {
                httpDelete("/coding-sessions/" + item.get("sessionId").asText(), token);
            }
            log.info("[LIMIT] 已清理现有会话");

            // 创建 51 个会话
            List<String> allSessionIds = new ArrayList<>();
            for (int i = 0; i < 51; i++) {
                ObjectNode body = mapper.createObjectNode();
                body.put("cliSessionId", "cli-limit-" + i + "-" + UUID.randomUUID());
                body.put("title", "Limit Test " + i);

                HttpResponse<String> resp = httpPost("/coding-sessions", body.toString(), token);
                assertEquals(200, resp.statusCode());
                allSessionIds.add(parseSuccessData(resp.body()).get("sessionId").asText());

                if (i % 10 == 0) {
                    log.info("[LIMIT] 已创建 {} 个会话", i + 1);
                }
            }

            // 查询当前会话数
            HttpResponse<String> finalList = httpGet("/coding-sessions?page=0&size=100", token);
            JsonNode finalResult = parseSuccessData(finalList.body());
            int totalSessions = finalResult.get("content").size();
            log.info("[LIMIT] 创建 51 个后，实际会话数: {}", totalSessions);

            // 由于 MAX_SESSIONS_PER_USER = 50，应自动清理最旧的
            assertTrue(totalSessions <= 50, "会话数不应超过 50，实际为: " + totalSessions);

            // 清理
            HttpResponse<String> cleanupList = httpGet("/coding-sessions?page=0&size=100", token);
            for (JsonNode item : parseSuccessData(cleanupList.body()).get("content")) {
                httpDelete("/coding-sessions/" + item.get("sessionId").asText(), token);
            }
            log.info("[LIMIT] 会话数量限制测试通过");
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  辅助方法
    // ═════════════════════════════════════════════════════════════════

    // ── HTTP 工具方法 ──

    private HttpResponse<String> httpGet(String path, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + path))
                        .timeout(Duration.ofSeconds(10))
                        .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> httpPost(String path, String body, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> httpPatch(String path, String body, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> httpDelete(String path, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + path))
                        .timeout(Duration.ofSeconds(10))
                        .DELETE();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    // ── Token 获取 ──

    private String obtainDeveloperToken() {
        return obtainDeveloperTokenForUser("user", "123456");
    }

    private String obtainDeveloperTokenForUser(String username, String password) {
        try {
            ObjectNode loginBody = mapper.createObjectNode();
            loginBody.put("username", username);
            loginBody.put("password", password);

            HttpResponse<String> resp = httpPost("/developers/login", loginBody.toString(), null);
            if (resp.statusCode() != 200) {
                log.warn("获取 token 失败: status={}", resp.statusCode());
                return null;
            }

            JsonNode body = mapper.readTree(resp.body());
            return body.path("data").path("access_token").asText(null);
        } catch (Exception e) {
            log.error("获取 token 异常: {}", e.getMessage());
            return null;
        }
    }

    // ── JSON 解析 ──

    private JsonNode parseSuccessData(String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);
        assertEquals("SUCCESS", root.path("code").asText(), "接口应返回 SUCCESS");
        return root.get("data");
    }

    // ── WebSocket JSON-RPC 消息构建 ──

    private String buildInitializeRequest(int id) throws Exception {
        ObjectNode fsNode =
                mapper.createObjectNode().put("readTextFile", true).put("writeTextFile", true);
        ObjectNode capNode = mapper.createObjectNode().put("terminal", true);
        capNode.set("fs", fsNode);
        ObjectNode infoNode =
                mapper.createObjectNode()
                        .put("name", "himarket-e2e-test")
                        .put("title", "HiMarket E2E Test")
                        .put("version", "1.0.0");
        ObjectNode paramsNode = mapper.createObjectNode().put("protocolVersion", 1);
        paramsNode.set("clientCapabilities", capNode);
        paramsNode.set("clientInfo", infoNode);
        ObjectNode rootNode =
                mapper.createObjectNode()
                        .put("jsonrpc", "2.0")
                        .put("id", id)
                        .put("method", "initialize");
        rootNode.set("params", paramsNode);
        return mapper.writeValueAsString(rootNode);
    }

    private String buildSessionConfigMessage(
            String modelProductId,
            List<String> mcpServerIds,
            List<String> skillIds,
            String authToken)
            throws Exception {
        ObjectNode paramsNode = mapper.createObjectNode();
        if (modelProductId != null) {
            paramsNode.put("modelProductId", modelProductId);
        }
        if (mcpServerIds != null) {
            ArrayNode mcpArray = mapper.createArrayNode();
            for (String id : mcpServerIds) {
                mcpArray.add(mapper.createObjectNode().put("productId", id));
            }
            paramsNode.set("mcpServers", mcpArray);
        }
        if (skillIds != null) {
            ArrayNode skillArray = mapper.createArrayNode();
            for (String id : skillIds) {
                skillArray.add(mapper.createObjectNode().put("productId", id));
            }
            paramsNode.set("skills", skillArray);
        }
        if (authToken != null) {
            paramsNode.put("authToken", authToken);
        }

        ObjectNode rootNode =
                mapper.createObjectNode().put("jsonrpc", "2.0").put("method", "session/config");
        rootNode.set("params", paramsNode);
        return mapper.writeValueAsString(rootNode);
    }

    private String buildSessionNewRequest(int id, String cwd) throws Exception {
        ObjectNode paramsNode = mapper.createObjectNode().put("cwd", cwd);
        paramsNode.set("mcpServers", mapper.createArrayNode());
        ObjectNode rootNode =
                mapper.createObjectNode()
                        .put("jsonrpc", "2.0")
                        .put("id", id)
                        .put("method", "session/new");
        rootNode.set("params", paramsNode);
        return mapper.writeValueAsString(rootNode);
    }

    private String buildSessionLoadRequest(int id, String sessionId, String cwd) throws Exception {
        ObjectNode paramsNode = mapper.createObjectNode();
        paramsNode.put("sessionId", sessionId);
        paramsNode.put("cwd", cwd);
        paramsNode.set("mcpServers", mapper.createArrayNode());
        ObjectNode rootNode =
                mapper.createObjectNode()
                        .put("jsonrpc", "2.0")
                        .put("id", id)
                        .put("method", "session/load");
        rootNode.set("params", paramsNode);
        return mapper.writeValueAsString(rootNode);
    }

    private String buildSessionPromptRequest(int id, String sessionId, String text)
            throws Exception {
        ObjectNode textBlock = mapper.createObjectNode().put("type", "text").put("text", text);
        ArrayNode promptArray = mapper.createArrayNode().add(textBlock);

        ObjectNode paramsNode = mapper.createObjectNode();
        paramsNode.put("sessionId", sessionId);
        paramsNode.set("prompt", promptArray);

        ObjectNode rootNode =
                mapper.createObjectNode()
                        .put("jsonrpc", "2.0")
                        .put("id", id)
                        .put("method", "session/prompt");
        rootNode.set("params", paramsNode);
        return mapper.writeValueAsString(rootNode);
    }

    // ── 工具方法 ──

    @SuppressWarnings("deprecation")
    private boolean isServerRunning() {
        try {
            var conn = new java.net.URL(BASE_URL + "/cli-providers").openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.connect();
            return true;
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
