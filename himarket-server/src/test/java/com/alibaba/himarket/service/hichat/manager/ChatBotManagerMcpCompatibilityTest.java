/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.service.hichat.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.service.hichat.support.ChatBot;
import com.alibaba.himarket.service.hichat.support.LlmChatRequest;
import com.alibaba.himarket.support.chat.mcp.McpTransportConfig;
import com.alibaba.himarket.support.enums.McpTransportMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class ChatBotManagerMcpCompatibilityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldRegisterMcpToolsForChatBot() throws Exception {
        MockMcpSseServer server = new MockMcpSseServer();
        server.start();

        try {
            ToolManager toolManager = new ToolManager();
            ChatBotManager chatBotManager = new ChatBotManager(toolManager);

            ProductResult product = new ProductResult();
            product.setProductId("model-product");
            product.setName("test-model");

            McpTransportConfig mcpConfig =
                    McpTransportConfig.builder()
                            .mcpServerName("mock-mcp")
                            .productId("mcp-product")
                            .transportMode(McpTransportMode.SSE)
                            .url(server.sseUrl())
                            .build();

            LlmChatRequest request =
                    LlmChatRequest.builder()
                            .chatId("chat-1")
                            .sessionId("session-1")
                            .product(product)
                            .historyMessages(List.of())
                            .mcpConfigs(List.of(mcpConfig))
                            .build();

            StubModel model = new StubModel();
            ChatBot chatBot = chatBotManager.getOrCreateChatBot(request, model);

            assertNotNull(chatBot);
            assertFalse(chatBot.isDegraded());
            assertTrue(chatBot.getToolMetas().containsKey("echo"));
            assertEquals("mock-mcp", chatBot.getToolMetas().get("echo").getMcpServerName());

            List<Event> events =
                    chatBot.chat(Msg.builder().role(MsgRole.USER).textContent("echo").build())
                            .collectList()
                            .block();

            assertNotNull(events);
            assertEquals(2, model.callCount());
            assertEquals(1, server.toolCallCount());
        } finally {
            server.stop();
        }
    }

    private static final class StubModel implements Model {

        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Flux<ChatResponse> stream(
                List<Msg> messages, List<ToolSchema> toolSchemas, GenerateOptions options) {
            if (calls.getAndIncrement() == 0) {
                return Flux.just(
                        ChatResponse.builder()
                                .id("tool-call-response")
                                .content(
                                        List.of(
                                                ToolUseBlock.builder()
                                                        .id("call-1")
                                                        .name("echo")
                                                        .input(Map.of("text", "hello"))
                                                        .content("{\"text\":\"hello\"}")
                                                        .build()))
                                .finishReason("tool_calls")
                                .build());
            }
            return Flux.just(
                    ChatResponse.builder()
                            .id("final-response")
                            .content(List.of(TextBlock.builder().text("done").build()))
                            .finishReason("stop")
                            .build());
        }

        @Override
        public String getModelName() {
            return "stub-model";
        }

        int callCount() {
            return calls.get();
        }
    }

    private static final class MockMcpSseServer {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final CountDownLatch closeLatch = new CountDownLatch(1);
        private final AtomicInteger toolCallCount = new AtomicInteger();
        private final AtomicReference<OutputStream> sseStream = new AtomicReference<>();

        MockMcpSseServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/sse", this::handleSse);
            server.createContext("/message", this::handleMessage);
            server.setExecutor(executor);
        }

        void start() {
            server.start();
        }

        String sseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/sse";
        }

        int toolCallCount() {
            return toolCallCount.get();
        }

        void stop() {
            closeLatch.countDown();
            server.stop(0);
            executor.shutdownNow();
        }

        private void handleSse(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);

            OutputStream body = exchange.getResponseBody();
            sseStream.set(body);
            writeSse(body, "endpoint", "/message");
            try {
                closeLatch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                body.close();
                exchange.close();
            }
        }

        private void handleMessage(HttpExchange exchange) throws IOException {
            JsonNode request = MAPPER.readTree(exchange.getRequestBody());
            if (request.has("id")) {
                writeSse(sseStream.get(), "message", responseFor(request, toolCallCount));
            }
            exchange.sendResponseHeaders(202, 2);
            exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        }

        private static String responseFor(JsonNode request, AtomicInteger toolCallCount)
                throws IOException {
            ObjectNode response = MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", request.get("id"));

            String method = request.path("method").asText();
            ObjectNode result = response.putObject("result");
            if ("initialize".equals(method)) {
                result.put("protocolVersion", "2024-11-05");
                result.putObject("capabilities").putObject("tools");
                result.putObject("serverInfo").put("name", "mock").put("version", "1.0.0");
            } else if ("tools/list".equals(method)) {
                ObjectNode tool = result.putArray("tools").addObject();
                tool.put("name", "echo");
                tool.put("description", "Echo input text");
                ObjectNode schema = tool.putObject("inputSchema");
                schema.put("type", "object");
                schema.putObject("properties")
                        .putObject("text")
                        .put("type", "string")
                        .put("description", "Text to echo");
                schema.putArray("required").add("text");
            } else if ("tools/call".equals(method)) {
                toolCallCount.incrementAndGet();
                String text = request.path("params").path("arguments").path("text").asText();
                ObjectNode content = result.putArray("content").addObject();
                content.put("type", "text");
                content.put("text", text);
                result.put("isError", false);
            }
            return MAPPER.writeValueAsString(response);
        }

        private static void writeSse(OutputStream stream, String event, String data)
                throws IOException {
            stream.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
            stream.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
            stream.flush();
        }
    }
}
