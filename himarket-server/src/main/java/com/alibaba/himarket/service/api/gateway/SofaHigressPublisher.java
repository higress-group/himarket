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

package com.alibaba.himarket.service.api.gateway;

import com.alibaba.fastjson.TypeReference;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.dto.result.api.APIEndpointVO;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.api.GatewayCapabilityRegistry;
import com.alibaba.himarket.service.api.GatewayPublisher;
import com.alibaba.himarket.service.gateway.SofaHigressOperator;
import com.alibaba.himarket.service.gateway.client.SofaHigressClient;
import com.alibaba.himarket.support.api.PublishConfig;
import com.alibaba.himarket.support.enums.APIStatus;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.GatewayType;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class SofaHigressPublisher implements GatewayPublisher {

    @Autowired
    private SofaHigressOperator sofaHigressOperator;

    @Autowired
    private GatewayCapabilityRegistry gatewayCapabilityRegistry;

    @PostConstruct
    public void init() {
        gatewayCapabilityRegistry.registerPublisher(this);
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.SOFA_HIGRESS;
    }

    @Override
    public List<APIType> getSupportedAPITypes() {
        return List.of(APIType.MCP_SERVER, APIType.REST_API, APIType.MODEL_API);
    }

    @Override
    public String publish(Gateway gateway, APIDefinitionVO apiDefinition, List<APIEndpointVO> endpoints, PublishConfig publishConfig) {
        apiDefinition.setEndpoints(endpoints);
        SofaHigressClient client = getClient(gateway);

        SofaHigressAPIDefinitionResponse response = client.execute(
                "/apiDefinition/pub",
                HttpMethod.POST,
                SofaHigressAPIDefinitionParam.builder()
                        .apiDefinitionVO(apiDefinition)
                        .publishConfig(publishConfig)
                        .build()
                        .autoFillTenantInfo(),
                new TypeReference<>(){});

        // rest API返回routeId，model API返回apiId，mcp server返回serverId
        apiDefinition.setStatus(APIStatus.PUBLISHED);
        return response.getResourceId();
    }

    @Override
    public String update(Gateway gateway, APIDefinitionVO apiDefinition, List<APIEndpointVO> endpoints, PublishConfig publishConfig) {
        throw new UnsupportedOperationException("Sofa Higress gateway does not support APIDefinition update");
    }

    @Override
    public String unpublish(Gateway gateway, APIDefinitionVO apiDefinition, PublishConfig publishConfig) {
        SofaHigressClient client = getClient(gateway);

        SofaHigressAPIDefinitionResponse response = client.execute(
                "/apiDefinition/unpub",
                HttpMethod.POST,
                SofaHigressAPIDefinitionParam.builder()
                        .apiDefinitionVO(apiDefinition)
                        .publishConfig(publishConfig)
                        .build()
                        .autoFillTenantInfo(),
                new TypeReference<>(){});

        // rest API返回routeId，model API返回apiId，mcp server返回serverId
        apiDefinition.setStatus(APIStatus.DRAFT);
        return response.getResourceId();
    }

    @Override
    public boolean isPublished(Gateway gateway, APIDefinitionVO apiDefinition) {
        SofaHigressClient client = getClient(gateway);

        String response = client.execute(
                "/apiDefinition/isPub",
                HttpMethod.POST,
                SofaHigressAPIDefinitionParam.builder()
                        .apiDefinitionVO(apiDefinition)
                        .build()
                        .autoFillTenantInfo());

        return Boolean.parseBoolean(response);
    }

    @Override
    public void validatePublishConfig(APIDefinitionVO apiDefinition, List<APIEndpointVO> endpoints, PublishConfig publishConfig) {
        // 确定必要的参数是否缺失
        if (apiDefinition == null || endpoints == null || publishConfig == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "API definition, endpoints or publish config is missing");
        }

        if (apiDefinition.getStatus() != APIStatus.DRAFT) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "only draft API definition can be published");
        }

    }

    private SofaHigressClient getClient(Gateway gateway) {
        return sofaHigressOperator.getClient(gateway);
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class SofaHigressAPIDefinitionParam extends SofaHigressOperator.BaseRequest<Object> {
        APIDefinitionVO apiDefinitionVO;
        PublishConfig publishConfig;
        String serviceAddress;
    }

    @Data
    @Builder
    public static class SofaHigressAPIDefinitionResponse {
        String resourceId;
        String type;
    }
}
