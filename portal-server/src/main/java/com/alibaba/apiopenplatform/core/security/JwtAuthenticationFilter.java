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

package com.alibaba.apiopenplatform.core.security;

import com.alibaba.apiopenplatform.core.constant.CommonConstants;
import com.alibaba.apiopenplatform.core.utils.TokenUtil;
import com.alibaba.apiopenplatform.support.common.User;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // 白名单路径
    private static final String[] WHITELIST_PATHS = {
            "/admins/init",
            "/admins/need-init", 
            "/admins/login",
            "/developers",
            "/developers/login",
            "/developers/authorize",
            "/developers/callback",
            "/developers/providers",
            "/developers/oidc/authorize",
            "/developers/oidc/callback",
            "/developers/oidc/providers",
            "/developers/oauth2/token",
            "/portal/swagger-ui.html",
            "/portal/swagger-ui/**",
            "/portal/v3/api-docs/**",
            "/favicon.ico",
            "/error"
    };

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain chain)
            throws IOException, ServletException {

        // 检查是否是白名单路径
        if (isWhitelistPath(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String token = TokenUtil.getTokenFromRequest(request);
            if (token != null) {
                // 检查token是否被撤销
                if (TokenUtil.isTokenRevoked(token)) {
                    log.debug("Token已被撤销: {}", token);
                    SecurityContextHolder.clearContext();
                } else {
                    try {
                        authenticateRequest(token);
                    } catch (Exception e) {
                        log.debug("Token认证失败: {}", e.getMessage());
                        SecurityContextHolder.clearContext();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Token处理异常: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    private boolean isWhitelistPath(String requestURI) {
        for (String whitelistPath : WHITELIST_PATHS) {
            if (whitelistPath.endsWith("/**")) {
                // 处理通配符路径
                String basePath = whitelistPath.substring(0, whitelistPath.length() - 2);
                if (requestURI.startsWith(basePath)) {
                    return true;
                }
            } else if (requestURI.equals(whitelistPath)) {
                return true;
            }
        }
        return false;
    }

    private void authenticateRequest(String token) {
        User user = TokenUtil.parseUser(token);
        // 设置认证信息
        String role = CommonConstants.ROLE_PREFIX + user.getUserType().name();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUserId(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
} 