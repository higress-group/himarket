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

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.support.gateway.APIGConfig;
import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.sdk.service.apig20240327.AsyncClient;
import darabonba.core.client.ClientOverrideConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

@Slf4j
public class APIGClient extends GatewayClient {

    private final AsyncClient apigClient;

    public APIGClient(APIGConfig config) {
        this.apigClient = createClient(config);
    }

    @Override
    public void close() {
        if (apigClient != null) {
            apigClient.close();
        }
    }

    public <E> E execute(Function<AsyncClient, E> function) {
        try {
            return function.apply(apigClient);
        } catch (Exception e) {
            log.error("Error executing APIG request", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    private AsyncClient createClient(APIGConfig config) {
        // noinspection AklessInspection
        StaticCredentialProvider provider = StaticCredentialProvider.create(Credential.builder()
                .accessKeyId(config.getAccessKey())
                .accessKeySecret(config.getSecretKey())
                .build());

        return AsyncClient.builder()
                .credentialsProvider(provider)
                .overrideConfiguration(
                        ClientOverrideConfiguration.create()
                                .setEndpointOverride(getAPIGEndpoint(config.getRegion()))
                ).build();
    }

}