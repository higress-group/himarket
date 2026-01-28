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

package com.alibaba.himarket.service.gateway.client;

import cn.com.antcloud.api.acapi.AntCloudHttpClient;
import cn.com.antcloud.api.acapi.HttpConfig;
import cn.com.antcloud.api.antcloud.AntCloudClient;
import cn.com.antcloud.api.antcloud.AntCloudClientRequest;
import cn.com.antcloud.api.antcloud.AntCloudClientResponse;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.support.gateway.SofaHigressConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.util.ReflectionUtils;

@Slf4j
public class SofaHigressClient extends GatewayClient {

    private final SofaHigressConfig config;
    private final AntCloudHttpClient antCloudHttpClient;
    private final AntCloudClient antCloudClient;

    private static final String PATH_PREFIX = "/sofa-higress";
    private static final String PATH_SUFFIX = "?queryType=Himarket";
    private static final String OPENAPI_VERSION = "1.0";

    public SofaHigressClient(SofaHigressConfig config) {
        this.config = config;
        this.antCloudHttpClient = createHttpClient();
        this.antCloudClient = createClient();
    }

    @Override
    public void close() {
        if (antCloudHttpClient != null) {
            try {
                antCloudHttpClient.close();
            } catch (IOException e) {
                log.error("Error closing antCloudHttpClient", e);
            }
        }
    }

    /**
     * 执行sofa higress console openapi请求，R为请求类型，T为返回类型
     * @param path
     * @param method
     * @param requestParam
     * @return
     * @param <T> 返回类型
     * @param <R> 请求参数
     */
    public <T, R> T execute(
            String path,
            HttpMethod method,
            R requestParam,
            TypeReference<T> typeReference,
            ObjectMapper objectMapper) {

        String data = execute(path, method, requestParam, objectMapper);
        try {
            return objectMapper.readValue(data, typeReference);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行sofa higress console openapi请求，R为请求类型，T为返回类型
     * @param path
     * @param method
     * @param requestParam
     * @return
     * @param <T> 返回类型
     * @param <R> 请求参数
     */
    public <T, R> T execute(
            String path, HttpMethod method, R requestParam, TypeReference<T> typeReference) {

        String data = execute(path, method, requestParam);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(data, typeReference);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行sofa higress console openapi请求，R为请求类型
     * @param path
     * @param method
     * @param requestParam
     * @return
     * @param <R>
     */
    public <R> String execute(String path, HttpMethod method, R requestParam) {
        return execute(path, method, requestParam, new ObjectMapper());
    }

    /**
     * 执行sofa higress console openapi请求，R为请求类型，T为返回类型
     * @param path
     * @param method
     * @param requestParam
     * @param objectMapper
     * @return
     * @param <R> 请求参数
     */
    public <R> String execute(
            String path, HttpMethod method, R requestParam, ObjectMapper objectMapper) {

        path = PATH_PREFIX + path + PATH_SUFFIX;
        autoSetTenantAndWorkspace(requestParam);
        Map<String, Object> request = new HashMap<>();
        // 请求路径path
        request.put("path", path);

        Map<String, Object> payload = new HashMap<>();
        // 请求参数params
        payload.put("params", requestParam);

        ObjectMapper finalObjectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        try {
            request.put("payload", finalObjectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        AntCloudClientRequest clientRequest = new AntCloudClientRequest();
        clientRequest.putParametersFromObject(request);
        clientRequest.setMethod(method.name());
        clientRequest.setVersion(OPENAPI_VERSION);
        AntCloudClientResponse clientResponse;
        try {
            log.info(
                    "call sofa higress console path: {}, requestParam: {}",
                    path,
                    finalObjectMapper.writeValueAsString(requestParam));
            clientResponse = antCloudClient.execute(clientRequest);
            if (!clientResponse.isSuccess()) {
                log.error(
                        "failed to call sofa higress console path: {}, response: {}, resultCode:"
                                + " {}, resultMsg: {}",
                        path,
                        clientResponse.getData(),
                        clientResponse.getResultCode(),
                        clientResponse.getResultMsg());
                throw new BusinessException(
                        ErrorCode.GATEWAY_ERROR,
                        "Sofa Higress request failed: " + clientResponse.getResultMsg());
            }
            log.info("call sofa higress console {} response:{}", path, clientResponse.getData());
        } catch (Exception e) {
            log.error("call OpenApi error", e);
            throw new RuntimeException("Failed to execute sofa higress request", e);
        }
        // 将返回的结果转为指定类型
        String data = clientResponse.getData();
        if (data == null) {
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Sofa Higress returned null data");
        }
        try {
            HigressResult<String> result =
                    finalObjectMapper.readValue(data, new TypeReference<>() {});
            return result.getData();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * auto set tenantId and workspaceId if present
     */
    private <R> void autoSetTenantAndWorkspace(R requestParam) {
        if (requestParam == null) {
            return;
        }
        String tenantId = config.getTenantId();
        String workspaceId = config.getWorkspaceId();
        Class<?> clazz = requestParam.getClass();
        Field tenantIdField = ReflectionUtils.findField(clazz, "tenantId");
        Field workspaceIdField = ReflectionUtils.findField(clazz, "workspaceId");
        if (tenantIdField != null) {
            ReflectionUtils.makeAccessible(tenantIdField);
            if (tenantId != null) {
                ReflectionUtils.setField(tenantIdField, requestParam, tenantId);
            }
        }
        if (workspaceIdField != null) {
            ReflectionUtils.makeAccessible(workspaceIdField);
            if (workspaceId != null) {
                ReflectionUtils.setField(workspaceIdField, requestParam, workspaceId);
            }
        }
    }

    private AntCloudHttpClient createHttpClient() {
        // 设置readTimeoutMillis，connectionTimeoutMillis，默认20s
        HttpConfig httpConfig = new HttpConfig();
        httpConfig.setReadTimeoutMillis(200000);
        return new AntCloudHttpClient(httpConfig);
    }

    private AntCloudClient createClient() {
        return AntCloudClient.newBuilder()
                // cop-operation地址
                .setEndpoint(config.getAddress() + "/open/api.json")
                // 用户在iam租户下的aksk
                .setAccess(config.getAccessKey(), config.getSecretKey())
                // 是否需要对返回值验签
                .setCheckSign(false)
                // 如果不需要自定义超时时间的话可以不设置
                .setHttpClient(antCloudHttpClient)
                .build();
    }

    @Data
    public static class HigressResult<T> {
        private boolean success = true;
        private T data;
        private String reqMsgId;
        private String resultCode;
        private String resultMsg;
    }
}

