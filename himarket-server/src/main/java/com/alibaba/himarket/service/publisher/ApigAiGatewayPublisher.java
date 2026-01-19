package com.alibaba.himarket.service.publisher;

import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.utils.McpPluginConfigUtil;
import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.dto.result.api.APIEndpointVO;
import com.alibaba.himarket.dto.result.mcp.McpServerInfo;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.gateway.AIGWOperator;
import com.alibaba.himarket.support.api.PublishConfig;
import com.alibaba.himarket.support.api.endpoint.EndpointConfig;
import com.alibaba.himarket.support.api.endpoint.HttpEndpointConfig;
import com.alibaba.himarket.support.api.property.BaseAPIProperty;
import com.alibaba.himarket.support.api.service.AiServiceConfig;
import com.alibaba.himarket.support.api.service.GatewayServiceConfig;
import com.alibaba.himarket.support.api.service.ServiceConfig;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.enums.PropertyType;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.alibaba.himarket.support.product.GatewayRefConfig;
import com.aliyun.sdk.service.apig20240327.models.CreateMcpServerRequest;
import com.aliyun.sdk.service.apig20240327.models.CreateMcpServerRequest.BackendConfig;
import com.aliyun.sdk.service.apig20240327.models.CreateMcpServerRequest.Services;
import com.aliyun.sdk.service.apig20240327.models.CreatePolicyAttachmentRequest;
import com.aliyun.sdk.service.apig20240327.models.CreatePolicyRequest;
import com.aliyun.sdk.service.apig20240327.models.HttpApiDeployConfig;
import com.aliyun.sdk.service.apig20240327.models.HttpApiDeployConfig.PolicyConfigs;
import com.aliyun.sdk.service.apig20240327.models.HttpApiDeployConfig.ServiceConfigs;
import com.aliyun.sdk.service.apig20240327.models.HttpRouteMatch;
import com.aliyun.sdk.service.apig20240327.models.HttpRouteMatch.HttpRouteMatchPath;
import com.aliyun.sdk.service.apig20240327.models.UpdateMcpServerRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
public class ApigAiGatewayPublisher extends ApigApiGatewayPublisher {

    public ApigAiGatewayPublisher(AIGWOperator aigwOperator) {
        super(aigwOperator);
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
    public GatewayRefConfig publish(
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

    private APIGRefConfig publishMcpServer(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
        // Check for DIRECT bridge type
        if (apiDefinition.getMetadata() != null
                && "DIRECT".equals(apiDefinition.getMetadata().get("mcpBridgeType"))) {
            return publishDirectMcpServer(gateway, publishConfig, apiDefinition);
        } else {
            return publishHttpToMcpBridgeServer(gateway, publishConfig, apiDefinition);
        }
    }

    private APIGRefConfig publishDirectMcpServer(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
        log.info("Publishing MCP server with DIRECT bridge type: {}", apiDefinition.getName());

        // Extract domain IDs
        Set<String> publishedDomainSet =
                publishConfig.getDomains().stream()
                        .map(d -> d.getDomain())
                        .collect(Collectors.toSet());
        List<String> domainIds = operator.getDomainIds(gateway, publishedDomainSet);

        // Build the backend config
        BackendConfig backendConfig = getMcpBackendConfig(gateway, publishConfig, apiDefinition);

        // Extract MCP config from ServiceConfig meta
        String mcpProtocol = "SSE";
        String mcpPath = null;
        if (publishConfig.getServiceConfig() != null
                && publishConfig.getServiceConfig().getMeta() != null) {
            mcpProtocol =
                    publishConfig.getServiceConfig().getMeta().getOrDefault("mcpProtocol", "SSE");
            mcpPath = publishConfig.getServiceConfig().getMeta().get("mcpPath");
        }

        // Build CreateMcpServerRequest for DIRECT type
        // Note: using exposedUriPath for the backend path and createFromType as
        // "ApiGatewayProxyMcpHosting"
        CreateMcpServerRequest.Builder requestBuilder =
                CreateMcpServerRequest.builder()
                        .name(apiDefinition.getName())
                        .type("RealMCP")
                        .match(getMcpMatch(apiDefinition))
                        .gatewayId(gateway.getGatewayId())
                        .protocol(mcpProtocol)
                        .domainIds(domainIds)
                        .exposedUriPath(mcpPath)
                        .createFromType("ApiGatewayProxyMcpHosting");

        // Only set description if it's not null/empty
        if (StringUtils.isNotEmpty(apiDefinition.getDescription())) {
            requestBuilder.description(apiDefinition.getDescription());
        }

        // Only set backendConfig if it's not null
        if (backendConfig != null) {
            requestBuilder.backendConfig(backendConfig);
        }

        CreateMcpServerRequest request = requestBuilder.build();

        // Check if MCP server with the same name already exists
        String mcpServerId;
        String mcpServerName = apiDefinition.getName();
        Optional<String> existingMcpServerId =
                operator.findMcpServerIdByName(gateway, mcpServerName);

        if (existingMcpServerId.isPresent()) {
            log.info(
                    "MCP server with name '{}' already exists in gateway {}, updating existing"
                            + " server",
                    mcpServerName,
                    gateway.getGatewayId());
            mcpServerId = existingMcpServerId.get();

            // Build update request
            UpdateMcpServerRequest.Builder updateRequestBuilder =
                    UpdateMcpServerRequest.builder()
                            .mcpServerId(mcpServerId)
                            .type("RealMCP")
                            .match(getMcpMatch(apiDefinition))
                            .protocol(mcpProtocol)
                            .domainIds(domainIds)
                            .exposedUriPath(mcpPath)
                            .createFromType("ApiGatewayProxyMcpHosting");

            if (StringUtils.isNotEmpty(apiDefinition.getDescription())) {
                updateRequestBuilder.description(apiDefinition.getDescription());
            }

            if (backendConfig != null) {
                updateRequestBuilder.backendConfig(convertBackendConfig(backendConfig));
            }

            UpdateMcpServerRequest updateRequest = updateRequestBuilder.build();
            operator.updateMcpServer(gateway, mcpServerId, updateRequest);
            log.info("Updated MCP Server with ID: {}", mcpServerId);
        } else {
            // Create new MCP server
            mcpServerId = operator.createMcpServer(gateway, request);
            log.info("Created new MCP Server with ID: {}", mcpServerId);
        }
        log.info("Using MCP Server with ID: {}", mcpServerId);

        // Deploy the MCP server
        operator.deployMcpServer(gateway, mcpServerId);
        log.info("Deployed MCP Server: {}", mcpServerId);

        // Fetch MCP server info after deployment/update to get routeId
        McpServerInfo data = operator.fetchRawMcpServerInfo(gateway, mcpServerId);

        // For DIRECT mode, we assume no plugin attachment logic is needed, or it's
        // similar
        // to Standard?
        // The requirement didn't specify plugin logic for DIRECT. Standard uses it to
        // attach
        // PluginConfig.
        // Assuming DIRECT might allow plugin attachment too if apiDefinition has it.
        // Reusing the same plugin attachment logic seems safe if the goal is to just
        // proxy.
        // However, "DIRECT" implies the gateway proxies directly to backend without
        // extra
        // processing?
        // But if it's an MCP server, it might still need the plugin handling if it's
        // handling
        // tools?
        // The user request says "publishDirectMcpServer".
        // Let's assume we DO want to attach the plugin config if it's there, just like
        // standard.
        // Copying the plugin attachment logic from publishStandardMcpServer.

        String pluginId = operator.findMcpServerPlugin(gateway);
        String pluginConfigBase64 =
                McpPluginConfigUtil.convertApiDefinitionToPluginConfig(apiDefinition);

        String pluginAttachmentId = data.getMcpServerConfigPluginAttachmentId();
        if (StringUtils.isNotEmpty(pluginAttachmentId)) {
            log.info(
                    "Plugin attachment already exists with ID: {}, updating it",
                    pluginAttachmentId);
            operator.updatePluginAttachment(
                    gateway,
                    pluginAttachmentId,
                    Collections.singletonList(data.getRouteId()),
                    true,
                    pluginConfigBase64);
        } else {
            log.info("Creating new plugin attachment");
            operator.createPluginAttachment(
                    gateway,
                    pluginId,
                    Collections.singletonList(data.getRouteId()),
                    "GatewayRoute",
                    true,
                    pluginConfigBase64);
        }

        return APIGRefConfig.builder().mcpServerId(mcpServerId).build();
    }

    private APIGRefConfig publishHttpToMcpBridgeServer(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
        Set<String> publishedDomainSet =
                publishConfig.getDomains().stream()
                        .map(d -> d.getDomain())
                        .collect(Collectors.toSet());
        List<String> domainIds = operator.getDomainIds(gateway, publishedDomainSet);
        // Note: CreateMcpServerRequest.Builder does not have an environmentId() method
        // The environmentId might be automatically assigned by the gateway based on
        // gatewayId
        // or it might need to be set through a different mechanism

        // Build the backend config (may be null for non-gateway services)
        BackendConfig backendConfig = getMcpBackendConfig(gateway, publishConfig, apiDefinition);

        CreateMcpServerRequest.Builder requestBuilder =
                CreateMcpServerRequest.builder()
                        .name(apiDefinition.getName())
                        .type("RealMCP")
                        .match(getMcpMatch(apiDefinition))
                        .gatewayId(gateway.getGatewayId())
                        .protocol("HTTP")
                        .domainIds(domainIds);

        // Only set description if it's not null/empty
        if (StringUtils.isNotEmpty(apiDefinition.getDescription())) {
            requestBuilder.description(apiDefinition.getDescription());
        }

        // Only set backendConfig if it's not null to avoid SDK serialization errors
        if (backendConfig != null) {
            requestBuilder.backendConfig(backendConfig);
        }

        CreateMcpServerRequest request = requestBuilder.build();

        // Debug: Print request as JSON to identify null values
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String requestJson = objectMapper.writeValueAsString(request);
            log.info("CreateMcpServerRequest JSON: {}", requestJson);
        } catch (Exception e) {
            log.error("Failed to serialize request to JSON", e);
        }

        // Check if MCP server with the same name already exists
        String mcpServerId;
        String mcpServerName = apiDefinition.getName();
        Optional<String> existingMcpServerId =
                operator.findMcpServerIdByName(gateway, mcpServerName);

        if (existingMcpServerId.isPresent()) {
            log.info(
                    "MCP server with name '{}' already exists in gateway {}, updating existing"
                            + " server",
                    mcpServerName,
                    gateway.getGatewayId());
            mcpServerId = existingMcpServerId.get();

            // Build update request with the same parameters as create
            UpdateMcpServerRequest.Builder updateRequestBuilder =
                    UpdateMcpServerRequest.builder()
                            .mcpServerId(mcpServerId)
                            .type("RealMCP")
                            .match(getMcpMatch(apiDefinition))
                            .protocol("HTTP")
                            .domainIds(domainIds);

            // Only set description if it's not null/empty
            if (StringUtils.isNotEmpty(apiDefinition.getDescription())) {
                updateRequestBuilder.description(apiDefinition.getDescription());
            }

            // Only set backendConfig if it's not null
            if (backendConfig != null) {
                updateRequestBuilder.backendConfig(convertBackendConfig(backendConfig));
            }

            UpdateMcpServerRequest updateRequest = updateRequestBuilder.build();
            operator.updateMcpServer(gateway, mcpServerId, updateRequest);
            log.info("Updated MCP Server with ID: {}", mcpServerId);
        } else {
            // Create new MCP server
            mcpServerId = operator.createMcpServer(gateway, request);
            log.info("Created new MCP Server with ID: {}", mcpServerId);
        }
        log.info("Using MCP Server with ID: {}", mcpServerId);

        // Deploy the MCP server
        operator.deployMcpServer(gateway, mcpServerId);
        log.info("Deployed MCP Server: {}", mcpServerId);

        // Fetch MCP server info after deployment
        McpServerInfo data = operator.fetchRawMcpServerInfo(gateway, mcpServerId);
        log.info(
                "Fetched MCP Server info - routeId: {}, deployStatus: {},"
                        + " mcpServerConfigPluginAttachmentId: {}",
                data.getRouteId(),
                data.getDeployStatus(),
                data.getMcpServerConfigPluginAttachmentId());

        String pluginId = operator.findMcpServerPlugin(gateway);
        String pluginConfigBase64 =
                McpPluginConfigUtil.convertApiDefinitionToPluginConfig(apiDefinition);

        // Check if plugin attachment already exists
        String pluginAttachmentId = data.getMcpServerConfigPluginAttachmentId();
        if (StringUtils.isNotEmpty(pluginAttachmentId)) {
            log.info(
                    "Plugin attachment already exists with ID: {}, updating it",
                    pluginAttachmentId);
            operator.updatePluginAttachment(
                    gateway,
                    pluginAttachmentId,
                    Collections.singletonList(data.getRouteId()),
                    true,
                    pluginConfigBase64);
            log.info("Updated plugin attachment with ID: {}", pluginAttachmentId);
        } else {
            log.info("Creating new plugin attachment");
            operator.createPluginAttachment(
                    gateway,
                    pluginId,
                    Collections.singletonList(data.getRouteId()),
                    "GatewayRoute",
                    true,
                    pluginConfigBase64);
            log.info("Created new plugin attachment");
        }

        return APIGRefConfig.builder().mcpServerId(mcpServerId).build();
    }

    /**
     * Generate match configuration for MCP server based on server name
     *
     * @param apiDefinition The API definition containing the MCP server name
     * @return Match configuration with path prefix pattern
     */
    private HttpRouteMatch getMcpMatch(APIDefinitionVO apiDefinition) {
        // Build match configuration with path prefix: /mcp-servers/{name}
        // URL encode the name to handle special characters like spaces
        String encodedName;
        try {
            encodedName = URLEncoder.encode(apiDefinition.getName(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("Failed to URL encode MCP server name: {}", apiDefinition.getName(), e);
            // Fallback to original name if encoding fails
            encodedName = apiDefinition.getName();
        }

        String pathValue = "/mcp-servers/" + encodedName;

        // Create path match configuration
        HttpRouteMatchPath path =
                HttpRouteMatchPath.builder().type("Prefix").value(pathValue).build();

        // Create match configuration
        return HttpRouteMatch.builder().path(path).build();
    }

    private BackendConfig getMcpBackendConfig(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
        if (publishConfig == null || publishConfig.getServiceConfig() == null) {
            return null;
        }

        ServiceConfig serviceConfig = publishConfig.getServiceConfig();

        // Ensure service exists (query/create/update) and get serviceId
        String serviceId = ensureServiceExists(gateway, apiDefinition.getName(), serviceConfig);

        // Build the backend service configuration
        Services service = Services.builder().serviceId(serviceId).build();

        // Build the BackendConfig with scene "SingleService" and the service list
        return BackendConfig.builder()
                .scene("SingleService")
                .services(Collections.singletonList(service))
                .build();
    }

    private APIGRefConfig publishModelAPI(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
        // 1. Fetch environment ID using operator method
        String environmentId = operator.getGatewayEnvironmentId(gateway);
        log.info(
                "Fetched environment ID: {} for gateway: {}",
                environmentId,
                gateway.getGatewayId());

        // 2. Extract domain IDs from publishConfig
        Set<String> publishedDomainSet =
                publishConfig.getDomains().stream()
                        .map(d -> d.getDomain())
                        .collect(Collectors.toSet());

        List<String> customDomainIds = operator.getDomainIds(gateway, publishedDomainSet);

        // 3. Extract AI protocols - default to OpenAI/v1
        List<String> aiProtocols = extractAiProtocols(publishConfig);

        // 4. Extract model category - default to Text
        String modelCategory = extractModelCategory(apiDefinition);

        // 5. Extract resource group ID from gateway
        String resourceGroupId = extractResourceGroupId(gateway);

        // 6. Build service configs for backend using SDK classes
        List<ServiceConfigs> serviceConfigs =
                buildServiceConfigsSDK(gateway, publishConfig, apiDefinition);

        // 7. Build policy configs for AI capabilities using SDK classes
        List<PolicyConfigs> policyConfigs = buildPolicyConfigsSDK(apiDefinition);

        // 8. Extract basePath from publishConfig, default to "/" if not provided
        String basePath = publishConfig.getBasePath();
        if (StringUtils.isEmpty(basePath)) {
            basePath = "/";
            log.warn("No basePath provided in publishConfig, using default: /");
        }
        log.info("Using basePath: {} for Model API", basePath);

        // 9. Build deploy configs using SDK classes
        HttpApiDeployConfig deployConfig =
                HttpApiDeployConfig.builder()
                        .gatewayId(gateway.getGatewayId())
                        .environmentId(environmentId)
                        .autoDeploy(true)
                        .customDomainIds(customDomainIds)
                        .backendScene("SingleService")
                        .serviceConfigs(serviceConfigs)
                        .policyConfigs(policyConfigs)
                        .gatewayType("AI")
                        .build();

        // 10. Call base class method to create or update HTTP API
        String httpApiId =
                createOrUpdateHttpApi(
                        gateway,
                        apiDefinition.getName(),
                        "LLM",
                        basePath,
                        aiProtocols,
                        deployConfig,
                        resourceGroupId,
                        apiDefinition.getDescription(),
                        modelCategory);

        // 11. Attach other policies that are not supported in buildPolicyConfigsSDK
        attachOtherPolicies(gateway, environmentId, httpApiId, apiDefinition, "LLMApi");

        return APIGRefConfig.builder().modelApiId(httpApiId).build();
    }

    /**
     * Attach other policies that are not supported in buildPolicyConfigsSDK
     * These policies need to be created and attached after the API is created
     *
     * @param gateway              The gateway
     * @param environmentId        The environment ID
     * @param httpApiId            The HTTP API ID
     * @param apiDefinition        The API definition
     * @param attachResourceType   The resource type for policy attachment (e.g., "LLMApi", "AgentApi")
     */
    private void attachOtherPolicies(
            Gateway gateway,
            String environmentId,
            String httpApiId,
            APIDefinitionVO apiDefinition,
            String attachResourceType) {
        if (apiDefinition == null || apiDefinition.getProperties() == null) {
            return;
        }

        for (BaseAPIProperty property : apiDefinition.getProperties()) {
            if (property == null || property.getType() == null) {
                continue;
            }

            PropertyType type = property.getType();
            // Skip policies handled in buildPolicyConfigsSDK (built-in policies)
            if (isBuiltInPolicy(apiDefinition.getType(), type)) {
                continue;
            }

            String className = getPolicyClassName(type);
            if (className == null) {
                continue;
            }

            try {
                // Prepare policy config JSON
                String configJson = JSONUtil.toJsonStr(property);

                // Generate policy name based on API definition name and policy class name
                String policyName = apiDefinition.getName() + "-" + className;

                // 1. Create Policy
                CreatePolicyRequest createRequest =
                        CreatePolicyRequest.builder()
                                .config(configJson)
                                .className(className)
                                .name(policyName)
                                .build();

                String policyId = operator.createPolicy(gateway, createRequest);

                // 2. Create Policy Attachment
                CreatePolicyAttachmentRequest attachmentRequest =
                        CreatePolicyAttachmentRequest.builder()
                                .attachResourceType(attachResourceType)
                                .environmentId(environmentId)
                                .gatewayId(gateway.getGatewayId())
                                .attachResourceId(httpApiId)
                                .policyId(policyId)
                                .build();

                operator.createPolicyAttachment(gateway, attachmentRequest);

                log.info(
                        "Successfully attached policy {} to API {}",
                        className,
                        apiDefinition.getName());

            } catch (Exception e) {
                log.error(
                        "Failed to attach policy {} to API {}",
                        className,
                        apiDefinition.getName(),
                        e);
                // We don't throw exception here to avoid failing the whole publish process
            }
        }
    }

    /**
     * Check if a property type is a built-in policy supported by buildPolicyConfigsSDK
     * Built-in policies are those that are directly handled by the SDK for specific API types
     *
     * @param apiType The API definition type
     * @param propertyType The property type
     * @return true if it's a built-in policy, false otherwise
     */
    private boolean isBuiltInPolicy(APIType apiType, PropertyType propertyType) {
        // For MODEL_API and AGENT_API, AI policies are built-in
        if (apiType == APIType.MODEL_API || apiType == APIType.AGENT_API) {
            switch (propertyType) {
                case OBSERVABILITY:
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }

    /**
     * Get the APIG policy class name for a property type
     *
     * @param type The property type
     * @return The policy class name or null if not a policy type
     */
    private String getPolicyClassName(PropertyType type) {
        switch (type) {
            case TIMEOUT:
                return "Timeout";
            case OBSERVABILITY:
                return "Observability";
            default:
                return null;
        }
    }

    private APIGRefConfig publishAgentAPI(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
        log.info(
                "Publishing Agent API: name={}, gatewayId={}",
                apiDefinition.getName(),
                gateway.getGatewayId());

        // 1. Fetch environment ID using operator method
        String environmentId = operator.getGatewayEnvironmentId(gateway);
        log.info(
                "Fetched environment ID: {} for gateway: {}",
                environmentId,
                gateway.getGatewayId());

        // 2. Extract domain IDs from publishConfig
        Set<String> publishedDomainSet =
                publishConfig.getDomains().stream()
                        .map(d -> d.getDomain())
                        .collect(Collectors.toSet());

        List<String> customDomainIds = operator.getDomainIds(gateway, publishedDomainSet);

        // 3. Extract agent protocols - default to Dify
        List<String> agentProtocols = extractAgentProtocols(publishConfig);

        // 4. Extract resource group ID from gateway
        String resourceGroupId = extractResourceGroupId(gateway);

        // 5. Build service configs for backend using SDK classes
        List<ServiceConfigs> serviceConfigs =
                buildServiceConfigsSDK(gateway, publishConfig, apiDefinition);

        // 6. Build policy configs (empty for Agent API by default)
        List<PolicyConfigs> policyConfigs = Collections.emptyList();

        // 7. Extract basePath from publishConfig, default to "/" if not provided
        String basePath = publishConfig.getBasePath();
        if (StringUtils.isEmpty(basePath)) {
            basePath = "/";
            log.warn("No basePath provided in publishConfig, using default: /");
        }
        log.info("Using basePath: {} for Agent API", basePath);

        // 8. Build deploy configs using SDK classes
        HttpApiDeployConfig deployConfig =
                HttpApiDeployConfig.builder()
                        .gatewayId(gateway.getGatewayId())
                        .environmentId(environmentId)
                        .autoDeploy(true)
                        .customDomainIds(customDomainIds)
                        .backendScene("SingleService")
                        .serviceConfigs(serviceConfigs)
                        .policyConfigs(policyConfigs)
                        .gatewayType("AI")
                        .build();

        // 9. Call base class method to create or update HTTP API
        String httpApiId =
                createOrUpdateHttpApi(
                        gateway,
                        apiDefinition.getName(),
                        "Agent",
                        basePath,
                        agentProtocols,
                        deployConfig,
                        resourceGroupId,
                        apiDefinition.getDescription(),
                        null);

        // 10. Create routes for each endpoint
        if (!CollectionUtils.isEmpty(apiDefinition.getEndpoints())) {
            log.info(
                    "Creating {} routes for Agent API: {}",
                    apiDefinition.getEndpoints().size(),
                    httpApiId);
            createAgentApiRoutes(
                    gateway,
                    httpApiId,
                    environmentId,
                    customDomainIds,
                    serviceConfigs,
                    apiDefinition.getEndpoints());
        }

        // 11. Attach other policies that are not supported in buildPolicyConfigsSDK
        attachOtherPolicies(gateway, environmentId, httpApiId, apiDefinition, "AgentApi");

        return APIGRefConfig.builder().agentApiId(httpApiId).build();
    }

    private void unpublishMcpServer(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
        // Find MCP server ID by name
        String mcpServerName = apiDefinition.getName();
        Optional<String> mcpServerId = operator.findMcpServerIdByName(gateway, mcpServerName);

        if (mcpServerId.isPresent()) {
            log.info("Undeploying MCP server: {}", mcpServerId.get());
            operator.undeployMcpServer(gateway, mcpServerId.get());
            log.info("Successfully undeployed MCP server: {}", mcpServerId.get());
        } else {
            log.warn("MCP server not found: {}", mcpServerName);
        }
    }

    /**
     * Create HTTP API routes for Agent API endpoints
     *
     * @param gateway         The gateway
     * @param httpApiId       The HTTP API ID
     * @param environmentId   The environment ID
     * @param customDomainIds List of custom domain IDs
     * @param serviceConfigs  List of service configurations
     * @param endpoints       List of API endpoints
     */
    private void createAgentApiRoutes(
            Gateway gateway,
            String httpApiId,
            String environmentId,
            List<String> customDomainIds,
            List<ServiceConfigs> serviceConfigs,
            List<APIEndpointVO> endpoints) {

        for (APIEndpointVO endpoint : endpoints) {
            try {
                createAgentApiRoute(
                        gateway,
                        httpApiId,
                        environmentId,
                        customDomainIds,
                        serviceConfigs,
                        endpoint);
            } catch (Exception e) {
                log.error(
                        "Failed to create route for endpoint: {}, error: {}",
                        endpoint.getName(),
                        e.getMessage(),
                        e);
                // Continue with other endpoints even if one fails
            }
        }
    }

    /**
     * Create a single HTTP API route for an Agent API endpoint
     *
     * @param gateway         The gateway
     * @param httpApiId       The HTTP API ID
     * @param environmentId   The environment ID
     * @param customDomainIds List of custom domain IDs
     * @param serviceConfigs  List of service configurations
     * @param endpoint        The API endpoint
     */
    private void createAgentApiRoute(
            Gateway gateway,
            String httpApiId,
            String environmentId,
            List<String> customDomainIds,
            List<ServiceConfigs> serviceConfigs,
            APIEndpointVO endpoint) {

        // Extract endpoint config
        EndpointConfig endpointConfig = endpoint.getConfig();
        if (!(endpointConfig instanceof HttpEndpointConfig)) {
            log.warn(
                    "Endpoint {} is not an HTTP endpoint, skipping route creation",
                    endpoint.getName());
            return;
        }

        HttpEndpointConfig httpConfig = (HttpEndpointConfig) endpointConfig;
        HttpEndpointConfig.MatchConfig matchConfig = httpConfig.getMatchConfig();
        if (matchConfig == null || matchConfig.getPath() == null) {
            log.warn(
                    "Endpoint {} has no match config or path, skipping route creation",
                    endpoint.getName());
            return;
        }

        // Build HttpRouteMatch
        HttpRouteMatchPath pathMatch =
                HttpRouteMatchPath.builder()
                        .type(matchConfig.getPath().getType())
                        .value(matchConfig.getPath().getValue())
                        .build();

        HttpRouteMatch.Builder matchBuilder = HttpRouteMatch.builder().path(pathMatch);

        // Add methods if specified
        if (!CollectionUtils.isEmpty(matchConfig.getMethods())) {
            matchBuilder.methods(matchConfig.getMethods());
        }

        // Set ignoreUriCase (default to false)
        matchBuilder.ignoreUriCase(false);

        HttpRouteMatch match = matchBuilder.build();

        // Extract serviceId from serviceConfigs
        String serviceId = "";
        if (!CollectionUtils.isEmpty(serviceConfigs)) {
            serviceId = serviceConfigs.get(0).getServiceId();
        }

        // Call operator to create route
        operator.createHttpApiRoute(
                gateway,
                httpApiId,
                endpoint.getName(),
                environmentId,
                customDomainIds,
                match,
                serviceId);

        log.info(
                "Successfully created route for endpoint: {} with path: {}",
                endpoint.getName(),
                matchConfig.getPath().getValue());
    }

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

    /**
     * Build policy configurations for Model API using SDK classes
     * Policy configurations are converted from API definition properties
     * Each property with type matching a policy type will be converted to a PolicyConfig
     *
     * @param apiDefinition The API definition containing policy configuration
     * @return List of SDK PolicyConfigs
     */
    private List<PolicyConfigs> buildPolicyConfigsSDK(APIDefinitionVO apiDefinition) {
        List<PolicyConfigs> configs = new ArrayList<>();

        if (apiDefinition == null || apiDefinition.getProperties() == null) {
            return configs;
        }

        // Convert each property to a PolicyConfig
        for (BaseAPIProperty property : apiDefinition.getProperties()) {
            PolicyConfigs policyConfig = convertPropertyToPolicyConfig(property);
            if (policyConfig != null) {
                configs.add(policyConfig);
            }
        }

        return configs;
    }

    /**
     * Convert a BaseAPIProperty to a PolicyConfig
     * Maps property types to corresponding policy types
     *
     * @param property The property to convert
     * @return PolicyConfig or null if property is not a policy type
     */
    private PolicyConfigs convertPropertyToPolicyConfig(BaseAPIProperty property) {
        if (property == null || property.getType() == null) {
            return null;
        }

        boolean enabled = property.getEnabled() != null ? property.getEnabled() : false;
        PropertyType propertyType = property.getType();

        // Map property type to policy type and build corresponding PolicyConfig
        switch (propertyType) {
            case OBSERVABILITY:
                return PolicyConfigs.builder().type("AiStatistics").enable(enabled).build();
            default:
                // Not a policy type, skip
                log.debug("Skipping property with type '{}' - not a policy type", propertyType);
                return null;
        }
    }

    /**
     * Build service configurations for Model API backend using SDK classes
     * Supports both AI Service and Gateway Service configurations
     *
     * @param gateway       The gateway
     * @param publishConfig The publish configuration
     * @param apiDefinition The API definition
     * @return List of SDK ServiceConfigs
     */
    private List<ServiceConfigs> buildServiceConfigsSDK(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
        List<ServiceConfigs> serviceConfigs = new ArrayList<>();

        ServiceConfig serviceConfig = publishConfig.getServiceConfig();
        if (serviceConfig == null) {
            log.warn("No service config found in publish config");
            return serviceConfigs;
        }

        if (serviceConfig.isNativeService()) {
            // Native gateway service - may already have serviceId
            GatewayServiceConfig gatewayServiceConfig = (GatewayServiceConfig) serviceConfig;
            String serviceId = gatewayServiceConfig.getServiceId();

            // If serviceId is not provided, ensure service exists
            if (StringUtils.isEmpty(serviceId)) {
                serviceId = ensureServiceExists(gateway, apiDefinition.getName(), serviceConfig);
            }

            ServiceConfigs sdkServiceConfig = ServiceConfigs.builder().serviceId(serviceId).build();
            serviceConfigs.add(sdkServiceConfig);

            log.info("Using Gateway Service for Model API: serviceId={}", serviceId);

        } else {
            // Non-native service (AI, DNS, FixedAddress, etc.) - ensure service exists
            String serviceId = ensureServiceExists(gateway, apiDefinition.getName(), serviceConfig);
            ServiceConfigs sdkServiceConfig = ServiceConfigs.builder().serviceId(serviceId).build();
            serviceConfigs.add(sdkServiceConfig);

            // Log with service-specific details
            if (serviceConfig instanceof AiServiceConfig) {
                AiServiceConfig aiServiceConfig = (AiServiceConfig) serviceConfig;
                log.info(
                        "Using AI Service for Model API: provider={}, protocol={}, serviceId={}",
                        aiServiceConfig.getProvider(),
                        aiServiceConfig.getProtocol(),
                        serviceId);
            } else {
                log.info(
                        "Using {} Service for Model API: serviceId={}",
                        serviceConfig.getClass().getSimpleName(),
                        serviceId);
            }
        }

        return serviceConfigs;
    }

    /**
     * Extract model category from API definition metadata
     *
     * @param apiDefinition The API definition
     * @return Model category (e.g., "Text", "Image", "Audio", "Video")
     */
    private String extractModelCategory(APIDefinitionVO apiDefinition) {
        // Extract from API definition metadata if available
        if (apiDefinition != null && apiDefinition.getMetadata() != null) {
            Object scenario = apiDefinition.getMetadata().get("scenario");
            if (scenario != null) {
                String scenarioStr = scenario.toString();
                // Convert scenario value to category key
                return convertScenarioToCategory(scenarioStr);
            }
        }

        // Default to "Text" for LLM models
        return "Text";
    }

    /**
     * Convert scenario value from frontend form to category key
     *
     * @param scenario The scenario value (e.g., "text-generation", "image-generation")
     * @return Category key (e.g., "Text", "Image")
     */
    private String convertScenarioToCategory(String scenario) {
        if (StringUtils.isEmpty(scenario)) {
            return "Text";
        }

        switch (scenario.toLowerCase()) {
            case "text-generation":
                return "Text";
            default:
                log.warn("Unknown scenario: {}, defaulting to Text", scenario);
                return "Text";
        }
    }

    /**
     * Extract AI protocols from publish config
     *
     * @param publishConfig The publish configuration
     * @return List of AI protocols (e.g., ["OpenAI/v1"], ["Anthropic"])
     */
    private List<String> extractAiProtocols(PublishConfig publishConfig) {
        // Extract from publishConfig or API definition metadata
        // For now, default to OpenAI/v1
        // This should be configurable through the publish config or API definition
        return List.of("OpenAI/v1");
    }

    /**
     * Extract agent protocols from publish config
     *
     * @param publishConfig The publish configuration
     * @return List of agent protocols (e.g., ["Dify"])
     */
    private List<String> extractAgentProtocols(PublishConfig publishConfig) {
        // Extract from publishConfig or API definition metadata
        // For now, default to Dify
        // This should be configurable through the publish config or API definition
        return List.of("Dify");
    }

    /**
     * Convert CreateMcpServerRequest.BackendConfig to
     * UpdateMcpServerRequest.BackendConfig
     *
     * @param backendConfig The backend config to convert
     * @return Converted backend config or null
     */
    private UpdateMcpServerRequest.BackendConfig convertBackendConfig(
            CreateMcpServerRequest.BackendConfig backendConfig) {
        if (backendConfig == null) {
            return null;
        }

        List<UpdateMcpServerRequest.Services> services = null;
        if (backendConfig.getServices() != null) {
            services =
                    backendConfig.getServices().stream()
                            .map(this::convertService)
                            .collect(Collectors.toList());
        }

        return UpdateMcpServerRequest.BackendConfig.builder()
                .scene(backendConfig.getScene())
                .services(services)
                .build();
    }

    private UpdateMcpServerRequest.Services convertService(CreateMcpServerRequest.Services s) {
        return UpdateMcpServerRequest.Services.builder()
                .serviceId(s.getServiceId())
                .port(s.getPort())
                .version(s.getVersion())
                .weight(s.getWeight())
                .build();
    }
}
