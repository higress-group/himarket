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

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.entity.APIEndpoint;
import com.alibaba.himarket.service.McpToolService;
import com.alibaba.himarket.service.hichat.manager.ToolManager;
import com.alibaba.himarket.support.api.endpoint.MCPToolConfig;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.enums.EndpointType;
import com.alibaba.himarket.support.enums.MCPTransportMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class McpToolServiceImpl implements McpToolService {

    private final ObjectMapper objectMapper;
    private final ToolManager toolManager;

    @Override
    public List<APIEndpoint> importFromMcpServer(String endpoint, String token, String type) {
        Map<String, String> headers = new HashMap<>();
        if (StrUtil.isNotBlank(token)) {
            headers.put("Authorization", "Bearer " + token);
        }

        MCPTransportMode transportMode =
                Arrays.stream(MCPTransportMode.values())
                        .filter(
                                m ->
                                        m.name().equalsIgnoreCase(type)
                                                || m.getMode().equalsIgnoreCase(type))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.INVALID_PARAMETER,
                                                "Invalid transport type: " + type));

        MCPTransportConfig transportConfig =
                MCPTransportConfig.builder()
                        .url(endpoint)
                        .transportMode(transportMode)
                        .headers(headers)
                        .mcpServerName("temp-import-server-" + UUID.randomUUID())
                        .build();

        try (McpClientWrapper client = toolManager.createClient(transportConfig)) {
            if (client == null) {
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "Failed to connect to MCP Server");
            }

            List<McpSchema.Tool> tools = client.listTools().block();

            return tools.stream()
                    .map(
                            tool -> {
                                APIEndpoint apiEndpoint = new APIEndpoint();
                                apiEndpoint.setEndpointId(UUID.randomUUID().toString());
                                apiEndpoint.setApiDefinitionId("temp-id");
                                apiEndpoint.setType(EndpointType.MCP_TOOL);
                                apiEndpoint.setName(tool.name());
                                apiEndpoint.setDescription(tool.description());

                                MCPToolConfig config = new MCPToolConfig();
                                config.setInputSchema(
                                        objectMapper.convertValue(
                                                tool.inputSchema(),
                                                new TypeReference<Map<String, Object>>() {}));
                                try {
                                    apiEndpoint.setConfig(objectMapper.writeValueAsString(config));
                                } catch (JsonProcessingException e) {
                                    throw new BusinessException(
                                            ErrorCode.INTERNAL_ERROR,
                                            "Failed to serialize config",
                                            e);
                                }

                                return apiEndpoint;
                            })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to import from MCP Server", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Import failed: " + e.getMessage());
        }
    }
}
