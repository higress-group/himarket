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
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.higress.sdk.model.route.KeyedRoutePredicate;
import com.alibaba.higress.sdk.model.route.RoutePredicate;
import com.alibaba.himarket.dto.result.agent.AgentAPIResult;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.gateway.GatewayResult;
import com.alibaba.himarket.dto.result.httpapi.APIConfigResult;
import com.alibaba.himarket.dto.result.httpapi.APIResult;
import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMCPServerResult;
import com.alibaba.himarket.dto.result.mcp.MCPConfigResult;
import com.alibaba.himarket.dto.result.mcp.SofaHigressMCPServerResult;
import com.alibaba.himarket.dto.result.model.GatewayModelAPIResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.dto.result.model.SofaHigressModelResult;
import com.alibaba.himarket.entity.Consumer;
import com.alibaba.himarket.entity.ConsumerCredential;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.gateway.client.SofaHigressClient;
import com.alibaba.himarket.support.consumer.ApiKeyConfig;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.consumer.SofaHigressAuthConfig;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.gateway.GatewayConfig;
import com.alibaba.himarket.support.gateway.SofaHigressConfig;
import com.alibaba.himarket.support.product.SofaHigressRefConfig;
import com.aliyun.sdk.service.apig20240327.models.HttpApiApiInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SofaHigressOperator extends GatewayOperator<SofaHigressClient> {
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

        SofaHigressPageRequest<SofaHigressApiConfig> request = SofaHigressPageRequest.<SofaHigressApiConfig>builder()
                .pageInfo(PageInfo.builder()
                        .pageIndex((long) page)
                        .pageSize((long) size)
                        .build())
                .fuzzySearch(true)
                .param(SofaHigressApiConfig.builder()
                        .type("HTTP")
                        .build())
                .build()
                .autoFillTenantInfo();

        SofaHigressPageResponse<SofaHigressRouteConfig> response = client.execute(
                "/route/list",
                HttpMethod.POST,
                request,
                new TypeReference<>(){});

        List<APIResult> apiResults = response.getList()
                .stream()
                .map(s -> {
                    APIResult apiResult = new APIResult();
                    apiResult.setApiId(s.getRouteId());
                    apiResult.setApiName(s.getName());
                    return apiResult;
                })
                .toList();

        return PageResult.of(apiResults, page, size, response.getPageInfo().getTotal());
    }

    @Override
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway gateway, int page, int size) {
        SofaHigressClient client = getClient(gateway);

        SofaHigressPageRequest<SofaHigressMCPConfig> request = SofaHigressPageRequest.<SofaHigressMCPConfig>builder()
                .pageInfo(PageInfo.builder()
                        .pageIndex((long) page)
                        .pageSize((long) size)
                        .build())
                .fuzzySearch(true)
                .param(SofaHigressMCPConfig.builder()
                        .status("ONSHELF")
                        .build())
                .build()
                .autoFillTenantInfo();

        SofaHigressPageResponse<SofaHigressMCPConfig> response = client.execute(
                "/mcpServer/list",
                HttpMethod.POST,
                request,
                new TypeReference<>(){});

        List<SofaHigressMCPServerResult> mcpServers = response.getList().stream()
                .map(s -> new SofaHigressMCPServerResult().convertFrom(s))
                .toList();

        return PageResult.of(mcpServers, page, size, response.getPageInfo().getTotal());
    }

    @Override
    public PageResult<AgentAPIResult> fetchAgentAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<? extends GatewayModelAPIResult> fetchModelAPIs(Gateway gateway, int page, int size) {
        SofaHigressClient client = getClient(gateway);

        SofaHigressPageRequest<SofaHigressApiConfig> request = SofaHigressPageRequest.<SofaHigressApiConfig>builder()
                .pageInfo(PageInfo.builder()
                        .pageIndex((long) page)
                        .pageSize((long) size)
                        .build())
                .fuzzySearch(true)
                .param(SofaHigressApiConfig.builder()
                        .type("AI")
                        .build())
                .build()
                .autoFillTenantInfo();

        SofaHigressPageResponse<SofaHigressApiConfig> response = client.execute(
                "/api/list",
                HttpMethod.POST,
                request,
                new TypeReference<>(){});

        List<SofaHigressModelResult> modelResults = response.getList()
                .stream()
                .map(s -> SofaHigressModelResult.builder()
                        .modelApiId(s.getApiId())
                        .modelApiName(s.getName())
                        .build())
                .toList();

        return PageResult.of(modelResults, page, size, response.getPageInfo().getTotal());
    }

    @Override
    public String fetchAPIConfig(Gateway gateway, Object config) {
        SofaHigressClient client = getClient(gateway);
        SofaHigressRefConfig refConfig = (SofaHigressRefConfig) config;

        // 通过 routeId 查询 api-route 详情
        SofaHigressRouteConfig response = client.execute(
                "/route/detail",
                HttpMethod.POST,
                BaseRequest.<SofaHigressRouteConfig>builder()
                        .param(SofaHigressRouteConfig.builder()
                                .routeId(refConfig.getApiId())
                                .build())
                        .build()
                        .autoFillTenantInfo(),
                new TypeReference<>(){});

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

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        SofaHigressRefConfig config = (SofaHigressRefConfig) conf;

        SofaHigressMCPConfig response = fetchSofaHigressMCPConfig(gateway, config.getServerId());
        // 通过响应构建 MCP 配置结果
        MCPConfigResult mcpConfigResult = new MCPConfigResult();
        mcpConfigResult.setMcpServerName(response.getName());
        // mcpServerConfig需要path和domain信息
        MCPConfigResult.MCPServerConfig mcpServerConfig = new MCPConfigResult.MCPServerConfig();
        mcpServerConfig.setPath(response.getPath());
        List<String> domains = response.getDomains();
        mcpServerConfig.setDomains(domainConvert(gateway, domains));
        mcpConfigResult.setMcpServerConfig(mcpServerConfig);
        // 设置工具信息
        mcpConfigResult.setTools(JSON.toJSONString(response.getTools()));
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
                        .param(SofaHigressMCPConfig.builder()
                                .serverId(serverId)
                                .build())
                        .build()
                        .autoFillTenantInfo(),
                new TypeReference<>(){});
    }

    private SofaHigressDomainConfig fetchDomain(Gateway gateway, String domain) {
        SofaHigressClient client = getClient(gateway);
        SofaHigressDomainConfig response = client.execute(
                "/domain/detailByName",
                HttpMethod.POST,
                BaseRequest.<SofaHigressDomainConfig>builder()
                        .param(SofaHigressDomainConfig.builder()
                                .domainName(domain)
                                .build())
                        .build()
                        .autoFillTenantInfo(),
                new TypeReference<>(){});
        return response;
    }

    private List<DomainResult> domainConvert(Gateway gateway, List<String> domains) {
        if (CollUtil.isEmpty(domains)) {
            List<String> gatewayIps = fetchGatewayIps(gateway);
            String domain = CollUtil.isEmpty(gatewayIps) ? "<sofa-higress-gateway-ip>" : gatewayIps.get(0);
            return Collections.singletonList(DomainResult.builder().domain(domain).protocol("http").build());
        }
        return domains.stream().map(domain -> {
                    SofaHigressDomainConfig domainConfig = fetchDomain(gateway, domain);
                    String protocol =
                            (domainConfig == null ||
                                    domainConfig.getEnableHttps().equalsIgnoreCase("off"))
                                    ? "http" : "https";
                    return DomainResult.builder()
                            .domain(domain)
                            .protocol(protocol)
                            .build();
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
        SofaHigressApiConfig response = fetchSofaHigressApiConfig(gateway, refConfig.getModelApiId());

        ModelConfigResult result = new ModelConfigResult();
        // AI route
        List<String> domains = response.getRouteInfo().getDomains();
        List<HttpRouteResult> routeResults =
                Collections.singletonList(new HttpRouteResult().convertFrom(response.getRouteInfo(), domainConvert(gateway, domains)));
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

    private SofaHigressApiConfig fetchSofaHigressApiConfig(Gateway gateway, String apiId) {
        SofaHigressClient client = getClient(gateway);
        // 通过 serverId 查询 Mcp 详情
        return client.execute(
                "/api/detail",
                HttpMethod.POST,
                BaseRequest.<SofaHigressApiConfig>builder()
                        .param(SofaHigressApiConfig.builder()
                                .apiId(apiId)
                                .build())
                        .build()
                        .autoFillTenantInfo(),
                new TypeReference<>(){});
    }

    @Override
    public PageResult<GatewayResult> fetchGateways(Object param, int page, int size) {
        throw new UnsupportedOperationException(
                "Sofa Higress gateway does not support fetching Gateways");
    }

    @Override
    public String createConsumer(Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        SofaHigressConfig sofaHigressConfig = config.getSofaHigressConfig();
        SofaHigressClient client = new SofaHigressClient(sofaHigressConfig);
        SofaHigressConsumerConfig createdConsumer = client.execute(
                "/consumer/create",
                HttpMethod.POST,
                buildSofaHigressConsumer(null, consumer.getConsumerId(), credential.getApiKeyConfig()),
                new TypeReference<>(){});
        return createdConsumer.getConsumerId();
    }

    @Override
    public void updateConsumer(String consumerId, ConsumerCredential credential, GatewayConfig config) {
        SofaHigressConfig sofaHigressConfig = config.getSofaHigressConfig();
        SofaHigressClient client = new SofaHigressClient(sofaHigressConfig);
        client.execute(
                "/consumer/update",
                HttpMethod.POST,
                buildSofaHigressConsumer(consumerId, null, credential.getApiKeyConfig()),
                new TypeReference<>(){});
    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {
        SofaHigressConfig sofaHigressConfig = config.getSofaHigressConfig();
        SofaHigressClient client = new SofaHigressClient(sofaHigressConfig);
        client.execute(
                "/consumer/delete",
                HttpMethod.POST,
                buildSofaHigressConsumer(consumerId),
                new TypeReference<>(){});
    }

    @Override
    public boolean isConsumerExists(String consumerId, GatewayConfig config) {
        SofaHigressConfig sofaHigressConfig = config.getSofaHigressConfig();
        SofaHigressClient client = new SofaHigressClient(sofaHigressConfig);
        SofaHigressConsumerConfig consumerConfig = client.execute(
                "/consumer/detail",
                HttpMethod.POST,
                buildSofaHigressConsumer(consumerId),
                new TypeReference<>(){});
        return consumerConfig == null;
    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(Gateway gateway, String consumerId, Object refConfig) {
        SofaHigressRefConfig sofaHigressRefConfig = (SofaHigressRefConfig) refConfig;

        String serverId = sofaHigressRefConfig.getServerId();
        String modelApiId = sofaHigressRefConfig.getModelApiId();

        // MCP or AIRoute
        return StrUtil.isNotBlank(serverId)
                ? authorizeMCPServer(gateway, consumerId, serverId)
                : authorizeAIRoute(gateway, consumerId, modelApiId);
    }

    private ConsumerAuthConfig authorizeMCPServer(Gateway gateway, String consumerId, String serverId) {
        SofaHigressClient client = getClient(gateway);
        SofaHigressMCPConfig response = fetchSofaHigressMCPConfig(gateway, serverId);

        // 通过MCP server的routeId和consumerId进行请求订阅
        client.execute(
                "/mcpServer/sub",
                HttpMethod.POST,
                SubOrUnSubRequest.builder()
                        .consumerId(consumerId)
                        .routerId(response.getRouteId())
                        .build()
                        .autoFillTenantInfo(),
                new TypeReference<>(){});

        SofaHigressAuthConfig sofaHigressAuthConfig = SofaHigressAuthConfig.builder()
                .resourceType("MCP_SERVER")
                .resourceId(response.getRouteId())
                .build();

        return ConsumerAuthConfig.builder()
                .sofaHigressAuthConfig(sofaHigressAuthConfig)
                .build();
    }

    private ConsumerAuthConfig authorizeAIRoute(Gateway gateway, String consumerId, String modelApiId) {
        SofaHigressClient client = getClient(gateway);
        SofaHigressApiConfig response = fetchSofaHigressApiConfig(gateway, modelApiId);

        client.execute(
                "/route/sub",
                HttpMethod.POST,
                SubOrUnSubRequest.builder()
                        .consumerId(consumerId)
                        .routerId(response.getRouteInfo().getRouteId())
                        .build()
                        .autoFillTenantInfo(),
                new TypeReference<>(){});

        SofaHigressAuthConfig sofaHigressAuthConfig = SofaHigressAuthConfig.builder()
                .resourceType("MODEL_API")
                .resourceId(response.getRouteInfo().getRouteId())
                .build();

        return ConsumerAuthConfig.builder()
                .sofaHigressAuthConfig(sofaHigressAuthConfig)
                .build();
    }

    @Override
    public void revokeConsumerAuthorization(Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        SofaHigressAuthConfig sofaHigressAuthConfig = authConfig.getSofaHigressAuthConfig();
        if (sofaHigressAuthConfig == null) {
            return;
        }

        if (sofaHigressAuthConfig.getResourceType().equalsIgnoreCase("MCP_SERVER")) {
            revokeAuthorizeMCPServer(gateway, consumerId, sofaHigressAuthConfig.getResourceId());
        } else {
            revokeAuthorizeAIRoute(gateway, consumerId, sofaHigressAuthConfig.getResourceId());
        }

    }

    private void revokeAuthorizeMCPServer(Gateway gateway, String consumerId, String serverId) {
        SofaHigressClient client = getClient(gateway);
        SofaHigressMCPConfig response = fetchSofaHigressMCPConfig(gateway, serverId);

        // 通过MCP server的routeId和consumerId进行请求订阅
        client.execute(
                "/mcpServer/unsub",
                HttpMethod.POST,
                SubOrUnSubRequest.builder()
                        .consumerId(consumerId)
                        .routerId(response.getRouteId())
                        .build()
                        .autoFillTenantInfo(),
                new TypeReference<>(){});
    }

    private void revokeAuthorizeAIRoute(Gateway gateway, String consumerId, String modelApiId) {
        SofaHigressClient client = getClient(gateway);
        SofaHigressApiConfig response = fetchSofaHigressApiConfig(gateway, modelApiId);

        client.execute(
                "/route/unsub",
                HttpMethod.POST,
                SubOrUnSubRequest.builder()
                        .consumerId(consumerId)
                        .routerId(response.getRouteInfo().getRouteId())
                        .build()
                        .autoFillTenantInfo(),
                new TypeReference<>(){});
    }

    @Override
    public HttpApiApiInfo fetchAPI(Gateway gateway, String apiId) {
        throw new UnsupportedOperationException("Sofa Higress gateway does not support fetching API");
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.SOFA_HIGRESS;
    }

    @Override
    public String getDashboard(Gateway gateway, String type) {
        throw new UnsupportedOperationException(
                "Sofa Higress gateway does not support getting dashboard");
    }

    @Override
    public List<String> fetchGatewayIps(Gateway gateway) {
        SofaHigressClient client = getClient(gateway);
        String gatewayUrl = client.execute(
                "/gatewayUrl",
                HttpMethod.POST,
                new BaseRequest().autoFillTenantInfo());
        String gatewayIp = StrUtil.subAfter(gatewayUrl, "://", true).trim();

        return StrUtil.isNotBlank(gatewayIp)
                ? Collections.singletonList(gatewayIp)
                : CollUtil.empty(List.class);
    }

    public BaseRequest<SofaHigressConsumerConfig> buildSofaHigressConsumer(
            String consumerId, String consumerName, ApiKeyConfig apiKeyConfig) {
        String source = mapSource(apiKeyConfig.getSource());
        // todo: sofa-higress目前只支持一个消费者绑定一个认证凭证，
        //  所以只取Credentials的第一个apiKey，后续需要拓展以支持多个认证凭证
        String value = apiKeyConfig.getCredentials().get(0).getApiKey();
        SofaHigressConsumerConfig consumerConfig = SofaHigressConsumerConfig.builder()
                .consumerId(consumerId)
                .name(consumerName)
                .description("consumer from Himarket")
                .keyAuthConfig(
                        KeyAuthConfig.builder()
                                .source(source)
                                .key(apiKeyConfig.getKey())
                                .value(value)
                                .build())
                .build();
        return BaseRequest.<SofaHigressConsumerConfig>builder()
                .param(consumerConfig)
                .build()
                .autoFillTenantInfo();
    }

    public BaseRequest<SofaHigressConsumerConfig> buildSofaHigressConsumer(String consumerId) {
        SofaHigressConsumerConfig consumerConfig = SofaHigressConsumerConfig.builder()
                .consumerId(consumerId)
                .build();
        return BaseRequest.<SofaHigressConsumerConfig>builder()
                .param(consumerConfig)
                .build()
                .autoFillTenantInfo();
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

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class BaseRequest<T> {
        protected String tenantId;
        protected String workspaceId;
        protected T param;

        public BaseRequest<T> autoFillTenantInfo() {
            this.tenantId = "alipay";
            this.workspaceId = "sofa-higress";
            return this;
        }
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class SofaHigressPageRequest<T> extends BaseRequest<T> {
        private PageInfo    pageInfo;
        private Boolean     fuzzySearch;
        private String      queryType;

        public SofaHigressPageRequest<T> autoFillTenantInfo() {
            this.tenantId = "alipay";
            this.workspaceId = "sofa-higress";
            return this;
        }
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class SubOrUnSubRequest extends BaseRequest<Object> {
        private String routerId;
        private String consumerId;

        public SubOrUnSubRequest autoFillTenantInfo() {
            this.tenantId = "alipay";
            this.workspaceId = "sofa-higress";
            return this;
        }
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
        private String               serverId;
        private String               name;
        private String               nameCn;
        private String               routeId;
        private String               serviceId;
        private String               type;
        private String               categoryId;
        private String               categoryName;
        private String               introduction;
        private List<McpToolVO>      tools;
        private String               path;
        private String               config;
        private String               status;
        private String               description;
        private String               upstreamProtocol;
        private String               upstreamPrefix;
        // private QueryRateLimitConfig queryRateLimitConfig;
        // private UpstreamTokenModel   upstreamToken;
        // private AiRouteAuthConfig    authConfig;
        private String               sseUrl;
        private String               sseUrlExample;
        private String               queryType;
        private List<String>         domains;
        private String               serviceName;
        private String               queryContent;
    }

    @Data
    public static class McpToolVO {
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
        private List<McpToolHeaderVO> mcpToolHeaders;
        private String routerId;
    }

    @Data
    public static class McpToolHeaderVO {
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
//        private AiUpstreamConfig aiUpstreamConfig;
//        private UpstreamConfig upstreamConfig;
//        private RouteFallbackConfig routeFallbackConfig;
//        private TimeoutConfig timeoutConfig;
//        private ProxyNextUpstreamConfig proxyNextUpstream;
//        private RewriteConfig rewriteConfig;
//        private LoadBalanceConfig loadBalanceConfig;
//        private CorsConfig corsConfig;
//        private HeaderControlConfig headerControl;
//        private AiRouteAuthConfig authConfig;
//        private AiPluginConfig aiPluginConfig;
//        private QueryRateLimitConfig queryRateLimitConfig;
//        private MockConfig mockConfig;
//        private RedirectConfig redirectConfig;
//        private CircuitBreakerConfig circuitBreakerConfig;
//        private ConsumerCallStatisticsConfig statisticsConfig;
//        private AnnotationConfig annotationConfig;
//        private TrafficMirrorConfig trafficMirrorConfig;
//        private IpControlConfig ipConfig;
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
        private String            consumerId;
        private String            name;
        private String            description;
        private KeyAuthConfig     keyAuthConfig;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyAuthConfig {
        private String key;
        private String source;
        private String value;
    }
}
