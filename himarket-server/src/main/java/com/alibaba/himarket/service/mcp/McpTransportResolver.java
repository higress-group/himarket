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

import cn.hutool.core.util.StrUtil;
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
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.enums.MCPTransportMode;
import com.alibaba.himarket.support.enums.McpEndpointStatus;
import com.alibaba.himarket.support.enums.McpHostingType;
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
@RequiredArgsConstructor
@Slf4j
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
    public List<MCPTransportConfig> resolveTransportConfigs(
            List<String> productIds, String userId) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        String consumerId;
        try {
            ConsumerResult primaryConsumer = consumerService.getPrimaryConsumer(userId);
            consumerId = primaryConsumer.getConsumerId();
        } catch (Exception e) {
            log.warn("[resolveTransportConfigs] 用户 {} 无 consumer，无法校验订阅，返回空列表", userId);
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
                        .collect(Collectors.toList());
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
                metaByProduct.values().stream()
                        .map(McpServerMeta::getMcpServerId)
                        .collect(Collectors.toList());
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

        List<MCPTransportConfig> configs = new ArrayList<>();
        for (String productId : approvedProductIds) {
            McpServerMeta meta = metaByProduct.get(productId);
            if (meta == null) {
                continue;
            }

            List<McpServerEndpoint> endpoints =
                    endpointsByServer.getOrDefault(meta.getMcpServerId(), List.of());
            if (endpoints.isEmpty()) {
                log.debug(
                        "[resolveTransportConfigs] 产品 {} 用户 {} 无可用 endpoint，跳过", productId, userId);
                continue;
            }

            McpServerEndpoint endpoint =
                    endpoints.stream()
                            .filter(ep -> userId.equals(ep.getUserId()))
                            .findFirst()
                            .orElse(endpoints.get(0));

            if (StrUtil.isBlank(endpoint.getEndpointUrl())) {
                continue;
            }

            String protocol =
                    StrUtil.blankToDefault(endpoint.getProtocol(), meta.getProtocolType());
            MCPTransportMode transportMode =
                    McpProtocolUtils.isStreamableHttp(protocol)
                            ? MCPTransportMode.STREAMABLE_HTTP
                            : MCPTransportMode.SSE;

            String url = McpProtocolUtils.normalizeEndpointUrl(endpoint.getEndpointUrl(), protocol);

            Product product = productMap.get(productId);
            configs.add(
                    MCPTransportConfig.builder()
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
                        StrUtil.blankToDefault(
                                endpoint.getHostingType(), McpHostingType.DIRECT.name()));
        if (hosting == McpHostingType.GATEWAY || hosting == McpHostingType.NACOS) {
            try {
                CredentialContext credential = consumerService.getDefaultCredential(userId);
                Map<String, String> headers = credential.copyHeaders();
                return headers.isEmpty() ? null : headers;
            } catch (Exception e) {
                log.warn("[resolveAuthHeaders] 获取用户 credential 失败: {}", e.getMessage());
            }
        } else if (hosting == McpHostingType.SANDBOX) {
            if (StrUtil.isNotBlank(endpoint.getSubscribeParams())) {
                try {
                    cn.hutool.json.JSONObject params =
                            cn.hutool.json.JSONUtil.parseObj(endpoint.getSubscribeParams());
                    String authType = params.getStr("authType");
                    if ("apikey".equalsIgnoreCase(authType)) {
                        String apiKey = params.getStr("apiKey");
                        if (StrUtil.isNotBlank(apiKey)) {
                            return Map.of("Authorization", apiKey);
                        }
                        log.error(
                                "[resolveAuthHeaders] 沙箱 endpoint authType=apikey 但 apiKey 为空:"
                                        + " endpointId={}",
                                endpoint.getEndpointId());
                        throw new BusinessException(
                                ErrorCode.INTERNAL_ERROR, "沙箱 API Key 鉴权配置异常：apiKey 为空");
                    }
                } catch (BusinessException e) {
                    throw e;
                } catch (Exception e) {
                    log.warn("[resolveAuthHeaders] 解析沙箱 subscribeParams 失败: {}", e.getMessage());
                }
            }
        }
        return null;
    }
}
