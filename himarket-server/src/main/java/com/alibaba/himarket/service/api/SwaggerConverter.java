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
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.entity.APIEndpoint;
import com.alibaba.himarket.support.api.MCPToolConfig;
import com.alibaba.himarket.support.api.RESTRouteConfig;
import com.alibaba.himarket.support.enums.APIStatus;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.EndpointType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Swagger/OpenAPI 转换器 支持 Swagger 2.0 和 OpenAPI 3.0 规范 */
@Slf4j
@Component
public class SwaggerConverter {

    /**
     * 将 Swagger 文档转换为 API Definition
     *
     * @param swaggerContent Swagger JSON 内容
     * @param name API 名称（可选）
     * @param description API 描述（可选）
     * @param version API 版本（可选）
     * @param type 导入类型（REST 或 MCP）
     * @return API Definition
     */
    public APIDefinition convert(
            String swaggerContent,
            String name,
            String description,
            String version,
            String type) {
        try {
            JSONObject swagger = JSONUtil.parseObj(swaggerContent);

            APIType apiType = APIType.REST_API;
            if ("MCP".equalsIgnoreCase(type)) {
                apiType = APIType.MCP_SERVER;
            }

            // 判断是 Swagger 2.0 还是 OpenAPI 3.0
            String swaggerVersion = swagger.getStr("swagger");
            String openApiVersion = swagger.getStr("openapi");

            if (StrUtil.isNotBlank(swaggerVersion)) {
                return convertSwagger2(swagger, name, description, version, apiType);
            } else if (StrUtil.isNotBlank(openApiVersion)) {
                return convertOpenApi3(swagger, name, description, version, apiType);
            } else {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        "Invalid Swagger/OpenAPI document: missing version field");
            }
        } catch (Exception e) {
            log.error("Failed to convert Swagger document", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to parse Swagger document: " + e.getMessage());
        }
    }

    /** 转换 Swagger 2.0 文档 */
    private APIDefinition convertSwagger2(
            JSONObject swagger,
            String name,
            String description,
            String version,
            APIType type) {
        APIDefinition apiDefinition = new APIDefinition();
        apiDefinition.setApiDefinitionId(UUID.randomUUID().toString());
        apiDefinition.setType(type);
        apiDefinition.setStatus(APIStatus.DRAFT);

        // 提取基本信息
        JSONObject info = swagger.getJSONObject("info");
        if (info != null) {
            apiDefinition.setName(
                    StrUtil.isNotBlank(name) ? name : info.getStr("title", "Imported API"));
            apiDefinition.setDescription(
                    StrUtil.isNotBlank(description) ? description : info.getStr("description"));
            apiDefinition.setVersion(
                    StrUtil.isNotBlank(version) ? version : info.getStr("version", "1.0.0"));
        } else {
            apiDefinition.setName(StrUtil.isNotBlank(name) ? name : "Imported API");
            apiDefinition.setDescription(description);
            apiDefinition.setVersion(StrUtil.isNotBlank(version) ? version : "1.0.0");
        }

        // 提取元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("swagger", "2.0");
        metadata.put("basePath", swagger.getStr("basePath", ""));
        metadata.put("host", swagger.getStr("host", ""));
        metadata.put("schemes", swagger.getJSONArray("schemes"));
        apiDefinition.setMetadata(JSONUtil.toJsonStr(metadata));

        return apiDefinition;
    }

    /** 转换 OpenAPI 3.0 文档 */
    private APIDefinition convertOpenApi3(
            JSONObject openApi,
            String name,
            String description,
            String version,
            APIType type) {
        APIDefinition apiDefinition = new APIDefinition();
        apiDefinition.setApiDefinitionId(UUID.randomUUID().toString());
        apiDefinition.setType(type);
        apiDefinition.setStatus(APIStatus.DRAFT);

        // 提取基本信息
        JSONObject info = openApi.getJSONObject("info");
        if (info != null) {
            apiDefinition.setName(
                    StrUtil.isNotBlank(name) ? name : info.getStr("title", "Imported API"));
            apiDefinition.setDescription(
                    StrUtil.isNotBlank(description) ? description : info.getStr("description"));
            apiDefinition.setVersion(
                    StrUtil.isNotBlank(version) ? version : info.getStr("version", "1.0.0"));
        } else {
            apiDefinition.setName(StrUtil.isNotBlank(name) ? name : "Imported API");
            apiDefinition.setDescription(description);
            apiDefinition.setVersion(StrUtil.isNotBlank(version) ? version : "1.0.0");
        }

        // 提取元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("openapi", openApi.getStr("openapi"));
        metadata.put("servers", openApi.getJSONArray("servers"));
        apiDefinition.setMetadata(JSONUtil.toJsonStr(metadata));

        return apiDefinition;
    }

    /**
     * 转换 Swagger 文档中的 paths 为 Endpoints
     *
     * @param swaggerContent Swagger JSON 内容
     * @param apiDefinitionId API Definition ID
     * @param type API 类型
     * @return Endpoints 列表
     */
    public List<APIEndpoint> convertEndpoints(
            String swaggerContent, String apiDefinitionId, APIType type) {
        try {
            JSONObject swagger = JSONUtil.parseObj(swaggerContent);
            JSONObject paths = swagger.getJSONObject("paths");

            if (paths == null || paths.isEmpty()) {
                return new ArrayList<>();
            }

            List<APIEndpoint> endpoints = new ArrayList<>();
            int sortOrder = 0;

            for (String path : paths.keySet()) {
                JSONObject pathItem = paths.getJSONObject(path);

                // 处理每个 HTTP 方法
                for (String method : pathItem.keySet()) {
                    if (isHttpMethod(method)) {
                        JSONObject operation = pathItem.getJSONObject(method);
                        APIEndpoint endpoint =
                                convertOperation(
                                        path,
                                        method,
                                        operation,
                                        apiDefinitionId,
                                        sortOrder++,
                                        type,
                                        swagger);
                        endpoints.add(endpoint);
                    }
                }
            }

            return endpoints;
        } catch (Exception e) {
            log.error("Failed to convert Swagger endpoints", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to convert Swagger endpoints: " + e.getMessage());
        }
    }

    /** 转换单个操作为 Endpoint */
    private APIEndpoint convertOperation(
            String path,
            String method,
            JSONObject operation,
            String apiDefinitionId,
            int sortOrder,
            APIType type,
            JSONObject swagger) {
        APIEndpoint endpoint = new APIEndpoint();
        endpoint.setEndpointId(UUID.randomUUID().toString());
        endpoint.setApiDefinitionId(apiDefinitionId);
        endpoint.setSortOrder(sortOrder);

        if (type == APIType.MCP_SERVER) {
            endpoint.setType(EndpointType.MCP_TOOL);
            endpoint.setName(generateToolName(path, method, operation));
            endpoint.setDescription(getToolDescription(operation));

            MCPToolConfig config = new MCPToolConfig();

            MCPToolConfig.RequestTemplate requestTemplate = new MCPToolConfig.RequestTemplate();
            requestTemplate.setUrl(path);
            requestTemplate.setMethod(method.toUpperCase());
            config.setRequestTemplate(requestTemplate);

            // 设置 Input Schema
            config.setInputSchema(createInputSchema(operation, swagger));

            // 设置 Output Schema
            config.setOutputSchema(createOutputSchema(operation, swagger));

            // 保存配置
            endpoint.setConfig(JSONUtil.toJsonStr(config));
        } else {
            endpoint.setType(EndpointType.REST_ROUTE);
            // 提取基本信息
            String summary = operation.getStr("summary");
            String description = operation.getStr("description");
            endpoint.setName(
                    StrUtil.isNotBlank(summary) ? summary : method.toUpperCase() + " " + path);
            endpoint.setDescription(description);

            // 构建 REST Route 配置
            RESTRouteConfig config = new RESTRouteConfig();
            config.setPath(path);
            config.setMethod(method.toUpperCase());

            // 提取参数
            List<RESTRouteConfig.Parameter> parameters = new ArrayList<>();
            if (operation.containsKey("parameters")) {
                operation
                        .getJSONArray("parameters")
                        .forEach(
                                param -> {
                                    JSONObject paramObj = (JSONObject) param;
                                    RESTRouteConfig.Parameter parameter =
                                            new RESTRouteConfig.Parameter();
                                    parameter.setName(paramObj.getStr("name"));
                                    parameter.setIn(paramObj.getStr("in"));
                                    parameter.setDescription(paramObj.getStr("description"));
                                    parameter.setRequired(paramObj.getBool("required", false));
                                    parameter.setSchema(paramObj.getJSONObject("schema"));
                                    parameters.add(parameter);
                                });
            }
            config.setParameters(parameters);

            // 提取响应
            Map<String, RESTRouteConfig.ResponseDef> responses = new HashMap<>();
            if (operation.containsKey("responses")) {
                JSONObject responsesObj = operation.getJSONObject("responses");
                responsesObj.forEach(
                        (code, response) -> {
                            RESTRouteConfig.ResponseDef resp = new RESTRouteConfig.ResponseDef();
                            JSONObject respObj = (JSONObject) response;
                            resp.setDescription(respObj.getStr("description"));
                            resp.setSchema(respObj.getJSONObject("schema"));
                            responses.put(code, resp);
                        });
            }
            config.setResponses(responses);

            // 保存配置
            endpoint.setConfig(JSONUtil.toJsonStr(config));
        }

        return endpoint;
    }

    /** 生成 MCP Tool 名称 */
    private String generateToolName(String path, String method, JSONObject operation) {
        String operationId = operation.getStr("operationId");
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
    private String getToolDescription(JSONObject operation) {
        String summary = operation.getStr("summary");
        String description = operation.getStr("description");

        if (StrUtil.isNotBlank(summary)) {
            if (StrUtil.isNotBlank(description)) {
                return summary + " - " + description;
            }
            return summary;
        }
        return description;
    }

    /** 创建 MCP Input Schema */
    private Map<String, Object> createInputSchema(JSONObject operation, JSONObject swagger) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        // 处理参数
        if (operation.containsKey("parameters")) {
            operation
                    .getJSONArray("parameters")
                    .forEach(
                            param -> {
                                JSONObject paramObj = (JSONObject) param;
                                // 处理参数引用
                                if (paramObj.containsKey("$ref")) {
                                    paramObj = resolveRef(paramObj.getStr("$ref"), swagger);
                                }
                                if (paramObj == null) return;

                                String name = paramObj.getStr("name");
                                JSONObject paramSchema = paramObj.getJSONObject("schema");
                                if (paramSchema != null) {
                                    // 复制 schema 属性
                                    properties.put(name, resolveSchema(paramSchema, swagger));
                                } else {
                                    // 如果没有 schema，尝试直接从参数定义中获取类型信息 (Swagger 2.0)
                                    Map<String, Object> prop = new HashMap<>();
                                    prop.put("type", paramObj.getStr("type", "string"));
                                    prop.put("description", paramObj.getStr("description"));
                                    properties.put(name, prop);
                                }

                                if (paramObj.getBool("required", false)) {
                                    required.add(name);
                                }
                            });
        }

        // 处理 Request Body (OpenAPI 3.0)
        if (operation.containsKey("requestBody")) {
            JSONObject requestBody = operation.getJSONObject("requestBody");
            // 处理 Request Body 引用
            if (requestBody.containsKey("$ref")) {
                requestBody = resolveRef(requestBody.getStr("$ref"), swagger);
            }

            if (requestBody != null) {
                JSONObject content = requestBody.getJSONObject("content");
                if (content != null) {
                    // 优先处理 application/json
                    JSONObject jsonContent = content.getJSONObject("application/json");
                    if (jsonContent != null && jsonContent.containsKey("schema")) {
                        JSONObject bodySchema =
                                resolveSchema(jsonContent.getJSONObject("schema"), swagger);
                        // 如果 body 是 object，将其属性合并到 properties
                        if ("object".equals(bodySchema.get("type"))) {
                            if (bodySchema.containsKey("properties")) {
                                JSONObject bodyProps =
                                        (JSONObject) bodySchema.get("properties");
                                bodyProps.forEach((k, v) -> properties.put(k, v));
                            }
                            if (bodySchema.containsKey("required")) {
                                ((List<?>) bodySchema.get("required"))
                                        .forEach(r -> required.add(r.toString()));
                            }
                        } else {
                            // 如果 body 不是 object (例如 array)，作为一个名为 body 的参数
                            properties.put("body", bodySchema);
                            if (requestBody.getBool("required", false)) {
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
        return schema;
    }

    /** 创建 MCP Output Schema */
    private Map<String, Object> createOutputSchema(JSONObject operation, JSONObject swagger) {
        if (!operation.containsKey("responses")) {
            return null;
        }
        JSONObject responses = operation.getJSONObject("responses");

        // 查找 2xx 响应
        for (String code : responses.keySet()) {
            if (code.startsWith("2")) {
                JSONObject response = responses.getJSONObject(code);
                // 处理 Response 引用
                if (response.containsKey("$ref")) {
                    response = resolveRef(response.getStr("$ref"), swagger);
                }
                if (response == null) continue;

                if (response.containsKey("content")) {
                    JSONObject content = response.getJSONObject("content");
                    // 优先处理 application/json
                    JSONObject jsonContent = content.getJSONObject("application/json");
                    if (jsonContent != null && jsonContent.containsKey("schema")) {
                        return resolveSchema(jsonContent.getJSONObject("schema"), swagger);
                    }
                    // 尝试其他 content type
                    for (String contentType : content.keySet()) {
                        JSONObject typeContent = content.getJSONObject(contentType);
                        if (typeContent != null && typeContent.containsKey("schema")) {
                            return resolveSchema(typeContent.getJSONObject("schema"), swagger);
                        }
                    }
                } else if (response.containsKey("schema")) {
                    // Swagger 2.0
                    return resolveSchema(response.getJSONObject("schema"), swagger);
                }
            }
        }
        return null;
    }

    /** 解析 Schema 中的引用 */
    private JSONObject resolveSchema(JSONObject schema, JSONObject swagger) {
        if (schema == null) return null;
        
        // 如果是引用，解析引用
        if (schema.containsKey("$ref")) {
            JSONObject resolved = resolveRef(schema.getStr("$ref"), swagger);
            if (resolved != null) {
                // 递归解析引用的 schema
                return resolveSchema(resolved, swagger);
            }
            return schema; // 无法解析，返回原样
        }

        // 递归处理 properties
        if (schema.containsKey("properties")) {
            JSONObject properties = schema.getJSONObject("properties");
            JSONObject newProperties = new JSONObject();
            properties.forEach((k, v) -> {
                if (v instanceof JSONObject) {
                    newProperties.put(k, resolveSchema((JSONObject) v, swagger));
                } else {
                    newProperties.put(k, v);
                }
            });
            schema.put("properties", newProperties);
        }

        // 递归处理 items (数组)
        if (schema.containsKey("items")) {
            JSONObject items = schema.getJSONObject("items");
            schema.put("items", resolveSchema(items, swagger));
        }
        
        // 递归处理 allOf, anyOf, oneOf
        String[] combinators = {"allOf", "anyOf", "oneOf"};
        for (String combinator : combinators) {
            if (schema.containsKey(combinator)) {
                List<Object> list = schema.getJSONArray(combinator);
                List<Object> newList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof JSONObject) {
                        newList.add(resolveSchema((JSONObject) item, swagger));
                    } else {
                        newList.add(item);
                    }
                }
                schema.put(combinator, newList);
            }
        }

        return schema;
    }

    /** 解析引用路径 */
    private JSONObject resolveRef(String ref, JSONObject swagger) {
        if (StrUtil.isBlank(ref) || !ref.startsWith("#/")) {
            return null;
        }

        try {
            String[] parts = ref.substring(2).split("/");
            JSONObject current = swagger;
            for (String part : parts) {
                // 处理转义字符: ~1 -> /, ~0 -> ~
                part = part.replace("~1", "/").replace("~0", "~");
                if (current.containsKey(part)) {
                    Object val = current.get(part);
                    if (val instanceof JSONObject) {
                        current = (JSONObject) val;
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
            // 复制一份，避免修改原对象
            return JSONUtil.parseObj(JSONUtil.toJsonStr(current));
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
}
