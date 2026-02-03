package com.alibaba.himarket.service.publisher;

import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.api.GatewayPublisher;
import com.alibaba.himarket.support.api.DeploymentConfig;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.product.GatewayRefConfig;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApsaraGatewayPublisher implements GatewayPublisher {

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.APSARA_GATEWAY;
    }

    @Override
    public List<APIType> getSupportedAPITypes() {
        return List.of(APIType.REST_API);
    }

    @Override
    public GatewayRefConfig publish(
            Gateway gateway, APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {
        return null;
    }

    @Override
    public String unpublish(
            Gateway gateway, APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {
        return "Mock unpublish success";
    }

    @Override
    public boolean isPublished(Gateway gateway, APIDefinition apiDefinition) {
        return false;
    }

    @Override
    public void validateDeploymentConfig(
            APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {
        // Mock validation
    }
}
