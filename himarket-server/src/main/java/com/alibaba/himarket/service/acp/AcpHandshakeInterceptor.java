package com.alibaba.himarket.service.acp;

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
public class AcpHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AcpHandshakeInterceptor.class);

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
        } catch (Exception e) {
            logger.debug("Failed to parse token from query param", e);
        }

        // 2. Fallback to Authorization header / cookie (via TokenUtil)
        if (StrUtil.isBlank(token) && request instanceof ServletServerHttpRequest servletRequest) {
            token = TokenUtil.getTokenFromRequest(servletRequest.getServletRequest());
        }

        if (StrUtil.isBlank(token)) {
            // POC mode: allow anonymous access
            logger.info("WebSocket handshake allowed: anonymous (no token, POC mode)");
            attributes.put("userId", "anonymous");
            return true;
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
