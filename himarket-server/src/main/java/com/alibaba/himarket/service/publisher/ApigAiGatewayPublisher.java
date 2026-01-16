package com.alibaba.himarket.service.publisher;

import com.alibaba.himarket.core.utils.McpPluginConfigUtil;
import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.dto.result.api.APIEndpointVO;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.mcp.McpServerInfo;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.gateway.AIGWOperator;
import com.alibaba.himarket.support.api.AiServiceConfig;
import com.alibaba.himarket.support.api.DnsServiceConfig;
import com.alibaba.himarket.support.api.EndpointConfig;
import com.alibaba.himarket.support.api.FixedAddressServiceConfig;
import com.alibaba.himarket.support.api.GatewayServiceConfig;
import com.alibaba.himarket.support.api.MCPToolConfig;
import com.alibaba.himarket.support.api.HttpEndpointConfig;
import com.alibaba.himarket.support.api.PublishConfig;
import com.alibaba.himarket.support.api.ServiceConfig;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.alibaba.himarket.support.product.GatewayRefConfig;
import com.aliyun.sdk.service.apig20240327.models.CreateMcpServerRequest;
import com.aliyun.sdk.service.apig20240327.models.CreateMcpServerRequest.BackendConfig;
import com.aliyun.sdk.service.apig20240327.models.CreateMcpServerRequest.Services;
import com.aliyun.sdk.service.apig20240327.models.HttpApiDeployConfig;
import com.aliyun.sdk.service.apig20240327.models.HttpApiDeployConfig.AiFallbackConfig;
import com.aliyun.sdk.service.apig20240327.models.HttpApiDeployConfig.PolicyConfigs;
import com.aliyun.sdk.service.apig20240327.models.HttpApiDeployConfig.ServiceConfigs;
import com.aliyun.sdk.service.apig20240327.models.HttpRouteMatch;
import com.aliyun.sdk.service.apig20240327.models.HttpRouteMatch.HttpRouteMatchPath;
import com.aliyun.sdk.service.apig20240327.models.UpdateMcpServerRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
            return publishStandardMcpServer(gateway, publishConfig, apiDefinition);
        }
    }

    private APIGRefConfig publishDirectMcpServer(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
        log.info("Publishing MCP server with DIRECT bridge type: {}", apiDefinition.getName());

        // Extract domain IDs
        Set<String> publishedDomainSet = publishConfig.getDomains().stream()
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
            mcpProtocol = publishConfig.getServiceConfig().getMeta().getOrDefault("mcpProtocol", "SSE");
            mcpPath = publishConfig.getServiceConfig().getMeta().get("mcpPath");
        }

        // Build CreateMcpServerRequest for DIRECT type
        // Note: using exposedUriPath for the backend path and createFromType as
        // "ApiGatewayProxyMcpHosting"
        CreateMcpServerRequest.Builder requestBuilder = CreateMcpServerRequest.builder()
                .name(apiDefinition.getName())
                .type("RealMCP")
                .match(getMcpMatch(apiDefinition))
                .gatewayId(gateway.getGatewayId())
                .protocol(mcpProtocol)
                .domainIds(domainIds)
                .exposedUriPath(mcpPath)
                .createFromType("ApiGatewayProxyMcpHosting");

        // Only set description if it's not null/empty
        if (apiDefinition.getDescription() != null && !apiDefinition.getDescription().isEmpty()) {
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
        Optional<String> existingMcpServerId = operator.findMcpServerIdByName(gateway, mcpServerName);

        if (existingMcpServerId.isPresent()) {
            log.info(
                    "MCP server with name '{}' already exists in gateway {}, updating existing"
                            + " server",
                    mcpServerName,
                    gateway.getGatewayId());
            mcpServerId = existingMcpServerId.get();

            // Build update request
            UpdateMcpServerRequest.Builder updateRequestBuilder = UpdateMcpServerRequest.builder()
                    .mcpServerId(mcpServerId)
                    .type("RealMCP")
                    .match(getMcpMatch(apiDefinition))
                    .protocol(mcpProtocol)
                    .domainIds(domainIds)
                    .exposedUriPath(mcpPath)
                    .createFromType("ApiGatewayProxyMcpHosting");

            if (apiDefinition.getDescription() != null
                    && !apiDefinition.getDescription().isEmpty()) {
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
        String pluginConfigBase64 = McpPluginConfigUtil.convertApiDefinitionToPluginConfig(apiDefinition);

        String pluginAttachmentId = data.getMcpServerConfigPluginAttachmentId();
        if (pluginAttachmentId != null && !pluginAttachmentId.isEmpty()) {
            log.info("Plugin attachment already exists with ID: {}, updating it", pluginAttachmentId);
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

    private APIGRefConfig publishStandardMcpServer(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
        Set<String> publishedDomainSet = publishConfig.getDomains().stream()
                .map(d -> d.getDomain())
                .collect(Collectors.toSet());
        List<String> domainIds = operator.getDomainIds(gateway, publishedDomainSet);
        // Note: CreateMcpServerRequest.Builder does not have an environmentId() method
        // The environmentId might be automatically assigned by the gateway based on
        // gatewayId
        // or it might need to be set through a different mechanism

        // Build the backend config (may be null for non-gateway services)
        BackendConfig backendConfig = getMcpBackendConfig(gateway, publishConfig, apiDefinition);

        CreateMcpServerRequest.Builder requestBuilder = CreateMcpServerRequest.builder()
                .name(apiDefinition.getName())
                .type("RealMCP")
                .match(getMcpMatch(apiDefinition))
                .gatewayId(gateway.getGatewayId())
                .protocol("HTTP")
                .domainIds(domainIds);

        // Only set description if it's not null/empty
        if (apiDefinition.getDescription() != null && !apiDefinition.getDescription().isEmpty()) {
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
        Optional<String> existingMcpServerId = operator.findMcpServerIdByName(gateway, mcpServerName);

        if (existingMcpServerId.isPresent()) {
            log.info(
                    "MCP server with name '{}' already exists in gateway {}, updating existing"
                            + " server",
                    mcpServerName,
                    gateway.getGatewayId());
            mcpServerId = existingMcpServerId.get();

            // Build update request with the same parameters as create
            UpdateMcpServerRequest.Builder updateRequestBuilder = UpdateMcpServerRequest.builder()
                    .mcpServerId(mcpServerId)
                    .type("RealMCP")
                    .match(getMcpMatch(apiDefinition))
                    .protocol("HTTP")
                    .domainIds(domainIds);

            // Only set description if it's not null/empty
            if (apiDefinition.getDescription() != null
                    && !apiDefinition.getDescription().isEmpty()) {
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
        String pluginConfigBase64 = McpPluginConfigUtil.convertApiDefinitionToPluginConfig(apiDefinition);

        // Check if plugin attachment already exists
        String pluginAttachmentId = data.getMcpServerConfigPluginAttachmentId();
        if (pluginAttachmentId != null && !pluginAttachmentId.isEmpty()) {
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

        return APIGRefConfig.builder()
                .mcpServerId(mcpServerId)
                .build();
    }

    /**
     * Generate match configuration for MCP server based on server name
     *
     * @param apiDefinition The API definition containing the MCP server name
     * @return Match configuration with path prefix pattern
     */
    private HttpRouteMatch getMcpMatch(APIDefinitionVO apiDefinition) {
        // Build match configuration with path prefix: /mcp-servers/{name}
        String pathValue = "/mcp-servers/" + apiDefinition.getName();

        // Create path match configuration
        HttpRouteMatchPath path = HttpRouteMatchPath.builder().type("Prefix").value(pathValue).build();

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
        Set<String> publishedDomainSet = publishConfig.getDomains().stream()
                .map(d -> d.getDomain())
                .collect(Collectors.toSet());

        List<String> customDomainIds = operator.getDomainIds(gateway, publishedDomainSet);

        // 3. Extract AI protocols - default to OpenAI/v1
        List<String> aiProtocols = extractAiProtocols(publishConfig);

        // 4. Extract model category - default to Text
        String modelCategory = extractModelCategory(publishConfig);

        // 5. Extract resource group ID from gateway
        String resourceGroupId = extractResourceGroupId(gateway);

        // 6. Build service configs for backend using SDK classes
        List<ServiceConfigs> serviceConfigs = buildServiceConfigsSDK(gateway, publishConfig, apiDefinition);

        // 7. Build policy configs for AI capabilities using SDK classes
        List<PolicyConfigs> policyConfigs = buildPolicyConfigsSDK();

        // 8. Extract basePath from publishConfig, default to "/" if not provided
        String basePath = publishConfig.getBasePath();
        if (basePath == null || basePath.isEmpty()) {
            basePath = "/";
            log.warn("No basePath provided in publishConfig, using default: /");
        }
        log.info("Using basePath: {} for Model API", basePath);

        // 9. Build deploy configs using SDK classes
        HttpApiDeployConfig deployConfig = HttpApiDeployConfig.builder()
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
        String httpApiId = createOrUpdateHttpApi(
                gateway,
                apiDefinition.getName(),
                "LLM",
                basePath,
                aiProtocols,
                deployConfig,
                resourceGroupId,
                apiDefinition.getDescription(),
                modelCategory);

        return APIGRefConfig.builder().modelApiId(httpApiId).build();
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
        Set<String> publishedDomainSet = publishConfig.getDomains().stream()
                .map(d -> d.getDomain())
                .collect(Collectors.toSet());

        List<String> customDomainIds = operator.getDomainIds(gateway, publishedDomainSet);

        // 3. Extract agent protocols - default to Dify
        List<String> agentProtocols = extractAgentProtocols(publishConfig);

        // 4. Extract resource group ID from gateway
        String resourceGroupId = extractResourceGroupId(gateway);

        // 5. Build service configs for backend using SDK classes
        List<ServiceConfigs> serviceConfigs = buildServiceConfigsSDK(gateway, publishConfig, apiDefinition);

        // 6. Build policy configs (empty for Agent API by default)
        List<PolicyConfigs> policyConfigs = Collections.emptyList();

        // 7. Extract basePath from publishConfig, default to "/" if not provided
        String basePath = publishConfig.getBasePath();
        if (basePath == null || basePath.isEmpty()) {
            basePath = "/";
            log.warn("No basePath provided in publishConfig, using default: /");
        }
        log.info("Using basePath: {} for Agent API", basePath);

        // 8. Build deploy configs using SDK classes
        HttpApiDeployConfig deployConfig = HttpApiDeployConfig.builder()
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
        String httpApiId = createOrUpdateHttpApi(
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
        if (apiDefinition.getEndpoints() != null && !apiDefinition.getEndpoints().isEmpty()) {
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
        HttpRouteMatchPath pathMatch = HttpRouteMatchPath.builder()
                .type(matchConfig.getPath().getType())
                .value(matchConfig.getPath().getValue())
                .build();

        HttpRouteMatch.Builder matchBuilder = HttpRouteMatch.builder().path(pathMatch);

        // Add methods if specified
        if (matchConfig.getMethods() != null && !matchConfig.getMethods().isEmpty()) {
            matchBuilder.methods(matchConfig.getMethods());
        }

        // Set ignoreUriCase (default to false)
        matchBuilder.ignoreUriCase(false);

        HttpRouteMatch match = matchBuilder.build();

        // Extract serviceId from serviceConfigs
        String serviceId = "";
        if (serviceConfigs != null && !serviceConfigs.isEmpty()) {
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
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
    }

    private void unpublishAgentAPI(
            Gateway gateway, PublishConfig publishConfig, APIDefinitionVO apiDefinition) {
    }

    private void validateMcpServerConfig(
            APIDefinitionVO apiDefinition, PublishConfig publishConfig) {
    }

    private void validateModelAPIConfig(
            APIDefinitionVO apiDefinition, PublishConfig publishConfig) {
    }

    private void validateAgentAPIConfig(
            APIDefinitionVO apiDefinition, PublishConfig publishConfig) {
    }

    /**
     * Build policy configurations for Model API using SDK classes
     *
     * @return List of SDK PolicyConfigs
     */
    private List<PolicyConfigs> buildPolicyConfigsSDK() {
        List<PolicyConfigs> configs = new ArrayList<>();

        // AI Fallback policy (disabled by default)
        PolicyConfigs aiFallbackPolicy = PolicyConfigs.builder()
                .type("AiFallback")
                .enable(false)
                .aiFallbackConfig(
                        AiFallbackConfig.builder()
                                .serviceConfigs(Collections.emptyList())
                                .build())
                .build();
        configs.add(aiFallbackPolicy);

        // AI Statistics policy (enabled by default)
        // Note: AiStatisticsConfig is not part of the SDK PolicyConfigs nested class
        // So we only set type and enable
        PolicyConfigs aiStatisticsPolicy = PolicyConfigs.builder().type("AiStatistics").enable(true).build();
        configs.add(aiStatisticsPolicy);

        // Semantic Router policy (disabled by default)
        // Note: SemanticRouterConfig is not part of the SDK PolicyConfigs nested class
        // So we only set type and enable
        PolicyConfigs semanticRouterPolicy = PolicyConfigs.builder().type("SemanticRouter").enable(false)
                .build();
        configs.add(semanticRouterPolicy);

        return configs;
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

        if (serviceConfig instanceof GatewayServiceConfig) {
            // Gateway Service configuration - use existing serviceId if available
            GatewayServiceConfig gatewayServiceConfig = (GatewayServiceConfig) serviceConfig;
            String serviceId = gatewayServiceConfig.getServiceId();

            // If serviceId is not provided, ensure service exists
            if (serviceId == null || serviceId.isEmpty()) {
                serviceId = ensureServiceExists(gateway, apiDefinition.getName(), serviceConfig);
            }

            ServiceConfigs sdkServiceConfig = ServiceConfigs.builder().serviceId(serviceId).build();
            serviceConfigs.add(sdkServiceConfig);

            log.info("Using Gateway Service for Model API: serviceId={}", serviceId);

        } else if (serviceConfig instanceof FixedAddressServiceConfig
                || serviceConfig instanceof DnsServiceConfig) {
            // Fixed address or DNS service - ensure service exists
            String serviceId = ensureServiceExists(gateway, apiDefinition.getName(), serviceConfig);
            ServiceConfigs sdkServiceConfig = ServiceConfigs.builder().serviceId(serviceId).build();
            serviceConfigs.add(sdkServiceConfig);

            log.info(
                    "Using {} Service for Model API: serviceId={}",
                    serviceConfig.getClass().getSimpleName(),
                    serviceId);

        } else if (serviceConfig instanceof AiServiceConfig) {
            // AI Service configuration - ensure service exists
            AiServiceConfig aiServiceConfig = (AiServiceConfig) serviceConfig;

            log.info(
                    "Using AI Service for Model API: provider={}, protocol={}",
                    aiServiceConfig.getProvider(),
                    aiServiceConfig.getProtocol());

            // Ensure the AI service exists in APIG
            String serviceId = ensureServiceExists(gateway, apiDefinition.getName(), serviceConfig);
            ServiceConfigs sdkServiceConfig = ServiceConfigs.builder().serviceId(serviceId).build();
            serviceConfigs.add(sdkServiceConfig);

            log.info("AI Service registered in APIG: serviceId={}", serviceId);

        } else {
            log.warn(
                    "Unsupported service config type for Model API: {}",
                    serviceConfig.getClass().getSimpleName());
        }

        return serviceConfigs;
    }

    /**
     * Extract model category from publish config
     *
     * @param publishConfig The publish configuration
     * @return Model category (e.g., "Text", "Image", "Audio", "Video")
     */
    private String extractModelCategory(PublishConfig publishConfig) {
        // Extract from publishConfig metadata or API definition
        // For now, default to "Text" for LLM models
        // This can be enhanced to read from API definition metadata
        return "Text";
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
            services = backendConfig.getServices().stream()
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
