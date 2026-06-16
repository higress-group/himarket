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

package com.alibaba.himarket.service.mcp;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.consumer.ConsumerResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.entity.McpServerEndpoint;
import com.alibaba.himarket.entity.McpServerMeta;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.entity.ProductSubscription;
import com.alibaba.himarket.repository.McpServerEndpointRepository;
import com.alibaba.himarket.repository.McpServerMetaRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.repository.SubscriptionRepository;
import com.alibaba.himarket.service.ConsumerService;
import com.alibaba.himarket.support.chat.mcp.McpTransportConfig;
import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.support.enums.McpEndpointStatus;
import com.alibaba.himarket.support.enums.McpHostingType;
import com.alibaba.himarket.support.enums.McpTransportMode;
import com.alibaba.himarket.support.enums.SubscriptionStatus;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Resolves MCP transport configurations for HiChat and HiCoding.
 *
 * <p>Extracted from McpServerServiceImpl to handle:
 * <ul>
 *   <li>Subscription validation</li>
 *   <li>Endpoint resolution (user-specific vs public)</li>
 *   <li>Auth header resolution (Gateway/Nacos/Sandbox)</li>
 *   <li>Transport config assembly</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class McpTransportResolver {

    private final McpServerMetaRepository metaRepository;
    private final McpServerEndpointRepository endpointRepository;
    private final ProductRepository productRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Lazy @Resource private ConsumerService consumerService;

    /**
     * Resolve transport configs for a list of product IDs and a specific user.
     * Validates subscription status and resolves endpoints with auth headers.
     */
    public List<McpTransportConfig> resolveTransportConfigs(
            List<String> productIds, String userId) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        String consumerId;
        try {
            ConsumerResult primaryConsumer = consumerService.getPrimaryConsumer(userId);
            consumerId = primaryConsumer.getConsumerId();
        } catch (Exception e) {
            log.warn(
                    "User has no consumer, returning empty MCP transport configs, userId={}",
                    userId);
            return List.of();
        }

        Map<String, ProductSubscription> subscriptionMap =
                subscriptionRepository
                        .findByConsumerIdAndProductIdIn(consumerId, productIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        ProductSubscription::getProductId, s -> s, (a, b) -> a));
        List<String> approvedProductIds =
                productIds.stream()
                        .filter(
                                pid -> {
                                    ProductSubscription sub = subscriptionMap.get(pid);
                                    return sub != null
                                            && sub.getStatus() == SubscriptionStatus.APPROVED;
                                })
                        .toList();
        if (approvedProductIds.isEmpty()) {
            return List.of();
        }

        List<McpServerMeta> allMetas = metaRepository.findByProductIdIn(approvedProductIds);
        Map<String, McpServerMeta> metaByProduct =
                allMetas.stream()
                        .collect(
                                Collectors.toMap(McpServerMeta::getProductId, m -> m, (a, b) -> a));
        if (metaByProduct.isEmpty()) {
            return List.of();
        }

        List<String> mcpServerIds =
                metaByProduct.values().stream().map(McpServerMeta::getMcpServerId).toList();
        Map<String, List<McpServerEndpoint>> endpointsByServer =
                endpointRepository
                        .findByMcpServerIdInAndUserIdInAndStatus(
                                mcpServerIds,
                                List.of(userId, McpEndpointStatus.PUBLIC_USER_ID),
                                McpEndpointStatus.ACTIVE.name())
                        .stream()
                        .collect(Collectors.groupingBy(McpServerEndpoint::getMcpServerId));

        Map<String, Product> productMap =
                productRepository.findByProductIdIn(approvedProductIds).stream()
                        .collect(Collectors.toMap(Product::getProductId, p -> p, (a, b) -> a));

        List<McpTransportConfig> configs = new ArrayList<>();
        for (String productId : approvedProductIds) {
            McpServerMeta meta = metaByProduct.get(productId);
            if (meta == null) {
                continue;
            }

            List<McpServerEndpoint> endpoints =
                    endpointsByServer.getOrDefault(meta.getMcpServerId(), List.of());
            if (endpoints.isEmpty()) {
                log.debug(
                        "Product has no available MCP endpoint for user, productId={}, userId={}",
                        productId,
                        userId);
                continue;
            }

            McpServerEndpoint endpoint =
                    endpoints.stream()
                            .filter(ep -> userId.equals(ep.getUserId()))
                            .findFirst()
                            .orElse(endpoints.get(0));

            if (Strings.isBlank(endpoint.getEndpointUrl())) {
                continue;
            }

            String protocol =
                    Strings.blankToDefault(endpoint.getProtocol(), meta.getProtocolType());
            McpTransportMode transportMode =
                    McpProtocolUtils.isStreamableHttp(protocol)
                            ? McpTransportMode.STREAMABLE_HTTP
                            : McpTransportMode.SSE;

            String url = McpProtocolUtils.normalizeEndpointUrl(endpoint.getEndpointUrl(), protocol);

            Product product = productMap.get(productId);
            configs.add(
                    McpTransportConfig.builder()
                            .mcpServerName(endpoint.getMcpName())
                            .productId(productId)
                            .description(product != null ? product.getDescription() : null)
                            .transportMode(transportMode)
                            .url(url)
                            .headers(resolveAuthHeaders(endpoint, meta, userId))
                            .build());
        }

        return configs;
    }

    /**
     * Resolve auth headers based on endpoint hosting type.
     * Gateway/Nacos: use consumer credential (API Key).
     * Sandbox: read API Key from subscribeParams.
     */
    private Map<String, String> resolveAuthHeaders(
            McpServerEndpoint endpoint, McpServerMeta meta, String userId) {
        McpHostingType hosting =
                McpHostingType.valueOf(
                        Strings.blankToDefault(
                                endpoint.getHostingType(), McpHostingType.DIRECT.name()));
        if (hosting == McpHostingType.GATEWAY || hosting == McpHostingType.NACOS) {
            try {
                CredentialContext credential = consumerService.getDefaultCredential(userId);
                Map<String, String> headers = credential.copyHeaders();
                return headers.isEmpty() ? null : headers;
            } catch (Exception e) {
                log.warn(
                        "Failed to resolve MCP auth headers from user credential, hostingType={},"
                                + " userId={}, errorMessage={}",
                        hosting,
                        userId,
                        e.getMessage());
            }
        } else if (hosting == McpHostingType.SANDBOX) {
            if (Strings.isNotBlank(endpoint.getSubscribeParams())) {
                try {
                    com.fasterxml.jackson.databind.node.ObjectNode params =
                            com.alibaba.himarket.utils.JsonUtil.readObjectNode(
                                    endpoint.getSubscribeParams());
                    String authType = params.path("authType").asText();
                    if ("apikey".equalsIgnoreCase(authType)) {
                        String apiKey = params.path("apiKey").asText();
                        if (Strings.isNotBlank(apiKey)) {
                            return Map.of("Authorization", apiKey);
                        }
                        log.error(
                                "Sandbox endpoint API key is empty, endpointId={}",
                                endpoint.getEndpointId());
                        throw new BusinessException(
                                ErrorCode.INTERNAL_ERROR,
                                "Sandbox API key authentication config is invalid: apiKey is"
                                        + " empty");
                    }
                } catch (BusinessException e) {
                    throw e;
                } catch (Exception e) {
                    log.warn(
                            "Failed to parse sandbox subscribe parameters, endpointId={},"
                                    + " errorMessage={}",
                            endpoint.getEndpointId(),
                            e.getMessage());
                }
            }
        }
        return null;
    }
}
