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

package com.alibaba.himarket.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.constant.IdpConstants;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.service.IdpService;
import com.alibaba.himarket.support.enums.GrantType;
import com.alibaba.himarket.support.enums.PublicKeyFormat;
import com.alibaba.himarket.support.portal.*;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdpServiceImpl implements IdpService {

    private final RestTemplate restTemplate;

    @Override
    public void validateOidcConfigs(List<OidcConfig> oidcConfigs) {
        if (CollUtil.isEmpty(oidcConfigs)) {
            return;
        }

        // provider唯一
        Set<String> providers =
                oidcConfigs.stream()
                        .map(OidcConfig::getProvider)
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.toSet());
        if (providers.size() != oidcConfigs.size()) {
            throw new BusinessException(ErrorCode.CONFLICT, "OIDC配置中存在空或重复的provider");
        }

        oidcConfigs.forEach(
                config -> {
                    AuthCodeConfig authConfig =
                            Optional.ofNullable(config.getAuthCodeConfig())
                                    .orElseThrow(
                                            () ->
                                                    new BusinessException(
                                                            ErrorCode.INVALID_PARAMETER,
                                                            StrUtil.format(
                                                                    "OIDC配置{}缺少授权码配置",
                                                                    config.getProvider())));
                    // 基础参数
                    if (StrUtil.isBlank(authConfig.getClientId())
                            || StrUtil.isBlank(authConfig.getClientSecret())
                            || StrUtil.isBlank(authConfig.getScopes())) {
                        throw new BusinessException(
                                ErrorCode.INVALID_PARAMETER,
                                StrUtil.format(
                                        "OIDC配置{}缺少必要参数: Client ID, Client Secret 或 Scopes",
                                        config.getProvider()));
                    }

                    // 端点配置
                    if (StrUtil.isNotBlank(authConfig.getIssuer())) {
                        discoverAndSetEndpoints(config.getProvider(), authConfig);
                    } else {
                        if (StrUtil.isBlank(authConfig.getAuthorizationEndpoint())
                                || StrUtil.isBlank(authConfig.getTokenEndpoint())
                                || StrUtil.isBlank(authConfig.getUserInfoEndpoint())) {
                            throw new BusinessException(
                                    ErrorCode.INVALID_PARAMETER,
                                    StrUtil.format("OIDC配置{}缺少必要端点配置", config.getProvider()));
                        }
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void discoverAndSetEndpoints(String provider, AuthCodeConfig config) {
        String discoveryUrl =
                config.getIssuer().replaceAll("/$", "") + "/.well-known/openid-configuration";
        try {
            Map<String, Object> discovery =
                    restTemplate.exchange(discoveryUrl, HttpMethod.GET, null, Map.class).getBody();

            // 验证并设置端点
            String authEndpoint =
                    getRequiredEndpoint(discovery, IdpConstants.AUTHORIZATION_ENDPOINT);
            String tokenEndpoint = getRequiredEndpoint(discovery, IdpConstants.TOKEN_ENDPOINT);
            String userInfoEndpoint =
                    getRequiredEndpoint(discovery, IdpConstants.USERINFO_ENDPOINT);

            config.setAuthorizationEndpoint(authEndpoint);
            config.setTokenEndpoint(tokenEndpoint);
            config.setUserInfoEndpoint(userInfoEndpoint);
        } catch (Exception e) {
            log.error("Failed to discover OIDC endpoints from discovery URL: {}", discoveryUrl, e);
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format("OIDC配置{}的Issuer无效或无法访问", provider));
        }
    }

    private String getRequiredEndpoint(Map<String, Object> discovery, String name) {
        return Optional.ofNullable(discovery.get(name))
                .map(Object::toString)
                .filter(StrUtil::isNotBlank)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.INVALID_PARAMETER,
                                        "OIDC Discovery配置中缺少端点: " + name));
    }

    @Override
    public void validateOAuth2Configs(List<OAuth2Config> oauth2Configs) {
        if (CollUtil.isEmpty(oauth2Configs)) {
            return;
        }

        // provider唯一
        Set<String> providers =
                oauth2Configs.stream()
                        .map(OAuth2Config::getProvider)
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.toSet());
        if (providers.size() != oauth2Configs.size()) {
            throw new BusinessException(ErrorCode.CONFLICT, "OAuth2配置中存在空或重复的provider");
        }

        oauth2Configs.forEach(
                config -> {
                    if (GrantType.JWT_BEARER.equals(config.getGrantType())) {
                        validateJwtBearerConfig(config);
                    }
                });
    }

    private void validateJwtBearerConfig(OAuth2Config config) {
        JwtBearerConfig jwtBearerConfig = config.getJwtBearerConfig();
        if (jwtBearerConfig == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format("OAuth2配置{}使用JWT断言模式但缺少JWT断言配置", config.getProvider()));
        }

        List<PublicKeyConfig> publicKeys = jwtBearerConfig.getPublicKeys();
        if (CollUtil.isEmpty(publicKeys)) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format("OAuth2配置{}缺少公钥配置", config.getProvider()));
        }

        if (publicKeys.stream()
                        .map(
                                key -> {
                                    // 加载公钥验证有效性
                                    loadPublicKey(key.getFormat(), key.getValue());
                                    return key.getKid();
                                })
                        .collect(Collectors.toSet())
                        .size()
                != publicKeys.size()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    StrUtil.format("OAuth2配置{}的公钥ID存在重复", config.getProvider()));
        }
    }

    @Override
    public PublicKey loadPublicKey(PublicKeyFormat format, String publicKey) {
        switch (format) {
            case PEM:
                return loadPublicKeyFromPem(publicKey);
            case JWK:
                return loadPublicKeyFromJwk(publicKey);
            default:
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "公钥格式不支持");
        }
    }

    private PublicKey loadPublicKeyFromPem(String pemContent) {
        // 清理PEM格式标记和空白字符
        String publicKeyPEM =
                pemContent
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                        .replace("-----END RSA PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");

        if (StrUtil.isBlank(publicKeyPEM)) {
            throw new IllegalArgumentException("PEM内容为空");
        }

        try {
            // Base64解码
            byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);

            // 公钥对象
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            log.error("PEM公钥解析失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PEM公钥解析失败: " + e.getMessage());
        }
    }

    private PublicKey loadPublicKeyFromJwk(String jwkContent) {
        JSONObject jwk = JSONUtil.parseObj(jwkContent);

        // 验证必需字段
        String kty = getRequiredField(jwk, "kty");
        if (!"RSA".equals(kty)) {
            throw new IllegalArgumentException("当前仅支持RSA类型的JWK");
        }

        return loadRSAPublicKeyFromJwk(jwk);
    }

    private PublicKey loadRSAPublicKeyFromJwk(JSONObject jwk) {
        // 获取必需的RSA参数
        String nStr = getRequiredField(jwk, "n");
        String eStr = getRequiredField(jwk, "e");

        try {
            // Base64解码参数
            byte[] nBytes = Base64.getUrlDecoder().decode(nStr);
            byte[] eBytes = Base64.getUrlDecoder().decode(eStr);

            // 构建RSA公钥
            BigInteger modulus = new BigInteger(1, nBytes);
            BigInteger exponent = new BigInteger(1, eBytes);

            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            log.error("JWK RSA参数解析失败", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "JWK RSA参数解析失败: " + e.getMessage());
        }
    }

    private String getRequiredField(JSONObject jwk, String fieldName) {
        String value = jwk.getStr(fieldName);
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWK中缺少字段: " + fieldName);
        }
        return value;
    }
}
