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

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.support.gateway.APIGConfig;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PopGatewayClient extends GatewayClient {

    private final APIGConfig config;

    private final IAcsClient client;

    public PopGatewayClient(APIGConfig config) {
        this.config = config;
        this.client = createClient(config);
    }

    private IAcsClient createClient(APIGConfig config) {
        DefaultProfile profile =
                DefaultProfile.getProfile(
                        config.getRegion(), config.getAccessKey(), config.getSecretKey());
        return new DefaultAcsClient(profile);
    }

    @Override
    public void close() {
        client.shutdown();
    }

    public <E> E execute(
            String uri,
            MethodType methodType,
            Map<String, String> queryParams,
            Function<JSONObject, E> converter) {

        // CommonRequest
        CommonRequest request = new CommonRequest();
        request.setSysProtocol(ProtocolType.HTTPS);
        request.setSysDomain(getAPIGEndpoint(config.getRegion()));
        request.setSysVersion("2024-03-27");
        request.setSysUriPattern(uri);
        request.setSysMethod(methodType);

        // Query Parameters
        if (queryParams != null) {
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                request.putQueryParameter(entry.getKey(), entry.getValue());
            }
        }

        try {
            CommonResponse response = client.getCommonResponse(request);
            JSONObject data = JSONUtil.parseObj(response.getData()).getJSONObject("data");

            return converter.apply(data);
        } catch (ClientException e) {
            log.error("Error executing Pop request", e);
            throw new RuntimeException(e);
        }
    }
}
