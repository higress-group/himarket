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

package com.alibaba.himarket.dto.params.chat;

import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.support.chat.ChatMessage;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import java.net.URI;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvokeModelParam {

    /** Unique ID */
    private String chatId;

    /** Model Product */
    private ProductResult product;

    /** User question */
    private String userQuestion;

    /** Chat messages in OpenAI-compatible format */
    private List<ChatMessage> chatMessages;

    /** If need web search */
    private Boolean enableWebSearch;

    /** Gateway uris, used to request gateway */
    private List<URI> gatewayUris;

    /** MCP servers with transport config */
    private List<MCPTransportConfig> mcpConfigs;

    /** Credential for invoking the Model and MCP */
    private CredentialContext credentialContext;
}
