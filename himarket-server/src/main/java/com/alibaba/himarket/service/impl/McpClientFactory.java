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
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author shihan
 * @version : McpClientFactory, v0.1 2025年11月26日 21:12 shihan Exp $
 */
@Component
@Slf4j
public class McpClientFactory {

    public McpClientWrapper newClient(
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
                            .requestTimeout(Duration.ofSeconds(10))
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

    private McpClientTransport buildTransport(
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
