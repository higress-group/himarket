package com.alibaba.himarket.service.publisher;

import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.dto.result.api.APIEndpointVO;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.mcp.McpServerInfo;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.api.GatewayPublisher;
import com.alibaba.himarket.service.gateway.AIGWOperator;
import com.alibaba.himarket.support.api.GatewayServiceConfig;
import com.alibaba.himarket.support.api.MCPToolConfig;
import com.alibaba.himarket.support.api.PublishConfig;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.GatewayType;
import com.aliyun.sdk.service.apig20240327.models.CreateMcpServerRequest;
import com.aliyun.sdk.service.apig20240327.models.CreateMcpServerRequest.BackendConfig;
import com.aliyun.sdk.service.apig20240327.models.CreateMcpServerRequest.Services;
import com.aliyun.sdk.service.apig20240327.models.HttpRouteMatch;
import com.aliyun.sdk.service.apig20240327.models.HttpRouteMatch.HttpRouteMatchPath;
import com.aliyun.sdk.service.apig20240327.models.UpdateMcpServerRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Slf4j
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
        Set<String> publishedDomainSet =
                publishConfig.getDomains().stream()
                        .map(d -> d.getDomain())
                        .collect(Collectors.toSet());
        List<String> domainIds =
                gatewayDomains.stream()
                        .filter(d -> publishedDomainSet.contains(d.getDomain()))
                        .map(
                                (d) -> {
                                    return d.getMeta().get("domainId");
                                })
                        .toList();

        // Fetch gateway environment ID
        String environmentId = operator.getGatewayEnvironmentId(gateway);

        // Note: CreateMcpServerRequest.Builder does not have an environmentId() method
        // The environmentId might be automatically assigned by the gateway based on
        // gatewayId
        // or it might need to be set through a different mechanism

        // Build the backend config (may be null for non-gateway services)
        BackendConfig backendConfig = getMcpBackendConfig(publishConfig);

        CreateMcpServerRequest.Builder requestBuilder =
                CreateMcpServerRequest.builder()
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
            if (apiDefinition.getDescription() != null
                    && !apiDefinition.getDescription().isEmpty()) {
                updateRequestBuilder.description(apiDefinition.getDescription());
            }

            // Only set backendConfig if it's not null
            if (backendConfig != null) {
                updateRequestBuilder.backendConfig(
                        UpdateMcpServerRequest.BackendConfig.builder()
                                .scene(backendConfig.getScene())
                                .services(
                                        backendConfig.getServices() != null
                                                ? backendConfig.getServices().stream()
                                                        .map(
                                                                s ->
                                                                        UpdateMcpServerRequest
                                                                                .Services.builder()
                                                                                .serviceId(
                                                                                        s
                                                                                                .getServiceId())
                                                                                .port(s.getPort())
                                                                                .version(
                                                                                        s
                                                                                                .getVersion())
                                                                                .weight(
                                                                                        s
                                                                                                .getWeight())
                                                                                .build())
                                                        .collect(Collectors.toList())
                                                : null)
                                .build());
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
        String pluginConfigBase64 = convertApiDefinitionToPluginConfig(apiDefinition);

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

        return mcpServerId;
    }

    /**
     * Convert API definition to plugin config (YAML format) and Base64 encode it
     *
     * @param apiDefinition The API definition to convert
     * @return Base64 encoded YAML string
     */
    private String convertApiDefinitionToPluginConfig(APIDefinitionVO apiDefinition) {
        // Build MCP Server plugin configuration according to Higress documentation
        Map<String, Object> pluginConfig = new LinkedHashMap<>();

        // Server configuration
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("name", apiDefinition.getName());
        // Note: Higress does NOT include "type" field in server config
        pluginConfig.put("server", server);

        // Tools configuration - convert endpoints to MCP tools
        List<Map<String, Object>> tools = new ArrayList<>();
        if (apiDefinition.getEndpoints() != null) {
            for (APIEndpointVO endpoint : apiDefinition.getEndpoints()) {
                Map<String, Object> tool = convertEndpointToTool(endpoint);
                if (tool != null) {
                    tools.add(tool);
                }
            }
        }
        pluginConfig.put("tools", tools);

        // Convert to YAML string
        Yaml yaml = new Yaml();
        String yamlString = yaml.dump(pluginConfig);

        // Base64 encode
        return Base64.getEncoder().encodeToString(yamlString.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Convert an API endpoint to an MCP tool configuration
     *
     * @param endpoint The API endpoint to convert
     * @return MCP tool configuration map
     */
    private Map<String, Object> convertEndpointToTool(APIEndpointVO endpoint) {
        if (!(endpoint.getConfig() instanceof MCPToolConfig)) {
            return null;
        }

        MCPToolConfig mcpConfig = (MCPToolConfig) endpoint.getConfig();
        Map<String, Object> tool = new LinkedHashMap<>();

        // Basic tool information
        tool.put("name", endpoint.getName());
        tool.put("description", endpoint.getDescription());

        // Convert input schema to args
        if (mcpConfig.getInputSchema() != null) {
            List<Map<String, Object>> args = convertSchemaToArgs(mcpConfig.getInputSchema());
            tool.put("args", args);
        }

        // Request template
        if (mcpConfig.getRequestTemplate() != null) {
            Map<String, Object> requestTemplate = new LinkedHashMap<>();
            MCPToolConfig.RequestTemplate reqTemplate = mcpConfig.getRequestTemplate();

            if (reqTemplate.getUrl() != null) {
                requestTemplate.put("url", reqTemplate.getUrl());
            }
            if (reqTemplate.getMethod() != null) {
                requestTemplate.put("method", reqTemplate.getMethod());
            }

            // Auto-add Content-Type header for POST/PUT/PATCH with body
            List<Map<String, String>> headers = new ArrayList<>();
            if (reqTemplate.getHeaders() != null && !reqTemplate.getHeaders().isEmpty()) {
                for (MCPToolConfig.Header header : reqTemplate.getHeaders()) {
                    Map<String, String> headerMap = new LinkedHashMap<>();
                    headerMap.put("key", header.getKey());
                    headerMap.put("value", header.getValue());
                    headers.add(headerMap);
                }
            }
            // Auto-add Content-Type for POST/PUT/PATCH if not already present
            String method = reqTemplate.getMethod();
            if (method != null
                    && ("POST".equalsIgnoreCase(method)
                            || "PUT".equalsIgnoreCase(method)
                            || "PATCH".equalsIgnoreCase(method))) {
                // Check if Content-Type already exists
                boolean hasContentType =
                        headers.stream()
                                .anyMatch(h -> "Content-Type".equalsIgnoreCase(h.get("key")));
                if (!hasContentType) {
                    Map<String, String> contentTypeHeader = new LinkedHashMap<>();
                    contentTypeHeader.put("key", "Content-Type");
                    contentTypeHeader.put("value", "application/json");
                    headers.add(contentTypeHeader);
                }
            }

            if (!headers.isEmpty()) {
                requestTemplate.put("headers", headers);
            }

            if (reqTemplate.getBody() != null) {
                requestTemplate.put("body", reqTemplate.getBody());
            }
            if (reqTemplate.getQueryParams() != null && !reqTemplate.getQueryParams().isEmpty()) {
                requestTemplate.put("queryParams", reqTemplate.getQueryParams());
            }

            tool.put("requestTemplate", requestTemplate);
        }

        // Response template - use existing or generate default
        Map<String, Object> responseTemplate = new LinkedHashMap<>();
        if (mcpConfig.getResponseTemplate() != null) {
            MCPToolConfig.ResponseTemplate respTemplate = mcpConfig.getResponseTemplate();
            if (respTemplate.getPrependBody() != null) {
                responseTemplate.put("prependBody", respTemplate.getPrependBody());
            }
            if (respTemplate.getAppendBody() != null) {
                responseTemplate.put("appendBody", respTemplate.getAppendBody());
            }
            if (respTemplate.getBody() != null) {
                responseTemplate.put("body", respTemplate.getBody());
            }
        }

        // Auto-generate prependBody if not provided and we have output schema
        if (!responseTemplate.containsKey("prependBody") && mcpConfig.getOutputSchema() != null) {
            String prependBody = generateResponseDocumentation(mcpConfig.getOutputSchema());
            responseTemplate.put("prependBody", prependBody);
        }

        if (!responseTemplate.isEmpty()) {
            tool.put("responseTemplate", responseTemplate);
        }

        // Output schema
        if (mcpConfig.getOutputSchema() != null) {
            tool.put("outputSchema", mcpConfig.getOutputSchema());
        }

        return tool;
    }

    /**
     * Generate markdown documentation for response schema
     *
     * @param outputSchema The output schema
     * @return Markdown formatted documentation
     */
    private String generateResponseDocumentation(Map<String, Object> outputSchema) {
        StringBuilder doc = new StringBuilder();
        doc.append("# API Response Information\n\n");
        doc.append(
                "Below is the response from an API call. To help you understand the data, I've"
                        + " provided:\n\n");
        doc.append("1. A detailed description of all fields in the response structure\n");
        doc.append("2. The complete API response\n\n");
        doc.append("## Response Structure\n\n");
        doc.append("> Content-Type: application/json\n\n");

        // Extract description if present
        if (outputSchema.containsKey("description")) {
            doc.append("Description: ").append(outputSchema.get("description")).append("\n\n");
        }

        // List properties
        if (outputSchema.containsKey("properties")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) outputSchema.get("properties");
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String fieldName = entry.getKey();
                if (entry.getValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fieldSchema = (Map<String, Object>) entry.getValue();
                    String type =
                            fieldSchema.get("type") != null
                                    ? fieldSchema.get("type").toString()
                                    : "unknown";
                    String description =
                            fieldSchema.get("description") != null
                                    ? fieldSchema.get("description").toString()
                                    : "";
                    doc.append("- **").append(fieldName).append("**: ");
                    if (!description.isEmpty()) {
                        doc.append(description).append(" ");
                    }
                    doc.append("(Type: ").append(type).append(")\n");
                }
            }
        }

        doc.append("\n## Original Response\n");
        return doc.toString();
    }

    /**
     * Convert JSON Schema to MCP args format
     *
     * @param inputSchema JSON Schema of input parameters
     * @return List of MCP args
     */
    private List<Map<String, Object>> convertSchemaToArgs(Map<String, Object> inputSchema) {
        List<Map<String, Object>> args = new ArrayList<>();

        if (inputSchema == null) {
            return args;
        }

        // Extract properties from JSON Schema
        Object propertiesObj = inputSchema.get("properties");
        if (!(propertiesObj instanceof Map)) {
            return args;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) propertiesObj;

        // Extract required fields
        Object requiredObj = inputSchema.get("required");
        List<String> requiredFields = new ArrayList<>();
        if (requiredObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> requiredList = (List<Object>) requiredObj;
            for (Object item : requiredList) {
                if (item instanceof String) {
                    requiredFields.add((String) item);
                }
            }
        }

        // Convert each property to an arg
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String paramName = entry.getKey();
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> propertySchema = (Map<String, Object>) entry.getValue();

            Map<String, Object> arg = new LinkedHashMap<>();
            arg.put("name", paramName);

            if (propertySchema.get("description") != null) {
                arg.put("description", propertySchema.get("description"));
            }
            if (propertySchema.get("type") != null) {
                arg.put("type", propertySchema.get("type"));
            }
            if (requiredFields.contains(paramName)) {
                arg.put("required", true);
            }
            if (propertySchema.get("default") != null) {
                arg.put("default", propertySchema.get("default"));
            }
            if (propertySchema.get("enum") != null) {
                arg.put("enum", propertySchema.get("enum"));
            }
            if (propertySchema.get("items") != null) {
                arg.put("items", propertySchema.get("items"));
            }
            if (propertySchema.get("properties") != null) {
                arg.put("properties", propertySchema.get("properties"));
            }
            // Add position metadata (query/header/cookie/body/path)
            if (propertySchema.get("x-position") != null) {
                arg.put("position", propertySchema.get("x-position"));
            }

            args.add(arg);
        }

        return args;
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
        HttpRouteMatchPath path =
                HttpRouteMatchPath.builder().type("Prefix").value(pathValue).build();

        // Create match configuration
        return HttpRouteMatch.builder().path(path).build();
    }

    private BackendConfig getMcpBackendConfig(PublishConfig publishConfig) {
        if (publishConfig == null || publishConfig.getServiceConfig() == null) {
            return null;
        }

        // Check if the service type is GATEWAY
        if (publishConfig.getServiceConfig() instanceof GatewayServiceConfig) {
            GatewayServiceConfig gatewayServiceConfig =
                    (GatewayServiceConfig) publishConfig.getServiceConfig();

            // Build the backend service configuration
            Services service =
                    Services.builder().serviceId(gatewayServiceConfig.getServiceId()).build();

            // Build the BackendConfig with scene "SingleService" and the service list
            return BackendConfig.builder()
                    .scene("SingleService")
                    .services(Collections.singletonList(service))
                    .build();
        }

        return null;
    }

    private String getProtocol(APIDefinitionVO apiDefinition) {
        return null;
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
