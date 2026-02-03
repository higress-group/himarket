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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.himarket.core.event.McpClientRemovedEvent;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.service.hichat.support.ChatBot;
import com.alibaba.himarket.service.hichat.support.LlmChatRequest;
import com.alibaba.himarket.service.hichat.support.ToolMeta;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.utils.CacheUtil;
import com.github.benmanes.caffeine.cache.Cache;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.Memory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.ToolGroup;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpTool;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatBotManager {

    private final ToolManager toolManager;
    private final Cache<String, ChatBot> chatBotCache = CacheUtil.newLRUCache(10 * 60);

    /**
     * A reverse lookup map tracking dependencies between tools and ChatBots.
     *
     * <p>Structure:
     * - Key: Tool key "tool:{md5}" (md5 = hash(url+headers+params))
     * - Value: Set of dependent ChatBot keys
     *
     * <p>Used for cascade invalidation when a tool is removed from cache.
     */
    private final Map<String, Set<String>> toolDependencies = new ConcurrentHashMap<>();

    /**
     * Get existing ChatBot or create a new one based on request
     *
     * @param request chat request containing session and configuration info
     * @param model   LLM model to be used
     * @return ChatBot instance or null if creation fails
     */
    public ChatBot getOrCreateChatBot(LlmChatRequest request, Model model) {
        String sessionId = request.getSessionId();
        String productId = request.getProduct().getProductId();

        if (StrUtil.isBlank(sessionId) || StrUtil.isBlank(productId)) {
            log.error("Invalid request: sessionId and productId required");
            return null;
        }

        String cacheKey = buildCacheKey(request);

        // Check if cached ChatBot exists and is still valid
        ChatBot cachedBot = chatBotCache.getIfPresent(cacheKey);
        if (cachedBot != null) {
            if (cachedBot.isValid()) {
                log.debug("Reused ChatBot from cache, degraded: {}", cachedBot.isDegraded());
                return cachedBot;
            } else {
                // Invalid (degraded TTL exceeded), remove from cache and create a new one
                chatBotCache.invalidate(cacheKey);
                log.info("ChatBot invalid (degraded TTL exceeded), removed from cache");
            }
        }

        // Create a new ChatBot
        try {
            ChatBot chatBot = createChatBot(request, model);
            if (chatBot != null) {
                chatBotCache.put(cacheKey, chatBot);

                // Register mapping relationship
                int mcpCount = registerToolDependencies(cacheKey, request.getMcpConfigs());

                log.info(
                        "Created new ChatBot for session: {}, degraded: {}, MCPs: {}",
                        sessionId,
                        chatBot.isDegraded(),
                        mcpCount);
            }
            return chatBot;
        } catch (Exception e) {
            log.error(
                    "Failed to create ChatBot, sessionId: {}, productId: {}",
                    sessionId,
                    productId,
                    e);
            return null;
        }
    }

    /**
     * Create a new ChatBot instance with required components
     *
     * @param request chat request containing configuration
     * @param model   LLM model to be used
     * @return configured ChatBot instance
     */
    private ChatBot createChatBot(LlmChatRequest request, Model model) {
        ProductResult product = request.getProduct();
        long startTime = System.currentTimeMillis();

        // Initialize and register mcp tools
        List<MCPTransportConfig> mcpConfigs = request.getMcpConfigs();
        int expectedMcpCount = CollUtil.isEmpty(mcpConfigs) ? 0 : mcpConfigs.size();

        List<McpClientWrapper> mcpClients = loadMcpClients(request);
        Toolkit toolkit = new Toolkit();
        long actualSuccessCount = registerMcpTools(toolkit, mcpClients);

        // Build tool metadata mapping
        Map<String, ToolMeta> toolMetas = buildToolMetas(toolkit);

        // Initialize memory
        Memory memory = createMemory(request.getHistoryMessages());
        String systemPrompt = buildSystemPrompt(product.getName());

        // Build agent for react chat
        ReActAgent agent =
                ReActAgent.builder()
                        .name(product.getName())
                        .sysPrompt(systemPrompt)
                        .model(model)
                        .toolkit(toolkit)
                        .memory(memory)
                        .maxIters(10)
                        .build();

        // Determine if ChatBot is in degraded mode
        boolean degraded = actualSuccessCount < expectedMcpCount;

        long totalTime = System.currentTimeMillis() - startTime;
        log.info(
                "ChatBot created successfully for session: {}, MCP: {}/{}, degraded: {}, total"
                        + " time: {}ms",
                request.getSessionId(),
                actualSuccessCount,
                expectedMcpCount,
                degraded,
                totalTime);

        return ChatBot.builder().agent(agent).toolMetas(toolMetas).degraded(degraded).build();
    }

    /**
     * Load MCP clients from configuration
     *
     * @param request chat request containing MCP configs
     * @return list of MCP client wrappers
     */
    private List<McpClientWrapper> loadMcpClients(LlmChatRequest request) {
        List<MCPTransportConfig> mcpConfigs = request.getMcpConfigs();
        if (CollUtil.isEmpty(mcpConfigs)) {
            log.debug("No MCP configs found for chat: {}", request.getChatId());
            return List.of();
        }

        List<McpClientWrapper> clients = toolManager.getOrCreateClients(mcpConfigs);
        if (clients.isEmpty()) {
            log.warn("No MCP clients available for chat: {}", request.getChatId());
        }
        return clients;
    }

    /**
     * Register MCP tools to toolkit
     *
     * @param toolkit toolkit to register tools
     * @param clients MCP clients containing tools
     * @return number of MCP clients that successfully registered tools
     */
    private long registerMcpTools(Toolkit toolkit, List<McpClientWrapper> clients) {
        if (clients.isEmpty()) {
            return 0;
        }

        long startTime = System.currentTimeMillis();

        // Process all MCP clients in parallel (max 20 concurrent)
        Long result =
                Flux.fromIterable(clients)
                        .flatMap(
                                client -> {
                                    // Try to list and register tools from this client
                                    return client.listTools()
                                            .flatMapIterable(tools -> tools)
                                            .doOnNext(tool -> registerTool(toolkit, client, tool))
                                            // Success: count this client
                                            .then(Mono.just(1))
                                            .doOnError(
                                                    error ->
                                                            log.error(
                                                                    "Failed to list tools from MCP"
                                                                        + " server: {}, error: {}",
                                                                    client.getName(),
                                                                    error.getMessage()))
                                            .onErrorResume(error -> Mono.empty());
                                },
                                20)
                        .count()
                        .defaultIfEmpty(0L)
                        .block();

        long successCount = result != null ? result : 0;
        long totalTime = System.currentTimeMillis() - startTime;

        log.info(
                "MCP tools registered: {}/{} servers succeeded, total time: {}ms",
                successCount,
                clients.size(),
                totalTime);

        return successCount;
    }

    /**
     * Build tool metadata from toolkit
     *
     * @param toolkit toolkit containing registered tools
     * @return map from tool name to tool metadata
     */
    private Map<String, ToolMeta> buildToolMetas(Toolkit toolkit) {
        Map<String, ToolMeta> toolMetas = new HashMap<>();

        // Get all active groups (each group represents an MCP server)
        List<String> activeGroups = toolkit.getActiveGroups();

        for (String groupName : activeGroups) {
            ToolGroup group = toolkit.getToolGroup(groupName);
            if (group == null) {
                log.warn("Tool group not found: {}", groupName);
                continue;
            }

            Set<String> tools = group.getTools();

            for (String toolName : tools) {
                ToolMeta toolMeta =
                        ToolMeta.builder().mcpServerName(groupName).toolName(toolName).build();

                toolMetas.put(toolName, toolMeta);
            }
        }

        return toolMetas;
    }

    /**
     * Register single MCP tool to toolkit with groupName
     *
     * @param toolkit toolkit to register tool
     * @param client  MCP client wrapper
     * @param tool    tool to be registered
     */
    private void registerTool(Toolkit toolkit, McpClientWrapper client, McpSchema.Tool tool) {
        try {
            Map<String, Object> parameters =
                    McpTool.convertMcpSchemaToParameters(tool.inputSchema());
            McpTool mcpTool =
                    new McpTool(
                            tool.name(),
                            tool.description() != null ? tool.description() : "",
                            parameters,
                            client);

            // Use MCP server name as groupName
            String groupName = client.getName();

            // Create tool group if not exists
            if (toolkit.getToolGroup(groupName) == null) {
                toolkit.createToolGroup(groupName, "Tools from MCP server: " + groupName, true);
            }

            // Register tool with group
            toolkit.registration().agentTool(mcpTool).group(groupName).apply();

        } catch (Exception e) {
            log.error("Failed to register tool: {} from {}", tool.name(), client.getName(), e);
        }
    }

    /**
     * Create memory with history messages
     *
     * @param historyMessages list of historical messages
     * @return memory instance with loaded messages
     */
    private Memory createMemory(List<Msg> historyMessages) {
        Memory memory = new InMemoryMemory();

        if (CollUtil.isNotEmpty(historyMessages)) {
            // Limit initial memory to 20 messages
            int maxInitMessages = 20;
            List<Msg> messagesToLoad = historyMessages;

            if (historyMessages.size() > maxInitMessages) {
                int startIndex = historyMessages.size() - maxInitMessages;
                messagesToLoad = historyMessages.subList(startIndex, historyMessages.size());
                log.info(
                        "Truncated history from {} to {} messages for initialization",
                        historyMessages.size(),
                        maxInitMessages);
            }

            messagesToLoad.forEach(memory::addMessage);
            log.debug("Initialized memory with {} messages", messagesToLoad.size());
        }

        return memory;
    }

    /**
     * Build system prompt for ChatBot
     *
     * @param productName name of the product
     * @return formatted system prompt
     */
    private String buildSystemPrompt(String productName) {
        return String.format(
                "You are a helpful AI assistant powered by %s. "
                        + "You can use various tools to help answer user questions. "
                        + "Always provide accurate and helpful responses.",
                productName);
    }

    /**
     * Register tool dependencies for cascade invalidation.
     * Maps MCP tool keys to dependent ChatBot for automatic cleanup when tool is removed.
     *
     * @param chatBotCacheKey Cache key of ChatBot to track
     * @param mcpConfigs List of MCP configs used by ChatBot
     * @return Number of registered tool dependencies
     */
    private int registerToolDependencies(
            String chatBotCacheKey, List<MCPTransportConfig> mcpConfigs) {
        if (CollUtil.isEmpty(mcpConfigs)) {
            return 0;
        }

        // Build MCP cache keys
        List<String> mcpCacheKeys = mcpConfigs.stream().map(toolManager::buildCacheKey).toList();

        // Register mapping
        for (String mcpCacheKey : mcpCacheKeys) {
            toolDependencies
                    .computeIfAbsent(mcpCacheKey, k -> ConcurrentHashMap.newKeySet())
                    .add(chatBotCacheKey);
        }

        log.debug(
                "Registered mapping for ChatBot: {}, MCP keys: {}, total mappings: {}",
                chatBotCacheKey,
                mcpCacheKeys.size(),
                toolDependencies.size());

        return mcpCacheKeys.size();
    }

    /**
     * Event listener for MCP client removal
     * Invalidates all ChatBots that depend on the removed MCP client
     *
     * @param event MCP client removed event
     */
    @EventListener
    @Async("taskExecutor")
    public void onMcpClientRemoved(McpClientRemovedEvent event) {
        String mcpCacheKey = event.getMcpCacheKey();

        log.info("Received MCP client removed event, key: {}", mcpCacheKey);

        // Get all ChatBot cache keys that depend on this MCP
        Set<String> chatBotKeys = toolDependencies.remove(mcpCacheKey);

        if (CollUtil.isEmpty(chatBotKeys)) {
            log.debug("No ChatBots depend on MCP key: {}", mcpCacheKey);
            return;
        }

        // Invalidate all dependent ChatBots directly from cache
        for (String cacheKey : chatBotKeys) {
            chatBotCache.invalidate(cacheKey);
        }

        log.info("Invalidated {} ChatBots for MCP key: {}", chatBotKeys.size(), mcpCacheKey);
    }

    /**
     * Build cache key from session info, model endpoint and credentials
     *
     * @param request chat request containing configuration
     * @return MD5 hashed cache key
     */
    private String buildCacheKey(LlmChatRequest request) {
        StringBuilder sb = new StringBuilder();

        // Session ID (for Memory isolation)
        sb.append("session:").append(request.getSessionId()).append("|");

        // Product ID (for Product isolation)
        sb.append("product:").append(request.getProduct().getProductId()).append("|");

        // Model URL (scheme + host + port + path)
        if (request.getUri() != null) {
            sb.append("url:")
                    .append(request.getUri().getScheme())
                    .append("://")
                    .append(request.getUri().getHost());

            if (request.getUri().getPort() > 0) {
                sb.append(":").append(request.getUri().getPort());
            }

            if (StrUtil.isNotBlank(request.getUri().getPath())) {
                sb.append(request.getUri().getPath());
            }

            sb.append("|");
        } else {
            sb.append("url:none|");
        }

        // Credentials (API Key + Headers + Query Params)
        sb.append("cred:");

        // API Key
        if (StrUtil.isNotBlank(request.getApiKey())) {
            sb.append("apiKey=").append(request.getApiKey()).append(",");
        }

        // Headers (sorted)
        if (request.getHeaders() != null && !request.getHeaders().isEmpty()) {
            sb.append("headers={");
            request.getHeaders().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(
                            entry ->
                                    sb.append(entry.getKey())
                                            .append("=")
                                            .append(entry.getValue())
                                            .append(","));
            sb.append("},");
        }

        // Query Params (sorted)
        if (request.getQueryParams() != null && !request.getQueryParams().isEmpty()) {
            sb.append("params={");
            request.getQueryParams().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(
                            entry ->
                                    sb.append(entry.getKey())
                                            .append("=")
                                            .append(entry.getValue())
                                            .append(","));
            sb.append("}");
        }

        sb.append("|");

        // MCP Tool URLs (sorted)
        sb.append("mcp:");
        if (CollUtil.isNotEmpty(request.getMcpConfigs())) {
            String mcpUrls =
                    request.getMcpConfigs().stream()
                            .map(MCPTransportConfig::getUrl)
                            .filter(StrUtil::isNotBlank)
                            .sorted()
                            .collect(Collectors.joining(","));
            sb.append(mcpUrls);
        } else {
            sb.append("none");
        }

        // Hash the final string for fixed-length cache key
        String rawKey = sb.toString();

        return "chatBot:" + SecureUtil.md5(rawKey);
    }
}
