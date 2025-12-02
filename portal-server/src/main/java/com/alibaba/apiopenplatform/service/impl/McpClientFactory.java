
package com.alibaba.apiopenplatform.service.impl;

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

import java.net.URI;
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

    public McpClientHolder initClient(String type, String url, Map<String, String> headers, Map<String, String> params) {
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
            return new McpClientHolder(client);
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
}
