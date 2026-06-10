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

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.agent.AgentAPIResult;
import com.alibaba.himarket.dto.result.agent.AgentConfigResult;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.httpapi.APIResult;
import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.himarket.dto.result.mcp.APIGMcpServerResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMcpServerResult;
import com.alibaba.himarket.dto.result.mcp.McpConfigResult;
import com.alibaba.himarket.dto.result.model.AIGWModelAPIResult;
import com.alibaba.himarket.dto.result.model.GatewayModelAPIResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.service.gateway.client.APIGClient;
import com.alibaba.himarket.support.consumer.APIGAuthConfig;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.enums.*;
import com.alibaba.himarket.support.mcp.OpenAPIToolsConfigConverter;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.alibaba.himarket.utils.JsonUtil;
import com.aliyun.sdk.gateway.pop.exception.PopClientException;
import com.aliyun.sdk.service.apig20240327.models.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AIGWOperator extends APIGOperator {

    @Override
    public PageResult<? extends GatewayMcpServerResult> fetchMcpServers(
            Gateway gateway, int page, int size) {
        APIGClient client = getClient(gateway);

        CompletableFuture<ListMcpServersResponse> response =
                client.execute(
                        c ->
                                c.listMcpServers(
                                        ListMcpServersRequest.builder()
                                                .gatewayId(gateway.getGatewayId())
                                                .pageNumber(page)
                                                .pageSize(size)
                                                .build()));

        ListMcpServersResponse result = response.join();
        if (200 != result.getStatusCode()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, result.getBody().getMessage());
        }

        List<APIGMcpServerResult> mcpServers =
                Optional.ofNullable(result.getBody().getData().getItems())
                        .map(
                                items ->
                                        items.stream()
                                                .map(
                                                        item -> {
                                                            APIGMcpServerResult mcpServer =
                                                                    new APIGMcpServerResult();
                                                            mcpServer.setMcpServerId(
                                                                    item.getMcpServerId());
                                                            mcpServer.setMcpServerName(
                                                                    item.getName());
                                                            mcpServer.setApiId(item.getApiId());
                                                            mcpServer.setMcpRouteId(
                                                                    item.getRouteId());
                                                            return mcpServer;
                                                        })
                                                .collect(Collectors.toList()))
                        .orElse(new ArrayList<>());

        return PageResult.of(mcpServers, page, size, result.getBody().getData().getTotalSize());
    }

    @Override
    public GatewayMcpServerResult fetchMcpServer(Gateway gateway, String mcpServerId) {
        GetMcpServerResponseBody.Data resp = getMcpServer(gateway, mcpServerId);
        APIGMcpServerResult result = new APIGMcpServerResult();
        result.setMcpServerId(resp.getMcpServerId());
        result.setMcpServerName(resp.getName());
        result.setMcpRouteId(resp.getRouteId());
        return result;
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        APIGRefConfig config = (APIGRefConfig) conf;
        McpConfigResult mcpConfig = new McpConfigResult();

        GetMcpServerResponseBody.Data resp = getMcpServer(gateway, config.getMcpServerId());

        // mcpServer name
        mcpConfig.setMcpServerName(resp.getName());
        // mcpServer config
        McpConfigResult.McpServerConfig serverConfig = new McpConfigResult.McpServerConfig();

        String path = resp.getMcpServerPath();
        String exposedUriPath = resp.getExposedUriPath();
        boolean protocolCompatible =
                StrUtil.equalsIgnoreCase(resp.getCreateFromType(), "ApiGatewayProxyMcpHosting");
        if (!protocolCompatible && StrUtil.isNotBlank(exposedUriPath)) {
            path += exposedUriPath;
        }
        serverConfig.setPath(path);

        // default domains in gateway
        List<DomainResult> defaultDomains = fetchDefaultDomains(gateway);
        List<DomainResult> mcpDomains =
                Optional.ofNullable(resp.getDomainInfos()).orElse(Collections.emptyList()).stream()
                        .map(
                                d ->
                                        DomainResult.builder()
                                                .domain(d.getName())
                                                .protocol(
                                                        Optional.ofNullable(d.getProtocol())
                                                                .map(String::toLowerCase)
                                                                .orElse(null))
                                                .build())
                        .toList();

        serverConfig.setDomains(
                Stream.concat(mcpDomains.stream(), defaultDomains.stream())
                        .collect(Collectors.toList()));
        mcpConfig.setMcpServerConfig(serverConfig);

        mcpConfig.setProtocol(
                protocolCompatible
                        ? McpProtocolType.DUAL_HTTP
                        : McpProtocolType.fromString(resp.getProtocol()));
        mcpConfig.setFromType(
                !protocolCompatible && StrUtil.equalsIgnoreCase(resp.getProtocol(), "HTTP")
                        ? McpFromType.HTTP_TO_MCP
                        : McpFromType.NATIVE_MCP);

        McpConfigResult.McpMetadata meta = new McpConfigResult.McpMetadata();
        meta.setSource(GatewayType.APIG_AI.name());
        mcpConfig.setMeta(meta);

        // tools
        String tools = resp.getMcpServerConfig();
        if (StrUtil.isNotBlank(tools)) {
            String decodedTools = Base64.isBase64(tools) ? Base64.decodeStr(tools) : tools;
            mcpConfig.setTools(OpenAPIToolsConfigConverter.convertRawConfigToJson(decodedTools));
        }

        return JsonUtil.toJson(mcpConfig);
    }

    private GetMcpServerResponseBody.Data getMcpServer(Gateway gateway, String mcpServerId) {
        APIGClient client = getClient(gateway);
        CompletableFuture<GetMcpServerResponse> f =
                client.execute(
                        c ->
                                c.getMcpServer(
                                        GetMcpServerRequest.builder()
                                                .mcpServerId(mcpServerId)
                                                .build()));

        GetMcpServerResponse response = f.join();
        if (200 != response.getStatusCode()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, response.getBody().getMessage());
        }
        return response.getBody().getData();
    }

    @Override
    public CredentialContext fetchApiCredential(
            Gateway gateway, ProductType productType, ProductRef productRef) {
        // Only mcp server is supported for now
        if (productType != ProductType.MCP_SERVER) {
            return new CredentialContext();
        }

        APIGRefConfig config = productRef.getApigRefConfig();
        if (config == null || StrUtil.isBlank(config.getMcpRouteId())) {
            return new CredentialContext();
        }

        APIGClient client = getClient(gateway);
        CompletableFuture<QueryConsumerAuthorizationRulesResponse> f =
                client.execute(
                        c ->
                                c.queryConsumerAuthorizationRules(
                                        QueryConsumerAuthorizationRulesRequest.builder()
                                                .resourceId(config.getMcpRouteId())
                                                .resourceType(APIGResourceType.MCP.getType())
                                                .pageNumber(1)
                                                .pageSize(1)
                                                .build()));
        QueryConsumerAuthorizationRulesResponse response = f.join();
        if (200 != response.getStatusCode()) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        return Optional.ofNullable(response.getBody())
                .map(QueryConsumerAuthorizationRulesResponseBody::getData)
                .map(QueryConsumerAuthorizationRulesResponseBody.Data::getItems)
                .filter(items -> !items.isEmpty())
                .map(items -> items.get(0).getConsumerId())
                .filter(StrUtil::isNotBlank)
                .map(consumerId -> fetchConsumerCredential(gateway, consumerId))
                .orElseGet(() -> CredentialContext.builder().build());
    }

    @Override
    public PageResult<AgentAPIResult> fetchAgentAPIs(Gateway gateway, int page, int size) {
        PageResult<APIResult> apiResult = fetchAPIs(gateway, APIGAPIType.AGENT, page, size);

        return new PageResult<AgentAPIResult>()
                .mapFrom(
                        apiResult,
                        api ->
                                AgentAPIResult.builder()
                                        .agentApiId(api.getApiId())
                                        .agentApiName(api.getApiName())
                                        .build());
    }

    @Override
    public PageResult<? extends GatewayModelAPIResult> fetchModelAPIs(
            Gateway gateway, int page, int size) {
        PageResult<APIResult> apiResult = fetchAPIs(gateway, APIGAPIType.MODEL, page, size);

        return new PageResult<AIGWModelAPIResult>()
                .mapFrom(
                        apiResult,
                        api ->
                                AIGWModelAPIResult.builder()
                                        .modelApiId(api.getApiId())
                                        .modelApiName(api.getApiName())
                                        .build());
    }

    @Override
    public String fetchAgentConfig(Gateway gateway, Object conf) {
        APIGRefConfig config = (APIGRefConfig) conf;
        AgentConfigResult result = new AgentConfigResult();

        HttpApiApiInfo apiInfo = fetchAPI(gateway, config.getAgentApiId());
        List<DomainResult> apiDomains = extractAPIDomains(apiInfo);

        // Agent API consists of HTTP routes
        PageResult<HttpRoute> httpRoutes = fetchHttpRoutes(gateway, config.getAgentApiId(), 1, 500);

        List<HttpRouteResult> routeResults =
                httpRoutes.getContent().stream()
                        .map(httpRoute -> new HttpRouteResult().convertFrom(httpRoute, apiDomains))
                        .collect(Collectors.toList());

        AgentConfigResult.AgentAPIConfig agentAPIConfig =
                AgentConfigResult.AgentAPIConfig.builder()
                        .agentProtocols(apiInfo.getAgentProtocols())
                        .routes(routeResults)
                        .build();
        result.setAgentAPIConfig(agentAPIConfig);

        // Build metadata at the same level as agentAPIConfig
        AgentConfigResult.AgentMetadata meta =
                AgentConfigResult.AgentMetadata.builder()
                        .source(GatewayType.APIG_AI.name()) // Mark the source as AI gateway
                        .build();
        result.setMeta(meta); // Set metadata on the top-level result

        return JsonUtil.toJson(result);
    }

    @Override
    public String fetchModelConfig(Gateway gateway, Object conf) {
        APIGRefConfig config = (APIGRefConfig) conf;
        ModelConfigResult result = new ModelConfigResult();

        // Fetch http routes
        HttpApiApiInfo apiInfo = fetchAPI(gateway, config.getModelApiId());
        PageResult<HttpRoute> httpRoutes = fetchHttpRoutes(gateway, config.getModelApiId(), 1, 500);

        List<DomainResult> apiDomains = extractAPIDomains(apiInfo);
        // Convert route results
        List<HttpRouteResult> routeResults =
                httpRoutes.getContent().stream()
                        .map(httpRoute -> new HttpRouteResult().convertFrom(httpRoute, apiDomains))
                        .collect(Collectors.toList());

        ModelConfigResult.ModelAPIConfig apiConfig =
                ModelConfigResult.ModelAPIConfig.builder()
                        .aiProtocols(apiInfo.getAiProtocols())
                        .modelCategory(apiInfo.getModelCategory())
                        .routes(routeResults)
                        .build();
        result.setModelAPIConfig(apiConfig);

        return JsonUtil.toJson(result);
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.APIG_AI;
    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(
            Gateway gateway, String consumerId, Object refConfig) {
        APIGClient client = getClient(gateway);

        APIGRefConfig config = (APIGRefConfig) refConfig;

        APIGResourceType resourceType;
        String resourceId;
        if (StrUtil.isNotBlank(config.getMcpRouteId())) {
            resourceType = APIGResourceType.MCP;
            resourceId = config.getMcpRouteId();
        } else if (StrUtil.isNotBlank(config.getAgentApiId())) {
            resourceType = APIGResourceType.Agent;
            resourceId = config.getAgentApiId();
        } else {
            resourceType = APIGResourceType.LLM;
            resourceId = config.getModelApiId();
        }

        try {
            // Confirm the gateway environment ID
            String envId = fetchGatewayEnv(gateway);

            CreateConsumerAuthorizationRulesRequest.AuthorizationRules rule =
                    CreateConsumerAuthorizationRulesRequest.AuthorizationRules.builder()
                            .consumerId(consumerId)
                            .expireMode("LongTerm")
                            .resourceType(resourceType.getType())
                            .resourceIdentifier(
                                    CreateConsumerAuthorizationRulesRequest.ResourceIdentifier
                                            .builder()
                                            .resourceId(resourceId)
                                            .environmentId(envId)
                                            .build())
                            .build();

            CompletableFuture<CreateConsumerAuthorizationRulesResponse> f =
                    client.execute(
                            c -> {
                                CreateConsumerAuthorizationRulesRequest request =
                                        CreateConsumerAuthorizationRulesRequest.builder()
                                                .authorizationRules(Collections.singletonList(rule))
                                                .build();

                                return c.createConsumerAuthorizationRules(request);
                            });
            CreateConsumerAuthorizationRulesResponse response = f.join();
            if (200 != response.getStatusCode()) {
                throw new BusinessException(
                        ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            APIGAuthConfig apigAuthConfig =
                    APIGAuthConfig.builder()
                            .authorizationRuleIds(
                                    response.getBody().getData().getConsumerAuthorizationRuleIds())
                            .build();
            return ConsumerAuthConfig.builder().apigAuthConfig(apigAuthConfig).build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof PopClientException
                    && "Conflict.ConsumerAuthorizationForbidden"
                            .equals(((PopClientException) cause).getErrCode())) {
                return getConsumerAuthorization(
                        gateway, consumerId, resourceType.getType(), resourceId);
            }
            log.error(
                    "Error authorizing consumer {} to {}:{} in AI gateway {}",
                    consumerId,
                    resourceType,
                    resourceId,
                    gateway.getGatewayId(),
                    e);
            throw new BusinessException(
                    ErrorCode.GATEWAY_ERROR,
                    StrUtil.format(
                                    "Failed to authorize consumer to {} in AI gateway: ",
                                    resourceType)
                            + e.getMessage());
        }
    }

    public ConsumerAuthConfig getConsumerAuthorization(
            Gateway gateway, String consumerId, String resourceType, String resourceId) {
        APIGClient client = getClient(gateway);

        CompletableFuture<QueryConsumerAuthorizationRulesResponse> f =
                client.execute(
                        c -> {
                            QueryConsumerAuthorizationRulesRequest request =
                                    QueryConsumerAuthorizationRulesRequest.builder()
                                            .consumerId(consumerId)
                                            .resourceId(resourceId)
                                            .resourceType(resourceType)
                                            .build();

                            return c.queryConsumerAuthorizationRules(request);
                        });
        QueryConsumerAuthorizationRulesResponse response = f.join();

        if (200 != response.getStatusCode()) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        QueryConsumerAuthorizationRulesResponseBody.Items items =
                response.getBody().getData().getItems().get(0);
        APIGAuthConfig apigAuthConfig =
                APIGAuthConfig.builder()
                        .authorizationRuleIds(
                                Collections.singletonList(items.getConsumerAuthorizationRuleId()))
                        .build();

        return ConsumerAuthConfig.builder().apigAuthConfig(apigAuthConfig).build();
    }
}
