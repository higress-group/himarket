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

import static reactor.core.scheduler.Schedulers.boundedElastic;

import com.alibaba.himarket.core.event.McpClientRemovedEvent;
import com.alibaba.himarket.core.utils.CacheUtil;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.mcp.McpConfigResult;
import com.alibaba.himarket.support.chat.mcp.McpTransportConfig;
import com.alibaba.himarket.support.common.Strings;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Tool manager for MCP client management.
 *
 * <p>Handles client creation, caching and initialization.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ToolManager {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration INITIALIZE_TIMEOUT = Duration.ofSeconds(30);

    private final ApplicationEventPublisher eventPublisher;

    // MCP client cache with removal listener (10 minutes = 600 seconds)
    private final Cache<String, McpClientWrapper> clientCache =
            CacheUtil.newLRUCache(10 * 60, this::onClientRemoved);

    /**
     * Get existing client or create new one for MCP config
     *
     * @param config MCP transport configuration
     * @return MCP client wrapper, null if creation fails
     */
    public McpClientWrapper getOrCreateClient(McpTransportConfig config) {
        String cacheKey = buildCacheKey(config);

        return clientCache
                .asMap()
                .computeIfAbsent(
                        cacheKey,
                        key -> {
                            try {
                                McpClientWrapper client = createClient(config);
                                if (client != null) {
                                    return client;
                                }
                                log.error(
                                        "Failed to create MCP client, serverName={}, url={}",
                                        config.getMcpServerName(),
                                        config.getUrl());
                            } catch (Exception e) {
                                log.error(
                                        "Failed to create MCP client, serverName={}, url={},"
                                                + " errorMessage={}",
                                        config.getMcpServerName(),
                                        config.getUrl(),
                                        e.getMessage(),
                                        e);
                            }
                            return null;
                        });
    }

    /**
     * Get or create multiple MCP clients in parallel
     *
     * @param configs List of MCP transport configurations
     * @return List of created MCP client wrappers
     */
    public List<McpClientWrapper> getOrCreateClients(List<McpTransportConfig> configs) {
        if (CollectionUtils.isEmpty(configs)) {
            return Collections.emptyList();
        }

        long startTime = System.currentTimeMillis();

        List<McpClientWrapper> result =
                Flux.fromIterable(configs)
                        .flatMap(this::getClient, 20)
                        .collectList()
                        .onErrorReturn(Collections.emptyList())
                        .block();

        long totalTime = System.currentTimeMillis() - startTime;
        log.info(
                "MCP clients initialized, succeededClientCount={}, totalClientCount={},"
                        + " elapsedMillis={}",
                result.size(),
                configs.size(),
                totalTime);

        return result;
    }

    /**
     * Fetch tools from an MCP server using {@link McpConfigResult} and {@link CredentialContext}.
     *
     * @param mcpConfig the MCP configuration
     * @param credential the API credential; {@code null} treated as empty
     * @return list of MCP tools if successful; otherwise {@code null}
     */
    public List<McpSchema.Tool> fetchTools(
            McpConfigResult mcpConfig, CredentialContext credential) {
        if (mcpConfig == null) {
            return null;
        }

        McpTransportConfig transportConfig = mcpConfig.toTransportConfig();
        if (transportConfig == null || Strings.isBlank(transportConfig.getUrl())) {
            log.warn(
                    "Tool fetch skipped because transport config or URL is blank, serverName={}",
                    mcpConfig.getMcpServerName());
            return null;
        }

        CredentialContext ctx = credential != null ? credential : new CredentialContext();
        transportConfig.setHeaders(ctx.copyHeaders());
        transportConfig.setQueryParams(ctx.copyQueryParams());

        return fetchTools(transportConfig);
    }

    /**
     * Fetch tools from an MCP server using raw {@link McpTransportConfig}.
     *
     * @param transportConfig the MCP transport configuration
     * @return list of MCP tools if successful; otherwise {@code null}
     */
    public List<McpSchema.Tool> fetchTools(McpTransportConfig transportConfig) {
        String serverName = transportConfig == null ? null : transportConfig.getMcpServerName();
        if (transportConfig == null || Strings.isBlank(transportConfig.getUrl())) {
            log.warn(
                    "Tool fetch skipped because transport config or URL is blank,"
                            + " serverName={}",
                    serverName);
            return null;
        }

        McpClientWrapper client = getOrCreateClient(transportConfig);
        if (client == null) {
            log.warn(
                    "Tool fetch failed because MCP client cannot be created, serverName={}, url={}",
                    serverName,
                    transportConfig.getUrl());
            return null;
        }

        try {
            List<McpSchema.Tool> tools = client.listTools().block();
            if (CollectionUtils.isEmpty(tools)) {
                log.info("Tool fetch returned empty list, serverName={}", serverName);
                return null;
            }
            log.info(
                    "Tool fetch succeeded, serverName={}, toolCount={}, url={}",
                    serverName,
                    tools.size(),
                    transportConfig.getUrl());
            return tools;
        } catch (Exception e) {
            log.error(
                    "Tool fetch failed, serverName={}, url={}, errorMessage={}",
                    serverName,
                    transportConfig.getUrl(),
                    e.getMessage(),
                    e);
            return null;
        }
    }

    /**
     * Get cached client or create new one reactively
     *
     * <p>Uses computeIfAbsent to ensure atomic creation and prevent race conditions
     * where multiple threads might create duplicate clients for the same key.
     *
     * @param config MCP transport configuration
     * @return Mono of MCP client wrapper
     */
    private Mono<McpClientWrapper> getClient(McpTransportConfig config) {
        String cacheKey = buildCacheKey(config);
        String serverName = config.getMcpServerName();

        // Use computeIfAbsent for atomic check-and-create to prevent race conditions
        return Mono.fromCallable(
                        () ->
                                clientCache
                                        .asMap()
                                        .computeIfAbsent(
                                                cacheKey,
                                                key -> {
                                                    try {
                                                        return createClient(config);
                                                    } catch (Exception e) {
                                                        log.error(
                                                                "Failed to create MCP client for"
                                                                        + " server, serverName={},"
                                                                        + " errorMessage={}",
                                                                serverName,
                                                                e.getMessage(),
                                                                e);
                                                        return null;
                                                    }
                                                }))
                .subscribeOn(boundedElastic())
                .flatMap(
                        client -> {
                            if (client != null) {
                                log.debug("MCP client ready, serverName={}", serverName);
                                return Mono.just(client);
                            } else {
                                log.warn(
                                        "MCP client creation returned null, serverName={}",
                                        serverName);
                                return Mono.empty();
                            }
                        });
    }

    /**
     * Create new MCP client with config
     *
     * @param config MCP transport configuration
     * @return MCP client wrapper, null if creation fails
     */
    public McpClientWrapper createClient(McpTransportConfig config) {
        String serverName = config.getMcpServerName();
        long startTime = System.currentTimeMillis();

        log.info(
                "Creating MCP client, serverName={}, transportMode={}, url={}",
                serverName,
                config.getTransportMode(),
                config.getUrl());

        McpClientBuilder builder = McpClientBuilder.create(serverName).timeout(REQUEST_TIMEOUT);
        switch (config.getTransportMode()) {
            case SSE:
                builder.sseTransport(config.getUrl());
                break;
            case STREAMABLE_HTTP:
                builder.streamableHttpTransport(config.getUrl());
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("Unsupported transport: %s", config.getTransportMode()));
        }

        // Apply authentication headers and query parameters
        if (config.getHeaders() != null && !config.getHeaders().isEmpty()) {
            builder.headers(config.getHeaders());
        }
        if (config.getQueryParams() != null && !config.getQueryParams().isEmpty()) {
            builder.queryParams(config.getQueryParams());
        }

        McpClientWrapper clientWrapper = null;
        try {
            // Build and initialize client
            clientWrapper = builder.buildAsync().block();

            if (clientWrapper == null) {
                log.error("Failed to build MCP client, serverName={}", serverName);
                return null;
            }

            clientWrapper.initialize().timeout(INITIALIZE_TIMEOUT).block();

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("MCP client created, serverName={}, elapsedMillis={}", serverName, totalTime);

            return clientWrapper;

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error(
                    "Failed to create MCP client, serverName={}, elapsedMillis={}, errorMessage={}",
                    serverName,
                    totalTime,
                    e.getMessage(),
                    e);

            // Clean up failed client
            if (clientWrapper != null) {
                try {
                    clientWrapper.close();
                } catch (Exception closeException) {
                    log.warn(
                            "Failed to close MCP client, serverName={}, errorMessage={}",
                            serverName,
                            closeException.getMessage(),
                            closeException);
                }
            }
            return null;
        }
    }

    /**
     * Build cache key from URL, headers and query params
     * Public method for use by other components (e.g. ChatBotManager)
     *
     * @param config MCP transport configuration
     * @return MD5 hashed cache key in format "tool:{md5}"
     */
    public String buildCacheKey(McpTransportConfig config) {
        StringBuilder sb = new StringBuilder();

        // MCP Server URL
        sb.append("url:").append(config.getUrl()).append("|");

        // Credentials (Headers + Query Params)
        sb.append("cred:");

        // Headers (sorted)
        if (config.getHeaders() != null && !config.getHeaders().isEmpty()) {
            sb.append("headers={");
            config.getHeaders().entrySet().stream()
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
        if (config.getQueryParams() != null && !config.getQueryParams().isEmpty()) {
            sb.append("params={");
            config.getQueryParams().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(
                            entry ->
                                    sb.append(entry.getKey())
                                            .append("=")
                                            .append(entry.getValue())
                                            .append(","));
            sb.append("}");
        }

        // Hash the final string for fixed-length cache key
        String rawKey = sb.toString();
        String key = "tool:" + DigestUtils.md5DigestAsHex(rawKey.getBytes(StandardCharsets.UTF_8));

        log.debug("MCP client cache key built, rawKey={}, cacheKey={}", rawKey, key);

        return key;
    }

    /**
     * Callback when MCP client is removed from cache
     *
     * @param cacheKey cache key of the removed client
     * @param client   the removed MCP client wrapper
     * @param cause    reason for removal
     */
    private void onClientRemoved(String cacheKey, McpClientWrapper client, RemovalCause cause) {
        if (client == null) {
            return;
        }

        log.info("MCP client removed from cache, clientName={}, cause={}", client.getName(), cause);

        // Publish event with cache key to notify dependent components
        eventPublisher.publishEvent(new McpClientRemovedEvent(cacheKey));

        // Close the MCP client
        try {
            client.close();
            log.info("MCP client closed, clientName={}", client.getName());
        } catch (Exception e) {
            log.error(
                    "Failed to close MCP client, clientName={}, errorMessage={}",
                    client.getName(),
                    e.getMessage(),
                    e);
        }
    }
}
