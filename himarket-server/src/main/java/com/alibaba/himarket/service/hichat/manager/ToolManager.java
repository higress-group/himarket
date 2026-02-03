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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.himarket.core.event.McpClientRemovedEvent;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.utils.CacheUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Tool manager for MCP client management.
 * Handles client creation, caching and initialization.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolManager {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration INITIALIZE_TIMEOUT = Duration.ofSeconds(30);

    // MCP client cache with removal listener (10 minutes = 600 seconds)
    private final Cache<String, McpClientWrapper> clientCache =
            CacheUtil.newLRUCache(10 * 60, this::onClientRemoved);

    /**
     * Get existing client or create new one for MCP config
     *
     * @param config MCP transport configuration
     * @return MCP client wrapper, null if creation fails
     */
    public McpClientWrapper getOrCreateClient(MCPTransportConfig config) {
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
                                log.error("Create MCP client failed for: {}", config.getUrl());
                            } catch (Exception e) {
                                log.error("Create MCP client error for: {}", config.getUrl(), e);
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
    public List<McpClientWrapper> getOrCreateClients(List<MCPTransportConfig> configs) {
        if (CollUtil.isEmpty(configs)) {
            return CollUtil.empty(List.class);
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
                "MCP clients initialized: {}/{} succeeded, total time: {}ms",
                result.size(),
                configs.size(),
                totalTime);

        return result;
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
    private Mono<McpClientWrapper> getClient(MCPTransportConfig config) {
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
                                                                        + " server: {}, error: {}",
                                                                serverName,
                                                                e.getMessage());
                                                        return null;
                                                    }
                                                }))
                .subscribeOn(boundedElastic())
                .flatMap(
                        client -> {
                            if (client != null) {
                                log.debug("MCP client ready for server: {}", serverName);
                                return Mono.just(client);
                            } else {
                                log.warn(
                                        "MCP client creation returned null for server: {}",
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
    public McpClientWrapper createClient(MCPTransportConfig config) {
        String serverName = config.getMcpServerName();
        long startTime = System.currentTimeMillis();

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
                        "Unsupported transport: " + config.getTransportMode());
        }

        // Apply authentication headers and query parameters
        if (MapUtil.isNotEmpty(config.getHeaders())) {
            builder.headers(config.getHeaders());
        }
        if (MapUtil.isNotEmpty(config.getQueryParams())) {
            builder.queryParams(config.getQueryParams());
        }

        McpClientWrapper clientWrapper = null;
        try {
            // Build and initialize client
            clientWrapper = builder.buildAsync().block();

            if (clientWrapper == null) {
                log.error("Failed to build MCP client for server: {}", serverName);
                return null;
            }

            clientWrapper.initialize().timeout(INITIALIZE_TIMEOUT).block();

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("MCP client created for server: {}, total time: {}ms", serverName, totalTime);

            return clientWrapper;

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error(
                    "Failed to create MCP client for server: {}, time: {}ms, error: {}",
                    serverName,
                    totalTime,
                    e.getMessage());

            // Clean up failed client
            if (clientWrapper != null) {
                try {
                    clientWrapper.close();
                } catch (Exception closeException) {
                    log.warn("Failed to close MCP client for server: {}", serverName);
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
    public String buildCacheKey(MCPTransportConfig config) {
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
        String key = "tool:" + SecureUtil.md5(rawKey);

        log.debug("MCP client cache key built - raw length: {}, key: {}", rawKey.length(), key);

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

        log.info(
                "MCP client removed from cache: {}, key: {}, cause: {}",
                client.getName(),
                cacheKey,
                cause);

        // Publish event with cache key to notify dependent components
        SpringUtil.getApplicationContext().publishEvent(new McpClientRemovedEvent(cacheKey));

        // Close the MCP client
        try {
            client.close();
            log.info("MCP client closed: {}", client.getName());
        } catch (Exception e) {
            log.error("Failed to close MCP client: {}", client.getName(), e);
        }
    }
}
