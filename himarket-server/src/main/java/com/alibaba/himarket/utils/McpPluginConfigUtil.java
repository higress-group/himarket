package com.alibaba.himarket.utils;

import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.support.api.v2.spec.MCPServerSpec;
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
    public static String convertApiDefinitionToPluginConfig(APIDefinition apiDefinition) {
        // Validate spec type
        if (!(apiDefinition.getSpec() instanceof MCPServerSpec)) {
            throw new IllegalArgumentException(
                    "API definition spec must be MCPServerSpec for MCP Server");
        }

        MCPServerSpec mcpSpec = (MCPServerSpec) apiDefinition.getSpec();

        // Build MCP Server plugin configuration according to Higress documentation
        Map<String, Object> pluginConfig = new LinkedHashMap<>();

        // Server configuration
        Map<String, Object> server = new LinkedHashMap<>();
        if (mcpSpec.getServer() != null) {
            server.put("name", mcpSpec.getServer().getName());
        } else {
            server.put("name", apiDefinition.getName());
        }
        // Note: Higress does NOT include "type" field in server config
        pluginConfig.put("server", server);

        // Tools configuration - convert spec tools to MCP tools
        List<Map<String, Object>> tools = new ArrayList<>();
        if (mcpSpec.getTools() != null) {
            for (MCPServerSpec.Tool tool : mcpSpec.getTools()) {
                Map<String, Object> toolMap = convertToolToMap(tool);
                if (toolMap != null) {
                    tools.add(toolMap);
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
     * Convert a tool from MCPServerSpec to an MCP tool configuration map
     *
     * @param tool The tool from MCPServerSpec
     * @return MCP tool configuration map
     */
    private static Map<String, Object> convertToolToMap(MCPServerSpec.Tool tool) {
        if (tool == null) {
            return null;
        }

        Map<String, Object> toolMap = new LinkedHashMap<>();

        // Basic tool information
        if (tool.getName() != null) {
            toolMap.put("name", tool.getName());
        }
        if (tool.getDescription() != null) {
            toolMap.put("description", tool.getDescription());
        }

        // Args
        if (tool.getArgs() != null && !tool.getArgs().isEmpty()) {
            List<Map<String, Object>> args = new ArrayList<>();
            for (MCPServerSpec.Arg arg : tool.getArgs()) {
                Map<String, Object> argMap = new LinkedHashMap<>();
                if (arg.getName() != null) {
                    argMap.put("name", arg.getName());
                }
                if (arg.getDescription() != null) {
                    argMap.put("description", arg.getDescription());
                }
                if (arg.getType() != null) {
                    argMap.put("type", arg.getType());
                }
                if (arg.isRequired()) {
                    argMap.put("required", true);
                }
                if (arg.getDefaultValue() != null) {
                    argMap.put("default", arg.getDefaultValue());
                }
                if (arg.getEnumValues() != null) {
                    argMap.put("enum", arg.getEnumValues());
                }
                if (arg.getPosition() != null) {
                    argMap.put("position", arg.getPosition());
                }
                if (arg.getItems() != null) {
                    argMap.put("items", arg.getItems());
                }
                if (arg.getProperties() != null) {
                    argMap.put("properties", arg.getProperties());
                }
                args.add(argMap);
            }
            toolMap.put("args", args);
        }

        // Request template
        if (tool.getRequestTemplate() != null) {
            Map<String, Object> requestTemplate = new LinkedHashMap<>();
            MCPServerSpec.RequestTemplate reqTemplate = tool.getRequestTemplate();

            if (reqTemplate.getUrl() != null) {
                requestTemplate.put("url", reqTemplate.getUrl());
            }
            if (reqTemplate.getMethod() != null) {
                requestTemplate.put("method", reqTemplate.getMethod());
            }

            // Headers
            List<Map<String, String>> headers = new ArrayList<>();
            if (reqTemplate.getHeaders() != null && !reqTemplate.getHeaders().isEmpty()) {
                for (MCPServerSpec.Header header : reqTemplate.getHeaders()) {
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

            toolMap.put("requestTemplate", requestTemplate);
        }

        // Response template
        Map<String, Object> responseTemplate = new LinkedHashMap<>();
        if (tool.getResponseTemplate() != null) {
            MCPServerSpec.ResponseTemplate respTemplate = tool.getResponseTemplate();
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
        if (!responseTemplate.containsKey("prependBody") && tool.getOutputSchema() != null) {
            String prependBody = generateResponseDocumentation(tool.getOutputSchema());
            responseTemplate.put("prependBody", prependBody);
        }

        if (!responseTemplate.isEmpty()) {
            toolMap.put("responseTemplate", responseTemplate);
        }

        // Output schema
        if (tool.getOutputSchema() != null) {
            toolMap.put("outputSchema", tool.getOutputSchema());
        }

        return toolMap;
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
}
