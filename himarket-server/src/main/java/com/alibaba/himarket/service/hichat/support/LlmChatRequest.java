package com.alibaba.himarket.service.hichat.support;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import io.agentscope.core.message.Msg;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@Data
public class LlmChatRequest {

    /**
     * The unique chatId
     */
    private String chatId;

    /**
     * Session ID
     */
    private String sessionId;

    /**
     * Model product
     */
    private ProductResult product;

    /**
     * User message
     */
    private Msg userMessages;

    /**
     * History messages for initializing memory
     */
    private List<Msg> historyMessages;

    /**
     * URI, use this uri to request model
     */
    private URI uri;

    /**
     * API key
     */
    private String apiKey;

    /**
     * Custom headers
     */
    private Map<String, String> headers;

    /**
     * Custom query parameters
     */
    private Map<String, String> queryParams;

    /**
     * Custom json body
     */
    private Map<String, Object> bodyParams;

    /**
     * If not empty, use these URIs to resolve DNS
     */
    private List<URI> gatewayUris;

    /**
     * MCP servers with transport config
     */
    private List<MCPTransportConfig> mcpConfigs;

    public void tryResolveDns() {
        if (CollUtil.isEmpty(gatewayUris) || !"http".equalsIgnoreCase(uri.getScheme())) {
            return;
        }

        try {
            // Randomly select a gateway URI
            URI gatewayUri = gatewayUris.get(0);

            String originalHost = uri.getHost();
            // Build new URI keeping original path and query but replacing scheme, host and port
            this.uri =
                    new URI(
                            gatewayUri.getScheme(),
                            uri.getUserInfo(),
                            gatewayUri.getHost(),
                            gatewayUri.getPort(),
                            uri.getPath(),
                            uri.getQuery(),
                            uri.getFragment());

            if (this.headers == null) {
                this.headers = new HashMap<>();
            }
            // Set Host header
            this.headers.put("Host", originalHost);

        } catch (Exception e) {
            log.warn("Failed to resolve DNS for URI: {}", uri, e);
        }
    }
}
