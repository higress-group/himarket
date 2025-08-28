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
import com.alibaba.apiopenplatform.dto.result.*;
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
import com.aliyun.sdk.service.apig20240327.models.*;
import com.aliyun.sdk.service.apig20240327.models.CreateConsumerAuthorizationRulesRequest.AuthorizationRules;
import com.aliyun.sdk.service.apig20240327.models.CreateConsumerAuthorizationRulesRequest.ResourceIdentifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Slf4j
@Primary
public class APIGOperator extends GatewayOperator<APIGClient> {

    @Override
    public PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size) {
        return fetchAPIs(gateway, APIGAPIType.HTTP, page, size);
    }

    public PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size) {
        return fetchAPIs(gateway, APIGAPIType.REST, page, size);
    }

    @Override
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway gateway, int page, int size) {
        throw new UnsupportedOperationException("APIG does not support MCP Servers");
    }

    @Override
    public String fetchAPIConfig(Gateway gateway, Object config) {
        APIGClient client = getClient(gateway);

        try {
            APIGRefConfig apigRefConfig = (APIGRefConfig) config;
            ExportHttpApiResponse response = client.execute(c -> {
                ExportHttpApiRequest request = ExportHttpApiRequest.builder()
                        .httpApiId(apigRefConfig.getApiId())
                        .build();
                try {
                    return c.exportHttpApi(request).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

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
    public PageResult<GatewayResult> fetchGateways(Object param, int page, int size) {
        if (!(param instanceof QueryAPIGParam)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "param");
        }
        return fetchGateways((QueryAPIGParam) param, page, size);
    }

    public PageResult<GatewayResult> fetchGateways(QueryAPIGParam param, int page, int size) {
        APIGClient client = new APIGClient(param.convertTo());

        List<GatewayResult> gateways = new ArrayList<>();
        try {
            ListGatewaysResponse response = client.execute(c -> {
                ListGatewaysRequest request = ListGatewaysRequest.builder()
                        .gatewayType(param.getGatewayType().getType())
                        .pageNumber(page)
                        .pageSize(size)
                        .build();
                try {
                    return c.listGateways(request).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

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
            GetGatewayResponse response = client.execute(c -> {
                GetGatewayRequest request = GetGatewayRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .build();
                try {
                    return c.getGateway(request).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
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

        try {
            // ApiKey
            ApiKeyIdentityConfig apikeyIdentityConfig = convertToApiKeyIdentityConfig(credential.getApiKeyConfig());

            // Hmac
            List<AkSkIdentityConfig> akSkIdentityConfigs = convertToAkSkIdentityConfigs(credential.getHmacConfig());

            String mark = consumer.getConsumerId().substring(Math.max(0, consumer.getConsumerId().length() - 8));
            CreateConsumerRequest.Builder builder = CreateConsumerRequest.builder()
                    .name(StrUtil.format("{}-{}", consumer.getName(), mark))
                    .description("Created by AI Portal")
                    .gatewayType(config.getGatewayType().getType())
                    .enable(true);
            if (apikeyIdentityConfig != null) {
                builder.apikeyIdentityConfig(apikeyIdentityConfig);
            }
            if (akSkIdentityConfigs != null) {
                builder.akSkIdentityConfigs(akSkIdentityConfigs);
            }

            CreateConsumerResponse response = client.execute(c -> {
                try {
                    return c.createConsumer(builder.build()).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            return response.getBody().getData().getConsumerId();
        } catch (Exception e) {
            log.error("Error creating Consumer", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error creating Consumer，Cause：" + e.getMessage());
        }

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

            UpdateConsumerResponse response = client.execute(c -> {
                try {
                    return c.updateConsumer(builder.build()).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
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

            CreateConsumerAuthorizationRulesResponse response = client.execute(c -> {
                CreateConsumerAuthorizationRulesRequest request = CreateConsumerAuthorizationRulesRequest.builder()
                        .authorizationRules(rules)
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

            return ConsumerAuthConfig.builder()
                    .apigAuthorizationRuleIds(response.getBody().getData().getConsumerAuthorizationRuleIds())
                    .build();
        } catch (Exception e) {
            log.error("Error authorizing consumer {} to apiId {} in APIG gateway {}", consumerId, apiId, gateway.getGatewayId(), e);
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Failed to authorize consumer to apiId in APIG gateway: " + e.getMessage());
        }
    }

    @Override
    public void revokeConsumerAuthorization(Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        APIGClient client = getClient(gateway);

        try {
            BatchDeleteConsumerAuthorizationRuleRequest request = BatchDeleteConsumerAuthorizationRuleRequest.builder()
                    .consumerAuthorizationRuleIds(StrUtil.join(",", authConfig.getApigAuthorizationRuleIds()))
                    .build();

            BatchDeleteConsumerAuthorizationRuleResponse response = client.execute(c -> {
                try {
                    return c.batchDeleteConsumerAuthorizationRule(request).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }
        } catch (Exception e) {
            log.error("Error deleting Consumer Authorization", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error deleting Consumer Authorization，Cause：" + e.getMessage());
        }
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.APIG_API;
    }

    public APIResult fetchAPI(Gateway gateway, String apiId) {
        APIGClient client = getClient(gateway);
        try {
            GetHttpApiResponse response = client.execute(c -> {
                GetHttpApiRequest request = GetHttpApiRequest.builder()
                        .httpApiId(apiId)
                        .build();
                try {
                    return c.getHttpApi(request).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
            if (response.getStatusCode() != 200) {
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, response.getBody().getMessage());
            }

            HttpApiApiInfo apiInfo = response.getBody().getData();
            return new APIResult().convertFrom(apiInfo);
        } catch (Exception e) {
            log.error("Error fetching API", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Error fetching API，Cause：" + e.getMessage());
        }
    }

    protected HttpRoute fetchHTTPRoute(Gateway gateway, String apiId, String routeId) {
        APIGClient client = getClient(gateway);

        try {
            GetHttpApiRouteResponse response = client.execute(c -> {
                GetHttpApiRouteRequest request = GetHttpApiRouteRequest.builder()
                        .httpApiId(apiId)
                        .routeId(routeId)
                        .build();
                try {
                    return c.getHttpApiRoute(request).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

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
            ListHttpApisResponse response = client.execute(c -> {
                ListHttpApisRequest request = ListHttpApisRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .gatewayType(gateway.getGatewayType().getType())
                        .types(type.getType())
                        .pageNumber(page)
                        .pageSize(size)
                        .build();
                try {
                    return c.listHttpApis(request).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
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
            ListHttpApiRoutesResponse response = client.execute(c -> {
                ListHttpApiRoutesRequest request = ListHttpApiRoutesRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .httpApiId(apiId)
                        .pageNumber(page)
                        .pageSize(size)
                        .build();
                try {
                    return c.listHttpApiRoutes(request).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
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
            ListHttpApiOperationsResponse response = client.execute(c -> {
                ListHttpApiOperationsRequest request = ListHttpApiOperationsRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .httpApiId(apiId)
                        .pageNumber(1)
                        .pageSize(500)
                        .build();
                try {
                    return c.listHttpApiOperations(request).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });

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
}

