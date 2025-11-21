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

package com.alibaba.apiopenplatform.service.gateway.client;

import cn.hutool.core.map.MapBuilder;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.service.gateway.factory.HTTPClientFactory;
import com.alibaba.apiopenplatform.support.gateway.HigressConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
public class HigressClient extends GatewayClient {

    private static final String HIGRESS_COOKIE_NAME = "_hi_sess";

    private final RestTemplate restTemplate;
    private final HigressConfig config;
    private String higressToken;
    private final ThreadLocal<Boolean> isRetrying = new ThreadLocal<>();

    public HigressClient(HigressConfig higressConfig) {
        this.config = higressConfig;
        this.restTemplate = HTTPClientFactory.createRestTemplate();
    }

    public <T, R> T execute(String path,
                            HttpMethod method,
                            Map<String, String> queryParams,
                            R body,
                            ParameterizedTypeReference<T> responseType) {
        return execute(path, method, null, queryParams, body, responseType);
    }

    public <T, R> T execute(String path,
                            HttpMethod method,
                            Map<String, String> queryParams,
                            R body,
                            Class<T> responseType) {
        return execute(path, method, queryParams, body,
                ParameterizedTypeReference.forType(responseType));
    }

    public <T, R> T execute(String path,
                            HttpMethod method,
                            HttpHeaders headers,
                            Map<String, String> queryParams,
                            R body,
                            ParameterizedTypeReference<T> responseType) {
        try {
            return doExecute(path, method, headers, queryParams, body, responseType);
        } finally {
            isRetrying.remove();
        }
    }

    private <T, R> T doExecute(String path,
                               HttpMethod method,
                               HttpHeaders headers,
                               Map<String, String> queryParams,
                               R body,
                               ParameterizedTypeReference<T> responseType) {
        try {
            ensureConsoleToken();

            // 构建URL
            String url = buildUrlWithParams(path, queryParams);

            // Headers
            HttpHeaders mergedHeaders = new HttpHeaders();
            if (headers != null) {
                mergedHeaders.putAll(headers);
            }
            mergedHeaders.add("Cookie", HIGRESS_COOKIE_NAME + "=" + higressToken);

            ResponseEntity<T> response = restTemplate.exchange(
                    url,
                    method,
                    new HttpEntity<>(body, mergedHeaders),
                    responseType
            );

            log.info("Higress response: status={}, body={}",
                    response.getStatusCode(), JSONUtil.toJsonStr(response.getBody()));

            return response.getBody();
        } catch (HttpClientErrorException e) {
            // 401重新登录，且只重试一次
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED
                    && !Boolean.TRUE.equals(isRetrying.get())) {
                log.warn("Token expired, trying to relogin");
                higressToken = null;
                isRetrying.set(true);
                return doExecute(path, method, headers, queryParams, body, responseType);
            }
            log.error("HTTP error executing Higress request: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            log.error("Error executing Higress request: {}", e.getMessage());
            throw new RuntimeException("Failed to execute Higress request", e);
        }
    }

    private String buildUrlWithParams(String path, Map<String, String> queryParams) {
        StringBuilder url = new StringBuilder(buildUrl(path));

        if (queryParams != null && !queryParams.isEmpty()) {
            url.append('?');
            queryParams.forEach((key, value) -> {
                if (url.charAt(url.length() - 1) != '?') {
                    url.append('&');
                }
                url.append(key).append('=').append(value);
            });
        }

        return url.toString();
    }

    private String buildUrl(String path) {
        String baseUrl = config.getAddress();

        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        path = path.startsWith("/") ? path : "/" + path;
        return baseUrl + path;
    }

    private void ensureConsoleToken() {
        if (higressToken == null) {
            login();
        }
    }

    private void login() {
        Map<Object, Object> loginParam = MapBuilder.create()
                .put("username", config.getUsername())
                .put("password", config.getPassword())
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                buildUrl("/session/login"),
                HttpMethod.POST,
                new HttpEntity<>(loginParam, headers),
                String.class
        );

        List<String> cookies = response.getHeaders().get("Set-Cookie");
        if (cookies == null || cookies.isEmpty()) {
            throw new RuntimeException("No cookies received from server");
        }

        this.higressToken = cookies.stream()
                .filter(cookie -> cookie.startsWith(HIGRESS_COOKIE_NAME + "="))
                .findFirst()
                .map(cookie -> {
                    int endIndex = cookie.indexOf(';');
                    return endIndex == -1
                            ? cookie.substring(HIGRESS_COOKIE_NAME.length() + 1)
                            : cookie.substring(HIGRESS_COOKIE_NAME.length() + 1, endIndex);
                })
                .orElseThrow(() -> new RuntimeException("Failed to get Higress session token"));
    }

    @Override
    public void close() {
        HTTPClientFactory.closeClient(restTemplate);
    }
}