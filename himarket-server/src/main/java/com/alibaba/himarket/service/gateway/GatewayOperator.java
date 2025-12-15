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

package com.alibaba.himarket.service.gateway;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.agent.AgentAPIResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.gateway.GatewayResult;
import com.alibaba.himarket.dto.result.httpapi.APIResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMCPServerResult;
import com.alibaba.himarket.dto.result.model.GatewayModelAPIResult;
import com.alibaba.himarket.entity.*;
import com.alibaba.himarket.service.gateway.client.APIGClient;
import com.alibaba.himarket.service.gateway.client.ApsaraStackGatewayClient;
import com.alibaba.himarket.service.gateway.client.GatewayClient;
import com.alibaba.himarket.service.gateway.client.HigressClient;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.gateway.GatewayConfig;
import com.aliyun.sdk.service.apig20240327.models.HttpApiApiInfo;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class GatewayOperator<T> {

    private final Map<String, GatewayClient> clientCache = new ConcurrentHashMap<>();

    public abstract PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size);

    public abstract PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size);

    public abstract PageResult<? extends GatewayMCPServerResult> fetchMcpServers(
            Gateway gateway, int page, int size);

    public abstract PageResult<AgentAPIResult> fetchAgentAPIs(Gateway gateway, int page, int size);

    public abstract PageResult<? extends GatewayModelAPIResult> fetchModelAPIs(
            Gateway gateway, int page, int size);

    public abstract String fetchAPIConfig(Gateway gateway, Object config);

    public abstract String fetchMcpConfig(Gateway gateway, Object conf);

    public abstract String fetchAgentConfig(Gateway gateway, Object conf);

    public abstract String fetchModelConfig(Gateway gateway, Object conf);

    public abstract String fetchMcpToolsForConfig(Gateway gateway, Object conf);

    public abstract PageResult<GatewayResult> fetchGateways(Object param, int page, int size);

    public abstract String createConsumer(
            Consumer consumer, ConsumerCredential credential, GatewayConfig config);

    public abstract void updateConsumer(
            String consumerId, ConsumerCredential credential, GatewayConfig config);

    public abstract void deleteConsumer(String consumerId, GatewayConfig config);

    public abstract boolean isConsumerExists(String consumerId, GatewayConfig config);

    public abstract ConsumerAuthConfig authorizeConsumer(
            Gateway gateway, String consumerId, Object refConfig);

    public abstract void revokeConsumerAuthorization(
            Gateway gateway, String consumerId, ConsumerAuthConfig authConfig);

    public abstract HttpApiApiInfo fetchAPI(Gateway gateway, String apiId);

    public abstract GatewayType getGatewayType();

    public abstract List<URI> fetchGatewayUris(Gateway gateway);

    @SuppressWarnings("unchecked")
    protected T getClient(Gateway gateway) {
        String clientKey =
                gateway.getGatewayType().isAPIG()
                        ? gateway.getApigConfig().buildUniqueKey()
                        : gateway.getHigressConfig().buildUniqueKey();
        return (T) clientCache.computeIfAbsent(clientKey, key -> createClient(gateway));
    }

    /** Create a gateway client for the given gateway. */
    private GatewayClient createClient(Gateway gateway) {
        switch (gateway.getGatewayType()) {
            case APIG_API:
            case APIG_AI:
                return new APIGClient(gateway.getApigConfig());
            case APSARA_GATEWAY:
                return new ApsaraStackGatewayClient(gateway.getApsaraGatewayConfig());
            case HIGRESS:
                return new HigressClient(gateway.getHigressConfig());
            default:
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "No factory found for gateway type: " + gateway.getGatewayType());
        }
    }

    /** Remove a gateway client for the given gateway. */
    public void removeClient(String instanceId) {
        GatewayClient client = clientCache.remove(instanceId);
        try {
            client.close();
        } catch (Exception e) {
            log.error("Error closing client for instance: {}", instanceId, e);
        }
    }
}
