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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapBuilder;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.dto.result.agent.AgentAPIResult;
import com.alibaba.apiopenplatform.dto.result.httpapi.APIResult;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.dto.result.gateway.GatewayResult;
import com.alibaba.apiopenplatform.dto.result.common.DomainResult;
import com.alibaba.apiopenplatform.dto.result.httpapi.HttpRouteResult;
import com.alibaba.apiopenplatform.dto.result.mcp.GatewayMCPServerResult;
import com.alibaba.apiopenplatform.dto.result.mcp.HigressMCPServerResult;
import com.alibaba.apiopenplatform.dto.result.mcp.MCPConfigResult;
import com.alibaba.apiopenplatform.dto.result.model.GatewayModelAPIResult;
import com.alibaba.apiopenplatform.dto.result.model.HigressModelResult;
import com.alibaba.apiopenplatform.dto.result.model.ModelConfigResult;
import com.alibaba.apiopenplatform.entity.Gateway;
import com.alibaba.apiopenplatform.entity.Consumer;
import com.alibaba.apiopenplatform.entity.ConsumerCredential;
import com.alibaba.apiopenplatform.service.gateway.client.HigressClient;
import com.alibaba.apiopenplatform.support.consumer.ApiKeyConfig;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.consumer.HigressAuthConfig;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
import com.alibaba.apiopenplatform.support.gateway.HigressConfig;
import com.alibaba.apiopenplatform.support.product.HigressRefConfig;

import com.alibaba.higress.sdk.model.route.KeyedRoutePredicate;
import com.alibaba.higress.sdk.model.route.RoutePredicate;
import com.aliyun.sdk.service.apig20240327.models.HttpApiApiInfo;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HigressOperator extends GatewayOperator<HigressClient> {

    @Override
    public PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException("Higress gateway does not support HTTP APIs");
    }

    @Override
    public PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException("Higress gateway does not support REST APIs");
    }

    @Override
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway gateway, int page, int size) {
        HigressClient client = getClient(gateway);

        Map<String, String> queryParams = MapBuilder.<String, String>create()
                .put("pageNum", String.valueOf(page))
                .put("pageSize", String.valueOf(size))
                .build();

        HigressPageResponse<HigressMCPConfig> response = client.execute("/v1/mcpServer",
                HttpMethod.GET,
                queryParams,
                null,
                new ParameterizedTypeReference<HigressPageResponse<HigressMCPConfig>>() {
                });

        List<HigressMCPServerResult> mcpServers = response.getData().stream()
                .map(s -> new HigressMCPServerResult().convertFrom(s))
                .collect(Collectors.toList());

        return PageResult.of(mcpServers, page, size, response.getTotal());
    }

    @Override
    public PageResult<AgentAPIResult> fetchAgentAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<? extends GatewayModelAPIResult> fetchModelAPIs(Gateway gateway, int page, int size) {
        HigressClient client = getClient(gateway);

        Map<String, String> queryParams = MapBuilder.<String, String>create()
                .put("pageNum", String.valueOf(page))
                .put("pageSize", String.valueOf(size))
                .build();

        try {
            HigressPageResponse<HigressAIRoute> response = client.execute("/v1/ai/routes",
                    HttpMethod.GET,
                    queryParams,
                    null,
                    new ParameterizedTypeReference<HigressPageResponse<HigressAIRoute>>() {
                    });

            List<HigressModelResult> modelAPIs = response.getData().stream()
                    .map(config -> HigressModelResult.builder()
                            .modelRouteName(config.getName())
                            .build())
                    .collect(Collectors.toList());

            return PageResult.of(modelAPIs, page, size, response.getTotal());
        } catch (Exception e) {
            log.warn("Failed to fetch model APIs from Higress, returning empty result", e);
            return PageResult.of(Collections.emptyList(), page, size, 0);
        }
    }

    @Override
    public String fetchAPIConfig(Gateway gateway, Object config) {
        throw new UnsupportedOperationException("Higress gateway does not support fetching API config");
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        HigressClient client = getClient(gateway);
        HigressRefConfig config = (HigressRefConfig) conf;

        HigressResponse<HigressMCPConfig> response = client.execute("/v1/mcpServer/" + config.getMcpServerName(),
                HttpMethod.GET,
                null,
                null,
                new ParameterizedTypeReference<HigressResponse<HigressMCPConfig>>() {
                });

        MCPConfigResult m = new MCPConfigResult();
        HigressMCPConfig higressMCPConfig = response.getData();
        m.setMcpServerName(higressMCPConfig.getName());

        // mcpServer config
        MCPConfigResult.MCPServerConfig c = new MCPConfigResult.MCPServerConfig();

        boolean isDirect = "direct_route".equalsIgnoreCase(higressMCPConfig.getType());
        DirectRouteConfig directRouteConfig = higressMCPConfig.getDirectRouteConfig();
        String transportType = isDirect ? directRouteConfig.getTransportType() : null;
        String path = isDirect ? directRouteConfig.getPath() : "/mcp-servers/" + higressMCPConfig.getName();

        c.setPath(path);
        List<String> domains = higressMCPConfig.getDomains();
        if (CollUtil.isEmpty(domains)) {
            c.setDomains(Collections.singletonList(
                    DomainResult.builder()
                            .domain("<higress-gateway-ip>")
                            .protocol("http")
                            .build()
            ));
        } else {
            c.setDomains(domains.stream()
                    .map(domain -> {
                        HigressDomainConfig domainConfig = fetchDomain(gateway, domain);
                        String protocol = (domainConfig == null || "off".equalsIgnoreCase(domainConfig.getEnableHttps()))
                                ? "http" : "https";
                        return DomainResult.builder()
                                .domain(domain)
                                .protocol(protocol)
                                .build();
                    })
                    .collect(Collectors.toList()));
        }

        m.setMcpServerConfig(c);

        // tools
        m.setTools(higressMCPConfig.getRawConfigurations());

        // meta
        MCPConfigResult.McpMetadata meta = new MCPConfigResult.McpMetadata();
        meta.setSource(GatewayType.HIGRESS.name());
        meta.setCreateFromType(higressMCPConfig.getType());
        meta.setProtocol((StrUtil.isBlank(transportType) || transportType.equalsIgnoreCase("SSE")) ? "SSE" : "HTTP");
        m.setMeta(meta);

        return JSONUtil.toJsonStr(m);
    }

    private HigressDomainConfig fetchDomain(Gateway gateway, String domain) {
        HigressClient client = getClient(gateway);
        HigressResponse<HigressDomainConfig> response = client.execute("/v1/domains/" + domain,
                HttpMethod.GET,
                null,
                null,
                new ParameterizedTypeReference<HigressResponse<HigressDomainConfig>>() {
                });
        return response.getData();
    }

    @Override
    public String fetchAgentConfig(Gateway gateway, Object conf) {
        return "";
    }

    @Override
    public String fetchModelConfig(Gateway gateway, Object conf) {
        HigressRefConfig higressRefConfig = (HigressRefConfig) conf;
        HigressAIRoute aiRoute = fetchAIRoute(gateway, higressRefConfig.getModelRouteName());

        // Domains
        List<DomainResult> domains = Optional.ofNullable(aiRoute.getDomains())
                .map(domainList -> domainList.stream()
                        .map(domain -> DomainResult.builder()
                                .domain(domain)
                                .protocol(Optional.ofNullable(fetchDomain(gateway, domain))
                                        .map(HigressDomainConfig::getEnableHttps)
                                        .map(String::toLowerCase)
                                        .filter("off"::equals)
                                        .map(s -> "http")
                                        .orElse("https"))
                                .build())
                        .toList())
                .orElse(Collections.emptyList());

        // AI route
        List<HttpRouteResult> routeResults = Collections.singletonList(new HttpRouteResult().convertFrom(aiRoute, domains));

        ModelConfigResult.ModelAPIConfig config = ModelConfigResult.ModelAPIConfig.builder()
                // Default value
                .aiProtocols(List.of("OpenAI/V1"))
                .modelCategory("Text")
                .routes(routeResults)
                .build();

        ModelConfigResult result = new ModelConfigResult();
        result.setModelAPIConfig(config);

        return JSONUtil.toJsonStr(result);
    }

    @Override
    public PageResult<GatewayResult> fetchGateways(Object param, int page, int size) {
        throw new UnsupportedOperationException("Higress gateway does not support fetching Gateways");
    }

    @Override
    public String createConsumer(Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        HigressConfig higressConfig = config.getHigressConfig();
        HigressClient client = new HigressClient(higressConfig);

        client.execute("/v1/consumers",
                HttpMethod.POST,
                null,
                buildHigressConsumer(consumer.getConsumerId(), credential.getApiKeyConfig()),
                String.class);

        return consumer.getConsumerId();
    }

    @Override
    public void updateConsumer(String consumerId, ConsumerCredential credential, GatewayConfig config) {
        HigressConfig higressConfig = config.getHigressConfig();
        HigressClient client = new HigressClient(higressConfig);

        client.execute("/v1/consumers/" + consumerId,
                HttpMethod.PUT,
                null,
                buildHigressConsumer(consumerId, credential.getApiKeyConfig()),
                String.class);
    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {
        HigressConfig higressConfig = config.getHigressConfig();
        HigressClient client = new HigressClient(higressConfig);

        client.execute("/v1/consumers/" + consumerId,
                HttpMethod.DELETE,
                null,
                null,
                String.class);
    }

    @Override
    public boolean isConsumerExists(String consumerId, GatewayConfig config) {
        // TODO: 实现Higress网关消费者存在性检查
        return true;
    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(Gateway gateway, String consumerId, Object refConfig) {
        HigressRefConfig config = (HigressRefConfig) refConfig;

        String mcpServerName = config.getMcpServerName();
        String modelRouteName = config.getModelRouteName();

        // MCP or AIRoute
        return StrUtil.isNotBlank(mcpServerName) ?
                authorizeMCPServer(gateway, consumerId, mcpServerName) : authorizeAIRoute(gateway, consumerId, modelRouteName);
    }

    private ConsumerAuthConfig authorizeMCPServer(Gateway gateway, String consumerId, String mcpServerName) {
        HigressClient client = getClient(gateway);

        client.execute("/v1/mcpServer/consumers/",
                HttpMethod.PUT,
                null,
                buildAuthHigressConsumer(mcpServerName, consumerId),
                Void.class);

        HigressAuthConfig higressAuthConfig = HigressAuthConfig.builder()
                .resourceType("MCP_SERVER")
                .resourceName(mcpServerName)
                .build();

        return ConsumerAuthConfig.builder()
                .higressAuthConfig(higressAuthConfig)
                .build();
    }

    private ConsumerAuthConfig authorizeAIRoute(Gateway gateway, String consumerId, String modelRouteName) {
        HigressAIRoute aiRoute = fetchAIRoute(gateway, modelRouteName);

        if (aiRoute.getAuthConfig() == null) {
            aiRoute.setAuthConfig(new RouteAuthConfig());
        }

        RouteAuthConfig authConfig = aiRoute.getAuthConfig();
        List<String> allowedConsumers = authConfig.getAllowedConsumers();
        // Add consumer only if not exists
        if (!CollUtil.contains(allowedConsumers, consumerId)) {
            allowedConsumers.add(consumerId);
            updateAIRoute(gateway, aiRoute);
        }

        HigressAuthConfig higressAuthConfig = HigressAuthConfig.builder()
                .resourceType("MODEL_API")
                .resourceName(modelRouteName)
                .build();

        return ConsumerAuthConfig.builder()
                .higressAuthConfig(higressAuthConfig)
                .build();
    }

    @Override
    public void revokeConsumerAuthorization(Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        HigressClient client = getClient(gateway);

        HigressAuthConfig higressAuthConfig = authConfig.getHigressAuthConfig();
        if (higressAuthConfig == null) {
            return;
        }

        if ("MCP_SERVER".equalsIgnoreCase(higressAuthConfig.getResourceType())) {
            client.execute("/v1/mcpServer/consumers/",
                    HttpMethod.DELETE,
                    null,
                    buildAuthHigressConsumer(higressAuthConfig.getResourceName(), consumerId),
                    Void.class);
        } else {
            HigressAIRoute aiRoute = fetchAIRoute(gateway, higressAuthConfig.getResourceName());
            RouteAuthConfig aiRouteAuthConfig = aiRoute.getAuthConfig();

            if (aiRouteAuthConfig == null || CollUtil.isEmpty(aiRouteAuthConfig.getAllowedConsumers())) {
                return;
            }

            aiRouteAuthConfig.getAllowedConsumers().remove(consumerId);
            updateAIRoute(gateway, aiRoute);
        }
    }

    private HigressAIRoute fetchAIRoute(Gateway gateway, String modelRouteName) {
        HigressClient client = getClient(gateway);

        HigressResponse<HigressAIRoute> response = client.execute("/v1/ai/routes/" + modelRouteName,
                HttpMethod.GET,
                null,
                null,
                new ParameterizedTypeReference<HigressResponse<HigressAIRoute>>() {
                });

        return response.getData();
    }

    private void updateAIRoute(Gateway gateway, HigressAIRoute aiRoute) {
        HigressClient client = getClient(gateway);

        client.execute("/v1/ai/routes/" + aiRoute.getName(),
                HttpMethod.PUT,
                null,
                aiRoute,
                Void.class);
    }

    @Override
    public HttpApiApiInfo fetchAPI(Gateway gateway, String apiId) {
        throw new UnsupportedOperationException("Higress gateway does not support fetching API");
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.HIGRESS;
    }

    @Override
    public String getDashboard(Gateway gateway, String type) {
        throw new UnsupportedOperationException("Higress gateway does not support getting dashboard");
    }

    @Override
    public List<String> fetchGatewayIps(Gateway gateway) {
        return Collections.emptyList();
    }

    @Data
    @Builder
    public static class HigressConsumerConfig {
        private String name;
        private List<HigressCredentialConfig> credentials;
    }

    @Data
    @Builder
    public static class HigressCredentialConfig {
        private String type;
        private String source;
        private String key;
        private List<String> values;
    }

    public HigressConsumerConfig buildHigressConsumer(String consumerId, ApiKeyConfig apiKeyConfig) {

        String source = mapSource(apiKeyConfig.getSource());

        List<String> apiKeys = apiKeyConfig.getCredentials().stream()
                .map(ApiKeyConfig.ApiKeyCredential::getApiKey)
                .collect(Collectors.toList());

        return HigressConsumerConfig.builder()
                .name(consumerId)
                .credentials(Collections.singletonList(
                        HigressCredentialConfig.builder()
                                .type("key-auth")
                                .source(source)
                                .key(apiKeyConfig.getKey())
                                .values(apiKeys)
                                .build())
                )
                .build();
    }

    @Data
    public static class HigressMCPConfig {
        private String name;
        private String type;
        private List<String> domains;
        private String rawConfigurations;
        private DirectRouteConfig directRouteConfig;
    }

    @Data
    public static class DirectRouteConfig {
        private String path;
        private String transportType;
    }

    @Data
    public static class HigressPageResponse<T> {
        private List<T> data;
        private int total;
    }

    @Data
    public static class HigressResponse<T> {
        private T data;
    }

    @Data
    public static class HigressDomainConfig {
        private String name;
        private String enableHttps;
    }

    // AI route definition start

    @Data
    public static class HigressAIRoute {
        private String name;
        private String version;
        private List<String> domains;
        private RoutePredicate pathPredicate;
        private List<KeyedRoutePredicate> headerPredicates;
        private List<KeyedRoutePredicate> urlParamPredicates;
        private List<AiUpstream> upstreams;
        private List<AiModelPredicate> modelPredicates;
        private RouteAuthConfig authConfig;
        private AiRouteFallbackConfig fallbackConfig;
    }

    public static class AiModelPredicate extends RoutePredicate {
    }

    @Data
    public static class AiUpstream {
        private String provider;
        private Integer weight;
        private Map<String, String> modelMapping;
    }

    @Data
    public static class RouteAuthConfig {
        private Boolean enabled;
        private List<String> allowedCredentialTypes;
        private List<String> allowedConsumers = new ArrayList<>();
    }

    @Data
    public static class AiRouteFallbackConfig {
        private Boolean enabled;
        private List<AiUpstream> upstreams;
        private String fallbackStrategy;
        private List<String> responseCodes;
    }

    // AI route definition end

    public HigressAuthConsumerConfig buildAuthHigressConsumer(String gatewayName, String consumerId) {
        return HigressAuthConsumerConfig.builder()
                .mcpServerName(gatewayName)
                .consumers(Collections.singletonList(consumerId))
                .build();
    }

    @Data
    @Builder
    public static class HigressAuthConsumerConfig {
        private String mcpServerName;
        private List<String> consumers;
    }

    private String mapSource(String source) {
        if (StringUtils.isBlank(source)) return null;
        if ("Default".equalsIgnoreCase(source)) return "BEARER";
        if ("HEADER".equalsIgnoreCase(source)) return "HEADER";
        if ("QueryString".equalsIgnoreCase(source)) return "QUERY";
        return source;
    }

}