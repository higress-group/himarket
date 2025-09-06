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

package com.alibaba.apiopenplatform.service;

import com.alibaba.apiopenplatform.dto.params.gateway.ImportGatewayParam;
import com.alibaba.apiopenplatform.dto.params.gateway.QueryAPIGParam;
import com.alibaba.apiopenplatform.dto.result.GatewayMCPServerResult;
import com.alibaba.apiopenplatform.dto.result.*;
import com.alibaba.apiopenplatform.entity.Consumer;
import com.alibaba.apiopenplatform.entity.ConsumerCredential;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
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
    PageResult<GatewayResult> fetchGateways(QueryAPIGParam param, int page, int size);

    /**
     * 导入Gateway
     *
     * @param param
     */
    void importGateway(ImportGatewayParam param);

    GatewayResult getGateway(String gatewayId);

    /**
     * 获取导入的Gateway列表
     *
     * @param pageable
     * @return
     */
    PageResult<GatewayResult> listGateways(Pageable pageable);

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

    PageResult<APIResult> fetchRoutes(String gatewayId, int page, int size);

    PageResult<GatewayMCPServerResult> fetchMcpServers(String gatewayId, int page, int size);

    String fetchAPIConfig(String gatewayId, Object config);

    String fetchMcpConfig(String gatewayId, Object conf);

    String createConsumer(Consumer consumer, ConsumerCredential credential, GatewayConfig config);

    void updateConsumer(String gwConsumerId, ConsumerCredential credential, GatewayConfig config);

    void deleteConsumer(String gwConsumerId, GatewayConfig config);

    ConsumerAuthConfig authorizeConsumer(String gatewayId, String gwConsumerId, ProductRefResult productRef);

    void revokeConsumerAuthorization(String gatewayId, String gwConsumerId, ConsumerAuthConfig config);

    GatewayConfig getGatewayConfig(String gatewayId);

    /**
     * 获取仪表板URL
     *
     * @return 仪表板URL
     */
    String getDashboard(String gatewayId);
}
