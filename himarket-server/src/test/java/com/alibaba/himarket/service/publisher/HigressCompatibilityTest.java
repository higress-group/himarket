package com.alibaba.himarket.service.publisher;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.dto.result.api.APIEndpointVO;
import com.alibaba.himarket.entity.APIEndpoint;
import com.alibaba.himarket.service.api.SwaggerConverter;
import com.alibaba.himarket.support.api.MCPToolConfig;
import com.alibaba.himarket.support.enums.APIType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Test compatibility with Higress openapi-to-mcpserver test cases
 * Reference: https://github.com/higress-group/openapi-to-mcpserver/tree/main/test
 */
public class HigressCompatibilityTest {

    private SwaggerConverter swaggerConverter;
    private ApigAiGatewayPublisher publisher;
    private ObjectMapper objectMapper;

    @Test
    public void testCookieParametersConversion() throws Exception {
        // Initialize
        objectMapper = new ObjectMapper();
        swaggerConverter = new SwaggerConverter(objectMapper);
        publisher = new ApigAiGatewayPublisher(null);

        System.out.println("=== Testing Cookie Parameters Conversion ===\n");

        // Cookie parameters test case from Higress
        String cookieParamsSwagger =
                """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "version": "1.0.0",
                    "title": "Cookie Parameters API"
                  },
                  "servers": [{"url": "http://api.example.com/v1"}],
                  "paths": {
                    "/session": {
                      "get": {
                        "summary": "Get session information",
                        "operationId": "getSession",
                        "parameters": [
                          {
                            "name": "sessionId",
                            "in": "cookie",
                            "required": true,
                            "description": "Session identifier cookie",
                            "schema": {"type": "string"}
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "Session information",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "userId": {"type": "string", "description": "User ID"}
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;

        // Convert
        List<APIEndpoint> endpoints =
                swaggerConverter.convertEndpoints(cookieParamsSwagger, APIType.MCP_SERVER);
        assertEquals(1, endpoints.size(), "Should have 1 endpoint");

        APIEndpoint endpoint = endpoints.get(0);
        assertEquals("getSession", endpoint.getName());

        // Check MCPToolConfig
        MCPToolConfig config = objectMapper.readValue(endpoint.getConfig(), MCPToolConfig.class);
        assertNotNull(config.getInputSchema(), "Should have input schema");

        // Build APIDefinitionVO and convert to plugin config
        APIDefinitionVO apiDefinition = buildApiDefinition("cookie-params-api", endpoints);
        String yamlBase64 = invokeConvertApiDefinitionToPluginConfig(publisher, apiDefinition);
        String yaml = new String(Base64.getDecoder().decode(yamlBase64), StandardCharsets.UTF_8);

        System.out.println("Generated YAML:");
        System.out.println(yaml);
        System.out.println();

        // Parse and validate YAML structure
        Yaml yamlParser = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> config_map = yamlParser.load(yaml);

        assertNotNull(config_map.get("server"), "Should have server config");
        assertNotNull(config_map.get("tools"), "Should have tools");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) config_map.get("tools");
        assertEquals(1, tools.size(), "Should have 1 tool");

        Map<String, Object> tool = tools.get(0);
        assertEquals("getSession", tool.get("name"));

        // Check args
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> args = (List<Map<String, Object>>) tool.get("args");
        assertNotNull(args, "Should have args");

        boolean foundCookieParam = false;
        for (Map<String, Object> arg : args) {
            if ("sessionId".equals(arg.get("name"))) {
                foundCookieParam = true;
                System.out.println("✓ Found sessionId parameter");

                // Check if position is included (expected by Higress)
                if (arg.containsKey("position")) {
                    System.out.println("✓ Has position field: " + arg.get("position"));
                } else {
                    System.out.println("✗ MISSING position field (expected: 'cookie')");
                }
            }
        }
        assertTrue(foundCookieParam, "Should have sessionId cookie parameter");

        // Check outputSchema
        if (tool.containsKey("outputSchema")) {
            System.out.println("✓ Has outputSchema");
        } else {
            System.out.println("✗ MISSING outputSchema (response schema)");
        }

        // Validate against expected structure from Higress test
        assertTrue(yaml.contains("name: cookie-params-api"), "Server name should match");
        assertTrue(yaml.contains("name: getSession"), "Should have getSession tool");
        assertTrue(yaml.contains("position: cookie"), "Should have cookie position");
        // Note: This test only has 1 endpoint (getSession), not the full Higress test with
        // getPreferences

        System.out.println("\n=== Deep YAML Object Comparison ===");
        // Since our test only has 1 endpoint, we can't compare with full Higress expected
        // file
        // But we can validate the structure is correct
        System.out.println("✅ Cookie parameter YAML structure validated");

        System.out.println("\n=== Cookie Parameters Test Complete ===\n");
    }

    @Test
    public void testAllOfParametersConversion() throws Exception {
        // Initialize
        objectMapper = new ObjectMapper();
        swaggerConverter = new SwaggerConverter(objectMapper);
        publisher = new ApigAiGatewayPublisher(null);

        System.out.println("=== Testing AllOf Parameters Conversion ===\n");

        // AllOf test case from Higress
        String allOfSwagger =
                """
                {
                    "openapi": "3.0.3",
                    "info": {"title": "User API", "version": "0.0.1"},
                    "paths": {
                        "/user/info": {
                            "post": {
                                "operationId": "User_Search",
                                "description": "搜索用户",
                                "requestBody": {
                                    "content": {
                                        "application/json": {
                                            "schema": {"$ref": "#/components/schemas/user.Req"}
                                        }
                                    },
                                    "required": true
                                },
                                "responses": {
                                    "200": {
                                        "description": "OK",
                                        "content": {
                                            "application/json": {
                                                "schema": {"$ref": "#/components/schemas/user.Rsp"}
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    "components": {
                        "schemas": {
                            "user.Req": {
                                "type": "object",
                                "properties": {
                                    "search": {
                                        "allOf": [{"$ref": "#/components/schemas/user.Req_Search"}],
                                        "description": "搜索项"
                                    },
                                    "page": {"type": "integer"},
                                    "size": {"type": "integer"}
                                }
                            },
                            "user.Req_Search": {
                                "type": "object",
                                "properties": {
                                    "keyword": {"type": "string"},
                                    "created_at": {
                                        "allOf": [{"$ref": "#/components/schemas/user.Req_Range"}],
                                        "description": "创建时间范围"
                                    }
                                }
                            },
                            "user.Req_Range": {
                                "type": "object",
                                "properties": {
                                    "start": {"type": "string"},
                                    "end": {"type": "string"}
                                }
                            },
                            "user.Rsp": {
                                "type": "object",
                                "properties": {
                                    "id": {"type": "string"},
                                    "name": {"type": "string"}
                                }
                            }
                        }
                    }
                }
                """;

        // Convert
        List<APIEndpoint> endpoints =
                swaggerConverter.convertEndpoints(allOfSwagger, APIType.MCP_SERVER);
        assertEquals(1, endpoints.size(), "Should have 1 endpoint");

        APIEndpoint endpoint = endpoints.get(0);
        MCPToolConfig config = objectMapper.readValue(endpoint.getConfig(), MCPToolConfig.class);

        System.out.println(
                "Input Schema: "
                        + objectMapper
                                .writerWithDefaultPrettyPrinter()
                                .writeValueAsString(config.getInputSchema()));

        // Check if search parameter is properly resolved
        @SuppressWarnings("unchecked")
        Map<String, Object> properties =
                (Map<String, Object>) config.getInputSchema().get("properties");
        assertNotNull(properties, "Should have properties");

        if (properties.containsKey("search")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> search = (Map<String, Object>) properties.get("search");
            System.out.println("✓ Found 'search' parameter");
            System.out.println("  search type: " + search.get("type"));
            System.out.println("  search properties: " + search.get("properties"));

            if ("object".equals(search.get("type")) && search.containsKey("properties")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> searchProps = (Map<String, Object>) search.get("properties");
                if (searchProps.containsKey("created_at")) {
                    System.out.println("✓ AllOf properly resolved - found nested created_at");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> createdAt =
                            (Map<String, Object>) searchProps.get("created_at");
                    if ("object".equals(createdAt.get("type"))
                            && createdAt.containsKey("properties")) {
                        System.out.println(
                                "✓ Deep nested allOf resolved - found start/end properties");
                    }
                }
            }
        }

        // Build and convert to plugin config
        APIDefinitionVO apiDefinition = buildApiDefinition("user-api", endpoints);
        String yamlBase64 = invokeConvertApiDefinitionToPluginConfig(publisher, apiDefinition);
        String yaml = new String(Base64.getDecoder().decode(yamlBase64), StandardCharsets.UTF_8);

        System.out.println("\nGenerated YAML:");
        System.out.println(yaml);

        System.out.println("\n=== AllOf Parameters Test Complete ===\n");
    }

    @Test
    public void testResponseTemplateGeneration() throws Exception {
        // Initialize
        objectMapper = new ObjectMapper();
        swaggerConverter = new SwaggerConverter(objectMapper);
        publisher = new ApigAiGatewayPublisher(null);

        System.out.println("=== Testing Response Template Generation ===\n");

        String simpleSwagger =
                """
                {
                  "openapi": "3.0.0",
                  "info": {"version": "1.0.0", "title": "Test API"},
                  "paths": {
                    "/test": {
                      "get": {
                        "operationId": "testOp",
                        "responses": {
                          "200": {
                            "description": "Success",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "message": {"type": "string", "description": "Response message"}
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;

        List<APIEndpoint> endpoints =
                swaggerConverter.convertEndpoints(simpleSwagger, APIType.MCP_SERVER);
        APIDefinitionVO apiDefinition = buildApiDefinition("test-api", endpoints);
        String yamlBase64 = invokeConvertApiDefinitionToPluginConfig(publisher, apiDefinition);
        String yaml = new String(Base64.getDecoder().decode(yamlBase64), StandardCharsets.UTF_8);

        System.out.println("Generated YAML:");
        System.out.println(yaml);

        // Check if responseTemplate with prependBody is present
        if (yaml.contains("responseTemplate")) {
            System.out.println("✓ Has responseTemplate");
            if (yaml.contains("prependBody")) {
                System.out.println("✓ Has prependBody with documentation");
            } else {
                System.out.println("✗ MISSING prependBody (expected markdown documentation)");
            }
        } else {
            System.out.println("✗ MISSING responseTemplate");
        }

        System.out.println("\n=== Response Template Test Complete ===\n");
    }

    private APIDefinitionVO buildApiDefinition(String name, List<APIEndpoint> endpoints)
            throws Exception {
        APIDefinitionVO apiDefinition = new APIDefinitionVO();
        apiDefinition.setName(name);
        apiDefinition.setType(APIType.MCP_SERVER);

        List<APIEndpointVO> endpointVOs =
                endpoints.stream()
                        .map(
                                e -> {
                                    APIEndpointVO vo = new APIEndpointVO();
                                    vo.setEndpointId(e.getEndpointId());
                                    vo.setName(e.getName());
                                    vo.setDescription(e.getDescription());
                                    vo.setType(e.getType());
                                    if (e.getConfig() != null) {
                                        try {
                                            MCPToolConfig config =
                                                    objectMapper.readValue(
                                                            e.getConfig(), MCPToolConfig.class);
                                            vo.setConfig(config);
                                        } catch (Exception ex) {
                                            throw new RuntimeException(ex);
                                        }
                                    }
                                    return vo;
                                })
                        .toList();

        apiDefinition.setEndpoints(endpointVOs);
        return apiDefinition;
    }

    private String invokeConvertApiDefinitionToPluginConfig(
            ApigAiGatewayPublisher publisher, APIDefinitionVO apiDefinition) throws Exception {
        java.lang.reflect.Method method =
                ApigAiGatewayPublisher.class.getDeclaredMethod(
                        "convertApiDefinitionToPluginConfig", APIDefinitionVO.class);
        method.setAccessible(true);
        return (String) method.invoke(publisher, apiDefinition);
    }
}
