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

package com.alibaba.himarket.utils;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.alibaba.himarket.core.constant.CommonConstants;
import com.alibaba.himarket.support.common.User;
import com.alibaba.himarket.support.enums.UserType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TokenUtil {

    private static String JWT_SECRET;

    private static long JWT_EXPIRE_MILLIS;

    private static final Map<String, Long> INVALID_TOKENS = new ConcurrentHashMap<>();

    private static String getJwtSecret() {
        if (JWT_SECRET == null) {
            JWT_SECRET = SpringUtil.getProperty("jwt.secret");
        }

        if (StrUtil.isBlank(JWT_SECRET)) {
            throw new RuntimeException("JWT secret cannot be empty");
        }
        return JWT_SECRET;
    }

    private static long getJwtExpireMillis() {
        if (JWT_EXPIRE_MILLIS == 0) {
            String expiration = SpringUtil.getProperty("jwt.expiration");
            if (StrUtil.isBlank(expiration)) {
                throw new RuntimeException("JWT expiration is empty");
            }

            if (expiration.matches("\\d+[smhd]")) {
                JWT_EXPIRE_MILLIS = Duration.parse("PT" + expiration.toUpperCase()).toMillis();
            } else {
                JWT_EXPIRE_MILLIS = Long.parseLong(expiration);
            }
        }
        return JWT_EXPIRE_MILLIS;
    }

    public static String generateAdminToken(String userId) {
        return generateToken(UserType.ADMIN, userId);
    }

    public static String generateDeveloperToken(String userId) {
        return generateToken(UserType.DEVELOPER, userId);
    }

    /**
     * Generate token
     *
     * @param userType user type
     * @param userId user ID
     * @return JWT token
     */
    private static String generateToken(UserType userType, String userId) {
        long now = System.currentTimeMillis();

        Map<String, String> claims =
                MapUtil.<String, String>builder()
                        .put(CommonConstants.USER_TYPE, userType.name())
                        .put(CommonConstants.USER_ID, userId)
                        .build();

        return JWT.create()
                .addPayloads(claims)
                .setIssuedAt(new Date(now))
                .setExpiresAt(new Date(now + getJwtExpireMillis()))
                .setSigner(JWTSignerUtil.hs256(getJwtSecret().getBytes(StandardCharsets.UTF_8)))
                .sign();
    }

    /**
     * Parse token
     *
     * @param token JWT token
     * @return user info
     */
    public static User parseUser(String token) {
        JWT jwt = JWTUtil.parseToken(token);

        // Verify signature
        boolean isValid =
                jwt.setSigner(JWTSignerUtil.hs256(getJwtSecret().getBytes(StandardCharsets.UTF_8)))
                        .verify();
        if (!isValid) {
            throw new IllegalArgumentException("Invalid token signature");
        }

        // Verify expiration
        Object expObj = jwt.getPayloads().get(JWT.EXPIRES_AT);
        if (ObjectUtil.isNotNull(expObj)) {
            long expireAt = Long.parseLong(expObj.toString());
            if (expireAt * 1000 <= System.currentTimeMillis()) {
                throw new IllegalArgumentException("Token has expired");
            }
        }

        return jwt.getPayloads().toBean(User.class);
    }

    public static String getTokenFromRequest(HttpServletRequest request) {
        // Get token from header
        String authHeader = request.getHeader(CommonConstants.AUTHORIZATION_HEADER);

        String token = null;
        if (authHeader != null && authHeader.startsWith(CommonConstants.BEARER_PREFIX)) {
            token = authHeader.substring(CommonConstants.BEARER_PREFIX.length());
        }

        // Get token from cookie
        if (StrUtil.isBlank(token)) {
            token =
                    Optional.ofNullable(request.getCookies())
                            .flatMap(
                                    cookies ->
                                            Arrays.stream(cookies)
                                                    .filter(
                                                            cookie ->
                                                                    CommonConstants
                                                                            .AUTH_TOKEN_COOKIE
                                                                            .equals(
                                                                                    cookie
                                                                                            .getName()))
                                                    .map(Cookie::getValue)
                                                    .findFirst())
                            .orElse(null);
        }
        if (StrUtil.isBlank(token) || isTokenRevoked(token)) {
            return null;
        }

        return token;
    }

    public static void revokeToken(String token) {
        if (StrUtil.isBlank(token)) {
            return;
        }
        long expireAt = getTokenExpireTime(token);
        INVALID_TOKENS.put(token, expireAt);
        cleanExpiredTokens();
    }

    private static long getTokenExpireTime(String token) {
        JWT jwt = JWTUtil.parseToken(token);
        Object expObj = jwt.getPayloads().get(JWT.EXPIRES_AT);
        if (ObjectUtil.isNotNull(expObj)) {
            return Long.parseLong(expObj.toString()) * 1000; // JWT expiration is in seconds
        }
        return System.currentTimeMillis() + getJwtExpireMillis(); // Default expiration
    }

    public static void revokeToken(HttpServletRequest request) {
        String token = getTokenFromRequest(request);
        if (StrUtil.isNotBlank(token)) {
            revokeToken(token);
        }
    }

    public static boolean isTokenRevoked(String token) {
        if (StrUtil.isBlank(token)) {
            return false;
        }
        Long expireAt = INVALID_TOKENS.get(token);
        if (expireAt == null) {
            return false;
        }
        if (expireAt <= System.currentTimeMillis()) {
            INVALID_TOKENS.remove(token);
            return false;
        }
        return true;
    }

    private static void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        INVALID_TOKENS.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    public static long getTokenExpiresIn() {
        return getJwtExpireMillis() / 1000;
    }
}
