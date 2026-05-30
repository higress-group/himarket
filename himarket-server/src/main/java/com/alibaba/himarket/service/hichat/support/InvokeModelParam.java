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

package com.alibaba.himarket.service.hichat.support;

import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.support.chat.mcp.McpTransportConfig;
import io.agentscope.core.message.Msg;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvokeModelParam {

    /**
     * Chat ID
     */
    private String chatId;

    /**
     * Session ID
     */
    private String sessionId;

    /**
     * Model Product
     */
    private ProductResult product;

    /**
     * User message, contains user question and multimodal
     */
    private Msg userMessage;

    /**
     * History messages for initializing memory
     */
    private List<Msg> historyMessages;

    /**
     * If need web search
     */
    private Boolean enableWebSearch;

    /**
     * Gateway ID
     */
    private String gatewayId;

    /**
     * MCP servers with transport config
     */
    private List<McpTransportConfig> mcpConfigs;

    /**
     * Credential for invoking the Model and MCP
     */
    private CredentialContext credentialContext;
}
