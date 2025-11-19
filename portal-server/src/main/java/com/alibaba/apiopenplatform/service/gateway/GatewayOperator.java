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

package com.alibaba.apiopenplatform.service.gateway;

import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.dto.result.httpapi.APIResult;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.dto.result.gateway.GatewayResult;
import com.alibaba.apiopenplatform.dto.result.mcp.GatewayMCPServerResult;
import com.alibaba.apiopenplatform.dto.result.agent.AgentAPIResult;
import com.alibaba.apiopenplatform.dto.result.model.AIGWModelAPIResult;
import com.alibaba.apiopenplatform.dto.result.model.GatewayModelAPIResult;
import com.alibaba.apiopenplatform.entity.*;
import com.alibaba.apiopenplatform.service.gateway.client.APIGClient;
import com.alibaba.apiopenplatform.service.gateway.client.GatewayClient;
import com.alibaba.apiopenplatform.service.gateway.client.HigressClient;
import com.alibaba.apiopenplatform.service.gateway.client.ApsaraStackGatewayClient;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
import com.aliyun.sdk.service.apig20240327.models.HttpApiApiInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class GatewayOperator<T> {

    private final Map<String, GatewayClient> clientCache = new ConcurrentHashMap<>();

    abstract public PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size);

    abstract public PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size);

    abstract public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway gateway, int page, int size);

    abstract public PageResult<AgentAPIResult> fetchAgentAPIs(Gateway gateway, int page, int size);

    abstract public PageResult<? extends GatewayModelAPIResult> fetchModelAPIs(Gateway gateway, int page, int size);

    abstract public String fetchAPIConfig(Gateway gateway, Object config);

    abstract public String fetchMcpConfig(Gateway gateway, Object conf);

    abstract public String fetchAgentConfig(Gateway gateway, Object conf);

    abstract public String fetchModelConfig(Gateway gateway, Object conf);

    abstract public PageResult<GatewayResult> fetchGateways(Object param, int page, int size);

    abstract public String createConsumer(Consumer consumer, ConsumerCredential credential, GatewayConfig config);

    abstract public void updateConsumer(String consumerId, ConsumerCredential credential, GatewayConfig config);

    abstract public void deleteConsumer(String consumerId, GatewayConfig config);

    /**
     * 检查消费者是否存在于网关中
     *
     * @param consumerId 消费者ID
     * @param config     网关配置
     * @return 是否存在
     */
    abstract public boolean isConsumerExists(String consumerId, GatewayConfig config);

    abstract public ConsumerAuthConfig authorizeConsumer(Gateway gateway, String consumerId, Object refConfig);

    abstract public void revokeConsumerAuthorization(Gateway gateway, String consumerId, ConsumerAuthConfig authConfig);

    abstract public HttpApiApiInfo fetchAPI(Gateway gateway, String apiId);

    abstract public GatewayType getGatewayType();

    abstract public String getDashboard(Gateway gateway, String type);

    abstract public List<String> fetchGatewayIps(Gateway gateway);

    @SuppressWarnings("unchecked")
    protected T getClient(Gateway gateway) {
        String clientKey = gateway.getGatewayType().isAPIG() ?
                gateway.getApigConfig().buildUniqueKey() : gateway.getHigressConfig().buildUniqueKey();
        return (T) clientCache.computeIfAbsent(
                clientKey,
                key -> createClient(gateway)
        );
    }

    /**
     * Create a gateway client for the given gateway.
     */
    private GatewayClient createClient(Gateway gateway) {
        switch (gateway.getGatewayType()) {
            case APIG_API:
            case APIG_AI:
                return new APIGClient(gateway.getApigConfig());
            case APSARA_GATEWAY:
                return new ApsaraStackGatewayClient(
                        gateway.getApsaraGatewayConfig());
            case HIGRESS:
                return new HigressClient(gateway.getHigressConfig());
            default:
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "No factory found for gateway type: " + gateway.getGatewayType());
        }
    }

    /**
     * Remove a gateway client for the given gateway.
     */
    public void removeClient(String instanceId) {
        GatewayClient client = clientCache.remove(instanceId);
        try {
            client.close();
        } catch (Exception e) {
            log.error("Error closing client for instance: {}", instanceId, e);
        }
    }
}
