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
import cn.com.antcloud.api.antcloud.AntCloudClientRequest;
import cn.com.antcloud.api.antcloud.AntCloudClientResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.himarket.support.gateway.SofaHigressConfig;
import cn.com.antcloud.api.antcloud.AntCloudClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class SofaHigressClient extends GatewayClient {

    private final SofaHigressConfig config;
    private final AntCloudHttpClient antCloudHttpClient;
    private final AntCloudClient antCloudClient;

    private final static String PATH_PREFIX = "/sofa-higress";
    private final static String PATH_SUFFIX = "?queryType=Himarket";
    private final static String OPENAPI_VERSION = "1.0";

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
    public <T, R> T execute(String path,
                            HttpMethod method,
                            R requestParam,
                            TypeReference<T> typeReference) {

        String data = execute(path, method, requestParam);
        return JSONObject.parseObject(data, typeReference);
    }

    /**
     * 执行sofa higress console openapi请求，R为请求类型，T为返回类型
     * @param path
     * @param method
     * @param requestParam
     * @return
     * @param <R> 请求参数
     */
    public <R> String execute(String path,
                            HttpMethod method,
                            R requestParam) {

        path = PATH_PREFIX + path + PATH_SUFFIX;
        Map<String, Object> request = new HashMap<>();
        // 请求路径path
        request.put("path", path);
        JSONObject payload = new JSONObject();
        // 请求参数params
        payload.put("params", requestParam);
        request.put("payload", JSON.toJSONString(payload));
        AntCloudClientRequest clientRequest = new AntCloudClientRequest();
        clientRequest.putParametersFromObject(request);
        clientRequest.setMethod(method.name());
        clientRequest.setVersion(OPENAPI_VERSION);
        AntCloudClientResponse clientResponse;
        try {
            log.debug("call sofa higress console request: {}", JSON.toJSONString(request));
            clientResponse = antCloudClient.execute(clientRequest);
            if (!clientResponse.isSuccess()) {
                log.error("failed to call sofa higress console: {}", clientResponse.getData());
                throw new RuntimeException("return response is null or not success");
            }
            log.debug("call sofa higress console {} response:{}", path, clientResponse.getData());
        } catch (Exception e) {
            log.error("call OpenApi error", e);
            throw new RuntimeException("Failed to execute sofa higress request", e);
        }
        // 将返回的结果转为指定类型
        String data = clientResponse.getData();
        HigressResult<String> result = JSONObject.parseObject(data, new TypeReference<>() {});
        return result.getData();
    }

    private AntCloudHttpClient createHttpClient() {
        //设置readTimeoutMillis，connectionTimeoutMillis，默认20s
        HttpConfig httpConfig = new HttpConfig();
        httpConfig.setReadTimeoutMillis(200000);
        return new AntCloudHttpClient(httpConfig);
    }

    private AntCloudClient createClient() {
        AntCloudClient antCloudClient = AntCloudClient.newBuilder()
                //cop-operation地址
                .setEndpoint(config.getAddress()+"/open/api.json")
                //用户在iam租户下的aksk
                .setAccess(config.getAccessKey(), config.getSecretKey())
                //是否需要对返回值验签
                .setCheckSign(false)
                //如果不需要自定义超时时间的话可以不设置
                .setHttpClient(antCloudHttpClient)
                .build();
        return antCloudClient;
    }

    @Data
    public static class HigressResult<T> {

        /**
         * 是否成功
         */
        private boolean success = true;
        /**
         * 数据
         */
        private T       data;
        /**
         * 请求ID
         */
        private String  reqMsgId;
        /**
         * 响应编码
         */
        private String  resultCode;
        /**
         * 响应说明
         */
        private String  resultMsg;
    }
}
