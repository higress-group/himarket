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

import cn.hutool.core.map.MapBuilder;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.constant.Resources;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.dto.params.gateway.QueryAPIGParam;
import com.alibaba.apiopenplatform.dto.result.*;
import com.alibaba.apiopenplatform.entity.Gateway;
import com.alibaba.apiopenplatform.entity.Consumer;
import com.alibaba.apiopenplatform.entity.ConsumerCredential;
import com.alibaba.apiopenplatform.entity.Product;
import com.alibaba.apiopenplatform.entity.ProductRef;
import com.alibaba.apiopenplatform.repository.ProductRefRepository;
import com.alibaba.apiopenplatform.service.gateway.client.APIGClient;
import com.alibaba.apiopenplatform.service.gateway.client.HigressClient;
import com.alibaba.apiopenplatform.support.consumer.ApiKeyConfig;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.consumer.HigressAuthConfig;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
import com.alibaba.apiopenplatform.support.gateway.HigressConfig;
import com.alibaba.apiopenplatform.support.product.HigressRefConfig;

import java.util.ArrayList;
import java.util.Optional;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        c.setPath("/" + higressMCPConfig.getName());
        c.setDomains(higressMCPConfig.getDomains().stream().map(domain -> MCPConfigResult.Domain.builder()
                        .domain(domain)
                        .protocol("https")
                        .build())
                .collect(Collectors.toList()));
        m.setMcpServerConfig(c);

        // tools
        m.setTools(higressMCPConfig.getRawConfigurations());

        // meta
        MCPConfigResult.McpMetadata meta = new MCPConfigResult.McpMetadata();
        meta.setSource(GatewayType.HIGRESS.name());
        meta.setFromType(higressMCPConfig.getType());
        m.setMeta(meta);

        return JSONUtil.toJsonStr(m);
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
    public ConsumerAuthConfig authorizeConsumer(Gateway gateway, String consumerId, Object refConfig) {
        HigressRefConfig config = (HigressRefConfig) refConfig;
        HigressClient client = getClient(gateway);

        String mcpServerName = config.getMcpServerName();
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

    @Override
    public void revokeConsumerAuthorization(Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {
        HigressClient client = getClient(gateway);

        HigressAuthConfig higressAuthConfig = authConfig.getHigressAuthConfig();
        if (higressAuthConfig == null) {
            return;
        }

        client.execute("/v1/mcpServer/consumers/",
                HttpMethod.DELETE,
                null,
                buildAuthHigressConsumer(higressAuthConfig.getResourceName(), consumerId),
                Void.class);
    }

    @Override
    public APIResult fetchAPI(Gateway gateway, String apiId) {
        throw new UnsupportedOperationException("Higress gateway does not support fetching API");
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.HIGRESS;
    }

    @Override
    public String getDashboard(Gateway gateway) {
        throw new UnsupportedOperationException("Higress gateway does not support getting dashboard");
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
