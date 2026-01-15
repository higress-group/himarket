package com.alibaba.himarket.core.utils;

import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.dto.result.api.APIEndpointVO;
import com.alibaba.himarket.support.api.MCPToolConfig;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Utility class for converting MCP API definitions to plugin configurations.
 */
public class McpPluginConfigUtil {

    private McpPluginConfigUtil() {
        // Utility class, prevent instantiation
    }

    /**
     * Convert API definition to plugin config (YAML format) and Base64 encode it
     *
     * @param apiDefinition The API definition to convert
     * @return Base64 encoded YAML string
     */
    public static String convertApiDefinitionToPluginConfig(APIDefinitionVO apiDefinition) {
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
    private static Map<String, Object> convertEndpointToTool(APIEndpointVO endpoint) {
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
                boolean hasContentType = headers.stream()
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
    private static String generateResponseDocumentation(Map<String, Object> outputSchema) {
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
                    String type = fieldSchema.get("type") != null
                            ? fieldSchema.get("type").toString()
                            : "unknown";
                    String description = fieldSchema.get("description") != null
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
    private static List<Map<String, Object>> convertSchemaToArgs(Map<String, Object> inputSchema) {
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
}
