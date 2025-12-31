package com.alibaba.himarket.service.publisher;

import com.alibaba.fastjson.TypeReference;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.api.GatewayPublisher;
import com.alibaba.himarket.service.gateway.SofaHigressOperator;
import com.alibaba.himarket.service.gateway.client.SofaHigressClient;
import com.alibaba.himarket.support.api.PublishConfig;
import com.alibaba.himarket.support.enums.APIStatus;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.GatewayType;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SofaHigressGatewayPublisher implements GatewayPublisher {

    @Autowired
    private SofaHigressOperator sofaHigressOperator;

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.SOFA_HIGRESS;
    }

    @Override
    public List<APIType> getSupportedAPITypes() {
        return List.of(APIType.MCP_SERVER, APIType.REST_API, APIType.MODEL_API);
    }

    @Override
    public String publish(Gateway gateway, APIDefinitionVO apiDefinition, PublishConfig publishConfig) {
        SofaHigressClient client = getClient(gateway);

        SofaHigressAPIDefinitionResponse response = client.execute(
                "/apiDefinition/pub",
                HttpMethod.POST,
                SofaHigressAPIDefinitionParam.builder()
                        .apiDefinitionVO(apiDefinition)
                        .publishConfig(publishConfig)
                        .build()
                        .autoFillTenantInfo(),
                new TypeReference<>(){},
                new ObjectMapper());

        // rest API返回routeId，model API返回apiId，mcp server返回serverId
        return response.getResourceId();
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
                new TypeReference<>(){},
                new ObjectMapper());

        // rest API返回routeId，model API返回apiId，mcp server返回serverId
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
    public void validatePublishConfig(APIDefinitionVO apiDefinition, PublishConfig publishConfig) {
        // 确定必要的参数是否缺失
        if (apiDefinition == null || publishConfig == null) {
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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SofaHigressAPIDefinitionResponse {
        String resourceId;
        String type;
    }
}
