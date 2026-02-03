package com.alibaba.himarket.service.publisher;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.api.APIDefinitionResult;
import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.api.GatewayPublisher;
import com.alibaba.himarket.service.gateway.SofaHigressOperator;
import com.alibaba.himarket.service.gateway.client.SofaHigressClient;
import com.alibaba.himarket.support.api.DeploymentConfig;
import com.alibaba.himarket.support.enums.APIStatus;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.product.GatewayRefConfig;
import com.alibaba.himarket.support.product.SofaHigressRefConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import java.util.List;
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

    @Autowired private SofaHigressOperator sofaHigressOperator;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
    public GatewayRefConfig publish(
            Gateway gateway, APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {
        SofaHigressClient client = getClient(gateway);

        // Convert to result object for the remote call
        APIDefinitionResult apiDefinitionResult =
                new APIDefinitionResult().convertFrom(apiDefinition);

        SofaHigressAPIDefinitionResponse response =
                client.execute(
                        "/apiDefinition/pub",
                        HttpMethod.POST,
                        SofaHigressAPIDefinitionParam.builder()
                                .apiDefinitionResult(apiDefinitionResult)
                                .deploymentConfig(deploymentConfig)
                                .build(),
                        new TypeReference<>() {},
                        objectMapper);

        // rest API返回routeId，model API返回apiId，mcp server返回serverId
        String resourceId = response.getResourceId();
        String resourceName = response.getResourceName();
        return switch (apiDefinition.getType()) {
            case MCP_SERVER ->
                    SofaHigressRefConfig.builder()
                            .serverId(resourceId)
                            .mcpServerName(resourceName)
                            .build();
            case MODEL_API ->
                    SofaHigressRefConfig.builder()
                            .modelApiId(resourceId)
                            .modelApiName(resourceName)
                            .build();
            case REST_API ->
                    SofaHigressRefConfig.builder().apiId(resourceId).apiName(resourceName).build();
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported API type: " + apiDefinition.getType());
        };
    }

    @Override
    public String unpublish(
            Gateway gateway, APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {
        SofaHigressClient client = getClient(gateway);

        // Convert to result object for the remote call
        APIDefinitionResult apiDefinitionResult =
                new APIDefinitionResult().convertFrom(apiDefinition);

        SofaHigressAPIDefinitionResponse response =
                client.execute(
                        "/apiDefinition/unpub",
                        HttpMethod.POST,
                        SofaHigressAPIDefinitionParam.builder()
                                .apiDefinitionResult(apiDefinitionResult)
                                .deploymentConfig(deploymentConfig)
                                .build(),
                        new TypeReference<>() {},
                        objectMapper);

        // rest API返回routeId，model API返回apiId，mcp server返回serverId
        return response.getResourceId();
    }

    @Override
    public boolean isPublished(Gateway gateway, APIDefinition apiDefinition) {
        SofaHigressClient client = getClient(gateway);

        // Convert to result object for the remote call
        APIDefinitionResult apiDefinitionResult =
                new APIDefinitionResult().convertFrom(apiDefinition);

        String response =
                client.execute(
                        "/apiDefinition/isPub",
                        HttpMethod.POST,
                        SofaHigressAPIDefinitionParam.builder()
                                .apiDefinitionResult(apiDefinitionResult)
                                .build());

        return Boolean.parseBoolean(response);
    }

    @Override
    public void validateDeploymentConfig(
            APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {
        // 确定必要的参数是否缺失
        if (apiDefinition == null || deploymentConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "API definition or publish config is missing");
        }

        if (apiDefinition.getStatus() != APIStatus.DRAFT) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "only draft API definition can be published");
        }
    }

    private SofaHigressClient getClient(Gateway gateway) {
        return sofaHigressOperator.getClient(gateway);
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class SofaHigressAPIDefinitionParam
            extends SofaHigressOperator.BaseRequest<Object> {
        APIDefinitionResult apiDefinitionResult;
        DeploymentConfig deploymentConfig;
        String serviceAddress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SofaHigressAPIDefinitionResponse {
        String resourceId;
        String resourceName;
        String type;
    }
}
