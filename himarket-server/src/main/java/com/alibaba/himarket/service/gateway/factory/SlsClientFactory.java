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

package com.alibaba.himarket.service.gateway.factory;

import com.alibaba.himarket.config.SlsConfig;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.support.enums.SlsAuthType;
import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.auth.Credentials;
import com.aliyun.openservices.log.common.auth.DefaultCredentials;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Factory for creating SLS clients from the configured authentication mode.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SlsClientFactory {

    private final SlsConfig slsConfig;

    /**
     * STS client cache keyed by user ID. Client creation is relatively expensive, so clients are
     * reused for a short window.
     */
    private final Cache<String, Client> stsClientCache =
            Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(25, TimeUnit.MINUTES).build();

    /**
     * Creates an SLS client from the configured authentication type.
     *
     * @param userId user ID required when authType is STS
     * @return SLS client
     */
    public Client createClient(String userId) {
        SlsAuthType authType = slsConfig.getAuthType();
        if (authType == SlsAuthType.STS) {
            return createClientWithSts(userId);
        } else {
            return createClientWithAkSk();
        }
    }

    /**
     * Creates an SLS client with STS credentials.
     *
     * @param userId user ID used to load STS credentials
     * @return SLS client
     */
    private Client createClientWithSts(String userId) {
        throw new UnsupportedOperationException("STS authentication is not supported");
    }

    /**
     * Creates an SLS client with the configured AK/SK credentials.
     *
     * @return SLS client
     */
    private Client createClientWithAkSk() {
        String accessKeyId = slsConfig.getAccessKeyId();
        String accessKeySecret = slsConfig.getAccessKeySecret();

        if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "AccessKeyId and AccessKeySecret must be configured in application.yaml when"
                            + " authType is AK_SK");
        }

        String endpoint = getEffectiveEndpoint();

        try {
            Credentials credentials = new DefaultCredentials(accessKeyId, accessKeySecret);
            log.debug("Creating SLS client, dependency=SLS, authType=AK_SK, endpoint={}", endpoint);
            return new Client(endpoint, credentials, null);
        } catch (Exception e) {
            log.error(
                    "Failed to create SLS client, dependency=SLS, authType=AK_SK,"
                            + " errorType={}, errorMessage={}",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to create SLS client with AK/SK");
        }
    }

    /**
     * Returns the configured SLS endpoint.
     *
     * @return SLS endpoint
     */
    private String getEffectiveEndpoint() {
        String endpoint = slsConfig.getEndpoint();
        if (!StringUtils.hasText(endpoint)) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "SLS endpoint must be configured in application.yml");
        }
        return endpoint;
    }
}
