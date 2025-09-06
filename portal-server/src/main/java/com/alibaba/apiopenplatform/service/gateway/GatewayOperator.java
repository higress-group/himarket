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
import com.alibaba.apiopenplatform.dto.result.GatewayMCPServerResult;
import com.alibaba.apiopenplatform.dto.result.*;
import com.alibaba.apiopenplatform.entity.*;
import com.alibaba.apiopenplatform.service.gateway.client.APIGClient;
import com.alibaba.apiopenplatform.service.gateway.client.GatewayClient;
import com.alibaba.apiopenplatform.service.gateway.client.HigressClient;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class GatewayOperator<T> {

    private final Map<String, GatewayClient> clientCache = new ConcurrentHashMap<>();

    abstract public PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size);

    abstract public PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size);

    abstract public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway gateway, int page, int size);

    abstract public String fetchAPIConfig(Gateway gateway, Object config);

    abstract public String fetchMcpConfig(Gateway gateway, Object conf);

    abstract public PageResult<GatewayResult> fetchGateways(Object param, int page, int size);

    abstract public String createConsumer(Consumer consumer, ConsumerCredential credential, GatewayConfig config);

    abstract public void updateConsumer(String consumerId, ConsumerCredential credential, GatewayConfig config);

    abstract public void deleteConsumer(String consumerId, GatewayConfig config);

    abstract public ConsumerAuthConfig authorizeConsumer(Gateway gateway, String consumerId, Object refConfig);

    abstract public void revokeConsumerAuthorization(Gateway gateway, String consumerId, ConsumerAuthConfig authConfig);

    abstract public APIResult fetchAPI(Gateway gateway, String apiId);

    abstract public GatewayType getGatewayType();

    /**
     * 获取网关控制台仪表盘链接
     * @param gateway 网关实体
     * @return 仪表盘访问链接
     */
    abstract public String getDashboard(Gateway gateway);

    @SuppressWarnings("unchecked")
    protected T getClient(Gateway gateway) {
        String clientKey = gateway.getGatewayType().isAPIG() ?
                gateway.getApigConfig().buildUniqueKey() : gateway.getHigressConfig().buildUniqueKey();
        return (T) clientCache.computeIfAbsent(
                clientKey,
                key -> createClient(gateway)
        );
    }

//    @SuppressWarnings("unchecked")
//    protected T getClient(Gateway gateway) {
//        String clientKey = gateway.getGatewayType().isAPIG() ?
//                gateway.getApigConfig().buildUniqueKey() : gateway.getHigressConfig().buildUniqueKey();
//        return (T) clientCache.computeIfAbsent(
//                clientKey,
//                key -> createClient(gateway)
//        );
//    }

    /**
     * 创建网关客户端
     */
    private GatewayClient createClient(Gateway gateway) {
        switch (gateway.getGatewayType()) {
            case APIG_API:
            case APIG_AI:
                return new APIGClient(gateway.getApigConfig());
            case HIGRESS:
                return new HigressClient(gateway.getHigressConfig());
            default:
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "No factory found for gateway type: " + gateway.getGatewayType());
        }
    }

    /**
     * 移除网关客户端
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
