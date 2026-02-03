/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.service.api;

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.api.ToolImportPreviewDTO;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.EndpointType;
import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SwaggerConverter {

    private final ObjectMapper objectMapper;

    /**
     * 转换 Swagger 文档中的 paths 为工具预览列表
     *
     * @param swaggerContent Swagger JSON 内容
     * @param type API 类型
     * @return 工具预览列表
     */
    public List<ToolImportPreviewDTO> convertEndpoints(String swaggerContent, APIType type) {
        try {
            JsonNode swagger = JsonUtil.readTree(swaggerContent);
            JsonNode paths = swagger.get("paths");

            if (paths == null || paths.isEmpty()) {
                return new ArrayList<>();
            }

            List<ToolImportPreviewDTO> tools = new ArrayList<>();
            int sortOrder = 0;

            Iterator<String> pathIterator = paths.fieldNames();
            while (pathIterator.hasNext()) {
                String path = pathIterator.next();
                JsonNode pathItem = paths.get(path);

                // 处理每个 HTTP 方法
                Iterator<String> methodIterator = pathItem.fieldNames();
                while (methodIterator.hasNext()) {
                    String method = methodIterator.next();
                    if (isHttpMethod(method)) {
                        JsonNode operation = pathItem.get(method);
                        ToolImportPreviewDTO tool =
                                convertOperation(
                                        path, method, operation, sortOrder++, type, swagger);
                        tools.add(tool);
                    }
                }
            }

            // Sort tools by name to ensure consistent order
            tools.sort(Comparator.comparing(ToolImportPreviewDTO::getName));

            return tools;
        } catch (Exception e) {
            log.error("Failed to convert Swagger endpoints", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to convert Swagger endpoints: " + e.getMessage());
        }
    }

    /** 转换单个操作为工具预览 DTO */
    private ToolImportPreviewDTO convertOperation(
            String path,
            String method,
            JsonNode operation,
            int sortOrder,
            APIType type,
            JsonNode swagger) {
        Map<String, Object> config = new HashMap<>();
        EndpointType endpointType;
        String name;
        String description;

        if (type == APIType.MCP_SERVER) {
            endpointType = EndpointType.MCP_TOOL;
            name = generateToolName(path, method, operation);
            description = getToolDescription(operation);

            // Build complete URL from servers + path
            String baseUrl = getBaseUrl(swagger);
            String fullUrl = baseUrl != null ? baseUrl + path : path;

            Map<String, Object> requestTemplate = new HashMap<>();
            requestTemplate.put("url", fullUrl);
            requestTemplate.put("method", method.toUpperCase());
            config.put("requestTemplate", requestTemplate);

            // 设置 Input Schema
            config.put("inputSchema", createInputSchema(operation, swagger));

            // 设置 Output Schema
            config.put("outputSchema", createOutputSchema(operation, swagger));

        } else {
            endpointType = EndpointType.REST_ROUTE;
            // 提取基本信息
            String summary = operation.path("summary").asText(null);
            String desc = operation.path("description").asText(null);
            name = StrUtil.isNotBlank(summary) ? summary : method.toUpperCase() + " " + path;
            description = desc;

            config.put("path", path);
            config.put("method", method.toUpperCase());

            // 提取参数
            List<Map<String, Object>> parameters = new ArrayList<>();
            if (operation.has("parameters")) {
                operation
                        .get("parameters")
                        .forEach(
                                param -> {
                                    JsonNode paramObj = (JsonNode) param;
                                    Map<String, Object> parameter = new HashMap<>();
                                    parameter.put("name", paramObj.path("name").asText(null));
                                    parameter.put("in", paramObj.path("in").asText(null));
                                    parameter.put(
                                            "description",
                                            paramObj.path("description").asText(null));
                                    parameter.put(
                                            "required", paramObj.path("required").asBoolean(false));
                                    JsonNode schemaNode = paramObj.get("schema");
                                    if (schemaNode != null) {
                                        parameter.put(
                                                "schema",
                                                objectMapper.convertValue(
                                                        schemaNode,
                                                        new com.fasterxml.jackson.core.type
                                                                        .TypeReference<
                                                                Map<String, Object>>() {}));
                                    }
                                    parameters.add(parameter);
                                });
            }
            config.put("parameters", parameters);

            // 提取响应
            Map<String, Map<String, Object>> responses = new HashMap<>();
            if (operation.has("responses")) {
                JsonNode responsesObj = operation.get("responses");
                Iterator<String> codeIterator = responsesObj.fieldNames();
                while (codeIterator.hasNext()) {
                    String code = codeIterator.next();
                    JsonNode response = responsesObj.get(code);
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("description", response.path("description").asText(null));
                    JsonNode schemaNode = response.get("schema");
                    if (schemaNode != null) {
                        resp.put(
                                "schema",
                                objectMapper.convertValue(
                                        schemaNode,
                                        new com.fasterxml.jackson.core.type.TypeReference<
                                                Map<String, Object>>() {}));
                    }
                    responses.put(code, resp);
                }
            }
            config.put("responses", responses);
        }

        return ToolImportPreviewDTO.builder()
                .name(name)
                .description(description)
                .type(endpointType)
                .config(config)
                .sortOrder(sortOrder)
                .build();
    }

    /** 生成 MCP Tool 名称 */
    private String generateToolName(String path, String method, JsonNode operation) {
        String operationId = operation.path("operationId").asText(null);
        if (StrUtil.isNotBlank(operationId)) {
            return sanitizeName(operationId);
        }

        // /api/v1/users/{id} -> api_v1_users_id
        String sanitizedPath =
                path.replaceAll("\\{([^}]+)\\}", "$1").replaceAll("[^a-zA-Z0-9]", "_");

        return (method.toLowerCase() + "_" + sanitizedPath)
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    /** 净化名称，只保留字母、数字、下划线和连字符 */
    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /** 获取 MCP Tool 描述 */
    private String getToolDescription(JsonNode operation) {
        String summary = operation.path("summary").asText(null);
        String description = operation.path("description").asText(null);

        if (StrUtil.isNotBlank(summary)) {
            if (StrUtil.isNotBlank(description)) {
                return summary + " - " + description;
            }
            return summary;
        }
        return description;
    }

    /** 创建 MCP Input Schema */
    private Map<String, Object> createInputSchema(JsonNode operation, JsonNode swagger) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        // Track parameter positions for MCP args generation
        Map<String, String> parameterPositions = new HashMap<>();

        // 处理参数
        if (operation.has("parameters")) {
            operation
                    .get("parameters")
                    .forEach(
                            param -> {
                                JsonNode paramObj = (JsonNode) param;
                                // 处理参数引用
                                if (paramObj.has("$ref")) {
                                    paramObj =
                                            resolveRef(paramObj.path("$ref").asText(null), swagger);
                                }
                                if (paramObj == null) return;

                                String name = paramObj.path("name").asText(null);
                                String position = paramObj.path("in").asText("query");

                                JsonNode paramSchema = paramObj.get("schema");
                                Map<String, Object> propSchema;
                                if (paramSchema != null) {
                                    // 复制 schema 属性
                                    propSchema = resolveSchema(paramSchema, swagger);
                                } else {
                                    // 如果没有 schema，尝试直接从参数定义中获取类型信息 (Swagger 2.0)
                                    propSchema = new HashMap<>();
                                    propSchema.put("type", paramObj.path("type").asText("string"));
                                }

                                // Add description if present
                                if (paramObj.has("description")) {
                                    propSchema.put(
                                            "description",
                                            paramObj.path("description").asText(null));
                                }

                                // Store position metadata
                                propSchema.put("x-position", position);
                                parameterPositions.put(name, position);

                                properties.put(name, propSchema);

                                if (paramObj.path("required").asBoolean(false)) {
                                    required.add(name);
                                }
                            });
        }

        // 处理 Request Body (OpenAPI 3.0)
        if (operation.has("requestBody")) {
            JsonNode requestBody = operation.get("requestBody");
            // 处理 Request Body 引用
            if (requestBody.has("$ref")) {
                requestBody = resolveRef(requestBody.path("$ref").asText(null), swagger);
            }

            if (requestBody != null) {
                JsonNode content = requestBody.get("content");
                if (content != null) {
                    // 优先处理 application/json
                    JsonNode jsonContent = content.get("application/json");
                    if (jsonContent != null && jsonContent.has("schema")) {
                        Map<String, Object> bodySchema =
                                resolveSchema(jsonContent.get("schema"), swagger);
                        // 如果 body 是 object，将其属性合并到 properties
                        if ("object".equals(bodySchema.get("type"))) {
                            if (bodySchema.containsKey("properties")) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> bodyProps =
                                        (Map<String, Object>) bodySchema.get("properties");
                                bodyProps.forEach(
                                        (k, v) -> {
                                            // Mark body parameters with position
                                            if (v instanceof Map) {
                                                @SuppressWarnings("unchecked")
                                                Map<String, Object> propMap =
                                                        (Map<String, Object>) v;
                                                propMap.put("x-position", "body");
                                            }
                                            properties.put(k, v);
                                            parameterPositions.put(k, "body");
                                        });
                            }
                            if (bodySchema.containsKey("required")) {
                                ((List<?>) bodySchema.get("required"))
                                        .forEach(r -> required.add(r.toString()));
                            }
                        } else {
                            // 如果 body 不是 object (例如 array)，作为一个名为 body 的参数
                            bodySchema.put("x-position", "body");
                            properties.put("body", bodySchema);
                            parameterPositions.put("body", "body");
                            if (requestBody.path("required").asBoolean(false)) {
                                required.add("body");
                            }
                        }
                    }
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        // Store position metadata at schema level
        schema.put("x-parameter-positions", parameterPositions);
        return schema;
    }

    /** 创建 MCP Output Schema */
    private Map<String, Object> createOutputSchema(JsonNode operation, JsonNode swagger) {
        if (!operation.has("responses")) {
            return null;
        }
        JsonNode responses = operation.get("responses");

        // 查找 2xx 响应
        Iterator<String> fieldNames = responses.fieldNames();
        while (fieldNames.hasNext()) {
            String code = fieldNames.next();
            if (code.startsWith("2")) {
                JsonNode response = responses.get(code);
                // 处理 Response 引用
                if (response.has("$ref")) {
                    response = resolveRef(response.path("$ref").asText(null), swagger);
                }
                if (response == null) continue;

                if (response.has("content")) {
                    JsonNode content = response.get("content");
                    // 优先处理 application/json
                    JsonNode jsonContent = content.get("application/json");
                    if (jsonContent != null && jsonContent.has("schema")) {
                        return resolveSchema(jsonContent.get("schema"), swagger);
                    }
                    // 尝试其他 content type
                    Iterator<String> contentTypeIterator = content.fieldNames();
                    while (contentTypeIterator.hasNext()) {
                        String contentType = contentTypeIterator.next();
                        JsonNode typeContent = content.get(contentType);
                        if (typeContent != null && typeContent.has("schema")) {
                            return resolveSchema(typeContent.get("schema"), swagger);
                        }
                    }
                } else if (response.has("schema")) {
                    // Swagger 2.0
                    return resolveSchema(response.get("schema"), swagger);
                }
            }
        }
        return null;
    }

    /** 解析 Schema 中的引用 */
    private Map<String, Object> resolveSchema(JsonNode schema, JsonNode swagger) {
        if (schema == null) return null;

        // 如果是引用，解析引用
        if (schema.has("$ref")) {
            JsonNode resolved = resolveRef(schema.path("$ref").asText(null), swagger);
            if (resolved != null) {
                // 递归解析引用的 schema
                return resolveSchema(resolved, swagger);
            }
            return objectMapper.convertValue(
                    schema,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        }

        // 将JsonNode转换为Map以便操作
        @SuppressWarnings("unchecked")
        Map<String, Object> schemaMap =
                objectMapper.convertValue(
                        schema,
                        new com.fasterxml.jackson.core.type.TypeReference<
                                Map<String, Object>>() {});

        // 处理 allOf: 合并所有 schema 到当前 schema
        if (schemaMap.containsKey("allOf")) {
            List<?> allOfList = (List<?>) schemaMap.get("allOf");
            Map<String, Object> merged = new HashMap<>();

            // 先复制当前 schema 的其他属性
            for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
                if (!"allOf".equals(entry.getKey())) {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }

            // 合并所有 allOf 中的 schema
            for (Object item : allOfList) {
                JsonNode itemNode = objectMapper.valueToTree(item);
                Map<String, Object> resolvedItem = resolveSchema(itemNode, swagger);
                if (resolvedItem != null) {
                    // 合并类型
                    if (resolvedItem.containsKey("type") && !merged.containsKey("type")) {
                        merged.put("type", resolvedItem.get("type"));
                    }
                    // 合并 properties
                    if (resolvedItem.containsKey("properties")) {
                        if (!merged.containsKey("properties")) {
                            merged.put("properties", new HashMap<>());
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mergedProps =
                                (Map<String, Object>) merged.get("properties");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemProps =
                                (Map<String, Object>) resolvedItem.get("properties");
                        mergedProps.putAll(itemProps);
                    }
                    // 合并 required
                    if (resolvedItem.containsKey("required")) {
                        @SuppressWarnings("unchecked")
                        List<Object> mergedRequired = (List<Object>) merged.get("required");
                        if (mergedRequired == null) {
                            mergedRequired = new ArrayList<>();
                            merged.put("required", mergedRequired);
                        }
                        @SuppressWarnings("unchecked")
                        List<Object> itemRequired = (List<Object>) resolvedItem.get("required");
                        for (Object req : itemRequired) {
                            if (!mergedRequired.contains(req)) {
                                mergedRequired.add(req);
                            }
                        }
                    }
                    // 合并其他字段 (description, etc)
                    for (String key : resolvedItem.keySet()) {
                        if (!merged.containsKey(key)
                                && !"type".equals(key)
                                && !"properties".equals(key)
                                && !"required".equals(key)) {
                            merged.put(key, resolvedItem.get(key));
                        }
                    }
                }
            }
            schemaMap = merged;
        }

        // 递归处理 properties
        if (schemaMap.containsKey("properties")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
            Map<String, Object> newProperties = new HashMap<>();
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (entry.getValue() != null) {
                    JsonNode valueNode = objectMapper.valueToTree(entry.getValue());
                    newProperties.put(entry.getKey(), resolveSchema(valueNode, swagger));
                } else {
                    newProperties.put(entry.getKey(), null);
                }
            }
            schemaMap.put("properties", newProperties);
        }

        // 递归处理 items (数组)
        if (schemaMap.containsKey("items")) {
            Object itemsObj = schemaMap.get("items");
            if (itemsObj != null) {
                JsonNode itemsNode = objectMapper.valueToTree(itemsObj);
                schemaMap.put("items", resolveSchema(itemsNode, swagger));
            }
        }

        // 递归处理 anyOf, oneOf (保留原样，不合并)
        String[] combinators = {"anyOf", "oneOf"};
        for (String combinator : combinators) {
            if (schemaMap.containsKey(combinator)) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) schemaMap.get(combinator);
                List<Object> newList = new ArrayList<>();
                for (Object item : list) {
                    if (item != null) {
                        JsonNode itemNode = objectMapper.valueToTree(item);
                        newList.add(resolveSchema(itemNode, swagger));
                    } else {
                        newList.add(null);
                    }
                }
                schemaMap.put(combinator, newList);
            }
        }

        return schemaMap;
    }

    /** 解析引用路径 */
    private JsonNode resolveRef(String ref, JsonNode swagger) {
        if (StrUtil.isBlank(ref) || !ref.startsWith("#/")) {
            return null;
        }

        try {
            String[] parts = ref.substring(2).split("/");
            JsonNode current = swagger;
            for (String part : parts) {
                // 处理转义字符: ~1 -> /, ~0 -> ~
                part = part.replace("~1", "/").replace("~0", "~");
                if (current.has(part)) {
                    JsonNode val = current.get(part);
                    if (val != null) {
                        current = val;
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
            // 复制一份，避免修改原对象
            return JsonUtil.readTree(JsonUtil.toJson(current));
        } catch (Exception e) {
            log.warn("Failed to resolve ref: {}", ref, e);
            return null;
        }
    }

    /** 判断是否为 HTTP 方法 */
    private boolean isHttpMethod(String method) {
        return "get".equalsIgnoreCase(method)
                || "post".equalsIgnoreCase(method)
                || "put".equalsIgnoreCase(method)
                || "delete".equalsIgnoreCase(method)
                || "patch".equalsIgnoreCase(method)
                || "options".equalsIgnoreCase(method)
                || "head".equalsIgnoreCase(method);
    }

    /**
     * 从 Swagger/OpenAPI 文档中提取 base URL
     *
     * @param swagger Swagger/OpenAPI 文档
     * @return base URL，如果没有定义则返回 null
     */
    private String getBaseUrl(JsonNode swagger) {
        // OpenAPI 3.0: servers[0].url
        if (swagger.has("servers")) {
            JsonNode serversNode = swagger.get("servers");
            if (serversNode != null && serversNode.isArray() && serversNode.size() > 0) {
                JsonNode firstServer = serversNode.get(0);
                if (firstServer != null && firstServer.has("url")) {
                    String urlStr = firstServer.path("url").asText(null);
                    if (urlStr != null) {
                        // Remove trailing slash
                        return urlStr.endsWith("/")
                                ? urlStr.substring(0, urlStr.length() - 1)
                                : urlStr;
                    }
                }
            }
        }

        // Swagger 2.0: schemes[0] + host + basePath
        if (swagger.has("host")) {
            String scheme = "http"; // default
            if (swagger.has("schemes")) {
                JsonNode schemesNode = swagger.get("schemes");
                if (schemesNode != null && schemesNode.isArray() && schemesNode.size() > 0) {
                    scheme = schemesNode.get(0).asText("http");
                }
            }
            String host = swagger.path("host").asText(null);
            String basePath = swagger.path("basePath").asText("");
            // Remove trailing slash from basePath
            if (basePath.endsWith("/")) {
                basePath = basePath.substring(0, basePath.length() - 1);
            }
            return scheme + "://" + host + basePath;
        }

        return null;
    }
}
