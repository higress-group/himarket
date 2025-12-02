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

package com.alibaba.apiopenplatform.filter;

import cn.hutool.core.util.StrUtil;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.service.PortalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;

@Slf4j
@RequiredArgsConstructor
public class PortalResolvingFilter extends OncePerRequestFilter {

    private final PortalService portalService;

    private final ContextHolder contextHolder;

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull FilterChain chain)
            throws ServletException, IOException {
        try {
            String origin = request.getHeader("Origin");
            String host = request.getHeader("Host");
            String xForwardedHost = request.getHeader("X-Forwarded-Host");
            String xRealIp = request.getHeader("X-Real-IP");
            String xForwardedFor = request.getHeader("X-Forwarded-For");

            String domain = null;
            if (origin != null) {
                try {
                    URI uri = new URI(origin);
                    domain = uri.getHost();
                } catch (Exception ignored) {
                }
            }

            log.debug("域名解析调试 - Origin: {}, Host: {}, X-Forwarded-Host: {}, ServerName: {}, X-Real-IP: {}, X-Forwarded-For: {}",
                    origin, host, xForwardedHost, request.getServerName(), xRealIp, xForwardedFor);

            if (domain == null) {
                // 优先使用Host头，如果没有则使用ServerName
                if (host != null && !host.isEmpty()) {
                    domain = host.split(":")[0]; // 去掉端口号
                } else {
                    domain = request.getServerName();
                }
            }
            String portalId = portalService.resolvePortal(domain);

            if (StrUtil.isNotBlank(portalId)) {
                contextHolder.savePortal(portalId);
                log.debug("Resolved portal for domain: {} with portalId: {}", domain, portalId);
            } else {
                log.debug("No portal found for domain: {}", domain);
                String defaultPortalId = portalService.getDefaultPortal();
                if (StrUtil.isNotBlank(defaultPortalId)) {
                    contextHolder.savePortal(defaultPortalId);
                    log.debug("Use default portal: {}", defaultPortalId);
                }
            }

            chain.doFilter(request, response);
        } finally {
            contextHolder.clearPortal();
        }
    }
}
