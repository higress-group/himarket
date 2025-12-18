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
     * @return API Definition
     */
    public APIDefinition convert(
            String swaggerContent, String name, String description, String version) {
        try {
            JSONObject swagger = JSONUtil.parseObj(swaggerContent);

            // 判断是 Swagger 2.0 还是 OpenAPI 3.0
            String swaggerVersion = swagger.getStr("swagger");
            String openApiVersion = swagger.getStr("openapi");

            if (StrUtil.isNotBlank(swaggerVersion)) {
                return convertSwagger2(swagger, name, description, version);
            } else if (StrUtil.isNotBlank(openApiVersion)) {
                return convertOpenApi3(swagger, name, description, version);
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
            JSONObject swagger, String name, String description, String version) {
        APIDefinition apiDefinition = new APIDefinition();
        apiDefinition.setApiDefinitionId(UUID.randomUUID().toString());
        apiDefinition.setType(APIType.REST_API);
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
            JSONObject openApi, String name, String description, String version) {
        APIDefinition apiDefinition = new APIDefinition();
        apiDefinition.setApiDefinitionId(UUID.randomUUID().toString());
        apiDefinition.setType(APIType.REST_API);
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
     * @return Endpoints 列表
     */
    public List<APIEndpoint> convertEndpoints(String swaggerContent, String apiDefinitionId) {
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
                                        path, method, operation, apiDefinitionId, sortOrder++);
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
            int sortOrder) {
        APIEndpoint endpoint = new APIEndpoint();
        endpoint.setEndpointId(UUID.randomUUID().toString());
        endpoint.setApiDefinitionId(apiDefinitionId);
        endpoint.setType(EndpointType.REST_ROUTE);
        endpoint.setSortOrder(sortOrder);

        // 提取基本信息
        String summary = operation.getStr("summary");
        String description = operation.getStr("description");
        endpoint.setName(StrUtil.isNotBlank(summary) ? summary : method.toUpperCase() + " " + path);
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

        return endpoint;
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
