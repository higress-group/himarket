package com.alibaba.himarket.service.publisher;

import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.api.GatewayPublisher;
import com.alibaba.himarket.support.api.PublishConfig;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.GatewayType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApigAiGatewayPublisher implements GatewayPublisher {

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.APIG_AI;
    }

    @Override
    public List<APIType> getSupportedAPITypes() {
        return List.of(APIType.AGENT_API, APIType.MODEL_API);
    }

    @Override
    public String publish(
            Gateway gateway, APIDefinitionVO apiDefinition, PublishConfig publishConfig) {

        return "Mock publish success";
    }

    @Override
    public String unpublish(
            Gateway gateway, APIDefinitionVO apiDefinition, PublishConfig publishConfig) {
        return "Mock unpublish success";
    }

    @Override
    public boolean isPublished(Gateway gateway, APIDefinitionVO apiDefinition) {
        return false;
    }

    @Override
    public void validatePublishConfig(APIDefinitionVO apiDefinition, PublishConfig publishConfig) {
        // Mock validation
    }

    private void publishMcpServer(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {}

    private void publishModelAPI(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {}

    private void publishAgentAPI(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {}

    private void unpublishMcpServer(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {}

    private void unpublishModelAPI(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {}

    private void unpublishAgentAPI(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {}
}
