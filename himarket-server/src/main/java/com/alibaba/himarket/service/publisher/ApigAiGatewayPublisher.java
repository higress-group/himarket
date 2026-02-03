package com.alibaba.himarket.service.publisher;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.mcp.McpServerInfo;
import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.entity.APIDeployment;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.repository.APIDeploymentRepository;
import com.alibaba.himarket.service.gateway.AIGWOperator;
import com.alibaba.himarket.support.api.DeploymentConfig;
import com.alibaba.himarket.support.api.DomainConfig;
import com.alibaba.himarket.support.api.property.APIPolicy;
import com.alibaba.himarket.support.api.service.AiServiceConfig;
import com.alibaba.himarket.support.api.service.GatewayServiceConfig;
import com.alibaba.himarket.support.api.service.ServiceConfig;
import com.alibaba.himarket.support.api.v2.HttpRoute;
import com.alibaba.himarket.support.api.v2.spec.AgentAPISpec;
import com.alibaba.himarket.support.api.v2.spec.MCPServerSpec;
import com.alibaba.himarket.support.api.v2.spec.ModelAPISpec;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.enums.PolicyType;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.alibaba.himarket.support.product.GatewayRefConfig;
import com.alibaba.himarket.utils.JsonUtil;
import com.alibaba.himarket.utils.McpPluginConfigUtil;
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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApigAiGatewayPublisher extends ApigApiGatewayPublisher {

    private final AIGWOperator operator;

    private final APIDeploymentRepository apiPublishRecordRepository;

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
            Gateway gateway, APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {
        switch (apiDefinition.getType()) {
            case MCP_SERVER:
                return publishMcpServer(gateway, deploymentConfig, apiDefinition);
            case MODEL_API:
                return publishModelAPI(gateway, deploymentConfig, apiDefinition);
            case AGENT_API:
                return publishAgentAPI(gateway, deploymentConfig, apiDefinition);
            default:
                throw new IllegalArgumentException(
                        "Unsupported API type: " + apiDefinition.getType());
        }
    }

    @Override
    public String unpublish(
            Gateway gateway, APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {
        switch (apiDefinition.getType()) {
            case MCP_SERVER:
                unpublishMcpServer(gateway, deploymentConfig, apiDefinition);
                break;
            case MODEL_API:
                unpublishModelAPI(gateway, deploymentConfig, apiDefinition);
                break;
            case AGENT_API:
                unpublishAgentAPI(gateway, deploymentConfig, apiDefinition);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported API type: " + apiDefinition.getType());
        }
        return "Mock unpublish success";
    }

    @Override
    public boolean isPublished(Gateway gateway, APIDefinition apiDefinition) {
        return false;
    }

    @Override
    public void validateDeploymentConfig(
            APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {
        switch (apiDefinition.getType()) {
            case MCP_SERVER:
                validateMcpServerConfig(apiDefinition, deploymentConfig);
                break;
            case MODEL_API:
                validateModelAPIConfig(apiDefinition, deploymentConfig);
                break;
            case AGENT_API:
                validateAgentAPIConfig(apiDefinition, deploymentConfig);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported API type: " + apiDefinition.getType());
        }
    }

    private APIGRefConfig publishMcpServer(
            Gateway gateway, DeploymentConfig deploymentConfig, APIDefinition apiDefinition) {
        // Check for DIRECT bridge type from MCPServerSpec
        if (apiDefinition.getSpec() instanceof MCPServerSpec mcpSpec) {
            if ("DIRECT".equals(mcpSpec.getBridgeType())) {
                return publishDirectMcpServer(gateway, deploymentConfig, apiDefinition);
            }
        }
        // Default to HTTP_TO_MCP bridge
        return publishHttpToMcpBridgeServer(gateway, deploymentConfig, apiDefinition);
    }

    private APIGRefConfig publishDirectMcpServer(
            Gateway gateway, DeploymentConfig deploymentConfig, APIDefinition apiDefinition) {
        log.info("Publishing MCP server with DIRECT bridge type: {}", apiDefinition.getName());

        // Extract domain IDs
        Set<String> publishedDomainSet =
                deploymentConfig.getDomains().stream()
                        .map(d -> d.getDomain())
                        .collect(Collectors.toSet());
        List<String> domainIds = operator.getDomainIds(gateway, publishedDomainSet);

        // Build the backend config
        BackendConfig backendConfig = getMcpBackendConfig(gateway, deploymentConfig, apiDefinition);

        // Extract MCP config from ServiceConfig meta
        String mcpProtocol = "SSE";
        String mcpPath = null;
        if (deploymentConfig.getServiceConfig() != null
                && deploymentConfig.getServiceConfig().getMeta() != null) {
            mcpProtocol =
                    deploymentConfig
                            .getServiceConfig()
                            .getMeta()
                            .getOrDefault("mcpProtocol", "SSE");
            mcpPath = deploymentConfig.getServiceConfig().getMeta().get("mcpPath");
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
            Gateway gateway, DeploymentConfig deploymentConfig, APIDefinition apiDefinition) {
        Set<String> publishedDomainSet =
                deploymentConfig.getDomains().stream()
                        .map(d -> d.getDomain())
                        .collect(Collectors.toSet());
        List<String> domainIds = operator.getDomainIds(gateway, publishedDomainSet);
        // Note: CreateMcpServerRequest.Builder does not have an environmentId() method
        // The environmentId might be automatically assigned by the gateway based on
        // gatewayId
        // or it might need to be set through a different mechanism

        // Build the backend config (may be null for non-gateway services)
        BackendConfig backendConfig = getMcpBackendConfig(gateway, deploymentConfig, apiDefinition);

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
            String requestJson = JsonUtil.toJson(request);
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
    private HttpRouteMatch getMcpMatch(APIDefinition apiDefinition) {
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
            Gateway gateway, DeploymentConfig deploymentConfig, APIDefinition apiDefinition) {
        if (deploymentConfig == null || deploymentConfig.getServiceConfig() == null) {
            return null;
        }

        ServiceConfig serviceConfig = deploymentConfig.getServiceConfig();

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
            Gateway gateway, DeploymentConfig deploymentConfig, APIDefinition apiDefinition) {
        // 1. Fetch environment ID using operator method
        String environmentId = operator.getGatewayEnvironmentId(gateway);

        // 2. Extract domain IDs (priority: deploymentConfig > spec)
        List<String> customDomainIds = extractDomainIds(gateway, deploymentConfig, apiDefinition);

        // 3. Extract AI protocols from spec
        List<String> aiProtocols = extractAiProtocols(apiDefinition);

        // 4. Extract model category from spec
        String modelCategory = extractModelCategory(apiDefinition);

        // 5. Extract resource group ID from gateway
        String resourceGroupId = extractResourceGroupId(gateway);

        // 6. Build service configs for backend using SDK classes
        List<ServiceConfigs> serviceConfigs =
                buildServiceConfigsSDK(gateway, deploymentConfig, apiDefinition);

        // 7. Build policy configs for AI capabilities using SDK classes
        List<PolicyConfigs> policyConfigs = buildPolicyConfigsSDK(apiDefinition);

        // 8. Extract basePath from spec, default to "/" if not provided
        String basePath = "/";
        if (apiDefinition.getSpec() instanceof ModelAPISpec) {
            ModelAPISpec spec = (ModelAPISpec) apiDefinition.getSpec();
            if (StringUtils.isNotEmpty(spec.getBasePath())) {
                basePath = spec.getBasePath();
            }
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

        return APIGRefConfig.builder()
                .modelApiId(httpApiId)
                .modelApiName(apiDefinition.getName())
                .build();
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
            APIDefinition apiDefinition,
            String attachResourceType) {
        List<APIPolicy> policies = apiDefinition.getPolicies();
        if (apiDefinition == null || policies == null || policies.isEmpty()) {
            return;
        }

        for (APIPolicy property : policies) {
            if (property == null || property.getType() == null) {
                continue;
            }

            PolicyType type = property.getType();
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
                String configJson = JsonUtil.toJson(property);

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
     * @param policyType The property type
     * @return true if it's a built-in policy, false otherwise
     */
    private boolean isBuiltInPolicy(APIType apiType, PolicyType policyType) {
        // For MODEL_API and AGENT_API, AI policies are built-in
        if (apiType == APIType.MODEL_API || apiType == APIType.AGENT_API) {
            switch (policyType) {
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
    private String getPolicyClassName(PolicyType type) {
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
            Gateway gateway, DeploymentConfig deploymentConfig, APIDefinition apiDefinition) {
        log.info(
                "Publishing Agent API: name={}, gatewayId={}",
                apiDefinition.getName(),
                gateway.getGatewayId());

        // 1. Fetch environment ID using operator method
        String environmentId = operator.getGatewayEnvironmentId(gateway);

        // 2. Extract domain IDs (priority: deploymentConfig > spec)
        List<String> customDomainIds = extractDomainIds(gateway, deploymentConfig, apiDefinition);

        // 3. Extract agent protocols - default to Dify
        List<String> agentProtocols = extractAgentProtocols(deploymentConfig);

        // 4. Extract resource group ID from gateway
        String resourceGroupId = extractResourceGroupId(gateway);

        // 5. Build service configs for backend using SDK classes
        List<ServiceConfigs> serviceConfigs =
                buildServiceConfigsSDK(gateway, deploymentConfig, apiDefinition);

        // 6. Build policy configs (empty for Agent API by default)
        List<PolicyConfigs> policyConfigs = Collections.emptyList();

        // 7. Extract basePath from spec, default to "/" if not provided
        String basePath = "/";
        if (apiDefinition.getSpec() instanceof AgentAPISpec) {
            AgentAPISpec spec = (AgentAPISpec) apiDefinition.getSpec();
            if (StringUtils.isNotEmpty(spec.getBasePath())) {
                basePath = spec.getBasePath();
            }
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

        // 10. Create routes from spec
        if (apiDefinition.getSpec() instanceof AgentAPISpec) {
            AgentAPISpec spec = (AgentAPISpec) apiDefinition.getSpec();
            if (!CollectionUtils.isEmpty(spec.getHttpRoutes())) {
                log.info(
                        "Creating {} routes for Agent API: {}",
                        spec.getHttpRoutes().size(),
                        httpApiId);
                createAgentApiRoutes(
                        gateway,
                        httpApiId,
                        environmentId,
                        customDomainIds,
                        serviceConfigs,
                        spec.getHttpRoutes());
            }
        } else {
            log.warn("Agent API spec not found or invalid for: {}", apiDefinition.getName());
        }

        // 11. Attach other policies that are not supported in buildPolicyConfigsSDK
        attachOtherPolicies(gateway, environmentId, httpApiId, apiDefinition, "AgentApi");

        return APIGRefConfig.builder().agentApiId(httpApiId).build();
    }

    private void unpublishMcpServer(
            Gateway gateway, DeploymentConfig deploymentConfig, APIDefinition apiDefinition) {
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
     * Create HTTP API routes for Agent API from spec
     *
     * @param gateway         The gateway
     * @param httpApiId       The HTTP API ID
     * @param environmentId   The environment ID
     * @param customDomainIds List of custom domain IDs
     * @param serviceConfigs  List of service configurations
     * @param httpRoutes      List of HTTP routes from spec
     */
    private void createAgentApiRoutes(
            Gateway gateway,
            String httpApiId,
            String environmentId,
            List<String> customDomainIds,
            List<ServiceConfigs> serviceConfigs,
            List<HttpRoute> httpRoutes) {

        for (HttpRoute route : httpRoutes) {
            try {
                createAgentApiRoute(
                        gateway, httpApiId, environmentId, customDomainIds, serviceConfigs, route);
            } catch (Exception e) {
                log.error(
                        "Failed to create route: {}, error: {}",
                        route.getName(),
                        e.getMessage(),
                        e);
                // Continue with other routes even if one fails
            }
        }
    }

    /**
     * Create a single HTTP API route for an Agent API from spec
     *
     * @param gateway         The gateway
     * @param httpApiId       The HTTP API ID
     * @param environmentId   The environment ID
     * @param customDomainIds List of custom domain IDs
     * @param serviceConfigs  List of service configurations
     * @param route           The HTTP route from spec
     */
    private void createAgentApiRoute(
            Gateway gateway,
            String httpApiId,
            String environmentId,
            List<String> customDomainIds,
            List<ServiceConfigs> serviceConfigs,
            HttpRoute route) {

        // Validate route match config
        if (route.getMatch() == null || route.getMatch().getPath() == null) {
            log.warn(
                    "Route {} has no match config or path, skipping route creation",
                    route.getName());
            return;
        }

        HttpRoute.RouteMatch routeMatch = route.getMatch();
        HttpRoute.PathMatch pathMatchSpec = routeMatch.getPath();

        // Build HttpRouteMatch for APIG SDK
        HttpRouteMatchPath pathMatch =
                HttpRouteMatchPath.builder()
                        .type(pathMatchSpec.getType())
                        .value(pathMatchSpec.getValue())
                        .build();

        HttpRouteMatch.Builder matchBuilder = HttpRouteMatch.builder().path(pathMatch);

        // Add methods if specified
        if (!CollectionUtils.isEmpty(routeMatch.getMethods())) {
            matchBuilder.methods(routeMatch.getMethods());
        }

        // Set ignoreUriCase based on caseSensitive flag
        Boolean caseSensitive = pathMatchSpec.getCaseSensitive();
        matchBuilder.ignoreUriCase(caseSensitive != null && !caseSensitive);

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
                route.getName(),
                environmentId,
                customDomainIds,
                match,
                serviceId);

        log.info(
                "Successfully created route: {} with path: {}",
                route.getName(),
                pathMatchSpec.getValue());
    }

    private void unpublishModelAPI(
            Gateway gateway, DeploymentConfig deploymentConfig, APIDefinition apiDefinition) {
        String apiDefinitionId = apiDefinition.getApiDefinitionId();
        String apiName = apiDefinition.getName();

        Optional<APIDeployment> latestRecord =
                apiPublishRecordRepository
                        .findFirstByApiDefinitionIdAndGatewayIdOrderByCreatedAtDesc(
                                apiDefinitionId, gateway.getGatewayId());

        String httpApiId = null;
        if (latestRecord.isPresent()) {
            String gatewayResourceConfig = latestRecord.get().getGatewayResourceConfig();
            if (StringUtils.isNotBlank(gatewayResourceConfig)) {
                try {
                    APIGRefConfig refConfig =
                            JsonUtil.parse(gatewayResourceConfig, APIGRefConfig.class);
                    if (refConfig != null) {
                        if (StringUtils.isNotBlank(refConfig.getModelApiId())) {
                            httpApiId = refConfig.getModelApiId();
                        } else if (StringUtils.isNotBlank(refConfig.getApiId())) {
                            httpApiId = refConfig.getApiId();
                        }
                    }
                } catch (Exception e) {
                    log.warn(
                            "Failed to parse gatewayResourceConfig for apiDefinitionId={},"
                                    + " deploymentId={}",
                            apiDefinitionId,
                            latestRecord.get().getDeploymentId(),
                            e);
                }
            }
        }

        if (StringUtils.isBlank(httpApiId)) {
            httpApiId = operator.findHttpApiIdByName(gateway, apiName, "LLM").orElse(null);
        }

        if (StringUtils.isBlank(httpApiId)) {
            log.warn(
                    "No HTTP API found for Model API unpublish: name={}, gatewayId={}",
                    apiName,
                    gateway.getGatewayId());
            return;
        }

        log.info(
                "Deleting Model HTTP API: name={}, httpApiId={}, gatewayId={}",
                apiName,
                httpApiId,
                gateway.getGatewayId());

        operator.deleteHttpApi(gateway, httpApiId);
    }

    private void unpublishAgentAPI(
            Gateway gateway, DeploymentConfig deploymentConfig, APIDefinition apiDefinition) {}

    private void validateMcpServerConfig(
            APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {}

    private void validateModelAPIConfig(
            APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {}

    private void validateAgentAPIConfig(
            APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {}

    /**
     * Build policy configurations for Model API using SDK classes
     * Policy configurations are converted from API definition properties
     * Each property with type matching a policy type will be converted to a PolicyConfig
     *
     * @param apiDefinition The API definition containing policy configuration
     * @return List of SDK PolicyConfigs
     */
    private List<PolicyConfigs> buildPolicyConfigsSDK(APIDefinition apiDefinition) {
        List<PolicyConfigs> configs = new ArrayList<>();

        List<APIPolicy> policies = apiDefinition.getPolicies();
        if (policies == null || policies.isEmpty()) {
            return configs;
        }

        // Convert each policy to a PolicyConfig
        for (APIPolicy policy : policies) {
            PolicyConfigs policyConfig = convertPropertyToPolicyConfig(policy);
            if (policyConfig != null) {
                configs.add(policyConfig);
            }
        }

        return configs;
    }

    /**
     * Convert an APIPolicy to a PolicyConfig
     * Maps property types to corresponding policy types
     *
     * @param property The property to convert
     * @return PolicyConfig or null if property is not a policy type
     */
    private PolicyConfigs convertPropertyToPolicyConfig(APIPolicy property) {
        if (property == null || property.getType() == null) {
            return null;
        }

        boolean enabled = property.getEnabled() != null ? property.getEnabled() : false;
        PolicyType policyType = property.getType();

        // Map property type to policy type and build corresponding PolicyConfig
        switch (policyType) {
            case OBSERVABILITY:
                return PolicyConfigs.builder().type("AiStatistics").enable(enabled).build();
            default:
                // Not a policy type, skip
                log.debug("Skipping property with type '{}' - not a policy type", policyType);
                return null;
        }
    }

    /**
     * Build service configurations for Model API backend using SDK classes
     * Supports both AI Service and Gateway Service configurations
     *
     * @param gateway       The gateway
     * @param deploymentConfig The publish configuration
     * @param apiDefinition The API definition
     * @return List of SDK ServiceConfigs
     */
    private List<ServiceConfigs> buildServiceConfigsSDK(
            Gateway gateway, DeploymentConfig deploymentConfig, APIDefinition apiDefinition) {
        List<ServiceConfigs> serviceConfigs = new ArrayList<>();

        ServiceConfig serviceConfig = deploymentConfig.getServiceConfig();
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
     * Extract model category from API definition spec
     *
     * @param apiDefinition The API definition
     * @return Model category (e.g., "Text", "Image", "Audio", "Video")
     */
    private String extractModelCategory(APIDefinition apiDefinition) {
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
     * Extract AI protocols from API definition spec
     *
     * @param apiDefinition The API definition
     * @return List of AI protocols (e.g., ["OpenAI/v1"], ["Anthropic"])
     */
    private List<String> extractAiProtocols(APIDefinition apiDefinition) {
        // Extract from ModelAPISpec if available
        if (apiDefinition != null && apiDefinition.getSpec() instanceof ModelAPISpec modelSpec) {
            if (modelSpec.getProtocols() != null && !modelSpec.getProtocols().isEmpty()) {
                return modelSpec.getProtocols();
            }
        }

        // Default to OpenAI/v1 for backward compatibility
        return List.of("OpenAI/v1");
    }

    /**
     * Extract agent protocols from publish config
     *
     * @param deploymentConfig The publish configuration
     * @return List of agent protocols (e.g., ["Dify"])
     */
    private List<String> extractAgentProtocols(DeploymentConfig deploymentConfig) {
        // Extract from deploymentConfig or API definition metadata
        // For now, default to Dify
        // This should be configurable through the publish config or API definition
        return List.of("Dify");
    }

    /**
     * Extract domain IDs with priority: deploymentConfig > spec
     * This method supports flexible domain configuration:
     * 1. If deploymentConfig.domains exists, use it (for environment-specific overrides)
     * 2. Otherwise, extract from API spec (Model API: first route, Agent API: all routes)
     *
     * @param gateway       The gateway
     * @param deploymentConfig The publish configuration
     * @param apiDefinition The API definition
     * @return List of domain IDs
     */
    private List<String> extractDomainIds(
            Gateway gateway, DeploymentConfig deploymentConfig, APIDefinition apiDefinition) {

        Set<String> domainSet = new HashSet<>();

        // Priority 1: Use deploymentConfig.domains if specified (for environment-specific
        // overrides)
        if (deploymentConfig.getDomains() != null && !deploymentConfig.getDomains().isEmpty()) {
            domainSet =
                    deploymentConfig.getDomains().stream()
                            .map(DomainConfig::getDomain)
                            .collect(Collectors.toSet());
            log.info("Using domains from deploymentConfig: {}", domainSet);
        }
        // Priority 2: Extract from API spec
        else if (apiDefinition.getSpec() instanceof ModelAPISpec) {
            ModelAPISpec spec = (ModelAPISpec) apiDefinition.getSpec();
            if (spec.getHttpRoutes() != null && !spec.getHttpRoutes().isEmpty()) {
                // For Model API, use the first route's domains
                HttpRoute firstRoute = spec.getHttpRoutes().get(0);
                if (firstRoute.getDomains() != null && !firstRoute.getDomains().isEmpty()) {
                    domainSet =
                            firstRoute.getDomains().stream()
                                    .map(HttpRoute.Domain::getDomain)
                                    .collect(Collectors.toSet());
                    log.info("Using domains from Model API spec (first route): {}", domainSet);
                }
            }
        } else if (apiDefinition.getSpec() instanceof AgentAPISpec) {
            AgentAPISpec spec = (AgentAPISpec) apiDefinition.getSpec();
            if (spec.getHttpRoutes() != null && !spec.getHttpRoutes().isEmpty()) {
                // For Agent API, merge domains from all routes
                domainSet =
                        spec.getHttpRoutes().stream()
                                .filter(route -> route.getDomains() != null)
                                .flatMap(route -> route.getDomains().stream())
                                .map(HttpRoute.Domain::getDomain)
                                .collect(Collectors.toSet());
                log.info("Using domains from Agent API spec (all routes): {}", domainSet);
            }
        }

        // Validate that at least one domain is specified
        if (domainSet.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "No domains specified in deploymentConfig or API spec");
        }

        return operator.getDomainIds(gateway, domainSet);
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
