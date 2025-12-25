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

package com.alibaba.himarket.dto.result.chat;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.support.chat.ChatMessage;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.product.ModelFeature;
import java.net.URI;
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

    /**
     * The unique chatId
     */
    private String chatId;

    /**
     * User question
     */
    private String userQuestion;

    /**
     * Generic chat messages, convertible to specific SDK formats (e.g., Spring AI).
     */
    private List<ChatMessage> chatMessages;

    /**
     * URI, use this uri to request model
     */
    private URI uri;

    /**
     * Custom headers
     */
    private Map<String, String> headers;

    /**
     * If not empty, use these URIs to resolve DNS
     */
    private List<URI> gatewayUris;

    /**
     * Credential for invoking the Model and MCP
     */
    private CredentialContext credentialContext;

    /**
     * MCP servers with transport config
     */
    private List<MCPTransportConfig> mcpConfigs;

    /**
     * Model feature
     */
    private ModelFeature modelFeature;

    /**
     * Web search options
     */
    private WebSearchOptions webSearchOptions;

    public void tryResolveDns() {
        if (CollUtil.isEmpty(gatewayUris) || !"http".equalsIgnoreCase(uri.getScheme())) {
            return;
        }

        try {
            // Randomly select a gateway URI
            URI gatewayUri = gatewayUris.get(new Random().nextInt(gatewayUris.size()));

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
