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


package com.alibaba.apiopenplatform.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.alibaba.apiopenplatform.core.constant.CommonConstants;
import com.alibaba.apiopenplatform.core.constant.IdpConstants;
import com.alibaba.apiopenplatform.core.constant.Resources;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.core.utils.TokenUtil;
import com.alibaba.apiopenplatform.dto.params.developer.CreateExternalDeveloperParam;
import com.alibaba.apiopenplatform.dto.result.common.AuthResult;
import com.alibaba.apiopenplatform.dto.result.developer.DeveloperResult;
import com.alibaba.apiopenplatform.dto.result.idp.IdpResult;
import com.alibaba.apiopenplatform.dto.result.idp.IdpState;
import com.alibaba.apiopenplatform.dto.result.idp.IdpTokenResult;
import com.alibaba.apiopenplatform.service.OidcService;
import com.alibaba.apiopenplatform.service.DeveloperService;
import com.alibaba.apiopenplatform.service.PortalService;
import com.alibaba.apiopenplatform.support.enums.DeveloperAuthType;
import com.alibaba.apiopenplatform.support.enums.GrantType;
import com.alibaba.apiopenplatform.support.portal.AuthCodeConfig;
import com.alibaba.apiopenplatform.support.portal.IdentityMapping;
import com.alibaba.apiopenplatform.support.portal.OidcConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OidcServiceImpl implements OidcService {

    private final PortalService portalService;

    private final DeveloperService developerService;

    private final RestTemplate restTemplate;

    private final ContextHolder contextHolder;

    @Override
    public String buildAuthorizationUrl(String provider, String apiPrefix, HttpServletRequest request) {
        OidcConfig oidcConfig = findOidcConfig(provider);
        AuthCodeConfig authCodeConfig = oidcConfig.getAuthCodeConfig();

        // state保存上下文信息
        String state = buildState(provider, apiPrefix);
        String redirectUri = buildRedirectUri(request);

        // 重定向URL
        String authUrl = UriComponentsBuilder
                .fromUriString(authCodeConfig.getAuthorizationEndpoint())
                // 授权码模式
                .queryParam(IdpConstants.RESPONSE_TYPE, IdpConstants.CODE)
                .queryParam(IdpConstants.CLIENT_ID, authCodeConfig.getClientId())
                .queryParam(IdpConstants.REDIRECT_URI, redirectUri)
                .queryParam(IdpConstants.SCOPE, authCodeConfig.getScopes())
                .queryParam(IdpConstants.STATE, state)
                .build()
                .toUriString();

        log.info("Generated OIDC authorization URL: {}", authUrl);
        return authUrl;
    }

    @Override
    public AuthResult handleCallback(String code, String state, HttpServletRequest request, HttpServletResponse response) {
        log.info("Processing OIDC callback with code: {}, state: {}", code, state);

        // 解析state获取provider信息
        IdpState idpState = parseState(state);
        String provider = idpState.getProvider();

        if (StrUtil.isBlank(provider)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "缺少OIDC provider");
        }

        OidcConfig oidcConfig = findOidcConfig(provider);

        // 使用授权码获取Token
        IdpTokenResult tokenResult = requestToken(code, oidcConfig, request);

        // 获取用户信息，优先使用ID Token，降级到UserInfo端点
        Map<String, Object> userInfo = getUserInfo(tokenResult, oidcConfig);
        log.info("Get OIDC user info: {}", userInfo);

        // 处理用户认证逻辑
        String developerId = createOrGetDeveloper(userInfo, oidcConfig);
        String accessToken = TokenUtil.generateDeveloperToken(developerId);

        return AuthResult.of(accessToken, TokenUtil.getTokenExpiresIn());
    }

    @Override
    public List<IdpResult> getAvailableProviders() {
        return Optional.ofNullable(portalService.getPortal(contextHolder.getPortal()))
                .filter(portal -> portal.getPortalSettingConfig() != null)
                .filter(portal -> portal.getPortalSettingConfig().getOidcConfigs() != null)
                .map(portal -> portal.getPortalSettingConfig().getOidcConfigs())
                // 确定当前Portal下启用的OIDC配置，返回Idp信息
                .map(configs -> configs.stream()
                        .filter(OidcConfig::isEnabled)
                        .map(config -> IdpResult.builder()
                                .provider(config.getProvider())
                                .displayName(config.getName())
                                .build())
                        .collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    private String buildRedirectUri(HttpServletRequest request) {
        String scheme = request.getScheme();
//        String serverName = "localhost";
//        int serverPort = 5173;
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        String baseUrl = scheme + "://" + serverName;
        if (serverPort != CommonConstants.HTTP_PORT && serverPort != CommonConstants.HTTPS_PORT) {
            baseUrl += ":" + serverPort;
        }

        // 重定向到前端的Callback接口
        return baseUrl + "/oidc/callback";
    }

    private OidcConfig findOidcConfig(String provider) {
        return Optional.ofNullable(portalService.getPortal(contextHolder.getPortal()))
                .filter(portal -> portal.getPortalSettingConfig() != null)
                .filter(portal -> portal.getPortalSettingConfig().getOidcConfigs() != null)
                // 根据provider字段过滤
                .flatMap(portal -> portal.getPortalSettingConfig()
                        .getOidcConfigs()
                        .stream()
                        .filter(config -> provider.equals(config.getProvider()) && config.isEnabled())
                        .findFirst())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.OIDC_CONFIG, provider));
    }

    private String buildState(String provider, String apiPrefix) {
        IdpState state = IdpState.builder()
                .provider(provider)
                .timestamp(System.currentTimeMillis())
                .nonce(IdUtil.fastSimpleUUID())
                .apiPrefix(apiPrefix)
                .build();
        return Base64.encode(JSONUtil.toJsonStr(state));
    }

    private IdpState parseState(String encodedState) {
        String stateJson = Base64.decodeStr(encodedState);
        IdpState idpState = JSONUtil.toBean(stateJson, IdpState.class);

        // 验证时间戳，10分钟有效期
        if (idpState.getTimestamp() != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - idpState.getTimestamp() > 10 * 60 * 1000) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "请求已过期");
            }
        }

        return idpState;
    }

    private IdpTokenResult requestToken(String code, OidcConfig oidcConfig, HttpServletRequest request) {
        AuthCodeConfig authCodeConfig = oidcConfig.getAuthCodeConfig();
        String redirectUri = buildRedirectUri(request);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(IdpConstants.GRANT_TYPE, GrantType.AUTHORIZATION_CODE.getType());
        params.add(IdpConstants.CODE, code);
        params.add(IdpConstants.REDIRECT_URI, redirectUri);
        params.add(IdpConstants.CLIENT_ID, authCodeConfig.getClientId());
        params.add(IdpConstants.CLIENT_SECRET, authCodeConfig.getClientSecret());

        log.info("Request tokens at: {}, params: {}", authCodeConfig.getTokenEndpoint(), params);
        return executeRequest(authCodeConfig.getTokenEndpoint(), HttpMethod.POST, null, params, IdpTokenResult.class);
    }

    private Map<String, Object> getUserInfo(IdpTokenResult tokenResult, OidcConfig oidcConfig) {
        // 优先使用ID Token
        if (StrUtil.isNotBlank(tokenResult.getIdToken())) {
            log.info("Get user info form id token: {}", tokenResult.getIdToken());
            return parseUserInfo(tokenResult.getIdToken(), oidcConfig);
        }

        // 降级策略：使用UserInfo端点
        log.warn("ID Token not available, falling back to UserInfo endpoint");
        if (StrUtil.isBlank(tokenResult.getAccessToken())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "OIDC获取用户信息失败");
        }

        AuthCodeConfig authCodeConfig = oidcConfig.getAuthCodeConfig();
        if (StrUtil.isBlank(authCodeConfig.getUserInfoEndpoint())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "OIDC配置缺少用户信息端点");
        }

        return requestUserInfo(tokenResult.getAccessToken(), authCodeConfig, oidcConfig);
    }

    private Map<String, Object> parseUserInfo(String idToken, OidcConfig oidcConfig) {
        JWT jwt = JWTUtil.parseToken(idToken);

        // 验证过期时间
        Object exp = jwt.getPayload("exp");
        if (exp != null) {
            long expTime = Convert.toLong(exp);
            long currentTime = System.currentTimeMillis() / 1000;
            if (expTime <= currentTime) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "ID Token已过期");
            }
        }
        // TODO 验签

        Map<String, Object> userInfo = jwt.getPayload().getClaimsJson();

        log.info("Successfully extracted user info from ID Token, sub: {}", userInfo);
        return userInfo;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestUserInfo(String accessToken, AuthCodeConfig authCodeConfig, OidcConfig oidcConfig) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            log.info("Fetching user info from endpoint: {}", authCodeConfig.getUserInfoEndpoint());
            Map<String, Object> userInfo = executeRequest(authCodeConfig.getUserInfoEndpoint(), HttpMethod.GET, headers, null, Map.class);

            log.info("Successfully fetched user info from endpoint, sub: {}", userInfo);
            return userInfo;
        } catch (Exception e) {
            log.error("Failed to fetch user info from endpoint: {}", authCodeConfig.getUserInfoEndpoint(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取用户信息失败");
        }
    }

    private String createOrGetDeveloper(Map<String, Object> userInfo, OidcConfig config) {
        IdentityMapping identityMapping = config.getIdentityMapping();
        // userId & userName & email
        String userIdField = StrUtil.isBlank(identityMapping.getUserIdField()) ?
                IdpConstants.SUBJECT : identityMapping.getUserIdField();
        String userNameField = StrUtil.isBlank(identityMapping.getUserNameField()) ?
                IdpConstants.NAME : identityMapping.getUserNameField();
        String emailField = StrUtil.isBlank(identityMapping.getEmailField()) ?
                IdpConstants.EMAIL : identityMapping.getEmailField();

        Object userIdObj = userInfo.get(userIdField);
        Object userNameObj = userInfo.get(userNameField);
        Object emailObj = userInfo.get(emailField);

        String userId = Convert.toStr(userIdObj);
        String userName = Convert.toStr(userNameObj);
        String email = Convert.toStr(emailObj);
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(userName)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Id Token中缺少用户ID字段或用户名称");
        }

        // 复用已有的Developer，否则创建
        return Optional.ofNullable(developerService.getExternalDeveloper(config.getProvider(), userId))
                .map(DeveloperResult::getDeveloperId)
                .orElseGet(() -> {
                    CreateExternalDeveloperParam param = CreateExternalDeveloperParam.builder()
                            .provider(config.getProvider())
                            .subject(userId)
                            .displayName(userName)
                            .email(email)
                            .authType(DeveloperAuthType.OIDC)
                            .build();

                    return developerService.createExternalDeveloper(param).getDeveloperId();
                });
    }

    private <T> T executeRequest(String url, HttpMethod method, HttpHeaders headers, Object body, Class<T> responseType) {
        HttpEntity<?> requestEntity = new HttpEntity<>(body, headers);
        log.info("Executing HTTP request to: {}", url);
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                method,
                requestEntity,
                String.class
        );

        log.info("Received HTTP response from: {}, status: {}, body: {}", url, response.getStatusCode(), response.getBody());

        return JSONUtil.toBean(response.getBody(), responseType);
    }
}
