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

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.mcp.McpConfigResult;
import com.alibaba.himarket.dto.result.mcp.McpToolListResult;
import com.alibaba.himarket.entity.Consumer;
import com.alibaba.himarket.entity.ConsumerCredential;
import com.alibaba.himarket.repository.ConsumerCredentialRepository;
import com.alibaba.himarket.repository.ConsumerRepository;
import com.alibaba.himarket.repository.SubscriptionRepository;
import com.alibaba.himarket.service.McpToolService;
import com.alibaba.himarket.service.hichat.manager.ToolManager;
import com.alibaba.himarket.support.api.spec.OpenAPIToolsConfig;
import com.alibaba.himarket.support.consumer.ApiKeyConfig;
import com.alibaba.himarket.support.mcp.OpenAPIToolsConfigConverter;
import com.alibaba.himarket.utils.JsonUtil;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class McpToolServiceImpl implements McpToolService {

    private final ContextHolder contextHolder;
    private final ConsumerRepository consumerRepository;
    private final ConsumerCredentialRepository credentialRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ToolManager toolManager;

    @Override
    @Transactional
    public McpToolListResult listMcpTools(String productId, McpConfigResult mcpConfig) {
        Consumer consumer = findPrimaryConsumer(contextHolder.getUser());
        subscriptionRepository
                .findByConsumerIdAndProductId(consumer.getConsumerId(), productId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.INVALID_REQUEST,
                                        "API product is not subscribed, not allowed to list"
                                                + " tools"));

        CredentialContext credential = getDefaultCredential(consumer.getConsumerId());
        List<McpSchema.Tool> tools = toolManager.fetchTools(mcpConfig, credential);
        if (tools == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to initialize MCP client");
        }

        McpToolListResult result = new McpToolListResult();
        result.setTools(tools);
        return result;
    }

    @Override
    public String fetchToolsConfig(McpConfigResult mcpConfig, CredentialContext credential) {
        List<McpSchema.Tool> tools = toolManager.fetchTools(mcpConfig, credential);
        if (CollectionUtils.isEmpty(tools)) {
            return null;
        }

        try {
            OpenAPIToolsConfig toolsConfig =
                    OpenAPIToolsConfigConverter.convertFromToolList(
                            mcpConfig.getMcpServerName(), tools);
            return JsonUtil.toJson(toolsConfig);
        } catch (Exception e) {
            log.warn(
                    "Failed to convert MCP tools config, mcpServerName={}, errorMessage={}",
                    mcpConfig.getMcpServerName(),
                    e.getMessage(),
                    e);
            return null;
        }
    }

    private Consumer findPrimaryConsumer(String developerId) {
        return consumerRepository
                .findByDeveloperIdAndIsPrimary(developerId, true)
                .orElseGet(
                        () -> {
                            Consumer firstConsumer =
                                    consumerRepository
                                            .findFirstByDeveloperId(
                                                    developerId,
                                                    Sort.by(Sort.Direction.ASC, "createAt"))
                                            .orElseThrow(
                                                    () ->
                                                            new BusinessException(
                                                                    ErrorCode.INVALID_REQUEST,
                                                                    String.format(
                                                                            "No consumer found for"
                                                                                    + " developer:"
                                                                                    + " %s",
                                                                            developerId)));
                            firstConsumer.setIsPrimary(true);
                            return consumerRepository.save(firstConsumer);
                        });
    }

    private CredentialContext getDefaultCredential(String consumerId) {
        return credentialRepository
                .findByConsumerId(consumerId)
                .map(this::buildCredentialContext)
                .orElseGet(
                        () -> {
                            log.debug(
                                    "No credential found for consumer, consumerId={}", consumerId);
                            return CredentialContext.builder().build();
                        });
    }

    private CredentialContext buildCredentialContext(ConsumerCredential credential) {
        ApiKeyConfig config = credential.getApiKeyConfig();
        if (config == null || CollectionUtils.isEmpty(config.getCredentials())) {
            log.debug("No API key configured for credential");
            return CredentialContext.builder().build();
        }

        String apiKey = config.getCredentials().get(0).getApiKey();
        String source = config.getSource();
        String key = config.getKey();
        Map<String, String> headers = new HashMap<>();
        Map<String, String> queryParams = new HashMap<>();

        if ("DEFAULT".equalsIgnoreCase(source)) {
            headers.put("Authorization", "Bearer " + apiKey);
        } else if ("QueryString".equalsIgnoreCase(source)) {
            queryParams.put(key, apiKey);
        } else {
            headers.put(key, apiKey);
        }

        return CredentialContext.builder()
                .apiKey(apiKey)
                .headers(headers)
                .queryParams(queryParams)
                .build();
    }
}
