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

import com.alibaba.himarket.core.constant.IdpConstants;
import com.alibaba.himarket.core.constant.JwtConstants;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.developer.CreateExternalDeveloperParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.DeveloperService;
import com.alibaba.himarket.service.IdpService;
import com.alibaba.himarket.service.OAuth2Service;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.service.TokenService;
import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.support.enums.DeveloperAuthType;
import com.alibaba.himarket.support.enums.GrantType;
import com.alibaba.himarket.support.enums.JwtAlgorithm;
import com.alibaba.himarket.support.portal.IdentityMapping;
import com.alibaba.himarket.support.portal.JwtBearerConfig;
import com.alibaba.himarket.support.portal.OAuth2Config;
import com.alibaba.himarket.support.portal.PortalSettingConfig;
import com.alibaba.himarket.support.portal.PublicKeyConfig;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jwt.SignedJWT;
import java.security.PublicKey;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class OAuth2ServiceImpl implements OAuth2Service {

    private final PortalService portalService;

    private final DeveloperService developerService;

    private final IdpService idpService;

    private final TokenService tokenService;

    @Override
    public AuthResult authenticate(String grantType, String jwtToken) {
        if (!GrantType.JWT_BEARER.getType().equals(grantType)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported grant type");
        }

        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(jwtToken);
        } catch (ParseException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid JWT token");
        }

        Map<String, Object> claims = jwt.getPayload().toJSONObject();
        if (claims == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid JWT payload");
        }

        String kid = jwt.getHeader().getKeyID();
        if (Strings.isBlank(kid)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT header missing field kid");
        }
        String provider = Objects.toString(claims.get(JwtConstants.PAYLOAD_PROVIDER), null);
        if (Strings.isBlank(provider)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "JWT payload missing field provider");
        }

        String portalId = Objects.toString(claims.get(JwtConstants.PAYLOAD_PORTAL), null);
        if (Strings.isBlank(portalId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "JWT payload missing field portal");
        }

        // Get OAuth2 config by provider
        PortalResult portal = portalService.getPortal(portalId);
        PortalSettingConfig portalSettingConfig = portal.getPortalSettingConfig();
        if (portalSettingConfig == null || portalSettingConfig.getOauth2Configs() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.OAUTH2_CONFIG, portalId);
        }
        List<OAuth2Config> oauth2Configs = portalSettingConfig.getOauth2Configs();

        OAuth2Config oAuth2Config =
                oauth2Configs.stream()
                        // JWT Bearer mode
                        .filter(config -> config.getGrantType() == GrantType.JWT_BEARER)
                        .filter(
                                config ->
                                        config.getJwtBearerConfig() != null
                                                && !CollectionUtils.isEmpty(
                                                        config.getJwtBearerConfig()
                                                                .getPublicKeys()))
                        // Provider identifier
                        .filter(config -> config.getProvider().equals(provider))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                Resources.OAUTH2_CONFIG,
                                                provider));

        // Find public key by kid
        JwtBearerConfig jwtConfig = oAuth2Config.getJwtBearerConfig();
        PublicKeyConfig publicKeyConfig =
                jwtConfig.getPublicKeys().stream()
                        .filter(key -> kid.equals(key.getKid()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, Resources.PUBLIC_KEY, kid));

        // Verify signature
        if (!verifySignature(jwt, publicKeyConfig)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "JWT signature verification failed");
        }

        // Validate claims
        validateJwtClaims(claims);

        // Developer
        String developerId = createOrGetDeveloper(claims, oAuth2Config);

        // Generate access token
        String accessToken = tokenService.generateDeveloperToken(developerId);
        log.info(
                "JWT Bearer authentication succeeded, provider={}, developerId={}",
                oAuth2Config.getProvider(),
                developerId);
        return AuthResult.of(accessToken, tokenService.getTokenExpiresIn());
    }

    private boolean verifySignature(SignedJWT jwt, PublicKeyConfig keyConfig) {
        // Load public key
        PublicKey publicKey = idpService.loadPublicKey(keyConfig.getFormat(), keyConfig.getValue());

        try {
            JWSVerifier verifier =
                    createJwsVerifier(jwt.getHeader(), keyConfig.getAlgorithm(), publicKey);
            return jwt.verify(verifier);
        } catch (JOSEException e) {
            log.warn(
                    "Failed to verify JWT signature, kid={}, algorithm={}",
                    keyConfig.getKid(),
                    keyConfig.getAlgorithm(),
                    e);
            return false;
        }
    }

    private JWSVerifier createJwsVerifier(JWSHeader header, String algorithm, PublicKey publicKey)
            throws JOSEException {
        JwtAlgorithm alg = JwtAlgorithm.of(algorithm);
        if (alg == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Unsupported JWT signature algorithm");
        }

        JWSAlgorithm expectedAlgorithm = JWSAlgorithm.parse(alg.name());
        if (!expectedAlgorithm.equals(header.getAlgorithm())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "JWT signature algorithm does not match");
        }
        JWSVerifier verifier = new DefaultJWSVerifierFactory().createJWSVerifier(header, publicKey);
        if (verifier == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Unsupported JWT signature algorithm");
        }
        return verifier;
    }

    private void validateJwtClaims(Map<String, Object> claims) {
        // Expiration
        Object expObj = claims.get(JwtConstants.PAYLOAD_EXP);
        Long exp = null;
        if (expObj instanceof Number number) {
            exp = number.longValue();
        } else if (expObj != null) {
            try {
                exp = Long.parseLong(expObj.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        // Issued at
        Object iatObj = claims.get(JwtConstants.PAYLOAD_IAT);
        Long iat = null;
        if (iatObj instanceof Number number) {
            iat = number.longValue();
        } else if (iatObj != null) {
            try {
                iat = Long.parseLong(iatObj.toString());
            } catch (NumberFormatException ignored) {
            }
        }

        if (iat == null || exp == null || iat > exp) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Invalid exp or iat in JWT payload");
        }

        long currentTime = System.currentTimeMillis() / 1000;
        if (exp <= currentTime) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT has expired");
        }
    }

    private String createOrGetDeveloper(Map<String, Object> claims, OAuth2Config config) {
        IdentityMapping identityMapping = config.getIdentityMapping();
        // userId & userName
        String userIdField =
                Strings.isBlank(identityMapping.getUserIdField())
                        ? JwtConstants.PAYLOAD_USER_ID
                        : identityMapping.getUserIdField();
        String userNameField =
                Strings.isBlank(identityMapping.getUserNameField())
                        ? JwtConstants.PAYLOAD_USER_NAME
                        : identityMapping.getUserNameField();
        String avatarUrlField =
                Strings.isBlank(identityMapping.getAvatarUrlField())
                        ? IdpConstants.AVATAR_URL
                        : identityMapping.getAvatarUrlField();
        Object userIdObj = claims.get(userIdField);
        Object userNameObj = claims.get(userNameField);
        String avatarUrl = Objects.toString(claims.get(avatarUrlField), null);

        String userId = Objects.toString(userIdObj, null);
        String userName = Objects.toString(userNameObj, null);
        if (Strings.isBlank(userId) || Strings.isBlank(userName)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Missing user ID or user name in JWT payload");
        }

        // Reuse existing developer or create new
        DeveloperResult existing =
                developerService.getExternalDeveloper(config.getProvider(), userId);
        if (existing != null) {
            developerService.updateExternalDeveloperAvatar(config.getProvider(), userId, avatarUrl);
            return existing.getDeveloperId();
        }

        CreateExternalDeveloperParam param =
                CreateExternalDeveloperParam.builder()
                        .provider(config.getProvider())
                        .subject(userId)
                        .displayName(userName)
                        .avatarUrl(avatarUrl)
                        .authType(DeveloperAuthType.OAUTH2)
                        .build();

        return developerService.createExternalDeveloper(param).getDeveloperId();
    }
}
