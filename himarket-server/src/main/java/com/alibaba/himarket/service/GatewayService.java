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

package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.params.gateway.*;
import com.alibaba.himarket.dto.result.agent.AgentAPIResult;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.gateway.GatewayResult;
import com.alibaba.himarket.dto.result.httpapi.APIResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMCPServerResult;
import com.alibaba.himarket.dto.result.model.GatewayModelAPIResult;
import com.alibaba.himarket.dto.result.product.ProductRefResult;
import com.alibaba.himarket.entity.Consumer;
import com.alibaba.himarket.entity.ConsumerCredential;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.gateway.GatewayConfig;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface GatewayService {

    /**
     * 获取APIG Gateway列表
     *
     * @param param
     * @param page
     * @param size
     * @return
     */
    PageResult<GatewayResult> fetchAPIGGateways(QueryAPIGParam param, int page, int size);

    PageResult<GatewayResult> fetchAdpGateways(QueryAdpAIGatewayParam param, int page, int size);

    PageResult<GatewayResult> fetchApsaraGateways(
            QueryApsaraGatewayParam param, int page, int size);

    /**
     * 导入Gateway
     *
     * @param param
     */
    void importGateway(ImportGatewayParam param);

    /**
     * Update gateway config
     *
     * @param gatewayId
     * @param param
     */
    void updateGateway(String gatewayId, UpdateGatewayParam param);

    GatewayResult getGateway(String gatewayId);

    /**
     * 获取导入的Gateway列表
     *
     * @param param
     * @param pageable
     * @return
     */
    PageResult<GatewayResult> listGateways(QueryGatewayParam param, Pageable pageable);

    /**
     * 删除Gateway
     *
     * @param gatewayId
     */
    void deleteGateway(String gatewayId);

    /**
     * 拉取网关API列表
     *
     * @param gatewayId
     * @param apiType
     * @param page
     * @param size
     * @return
     */
    PageResult<APIResult> fetchAPIs(String gatewayId, String apiType, int page, int size);

    PageResult<APIResult> fetchHTTPAPIs(String gatewayId, int page, int size);

    PageResult<APIResult> fetchRESTAPIs(String gatewayId, int page, int size);

    List<DomainResult> getGatewayDomains(String gatewayId);

    PageResult<APIResult> fetchRoutes(String gatewayId, int page, int size);

    PageResult<GatewayMCPServerResult> fetchMcpServers(String gatewayId, int page, int size);

    PageResult<AgentAPIResult> fetchAgentAPIs(String gatewayId, int page, int size);

    PageResult<GatewayModelAPIResult> fetchModelAPIs(String gatewayId, int page, int size);

    String fetchAPIConfig(String gatewayId, Object config);

    String fetchMcpConfig(String gatewayId, Object conf);

    String fetchMcpToolsForConfig(String gatewayId, Object conf);

    String fetchAgentConfig(String gatewayId, Object conf);

    String fetchModelConfig(String gatewayId, Object conf);

    String createConsumer(Consumer consumer, ConsumerCredential credential, GatewayConfig config);

    void updateConsumer(String gwConsumerId, ConsumerCredential credential, GatewayConfig config);

    void deleteConsumer(String gwConsumerId, GatewayConfig config);

    boolean isConsumerExists(String gwConsumerId, GatewayConfig config);

    ConsumerAuthConfig authorizeConsumer(
            String gatewayId, String gwConsumerId, ProductRefResult productRef);

    void revokeConsumerAuthorization(
            String gatewayId, String gwConsumerId, ConsumerAuthConfig config);

    GatewayConfig getGatewayConfig(String gatewayId);

    List<URI> fetchGatewayUris(String gatewayId);
}
