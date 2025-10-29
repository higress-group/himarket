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

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.dto.result.GatewayMCPServerResult;
import com.alibaba.apiopenplatform.dto.result.*;
import com.alibaba.apiopenplatform.dto.result.httpapi.DomainResult;
import com.alibaba.apiopenplatform.entity.Gateway;
import com.alibaba.apiopenplatform.service.gateway.client.APIGClient;
import com.alibaba.apiopenplatform.service.gateway.client.PopGatewayClient;
import com.alibaba.apiopenplatform.service.gateway.client.SLSClient;
import com.alibaba.apiopenplatform.support.consumer.APIGAuthConfig;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.enums.APIGAPIType;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.product.APIGRefConfig;
import com.aliyuncs.http.MethodType;
import com.aliyun.sdk.gateway.pop.exception.PopClientException;
import com.aliyun.sdk.service.apig20240327.models.*;
import com.aliyun.sdk.service.sls20201230.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class AIGatewayOperator extends APIGOperator {

    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers2(Gateway gateway, int page, int size) {
        APIGClient client = getClient(gateway);

        CompletableFuture<ListMcpServersResponse> response = client.execute(c ->
                c.listMcpServers(ListMcpServersRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .pageNumber(page)
                        .pageSize(size)
                        .build())
        );

        ListMcpServersResponse result = response.join();
        if (200 != result.getStatusCode()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, result.getBody().getMessage());
        }

        List<APIGMCPServerResult> mcpServers = Optional.ofNullable(result.getBody().getData().getItems())
                .map(items -> items.stream()
                        .map(item -> {
                            APIGMCPServerResult mcpServer = new APIGMCPServerResult();
                            mcpServer.setMcpServerId(item.getMcpServerId());
                            mcpServer.setMcpServerName(item.getName());
//                            mcpServer.setApiId(item.getApiId());
                            mcpServer.setMcpRouteId(item.getRouteId());
                            return mcpServer;
                        })
                        .collect(Collectors.toList()))
                .orElse(new ArrayList<>());

        return PageResult.of(mcpServers, page, size, result.getBody().getData().getTotalSize());
    }

    @Override
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway gateway, int page, int size) {
        PopGatewayClient client = new PopGatewayClient(gateway.getApigConfig());

        Map<String, String> queryParams = MapUtil.<String, String>builder()
                .put("gatewayId", gateway.getGatewayId())
                .put("pageNumber", String.valueOf(page))
                .put("pageSize", String.valueOf(size))
                .build();

        return client.execute("/v1/mcp-servers", MethodType.GET, queryParams, data -> {
            List<APIGMCPServerResult> mcpServers = Optional.ofNullable(data.getJSONArray("items"))
                    .map(items -> items.stream()
                            .map(JSONObject.class::cast)
                            .map(json -> {
                                APIGMCPServerResult result = new APIGMCPServerResult();
                                result.setMcpServerName(json.getStr("name"));
                                result.setMcpServerId(json.getStr("mcpServerId"));
                                result.setMcpRouteId(json.getStr("routeId"));
                                result.setApiId(json.getStr("apiId"));
                                return result;
                            })
                            .collect(Collectors.toList()))
                    .orElse(new ArrayList<>());

            return PageResult.of(mcpServers, page, size, data.getInt("totalSize"));
        });
    }

    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers_V1(Gateway gateway, int page, int size) {
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

            return PageResult.<APIGMCPServerResult>builder().build()
                    .mapFrom(routesPage, route -> {
                        APIGMCPServerResult r = new APIGMCPServerResult().convertFrom(route);
                        r.setApiId(apiId);
                        return r;
                    });
        } catch (Exception e) {
            log.error("Error fetching MCP servers", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching MCP servers，Cause：" + e.getMessage());
        }
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        APIGRefConfig config = (APIGRefConfig) conf;
        APIGClient client = getClient(gateway);
        MCPConfigResult mcpConfig = new MCPConfigResult();

        CompletableFuture<GetMcpServerResponse> f = client.execute(c -> {
            GetMcpServerRequest request = GetMcpServerRequest.builder()
                    .mcpServerId(config.getMcpServerId())
                    .build();
            return c.getMcpServer(request);
        });

        GetMcpServerResponse response = f.join();
        if (200 != response.getStatusCode()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, response.getBody().getMessage());
        }
        GetMcpServerResponseBody.Data resp = response.getBody().getData();

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

        // domains
        // default domains in gateway
        List<DomainResult> defaultDomains = fetchDefaultDomains(gateway);
        List<DomainResult> mcpDomains = Optional.ofNullable(resp.getDomainInfos())
                .orElse(Collections.emptyList())
                .stream()
                .map(d -> DomainResult.builder()
                        .domain(d.getName())
                        .protocol(Optional.ofNullable(d.getProtocol())
                                .map(String::toLowerCase)
                                .orElse(null))
                        .build())
                .collect(Collectors.toList());

        serverConfig.setDomains(
                Stream.concat(mcpDomains.stream(), defaultDomains.stream())
                        .collect(Collectors.toList())
        );
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
            mcpConfig.setTools(Base64.decodeStr(tools));
        }

        return JSONUtil.toJsonStr(mcpConfig);
    }

//    @Override
//    public String fetchMcpConfig(Gateway gateway, Object conf) {
//        APIGRefConfig config = (APIGRefConfig) conf;
//        PopGatewayClient client = new PopGatewayClient(gateway.getApigConfig());
//        String mcpServerId = config.getMcpServerId();
//        MCPConfigResult mcpConfig = new MCPConfigResult();
//
//        return client.execute("/v1/mcp-servers/" + mcpServerId, MethodType.GET, null, data -> {
//            mcpConfig.setMcpServerName(data.getStr("name"));
//
//            // mcpServer config
//            MCPConfigResult.MCPServerConfig serverConfig = new MCPConfigResult.MCPServerConfig();
//            String path = data.getStr("mcpServerPath");
//            String exposedUriPath = data.getStr("exposedUriPath");
//            if (StrUtil.isNotBlank(exposedUriPath)) {
//                path += exposedUriPath;
//            }
//            serverConfig.setPath(path);
//
//            JSONArray domains = data.getJSONArray("domainInfos");
//            if (domains != null && !domains.isEmpty()) {
//                serverConfig.setDomains(domains.stream()
//                        .map(JSONObject.class::cast)
//                        .map(json -> MCPConfigResult.Domain.builder()
//                                .domain(json.getStr("name"))
//                                .protocol(Optional.ofNullable(json.getStr("protocol"))
//                                        .map(String::toLowerCase)
//                                        .orElse(null))
//                                .build())
//                        .collect(Collectors.toList()));
//            }
//            mcpConfig.setMcpServerConfig(serverConfig);
//
//            // meta
//            MCPConfigResult.McpMetadata meta = new MCPConfigResult.McpMetadata();
//            meta.setSource(GatewayType.APIG_AI.name());
//            meta.setProtocol(data.getStr("protocol"));
//            meta.setCreateFromType(data.getStr("createFromType"));
//            mcpConfig.setMeta(meta);
//
//            // tools
//            String tools = data.getStr("mcpServerConfig");
//            if (StrUtil.isNotBlank(tools)) {
//                mcpConfig.setTools(Base64.decodeStr(tools));
//            }
//
//            return JSONUtil.toJsonStr(mcpConfig);
//        });
//    }

    public String fetchMcpConfig_V1(Gateway gateway, Object conf) {
        APIGRefConfig config = (APIGRefConfig) conf;
        HttpRoute httpRoute = fetchHTTPRoute(gateway, config.getApiId(), config.getMcpRouteId());

        MCPConfigResult m = new MCPConfigResult();
        m.setMcpServerName(httpRoute.getName());

        // mcpServer config
        MCPConfigResult.MCPServerConfig c = new MCPConfigResult.MCPServerConfig();
        if (httpRoute.getMatch() != null) {
            c.setPath(httpRoute.getMatch().getPath().getValue());
        }
        if (httpRoute.getDomainInfos() != null) {
            c.setDomains(httpRoute.getDomainInfos().stream()
                    .map(domainInfo -> DomainResult.builder()
                            .domain(domainInfo.getName())
                            .protocol(Optional.ofNullable(domainInfo.getProtocol())
                                    .map(String::toLowerCase)
                                    .orElse(null))
                            .build())
                    .collect(Collectors.toList()));
        }
        m.setMcpServerConfig(c);

        // meta
        MCPConfigResult.McpMetadata meta = new MCPConfigResult.McpMetadata();
        meta.setSource(GatewayType.APIG_AI.name());

        // tools
        HttpRoute.McpServerInfo mcpServerInfo = httpRoute.getMcpServerInfo();
        boolean fetchTool = true;
        if (mcpServerInfo.getMcpRouteConfig() != null) {
            String protocol = mcpServerInfo.getMcpRouteConfig().getProtocol();
            meta.setCreateFromType(protocol);

            // HTTP转MCP需从插件获取tools配置
            fetchTool = StrUtil.equalsIgnoreCase(protocol, "HTTP");
        }

        if (fetchTool) {
            String toolSpec = fetchMcpTools(gateway, config.getMcpRouteId());
            if (StrUtil.isNotBlank(toolSpec)) {
                m.setTools(toolSpec);
                // 默认为HTTP转MCP
                if (StrUtil.isBlank(meta.getCreateFromType())) {
                    meta.setCreateFromType("HTTP");
                }
            }
        }

        m.setMeta(meta);
        return JSONUtil.toJsonStr(m);
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.APIG_AI;
    }

    @Override
    public String getDashboard(Gateway gateway, String type) {
        SLSClient ticketClient = new SLSClient(gateway.getApigConfig(), true);
        String ticket = null;
        try {
            CreateTicketResponse response = ticketClient.execute(c -> {
                CreateTicketRequest request = CreateTicketRequest.builder().build();
                try {
                    return c.createTicket(request).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
            ticket = response.getBody().getTicket();
        } catch (Exception e) {
            log.error("Error fetching API", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching createTicket API,Cause:" + e.getMessage());
        }
        SLSClient client = new SLSClient(gateway.getApigConfig(), false);
        String projectName = null;
        try {
            ListProjectResponse response = client.execute(c -> {
                ListProjectRequest request = ListProjectRequest.builder().projectName("product").build();
                try {
                    return c.listProject(request).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
            projectName = response.getBody().getProjects().get(0).getProjectName();
        } catch (Exception e) {
            log.error("Error fetching Project", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching Project,Cause:" + e.getMessage());
        }
        String region = gateway.getApigConfig().getRegion();
        String gatewayId = gateway.getGatewayId();
        String dashboardId = "";
        String gatewayFilter = "";
        if (type.equals("Portal")) {
            dashboardId = "dashboard-1758009692051-393998";
            gatewayFilter = "";
        } else if (type.equals("MCP")) {
            dashboardId = "dashboard-1757483808537-433375";
            gatewayFilter = "filters=cluster_id%%253A%%2520" + gatewayId;
        } else if (type.equals("API")) {
            dashboardId = "dashboard-1756276497392-966932";
            gatewayFilter = "filters=cluster_id%%253A%%2520" + gatewayId;
            ;
        }
        String dashboardUrl = String.format("https://sls.console.aliyun.com/lognext/project/%s/dashboard/%s?%s&slsRegion=%s&sls_ticket=%s&isShare=true&hideTopbar=true&hideSidebar=true&ignoreTabLocalStorage=true", projectName, dashboardId, gatewayFilter, region, ticket);
        log.info("Dashboard URL: {}", dashboardUrl);
        return dashboardUrl;
    }

    public String fetchMcpTools(Gateway gateway, String routeId) {
        APIGClient client = getClient(gateway);

        try {
            CompletableFuture<ListPluginAttachmentsResponse> f = client.execute(c -> {
                ListPluginAttachmentsRequest request = ListPluginAttachmentsRequest.builder()
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
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
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
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching Plugin Attachment，Cause：" + e.getMessage());
        }
        return null;
    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(Gateway gateway, String consumerId, Object refConfig) {
        APIGClient client = getClient(gateway);

        APIGRefConfig config = (APIGRefConfig) refConfig;
        // MCP Server 授权
        String mcpRouteId = config.getMcpRouteId();

        try {
            // 确认Gateway的EnvId
            String envId = fetchGatewayEnv(gateway);

            CreateConsumerAuthorizationRulesRequest.AuthorizationRules rule = CreateConsumerAuthorizationRulesRequest.AuthorizationRules.builder()
                    .consumerId(consumerId)
                    .expireMode("LongTerm")
                    .resourceType("MCP")
                    .resourceIdentifier(CreateConsumerAuthorizationRulesRequest.ResourceIdentifier.builder()
                            .resourceId(mcpRouteId)
                            .environmentId(envId).build())
                    .build();

            CompletableFuture<CreateConsumerAuthorizationRulesResponse> f = client.execute(c -> {
                        CreateConsumerAuthorizationRulesRequest request = CreateConsumerAuthorizationRulesRequest.builder()
                                .authorizationRules(Collections.singletonList(rule))
                                .build();

                        return c.createConsumerAuthorizationRules(request);
                    }
            );
            CreateConsumerAuthorizationRulesResponse response = f.join();
            if (200 != response.getStatusCode()) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            APIGAuthConfig apigAuthConfig = APIGAuthConfig.builder()
                    .authorizationRuleIds(response.getBody().getData().getConsumerAuthorizationRuleIds())
                    .build();
            return ConsumerAuthConfig.builder()
                    .apigAuthConfig(apigAuthConfig)
                    .build();
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof PopClientException
                    && "Conflict.ConsumerAuthorizationForbidden".equals(((PopClientException) cause).getErrCode())) {
                return getConsumerAuthorization(gateway, consumerId, mcpRouteId);
            }
            log.error("Error authorizing consumer {} to mcp server {} in AI gateway {}", consumerId, mcpRouteId, gateway.getGatewayId(), e);
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Failed to authorize consumer to mcp server in AI gateway: " + e.getMessage());
        }
    }

    public ConsumerAuthConfig getConsumerAuthorization(Gateway gateway, String consumerId, String resourceId) {
        APIGClient client = getClient(gateway);

        CompletableFuture<QueryConsumerAuthorizationRulesResponse> f = client.execute(c -> {
            QueryConsumerAuthorizationRulesRequest request = QueryConsumerAuthorizationRulesRequest.builder()
                    .consumerId(consumerId)
                    .resourceId(resourceId)
                    .resourceType("MCP")
                    .build();

            return c.queryConsumerAuthorizationRules(request);
        });
        QueryConsumerAuthorizationRulesResponse response = f.join();

        if (200 != response.getStatusCode()) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        QueryConsumerAuthorizationRulesResponseBody.Items items = response.getBody().getData().getItems().get(0);
        APIGAuthConfig apigAuthConfig = APIGAuthConfig.builder()
                .authorizationRuleIds(Collections.singletonList(items.getConsumerAuthorizationRuleId()))
                .build();

        return ConsumerAuthConfig.builder()
                .apigAuthConfig(apigAuthConfig)
                .build();
    }
}
