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
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.dto.params.gateway.QueryAPIGParam;
import com.alibaba.apiopenplatform.dto.result.agent.AgentAPIResult;
import com.alibaba.apiopenplatform.dto.result.httpapi.APIConfigResult;
import com.alibaba.apiopenplatform.dto.result.httpapi.APIResult;
import com.alibaba.apiopenplatform.dto.result.common.DomainResult;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.dto.result.gateway.GatewayResult;
import com.alibaba.apiopenplatform.dto.result.mcp.GatewayMCPServerResult;
import com.alibaba.apiopenplatform.dto.result.model.GatewayModelAPIResult;
import com.alibaba.apiopenplatform.support.consumer.APIGAuthConfig;
import com.alibaba.apiopenplatform.support.consumer.ApiKeyConfig;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.consumer.HmacConfig;
import com.alibaba.apiopenplatform.support.enums.APIGAPIType;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.entity.Gateway;
import com.alibaba.apiopenplatform.entity.Consumer;
import com.alibaba.apiopenplatform.entity.ConsumerCredential;
import com.alibaba.apiopenplatform.service.gateway.client.APIGClient;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
import com.alibaba.apiopenplatform.support.product.APIGRefConfig;
import com.aliyun.sdk.gateway.pop.exception.PopClientException;
import com.aliyun.sdk.service.apig20240327.models.*;
import com.aliyun.sdk.service.apig20240327.models.CreateConsumerAuthorizationRulesRequest.AuthorizationRules;
import com.aliyun.sdk.service.apig20240327.models.CreateConsumerAuthorizationRulesRequest.ResourceIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException("APIG does not support MCP Servers");
    }

    @Override
    public PageResult<AgentAPIResult> fetchAgentAPIs(Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException("APIG does not support Agent APIs");
    }

    @Override
    public PageResult<? extends GatewayModelAPIResult> fetchModelAPIs(Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException("APIG does not support Model APIs");
    }

    @Override
    public String fetchAPIConfig(Gateway gateway, Object config) {
        APIGClient client = getClient(gateway);

        try {
            APIGRefConfig apigRefConfig = (APIGRefConfig) config;
            CompletableFuture<ExportHttpApiResponse> f = client.execute(c -> {
                ExportHttpApiRequest request = ExportHttpApiRequest.builder()
                        .httpApiId(apigRefConfig.getApiId())
                        .build();
                return c.exportHttpApi(request);
            });

            ExportHttpApiResponse response = f.join();
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

            return JSONUtil.toJsonStr(configResult);
        } catch (Exception e) {
            log.error("Error fetching API Spec", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching API Spec，Cause：" + e.getMessage());
        }
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
    public PageResult<GatewayResult> fetchGateways(Object param, int page, int size) {
        return fetchGateways((QueryAPIGParam) param, page, size);
    }

    public PageResult<GatewayResult> fetchGateways(QueryAPIGParam param, int page, int size) {
        APIGClient client = new APIGClient(param.convertTo());

        List<GatewayResult> gateways = new ArrayList<>();
        try {
            CompletableFuture<ListGatewaysResponse> f = client.execute(c -> {
                ListGatewaysRequest request = ListGatewaysRequest.builder()
                        .gatewayType(param.getGatewayType().getType())
                        .pageNumber(page)
                        .pageSize(size)
                        .build();

                return c.listGateways(request);
            });

            ListGatewaysResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            for (ListGatewaysResponseBody.Items item : response.getBody().getData().getItems()) {
                gateways.add(GatewayResult.builder()
                        .gatewayName(item.getName())
                        .gatewayId(item.getGatewayId())
                        .gatewayType(param.getGatewayType())
                        .build());
            }

            int total = Math.toIntExact(response.getBody().getData().getTotalSize());
            return PageResult.of(gateways, page, size, total);
        } catch (Exception e) {
            log.error("Error fetching Gateways", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching Gateways，Cause：" + e.getMessage());
        }
    }

    protected String fetchGatewayEnv(Gateway gateway) {
        APIGClient client = getClient(gateway);
        try {
            CompletableFuture<GetGatewayResponse> f = client.execute(c -> {
                GetGatewayRequest request = GetGatewayRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .build();

                return c.getGateway(request);

            });

            GetGatewayResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            List<GetGatewayResponseBody.Environments> environments = response.getBody().getData().getEnvironments();
            if (CollUtil.isEmpty(environments)) {
                return null;
            }

            return environments.get(0).getEnvironmentId();
        } catch (Exception e) {
            log.error("Error fetching Gateway", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching Gateway，Cause：" + e.getMessage());
        }
    }

    @Override
    public String createConsumer(Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        APIGClient client = new APIGClient(config.getApigConfig());

        String mark = consumer.getConsumerId().substring(Math.max(0, consumer.getConsumerId().length() - 8));
        String gwConsumerName = StrUtil.format("{}-{}", consumer.getName(), mark);
        try {
            // ApiKey
            ApiKeyIdentityConfig apikeyIdentityConfig = convertToApiKeyIdentityConfig(credential.getApiKeyConfig());

            // Hmac
            List<AkSkIdentityConfig> akSkIdentityConfigs = convertToAkSkIdentityConfigs(credential.getHmacConfig());

            CreateConsumerRequest.Builder builder = CreateConsumerRequest.builder()
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

            CompletableFuture<CreateConsumerResponse> f = client.execute(c -> c.createConsumer(builder.build()));

            CreateConsumerResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            return response.getBody().getData().getConsumerId();
        } catch (Exception e) {
            Throwable cause = e.getCause();
            // Consumer已经存在
            if (cause instanceof PopClientException && "Conflict.ConsumerNameDuplicate".equals(((PopClientException) cause).getErrCode())) {
                return retrievalConsumer(gwConsumerName, config);
            }
            log.error("Error creating Consumer", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error creating Consumer，Cause：" + e.getMessage());
        }
    }

    private String retrievalConsumer(String name, GatewayConfig gatewayConfig) {
        APIGClient client = new APIGClient(gatewayConfig.getApigConfig());

        try {
            CompletableFuture<ListConsumersResponse> f = client.execute(c -> {
                ListConsumersRequest request = ListConsumersRequest.builder()
                        .gatewayType(gatewayConfig.getGatewayType().getType())
                        .nameLike(name)
                        .pageNumber(1)
                        .pageSize(10)
                        .build();

                return c.listConsumers(request);
            });
            ListConsumersResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            for (ListConsumersResponseBody.Items item : response.getBody().getData().getItems()) {
                if (StrUtil.equals(item.getName(), name)) {
                    return item.getConsumerId();
                }
            }
        } catch (Exception e) {
            log.error("Error fetching Consumer", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching Consumer，Cause：" + e.getMessage());
        }
        return null;
    }

    @Override
    public void updateConsumer(String consumerId, ConsumerCredential credential, GatewayConfig config) {
        APIGClient client = new APIGClient(config.getApigConfig());
        try {
            // ApiKey
            ApiKeyIdentityConfig apikeyIdentityConfig = convertToApiKeyIdentityConfig(credential.getApiKeyConfig());

            // Hmac
            List<AkSkIdentityConfig> akSkIdentityConfigs = convertToAkSkIdentityConfigs(credential.getHmacConfig());

            UpdateConsumerRequest.Builder builder = UpdateConsumerRequest.builder()
                    .enable(true)
                    .consumerId(consumerId);

            if (apikeyIdentityConfig != null) {
                builder.apikeyIdentityConfig(apikeyIdentityConfig);
            }

            if (akSkIdentityConfigs != null) {
                builder.akSkIdentityConfigs(akSkIdentityConfigs);
            }

            CompletableFuture<UpdateConsumerResponse> f = client.execute(c -> c.updateConsumer(builder.build()));

            UpdateConsumerResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }
        } catch (Exception e) {
            log.error("Error creating Consumer", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error creating Consumer，Cause：" + e.getMessage());
        }
    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {
        APIGClient client = new APIGClient(config.getApigConfig());
        try {
            DeleteConsumerRequest request = DeleteConsumerRequest.builder()
                    .consumerId(consumerId)
                    .build();
            client.execute(c -> {
                c.deleteConsumer(request);
                return null;
            });
        } catch (Exception e) {
            log.error("Error deleting Consumer", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error deleting Consumer，Cause：" + e.getMessage());
        }
    }

    @Override
    public boolean isConsumerExists(String consumerId, GatewayConfig config) {
        // TODO: 实现APIG网关消费者存在性检查
        return true;
    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(Gateway gateway, String consumerId, Object refConfig) {
        APIGClient client = getClient(gateway);

        APIGRefConfig config = (APIGRefConfig) refConfig;
        // REST API 授权
        String apiId = config.getApiId();

        try {
            List<HttpApiOperationInfo> operations = fetchRESTOperations(gateway, apiId);
            if (CollUtil.isEmpty(operations)) {
                return null;
            }

            // 确认Gateway的EnvId
            String envId = fetchGatewayEnv(gateway);

            List<AuthorizationRules> rules = new ArrayList<>();
            for (HttpApiOperationInfo operation : operations) {
                AuthorizationRules rule = AuthorizationRules.builder()
                        .consumerId(consumerId)
                        .expireMode("LongTerm")
                        .resourceType("RestApiOperation")
                        .resourceIdentifier(ResourceIdentifier.builder()
                                .resourceId(operation.getOperationId())
                                .environmentId(envId).build())
                        .build();
                rules.add(rule);
            }

            CompletableFuture<CreateConsumerAuthorizationRulesResponse> f = client.execute(c -> {
                CreateConsumerAuthorizationRulesRequest request = CreateConsumerAuthorizationRulesRequest.builder()
                        .authorizationRules(rules)
                        .build();
                return c.createConsumerAuthorizationRules(request);
            });

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
            log.error("Error authorizing consumer {} to apiId {} in APIG gateway {}", consumerId, apiId, gateway.getGatewayId(), e);
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Failed to authorize consumer to apiId in APIG gateway: " + e.getMessage());
        }
    }

    @Override
    public void revokeConsumerAuthorization(Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        APIGAuthConfig apigAuthConfig = authConfig.getApigAuthConfig();
        if (apigAuthConfig == null) {
            return;
        }

        APIGClient client = getClient(gateway);

        try {
            BatchDeleteConsumerAuthorizationRuleRequest request = BatchDeleteConsumerAuthorizationRuleRequest.builder()
                    .consumerAuthorizationRuleIds(StrUtil.join(",", apigAuthConfig.getAuthorizationRuleIds()))
                    .build();

            CompletableFuture<BatchDeleteConsumerAuthorizationRuleResponse> f = client.execute(c -> c.batchDeleteConsumerAuthorizationRule(request));

            BatchDeleteConsumerAuthorizationRuleResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof PopClientException
                    && "DatabaseError.RecordNotFound".equals(((PopClientException) cause).getErrCode())) {
                log.warn("Consumer authorization rules[{}] not found, ignore", apigAuthConfig.getAuthorizationRuleIds());
                return;
            }

            log.error("Error deleting Consumer Authorization", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error deleting Consumer Authorization，Cause：" + e.getMessage());
        }
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.APIG_API;
    }

    @Override
    public String getDashboard(Gateway gateway, String type) {
        throw new UnsupportedOperationException("Dashboard feature has been removed");
    }

    @Override
    public List<String> fetchGatewayIps(Gateway gateway) {

        APIGClient client = getClient(gateway);
        try {
            CompletableFuture<GetGatewayResponse> f = client.execute(c -> {
                GetGatewayRequest request = GetGatewayRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .build();

                return c.getGateway(request);

            });

            GetGatewayResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            List<GetGatewayResponseBody.LoadBalancers> loadBalancers = response.getBody().getData().getLoadBalancers();
            return loadBalancers.stream()
                    // Only internet load balancer support
                    .filter(loadBalancer -> StrUtil.equalsIgnoreCase(loadBalancer.getAddressType(), "internet"))
                    .map(GetGatewayResponseBody.LoadBalancers::getIpv4Addresses)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching API", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching API，Cause：" + e.getMessage());
        }
    }

    public HttpApiApiInfo fetchAPI(Gateway gateway, String apiId) {
        APIGClient client = getClient(gateway);
        try {
            CompletableFuture<GetHttpApiResponse> f = client.execute(c -> {
                GetHttpApiRequest request = GetHttpApiRequest.builder()
                        .httpApiId(apiId)
                        .build();

                return c.getHttpApi(request);

            });

            GetHttpApiResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            return response.getBody().getData();
        } catch (Exception e) {
            log.error("Error fetching API", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching API，Cause：" + e.getMessage());
        }
    }

    protected HttpRoute fetchHTTPRoute(Gateway gateway, String apiId, String routeId) {
        APIGClient client = getClient(gateway);

        try {
            CompletableFuture<GetHttpApiRouteResponse> f = client.execute(c -> {
                GetHttpApiRouteRequest request = GetHttpApiRouteRequest.builder()
                        .httpApiId(apiId)
                        .routeId(routeId)
                        .build();

                return c.getHttpApiRoute(request);

            });

            GetHttpApiRouteResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            return response.getBody().getData();

        } catch (Exception e) {
            log.error("Error fetching HTTP Route", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching HTTP Route，Cause：" + e.getMessage());
        }
    }

    protected PageResult<APIResult> fetchAPIs(Gateway gateway, APIGAPIType type, int page, int size) {
        APIGClient client = getClient(gateway);
        try {
            List<APIResult> apis = new ArrayList<>();
            CompletableFuture<ListHttpApisResponse> f = client.execute(c -> {
                ListHttpApisRequest request = ListHttpApisRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .gatewayType(gateway.getGatewayType().getType())
                        .types(type.getType())
                        .pageNumber(page)
                        .pageSize(size)
                        .build();

                return c.listHttpApis(request);
            });

            ListHttpApisResponse response = f.join();
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
        } catch (Exception e) {
            log.error("Error fetching APIs", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching APIs，Cause：" + e.getMessage());
        }
    }

    public PageResult<HttpRoute> fetchHttpRoutes(Gateway gateway, String apiId, int page, int size) {
        APIGClient client = getClient(gateway);
        try {
            CompletableFuture<ListHttpApiRoutesResponse> f = client.execute(c -> {
                ListHttpApiRoutesRequest request = ListHttpApiRoutesRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .httpApiId(apiId)
                        .pageNumber(page)
                        .pageSize(size)
                        .build();

                return c.listHttpApiRoutes(request);

            });

            ListHttpApiRoutesResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }
            List<HttpRoute> httpRoutes = response.getBody().getData().getItems();
            int total = response.getBody().getData().getTotalSize();
            return PageResult.of(httpRoutes, page, size, total);
        } catch (Exception e) {
            log.error("Error fetching HTTP Roues", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching HTTP Roues，Cause：" + e.getMessage());
        }
    }

    public List<HttpApiOperationInfo> fetchRESTOperations(Gateway gateway, String apiId) {
        APIGClient client = getClient(gateway);

        try {
            CompletableFuture<ListHttpApiOperationsResponse> f = client.execute(c -> {
                ListHttpApiOperationsRequest request = ListHttpApiOperationsRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .httpApiId(apiId)
                        .pageNumber(1)
                        .pageSize(500)
                        .build();

                return c.listHttpApiOperations(request);

            });

            ListHttpApiOperationsResponse response = f.join();
            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            return response.getBody().getData().getItems();
        } catch (Exception e) {
            log.error("Error fetching REST operations", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching REST operations，Cause：" + e.getMessage());
        }
    }

    protected ApiKeyIdentityConfig convertToApiKeyIdentityConfig(ApiKeyConfig config) {
        if (config == null) {
            return null;
        }

        // ApikeySource
        ApiKeyIdentityConfig.ApikeySource apikeySource = ApiKeyIdentityConfig.ApikeySource.builder()
                .source(config.getSource())
                .value(config.getKey())
                .build();

        // credentials
        List<ApiKeyIdentityConfig.Credentials> credentials = config.getCredentials().stream()
                .map(cred -> ApiKeyIdentityConfig.Credentials.builder()
                        .apikey(cred.getApiKey())
                        .generateMode("Custom")
                        .build())
                .collect(Collectors.toList());

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
                .map(cred -> AkSkIdentityConfig.builder()
                        .ak(cred.getAk())
                        .sk(cred.getSk())
                        .generateMode("Custom")
                        .type("AkSk")
                        .build())
                .collect(Collectors.toList());
    }

    protected List<DomainResult> extractAPIDomains(HttpApiApiInfo apiInfo) {
        if (apiInfo == null || apiInfo.getEnvironments() == null) {
            return Collections.emptyList();
        }

        Stream<DomainResult> subDomains = apiInfo.getEnvironments()
                .stream()
                .map(HttpApiApiInfo.Environments::getSubDomains)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(subDomain -> DomainResult.builder()
                        .domain(subDomain.getName())
                        .protocol(subDomain.getProtocol())
                        .networkType(subDomain.getNetworkType())
                        .build())
                .filter(result -> result.getDomain() != null);

        Stream<DomainResult> customDomains = apiInfo.getEnvironments()
                .stream()
                .map(HttpApiApiInfo.Environments::getCustomDomains)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(customDomain -> DomainResult.builder()
                        .domain(customDomain.getName())
                        .protocol(customDomain.getProtocol())
                        .build())
                .filter(result -> result.getDomain() != null);

        return Stream.concat(customDomains, subDomains)
                .collect(Collectors.toList());
    }

    protected List<DomainResult> fetchDefaultDomains(Gateway gateway) {
        APIGClient client = getClient(gateway);
        CompletableFuture<ListEnvironmentsResponse> f = client.execute(c ->
                c.listEnvironments(ListEnvironmentsRequest.builder()
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

        return Optional.ofNullable(env.getSubDomainInfos())
                .orElse(Collections.emptyList())
                .stream()
                .map(domain -> DomainResult.builder()
                        .domain(domain.getName())
                        .protocol(Optional.ofNullable(domain.getProtocol())
                                .map(String::toLowerCase)
                                .orElse(null))
                        .networkType(domain.getNetworkType())
                        .build())
                .collect(Collectors.toList());
    }
}

