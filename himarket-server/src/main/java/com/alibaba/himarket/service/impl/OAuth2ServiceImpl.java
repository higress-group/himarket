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
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.alibaba.himarket.core.constant.JwtConstants;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.utils.TokenUtil;
import com.alibaba.himarket.dto.params.developer.CreateExternalDeveloperParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.service.DeveloperService;
import com.alibaba.himarket.service.IdpService;
import com.alibaba.himarket.service.OAuth2Service;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.support.enums.DeveloperAuthType;
import com.alibaba.himarket.support.enums.GrantType;
import com.alibaba.himarket.support.enums.JwtAlgorithm;
import com.alibaba.himarket.support.portal.*;
import java.security.PublicKey;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author zh
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2ServiceImpl implements OAuth2Service {

    private final PortalService portalService;

    private final DeveloperService developerService;

    private final IdpService idpService;

    @Override
    public AuthResult authenticate(String grantType, String jwtToken) {
        if (!GrantType.JWT_BEARER.getType().equals(grantType)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "不支持的授权模式");
        }

        // 解析JWT
        JWT jwt = JWTUtil.parseToken(jwtToken);
        String kid = (String) jwt.getHeader(JwtConstants.HEADER_KID);
        if (StrUtil.isBlank(kid)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT header缺少字段kid");
        }
        String provider = (String) jwt.getPayload(JwtConstants.PAYLOAD_PROVIDER);
        if (StrUtil.isBlank(provider)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT payload缺少字段provider");
        }

        String portalId = (String) jwt.getPayload(JwtConstants.PAYLOAD_PORTAL);
        if (StrUtil.isBlank(portalId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT payload缺少字段portal");
        }

        // 根据provider确定OAuth2配置
        PortalResult portal = portalService.getPortal(portalId);
        List<OAuth2Config> oauth2Configs =
                Optional.ofNullable(portal.getPortalSettingConfig())
                        .map(PortalSettingConfig::getOauth2Configs)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                Resources.OAUTH2_CONFIG,
                                                portalId));

        OAuth2Config oAuth2Config =
                oauth2Configs.stream()
                        // JWT Bearer模式
                        .filter(config -> config.getGrantType() == GrantType.JWT_BEARER)
                        .filter(
                                config ->
                                        config.getJwtBearerConfig() != null
                                                && CollUtil.isNotEmpty(
                                                        config.getJwtBearerConfig()
                                                                .getPublicKeys()))
                        // provider标识
                        .filter(config -> config.getProvider().equals(provider))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                Resources.OAUTH2_CONFIG,
                                                provider));

        // 根据kid找到对应公钥
        JwtBearerConfig jwtConfig = oAuth2Config.getJwtBearerConfig();
        PublicKeyConfig publicKeyConfig =
                jwtConfig.getPublicKeys().stream()
                        .filter(key -> kid.equals(key.getKid()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, Resources.PUBLIC_KEY, kid));

        // 验签
        if (!verifySignature(jwt, publicKeyConfig)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT签名验证失败");
        }

        // 验证Claims
        validateJwtClaims(jwt);

        // Developer
        String developerId = createOrGetDeveloper(jwt, oAuth2Config);

        // 生成Access Token
        String accessToken = TokenUtil.generateDeveloperToken(developerId);
        log.info(
                "JWT Bearer认证成功，provider: {}, developer: {}",
                oAuth2Config.getProvider(),
                developerId);
        return AuthResult.of(accessToken, TokenUtil.getTokenExpiresIn());
    }

    private boolean verifySignature(JWT jwt, PublicKeyConfig keyConfig) {
        // 加载公钥
        PublicKey publicKey = idpService.loadPublicKey(keyConfig.getFormat(), keyConfig.getValue());

        // 验证JWT
        JWTSigner signer = createJWTSigner(keyConfig.getAlgorithm(), publicKey);
        return jwt.setSigner(signer).verify();
    }

    private JWTSigner createJWTSigner(String algorithm, PublicKey publicKey) {
        JwtAlgorithm alg = EnumUtil.fromString(JwtAlgorithm.class, algorithm.toUpperCase());

        switch (alg) {
            case RS256:
                return JWTSignerUtil.rs256(publicKey);
            case RS384:
                return JWTSignerUtil.rs384(publicKey);
            case RS512:
                return JWTSignerUtil.rs512(publicKey);
            case ES256:
                return JWTSignerUtil.es256(publicKey);
            case ES384:
                return JWTSignerUtil.es384(publicKey);
            case ES512:
                return JWTSignerUtil.es512(publicKey);
            default:
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不支持的JWT签名算法");
        }
    }

    private void validateJwtClaims(JWT jwt) {
        // 过期时间
        Object expObj = jwt.getPayload(JwtConstants.PAYLOAD_EXP);
        Long exp = Convert.toLong(expObj);
        // 签发时间
        Object iatObj = jwt.getPayload(JwtConstants.PAYLOAD_IAT);
        Long iat = Convert.toLong(iatObj);

        if (iat == null || exp == null || iat > exp) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT payload中exp或iat不合法");
        }

        long currentTime = System.currentTimeMillis() / 1000;
        if (exp <= currentTime) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT已过期");
        }
    }

    private String createOrGetDeveloper(JWT jwt, OAuth2Config config) {
        IdentityMapping identityMapping = config.getIdentityMapping();
        // userId & userName
        String userIdField =
                StrUtil.isBlank(identityMapping.getUserIdField())
                        ? JwtConstants.PAYLOAD_USER_ID
                        : identityMapping.getUserIdField();
        String userNameField =
                StrUtil.isBlank(identityMapping.getUserNameField())
                        ? JwtConstants.PAYLOAD_USER_NAME
                        : identityMapping.getUserNameField();
        Object userIdObj = jwt.getPayload(userIdField);
        Object userNameObj = jwt.getPayload(userNameField);

        String userId = Convert.toStr(userIdObj);
        String userName = Convert.toStr(userNameObj);
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(userName)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "JWT payload中缺少用户ID字段或用户名称");
        }

        // 复用已有的Developer，否则创建
        return Optional.ofNullable(
                        developerService.getExternalDeveloper(config.getProvider(), userId))
                .map(DeveloperResult::getDeveloperId)
                .orElseGet(
                        () -> {
                            CreateExternalDeveloperParam param =
                                    CreateExternalDeveloperParam.builder()
                                            .provider(config.getProvider())
                                            .subject(userId)
                                            .displayName(userName)
                                            .authType(DeveloperAuthType.OAUTH2)
                                            .build();

                            return developerService.createExternalDeveloper(param).getDeveloperId();
                        });
    }
}
