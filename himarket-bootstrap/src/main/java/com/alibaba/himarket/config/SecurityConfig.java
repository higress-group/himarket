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

package com.alibaba.himarket.config;

import com.alibaba.himarket.core.security.JwtAuthenticationFilter;
import com.alibaba.himarket.core.security.PublicAccessPathScanner;
import com.alibaba.himarket.core.security.PublicAccessPathScanner.PublicAccessEndpoint;
import jakarta.servlet.DispatcherType;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
@Slf4j
@EnableMethodSecurity
public class SecurityConfig {

    private final PublicAccessPathScanner publicAccessPathScanner;

    // Auth endpoints
    private static final String[] AUTH_WHITELIST = {
        "/admins/init",
        "/admins/need-init",
        "/admins/login",
        "/developers/login",
        "/developers/authorize",
        "/developers/callback",
        "/developers/providers",
        "/developers/oidc/authorize",
        "/developers/oidc/callback",
        "/developers/oidc/providers",
        "/developers/oauth2/token",
        "/ws/acp",
        "/ws/terminal",
        "/cli-providers",
        "/skills/*/download",
        "/workers/*/download",
        "/workers/*/files/**"
    };

    // Swagger endpoints
    private static final String[] SWAGGER_WHITELIST = {
        "/portal/swagger-ui.html", "/portal/swagger-ui/**", "/portal/v3/api-docs/**"
    };

    // System endpoints
    private static final String[] SYSTEM_WHITELIST = {"/favicon.ico", "/error"};

    // Open API endpoints (API Key auth handled in controller)
    private static final String[] OPEN_API_WHITELIST = {"/open-api/**"};

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        List<PublicAccessEndpoint> publicEndpoints =
                publicAccessPathScanner.getPublicAccessEndpoints();
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> {
                            auth
                                    // Permit async dispatch for SSE/streaming
                                    .dispatcherTypeMatchers(DispatcherType.ASYNC)
                                    .permitAll()
                                    // Permit OPTIONS
                                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                                    .permitAll()
                                    // Permit developer registration (POST /developers)
                                    .requestMatchers(HttpMethod.POST, "/developers")
                                    .permitAll()
                                    // Permit auth endpoints
                                    .requestMatchers(AUTH_WHITELIST)
                                    .permitAll()
                                    // Permit Swagger endpoints
                                    .requestMatchers(SWAGGER_WHITELIST)
                                    .permitAll()
                                    // Permit system endpoints
                                    .requestMatchers(SYSTEM_WHITELIST)
                                    .permitAll()
                                    // Permit open API endpoints
                                    .requestMatchers(OPEN_API_WHITELIST)
                                    .permitAll();
                            // Permit @PublicAccess annotated endpoints with HTTP method precision
                            for (PublicAccessEndpoint endpoint : publicEndpoints) {
                                if (endpoint.httpMethod() != null) {
                                    auth.requestMatchers(endpoint.httpMethod(), endpoint.path())
                                            .permitAll();
                                } else {
                                    // null httpMethod means all methods
                                    auth.requestMatchers(endpoint.path()).permitAll();
                                }
                            }
                            auth.anyRequest().authenticated();
                        })
                .addFilterBefore(
                        new JwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
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
