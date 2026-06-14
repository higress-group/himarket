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
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.gateway.GatewayResult;
import com.alibaba.himarket.dto.result.httpapi.APIResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMcpServerResult;
import com.alibaba.himarket.dto.result.model.GatewayModelAPIResult;
import com.alibaba.himarket.dto.result.product.ProductRefResult;
import com.alibaba.himarket.entity.Consumer;
import com.alibaba.himarket.entity.ConsumerCredential;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.gateway.GatewayConfig;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface GatewayService {

    /**
     * Fetches APIG gateways.
     *
     * @param param APIG gateway query parameters
     * @param page page number
     * @param size page size
     * @return paged APIG gateway results
     */
    PageResult<GatewayResult> fetchAPIGGateways(QueryAPIGParam param, int page, int size);

    /**
     * Fetches ADP AI gateways.
     *
     * @param param ADP AI gateway query parameters
     * @param page page number
     * @param size page size
     * @return paged ADP AI gateway results
     */
    PageResult<GatewayResult> fetchAdpGateways(QueryAdpAIGatewayParam param, int page, int size);

    /**
     * Fetches Apsara gateways.
     *
     * @param param Apsara gateway query parameters
     * @param page page number
     * @param size page size
     * @return paged Apsara gateway results
     */
    PageResult<GatewayResult> fetchApsaraGateways(
            QueryApsaraGatewayParam param, int page, int size);

    /**
     * Imports a gateway.
     *
     * @param param import parameters
     */
    void importGateway(ImportGatewayParam param);

    /**
     * Updates gateway configuration.
     *
     * @param gatewayId gateway ID
     * @param param gateway update parameters
     */
    void updateGateway(String gatewayId, UpdateGatewayParam param);

    /**
     * Gets an imported gateway.
     *
     * @param gatewayId gateway ID
     * @return gateway details
     */
    GatewayResult getGateway(String gatewayId);

    /**
     * Lists imported gateways.
     *
     * @param param query parameters
     * @param pageable pagination parameters
     * @return paged gateway results
     */
    PageResult<GatewayResult> listGateways(QueryGatewayParam param, Pageable pageable);

    /**
     * Deletes a gateway.
     *
     * @param gatewayId gateway ID
     */
    void deleteGateway(String gatewayId);

    /**
     * Fetches gateway APIs.
     *
     * @param gatewayId gateway ID
     * @param apiType API type
     * @param page page number
     * @param size page size
     * @return paged API results
     */
    PageResult<APIResult> fetchAPIs(String gatewayId, String apiType, int page, int size);

    /**
     * Fetches HTTP APIs from a gateway.
     *
     * @param gatewayId gateway ID
     * @param page page number
     * @param size page size
     * @return paged HTTP API results
     */
    PageResult<APIResult> fetchHTTPAPIs(String gatewayId, int page, int size);

    /**
     * Fetches REST APIs from a gateway.
     *
     * @param gatewayId gateway ID
     * @param page page number
     * @param size page size
     * @return paged REST API results
     */
    PageResult<APIResult> fetchRESTAPIs(String gatewayId, int page, int size);

    /**
     * Fetches gateway routes.
     *
     * @param gatewayId gateway ID
     * @param page page number
     * @param size page size
     * @return paged route results
     */
    PageResult<APIResult> fetchRoutes(String gatewayId, int page, int size);

    /**
     * Fetches MCP servers from a gateway.
     *
     * @param gatewayId gateway ID
     * @param page page number
     * @param size page size
     * @return paged MCP server results
     */
    PageResult<GatewayMcpServerResult> fetchMcpServers(String gatewayId, int page, int size);

    /**
     * Gets an MCP server from a gateway.
     *
     * @param gatewayId gateway ID
     * @param mcpServerId MCP server ID
     * @return MCP server details
     */
    GatewayMcpServerResult fetchMcpServer(String gatewayId, String mcpServerId);

    /**
     * Fetches Agent APIs from a gateway.
     *
     * @param gatewayId gateway ID
     * @param page page number
     * @param size page size
     * @return paged Agent API results
     */
    PageResult<AgentAPIResult> fetchAgentAPIs(String gatewayId, int page, int size);

    /**
     * Fetches model APIs from a gateway.
     *
     * @param gatewayId gateway ID
     * @param page page number
     * @param size page size
     * @return paged model API results
     */
    PageResult<GatewayModelAPIResult> fetchModelAPIs(String gatewayId, int page, int size);

    /**
     * Fetches and normalizes the runtime configuration for an API product.
     *
     * @param gatewayId gateway ID
     * @param config gateway-specific API reference configuration
     * @return serialized API configuration stored on the product reference
     */
    String fetchAPIConfig(String gatewayId, Object config);

    /**
     * Fetches and normalizes the runtime configuration for an MCP server product.
     *
     * @param gatewayId gateway ID
     * @param conf gateway-specific MCP reference configuration
     * @return serialized MCP configuration stored on the product reference
     */
    String fetchMcpConfig(String gatewayId, Object conf);

    /**
     * Fetches a usable API credential for runtime access to a gateway-backed product.
     *
     * @param gatewayId gateway ID
     * @param productType product type that determines the gateway resource type
     * @param productRef product reference containing gateway-specific IDs
     * @return credential material suitable for runtime MCP or API access
     */
    CredentialContext fetchApiCredential(
            String gatewayId, ProductType productType, ProductRef productRef);

    /**
     * Fetches and normalizes the runtime configuration for an Agent product.
     *
     * @param gatewayId gateway ID
     * @param conf gateway-specific Agent reference configuration
     * @return serialized Agent configuration stored on the product reference
     */
    String fetchAgentConfig(String gatewayId, Object conf);

    /**
     * Fetches and normalizes the runtime configuration for a model product.
     *
     * @param gatewayId gateway ID
     * @param conf gateway-specific model reference configuration
     * @return serialized model configuration stored on the product reference
     */
    String fetchModelConfig(String gatewayId, Object conf);

    /**
     * Creates a gateway-side consumer for a HiMarket consumer.
     *
     * @param consumer HiMarket consumer
     * @param credential credential material configured for the consumer
     * @param config active gateway configuration
     * @return gateway-side consumer ID
     */
    String createConsumer(Consumer consumer, ConsumerCredential credential, GatewayConfig config);

    /**
     * Updates credential material for an existing gateway-side consumer.
     *
     * @param gwConsumerId gateway-side consumer ID
     * @param credential latest credential material
     * @param config active gateway configuration
     */
    void updateConsumer(String gwConsumerId, ConsumerCredential credential, GatewayConfig config);

    /**
     * Deletes a gateway-side consumer.
     *
     * @param gwConsumerId gateway-side consumer ID
     * @param config active gateway configuration
     */
    void deleteConsumer(String gwConsumerId, GatewayConfig config);

    /**
     * Checks whether a gateway-side consumer exists.
     *
     * @param gwConsumerId gateway-side consumer ID
     * @param config active gateway configuration
     * @return {@code true} if the gateway-side consumer exists
     */
    boolean isConsumerExists(String gwConsumerId, GatewayConfig config);

    /**
     * Authorizes a gateway-side consumer to access the referenced product.
     *
     * @param gatewayId gateway ID
     * @param gwConsumerId gateway-side consumer ID
     * @param productRef product reference containing gateway-specific authorization data
     * @return authorization configuration that should be stored with the subscription
     */
    ConsumerAuthConfig authorizeConsumer(
            String gatewayId, String gwConsumerId, ProductRefResult productRef);

    /**
     * Revokes a previously created gateway authorization.
     *
     * @param gatewayId gateway ID
     * @param gwConsumerId gateway-side consumer ID
     * @param config stored authorization configuration from the subscription
     */
    void revokeConsumerAuthorization(
            String gatewayId, String gwConsumerId, ConsumerAuthConfig config);

    /**
     * Gets the stored gateway configuration.
     *
     * @param gatewayId gateway ID
     * @return gateway configuration
     */
    GatewayConfig getGatewayConfig(String gatewayId);

    /**
     * Fetches gateway endpoint URIs for model invocation or other runtime traffic.
     *
     * @param gatewayId gateway ID
     * @return gateway endpoint URI list
     */
    List<URI> fetchGatewayUris(String gatewayId);
}
