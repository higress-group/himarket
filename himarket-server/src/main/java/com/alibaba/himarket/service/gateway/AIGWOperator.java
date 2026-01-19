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
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.agent.AgentAPIResult;
import com.alibaba.himarket.dto.result.agent.AgentConfigResult;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.httpapi.APIResult;
import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.himarket.dto.result.mcp.APIGMCPServerResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMCPServerResult;
import com.alibaba.himarket.dto.result.mcp.MCPConfigResult;
import com.alibaba.himarket.dto.result.mcp.McpServerInfo;
import com.alibaba.himarket.dto.result.model.AIGWModelAPIResult;
import com.alibaba.himarket.dto.result.model.GatewayModelAPIResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.gateway.client.APIGClient;
import com.alibaba.himarket.support.consumer.APIGAuthConfig;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.enums.APIGAPIType;
import com.alibaba.himarket.support.enums.APIGResourceType;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.product.APIGRefConfig;
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

    /**
     * Fetch gateway environment ID
     *
     * @param gateway The gateway
     * @return The environment ID
     */
    public String getGatewayEnvironmentId(Gateway gateway) {
        return fetchGatewayEnv(gateway);
    }

    @Override
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(
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

        List<APIGMCPServerResult> mcpServers =
                Optional.ofNullable(result.getBody().getData().getItems())
                        .map(
                                items ->
                                        items.stream()
                                                .map(
                                                        item -> {
                                                            APIGMCPServerResult mcpServer =
                                                                    new APIGMCPServerResult();
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

    //    @Override
    //    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway gateway, int
    // page, int size) {
    //        PopGatewayClient client = new PopGatewayClient(gateway.getApigConfig());
    //
    //        Map<String, String> queryParams = MapUtil.<String, String>builder()
    //                .put("gatewayId", gateway.getGatewayId())
    //                .put("pageNumber", String.valueOf(page))
    //                .put("pageSize", String.valueOf(size))
    //                .build();
    //
    //        return client.execute("/v1/mcp-servers", MethodType.GET, queryParams, data -> {
    //            List<APIGMCPServerResult> mcpServers =
    // Optional.ofNullable(data.getJSONArray("items"))
    //                    .map(items -> items.stream()
    //                            .map(JSONObject.class::cast)
    //                            .map(json -> {
    //                                APIGMCPServerResult result = new APIGMCPServerResult();
    //                                result.setMcpServerName(json.getStr("name"));
    //                                result.setMcpServerId(json.getStr("mcpServerId"));
    //                                result.setMcpRouteId(json.getStr("routeId"));
    //                                result.setApiId(json.getStr("apiId"));
    //                                return result;
    //                            })
    //                            .collect(Collectors.toList()))
    //                    .orElse(new ArrayList<>());
    //
    //            return PageResult.of(mcpServers, page, size, data.getInt("totalSize"));
    //        });
    //    }

    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers_V1(
            Gateway gateway, int page, int size) {
        PageResult<APIResult> apiPage = fetchAPIs(gateway, APIGAPIType.MCP, 0, 1);
        if (apiPage.getTotalElements() == 0) {
            return PageResult.empty(page, size);
        }

        // MCP Server定义在一个API下
        String apiId = apiPage.getContent().get(0).getApiId();
        try {
            PageResult<HttpRoute> routesPage = fetchHttpRoutes(gateway, apiId, page, size);
            if (routesPage.getTotalElements() == 0) {
                return PageResult.empty(page, size);
            }

            return PageResult.<APIGMCPServerResult>builder()
                    .build()
                    .mapFrom(
                            routesPage,
                            route -> {
                                APIGMCPServerResult r =
                                        new APIGMCPServerResult().convertFrom(route);
                                r.setApiId(apiId);
                                return r;
                            });
        } catch (Exception e) {
            log.error("Error fetching MCP servers", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Error fetching MCP servers，Cause：" + e.getMessage());
        }
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        APIGRefConfig config = (APIGRefConfig) conf;
        MCPConfigResult mcpConfig = new MCPConfigResult();

        McpServerInfo resp = fetchRawMcpServerInfo(gateway, config.getMcpServerId());

        // mcpServer name
        mcpConfig.setMcpServerName(resp.getName());
        // mcpServer config
        MCPConfigResult.MCPServerConfig serverConfig = new MCPConfigResult.MCPServerConfig();

        String path = resp.getMcpServerPath();
        String exposedUriPath = resp.getExposedUriPath();
        if (StrUtil.isNotBlank(exposedUriPath)) {
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

        // meta
        MCPConfigResult.McpMetadata meta = new MCPConfigResult.McpMetadata();
        meta.setSource(GatewayType.APIG_AI.name());
        meta.setProtocol(resp.getProtocol());
        meta.setCreateFromType(resp.getCreateFromType());
        mcpConfig.setMeta(meta);

        // tools
        String tools = resp.getMcpServerConfig();
        if (StrUtil.isNotBlank(tools)) {
            mcpConfig.setTools(Base64.isBase64(tools) ? Base64.decodeStr(tools) : tools);
        }

        return JSONUtil.toJsonStr(mcpConfig);
    }

    public McpServerInfo fetchRawMcpServerInfo(Gateway gateway, String mcpServerId) {
        APIGClient client = getClient(gateway);
        CompletableFuture<GetMcpServerResponse> f =
                client.execute(
                        c -> {
                            GetMcpServerRequest request =
                                    GetMcpServerRequest.builder().mcpServerId(mcpServerId).build();
                            return c.getMcpServer(request);
                        });

        GetMcpServerResponse response = f.join();
        if (200 != response.getStatusCode()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, response.getBody().getMessage());
        }

        GetMcpServerResponseBody.Data data = response.getBody().getData();
        return McpServerInfo.builder()
                .mcpServerId(data.getMcpServerId())
                .name(data.getName())
                .description(data.getDescription())
                .mcpServerPath(data.getMcpServerPath())
                .exposedUriPath(data.getExposedUriPath())
                .domainInfos(
                        Optional.ofNullable(data.getDomainInfos())
                                .map(
                                        list ->
                                                list.stream()
                                                        .map(
                                                                d ->
                                                                        McpServerInfo.DomainInfo
                                                                                .builder()
                                                                                .name(d.getName())
                                                                                .protocol(
                                                                                        d
                                                                                                .getProtocol())
                                                                                .build())
                                                        .collect(Collectors.toList()))
                                .orElse(null))
                .protocol(data.getProtocol())
                .createFromType(data.getCreateFromType())
                .mcpServerConfig(data.getMcpServerConfig())
                .mcpServerConfigPluginAttachmentId(data.getMcpServerConfigPluginAttachmentId())
                .gatewayId(data.getGatewayId())
                .environmentId(data.getEnvironmentId())
                .deployStatus(data.getDeployStatus())
                .routeId(data.getRouteId())
                .type(data.getType())
                .build();
    }

    /**
     * Deploy MCP Server
     *
     * @param gateway The gateway
     * @param mcpServerId The MCP server ID to deploy
     */
    public void deployMcpServer(Gateway gateway, String mcpServerId) {
        APIGClient client = getClient(gateway);
        try {
            CompletableFuture<DeployMcpServerResponse> f =
                    client.execute(
                            c -> {
                                DeployMcpServerRequest request =
                                        DeployMcpServerRequest.builder()
                                                .mcpServerId(mcpServerId)
                                                .build();
                                return c.deployMcpServer(request);
                            });

            DeployMcpServerResponse response = f.join();
            if (200 != response.getStatusCode()) {
                throw new BusinessException(
                        ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }
            log.info("Successfully deployed MCP server: {}", mcpServerId);
        } catch (Exception e) {
            log.error("Error deploying MCP server: {}", mcpServerId, e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Error deploying MCP server，Cause：" + e.getMessage());
        }
    }

    /**
     * Undeploy MCP Server
     *
     * @param gateway The gateway
     * @param mcpServerId The MCP server ID to undeploy
     */
    public void undeployMcpServer(Gateway gateway, String mcpServerId) {
        APIGClient client = getClient(gateway);
        try {
            CompletableFuture<UnDeployMcpServerResponse> f =
                    client.execute(
                            c -> {
                                UnDeployMcpServerRequest request =
                                        UnDeployMcpServerRequest.builder()
                                                .mcpServerId(mcpServerId)
                                                .build();
                                return c.unDeployMcpServer(request);
                            });

            UnDeployMcpServerResponse response = f.join();
            if (200 != response.getStatusCode()) {
                throw new BusinessException(
                        ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }
            log.info("Successfully undeployed MCP server: {}", mcpServerId);
        } catch (Exception e) {
            log.error("Error undeploying MCP server: {}", mcpServerId, e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Error undeploying MCP server，Cause：" + e.getMessage());
        }
    }

    public String findMcpServerPlugin(Gateway gateway) {
        APIGClient client = getClient(gateway);
        CompletableFuture<ListPluginClassesResponse> f =
                client.execute(
                        c -> {
                            ListPluginClassesRequest request =
                                    ListPluginClassesRequest.builder()
                                            .gatewayId(gateway.getGatewayId())
                                            .nameLike("mcp-server")
                                            .source("HigressOfficial")
                                            .gatewayType("AI")
                                            .pageSize(100)
                                            .pageNumber(1)
                                            .build();
                            return c.listPluginClasses(request);
                        });

        ListPluginClassesResponse response = f.join();
        if (200 != response.getStatusCode()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, response.getBody().getMessage());
        }

        return Optional.ofNullable(response.getBody().getData().getItems())
                .flatMap(
                        items ->
                                items.stream()
                                        .filter(
                                                item ->
                                                        "mcp-server"
                                                                .equalsIgnoreCase(item.getName()))
                                        .findFirst()
                                        .map(item -> item.getPluginId()))
                .orElse(null);
    }

    public void listPluginClasses(Gateway gateway) {
        APIGClient client = getClient(gateway);
        CompletableFuture<ListPluginClassesResponse> f =
                client.execute(
                        c -> {
                            ListPluginClassesRequest request =
                                    ListPluginClassesRequest.builder()
                                            .gatewayId(gateway.getGatewayId())
                                            .nameLike("mcp-server")
                                            .source("HigressOfficial")
                                            .gatewayType("AI")
                                            .pageSize(100)
                                            .pageNumber(1)
                                            .build();
                            return c.listPluginClasses(request);
                        });

        ListPluginClassesResponse response = f.join();
        if (200 != response.getStatusCode()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, response.getBody().getMessage());
        }
        response.getBody().getData().getItems();
    }

    @Override
    public String fetchMcpToolsForConfig(Gateway gateway, Object conf) {
        return null;
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

        // 构建元数据（与 agentAPIConfig 同级）
        AgentConfigResult.AgentMetadata meta =
                AgentConfigResult.AgentMetadata.builder()
                        .source(GatewayType.APIG_AI.name()) // 标识来源为 AI 网关
                        .build();
        result.setMeta(meta); // 设置元数据到顶层

        return JSONUtil.toJsonStr(result);
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

        return JSONUtil.toJsonStr(result);
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.APIG_AI;
    }

    public String fetchMcpTools(Gateway gateway, String routeId) {
        APIGClient client = getClient(gateway);

        try {
            CompletableFuture<ListPluginAttachmentsResponse> f =
                    client.execute(
                            c -> {
                                ListPluginAttachmentsRequest request =
                                        ListPluginAttachmentsRequest.builder()
                                                .gatewayId(gateway.getGatewayId())
                                                .attachResourceId(routeId)
                                                .attachResourceType("GatewayRoute")
                                                .pageNumber(1)
                                                .pageSize(100)
                                                .build();

                                return c.listPluginAttachments(request);
                            });

            ListPluginAttachmentsResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(
                        ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            for (ListPluginAttachmentsResponseBody.Items item :
                    response.getBody().getData().getItems()) {
                PluginClassInfo classInfo = item.getPluginClassInfo();

                if (!StrUtil.equalsIgnoreCase(classInfo.getName(), "mcp-server")) {
                    continue;
                }

                String pluginConfig = item.getPluginConfig();
                if (StrUtil.isNotBlank(pluginConfig)) {
                    return Base64.decodeStr(pluginConfig);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching Plugin Attachment", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Error fetching Plugin Attachment，Cause：" + e.getMessage());
        }
        return null;
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
            // 确认Gateway的EnvId
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
