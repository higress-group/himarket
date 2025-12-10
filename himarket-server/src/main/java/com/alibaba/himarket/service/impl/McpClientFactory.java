
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author shihan
 * @version : McpClientFactory, v0.1 2025年11月26日 21:12 shihan Exp $
 */
@Component
@Slf4j
public class McpClientFactory {

    public McpClientWrapper initClient(String type, String url, Map<String, String> headers, Map<String, String> params) {
        Map<String, String> mcpHeaders = new HashMap<>(headers);
        mcpHeaders.remove("Host");

        URI uri = getUri(url);
        if (uri == null) {
            return null;
        }

        // 提取路径
        String path = uri.getPath();
        String scheme = uri.getScheme();
        String host = uri.getAuthority();
        String endpoint = scheme + "://" + host;
        StringBuilder paramsSuffix = new StringBuilder(params.isEmpty() ? "" : "?");
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    paramsSuffix.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
                }
                if (paramsSuffix.length() > 1) {
                    paramsSuffix.deleteCharAt(paramsSuffix.length() - 1);
                }
                path = path + paramsSuffix;
        McpSyncClient client;

        try {
            McpClientTransport mcpClientTransport = null;
            if (StringUtils.equalsIgnoreCase(type, "sse")) {
                mcpClientTransport = HttpClientSseClientTransport.builder(endpoint)
                        .customizeRequest(builder -> {
                            if (MapUtils.isNotEmpty(mcpHeaders)) {
                                mcpHeaders.forEach(builder::header);
                            }
                        })
                        .connectTimeout(Duration.ofSeconds(2))
                        .sseEndpoint(path)
                        .build();
            } else {
                mcpClientTransport = HttpClientStreamableHttpTransport.builder(endpoint)
                        .customizeRequest(builder -> {
                            if (MapUtils.isNotEmpty(mcpHeaders)) {
                                mcpHeaders.forEach(builder::header);
                            }
                        })
                        .endpoint(path)
                        .connectTimeout(Duration.ofSeconds(2))
                        .build();
            }
            client = McpClient.sync(mcpClientTransport).requestTimeout(Duration.ofSeconds(10))
                    .capabilities(McpSchema.ClientCapabilities.builder().roots(true) // Enable roots capability
                            .build())
                    .build();
            // Initialize connection
            client.initialize();
            return new McpClientWrapper(client);
        } catch (Exception e) {
            log.error("init mcpSyncClient error", e);
            return null;
        }
    }

    private URI getUri(String url) {
        URI uri = null;
        try {
            // 创建URI对象
            uri = new URI(url);
        } catch (Exception e) {
            log.error("fail to parse uri " + url, e);
        }
        return uri;
    }

    public McpClientWrapper newClient(MCPTransportConfig config, CredentialContext credentialContext) {
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
            McpClientTransport transport = buildTransport(config.getTransportMode(), baseUrl, path, credentialContext.copyHeaders());
            if (transport == null) {
                return null;
            }

            // Create MCP client
            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(10))
                    .capabilities(McpSchema.ClientCapabilities.builder()
                            .roots(true)
                            .build())
                    .build();
            client.initialize();

            return new McpClientWrapper(client);
        } catch (Exception e) {
            log.error("Failed to initialize MCP client for URL: {}", config.getUrl(), e);
            return null;
        }
    }

    private McpClientTransport buildTransport(MCPTransportMode mode, String baseUrl, String path, Map<String, String> headers) {
        if (mode == MCPTransportMode.STREAMABLE_HTTP) {
            return HttpClientStreamableHttpTransport.builder(baseUrl)
                    .customizeRequest(builder -> {
                        if (MapUtils.isNotEmpty(headers)) {
                            headers.forEach(builder::header);
                        }
                    })
                    .endpoint(path)
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
        } else {
            return HttpClientSseClientTransport.builder(baseUrl)
                    .customizeRequest(builder -> {
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
