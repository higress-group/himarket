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

        // Provider must be unique
        Set<String> providers =
                oidcConfigs.stream()
                        .map(OidcConfig::getProvider)
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.toSet());
        if (providers.size() != oidcConfigs.size()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT, "Empty or duplicate provider in OIDC config");
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
                                                                    "OIDC config {} missing auth"
                                                                            + " code config",
                                                                    config.getProvider())));
                    // Basic parameters
                    if (StrUtil.isBlank(authConfig.getClientId())
                            || StrUtil.isBlank(authConfig.getClientSecret())
                            || StrUtil.isBlank(authConfig.getScopes())) {
                        throw new BusinessException(
                                ErrorCode.INVALID_PARAMETER,
                                StrUtil.format(
                                        "OIDC config {} missing required params: Client ID, Client"
                                                + " Secret or Scopes",
                                        config.getProvider()));
                    }

                    // Endpoint configuration
                    if (StrUtil.isNotBlank(authConfig.getIssuer())) {
                        discoverAndSetEndpoints(config.getProvider(), authConfig);
                    } else {
                        if (StrUtil.isBlank(authConfig.getAuthorizationEndpoint())
                                || StrUtil.isBlank(authConfig.getTokenEndpoint())
                                || StrUtil.isBlank(authConfig.getUserInfoEndpoint())) {
                            throw new BusinessException(
                                    ErrorCode.INVALID_PARAMETER,
                                    StrUtil.format(
                                            "OIDC config {} missing required endpoint config",
                                            config.getProvider()));
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

            // Validate and set endpoints
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
                    StrUtil.format("OIDC config {} issuer is invalid or unreachable", provider));
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
                                        "Missing endpoint in OIDC discovery config: " + name));
    }

    @Override
    public void validateOAuth2Configs(List<OAuth2Config> oauth2Configs) {
        if (CollUtil.isEmpty(oauth2Configs)) {
            return;
        }

        // Provider must be unique
        Set<String> providers =
                oauth2Configs.stream()
                        .map(OAuth2Config::getProvider)
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.toSet());
        if (providers.size() != oauth2Configs.size()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT, "Empty or duplicate provider in OAuth2 config");
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
                    StrUtil.format(
                            "OAuth2 config {} uses JWT bearer but missing JWT bearer config",
                            config.getProvider()));
        }

        List<PublicKeyConfig> publicKeys = jwtBearerConfig.getPublicKeys();
        if (CollUtil.isEmpty(publicKeys)) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    StrUtil.format(
                            "OAuth2 config {} missing public key config", config.getProvider()));
        }

        if (publicKeys.stream()
                        .map(
                                key -> {
                                    // Load public key to validate
                                    loadPublicKey(key.getFormat(), key.getValue());
                                    return key.getKid();
                                })
                        .collect(Collectors.toSet())
                        .size()
                != publicKeys.size()) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    StrUtil.format(
                            "OAuth2 config {} has duplicate public key IDs", config.getProvider()));
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
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER, "Unsupported public key format");
        }
    }

    private PublicKey loadPublicKeyFromPem(String pemContent) {
        // Clean PEM format markers and whitespace
        String publicKeyPEM =
                pemContent
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                        .replace("-----END RSA PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");

        if (StrUtil.isBlank(publicKeyPEM)) {
            throw new IllegalArgumentException("PEM content is empty");
        }

        try {
            // Base64 decode
            byte[] decoded = Base64.getDecoder().decode(publicKeyPEM);

            // Public key object
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            log.error("Failed to parse PEM public key", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to parse PEM public key: " + e.getMessage());
        }
    }

    private PublicKey loadPublicKeyFromJwk(String jwkContent) {
        JSONObject jwk = JSONUtil.parseObj(jwkContent);

        // Validate required fields
        String kty = getRequiredField(jwk, "kty");
        if (!"RSA".equals(kty)) {
            throw new IllegalArgumentException("Only RSA type JWK is supported");
        }

        return loadRSAPublicKeyFromJwk(jwk);
    }

    private PublicKey loadRSAPublicKeyFromJwk(JSONObject jwk) {
        // Get required RSA parameters
        String nStr = getRequiredField(jwk, "n");
        String eStr = getRequiredField(jwk, "e");

        try {
            // Base64 decode parameters
            byte[] nBytes = Base64.getUrlDecoder().decode(nStr);
            byte[] eBytes = Base64.getUrlDecoder().decode(eStr);

            // Build RSA public key
            BigInteger modulus = new BigInteger(1, nBytes);
            BigInteger exponent = new BigInteger(1, eBytes);

            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            log.error("Failed to parse JWK RSA parameters", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to parse JWK RSA parameters: " + e.getMessage());
        }
    }

    private String getRequiredField(JSONObject jwk, String fieldName) {
        String value = jwk.getStr(fieldName);
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Missing field in JWK: " + fieldName);
        }
        return value;
    }
}
