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
import com.alibaba.himarket.core.utils.HashUtils;
import com.alibaba.himarket.support.gateway.AdpAIGatewayConfig;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client for ADP AI gateway operations.
 */
@Slf4j
public class AdpAIGatewayClient extends GatewayClient {

    private final AdpAIGatewayConfig config;
    private final RestTemplate restTemplate;

    public AdpAIGatewayClient(AdpAIGatewayConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Executes an ADP HTTP request.
     */
    public <E> E executeHTTP(Function<HttpEntity<String>, E> function) {
        try {
            HttpEntity<String> requestEntity = createRequestEntity(null);
            return function.apply(requestEntity);
        } catch (Exception e) {
            log.error(
                    "Failed to execute gateway request, dependency=ADP, operation=executeHTTP,"
                            + " errorType={}, errorMessage={}",
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    e,
                    "Failed to execute ADP gateway request: " + e.getMessage());
        }
    }

    /**
     * Creates a request entity with the required authentication headers.
     */
    public HttpEntity<String> createRequestEntity(String body) {
        HttpHeaders headers = buildAuthHeaders();
        if (body == null) {
            return new HttpEntity<>(headers);
        }
        return new HttpEntity<>(body, headers);
    }

    /**
     * Builds the required authentication headers.
     */
    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (config.getAuthSeed() != null && !config.getAuthSeed().trim().isEmpty()) {
            String authHeader = createBasicAuthHeader();
            headers.set("Authorization", authHeader);
        } else if (config.getAuthHeaders() != null && !config.getAuthHeaders().isEmpty()) {
            for (AdpAIGatewayConfig.AuthHeader authHeader : config.getAuthHeaders()) {
                if (authHeader.getKey() != null && authHeader.getValue() != null) {
                    headers.set(authHeader.getKey(), authHeader.getValue());
                }
            }
        } else {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "ADP authentication config is missing. Configure authSeed or authHeaders.");
        }

        return headers;
    }

    /**
     * Creates the Basic authentication header from the configured seed.
     */
    private String createBasicAuthHeader() {
        String hashedAuthSeed = HashUtils.sha256Hex(config.getAuthSeed());
        String credentials = "admin:" + hashedAuthSeed;
        String base64Credentials =
                Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + base64Credentials;
    }

    /**
     * Returns the configured base URL.
     */
    public String getBaseUrl() {
        return config.getBaseUrl();
    }

    /**
     * Returns the configured gateway port.
     */
    public Integer getPort() {
        return config.getPort();
    }

    /**
     * Builds the full request URL for the given path.
     */
    public String getFullUrl(String path) {
        String baseUrl = config.getBaseUrl();

        if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            return String.format("%s:%d%s", baseUrl, config.getPort(), path);
        }

        return String.format("http://%s:%d%s", baseUrl, config.getPort(), path);
    }

    /**
     * Returns the HTTP client used by ADP calls.
     */
    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    @Override
    public void close() {
        // RestTemplate does not hold resources that need explicit cleanup here.
    }
}
