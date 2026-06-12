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

package com.alibaba.himarket.service.hicoding.websocket;

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.utils.TokenUtil;
import com.alibaba.himarket.support.common.User;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class HiCodingHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger logger =
            LoggerFactory.getLogger(HiCodingHandshakeInterceptor.class);

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes)
            throws Exception {
        String token = null;

        // 1. Try query param: ?token=xxx (primary for WebSocket since browsers can't set headers)
        try {
            var params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            token = params.getFirst("token");

            // Extract CLI provider selection from query param: ?provider=kiro-cli
            String provider = params.getFirst("provider");
            if (StrUtil.isNotBlank(provider)) {
                attributes.put("provider", provider);
            }

            // Extract runtime type from query param: ?runtime=local|k8s
            String runtime = params.getFirst("runtime");
            if (StrUtil.isNotBlank(runtime)) {
                attributes.put("runtime", runtime);
            }

            // Extract sandbox mode from query param: ?sandboxMode=user|session
            String sandboxMode = params.getFirst("sandboxMode");
            if (StrUtil.isNotBlank(sandboxMode)) {
                attributes.put("sandboxMode", sandboxMode);
            }
        } catch (Exception e) {
            logger.debug("Failed to parse token from query param", e);
        }

        // 2. Fallback to Authorization header / cookie (via TokenUtil)
        if (StrUtil.isBlank(token) && request instanceof ServletServerHttpRequest servletRequest) {
            token = TokenUtil.getTokenFromRequest(servletRequest.getServletRequest());
        }

        if (StrUtil.isBlank(token)) {
            logger.warn("WebSocket handshake rejected: missing token");
            return false;
        }

        try {
            User user = TokenUtil.parseUser(token);
            attributes.put("userId", user.getUserId());
            logger.info("WebSocket handshake authenticated: userId={}", user.getUserId());
            return true;
        } catch (Exception e) {
            logger.warn("WebSocket handshake rejected: invalid token - {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No-op
    }
}
