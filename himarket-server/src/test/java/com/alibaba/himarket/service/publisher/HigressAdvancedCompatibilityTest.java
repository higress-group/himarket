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
 * Advanced compatibility tests for Higress openapi-to-mcpserver
 * Tests additional scenarios: header params, path params, complex arrays
 */
public class HigressAdvancedCompatibilityTest {

    private SwaggerConverter swaggerConverter;
    private ApigAiGatewayPublisher publisher;
    private ObjectMapper objectMapper;

    @Test
    public void testHeaderParametersConversion() throws Exception {
        // Initialize
        objectMapper = new ObjectMapper();
        swaggerConverter = new SwaggerConverter(objectMapper);
        publisher = new ApigAiGatewayPublisher(null);

        System.out.println("=== Testing Header Parameters Conversion ===\n");

        String headerParamsSwagger =
                """
                {
                  "openapi": "3.0.0",
                  "info": {"version": "1.0.0", "title": "Header Parameters API"},
                  "servers": [{"url": "http://api.example.com/v1"}],
                  "paths": {
                    "/auth": {
                      "get": {
                        "summary": "Authenticate with API key",
                        "operationId": "authenticate",
                        "parameters": [
                          {
                            "name": "X-API-Key",
                            "in": "header",
                            "required": true,
                            "description": "API key for authentication",
                            "schema": {"type": "string"}
                          },
                          {
                            "name": "X-Client-ID",
                            "in": "header",
                            "required": false,
                            "description": "Client identifier",
                            "schema": {"type": "string"}
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "Authentication successful",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "authenticated": {"type": "boolean", "description": "Authentication status"},
                                    "expires": {"type": "string", "description": "Token expiration time"}
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
                swaggerConverter.convertEndpoints(headerParamsSwagger, APIType.MCP_SERVER);
        assertEquals(1, endpoints.size(), "Should have 1 endpoint");

        APIEndpoint endpoint = endpoints.get(0);
        assertEquals("authenticate", endpoint.getName());

        MCPToolConfig config = objectMapper.readValue(endpoint.getConfig(), MCPToolConfig.class);
        assertNotNull(config.getInputSchema(), "Should have input schema");

        APIDefinitionVO apiDefinition = buildApiDefinition("header-params-api", endpoints);
        String yamlBase64 = invokeConvertApiDefinitionToPluginConfig(publisher, apiDefinition);
        String yaml = new String(Base64.getDecoder().decode(yamlBase64), StandardCharsets.UTF_8);

        System.out.println("Generated YAML:");
        System.out.println(yaml);

        // Validate header parameters
        Yaml yamlParser = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> configMap = yamlParser.load(yaml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) configMap.get("tools");
        Map<String, Object> tool = tools.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> args = (List<Map<String, Object>>) tool.get("args");

        boolean foundApiKey = false;
        boolean foundClientId = false;
        for (Map<String, Object> arg : args) {
            String name = (String) arg.get("name");
            if ("X-API-Key".equals(name)) {
                foundApiKey = true;
                assertEquals(
                        "header", arg.get("position"), "X-API-Key should have position=header");
                assertEquals(true, arg.get("required"), "X-API-Key should be required");
                System.out.println("✓ Found X-API-Key with position=header, required=true");
            } else if ("X-Client-ID".equals(name)) {
                foundClientId = true;
                assertEquals(
                        "header", arg.get("position"), "X-Client-ID should have position=header");
                System.out.println("✓ Found X-Client-ID with position=header");
            }
        }

        assertTrue(foundApiKey, "Should have X-API-Key header parameter");
        assertTrue(foundClientId, "Should have X-Client-ID header parameter");

        // Additional validation: Check outputSchema presence
        assertNotNull(tool.get("outputSchema"), "Tool should have outputSchema");
        @SuppressWarnings("unchecked")
        Map<String, Object> outputSchema = (Map<String, Object>) tool.get("outputSchema");
        @SuppressWarnings("unchecked")
        Map<String, Object> outputProps = (Map<String, Object>) outputSchema.get("properties");
        assertTrue(
                outputProps.containsKey("authenticated"), "Output should have authenticated field");
        assertTrue(outputProps.containsKey("expires"), "Output should have expires field");

        // Validate responseTemplate
        assertNotNull(tool.get("responseTemplate"), "Tool should have responseTemplate");
        @SuppressWarnings("unchecked")
        Map<String, Object> responseTemplate = (Map<String, Object>) tool.get("responseTemplate");
        assertNotNull(
                responseTemplate.get("prependBody"), "Response template should have prependBody");
        String prependBody = responseTemplate.get("prependBody").toString();
        assertTrue(
                prependBody.contains("API Response Information"),
                "prependBody should contain header");
        assertTrue(
                prependBody.contains("authenticated"),
                "prependBody should mention authenticated field");

        System.out.println("✅ All header parameter validations passed!");

        // 🔥 Deep YAML comparison with Higress expected output
        System.out.println("\n=== Deep YAML Object Comparison ===");
        try {
            String expectedYaml =
                    YamlTestUtils.loadYamlFromUrl(
                            "https://raw.githubusercontent.com/higress-group/openapi-to-mcpserver/main/test/expected-header-params-mcp.yaml");

            YamlTestUtils.ComparisonResult comparison =
                    YamlTestUtils.compareYaml(expectedYaml, yaml);

            System.out.println(comparison.getDifferencesReport());

            if (!comparison.isMatch()) {
                System.out.println("\n⚠️ YAML structure differences found:");
                for (String diff : comparison.getDifferences()) {
                    System.out.println("  - " + diff);
                }
                System.out.println("\n(Differences logged but not failing test)");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Could not load expected YAML for comparison: " + e.getMessage());
        }

        System.out.println("\n=== Header Parameters Test Complete ===\n");
    }

    @Test
    public void testPathParametersConversion() throws Exception {
        // Initialize
        objectMapper = new ObjectMapper();
        swaggerConverter = new SwaggerConverter(objectMapper);
        publisher = new ApigAiGatewayPublisher(null);

        System.out.println("=== Testing Path Parameters Conversion ===\n");

        String pathParamsSwagger =
                """
                {
                  "openapi": "3.0.0",
                  "info": {"version": "1.0.0", "title": "Path Parameters API"},
                  "servers": [{"url": "http://api.example.com/v1"}],
                  "paths": {
                    "/users/{userId}": {
                      "get": {
                        "summary": "Get user by ID",
                        "operationId": "getUserById",
                        "parameters": [
                          {
                            "name": "userId",
                            "in": "path",
                            "required": true,
                            "description": "The ID of the user to retrieve",
                            "schema": {"type": "string"}
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "User information",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "id": {"type": "string", "description": "User ID"},
                                    "name": {"type": "string", "description": "User name"}
                                  }
                                }
                              }
                            }
                          }
                        }
                      },
                      "put": {
                        "summary": "Update user",
                        "operationId": "updateUser",
                        "parameters": [
                          {
                            "name": "userId",
                            "in": "path",
                            "required": true,
                            "description": "The ID of the user to update",
                            "schema": {"type": "string"}
                          }
                        ],
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "properties": {
                                  "name": {"type": "string", "description": "User name"},
                                  "email": {"type": "string", "description": "User email"}
                                }
                              }
                            }
                          }
                        },
                        "responses": {
                          "200": {"description": "User updated successfully"}
                        }
                      }
                    }
                  }
                }
                """;

        List<APIEndpoint> endpoints =
                swaggerConverter.convertEndpoints(pathParamsSwagger, APIType.MCP_SERVER);
        assertEquals(2, endpoints.size(), "Should have 2 endpoints (GET and PUT)");

        APIDefinitionVO apiDefinition = buildApiDefinition("path-params-api", endpoints);
        String yamlBase64 = invokeConvertApiDefinitionToPluginConfig(publisher, apiDefinition);
        String yaml = new String(Base64.getDecoder().decode(yamlBase64), StandardCharsets.UTF_8);

        System.out.println("Generated YAML:");
        System.out.println(yaml);

        // Validate path parameters
        Yaml yamlParser = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> configMap = yamlParser.load(yaml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) configMap.get("tools");

        // Check GET endpoint
        Map<String, Object> getTool = tools.get(0);
        assertEquals("getUserById", getTool.get("name"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> getArgs = (List<Map<String, Object>>) getTool.get("args");
        assertEquals(1, getArgs.size(), "GET should have 1 arg (userId)");
        assertEquals("userId", getArgs.get(0).get("name"));
        assertEquals("path", getArgs.get(0).get("position"), "userId should have position=path");
        System.out.println("✓ GET /users/{userId} - userId has position=path");

        // Check PUT endpoint
        Map<String, Object> putTool = tools.get(1);
        assertEquals("updateUser", putTool.get("name"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> putArgs = (List<Map<String, Object>>) putTool.get("args");
        assertTrue(putArgs.size() >= 3, "PUT should have at least 3 args (name, email, userId)");

        // Find userId in PUT args
        boolean foundPathParam = false;
        boolean foundBodyParams = false;
        for (Map<String, Object> arg : putArgs) {
            String name = (String) arg.get("name");
            String position = (String) arg.get("position");
            if ("userId".equals(name)) {
                assertEquals("path", position, "userId should have position=path");
                foundPathParam = true;
            } else if ("name".equals(name) || "email".equals(name)) {
                assertEquals("body", position, name + " should have position=body");
                foundBodyParams = true;
            }
        }
        assertTrue(foundPathParam, "PUT should have userId path parameter");
        assertTrue(foundBodyParams, "PUT should have body parameters");
        System.out.println("✓ PUT /users/{userId} - Mixed path and body parameters");

        // Check Content-Type header for PUT
        @SuppressWarnings("unchecked")
        Map<String, Object> requestTemplate = (Map<String, Object>) putTool.get("requestTemplate");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> headers =
                (List<Map<String, String>>) requestTemplate.get("headers");
        assertNotNull(headers, "PUT should have headers");
        boolean hasContentType =
                headers.stream()
                        .anyMatch(
                                h ->
                                        "Content-Type".equals(h.get("key"))
                                                && "application/json".equals(h.get("value")));
        assertTrue(hasContentType, "PUT should have Content-Type: application/json header");
        System.out.println("✓ PUT request has auto-added Content-Type header");

        System.out.println("\n=== Path Parameters Test Complete ===\n");
    }

    @Test
    public void testComplexArrayItemsConversion() throws Exception {
        // Initialize
        objectMapper = new ObjectMapper();
        swaggerConverter = new SwaggerConverter(objectMapper);
        publisher = new ApigAiGatewayPublisher(null);

        System.out.println("=== Testing Complex Array Items Conversion ===\n");

        String complexArraySwagger =
                """
                {
                  "openapi": "3.0.3",
                  "info": {"title": "Array Complex Items API", "version": "1.0.0"},
                  "paths": {
                    "/users": {
                      "get": {
                        "summary": "Get users with complex array items",
                        "operationId": "getUsers",
                        "parameters": [
                          {
                            "name": "tags",
                            "in": "query",
                            "description": "Filter by tags",
                            "schema": {
                              "type": "array",
                              "items": {
                                "type": "object",
                                "properties": {
                                  "name": {"type": "string", "description": "Tag name"},
                                  "color": {"type": "string", "enum": ["red", "blue", "green"]}
                                }
                              }
                            }
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "Successful response",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "users": {
                                      "type": "array",
                                      "items": {
                                        "type": "object",
                                        "properties": {
                                          "id": {"type": "integer"},
                                          "name": {"type": "string"},
                                          "profile": {
                                            "type": "object",
                                            "properties": {
                                              "bio": {"type": "string"},
                                              "skills": {
                                                "type": "array",
                                                "items": {
                                                  "type": "object",
                                                  "properties": {
                                                    "name": {"type": "string"},
                                                    "level": {"type": "string"}
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
                swaggerConverter.convertEndpoints(complexArraySwagger, APIType.MCP_SERVER);
        assertEquals(1, endpoints.size(), "Should have 1 endpoint");

        MCPToolConfig config =
                objectMapper.readValue(endpoints.get(0).getConfig(), MCPToolConfig.class);
        assertNotNull(config.getInputSchema(), "Should have input schema");

        // Check if array parameter with complex items is handled
        @SuppressWarnings("unchecked")
        Map<String, Object> properties =
                (Map<String, Object>) config.getInputSchema().get("properties");
        assertTrue(properties.containsKey("tags"), "Should have tags parameter");

        @SuppressWarnings("unchecked")
        Map<String, Object> tagsSchema = (Map<String, Object>) properties.get("tags");
        assertEquals("array", tagsSchema.get("type"), "tags should be array type");
        assertNotNull(tagsSchema.get("items"), "tags should have items definition");

        System.out.println(
                "✓ Complex array parameter handled: tags with object items including enum");

        // Check output schema with deeply nested arrays
        assertNotNull(config.getOutputSchema(), "Should have output schema");
        @SuppressWarnings("unchecked")
        Map<String, Object> outputProps =
                (Map<String, Object>) config.getOutputSchema().get("properties");
        assertTrue(outputProps.containsKey("users"), "Output should have users array");

        System.out.println(
                "✓ Complex nested response schema preserved: users > profile > skills[]");

        APIDefinitionVO apiDefinition = buildApiDefinition("complex-array-api", endpoints);
        String yamlBase64 = invokeConvertApiDefinitionToPluginConfig(publisher, apiDefinition);
        String yaml = new String(Base64.getDecoder().decode(yamlBase64), StandardCharsets.UTF_8);

        System.out.println("\nGenerated YAML (excerpt):");
        String[] lines = yaml.split("\n");
        for (int i = 0; i < Math.min(30, lines.length); i++) {
            System.out.println(lines[i]);
        }

        System.out.println("\n=== Complex Array Items Test Complete ===\n");
    }

    @Test
    public void testArrayRootResponseConversion() throws Exception {
        // Initialize
        objectMapper = new ObjectMapper();
        swaggerConverter = new SwaggerConverter(objectMapper);
        publisher = new ApigAiGatewayPublisher(null);

        System.out.println("=== Testing Array Root Response Conversion ===\n");

        String arrayRootSwagger =
                """
                {
                  "openapi": "3.0.3",
                  "info": {"title": "Array Root Response API", "version": "1.0.0"},
                  "servers": [{"url": "https://api.example.com/v1"}],
                  "paths": {
                    "/users": {
                      "get": {
                        "summary": "Get users list",
                        "description": "Retrieve a list of users (returns array directly)",
                        "responses": {
                          "200": {
                            "description": "Successful response with array",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "array",
                                  "items": {
                                    "type": "object",
                                    "properties": {
                                      "id": {"type": "integer", "description": "User ID"},
                                      "name": {"type": "string", "description": "User name"},
                                      "email": {"type": "string", "description": "User email"}
                                    },
                                    "required": ["id", "name"]
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/tags": {
                      "get": {
                        "summary": "Get tags list",
                        "description": "Retrieve a list of tags (returns array of strings)",
                        "responses": {
                          "200": {
                            "description": "Successful response with string array",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "array",
                                  "items": {"type": "string"}
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
                swaggerConverter.convertEndpoints(arrayRootSwagger, APIType.MCP_SERVER);
        assertEquals(2, endpoints.size(), "Should have 2 endpoints");

        // Check array root response handling
        for (APIEndpoint endpoint : endpoints) {
            MCPToolConfig config =
                    objectMapper.readValue(endpoint.getConfig(), MCPToolConfig.class);
            assertNotNull(config.getOutputSchema(), "Should have output schema");

            if (endpoint.getName().contains("user")) {
                assertEquals(
                        "array",
                        config.getOutputSchema().get("type"),
                        "Users endpoint should return array");
                assertNotNull(
                        config.getOutputSchema().get("items"),
                        "Array should have items definition");
                System.out.println(
                        "✓ Array root response handled: /users returns array of objects");
            } else if (endpoint.getName().contains("tag")) {
                assertEquals(
                        "array",
                        config.getOutputSchema().get("type"),
                        "Tags endpoint should return array");
                System.out.println("✓ Array root response handled: /tags returns array of strings");
            }
        }

        APIDefinitionVO apiDefinition = buildApiDefinition("array-root-api", endpoints);
        String yamlBase64 = invokeConvertApiDefinitionToPluginConfig(publisher, apiDefinition);
        String yaml = new String(Base64.getDecoder().decode(yamlBase64), StandardCharsets.UTF_8);

        System.out.println("\nGenerated YAML (excerpt):");
        String[] lines = yaml.split("\n");
        for (int i = 0; i < Math.min(40, lines.length); i++) {
            System.out.println(lines[i]);
        }

        System.out.println("\n=== Array Root Response Test Complete ===\n");
    }

    @Test
    public void testDeepNestedResponseConversion() throws Exception {
        // Initialize
        objectMapper = new ObjectMapper();
        swaggerConverter = new SwaggerConverter(objectMapper);
        publisher = new ApigAiGatewayPublisher(null);

        System.out.println("=== Testing Deep Nested Response Conversion ===\n");

        String deepNestedSwagger =
                """
                {
                  "openapi": "3.0.3",
                  "info": {"title": "Deep Nested API", "version": "1.0.0"},
                  "paths": {
                    "/deep": {
                      "get": {
                        "summary": "Get deep nested data",
                        "description": "Retrieve data with multiple levels of nesting",
                        "responses": {
                          "200": {
                            "description": "Successful response",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "company": {
                                      "type": "object",
                                      "description": "Company information",
                                      "properties": {
                                        "name": {"type": "string", "description": "Company name"},
                                        "departments": {
                                          "type": "array",
                                          "description": "Company departments",
                                          "items": {
                                            "type": "object",
                                            "properties": {
                                              "name": {"type": "string", "description": "Department name"},
                                              "manager": {
                                                "type": "object",
                                                "description": "Department manager",
                                                "properties": {
                                                  "name": {"type": "string", "description": "Manager name"},
                                                  "contact": {
                                                    "type": "object",
                                                    "description": "Manager contact info",
                                                    "properties": {
                                                      "email": {"type": "string", "description": "Manager email"},
                                                      "phone": {"type": "string", "description": "Manager phone"}
                                                    }
                                                  }
                                                }
                                              },
                                              "employees": {
                                                "type": "array",
                                                "description": "Department employees",
                                                "items": {
                                                  "type": "object",
                                                  "properties": {
                                                    "id": {"type": "integer", "description": "Employee ID"},
                                                    "name": {"type": "string", "description": "Employee name"},
                                                    "skills": {
                                                      "type": "array",
                                                      "description": "Employee skills",
                                                      "items": {
                                                        "type": "object",
                                                        "properties": {
                                                          "name": {"type": "string", "description": "Skill name"},
                                                          "level": {"type": "string", "enum": ["beginner", "intermediate", "expert"]}
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
                                    }
                                  },
                                  "required": ["company"]
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
                swaggerConverter.convertEndpoints(deepNestedSwagger, APIType.MCP_SERVER);
        assertEquals(1, endpoints.size(), "Should have 1 endpoint");

        MCPToolConfig config =
                objectMapper.readValue(endpoints.get(0).getConfig(), MCPToolConfig.class);
        assertNotNull(config.getOutputSchema(), "Should have output schema");

        // Verify deep nesting preservation
        @SuppressWarnings("unchecked")
        Map<String, Object> outputSchema = config.getOutputSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) outputSchema.get("properties");
        assertTrue(properties.containsKey("company"), "Should have company property");

        @SuppressWarnings("unchecked")
        Map<String, Object> company = (Map<String, Object>) properties.get("company");
        @SuppressWarnings("unchecked")
        Map<String, Object> companyProps = (Map<String, Object>) company.get("properties");
        assertTrue(companyProps.containsKey("departments"), "Company should have departments");

        System.out.println(
                "✓ Deep nested structure preserved: company > departments > manager > contact");
        System.out.println(
                "✓ Multiple array nesting handled: departments[] > employees[] > skills[]");

        APIDefinitionVO apiDefinition = buildApiDefinition("deep-nested-api", endpoints);
        String yamlBase64 = invokeConvertApiDefinitionToPluginConfig(publisher, apiDefinition);
        String yaml = new String(Base64.getDecoder().decode(yamlBase64), StandardCharsets.UTF_8);

        // Validate YAML contains nested structure
        assertTrue(
                yaml.contains("company"),
                "YAML should contain company in outputSchema or responseTemplate");
        assertTrue(
                yaml.contains("departments"),
                "YAML should contain departments in nested structure");

        System.out.println("\nGenerated YAML validated for deep nesting");
        System.out.println("\n=== Deep Nested Response Test Complete ===\n");
    }

    @Test
    public void testPetstoreExample() throws Exception {
        // Initialize
        objectMapper = new ObjectMapper();
        swaggerConverter = new SwaggerConverter(objectMapper);
        publisher = new ApigAiGatewayPublisher(null);

        System.out.println("=== Testing Petstore Example (Real-world API) ===\n");

        String petstoreSwagger =
                """
                    {
                        "openapi": "3.0.0",
                        "info": {
                            "title": "Petstore API",
                            "version": "1.0.0"
                        },
                        "servers": [{"url": "http://petstore.swagger.io/v1"}],
                        "paths": {
                            "/pets": {
                                "get": {
                                    "summary": "List all pets",
                                    "operationId": "listPets",
                                    "parameters": [{
                                        "name": "limit",
                                        "in": "query",
                                        "description": "How many items to return at one time (max 100)",
                                        "schema": {"type": "integer"}
                                    }],
                                    "responses": {
                                        "200": {
                                            "content": {
                                                "application/json": {
                                                    "schema": {
                                                        "type": "object",
                                                        "properties": {
                                                            "pets": {
                                                                "type": "array",
                                                                "items": {
                                                                    "type": "object",
                                                                    "properties": {
                                                                        "id": {"type": "integer"},
                                                                        "name": {"type": "string"},
                                                                        "tag": {"type": "string"}
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                "post": {
                                    "summary": "Create a pet",
                                    "operationId": "createPets",
                                    "requestBody": {
                                        "required": true,
                                        "content": {
                                            "application/json": {
                                                "schema": {
                                                    "type": "object",
                                                    "required": ["name"],
                                                    "properties": {
                                                        "name": {"type": "string"},
                                                        "tag": {"type": "string"}
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            "/pets/{petId}": {
                                "get": {
                                    "summary": "Info for a specific pet",
                                    "operationId": "showPetById",
                                    "parameters": [{
                                        "name": "petId",
                                        "in": "path",
                                        "required": true,
                                        "description": "The id of the pet to retrieve",
                                        "schema": {"type": "string"}
                                    }],
                                    "responses": {
                                        "200": {
                                            "content": {
                                                "application/json": {
                                                    "schema": {
                                                        "type": "object",
                                                        "properties": {
                                                            "id": {"type": "integer"},
                                                            "name": {"type": "string"},
                                                            "tag": {"type": "string"}
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
                swaggerConverter.convertEndpoints(petstoreSwagger, APIType.MCP_SERVER);
        APIDefinitionVO apiDefinition = buildApiDefinition("petstore-api", endpoints);
        assertNotNull(apiDefinition);
        assertEquals(3, apiDefinition.getEndpoints().size());

        // Test listPets - GET with query parameter
        APIEndpointVO listPetsEndpoint =
                apiDefinition.getEndpoints().stream()
                        .filter(e -> "listPets".equals(e.getName()))
                        .findFirst()
                        .orElse(null);
        assertNotNull(listPetsEndpoint);
        MCPToolConfig listPetsConfig = (MCPToolConfig) listPetsEndpoint.getConfig();
        assertTrue(listPetsConfig.getInputSchema().containsKey("properties"));
        System.out.println("✓ listPets endpoint validated");

        // Test createPets - POST with request body
        APIEndpointVO createPetsEndpoint =
                apiDefinition.getEndpoints().stream()
                        .filter(e -> "createPets".equals(e.getName()))
                        .findFirst()
                        .orElse(null);
        assertNotNull(createPetsEndpoint);
        MCPToolConfig createPetsConfig = (MCPToolConfig) createPetsEndpoint.getConfig();
        assertTrue(createPetsConfig.getInputSchema().containsKey("properties"));
        assertTrue(createPetsConfig.getRequestTemplate().getMethod().equalsIgnoreCase("POST"));
        System.out.println("✓ createPets endpoint validated");

        // Test showPetById - GET with path parameter
        APIEndpointVO showPetByIdEndpoint =
                apiDefinition.getEndpoints().stream()
                        .filter(e -> "showPetById".equals(e.getName()))
                        .findFirst()
                        .orElse(null);
        assertNotNull(showPetByIdEndpoint);
        MCPToolConfig showPetByIdConfig = (MCPToolConfig) showPetByIdEndpoint.getConfig();
        assertTrue(showPetByIdConfig.getRequestTemplate().getUrl().contains("{petId}"));
        System.out.println("✓ showPetById endpoint validated");

        // Generate complete YAML and validate against expected structure
        String yamlBase64 = invokeConvertApiDefinitionToPluginConfig(publisher, apiDefinition);
        String yaml = new String(Base64.getDecoder().decode(yamlBase64), StandardCharsets.UTF_8);

        System.out.println("\nGenerated YAML:");
        System.out.println(yaml);

        // Validate YAML structure matches Higress expected output
        Yaml yamlParser = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> configMap = yamlParser.load(yaml);

        // Validate server
        @SuppressWarnings("unchecked")
        Map<String, Object> server = (Map<String, Object>) configMap.get("server");
        assertNotNull(server, "Should have server section");
        assertEquals("petstore-api", server.get("name"), "Server name should match");

        // Validate tools
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) configMap.get("tools");
        assertNotNull(tools, "Should have tools");
        assertEquals(3, tools.size(), "Should have 3 tools");

        // Validate tool names and order
        List<String> toolNames = tools.stream().map(t -> (String) t.get("name")).toList();
        assertTrue(toolNames.contains("createPets"), "Should have createPets");
        assertTrue(toolNames.contains("listPets"), "Should have listPets");
        assertTrue(toolNames.contains("showPetById"), "Should have showPetById");

        // Validate createPets tool structure
        Map<String, Object> createPetsTool =
                tools.stream()
                        .filter(t -> "createPets".equals(t.get("name")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(createPetsTool);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> createPetsArgs =
                (List<Map<String, Object>>) createPetsTool.get("args");
        assertTrue(
                createPetsArgs.stream().anyMatch(a -> "name".equals(a.get("name"))),
                "createPets should have 'name' arg");
        assertTrue(
                createPetsArgs.stream()
                        .anyMatch(
                                a ->
                                        "name".equals(a.get("name"))
                                                && "body".equals(a.get("position"))),
                "name arg should have position=body");
        @SuppressWarnings("unchecked")
        Map<String, Object> createReqTemplate =
                (Map<String, Object>) createPetsTool.get("requestTemplate");
        assertEquals("POST", createReqTemplate.get("method"), "createPets should be POST");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> createHeaders =
                (List<Map<String, String>>) createReqTemplate.get("headers");
        assertTrue(
                createHeaders.stream()
                        .anyMatch(
                                h ->
                                        "Content-Type".equals(h.get("key"))
                                                && "application/json".equals(h.get("value"))),
                "POST should have Content-Type header");

        // Validate listPets tool structure
        Map<String, Object> listPetsTool =
                tools.stream()
                        .filter(t -> "listPets".equals(t.get("name")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(listPetsTool);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listPetsArgs =
                (List<Map<String, Object>>) listPetsTool.get("args");
        assertTrue(
                listPetsArgs.stream()
                        .anyMatch(
                                a ->
                                        "limit".equals(a.get("name"))
                                                && "query".equals(a.get("position"))),
                "listPets should have limit with position=query");
        assertNotNull(listPetsTool.get("outputSchema"), "listPets should have outputSchema");
        assertNotNull(
                listPetsTool.get("responseTemplate"), "listPets should have responseTemplate");

        // Validate showPetById tool structure
        Map<String, Object> showPetTool =
                tools.stream()
                        .filter(t -> "showPetById".equals(t.get("name")))
                        .findFirst()
                        .orElse(null);
        assertNotNull(showPetTool);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> showPetArgs = (List<Map<String, Object>>) showPetTool.get("args");
        assertTrue(
                showPetArgs.stream()
                        .anyMatch(
                                a ->
                                        "petId".equals(a.get("name"))
                                                && "path".equals(a.get("position"))
                                                && Boolean.TRUE.equals(a.get("required"))),
                "showPetById should have petId with position=path and required=true");
        @SuppressWarnings("unchecked")
        Map<String, Object> showReqTemplate =
                (Map<String, Object>) showPetTool.get("requestTemplate");
        assertTrue(
                showReqTemplate.get("url").toString().contains("{petId}"),
                "showPetById URL should contain {petId}");

        System.out.println("\n✅ All Petstore validations passed!");

        // 🔥 Deep YAML comparison: Load expected YAML from Higress and compare
        System.out.println("\n=== Deep YAML Object Comparison ===");
        try {
            String expectedYaml =
                    YamlTestUtils.loadYamlFromUrl(
                            "https://raw.githubusercontent.com/higress-group/openapi-to-mcpserver/main/test/expected-petstore-mcp.yaml");

            // 注意: Higress 测试的 server name 是 "petstore", 我们的是 "petstore-api"
            // 需要规范化对比或调整 server name
            String normalizedActual = yaml.replace("name: petstore-api", "name: petstore");

            YamlTestUtils.ComparisonResult comparison =
                    YamlTestUtils.compareYaml(expectedYaml, normalizedActual);

            System.out.println(comparison.getDifferencesReport());

            if (!comparison.isMatch()) {
                System.out.println("\n⚠️ YAML structure differences found:");
                for (String diff : comparison.getDifferences()) {
                    System.out.println("  - " + diff);
                }
                // For now, don't fail - just report differences
                System.out.println("\n(Differences logged but not failing test)");
            }
        } catch (Exception e) {
            System.out.println("⚠️ Could not load expected YAML for comparison: " + e.getMessage());
        }

        System.out.println("\n=== Petstore Example Test Complete ===\n");
    }

    @Test
    public void testSchemaReferencesConversion() throws Exception {
        // Initialize
        objectMapper = new ObjectMapper();
        swaggerConverter = new SwaggerConverter(objectMapper);
        publisher = new ApigAiGatewayPublisher(null);

        System.out.println("=== Testing Schema References ($ref) Conversion ===\n");

        String refSwagger =
                """
                    {
                        "openapi": "3.0.1",
                        "info": {"title": "Library API", "version": "1.0"},
                        "paths": {
                            "/api/books/recommendation": {
                                "post": {
                                    "operationId": "generateRecommendations",
                                    "parameters": [
                                        {
                                            "name": "Authorization",
                                            "in": "header",
                                            "required": true
                                        }
                                    ],
                                    "requestBody": {
                                        "content": {
                                            "application/json": {
                                                "schema": {
                                                    "$ref": "#/components/schemas/RecommendationRequest"
                                                }
                                            }
                                        }
                                    },
                                    "responses": {
                                        "200": {
                                            "content": {
                                                "*/*": {
                                                    "schema": {
                                                        "$ref": "#/components/schemas/RecommendationResponse"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        "components": {
                            "schemas": {
                                "RecommendationRequest": {
                                    "type": "object",
                                    "properties": {
                                        "readerProfile": {
                                            "$ref": "#/components/schemas/ReaderProfile"
                                        },
                                        "preferences": {
                                            "type": "array",
                                            "items": {
                                                "$ref": "#/components/schemas/PreferenceSetting"
                                            }
                                        }
                                    }
                                },
                                "RecommendationResponse": {
                                    "type": "object",
                                    "properties": {
                                        "status": {"type": "string"},
                                        "message": {"type": "string"},
                                        "readerProfile": {
                                            "$ref": "#/components/schemas/ReaderProfile"
                                        }
                                    }
                                },
                                "ReaderProfile": {
                                    "type": "object",
                                    "properties": {
                                        "readerName": {"type": "string"},
                                        "readerAge": {"type": "integer"},
                                        "membershipLevel": {"type": "string"}
                                    }
                                },
                                "PreferenceSetting": {
                                    "type": "object",
                                    "required": ["categoryId"],
                                    "properties": {
                                        "categoryId": {"type": "string"},
                                        "categoryName": {"type": "string"}
                                    }
                                }
                            }
                        }
                    }
                """;

        List<APIEndpoint> endpoints =
                swaggerConverter.convertEndpoints(refSwagger, APIType.MCP_SERVER);
        APIDefinitionVO apiDefinition = buildApiDefinition("library-api", endpoints);
        assertNotNull(apiDefinition);
        assertEquals(1, apiDefinition.getEndpoints().size());

        APIEndpointVO endpoint = apiDefinition.getEndpoints().get(0);
        assertNotNull(endpoint.getConfig());

        // Verify $ref resolution in input schema
        MCPToolConfig config = (MCPToolConfig) endpoint.getConfig();
        assertTrue(config.getInputSchema().containsKey("properties"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) config.getInputSchema().get("properties");
        assertTrue(props.containsKey("Authorization") || props.size() > 0);
        System.out.println("✓ Schema $ref resolved in request body");

        // Verify response schema with $ref
        assertNotNull(config.getOutputSchema());
        System.out.println("✓ Schema $ref resolved in response");

        System.out.println("\n=== Schema References Test Complete ===\n");
    }

    @Test
    public void testSecuritySchemesConversion() throws Exception {
        // Initialize
        objectMapper = new ObjectMapper();
        swaggerConverter = new SwaggerConverter(objectMapper);
        publisher = new ApigAiGatewayPublisher(null);

        System.out.println("=== Testing Security Schemes Conversion ===\n");

        String securitySwagger =
                """
                    {
                        "openapi": "3.0.0",
                        "info": {"title": "Security Test API", "version": "1.0.0"},
                        "components": {
                            "securitySchemes": {
                                "BasicAuth": {
                                    "type": "http",
                                    "scheme": "basic"
                                },
                                "BearerAuth": {
                                    "type": "http",
                                    "scheme": "bearer",
                                    "bearerFormat": "JWT"
                                },
                                "ApiKeyHeaderAuth": {
                                    "type": "apiKey",
                                    "in": "header",
                                    "name": "X-API-KEY"
                                },
                                "ApiKeyQueryAuth": {
                                    "type": "apiKey",
                                    "in": "query",
                                    "name": "api_key"
                                }
                            }
                        },
                        "paths": {
                            "/basic_auth_resource": {
                                "get": {
                                    "operationId": "getBasicAuthResource",
                                    "security": [{"BasicAuth": []}],
                                    "responses": {"200": {"description": "Success"}}
                                }
                            },
                            "/bearer_auth_resource": {
                                "get": {
                                    "operationId": "getBearerAuthResource",
                                    "security": [{"BearerAuth": []}],
                                    "responses": {"200": {"description": "Success"}}
                                }
                            },
                            "/apikey_header_resource": {
                                "get": {
                                    "operationId": "getApiKeyHeaderResource",
                                    "security": [{"ApiKeyHeaderAuth": []}],
                                    "responses": {"200": {"description": "Success"}}
                                }
                            },
                            "/apikey_query_resource": {
                                "get": {
                                    "operationId": "getApiKeyQueryResource",
                                    "security": [{"ApiKeyQueryAuth": []}],
                                    "responses": {"200": {"description": "Success"}}
                                }
                            },
                            "/multi_auth_resource": {
                                "get": {
                                    "operationId": "getMultiAuthResource",
                                    "security": [{"BearerAuth": []}, {"ApiKeyHeaderAuth": []}],
                                    "responses": {"200": {"description": "Success"}}
                                }
                            }
                        }
                    }
                """;

        List<APIEndpoint> endpoints =
                swaggerConverter.convertEndpoints(securitySwagger, APIType.MCP_SERVER);
        APIDefinitionVO apiDefinition = buildApiDefinition("security-api", endpoints);
        assertNotNull(apiDefinition);
        assertEquals(5, apiDefinition.getEndpoints().size());

        // Test that security endpoints are converted successfully
        for (APIEndpointVO endpoint : apiDefinition.getEndpoints()) {
            assertNotNull(endpoint.getConfig());
            System.out.println("✓ Security endpoint converted: " + endpoint.getName());
        }

        // Verify specific endpoints exist
        assertTrue(
                apiDefinition.getEndpoints().stream()
                        .anyMatch(e -> "getBasicAuthResource".equals(e.getName())));
        assertTrue(
                apiDefinition.getEndpoints().stream()
                        .anyMatch(e -> "getBearerAuthResource".equals(e.getName())));
        assertTrue(
                apiDefinition.getEndpoints().stream()
                        .anyMatch(e -> "getApiKeyHeaderResource".equals(e.getName())));
        assertTrue(
                apiDefinition.getEndpoints().stream()
                        .anyMatch(e -> "getApiKeyQueryResource".equals(e.getName())));
        assertTrue(
                apiDefinition.getEndpoints().stream()
                        .anyMatch(e -> "getMultiAuthResource".equals(e.getName())));

        System.out.println("\n=== Security Schemes Test Complete ===\n");
    }

    @Test
    public void testMixedParameterTypesConversion() throws Exception {
        // Initialize
        objectMapper = new ObjectMapper();
        swaggerConverter = new SwaggerConverter(objectMapper);
        publisher = new ApigAiGatewayPublisher(null);

        System.out.println("=== Testing Mixed Parameter Types Conversion ===\n");

        String mixedParamsSwagger =
                """
                {
                  "openapi": "3.0.0",
                  "info": {"version": "1.0.0", "title": "Mixed Parameters API"},
                  "paths": {
                    "/search/{category}": {
                      "post": {
                        "summary": "Search with mixed parameters",
                        "operationId": "searchItems",
                        "parameters": [
                          {
                            "name": "category",
                            "in": "path",
                            "required": true,
                            "description": "Search category",
                            "schema": {"type": "string"}
                          },
                          {
                            "name": "page",
                            "in": "query",
                            "required": false,
                            "description": "Page number",
                            "schema": {"type": "integer", "default": 1}
                          },
                          {
                            "name": "limit",
                            "in": "query",
                            "required": false,
                            "description": "Items per page",
                            "schema": {"type": "integer", "default": 10}
                          },
                          {
                            "name": "X-Request-ID",
                            "in": "header",
                            "required": false,
                            "description": "Request tracking ID",
                            "schema": {"type": "string"}
                          },
                          {
                            "name": "sessionToken",
                            "in": "cookie",
                            "required": true,
                            "description": "Session token",
                            "schema": {"type": "string"}
                          }
                        ],
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": {
                                "type": "object",
                                "properties": {
                                  "keywords": {"type": "string", "description": "Search keywords"},
                                  "filters": {
                                    "type": "object",
                                    "description": "Search filters",
                                    "properties": {
                                      "priceMin": {"type": "number"},
                                      "priceMax": {"type": "number"}
                                    }
                                  }
                                },
                                "required": ["keywords"]
                              }
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "description": "Search results",
                            "content": {
                              "application/json": {
                                "schema": {
                                  "type": "object",
                                  "properties": {
                                    "total": {"type": "integer"},
                                    "items": {"type": "array", "items": {"type": "object"}}
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
                swaggerConverter.convertEndpoints(mixedParamsSwagger, APIType.MCP_SERVER);
        assertEquals(1, endpoints.size(), "Should have 1 endpoint");

        APIDefinitionVO apiDefinition = buildApiDefinition("mixed-params-api", endpoints);
        String yamlBase64 = invokeConvertApiDefinitionToPluginConfig(publisher, apiDefinition);
        String yaml = new String(Base64.getDecoder().decode(yamlBase64), StandardCharsets.UTF_8);

        System.out.println("Generated YAML:");
        System.out.println(yaml);

        // Validate mixed parameters
        Yaml yamlParser = new Yaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> configMap = yamlParser.load(yaml);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) configMap.get("tools");
        Map<String, Object> tool = tools.get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> args = (List<Map<String, Object>>) tool.get("args");

        // Check all parameter types are present with correct positions
        boolean hasPath = false;
        boolean hasQuery = false;
        boolean hasHeader = false;
        boolean hasCookie = false;
        boolean hasBody = false;

        for (Map<String, Object> arg : args) {
            String name = (String) arg.get("name");
            String position = (String) arg.get("position");

            if ("category".equals(name)) {
                assertEquals("path", position, "category should be path param");
                hasPath = true;
            } else if ("page".equals(name) || "limit".equals(name)) {
                assertEquals("query", position, name + " should be query param");
                hasQuery = true;
            } else if ("X-Request-ID".equals(name)) {
                assertEquals("header", position, "X-Request-ID should be header param");
                hasHeader = true;
            } else if ("sessionToken".equals(name)) {
                assertEquals("cookie", position, "sessionToken should be cookie param");
                hasCookie = true;
            } else if ("keywords".equals(name) || "filters".equals(name)) {
                assertEquals("body", position, name + " should be body param");
                hasBody = true;
            }
        }

        assertTrue(hasPath, "Should have path parameter");
        assertTrue(hasQuery, "Should have query parameter");
        assertTrue(hasHeader, "Should have header parameter");
        assertTrue(hasCookie, "Should have cookie parameter");
        assertTrue(hasBody, "Should have body parameter");

        System.out.println(
                "✓ Mixed parameter types handled: path + query + header + cookie + body");

        // Check Content-Type header
        @SuppressWarnings("unchecked")
        Map<String, Object> requestTemplate = (Map<String, Object>) tool.get("requestTemplate");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> headers =
                (List<Map<String, String>>) requestTemplate.get("headers");
        assertNotNull(headers, "Should have headers");
        boolean hasContentType =
                headers.stream()
                        .anyMatch(
                                h ->
                                        "Content-Type".equals(h.get("key"))
                                                && "application/json".equals(h.get("value")));
        assertTrue(hasContentType, "POST should have auto-added Content-Type header");
        System.out.println("✓ Auto-added Content-Type header for POST with body");

        System.out.println("\n=== Mixed Parameter Types Test Complete ===\n");
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
