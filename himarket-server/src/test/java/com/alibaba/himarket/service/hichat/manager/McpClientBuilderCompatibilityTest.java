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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class McpClientBuilderCompatibilityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldInitializeAndListToolsWithManagedMcpSdk() throws Exception {
        MockMcpSseServer server = new MockMcpSseServer();
        server.start();

        McpClientWrapper client =
                McpClientBuilder.create("compat")
                        .sseTransport(server.sseUrl())
                        .timeout(Duration.ofSeconds(5))
                        .buildSync();

        try {
            assertNotNull(client);
            assertEquals("compat", client.getName());
            client.initialize().block(Duration.ofSeconds(5));

            List<McpSchema.Tool> tools = client.listTools().block(Duration.ofSeconds(5));

            assertNotNull(tools);
            assertEquals(1, tools.size());
            assertEquals("echo", tools.get(0).name());

            McpSchema.CallToolResult callResult =
                    client.callTool("echo", java.util.Map.of("text", "hello"))
                            .block(Duration.ofSeconds(5));

            assertNotNull(callResult);
            assertFalse(Boolean.TRUE.equals(callResult.isError()));
            assertEquals(1, callResult.content().size());
            assertEquals("hello", ((McpSchema.TextContent) callResult.content().get(0)).text());
        } finally {
            client.close();
            server.stop();
        }
    }

    private static final class MockMcpSseServer {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final CountDownLatch closeLatch = new CountDownLatch(1);
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
                writeSse(sseStream.get(), "message", responseFor(request));
            }
            exchange.sendResponseHeaders(202, 2);
            exchange.getResponseBody().write("{}".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        }

        private static String responseFor(JsonNode request) throws IOException {
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
