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
import java.util.Set;
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

        /**
         * Get domain IDs for specified domains
         *
         * @param gateway            The gateway
         * @param publishedDomainSet The set of domains to find IDs for
         * @return List of domain IDs
         */
        public List<String> getDomainIds(Gateway gateway, Set<String> publishedDomainSet) {
                List<DomainResult> gatewayDomains = getGatewayDomains(gateway);
                return gatewayDomains.stream()
                                .filter(d -> publishedDomainSet.contains(d.getDomain()))
                                .map(d -> d.getMeta().get("domainId"))
                                .toList();
        }

        @Override
        public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(
                        Gateway gateway, int page, int size) {
                APIGClient client = getClient(gateway);

                CompletableFuture<ListMcpServersResponse> response = client.execute(
                                c -> c.listMcpServers(
                                                ListMcpServersRequest.builder()
                                                                .gatewayId(gateway.getGatewayId())
                                                                .pageNumber(page)
                                                                .pageSize(size)
                                                                .build()));

                ListMcpServersResponse result = response.join();
                if (200 != result.getStatusCode()) {
                        throw new BusinessException(ErrorCode.INTERNAL_ERROR, result.getBody().getMessage());
                }

                List<APIGMCPServerResult> mcpServers = Optional.ofNullable(result.getBody().getData().getItems())
                                .map(
                                                items -> items.stream()
                                                                .map(
                                                                                item -> {
                                                                                        APIGMCPServerResult mcpServer = new APIGMCPServerResult();
                                                                                        mcpServer.setMcpServerId(
                                                                                                        item.getMcpServerId());
                                                                                        mcpServer.setMcpServerName(
                                                                                                        item.getName());
                                                                                        mcpServer.setApiId(item
                                                                                                        .getApiId());
                                                                                        mcpServer.setMcpRouteId(
                                                                                                        item.getRouteId());
                                                                                        return mcpServer;
                                                                                })
                                                                .collect(Collectors.toList()))
                                .orElse(new ArrayList<>());

                return PageResult.of(mcpServers, page, size, result.getBody().getData().getTotalSize());
        }

        // @Override
        // public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway
        // gateway, int
        // page, int size) {
        // PopGatewayClient client = new PopGatewayClient(gateway.getApigConfig());
        //
        // Map<String, String> queryParams = MapUtil.<String, String>builder()
        // .put("gatewayId", gateway.getGatewayId())
        // .put("pageNumber", String.valueOf(page))
        // .put("pageSize", String.valueOf(size))
        // .build();
        //
        // return client.execute("/v1/mcp-servers", MethodType.GET, queryParams, data ->
        // {
        // List<APIGMCPServerResult> mcpServers =
        // Optional.ofNullable(data.getJSONArray("items"))
        // .map(items -> items.stream()
        // .map(JSONObject.class::cast)
        // .map(json -> {
        // APIGMCPServerResult result = new APIGMCPServerResult();
        // result.setMcpServerName(json.getStr("name"));
        // result.setMcpServerId(json.getStr("mcpServerId"));
        // result.setMcpRouteId(json.getStr("routeId"));
        // result.setApiId(json.getStr("apiId"));
        // return result;
        // })
        // .collect(Collectors.toList()))
        // .orElse(new ArrayList<>());
        //
        // return PageResult.of(mcpServers, page, size, data.getInt("totalSize"));
        // });
        // }

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
                                                                APIGMCPServerResult r = new APIGMCPServerResult()
                                                                                .convertFrom(route);
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
                List<DomainResult> mcpDomains = Optional.ofNullable(resp.getDomainInfos())
                                .orElse(Collections.emptyList()).stream()
                                .map(
                                                d -> DomainResult.builder()
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
                CompletableFuture<GetMcpServerResponse> f = client.execute(
                                c -> {
                                        GetMcpServerRequest request = GetMcpServerRequest.builder()
                                                        .mcpServerId(mcpServerId).build();
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
                                                                                list -> list.stream()
                                                                                                .map(
                                                                                                                d -> McpServerInfo.DomainInfo
                                                                                                                                .builder()
                                                                                                                                .name(d.getName())
                                                                                                                                .protocol(
                                                                                                                                                d
                                                                                                                                                                .getProtocol())
                                                                                                                                .build())
                                                                                                .collect(Collectors
                                                                                                                .toList()))
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
         * @param gateway     The gateway
         * @param mcpServerId The MCP server ID to deploy
         */
        public void deployMcpServer(Gateway gateway, String mcpServerId) {
                APIGClient client = getClient(gateway);
                try {
                        CompletableFuture<DeployMcpServerResponse> f = client.execute(
                                        c -> {
                                                DeployMcpServerRequest request = DeployMcpServerRequest.builder()
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
         * @param gateway     The gateway
         * @param mcpServerId The MCP server ID to undeploy
         */
        public void undeployMcpServer(Gateway gateway, String mcpServerId) {
                APIGClient client = getClient(gateway);
                try {
                        CompletableFuture<UnDeployMcpServerResponse> f = client.execute(
                                        c -> {
                                                UnDeployMcpServerRequest request = UnDeployMcpServerRequest.builder()
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
                CompletableFuture<ListPluginClassesResponse> f = client.execute(
                                c -> {
                                        ListPluginClassesRequest request = ListPluginClassesRequest.builder()
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
                                                items -> items.stream()
                                                                .filter(
                                                                                item -> "mcp-server"
                                                                                                .equalsIgnoreCase(item
                                                                                                                .getName()))
                                                                .findFirst()
                                                                .map(item -> item.getPluginId()))
                                .orElse(null);
        }

        public void listPluginClasses(Gateway gateway) {
                APIGClient client = getClient(gateway);
                CompletableFuture<ListPluginClassesResponse> f = client.execute(
                                c -> {
                                        ListPluginClassesRequest request = ListPluginClassesRequest.builder()
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
                                                api -> AgentAPIResult.builder()
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
                                                api -> AIGWModelAPIResult.builder()
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

                List<HttpRouteResult> routeResults = httpRoutes.getContent().stream()
                                .map(httpRoute -> new HttpRouteResult().convertFrom(httpRoute, apiDomains))
                                .collect(Collectors.toList());

                AgentConfigResult.AgentAPIConfig agentAPIConfig = AgentConfigResult.AgentAPIConfig.builder()
                                .agentProtocols(apiInfo.getAgentProtocols())
                                .routes(routeResults)
                                .build();
                result.setAgentAPIConfig(agentAPIConfig);

                // 构建元数据（与 agentAPIConfig 同级）
                AgentConfigResult.AgentMetadata meta = AgentConfigResult.AgentMetadata.builder()
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
                List<HttpRouteResult> routeResults = httpRoutes.getContent().stream()
                                .map(httpRoute -> new HttpRouteResult().convertFrom(httpRoute, apiDomains))
                                .collect(Collectors.toList());

                ModelConfigResult.ModelAPIConfig apiConfig = ModelConfigResult.ModelAPIConfig.builder()
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
                        CompletableFuture<ListPluginAttachmentsResponse> f = client.execute(
                                        c -> {
                                                ListPluginAttachmentsRequest request = ListPluginAttachmentsRequest
                                                                .builder()
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

                        for (ListPluginAttachmentsResponseBody.Items item : response.getBody().getData().getItems()) {
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

                        CreateConsumerAuthorizationRulesRequest.AuthorizationRules rule = CreateConsumerAuthorizationRulesRequest.AuthorizationRules
                                        .builder()
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

                        CompletableFuture<CreateConsumerAuthorizationRulesResponse> f = client.execute(
                                        c -> {
                                                CreateConsumerAuthorizationRulesRequest request = CreateConsumerAuthorizationRulesRequest
                                                                .builder()
                                                                .authorizationRules(Collections.singletonList(rule))
                                                                .build();

                                                return c.createConsumerAuthorizationRules(request);
                                        });
                        CreateConsumerAuthorizationRulesResponse response = f.join();
                        if (200 != response.getStatusCode()) {
                                throw new BusinessException(
                                                ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
                        }

                        APIGAuthConfig apigAuthConfig = APIGAuthConfig.builder()
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

                CompletableFuture<QueryConsumerAuthorizationRulesResponse> f = client.execute(
                                c -> {
                                        QueryConsumerAuthorizationRulesRequest request = QueryConsumerAuthorizationRulesRequest
                                                        .builder()
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

                QueryConsumerAuthorizationRulesResponseBody.Items items = response.getBody().getData().getItems()
                                .get(0);
                APIGAuthConfig apigAuthConfig = APIGAuthConfig.builder()
                                .authorizationRuleIds(
                                                Collections.singletonList(items.getConsumerAuthorizationRuleId()))
                                .build();

                return ConsumerAuthConfig.builder().apigAuthConfig(apigAuthConfig).build();
        }

        /**
         * Create HTTP API for Model API
         *
         * @param gateway The gateway
         * @param request The CreateHttpApi request
         * @return The HTTP API ID
         */
        public String createHttpApi(Gateway gateway, CreateHttpApiRequest request) {
                APIGClient client = getClient(gateway);
                try {
                        // Log the request for debugging
                        log.info(
                                        "Creating HTTP API with request: name={}, type={}, gatewayId={}",
                                        request.getName(),
                                        request.getType(),
                                        request.getDeployConfigs() != null && !request.getDeployConfigs().isEmpty()
                                                        ? request.getDeployConfigs().get(0).getGatewayId()
                                                        : "N/A");

                        CompletableFuture<CreateHttpApiResponse> f = client.execute(c -> c.createHttpApi(request));

                        CreateHttpApiResponse response = f.join();

                        // Log the response for debugging
                        log.info(
                                        "CreateHttpApi response: statusCode={}, apiId={}",
                                        response.getStatusCode(),
                                        response.getBody() != null && response.getBody().getData() != null
                                                        ? response.getBody().getData().getHttpApiId()
                                                        : "N/A");

                        if (response.getStatusCode() != 200) {
                                throw new BusinessException(
                                                ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
                        }

                        return response.getBody().getData().getHttpApiId();
                } catch (Exception e) {
                        log.error("Error creating HTTP API: {}", request.getName(), e);
                        throw new BusinessException(
                                        ErrorCode.INTERNAL_ERROR, "Error creating HTTP API，Cause：" + e.getMessage());
                }
        }

        /**
         * Find HTTP API ID by name
         *
         * @param gateway The gateway to search
         * @param apiName The HTTP API name to find
         * @param type    The HTTP API type (e.g., "LLM", "Agent")
         * @return Optional containing the HTTP API ID if found, empty otherwise
         */
        public Optional<String> findHttpApiIdByName(Gateway gateway, String apiName, String type) {
                APIGClient client = getClient(gateway);
                try {
                        // Fetch all HTTP APIs of specified type and find the one matching the name
                        CompletableFuture<ListHttpApisResponse> f = client.execute(
                                        c -> {
                                                ListHttpApisRequest request = ListHttpApisRequest.builder()
                                                                .gatewayId(gateway.getGatewayId())
                                                                .gatewayType(gateway.getGatewayType().getType())
                                                                .types(type)
                                                                .name(apiName)
                                                                .pageNumber(1)
                                                                .pageSize(100)
                                                                .build();
                                                return c.listHttpApis(request);
                                        });

                        ListHttpApisResponse response = f.join();
                        if (200 != response.getStatusCode()) {
                                log.warn("Failed to find HTTP API by name: {}", response.getBody().getMessage());
                                return Optional.empty();
                        }

                        // Return the first matching API's ID
                        return Optional.ofNullable(response.getBody().getData().getItems())
                                        .flatMap(
                                                        items -> items.stream()
                                                                        .flatMap(item -> item.getVersionedHttpApis()
                                                                                        .stream())
                                                                        .filter(api -> apiName.equals(api.getName()))
                                                                        .findFirst())
                                        .map(api -> api.getHttpApiId());
                } catch (Exception e) {
                        log.warn("Error finding HTTP API ID by name: {}", apiName, e);
                        return Optional.empty();
                }
        }

        /**
         * Update HTTP API for Model API
         *
         * @param gateway The gateway
         * @param request The UpdateHttpApi request
         */
        public void updateHttpApi(
                        Gateway gateway,
                        com.aliyun.sdk.service.apig20240327.models.UpdateHttpApiRequest request) {
                APIGClient client = getClient(gateway);
                try {
                        // Log the request for debugging
                        log.info(
                                        "Updating HTTP API with request: httpApiId={}, basePath={}",
                                        request.getHttpApiId(),
                                        request.getBasePath());

                        CompletableFuture<com.aliyun.sdk.service.apig20240327.models.UpdateHttpApiResponse> f = client
                                        .execute(c -> c.updateHttpApi(request));

                        com.aliyun.sdk.service.apig20240327.models.UpdateHttpApiResponse response = f.join();

                        // Log the response for debugging
                        log.info(
                                        "UpdateHttpApi response: statusCode={}, message={}",
                                        response.getStatusCode(),
                                        response.getBody() != null ? response.getBody().getMessage() : "N/A");

                        if (response.getStatusCode() != 200) {
                                throw new BusinessException(
                                                ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
                        }

                        log.info("Successfully updated HTTP API: httpApiId={}", request.getHttpApiId());
                } catch (Exception e) {
                        log.error("Error updating HTTP API: {}", request.getHttpApiId(), e);
                        throw new BusinessException(
                                        ErrorCode.INTERNAL_ERROR, "Error updating HTTP API，Cause：" + e.getMessage());
                }
        }

        /**
         * Create HTTP API Route
         *
         * @param gateway       The gateway
         * @param httpApiId     The HTTP API ID
         * @param routeName     The route name
         * @param environmentId The environment ID
         * @param domainIds     List of domain IDs
         * @param match         The route match configuration
         * @param serviceId     The backend service ID
         */
        public void createHttpApiRoute(
                        Gateway gateway,
                        String httpApiId,
                        String routeName,
                        String environmentId,
                        List<String> domainIds,
                        com.aliyun.sdk.service.apig20240327.models.HttpRouteMatch match,
                        String serviceId) {
                APIGClient client = getClient(gateway);
                try {
                        // Build CreateHttpApiRoute request
                        // Note: BackendConfig structure follows the example request format
                        com.aliyun.sdk.service.apig20240327.models.CreateHttpApiRouteRequest request = com.aliyun.sdk.service.apig20240327.models.CreateHttpApiRouteRequest
                                        .builder()
                                        .httpApiId(httpApiId)
                                        .name(routeName)
                                        .environmentId(environmentId)
                                        .domainIds(domainIds)
                                        .match(match)
                                        .build();

                        log.info(
                                        "Creating HTTP API Route: httpApiId={}, routeName={}, path={}, serviceId={}",
                                        httpApiId,
                                        routeName,
                                        match.getPath() != null ? match.getPath().getValue() : "N/A",
                                        serviceId);

                        CompletableFuture<com.aliyun.sdk.service.apig20240327.models.CreateHttpApiRouteResponse> f = client
                                        .execute(c -> c.createHttpApiRoute(request));

                        com.aliyun.sdk.service.apig20240327.models.CreateHttpApiRouteResponse response = f.join();

                        if (response.getStatusCode() != 200) {
                                throw new BusinessException(
                                                ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
                        }

                        String routeId = response.getBody() != null && response.getBody().getData() != null
                                        ? response.getBody().getData().getRouteId()
                                        : "N/A";
                        log.info(
                                        "Successfully created HTTP API Route: httpApiId={}, routeName={}, routeId={}",
                                        httpApiId,
                                        routeName,
                                        routeId);
                } catch (Exception e) {
                        log.error(
                                        "Error creating HTTP API Route: httpApiId={}, routeName={}, error: {}",
                                        httpApiId,
                                        routeName,
                                        e.getMessage(),
                                        e);
                        throw new BusinessException(
                                        ErrorCode.INTERNAL_ERROR,
                                        "Error creating HTTP API Route，Cause：" + e.getMessage());
                }
        }

        /**
         * Create Service in the gateway
         *
         * @param gateway The gateway
         * @param request The CreateService request
         * @return The Service ID
         */
        public String createService(Gateway gateway, CreateServiceRequest request) {
                APIGClient client = getClient(gateway);
                try {
                        log.info(
                                        "Creating Service with request: name={}, sourceType={}, gatewayId={}",
                                        request.getServiceConfigs() != null && !request.getServiceConfigs().isEmpty()
                                                        ? request.getServiceConfigs().get(0).getName()
                                                        : "N/A",
                                        request.getSourceType(),
                                        request.getGatewayId());

                        CompletableFuture<CreateServiceResponse> f = client.execute(c -> c.createService(request));

                        CreateServiceResponse response = f.join();

                        // Log the response for debugging
                        log.info("CreateService response: statusCode={}", response.getStatusCode());

                        if (response.getStatusCode() != 200) {
                                throw new BusinessException(
                                                ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
                        }

                        // Extract service ID from response
                        // The SDK response structure should follow the pattern:
                        // response.getBody().getData().getServiceId()
                        // If the actual SDK structure differs, this will need to be adjusted
                        if (response.getBody() != null && response.getBody().getData() != null) {
                                // Try to extract serviceId using reflection
                                // This is a workaround until we can verify the actual SDK response structure
                                Object data = response.getBody().getData();
                                try {
                                        java.lang.reflect.Method getServiceIdMethod = data.getClass()
                                                        .getMethod("getServiceId");
                                        Object serviceId = getServiceIdMethod.invoke(data);
                                        if (serviceId != null) {
                                                log.info("Successfully extracted serviceId: {}", serviceId);
                                                return serviceId.toString();
                                        }
                                } catch (NoSuchMethodException e) {
                                        // If getServiceId() doesn't exist, try getId()
                                        try {
                                                java.lang.reflect.Method getIdMethod = data.getClass()
                                                                .getMethod("getId");
                                                Object id = getIdMethod.invoke(data);
                                                if (id != null) {
                                                        log.info("Successfully extracted serviceId using getId(): {}",
                                                                        id);
                                                        return id.toString();
                                                }
                                        } catch (Exception ex) {
                                                log.error(
                                                                "Failed to extract serviceId from CreateService response. "
                                                                                + "Response data type: {}, available methods: {}",
                                                                data.getClass().getName(),
                                                                java.util.Arrays.toString(
                                                                                data.getClass().getMethods()));
                                        }
                                } catch (Exception e) {
                                        log.error(
                                                        "Error extracting serviceId from CreateService response: {}",
                                                        e.getMessage(),
                                                        e);
                                }
                        }

                        // If we can't extract serviceId, throw an exception
                        throw new BusinessException(
                                        ErrorCode.INTERNAL_ERROR,
                                        "Unable to extract service ID from CreateService response. Please verify the"
                                                        + " SDK response structure and update the code accordingly.");
                } catch (Exception e) {
                        log.error("Error creating Service", e);
                        throw new BusinessException(
                                        ErrorCode.INTERNAL_ERROR, "Error creating Service，Cause：" + e.getMessage());
                }
        }

        /**
         * Find Service ID by name
         *
         * @param gateway     The gateway to search
         * @param serviceName The service name to find
         * @return Optional containing the service ID if found, empty otherwise
         */
        public Optional<String> findServiceIdByName(Gateway gateway, String serviceName) {
                APIGClient client = getClient(gateway);
                try {
                        // Fetch all services and find the one matching the name
                        CompletableFuture<ListServicesResponse> f = client.execute(
                                        c -> {
                                                ListServicesRequest request = ListServicesRequest.builder()
                                                                .gatewayId(gateway.getGatewayId())
                                                                .pageNumber(1)
                                                                .pageSize(100)
                                                                .build();
                                                return c.listServices(request);
                                        });

                        ListServicesResponse response = f.join();
                        if (200 != response.getStatusCode()) {
                                log.warn("Failed to find service by name: {}", response.getBody().getMessage());
                                return Optional.empty();
                        }

                        // Return the first matching service's ID
                        return Optional.ofNullable(response.getBody().getData().getItems())
                                        .flatMap(
                                                        items -> items.stream()
                                                                        .filter(item -> serviceName
                                                                                        .equals(item.getName()))
                                                                        .findFirst())
                                        .map(item -> item.getServiceId());
                } catch (Exception e) {
                        log.warn("Error finding service ID by name: {}", serviceName, e);
                        return Optional.empty();
                }
        }

        /**
         * Update Service in the gateway
         *
         * @param gateway The gateway
         * @param request The UpdateService request
         */
        public void updateService(Gateway gateway, UpdateServiceRequest request) {
                APIGClient client = getClient(gateway);
                try {
                        log.info("Updating Service with request: serviceId={}", request.getServiceId());

                        CompletableFuture<UpdateServiceResponse> f = client.execute(c -> c.updateService(request));

                        UpdateServiceResponse response = f.join();

                        log.info("UpdateService response: statusCode={}", response.getStatusCode());

                        if (response.getStatusCode() != 200) {
                                throw new BusinessException(
                                                ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
                        }

                        log.info("Successfully updated Service: serviceId={}", request.getServiceId());
                } catch (Exception e) {
                        log.error("Error updating Service: serviceId={}", request.getServiceId(), e);
                        throw new BusinessException(
                                        ErrorCode.INTERNAL_ERROR, "Error updating Service，Cause：" + e.getMessage());
                }
        }
}
