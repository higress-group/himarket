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

import cn.hutool.core.map.MapUtil;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.enums.MCPTransportMode;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class McpClientFactory {

    public static McpClientWrapper newClient(
            MCPTransportConfig config, CredentialContext credentialContext) {
        URL url;
        try {
            url = new URL(config.getUrl());
        } catch (MalformedURLException e) {
            log.warn("Invalid MCP url: {}", config.getUrl(), e);
            return null;
        }

        String baseUrl = String.format("%s://%s", url.getProtocol(), url.getAuthority());
        String path = url.getPath();

        // Compose path with params
        Map<String, String> queryParams = credentialContext.copyQueryParams();
        if (MapUtil.isNotEmpty(queryParams)) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
            queryParams.forEach(builder::queryParam);
            path = builder.build().toString();
        }

        try {
            // Build MCP transport by mode
            McpClientTransport transport =
                    buildTransport(
                            config.getTransportMode(),
                            baseUrl,
                            path,
                            credentialContext.copyHeaders());
            if (transport == null) {
                return null;
            }

            // Create MCP client
            McpSyncClient client =
                    McpClient.sync(transport)
                            .requestTimeout(Duration.ofSeconds(30))
                            .capabilities(
                                    McpSchema.ClientCapabilities.builder().roots(true).build())
                            .build();
            client.initialize();

            return new McpClientWrapper(client);
        } catch (Exception e) {
            log.error("Failed to initialize MCP client for URL: {}", config.getUrl(), e);
            return null;
        }
    }

    private static McpClientTransport buildTransport(
            MCPTransportMode mode, String baseUrl, String path, Map<String, String> headers) {
        if (mode == MCPTransportMode.STREAMABLE_HTTP) {
            return HttpClientStreamableHttpTransport.builder(baseUrl)
                    .customizeRequest(
                            builder -> {
                                if (MapUtils.isNotEmpty(headers)) {
                                    headers.forEach(builder::header);
                                }
                            })
                    .endpoint(path)
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
        } else {
            return HttpClientSseClientTransport.builder(baseUrl)
                    .customizeRequest(
                            builder -> {
                                if (MapUtils.isNotEmpty(headers)) {
                                    headers.forEach(builder::header);
                                }
                            })
                    .sseEndpoint(path)
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
        }
    }
}
