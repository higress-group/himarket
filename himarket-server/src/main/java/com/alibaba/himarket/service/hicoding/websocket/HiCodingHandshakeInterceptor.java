package com.alibaba.himarket.service.hicoding.websocket;

import com.alibaba.himarket.service.TokenService;
import com.alibaba.himarket.support.common.Strings;
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

    private final TokenService tokenService;

    public HiCodingHandshakeInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

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
            if (Strings.isNotBlank(provider)) {
                attributes.put("provider", provider);
            }

            // Extract runtime type from query param: ?runtime=local|k8s
            String runtime = params.getFirst("runtime");
            if (Strings.isNotBlank(runtime)) {
                attributes.put("runtime", runtime);
            }

            // Extract sandbox mode from query param: ?sandboxMode=user|session
            String sandboxMode = params.getFirst("sandboxMode");
            if (Strings.isNotBlank(sandboxMode)) {
                attributes.put("sandboxMode", sandboxMode);
            }
        } catch (Exception e) {
            logger.debug(
                    "Failed to parse token from query param, errorMessage={}", e.getMessage(), e);
        }

        // 2. Fallback to Authorization header / cookie
        if (Strings.isBlank(token) && request instanceof ServletServerHttpRequest servletRequest) {
            token = tokenService.getTokenFromRequest(servletRequest.getServletRequest());
        }

        if (Strings.isBlank(token)) {
            logger.warn("WebSocket handshake rejected, reason=missing_token");
            return false;
        }

        if (tokenService.isTokenRevoked(token)) {
            logger.warn("WebSocket handshake rejected, reason=revoked_token");
            return false;
        }

        try {
            User user = tokenService.parseUser(token);
            attributes.put("userId", user.getUserId());
            logger.info("WebSocket handshake authenticated, userId={}", user.getUserId());
            return true;
        } catch (Exception e) {
            logger.warn(
                    "WebSocket handshake rejected, reason=invalid_token, errorMessage={}",
                    e.getMessage(),
                    e);
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
