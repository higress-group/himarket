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

import com.alibaba.himarket.config.JwtProperties;
import com.alibaba.himarket.core.constant.CommonConstants;
import com.alibaba.himarket.core.constant.JwtConstants;
import com.alibaba.himarket.service.RevokedTokenService;
import com.alibaba.himarket.service.TokenService;
import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.support.common.User;
import com.alibaba.himarket.support.enums.UserType;
import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final JwtProperties jwtProperties;

    private final RevokedTokenService revokedTokenService;

    @Override
    public String generateAdminToken(String userId) {
        return generateToken(UserType.ADMIN, userId);
    }

    @Override
    public String generateDeveloperToken(String userId) {
        return generateToken(UserType.DEVELOPER, userId);
    }

    @Override
    public User parseUser(String token) {
        String[] tokenParts = splitToken(token);
        String signingInput = tokenParts[0] + "." + tokenParts[1];
        String expectedSignature = sign(signingInput);
        boolean validSignature =
                MessageDigest.isEqual(
                        expectedSignature.getBytes(StandardCharsets.US_ASCII),
                        tokenParts[2].getBytes(StandardCharsets.US_ASCII));

        if (!validSignature) {
            throw new IllegalArgumentException("Invalid token signature");
        }

        Map<String, Object> claims = parseClaims(tokenParts[1]);
        Object expObj = claims.get(JwtConstants.PAYLOAD_EXP);
        if (expObj != null) {
            long expireAt = Long.parseLong(expObj.toString());
            if (expireAt * 1000 <= System.currentTimeMillis()) {
                throw new IllegalArgumentException("Token has expired");
            }
        }

        return JsonUtil.convert(claims, User.class);
    }

    @Override
    public String getTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(CommonConstants.AUTHORIZATION_HEADER);

        String token = null;
        if (authHeader != null && authHeader.startsWith(CommonConstants.BEARER_PREFIX)) {
            token = authHeader.substring(CommonConstants.BEARER_PREFIX.length());
        }

        if (Strings.isBlank(token)) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if (CommonConstants.AUTH_TOKEN_COOKIE.equals(cookie.getName())) {
                        token = cookie.getValue();
                        break;
                    }
                }
            }
        }

        return Strings.isBlank(token) ? null : token;
    }

    @Override
    public void revokeToken(String token) {
        if (Strings.isBlank(token)) {
            return;
        }
        revokedTokenService.revokeToken(token, getTokenExpiresAtMillis(token));
    }

    @Override
    public void revokeRequestToken(HttpServletRequest request) {
        revokeToken(getTokenFromRequest(request));
    }

    @Override
    public boolean isTokenRevoked(String token) {
        return revokedTokenService.isTokenRevoked(token);
    }

    @Override
    public long getTokenExpiresIn() {
        return getJwtExpireMillis() / 1000;
    }

    private String generateToken(UserType userType, String userId) {
        long now = System.currentTimeMillis();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put(JwtConstants.HEADER_TYP, JwtConstants.JWT_TOKEN_TYPE);
        header.put(JwtConstants.HEADER_ALG, JwtConstants.ALGORITHM_HS256);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(CommonConstants.USER_TYPE, userType.name());
        claims.put(CommonConstants.USER_ID, userId);
        claims.put(JwtConstants.PAYLOAD_IAT, now / 1000);
        claims.put(JwtConstants.PAYLOAD_EXP, (now + getJwtExpireMillis()) / 1000);

        String headerPart = encodeJsonPart(header);
        String payloadPart = encodeJsonPart(claims);
        String signingInput = headerPart + "." + payloadPart;
        return signingInput + "." + sign(signingInput);
    }

    private long getTokenExpiresAtMillis(String token) {
        Map<String, Object> claims = parseClaims(splitToken(token)[1]);
        Object expObj = claims.get(JwtConstants.PAYLOAD_EXP);
        if (expObj != null) {
            return Long.parseLong(expObj.toString()) * 1000;
        }
        return System.currentTimeMillis() + getJwtExpireMillis();
    }

    private String getJwtSecret() {
        String secret = jwtProperties.getSecret();
        if (Strings.isBlank(secret)) {
            throw new IllegalStateException("JWT secret cannot be empty");
        }
        return secret;
    }

    private long getJwtExpireMillis() {
        Duration expiration = jwtProperties.getExpiration();
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new IllegalStateException("JWT expiration must be positive");
        }
        return expiration.toMillis();
    }

    private String encodeJsonPart(Map<String, Object> value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(JsonUtil.toJson(value).getBytes(StandardCharsets.UTF_8));
    }

    private String[] splitToken(String token) {
        if (Strings.isBlank(token)) {
            throw new IllegalArgumentException("Invalid token");
        }
        String[] tokenParts = token.split("\\.", -1);
        if (tokenParts.length != 3
                || Strings.isBlank(tokenParts[0])
                || Strings.isBlank(tokenParts[1])
                || Strings.isBlank(tokenParts[2])) {
            throw new IllegalArgumentException("Invalid token");
        }
        return tokenParts;
    }

    private Map<String, Object> parseClaims(String payloadPart) {
        String payloadJson =
                new String(Base64.getUrlDecoder().decode(payloadPart), StandardCharsets.UTF_8);
        return JsonUtil.parse(payloadJson, new TypeReference<>() {});
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(JwtConstants.JCA_HMAC_SHA256);
            mac.init(
                    new SecretKeySpec(
                            getJwtSecret().getBytes(StandardCharsets.UTF_8),
                            JwtConstants.JCA_HMAC_SHA256));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT token", e);
        }
    }
}
