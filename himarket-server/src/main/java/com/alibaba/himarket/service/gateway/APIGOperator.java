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
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.gateway.QueryAPIGParam;
import com.alibaba.himarket.dto.result.agent.AgentAPIResult;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.gateway.GatewayResult;
import com.alibaba.himarket.dto.result.httpapi.APIConfigResult;
import com.alibaba.himarket.dto.result.httpapi.APIResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMcpServerResult;
import com.alibaba.himarket.dto.result.model.GatewayModelAPIResult;
import com.alibaba.himarket.entity.Consumer;
import com.alibaba.himarket.entity.ConsumerCredential;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.service.gateway.client.APIGClient;
import com.alibaba.himarket.support.consumer.APIGAuthConfig;
import com.alibaba.himarket.support.consumer.ApiKeyConfig;
import com.alibaba.himarket.support.consumer.ConsumerAuthConfig;
import com.alibaba.himarket.support.consumer.HmacConfig;
import com.alibaba.himarket.support.enums.APIGAPIType;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.gateway.GatewayConfig;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.alibaba.himarket.utils.JsonUtil;
import com.aliyun.sdk.gateway.pop.exception.PopClientException;
import com.aliyun.sdk.service.apig20240327.models.*;
import com.aliyun.sdk.service.apig20240327.models.CreateConsumerAuthorizationRulesRequest.AuthorizationRules;
import com.aliyun.sdk.service.apig20240327.models.CreateConsumerAuthorizationRulesRequest.ResourceIdentifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
@Primary
public class APIGOperator extends GatewayOperator<APIGClient> {

    @Override
    public PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size) {
        return fetchAPIs(gateway, APIGAPIType.HTTP, page, size);
    }

    @Override
    public PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size) {
        return fetchAPIs(gateway, APIGAPIType.REST, page, size);
    }

    @Override
    public PageResult<? extends GatewayMcpServerResult> fetchMcpServers(
            Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException("APIG does not support MCP Servers");
    }

    @Override
    public PageResult<AgentAPIResult> fetchAgentAPIs(Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException("APIG does not support Agent APIs");
    }

    @Override
    public PageResult<? extends GatewayModelAPIResult> fetchModelAPIs(
            Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException("APIG does not support Model APIs");
    }

    @Override
    public String fetchAPIConfig(Gateway gateway, Object config) {
        APIGClient client = getClient(gateway);

        APIGRefConfig apigRefConfig = (APIGRefConfig) config;
        ExportHttpApiRequest request =
                ExportHttpApiRequest.builder().httpApiId(apigRefConfig.getApiId()).build();
        ExportHttpApiResponse response = client.execute(c -> c.exportHttpApi(request)).join();
        if (response.getStatusCode() != 200) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        String contentBase64 = response.getBody().getData().getSpecContentBase64();

        APIConfigResult configResult = new APIConfigResult();
        // spec
        String apiSpec = Base64.decodeStr(contentBase64);
        configResult.setSpec(apiSpec);

        // meta
        APIConfigResult.APIMetadata meta = new APIConfigResult.APIMetadata();
        meta.setSource(GatewayType.APIG_API.name());
        meta.setType("REST");
        configResult.setMeta(meta);

        return JsonUtil.toJson(configResult);
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        throw new UnsupportedOperationException("APIG does not support MCP Servers");
    }

    @Override
    public String fetchAgentConfig(Gateway gateway, Object conf) {
        throw new UnsupportedOperationException("APIG does not support Agent APIs");
    }

    @Override
    public String fetchModelConfig(Gateway gateway, Object conf) {
        throw new UnsupportedOperationException("APIG does not support Model APIs");
    }

    @Override
    public CredentialContext fetchApiCredential(
            Gateway gateway, ProductType productType, ProductRef productRef) {
        throw new UnsupportedOperationException("APIG does not support API credentials");
    }

    @Override
    public PageResult<GatewayResult> fetchGateways(Object param, int page, int size) {
        return fetchGateways((QueryAPIGParam) param, page, size);
    }

    public PageResult<GatewayResult> fetchGateways(QueryAPIGParam param, int page, int size) {
        APIGClient client = new APIGClient(param.convertTo());

        List<GatewayResult> gateways = new ArrayList<>();
        ListGatewaysRequest request =
                ListGatewaysRequest.builder()
                        .gatewayType(param.getGatewayType().getType())
                        .pageNumber(page)
                        .pageSize(size)
                        .build();
        ListGatewaysResponse response = client.execute(c -> c.listGateways(request)).join();
        if (response.getStatusCode() != 200) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        for (ListGatewaysResponseBody.Items item : response.getBody().getData().getItems()) {
            gateways.add(
                    GatewayResult.builder()
                            .gatewayName(item.getName())
                            .gatewayId(item.getGatewayId())
                            .gatewayType(param.getGatewayType())
                            .build());
        }

        int total = Math.toIntExact(response.getBody().getData().getTotalSize());
        return PageResult.of(gateways, page, size, total);
    }

    protected String fetchGatewayEnv(Gateway gateway) {
        APIGClient client = getClient(gateway);
        GetGatewayRequest request =
                GetGatewayRequest.builder().gatewayId(gateway.getGatewayId()).build();
        GetGatewayResponse response = client.execute(c -> c.getGateway(request)).join();
        if (response.getStatusCode() != 200) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        List<GetGatewayResponseBody.Environments> environments =
                response.getBody().getData().getEnvironments();
        if (CollUtil.isEmpty(environments)) {
            return null;
        }

        return environments.get(0).getEnvironmentId();
    }

    @Override
    public String createConsumer(
            Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        APIGClient client = new APIGClient(config.getApigConfig());

        String mark =
                consumer.getDeveloperId()
                        .substring(Math.max(0, consumer.getDeveloperId().length() - 8));
        String gwConsumerName = StrUtil.format("{}-{}", consumer.getName(), mark);
        try {
            // ApiKey
            ApiKeyIdentityConfig apikeyIdentityConfig =
                    convertToApiKeyIdentityConfig(credential.getApiKeyConfig());

            // Hmac
            List<AkSkIdentityConfig> akSkIdentityConfigs =
                    convertToAkSkIdentityConfigs(credential.getHmacConfig());

            CreateConsumerRequest.Builder builder =
                    CreateConsumerRequest.builder()
                            .name(gwConsumerName)
                            .description("Created by HiMarket")
                            .gatewayType(config.getGatewayType().getType())
                            .enable(true);
            if (apikeyIdentityConfig != null) {
                builder.apikeyIdentityConfig(apikeyIdentityConfig);
            }
            if (akSkIdentityConfigs != null) {
                builder.akSkIdentityConfigs(akSkIdentityConfigs);
            }

            CompletableFuture<CreateConsumerResponse> f =
                    client.execute(c -> c.createConsumer(builder.build()));

            CreateConsumerResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(
                        ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            return response.getBody().getData().getConsumerId();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            // Consumer already exists
            if (cause instanceof PopClientException
                    && "Conflict.ConsumerNameDuplicate"
                            .equals(((PopClientException) cause).getErrCode())) {
                return retrievalConsumer(gwConsumerName, config);
            }
            log.error(
                    "Failed to create gateway consumer, dependency=APIG, operation=createConsumer,"
                            + " consumerName={}, errorType={}, errorMessage={}",
                    gwConsumerName,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to create Consumer: " + e.getMessage());
        }
    }

    private String retrievalConsumer(String name, GatewayConfig gatewayConfig) {
        APIGClient client = new APIGClient(gatewayConfig.getApigConfig());

        ListConsumersRequest request =
                ListConsumersRequest.builder()
                        .gatewayType(gatewayConfig.getGatewayType().getType())
                        .nameLike(name)
                        .pageNumber(1)
                        .pageSize(10)
                        .build();
        ListConsumersResponse response = client.execute(c -> c.listConsumers(request)).join();
        if (response.getStatusCode() != 200) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        for (ListConsumersResponseBody.Items item : response.getBody().getData().getItems()) {
            if (StrUtil.equals(item.getName(), name)) {
                return item.getConsumerId();
            }
        }
        return null;
    }

    @Override
    public void updateConsumer(
            String consumerId, ConsumerCredential credential, GatewayConfig config) {
        APIGClient client = new APIGClient(config.getApigConfig());

        // ApiKey
        ApiKeyIdentityConfig apikeyIdentityConfig =
                convertToApiKeyIdentityConfig(credential.getApiKeyConfig());

        // Hmac
        List<AkSkIdentityConfig> akSkIdentityConfigs =
                convertToAkSkIdentityConfigs(credential.getHmacConfig());

        UpdateConsumerRequest.Builder builder =
                UpdateConsumerRequest.builder().enable(true).consumerId(consumerId);

        if (apikeyIdentityConfig != null) {
            builder.apikeyIdentityConfig(apikeyIdentityConfig);
        }

        if (akSkIdentityConfigs != null) {
            builder.akSkIdentityConfigs(akSkIdentityConfigs);
        }

        UpdateConsumerResponse response =
                client.execute(c -> c.updateConsumer(builder.build())).join();
        if (response.getStatusCode() != 200) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }
    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {
        APIGClient client = new APIGClient(config.getApigConfig());
        DeleteConsumerRequest request =
                DeleteConsumerRequest.builder().consumerId(consumerId).build();
        client.execute(
                c -> {
                    c.deleteConsumer(request);
                    return null;
                });
    }

    @Override
    public boolean isConsumerExists(String consumerId, GatewayConfig config) {
        APIGClient client = new APIGClient(config.getApigConfig());

        try {
            GetConsumerRequest request =
                    GetConsumerRequest.builder().consumerId(consumerId).build();
            GetConsumerResponse response = client.execute(c -> c.getConsumer(request)).join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(
                        ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }
            return true;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof PopClientException
                    && "DatabaseError.RecordNotFound"
                            .equals(((PopClientException) cause).getErrCode())) {
                return false;
            }

            log.error(
                    "Failed to fetch gateway consumer, dependency=APIG,"
                            + " operation=isConsumerExists, consumerId={}, errorType={},"
                            + " errorMessage={}",
                    consumerId,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to fetch Consumer: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    protected CredentialContext fetchConsumerCredential(Gateway gateway, String consumerId) {
        GetConsumerRequest request = GetConsumerRequest.builder().consumerId(consumerId).build();
        GetConsumerResponse response =
                getClient(gateway).execute(c -> c.getConsumer(request)).join();
        if (response.getStatusCode() != 200) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        ApiKeyIdentityConfig apiKeyConfig = null;
        GetConsumerResponseBody body = response.getBody();
        if (body != null && body.getData() != null) {
            apiKeyConfig = body.getData().getApiKeyIdentityConfig();
        }
        if (apiKeyConfig == null || CollUtil.isEmpty(apiKeyConfig.getCredentials())) {
            return CredentialContext.builder().build();
        }

        String apiKey =
                apiKeyConfig.getCredentials().stream()
                        .map(ApiKeyIdentityConfig.Credentials::getApikey)
                        .filter(StrUtil::isNotBlank)
                        .findFirst()
                        .orElse(null);
        if (StrUtil.isBlank(apiKey)) {
            return CredentialContext.builder().build();
        }

        CredentialContext context = CredentialContext.builder().apiKey(apiKey).build();
        ApiKeyIdentityConfig.ApikeySource sourceConfig = apiKeyConfig.getApikeySource();
        String source = "Default";
        String key = "Authorization";
        if (sourceConfig != null) {
            if (StrUtil.isNotBlank(sourceConfig.getSource())) {
                source = sourceConfig.getSource();
            }
            if (StrUtil.isNotBlank(sourceConfig.getValue())) {
                key = sourceConfig.getValue();
            }
        }

        switch (source.toUpperCase()) {
            case "DEFAULT", "BEARER" ->
                    context.getHeaders().put("Authorization", "Bearer " + apiKey);
            case "QUERYSTRING", "QUERY" -> context.getQueryParams().put(key, apiKey);
            default -> context.getHeaders().put(key, apiKey);
        }

        return context;
    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(
            Gateway gateway, String consumerId, Object refConfig) {
        APIGClient client = getClient(gateway);

        APIGRefConfig config = (APIGRefConfig) refConfig;
        // REST API authorization
        String apiId = config.getApiId();

        List<HttpApiOperationInfo> operations = fetchRESTOperations(gateway, apiId);
        if (CollUtil.isEmpty(operations)) {
            return null;
        }

        // Confirm the gateway environment ID
        String envId = fetchGatewayEnv(gateway);

        List<AuthorizationRules> rules = new ArrayList<>();
        for (HttpApiOperationInfo operation : operations) {
            AuthorizationRules rule =
                    AuthorizationRules.builder()
                            .consumerId(consumerId)
                            .expireMode("LongTerm")
                            .resourceType("RestApiOperation")
                            .resourceIdentifier(
                                    ResourceIdentifier.builder()
                                            .resourceId(operation.getOperationId())
                                            .environmentId(envId)
                                            .build())
                            .build();
            rules.add(rule);
        }

        CreateConsumerAuthorizationRulesRequest request =
                CreateConsumerAuthorizationRulesRequest.builder().authorizationRules(rules).build();
        CreateConsumerAuthorizationRulesResponse response =
                client.execute(c -> c.createConsumerAuthorizationRules(request)).join();
        if (200 != response.getStatusCode()) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        APIGAuthConfig apigAuthConfig =
                APIGAuthConfig.builder()
                        .authorizationRuleIds(
                                response.getBody().getData().getConsumerAuthorizationRuleIds())
                        .build();

        return ConsumerAuthConfig.builder().apigAuthConfig(apigAuthConfig).build();
    }

    @Override
    public void revokeConsumerAuthorization(
            Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        APIGAuthConfig apigAuthConfig = authConfig.getApigAuthConfig();
        if (apigAuthConfig == null) {
            return;
        }

        APIGClient client = getClient(gateway);

        try {
            BatchDeleteConsumerAuthorizationRuleRequest request =
                    BatchDeleteConsumerAuthorizationRuleRequest.builder()
                            .consumerAuthorizationRuleIds(
                                    StrUtil.join(",", apigAuthConfig.getAuthorizationRuleIds()))
                            .build();

            CompletableFuture<BatchDeleteConsumerAuthorizationRuleResponse> f =
                    client.execute(c -> c.batchDeleteConsumerAuthorizationRule(request));

            BatchDeleteConsumerAuthorizationRuleResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(
                        ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof PopClientException
                    && "DatabaseError.RecordNotFound"
                            .equals(((PopClientException) cause).getErrCode())) {
                log.warn(
                        "Consumer authorization rules not found, skipping revocation,"
                                + " dependency=APIG, operation=revokeConsumerAuthorization,"
                                + " authorizationRuleIds={}",
                        apigAuthConfig.getAuthorizationRuleIds());
                return;
            }

            log.error(
                    "Failed to delete consumer authorization, dependency=APIG,"
                            + " operation=revokeConsumerAuthorization, consumerId={},"
                            + " errorType={}, errorMessage={}",
                    consumerId,
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to delete Consumer Authorization: " + e.getMessage());
        }
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.APIG_API;
    }

    @Override
    public List<URI> fetchGatewayUris(Gateway gateway) {
        APIGClient client = getClient(gateway);
        GetGatewayRequest request =
                GetGatewayRequest.builder().gatewayId(gateway.getGatewayId()).build();
        GetGatewayResponse response = client.execute(c -> c.getGateway(request)).join();
        if (response.getStatusCode() != 200) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        List<GetGatewayResponseBody.LoadBalancers> loadBalancers =
                response.getBody().getData().getLoadBalancers();
        return loadBalancers.stream()
                // Only internet load balancer support
                .filter(
                        loadBalancer ->
                                StrUtil.equalsIgnoreCase(loadBalancer.getAddressType(), "internet"))
                .map(GetGatewayResponseBody.LoadBalancers::getIpv4Addresses)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(
                        ip -> {
                            try {
                                // Build gateway URI with http scheme by default
                                return new URI("http://" + ip);
                            } catch (URISyntaxException e) {
                                log.error(
                                        "Failed to create gateway URI, dependency=APIG,"
                                                + " operation=fetchGatewayUris, ip={},"
                                                + " errorType={}, errorMessage={}",
                                        ip,
                                        e.getClass().getSimpleName(),
                                        e.getMessage(),
                                        e);
                                return null;
                            }
                        })
                .filter(Objects::nonNull)
                .toList();
    }

    public HttpApiApiInfo fetchAPI(Gateway gateway, String apiId) {
        APIGClient client = getClient(gateway);
        GetHttpApiRequest request = GetHttpApiRequest.builder().httpApiId(apiId).build();
        GetHttpApiResponse response = client.execute(c -> c.getHttpApi(request)).join();
        if (response.getStatusCode() != 200) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        return response.getBody().getData();
    }

    protected HttpRoute fetchHTTPRoute(Gateway gateway, String apiId, String routeId) {
        APIGClient client = getClient(gateway);

        GetHttpApiRouteRequest request =
                GetHttpApiRouteRequest.builder().httpApiId(apiId).routeId(routeId).build();
        GetHttpApiRouteResponse response = client.execute(c -> c.getHttpApiRoute(request)).join();
        if (response.getStatusCode() != 200) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        return response.getBody().getData();
    }

    protected PageResult<APIResult> fetchAPIs(
            Gateway gateway, APIGAPIType type, int page, int size) {
        APIGClient client = getClient(gateway);
        List<APIResult> apis = new ArrayList<>();
        ListHttpApisRequest request =
                ListHttpApisRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .gatewayType(gateway.getGatewayType().getType())
                        .types(type.getType())
                        .pageNumber(page)
                        .pageSize(size)
                        .build();
        ListHttpApisResponse response = client.execute(c -> c.listHttpApis(request)).join();
        if (response.getStatusCode() != 200) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        for (HttpApiInfoByName item : response.getBody().getData().getItems()) {
            for (HttpApiApiInfo apiInfo : item.getVersionedHttpApis()) {
                APIResult apiResult = new APIResult().convertFrom(apiInfo);
                apis.add(apiResult);
                break;
            }
        }

        int total = response.getBody().getData().getTotalSize();
        return PageResult.of(apis, page, size, total);
    }

    public PageResult<HttpRoute> fetchHttpRoutes(
            Gateway gateway, String apiId, int page, int size) {
        APIGClient client = getClient(gateway);
        ListHttpApiRoutesRequest request =
                ListHttpApiRoutesRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .httpApiId(apiId)
                        .pageNumber(page)
                        .pageSize(size)
                        .build();
        ListHttpApiRoutesResponse response =
                client.execute(c -> c.listHttpApiRoutes(request)).join();
        if (response.getStatusCode() != 200) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }
        List<HttpRoute> httpRoutes = response.getBody().getData().getItems();
        int total = response.getBody().getData().getTotalSize();
        return PageResult.of(httpRoutes, page, size, total);
    }

    public List<HttpApiOperationInfo> fetchRESTOperations(Gateway gateway, String apiId) {
        APIGClient client = getClient(gateway);

        ListHttpApiOperationsRequest request =
                ListHttpApiOperationsRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .httpApiId(apiId)
                        .pageNumber(1)
                        .pageSize(500)
                        .build();
        ListHttpApiOperationsResponse response =
                client.execute(c -> c.listHttpApiOperations(request)).join();
        if (response.getStatusCode() != 200) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        return response.getBody().getData().getItems();
    }

    protected ApiKeyIdentityConfig convertToApiKeyIdentityConfig(ApiKeyConfig config) {
        if (config == null) {
            return null;
        }

        // ApikeySource
        ApiKeyIdentityConfig.ApikeySource apikeySource =
                ApiKeyIdentityConfig.ApikeySource.builder()
                        .source(config.getSource())
                        .value(config.getKey())
                        .build();

        // credentials
        List<ApiKeyIdentityConfig.Credentials> credentials =
                config.getCredentials().stream()
                        .map(
                                cred ->
                                        ApiKeyIdentityConfig.Credentials.builder()
                                                .apikey(cred.getApiKey())
                                                .generateMode("Custom")
                                                .build())
                        .toList();

        return ApiKeyIdentityConfig.builder()
                .apikeySource(apikeySource)
                .credentials(credentials)
                .type("Apikey")
                .build();
    }

    protected List<AkSkIdentityConfig> convertToAkSkIdentityConfigs(HmacConfig hmacConfig) {
        if (hmacConfig == null || hmacConfig.getCredentials() == null) {
            return null;
        }

        return hmacConfig.getCredentials().stream()
                .map(
                        cred ->
                                AkSkIdentityConfig.builder()
                                        .ak(cred.getAk())
                                        .sk(cred.getSk())
                                        .generateMode("Custom")
                                        .type("AkSk")
                                        .build())
                .toList();
    }

    protected List<DomainResult> extractAPIDomains(HttpApiApiInfo apiInfo) {
        if (apiInfo == null || apiInfo.getEnvironments() == null) {
            return Collections.emptyList();
        }

        Stream<DomainResult> subDomains =
                apiInfo.getEnvironments().stream()
                        .map(HttpApiApiInfo.Environments::getSubDomains)
                        .filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .map(
                                subDomain ->
                                        DomainResult.builder()
                                                .domain(subDomain.getName())
                                                .protocol(subDomain.getProtocol())
                                                .networkType(subDomain.getNetworkType())
                                                .build())
                        .filter(result -> result.getDomain() != null);

        Stream<DomainResult> customDomains =
                apiInfo.getEnvironments().stream()
                        .map(HttpApiApiInfo.Environments::getCustomDomains)
                        .filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .map(
                                customDomain ->
                                        DomainResult.builder()
                                                .domain(customDomain.getName())
                                                .protocol(customDomain.getProtocol())
                                                .build())
                        .filter(result -> result.getDomain() != null);

        List<DomainResult> domains = new ArrayList<>();
        customDomains.forEach(domains::add);
        subDomains.forEach(domains::add);
        return domains;
    }

    protected List<DomainResult> fetchDefaultDomains(Gateway gateway) {
        APIGClient client = getClient(gateway);
        CompletableFuture<ListEnvironmentsResponse> f =
                client.execute(
                        c ->
                                c.listEnvironments(
                                        ListEnvironmentsRequest.builder()
                                                .gatewayId(gateway.getGatewayId())
                                                .gatewayType(gateway.getGatewayType().getType())
                                                .build()));

        ListEnvironmentsResponse response = f.join();
        if (200 != response.getStatusCode()) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
        }

        List<EnvironmentInfo> items = response.getBody().getData().getItems();
        if (CollUtil.isEmpty(items)) {
            return Collections.emptyList();
        }

        // Default Environment
        EnvironmentInfo env = items.get(0);

        if (env.getSubDomainInfos() == null) {
            return Collections.emptyList();
        }

        List<DomainResult> domains = new ArrayList<>();
        for (SubDomainInfo domain : env.getSubDomainInfos()) {
            String protocol =
                    domain.getProtocol() == null ? null : domain.getProtocol().toLowerCase();
            domains.add(
                    DomainResult.builder()
                            .domain(domain.getName())
                            .protocol(protocol)
                            .networkType(domain.getNetworkType())
                            .build());
        }
        return domains;
    }
}
