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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.higress.sdk.model.route.KeyedRoutePredicate;
import com.alibaba.higress.sdk.model.route.RoutePredicate;
import com.alibaba.himarket.dto.result.agent.AgentAPIResult;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.gateway.GatewayResult;
import com.alibaba.himarket.dto.result.gateway.GatewayServiceResult;
import com.alibaba.himarket.dto.result.httpapi.APIConfigResult;
import com.alibaba.himarket.dto.result.httpapi.APIResult;
import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMCPServerResult;
import com.alibaba.himarket.dto.result.mcp.MCPConfigResult;
import com.alibaba.himarket.dto.result.mcp.OpenAPIMCPConfig;
import com.alibaba.himarket.dto.result.mcp.SofaHigressMCPServerResult;
import com.alibaba.himarket.dto.result.model.GatewayModelAPIResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.dto.result.model.SofaHigressModelResult;
import com.alibaba.himarket.entity.Consumer;
import com.alibaba.himarket.entity.ConsumerCredential;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.gateway.client.*;
import com.alibaba.himarket.service.hichat.manager.ToolManager;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.consumer.ApiKeyConfig;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.consumer.SofaHigressAuthConfig;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.gateway.GatewayConfig;
import com.alibaba.himarket.support.gateway.SofaHigressConfig;
import com.alibaba.himarket.support.product.SofaHigressRefConfig;
import com.aliyun.sdk.service.apig20240327.models.HttpApiApiInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Resource;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

@Service
@Slf4j
public class SofaHigressOperator extends GatewayOperator<SofaHigressClient> {
    @Resource private ToolManager toolManager;
    @Resource private ObjectMapper objectMapper;

    @Override
    public PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size) {
        return fetchHTTPorRestAPIs(gateway, page, size);
    }

    @Override
    public PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size) {
        return fetchHTTPorRestAPIs(gateway, page, size);
    }

    public PageResult<APIResult> fetchHTTPorRestAPIs(Gateway gateway, int page, int size) {
        SofaHigressClient client = getClient(gateway);

        SofaHigressRoutePageRequest request =
                SofaHigressRoutePageRequest.builder()
                        .pageInfo(
                                PageInfo.builder()
                                        .pageIndex((long) page)
                                        .pageSize((long) size)
                                        .build())
                        .fuzzySearch(true)
                        .type("HTTP")
                        .build();

        SofaHigressPageResponse<SofaHigressRouteConfig> response =
                client.execute("/route/list", HttpMethod.POST, request, new TypeReference<>() {});

        List<APIResult> apiResults =
                response.getList().stream()
                        .map(
                                s -> {
                                    APIResult apiResult = new APIResult();
                                    apiResult.setApiId(s.getRouteId());
                                    apiResult.setApiName(s.getName());
                                    return apiResult;
                                })
                        .toList();

        return PageResult.of(apiResults, page, size, response.getPageInfo().getTotal());
    }

    @Override
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(
            Gateway gateway, int page, int size) {
        SofaHigressClient client = getClient(gateway);

        SofaHigressPageRequest<SofaHigressMCPConfig> request =
                SofaHigressPageRequest.<SofaHigressMCPConfig>builder()
                        .pageInfo(
                                PageInfo.builder()
                                        .pageIndex((long) page)
                                        .pageSize((long) size)
                                        .build())
                        .fuzzySearch(true)
                        .param(SofaHigressMCPConfig.builder().status("ONSHELF").build())
                        .build();

        SofaHigressPageResponse<SofaHigressMCPConfig> response =
                client.execute(
                        "/mcpServer/list", HttpMethod.POST, request, new TypeReference<>() {});

        List<SofaHigressMCPServerResult> mcpServers =
                response.getList().stream()
                        .map(s -> new SofaHigressMCPServerResult().convertFrom(s))
                        .toList();

        return PageResult.of(mcpServers, page, size, response.getPageInfo().getTotal());
    }

    @Override
    public PageResult<AgentAPIResult> fetchAgentAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<? extends GatewayModelAPIResult> fetchModelAPIs(
            Gateway gateway, int page, int size) {
        SofaHigressClient client = getClient(gateway);

        SofaHigressApiPageRequest request =
                SofaHigressApiPageRequest.builder()
                        .pageInfo(
                                PageInfo.builder()
                                        .pageIndex((long) page)
                                        .pageSize((long) size)
                                        .build())
                        .type("AI")
                        .build();

        SofaHigressPageResponse<SofaHigressApiConfig> response =
                client.execute("/api/list", HttpMethod.POST, request, new TypeReference<>() {});

        List<SofaHigressModelResult> modelResults =
                response.getList().stream()
                        .map(
                                s ->
                                        SofaHigressModelResult.builder()
                                                .modelApiId(s.getApiId())
                                                .modelApiName(s.getName())
                                                .build())
                        .toList();

        return PageResult.of(modelResults, page, size, response.getPageInfo().getTotal());
    }

    @Override
    public String fetchAPIConfig(Gateway gateway, Object config) {
        SofaHigressRefConfig refConfig = (SofaHigressRefConfig) config;

        // 通过 routeId 查询 api-route 详情
        SofaHigressRouteConfig response = fetchRoute(gateway, refConfig.getApiId(), null);

        APIConfigResult configResult = new APIConfigResult();
        // spec
        configResult.setSpec(JSONUtil.toJsonStr(response));

        // meta
        APIConfigResult.APIMetadata meta = new APIConfigResult.APIMetadata();
        meta.setSource(GatewayType.SOFA_HIGRESS.name());
        meta.setType("Route");
        configResult.setMeta(meta);

        return JSONUtil.toJsonStr(configResult);
    }

    public SofaHigressRouteConfig fetchRoute(Gateway gateway, String routeId, String routeName) {
        SofaHigressClient client = getClient(gateway);

        return client.execute(
                "/route/detail",
                HttpMethod.POST,
                SofaHigressGetRouteRequest.builder().routeId(routeId).routeName(routeName).build(),
                new TypeReference<>() {});
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        SofaHigressRefConfig config = (SofaHigressRefConfig) conf;

        SofaHigressMCPConfig response = fetchSofaHigressMCPConfig(gateway, config.getServerId());
        // 通过响应构建 MCP 配置结果
        MCPConfigResult mcpConfigResult = new MCPConfigResult();
        mcpConfigResult.setMcpServerName(response.getName());
        // mcpServerConfig需要path和domain信息
        MCPConfigResult.MCPServerConfig mcpServerConfig = new MCPConfigResult.MCPServerConfig();
        mcpServerConfig.setPath(response.getPath() + "/sse");
        List<String> domains = response.getDomains();
        mcpServerConfig.setDomains(domainConvert(gateway, domains));
        mcpConfigResult.setMcpServerConfig(mcpServerConfig);
        // 设置工具信息
        try {
            mcpConfigResult.setTools(objectMapper.writeValueAsString(response.getTools()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        // meta
        MCPConfigResult.McpMetadata mcpMetadata = new MCPConfigResult.McpMetadata();
        mcpMetadata.setSource(GatewayType.SOFA_HIGRESS.name());
        mcpMetadata.setCreateFromType(response.getType());
        mcpMetadata.setProtocol("SSE");
        mcpConfigResult.setMeta(mcpMetadata);
        return JSONUtil.toJsonStr(mcpConfigResult);
    }

    private SofaHigressMCPConfig fetchSofaHigressMCPConfig(Gateway gateway, String serverId) {
        SofaHigressClient client = getClient(gateway);
        // 通过 serverId 查询 Mcp 详情
        return client.execute(
                "/mcpServer/detail",
                HttpMethod.POST,
                BaseRequest.<SofaHigressMCPConfig>builder()
                        .param(SofaHigressMCPConfig.builder().serverId(serverId).build())
                        .build(),
                new TypeReference<>() {});
    }

    private SofaHigressMCPConfig fetchSofaHigressMCPConfigByName(
            Gateway gateway, String serverName) {
        SofaHigressClient client = getClient(gateway);
        // 通过 serverName 查询 Mcp 详情
        return client.execute(
                "/mcpServer/detailByName",
                HttpMethod.POST,
                BaseRequest.<SofaHigressMCPConfig>builder()
                        .param(SofaHigressMCPConfig.builder().name(serverName).build())
                        .build(),
                new TypeReference<>() {});
    }

    private SofaHigressDomainConfig fetchDomain(Gateway gateway, String domain) {
        SofaHigressClient client = getClient(gateway);
        SofaHigressDomainConfig response =
                client.execute(
                        "/domain/detailByName",
                        HttpMethod.POST,
                        BaseRequest.<SofaHigressDomainConfig>builder()
                                .param(SofaHigressDomainConfig.builder().domainName(domain).build())
                                .build(),
                        new TypeReference<>() {});
        return response;
    }

    private List<DomainResult> domainConvert(Gateway gateway, List<String> domains) {
        if (CollUtil.isEmpty(domains)) {
            List<URI> uris = fetchGatewayUris(gateway);
            if (CollUtil.isEmpty(uris)) {
                return Collections.singletonList(
                        DomainResult.builder()
                                .domain("<sofa-higress-gateway-ip>")
                                .protocol("http")
                                .build());
            } else {
                URI uri = uris.get(0);
                return Collections.singletonList(
                        DomainResult.builder()
                                .domain(uri.getHost())
                                .protocol(uri.getScheme())
                                .port(uri.getPort() == -1 ? null : uri.getPort())
                                .build());
            }
        }
        return domains.stream()
                .map(
                        domain -> {
                            SofaHigressDomainConfig domainConfig = fetchDomain(gateway, domain);
                            String protocol =
                                    (domainConfig == null
                                                    || domainConfig
                                                            .getEnableHttps()
                                                            .equalsIgnoreCase("off"))
                                            ? "http"
                                            : "https";
                            return DomainResult.builder().domain(domain).protocol(protocol).build();
                        })
                .collect(Collectors.toList());
    }

    @Override
    public String fetchAgentConfig(Gateway gateway, Object conf) {
        return "";
    }

    @Override
    public String fetchModelConfig(Gateway gateway, Object conf) {
        SofaHigressRefConfig refConfig = (SofaHigressRefConfig) conf;

        // 获取 ai api 的信息，其中包括 ai route 的信息
        SofaHigressApiConfig response =
                fetchSofaHigressApiConfig(gateway, refConfig.getModelApiId(), null);

        ModelConfigResult result = new ModelConfigResult();
        // AI route
        List<String> domains = response.getRouteInfo().getDomains();
        List<HttpRouteResult> routeResults =
                Collections.singletonList(
                        new HttpRouteResult()
                                .convertFrom(
                                        response.getRouteInfo(), domainConvert(gateway, domains)));
        ModelConfigResult.ModelAPIConfig config =
                ModelConfigResult.ModelAPIConfig.builder()
                        // Default value
                        .aiProtocols(List.of("OpenAI/V1"))
                        .modelCategory("Text")
                        .routes(routeResults)
                        .build();
        result.setModelAPIConfig(config);

        return JSONUtil.toJsonStr(result);
    }

    public SofaHigressConsumerConfig getSofaHigressConsumerConfigByName(
            Gateway gateway, String consumerName) {
        SofaHigressClient client = getClient(gateway);
        return client.execute(
                "/consumer/detailByName",
                HttpMethod.POST,
                BaseRequest.<SofaHigressConsumerConfig>builder()
                        .param(SofaHigressConsumerConfig.builder().name(consumerName).build())
                        .build(),
                new TypeReference<>() {});
    }

    public List<SofaHigressConsumerConfig> getAllSofaHigressConsumerConfig(Gateway gateway) {
        SofaHigressClient client = getClient(gateway);
        return client.execute(
                "/consumer/all",
                HttpMethod.POST,
                BaseRequest.<SofaHigressConsumerConfig>builder().build(),
                new TypeReference<>() {});
    }

    @Override
    public String fetchMcpToolsForConfig(Gateway gateway, Object conf) {
        MCPConfigResult config = (MCPConfigResult) conf;
        SofaHigressMCPConfig response =
                fetchSofaHigressMCPConfigByName(gateway, config.getMcpServerName());

        List<SofaHigressConsumerConfig> consumersList = getAllSofaHigressConsumerConfig(gateway);

        // Build authentication context
        CredentialContext credentialContext = CredentialContext.builder().build();
        Optional<String> consumerOptional =
                Optional.ofNullable(response.getAuthConfig())
                        .filter(authInfo -> BooleanUtil.isTrue(authInfo.getEnabled()))
                        .map(AiRouteAuthConfig::getAllowedConsumers)
                        .filter(CollUtil::isNotEmpty)
                        .map(
                                consumerNames -> {
                                    Set<String> consumerNameSet = new HashSet<>();
                                    consumersList.forEach(
                                            consumer -> consumerNameSet.add(consumer.getName()));

                                    for (String consumerName : consumerNames) {
                                        if (consumerNameSet.contains(consumerName)) {
                                            return consumerName;
                                        }
                                    }
                                    return null;
                                });
        // If authentication is enabled, but no consumer found for current workspace and
        // tenant,
        // return null
        if (response.getAuthConfig() != null
                && response.getAuthConfig().getEnabled()
                && consumerOptional.isEmpty()) {
            log.warn("No consumer found in the allowed consumers list");
            return null;
        }
        consumerOptional.ifPresent(
                consumerName -> {
                    SofaHigressConsumerConfig consumerConfig =
                            getSofaHigressConsumerConfigByName(gateway, consumerName);

                    Optional.ofNullable(consumerConfig.getKeyAuthConfig())
                            .ifPresent(
                                    credential ->
                                            fillCredentialContext(credentialContext, credential));
                });

        MCPTransportConfig transportConfig = config.toTransportConfig();
        if (transportConfig == null) {
            return null;
        }

        transportConfig.setHeaders(credentialContext.getHeaders());
        transportConfig.setQueryParams(credentialContext.getQueryParams());

        // Get and transform tool list
        try (McpClientWrapper mcpClientWrapper = toolManager.createClient(transportConfig)) {
            if (mcpClientWrapper == null) {
                return null;
            }

            List<McpSchema.Tool> tools = mcpClientWrapper.listTools().block();
            OpenAPIMCPConfig openAPIMCPConfig =
                    OpenAPIMCPConfig.convertFromToolList(config.getMcpServerName(), tools);

            return JSONUtil.toJsonStr(openAPIMCPConfig);
        } catch (Exception e) {
            log.error("List mcp tools failed", e);
            return null;
        }
    }

    private void fillCredentialContext(CredentialContext context, KeyAuthConfig keyAuthConfig) {
        String apiKey = keyAuthConfig.getValue();
        if (apiKey == null) {
            return;
        }
        String source = keyAuthConfig.getSource();
        String key = keyAuthConfig.getKey();

        switch (source.toUpperCase()) {
            case "BEARER" -> context.getHeaders().put("Authorization", "Bearer " + apiKey);
            case "QUERY" -> context.getQueryParams().put(key, apiKey);
            // Header or other values
            default -> context.getHeaders().put(key, apiKey);
        }
    }

    private SofaHigressApiConfig fetchSofaHigressApiConfig(
            Gateway gateway, String apiId, String apiName) {
        SofaHigressClient client = getClient(gateway);
        // 通过 serverId 查询 Mcp 详情
        return client.execute(
                "/api/detail",
                HttpMethod.POST,
                SofaHigressGetApiRequest.builder().apiId(apiId).apiName(apiName).build(),
                new TypeReference<>() {});
    }

    @Override
    public PageResult<GatewayResult> fetchGateways(Object param, int page, int size) {
        throw new UnsupportedOperationException(
                "Sofa Higress gateway does not support fetching Gateways");
    }

    @Override
    public String createConsumer(
            Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        SofaHigressConfig sofaHigressConfig = config.getSofaHigressConfig();
        SofaHigressClient client = getClient(sofaHigressConfig);
        SofaHigressConsumerConfig createdConsumer =
                client.execute(
                        "/consumer/create",
                        HttpMethod.POST,
                        buildSofaHigressConsumer(
                                null, consumer.getConsumerId(), credential.getApiKeyConfig()),
                        new TypeReference<>() {});
        return createdConsumer.getConsumerId();
    }

    @Override
    public void updateConsumer(
            String consumerId, ConsumerCredential credential, GatewayConfig config) {
        SofaHigressConfig sofaHigressConfig = config.getSofaHigressConfig();
        SofaHigressClient client = getClient(sofaHigressConfig);
        client.execute(
                "/consumer/update",
                HttpMethod.POST,
                buildSofaHigressConsumer(consumerId, null, credential.getApiKeyConfig()),
                new TypeReference<>() {});
    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {
        SofaHigressConfig sofaHigressConfig = config.getSofaHigressConfig();
        SofaHigressClient client = getClient(sofaHigressConfig);
        client.execute(
                "/consumer/delete",
                HttpMethod.POST,
                buildSofaHigressConsumer(consumerId),
                new TypeReference<>() {});
    }

    @Override
    public boolean isConsumerExists(String consumerId, GatewayConfig config) {
        SofaHigressConfig sofaHigressConfig = config.getSofaHigressConfig();
        SofaHigressClient client = getClient(sofaHigressConfig);
        SofaHigressConsumerConfig consumerConfig =
                client.execute(
                        "/consumer/detail",
                        HttpMethod.POST,
                        buildSofaHigressConsumer(consumerId),
                        new TypeReference<>() {});
        return consumerConfig != null;
    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(
            Gateway gateway, String consumerId, Object refConfig) {
        SofaHigressRefConfig sofaHigressRefConfig = (SofaHigressRefConfig) refConfig;

        String routeId = sofaHigressRefConfig.getApiId();
        String routeName = sofaHigressRefConfig.getApiName();
        String serverId = sofaHigressRefConfig.getServerId();
        String mcpServerName = sofaHigressRefConfig.getMcpServerName();
        String modelApiId = sofaHigressRefConfig.getModelApiId();
        String modelApiName = sofaHigressRefConfig.getModelApiName();

        // MCP or AIRoute or RestRoute
        if (StrUtil.isNotBlank(routeId)) {
            return authorizeRestRoute(gateway, consumerId, routeId, routeName);
        } else if (StrUtil.isNotBlank(serverId)) {
            return authorizeMCPServer(gateway, consumerId, serverId, mcpServerName);
        } else {
            return authorizeAIRoute(gateway, consumerId, modelApiId, modelApiName);
        }
    }

    private ConsumerAuthConfig authorizeRestRoute(
            Gateway gateway, String consumerId, String routeId, String routeName) {
        SofaHigressClient client = getClient(gateway);

        client.execute(
                "/route/sub",
                HttpMethod.POST,
                SubOrUnSubRequest.builder().consumerId(consumerId).routerId(routeId).build(),
                new TypeReference<>() {});

        SofaHigressAuthConfig sofaHigressAuthConfig =
                SofaHigressAuthConfig.builder()
                        .resourceType("REST_API")
                        .resourceName(routeName)
                        .build();

        return ConsumerAuthConfig.builder().sofaHigressAuthConfig(sofaHigressAuthConfig).build();
    }

    private ConsumerAuthConfig authorizeMCPServer(
            Gateway gateway, String consumerId, String serverId, String mcpServerName) {
        SofaHigressClient client = getClient(gateway);
        SofaHigressMCPConfig response = fetchSofaHigressMCPConfig(gateway, serverId);

        // 通过MCP server的routeId和consumerId进行请求订阅
        client.execute(
                "/mcpServer/sub",
                HttpMethod.POST,
                SubOrUnSubRequest.builder()
                        .consumerId(consumerId)
                        .routerId(response.getRouteId())
                        .build(),
                new TypeReference<>() {});

        SofaHigressAuthConfig sofaHigressAuthConfig =
                SofaHigressAuthConfig.builder()
                        .resourceType("MCP_SERVER")
                        .resourceName(mcpServerName)
                        .build();

        return ConsumerAuthConfig.builder().sofaHigressAuthConfig(sofaHigressAuthConfig).build();
    }

    private ConsumerAuthConfig authorizeAIRoute(
            Gateway gateway, String consumerId, String modelApiId, String modelApiName) {
        SofaHigressClient client = getClient(gateway);
        SofaHigressApiConfig response = fetchSofaHigressApiConfig(gateway, modelApiId, null);

        client.execute(
                "/route/sub",
                HttpMethod.POST,
                SubOrUnSubRequest.builder()
                        .consumerId(consumerId)
                        .routerId(response.getRouteInfo().getRouteId())
                        .build(),
                new TypeReference<>() {});

        SofaHigressAuthConfig sofaHigressAuthConfig =
                SofaHigressAuthConfig.builder()
                        .resourceType("MODEL_API")
                        .resourceName(modelApiName)
                        .build();

        return ConsumerAuthConfig.builder().sofaHigressAuthConfig(sofaHigressAuthConfig).build();
    }

    @Override
    public void revokeConsumerAuthorization(
            Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        SofaHigressAuthConfig sofaHigressAuthConfig = authConfig.getSofaHigressAuthConfig();
        if (sofaHigressAuthConfig == null) {
            return;
        }

        if (sofaHigressAuthConfig.getResourceType().equalsIgnoreCase("REST_API")) {
            revokeAuthorizeRestRoute(gateway, consumerId, sofaHigressAuthConfig.getResourceName());
        } else if (sofaHigressAuthConfig.getResourceType().equalsIgnoreCase("MCP_SERVER")) {
            revokeAuthorizeMCPServer(gateway, consumerId, sofaHigressAuthConfig.getResourceName());
        } else {
            revokeAuthorizeAIRoute(gateway, consumerId, sofaHigressAuthConfig.getResourceName());
        }
    }

    private void revokeAuthorizeRestRoute(Gateway gateway, String consumerId, String routeName) {
        SofaHigressClient client = getClient(gateway);

        SofaHigressRouteConfig response = fetchRoute(gateway, null, routeName);

        client.execute(
                "/route/unsub",
                HttpMethod.POST,
                SubOrUnSubRequest.builder()
                        .consumerId(consumerId)
                        .routerId(response.getRouteId())
                        .build(),
                new TypeReference<>() {});
    }

    private void revokeAuthorizeMCPServer(
            Gateway gateway, String consumerId, String mcpServerName) {
        SofaHigressClient client = getClient(gateway);

        SofaHigressMCPConfig response = fetchSofaHigressMCPConfigByName(gateway, mcpServerName);

        // 通过MCP server的routeId和consumerId进行请求订阅
        client.execute(
                "/mcpServer/unsub",
                HttpMethod.POST,
                SubOrUnSubRequest.builder()
                        .consumerId(consumerId)
                        .routerId(response.getRouteId())
                        .build(),
                new TypeReference<>() {});
    }

    private void revokeAuthorizeAIRoute(Gateway gateway, String consumerId, String modelApiName) {
        SofaHigressClient client = getClient(gateway);
        SofaHigressApiConfig response = fetchSofaHigressApiConfig(gateway, null, modelApiName);

        client.execute(
                "/route/unsub",
                HttpMethod.POST,
                SubOrUnSubRequest.builder()
                        .consumerId(consumerId)
                        .routerId(response.getRouteInfo().getRouteId())
                        .build(),
                new TypeReference<>() {});
    }

    @Override
    public HttpApiApiInfo fetchAPI(Gateway gateway, String apiId) {
        throw new UnsupportedOperationException(
                "Sofa Higress gateway does not support fetching API");
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.SOFA_HIGRESS;
    }

    @Override
    public List<URI> fetchGatewayUris(Gateway gateway) {

        SofaHigressClient client = getClient(gateway);
        String gatewayUrl = client.execute("/gatewayUrl", HttpMethod.POST, new BaseRequest());

        try {
            URI uri = new URI(gatewayUrl);
            // If no scheme (protocol) specified, add default http://
            if (uri.getScheme() == null) {
                uri = new URI("http://" + gatewayUrl);
            }

            return Collections.singletonList(uri);
        } catch (URISyntaxException e) {
            log.warn("Invalid gateway address: {}, error: {}", gatewayUrl, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<DomainResult> getGatewayDomains(Gateway gateway) {
        SofaHigressClient client = getClient(gateway);
        List<SofaHigressDomainConfig> response =
                client.execute(
                        "/domain/all",
                        HttpMethod.POST,
                        BaseRequest.<SofaHigressDomainConfig>builder()
                                .param(SofaHigressDomainConfig.builder().build())
                                .build(),
                        new TypeReference<>() {});
        return response.stream()
                .filter(Objects::nonNull)
                .map(
                        domainConfig -> {
                            String protocol =
                                    domainConfig.getEnableHttps().equalsIgnoreCase("off")
                                            ? "http"
                                            : "https";
                            return DomainResult.builder()
                                    .domain(domainConfig.getDomainName())
                                    .protocol(protocol)
                                    .build();
                        })
                .toList();
    }

    @Override
    public List<GatewayServiceResult> fetchGatewayServices(Gateway gateway) {
        SofaHigressClient client = getClient(gateway);
        List<SofaHigressServiceConfig> response =
                client.execute(
                        "/service/all",
                        HttpMethod.POST,
                        BaseRequest.builder().build(),
                        new TypeReference<>() {});
        return response.stream().map(SofaHigressServiceConfig::toGatewayServiceResult).toList();
    }

    public BaseRequest<SofaHigressConsumerConfig> buildSofaHigressConsumer(
            String consumerId, String consumerName, ApiKeyConfig apiKeyConfig) {
        String source = mapSource(apiKeyConfig.getSource());
        // todo: sofa-higress目前只支持一个消费者绑定一个认证凭证，
        // 所以只取Credentials的第一个apiKey，后续需要拓展以支持多个认证凭证
        String value = apiKeyConfig.getCredentials().get(0).getApiKey();
        SofaHigressConsumerConfig consumerConfig =
                SofaHigressConsumerConfig.builder()
                        .consumerId(consumerId)
                        .name(consumerName)
                        .description("consumer from Himarket")
                        .status(true)
                        .keyAuthConfig(
                                KeyAuthConfig.builder()
                                        .enabled(true)
                                        .source(source)
                                        .key(apiKeyConfig.getKey())
                                        .value(value)
                                        .build())
                        .build();
        return BaseRequest.<SofaHigressConsumerConfig>builder().param(consumerConfig).build();
    }

    public BaseRequest<SofaHigressConsumerConfig> buildSofaHigressConsumer(String consumerId) {
        SofaHigressConsumerConfig consumerConfig =
                SofaHigressConsumerConfig.builder().consumerId(consumerId).build();
        return BaseRequest.<SofaHigressConsumerConfig>builder().param(consumerConfig).build();
    }

    private String mapSource(String source) {
        if (StringUtils.isBlank(source)) return null;
        if ("Default".equalsIgnoreCase(source)) return "BEARER";
        if ("HEADER".equalsIgnoreCase(source)) return "HEADER";
        if ("QueryString".equalsIgnoreCase(source)) return "QUERY";
        return source;
    }

    public SofaHigressClient getClient(Gateway gateway) {
        return super.getClient(gateway);
    }

    /**
     * reuse client when there is only config param exists (no gateway param exists)
     */
    @SuppressWarnings("unchecked")
    public SofaHigressClient getClient(SofaHigressConfig sofaHigressConfig) {
        String clientKey = sofaHigressConfig.buildUniqueKey();
        Field clientCacheField = ReflectionUtils.findField(getClass(), "clientCache");
        if (clientCacheField == null) {
            throw new RuntimeException(
                    "clientCache field not found in GatewayOperator which is not expected");
        }
        ReflectionUtils.makeAccessible(clientCacheField);
        Map<String, GatewayClient> clientCache =
                (Map<String, GatewayClient>) ReflectionUtils.getField(clientCacheField, this);
        if (clientCache == null) {
            throw new RuntimeException(
                    "clientCache is null in SofaHigressOperator which is not expected");
        }
        return (SofaHigressClient)
                clientCache.computeIfAbsent(
                        clientKey, key -> new SofaHigressClient(sofaHigressConfig));
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class BaseRequest<T> {
        protected String tenantId;
        protected String workspaceId;
        protected T param;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class SofaHigressPageRequest<T> extends BaseRequest<T> {
        private PageInfo pageInfo;
        private Boolean fuzzySearch;
        private String queryType;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class SofaHigressRoutePageRequest extends SofaHigressPageRequest<Object> {
        private String type;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class SofaHigressApiPageRequest extends SofaHigressPageRequest<Object> {
        private String type;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class SofaHigressGetRouteRequest extends BaseRequest<Object> {
        private String routeId;
        private String routeName;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class SofaHigressGetApiRequest extends BaseRequest<Object> {
        private String apiId;
        private String apiName;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class SubOrUnSubRequest extends BaseRequest<Object> {
        private String routerId;
        private String consumerId;
    }

    @Data
    public static class SofaHigressPageResponse<T> {
        private List<T> list;
        private PageInfo pageInfo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SofaHigressMCPConfig {
        private String serverId;
        private String name;
        private String nameCn;
        private String routeId;
        private String serviceId;
        private String type;
        private String categoryId;
        private String categoryName;
        private String introduction;
        private List<SofaHigressMCPToolConfig> tools;
        private String path;
        private String config;
        private String status;
        private String description;
        private String upstreamProtocol;
        private String upstreamPrefix;
        // private QueryRateLimitConfig queryRateLimitConfig;
        // private UpstreamTokenModel upstreamToken;
        private AiRouteAuthConfig authConfig;
        private String sseUrl;
        private String sseUrlExample;
        private String queryType;
        private List<String> domains;
        private String serviceName;
        private String queryContent;
    }

    @Data
    public static class SofaHigressMCPToolConfig {
        private String toolId;
        private String name;
        private String description;
        private String method;
        private String url;
        private String argsApplyType;
        private String headers;
        private List<String> whenToUse;
        private List<String> frequentlyAskedQuestions;
        private String dictCode;
        private String cnName;
        private String mcpServerType;
        // private List<McpToolArgVO> args;
        // private List<McpToolOutputArgVO> outputArgs;
        private List<SofaHigressMCPToolHeader> mcpToolHeaders;
        private String routerId;
    }

    @Data
    public static class SofaHigressMCPToolHeader {
        private String key;
        private String value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SofaHigressDomainConfig {
        private String domainName;
        private String enableHttps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SofaHigressApiConfig {
        private String apiId;
        private String name;
        private String type;
        private String description;
        private SofaHigressRouteConfig routeInfo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SofaHigressRouteConfig {
        private String routeId;
        private String name;
        private List<String> domains;
        private List<String> methods;
        private String path;
        private RouteMatchConfig routeMatchConfig;
        // private AiUpstreamConfig aiUpstreamConfig;
        // private UpstreamConfig upstreamConfig;
        // private RouteFallbackConfig routeFallbackConfig;
        // private TimeoutConfig timeoutConfig;
        // private ProxyNextUpstreamConfig proxyNextUpstream;
        // private RewriteConfig rewriteConfig;
        // private LoadBalanceConfig loadBalanceConfig;
        // private CorsConfig corsConfig;
        // private HeaderControlConfig headerControl;
        // private AiRouteAuthConfig authConfig;
        // private AiPluginConfig aiPluginConfig;
        // private QueryRateLimitConfig queryRateLimitConfig;
        // private MockConfig mockConfig;
        // private RedirectConfig redirectConfig;
        // private CircuitBreakerConfig circuitBreakerConfig;
        // private ConsumerCallStatisticsConfig statisticsConfig;
        // private AnnotationConfig annotationConfig;
        // private TrafficMirrorConfig trafficMirrorConfig;
        // private IpControlConfig ipConfig;
        private String description;
        private String status;
        private String apiId;
        private String apiName;
        private String apiType;
        private String gatewayId;
        private String gatewayInstanceName;
        private String source;
        private String dictCode;
        private String parentDictCode;
        private String parentDictName;
    }

    @Data
    public static class RouteMatchConfig {
        private RoutePredicate path;
        private List<KeyedRoutePredicate> headers;
        private List<KeyedRoutePredicate> urlParams;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageInfo {
        private Long pageIndex;
        private Long pageSize;
        private Long total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SofaHigressConsumerConfig {
        private String consumerId;
        private String name;
        private String description;
        private Boolean status;
        private KeyAuthConfig keyAuthConfig;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SofaHigressServiceConfig {
        private String serviceId;
        private String name;
        private String sourceType;
        private String address;
        private String port;
        private String protocol;
        private String namespace;
        private String gatewayId;
        private String certId;
        private String tlsMode;
        private String sni;

        public GatewayServiceResult toGatewayServiceResult() {
            return GatewayServiceResult.builder()
                    .serviceId(serviceId)
                    .serviceName(name)
                    .tlsEnabled(tlsMode != null && tlsMode.equals("mutual"))
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyAuthConfig {
        private Boolean enabled;
        private String key;
        private String source;
        private String value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "AI Route auth configuration")
    public static class AiRouteAuthConfig {
        private Boolean enabled;

        @Schema(description = "Allowed consumer names")
        private List<String> allowedConsumers;
    }
}
