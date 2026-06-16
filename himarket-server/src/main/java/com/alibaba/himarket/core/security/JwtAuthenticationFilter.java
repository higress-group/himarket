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

package com.alibaba.himarket.core.security;

import com.alibaba.himarket.core.constant.CommonConstants;
import com.alibaba.himarket.service.TokenService;
import com.alibaba.himarket.support.common.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    public JwtAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain chain)
            throws IOException, ServletException {

        try {
            String token = tokenService.getTokenFromRequest(request);
            if (token != null) {
                if (tokenService.isTokenRevoked(token)) {
                    log.debug("Token revoked");
                    SecurityContextHolder.clearContext();
                } else {
                    try {
                        authenticateRequest(token);
                    } catch (Exception e) {
                        log.debug("Token auth failed, errorMessage={}", e.getMessage(), e);
                        SecurityContextHolder.clearContext();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Token processing failed, errorMessage={}", e.getMessage(), e);
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }

    private void authenticateRequest(String token) {
        User user = tokenService.parseUser(token);
        String role = CommonConstants.ROLE_PREFIX + user.getUserType().name();
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        user.getUserId(),
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
