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

package com.alibaba.apiopenplatform.config;

import com.alibaba.apiopenplatform.core.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
 
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;

import com.alibaba.apiopenplatform.core.security.DeveloperAuthenticationProvider;
import jakarta.servlet.DispatcherType;
import org.springframework.http.HttpMethod;

/**
 * Spring Security安全配置，集成JWT认证与接口权限控制，支持管理员和开发者多用户体系
 *
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@EnableMethodSecurity
public class SecurityConfig {

    private final DeveloperAuthenticationProvider developerAuthenticationProvider;

    // Auth相关
    private static final String[] AUTH_WHITELIST = {
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
            "/developers/oauth2/token"
    };

    // Swagger API文档相关
    private static final String[] SWAGGER_WHITELIST = {
            "/portal/swagger-ui.html",
            "/portal/swagger-ui/**",
            "/portal/v3/api-docs/**"
    };

    // 系统路径白名单
    private static final String[] SYSTEM_WHITELIST = {
            "/favicon.ico",
            "/error"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 异步分发不进行权限检查（解决SSE等流式响应完成后的AccessDenied问题）
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                // OPTIONS请求放行
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 认证相关接口放行
                        .requestMatchers(AUTH_WHITELIST).permitAll()
                // Swagger相关接口放行
                        .requestMatchers(SWAGGER_WHITELIST).permitAll()
                // 系统路径放行
                        .requestMatchers(SYSTEM_WHITELIST).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .authenticationProvider(developerAuthenticationProvider);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(Collections.singletonList("*"));
        corsConfig.setAllowedMethods(Collections.singletonList("*"));
        corsConfig.setAllowedHeaders(Collections.singletonList("*"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }
}