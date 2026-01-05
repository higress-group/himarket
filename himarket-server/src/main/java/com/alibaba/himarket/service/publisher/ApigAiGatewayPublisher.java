package com.alibaba.himarket.service.publisher;

import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.api.GatewayPublisher;
import com.alibaba.himarket.service.gateway.AIGWOperator;
import com.alibaba.himarket.support.api.PublishConfig;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.GatewayType;
import com.aliyun.sdk.service.apig20240327.models.CreateMcpServerRequest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class ApigAiGatewayPublisher implements GatewayPublisher {

    private AIGWOperator operator;

    public ApigAiGatewayPublisher(AIGWOperator aigwOperator) {
        this.operator = aigwOperator;
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.APIG_AI;
    }

    @Override
    public List<APIType> getSupportedAPITypes() {
        return List.of(APIType.MCP_SERVER, APIType.AGENT_API, APIType.MODEL_API);
    }

    @Override
    public String publish(
            Gateway gateway, APIDefinitionVO apiDefinition, PublishConfig publishConfig) {
        switch (apiDefinition.getType()) {
            case MCP_SERVER:
                return publishMcpServer(gateway, publishConfig, apiDefinition);
            case MODEL_API:
                return publishModelAPI(gateway, publishConfig, apiDefinition);
            case AGENT_API:
                return publishAgentAPI(gateway, publishConfig, apiDefinition);
            default:
                throw new IllegalArgumentException(
                        "Unsupported API type: " + apiDefinition.getType());
        }
    }

    @Override
    public String unpublish(
            Gateway gateway, APIDefinitionVO apiDefinition, PublishConfig publishConfig) {
        switch (apiDefinition.getType()) {
            case MCP_SERVER:
                unpublishMcpServer(gateway, publishConfig, apiDefinition);
                break;
            case MODEL_API:
                unpublishModelAPI(gateway, publishConfig, apiDefinition);
                break;
            case AGENT_API:
                unpublishAgentAPI(gateway, publishConfig, apiDefinition);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported API type: " + apiDefinition.getType());
        }
        return "Mock unpublish success";
    }

    @Override
    public boolean isPublished(Gateway gateway, APIDefinitionVO apiDefinition) {
        return false;
    }

    @Override
    public void validatePublishConfig(APIDefinitionVO apiDefinition, PublishConfig publishConfig) {
        switch (apiDefinition.getType()) {
            case MCP_SERVER:
                validateMcpServerConfig(apiDefinition, publishConfig);
                break;
            case MODEL_API:
                validateModelAPIConfig(apiDefinition, publishConfig);
                break;
            case AGENT_API:
                validateAgentAPIConfig(apiDefinition, publishConfig);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported API type: " + apiDefinition.getType());
        }
    }

    private String publishMcpServer(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
        List<DomainResult> gatewayDomains = operator.getGatewayDomains(gateway);
        Set<String> publishedDomainSet = publishConfig.getDomains().stream().map(d -> d.getDomain()).collect(Collectors.toSet());
        List<String> domainIds = gatewayDomains.stream().filter(d -> publishedDomainSet.contains(d.getDomain())).map((d) -> {
            return d.getMeta().get("domainId");
        }).toList();

        


        return "mcp-server-resource-id";
    }

    private String publishModelAPI(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
        // TODO: Implement Model API publish logic
        return "model-api-resource-id";
    }

    private String publishAgentAPI(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
        // TODO: Implement Agent API publish logic
        return "agent-api-resource-id";
    }

    private void unpublishMcpServer(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {}

    private void unpublishModelAPI(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {}

    private void unpublishAgentAPI(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {}

    private void validateMcpServerConfig(
            APIDefinitionVO apiDefinition, PublishConfig publishConfig) {}

    private void validateModelAPIConfig(
            APIDefinitionVO apiDefinition, PublishConfig publishConfig) {}

    private void validateAgentAPIConfig(
            APIDefinitionVO apiDefinition, PublishConfig publishConfig) {}
}
