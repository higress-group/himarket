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
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.dto.result.GatewayMCPServerResult;
import com.alibaba.apiopenplatform.dto.result.*;
import com.alibaba.apiopenplatform.entity.Gateway;
import com.alibaba.apiopenplatform.service.gateway.client.APIGClient;
import com.alibaba.apiopenplatform.service.gateway.client.SLSClient;
import com.alibaba.apiopenplatform.support.consumer.APIGAuthConfig;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.enums.APIGAPIType;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.product.APIGRefConfig;
import com.aliyun.sdk.service.apig20240327.models.*;
import com.aliyun.sdk.service.sls20201230.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AIGatewayOperator extends APIGOperator {

    @Override
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway gateway, int page, int size) {
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
    public PageResult<APIResult> fetchModelServers(Gateway gateway, int page, int size) {
        // 在 APIG_AI 中，模型服务也通过 MCP 路由暴露（模型插件），此处沿用 MCP 的列举逻辑
        return fetchAPIs(gateway, APIGAPIType.LLM, page, size);
    }

    @Override
    public String fetchModelConfig(Gateway gateway, Object conf) {
        APIGRefConfig config = (APIGRefConfig) conf;

        ModelAPIResult modelAPI = fetchModelAPI(gateway, config.getApiId());

        PageResult<HttpRoute> httpRoutes = fetchHttpRoutes(gateway, config.getApiId(), 1, 100);

        ModelConfigResult modelConfigResult = new ModelConfigResult();
        // 基本信息
        modelConfigResult.setModelApiName(modelAPI.getApiName());
        modelConfigResult.setAiProtocols(modelAPI.getAiProtocols());
        modelConfigResult.setBasePath(modelAPI.getBasePath());

        // Domains 优先取路由域名，其次取 API 部署自定义域名
        if (httpRoutes != null && httpRoutes.getTotalElements() > 0 && httpRoutes.getContent() != null) {
            HttpRoute first = httpRoutes.getContent().get(0);
            if (first.getDomainInfos() != null) {
                modelConfigResult.setDomains(first.getDomainInfos().stream()
                        .map(d -> ModelConfigResult.Domain.builder()
                                .domain(d.getName())
                                .protocol(Optional.ofNullable(d.getProtocol()).map(String::toLowerCase).orElse(null))
                                .build())
                        .collect(Collectors.toList()));
            }
        }

        if ((modelConfigResult.getDomains() == null || modelConfigResult.getDomains().isEmpty())
                && modelAPI.getCustomDomains() != null) {
            modelConfigResult.setDomains(modelAPI.getCustomDomains().stream()
                    .map(d -> ModelConfigResult.Domain.builder()
                            .domain(d.getName())
                            .protocol(Optional.ofNullable(d.getProtocol()).map(String::toLowerCase).orElse(null))
                            .build())
                    .collect(Collectors.toList()));
        }

        // Routes
        if (httpRoutes != null && httpRoutes.getTotalElements() > 0 && httpRoutes.getContent() != null) {
            modelConfigResult.setRoutes(httpRoutes.getContent().stream().map(route -> {
                ModelConfigResult.Route r = ModelConfigResult.Route.builder()
                        .name(route.getName())
                        .ignoreUriCase(false)
                        .build();
                if (route.getMatch() != null) {
                    if (route.getMatch().getMethods() != null) {
                        r.setMethods(route.getMatch().getMethods());
                    }
                    if (route.getMatch().getPath() != null) {
                        ModelConfigResult.Path p = ModelConfigResult.Path.builder()
                                .type(route.getMatch().getPath().getType())
                                .value(route.getMatch().getPath().getValue())
                                .build();
                        r.setPaths(Collections.singletonList(p));
                    }
                }
                return r;
            }).collect(Collectors.toList()));
        }

        // Services（通过 fetchService(serviceId) 获取更完整信息）
        if (modelAPI.getServiceConfigs() != null && !modelAPI.getServiceConfigs().isEmpty()) {
            modelConfigResult.setServices(modelAPI.getServiceConfigs().stream()
                    .map(sc -> {
                        ServiceResult service = fetchService(gateway, sc.getServiceId());
                        String modelName = extractAiConfigField(service, "modelName");
                        // 若未提供模型名，优先使用 provider，其次回落为 serviceName
                        if (StrUtil.isBlank(modelName)) {
                            modelName = extractAiConfigField(service, "provider");
                        }
                        if (StrUtil.isBlank(modelName) && service != null) {
                            modelName = service.getServiceName();
                        }

                        String modelNamePattern = extractAiConfigField(service, "modelNamePattern");
                        String protocol = extractProtocol(service, modelAPI);
                        java.util.List<String> protocols = extractProtocols(service, modelAPI);
                        String address = extractAiConfigField(service, "address");

                        return ModelConfigResult.Service.builder()
                                .serviceName(service != null ? service.getServiceName() : sc.getServiceId())
                                .modelName(modelName)
                                .modelNamePattern(modelNamePattern)
                                .protocol(protocol)
                                .protocols(protocols)
                                .address(address)
                                .build();
                    })
                    .collect(Collectors.toList()));
        }

        return JSONUtil.toJsonStr(modelConfigResult);
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
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
                    .map(domainInfo -> MCPConfigResult.Domain.builder()
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
            meta.setFromType(protocol);

            // HTTP转MCP需从插件获取tools配置
            fetchTool = StrUtil.equalsIgnoreCase(protocol, "HTTP");
        }

        if (fetchTool) {
            String toolSpec = fetchMcpTools(gateway, config.getMcpRouteId());
            if (StrUtil.isNotBlank(toolSpec)) {
                m.setTools(toolSpec);
                // 默认为HTTP转MCP
                if (StrUtil.isBlank(meta.getFromType())) {
                    meta.setFromType("HTTP");
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


    private String extractAiConfigField(ServiceResult service, String field) {
        if (service == null || service.getAiServiceConfig() == null) {
            return null;
        }
        try {
            return JSONUtil.parseObj(service.getAiServiceConfig()).getStr(field);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractProtocol(ServiceResult service, ModelAPIResult modelAPI) {
        if (service != null && service.getAiServiceConfig() != null) {
            try {
                cn.hutool.json.JSONObject obj = JSONUtil.parseObj(service.getAiServiceConfig());
                String p = obj.getStr("protocol");
                if (StrUtil.isBlank(p) && obj.containsKey("protocols")) {
                    cn.hutool.json.JSONArray arr = obj.getJSONArray("protocols");
                    if (arr != null && !arr.isEmpty()) {
                        p = arr.getStr(0);
                    }
                }
                if (StrUtil.isNotBlank(p)) {
                    return p.toLowerCase();
                }
            } catch (Exception ignored) {}
        }
        if (modelAPI != null && modelAPI.getAiProtocols() != null && !modelAPI.getAiProtocols().isEmpty()) {
            return Optional.ofNullable(modelAPI.getAiProtocols().get(0)).map(String::toLowerCase).orElse(null);
        }
        return null;
    }

    private java.util.List<String> extractProtocols(ServiceResult service, ModelAPIResult modelAPI) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (service != null && service.getAiServiceConfig() != null) {
            try {
                cn.hutool.json.JSONObject obj = JSONUtil.parseObj(service.getAiServiceConfig());
                if (obj.containsKey("protocols")) {
                    cn.hutool.json.JSONArray arr = obj.getJSONArray("protocols");
                    if (arr != null) {
                        for (Object o : arr) {
                            String v = String.valueOf(o);
                            if (StrUtil.isNotBlank(v)) {
                                list.add(v);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (list.isEmpty() && modelAPI != null && modelAPI.getAiProtocols() != null) {
            list.addAll(modelAPI.getAiProtocols());
        }
        return list;
    }
  
    @Override
    public String getDashboard(Gateway gateway) {
        SLSClient ticketClient = new SLSClient(gateway.getApigConfig(),true);
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
        SLSClient client = new SLSClient(gateway.getApigConfig(),false);
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
        }  catch (Exception e) {
            log.error("Error fetching Project", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching Project,Cause:" + e.getMessage());
        }
        String region = gateway.getApigConfig().getRegion();
        String dashboardUrl = String.format("https://sls.console.aliyun.com/lognext/project/%s/dashboard/dashboard-1756276497392-966932?slsRegion=%s&sls_ticket=%s&isShare=true&hideTopbar=true&hideSidebar=true&ignoreTabLocalStorage=true", projectName,region, ticket);
        log.info("Dashboard URL: {}", dashboardUrl);
        return dashboardUrl;
    }

    public String fetchMcpTools(Gateway gateway, String routeId) {
        APIGClient client = getClient(gateway);

        try {
            ListPluginAttachmentsResponse response = client.execute(c -> {
                ListPluginAttachmentsRequest request = ListPluginAttachmentsRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .attachResourceId(routeId)
                        .attachResourceType("GatewayRoute")
                        .pageNumber(1)
                        .pageSize(100)
                        .build();
                try {
                    return c.listPluginAttachments(request).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

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

            CreateConsumerAuthorizationRulesResponse response = client.execute(c -> {
                CreateConsumerAuthorizationRulesRequest request = CreateConsumerAuthorizationRulesRequest.builder()
                        .authorizationRules(Collections.singletonList(rule))
                        .build();
                try {
                    return c.createConsumerAuthorizationRules(request).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

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
            log.error("Error authorizing consumer {} to mcp server {} in AI gateway {}", consumerId, mcpRouteId, gateway.getGatewayId(), e);
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Failed to authorize consumer to mcp server in AI gateway: " + e.getMessage());
        }
    }
}
