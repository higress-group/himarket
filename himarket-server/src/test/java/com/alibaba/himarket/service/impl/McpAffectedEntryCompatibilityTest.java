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

package com.alibaba.himarket.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.mcp.McpConfigResult;
import com.alibaba.himarket.entity.McpServerMeta;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.repository.ApiDefinitionRepository;
import com.alibaba.himarket.repository.ConsumerRepository;
import com.alibaba.himarket.repository.McpServerEndpointRepository;
import com.alibaba.himarket.repository.McpServerMetaRepository;
import com.alibaba.himarket.repository.ProductPublicationRepository;
import com.alibaba.himarket.repository.ProductRefRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.repository.SubscriptionRepository;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.ProductCategoryService;
import com.alibaba.himarket.service.SkillService;
import com.alibaba.himarket.service.WorkerService;
import com.alibaba.himarket.service.hichat.manager.ToolManager;
import com.alibaba.himarket.service.mcp.McpConfigSyncHelper;
import com.alibaba.himarket.service.mcp.McpSandboxOrchestrator;
import com.alibaba.himarket.service.mcp.McpTransportResolver;
import com.alibaba.himarket.support.api.spec.OpenAPIToolsConfig;
import com.alibaba.himarket.support.enums.McpProtocolType;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SourceType;
import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class McpAffectedEntryCompatibilityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void productSyncShouldFetchAndPersistMcpSdkTools() throws Exception {
        MockMcpSseServer server = new MockMcpSseServer();
        server.start();

        try {
            ProductServiceImpl productService = newProductService(new ToolManager());
            Product product =
                    Product.builder()
                            .productId("mcp-product")
                            .name("mock-mcp")
                            .type(ProductType.MCP_SERVER)
                            .build();
            ProductRef productRef =
                    ProductRef.builder()
                            .productId("mcp-product")
                            .sourceType(SourceType.CUSTOM)
                            .mcpConfig(mcpConfigJson("mock-mcp", server))
                            .build();

            invokePrivate(
                    productService,
                    "syncMcpTools",
                    new Class<?>[] {Product.class, ProductRef.class},
                    product,
                    productRef);

            McpConfigResult updated =
                    JsonUtil.parse(productRef.getMcpConfig(), McpConfigResult.class);
            OpenAPIToolsConfig tools = JsonUtil.parse(updated.getTools(), OpenAPIToolsConfig.class);

            assertNotNull(tools);
            assertEquals("mock-mcp", tools.getServer().getName());
            assertEquals(1, tools.getTools().size());
            assertEquals("echo", tools.getTools().get(0).getName());
            assertEquals("text", tools.getTools().get(0).getArgs().get(0).getName());
        } finally {
            server.stop();
        }
    }

    @Test
    void mcpServerRefreshShouldFetchAndSaveSdkTools() throws Exception {
        MockMcpSseServer server = new MockMcpSseServer();
        server.start();
        McpServerMetaRepository metaRepository = mock(McpServerMetaRepository.class);

        try {
            McpServerServiceImpl mcpServerService =
                    new McpServerServiceImpl(
                            metaRepository,
                            mock(McpServerEndpointRepository.class),
                            mock(ProductRepository.class),
                            mock(ProductPublicationRepository.class),
                            mock(ContextHolder.class),
                            new ToolManager(),
                            mock(McpConfigSyncHelper.class),
                            mock(McpSandboxOrchestrator.class),
                            mock(McpTransportResolver.class));
            McpServerMeta meta =
                    McpServerMeta.builder()
                            .mcpServerId("mcp-1")
                            .productId("product-1")
                            .mcpName("mock-mcp")
                            .build();

            invokePrivate(
                    mcpServerService,
                    "fetchAndSaveToolsListOrThrow",
                    new Class<?>[] {McpServerMeta.class, String.class, String.class},
                    meta,
                    server.sseUrl(),
                    "sse");

            assertNotNull(meta.getToolsConfig());
            assertTrue(meta.getToolsConfig().contains("\"name\":\"echo\""));
            verify(metaRepository).save(meta);
        } finally {
            server.stop();
        }
    }

    private static ProductServiceImpl newProductService(ToolManager toolManager) {
        return new ProductServiceImpl(
                mock(ContextHolder.class),
                mock(PortalService.class),
                mock(GatewayService.class),
                mock(ProductRepository.class),
                mock(ProductRefRepository.class),
                mock(ApiDefinitionRepository.class),
                mock(ProductPublicationRepository.class),
                mock(SubscriptionRepository.class),
                mock(ConsumerRepository.class),
                mock(NacosService.class),
                mock(ProductCategoryService.class),
                toolManager,
                mock(WorkerService.class),
                mock(SkillService.class));
    }

    private static String mcpConfigJson(String serverName, MockMcpSseServer server) {
        McpConfigResult config = new McpConfigResult();
        config.setMcpServerName(serverName);
        config.setProtocol(McpProtocolType.SSE);

        McpConfigResult.McpServerConfig serverConfig = new McpConfigResult.McpServerConfig();
        serverConfig.setPath("/sse");
        serverConfig.setDomains(
                List.of(
                        DomainResult.builder()
                                .protocol("http")
                                .domain("127.0.0.1")
                                .port(server.port())
                                .networkType("internet")
                                .build()));
        config.setMcpServerConfig(serverConfig);

        return JsonUtil.toJson(config);
    }

    private static Object invokePrivate(
            Object target, String methodName, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
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

        int port() {
            return server.getAddress().getPort();
        }

        String sseUrl() {
            return "http://127.0.0.1:" + port() + "/sse";
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
