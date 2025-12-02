package com.alibaba.apiopenplatform.dto.result.chat;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.apiopenplatform.dto.params.chat.ChatRequestBody;
import com.alibaba.apiopenplatform.dto.result.consumer.CredentialContext;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Data
@Builder
@Slf4j
public class LlmChatRequest {

    /**
     * the unique chatId
     */
    private String chatId;

    /**
     * URL, contains protocol, host and path
     */
    private URL url;

    /**
     * Custom headers
     */
    private Map<String, String> headers;

    /**
     * the llm api key
     */
    private String apiKey;

    /**
     * chatRequest
     */
    private ChatRequestBody chatRequest;

    /**
     * If not empty, use these IPs to resolve DNS
     */
    private List<String> gatewayIps;


    private CredentialContext credentialContext;

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