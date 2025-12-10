package com.alibaba.himarket.dto.result.chat;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.support.chat.ChatMessage;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.product.ModelFeature;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.WebSearchOptions;

@Data
@Builder
@Slf4j
public class LlmChatRequest {

    /** The unique chatId */
    private String chatId;

    /** User question */
    private String userQuestion;

    /** Generic chat messages, convertible to specific SDK formats (e.g., Spring AI Alibaba). */
    private List<ChatMessage> chatMessages;

    /** URL, contains protocol, host and path */
    private URL url;

    /** Custom headers */
    private Map<String, String> headers;

    /** If not empty, use these IPs to resolve DNS */
    private List<String> gatewayIps;

    /** Credential for invoking the Model and MCP */
    private CredentialContext credentialContext;

    /** MCP servers with transport config */
    private List<MCPTransportConfig> mcpConfigs;

    /** Model feature */
    private ModelFeature modelFeature;

    /** Web search options */
    private WebSearchOptions webSearchOptions;

    public void tryResolveDns() {
        if (CollUtil.isEmpty(gatewayIps) || !"http".equalsIgnoreCase(url.getProtocol())) {
            return;
        }

        try {
            String originalHost = url.getHost();

            // Randomly select an IP
            String randomIp = gatewayIps.get(new Random().nextInt(gatewayIps.size()));

            // Build new URL by replacing domain with IP
            String originalUrl = url.toString();
            String newUrl = originalUrl.replace(originalHost, randomIp);

            if (this.headers == null) {
                this.headers = new HashMap<>();
            }

            // Set Host header
            this.headers.put("Host", originalHost);

            this.url = new URL(newUrl);
        } catch (Exception e) {
            log.warn("Failed to resolve DNS for URL: {}", url, e);
        }
    }
}
