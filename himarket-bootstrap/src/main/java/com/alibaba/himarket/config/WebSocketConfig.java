package com.alibaba.himarket.config;

import com.alibaba.himarket.service.acp.AcpHandshakeInterceptor;
import com.alibaba.himarket.service.acp.AcpWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AcpWebSocketHandler acpWebSocketHandler;
    private final AcpHandshakeInterceptor acpHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(acpWebSocketHandler, "/ws/acp")
                .addInterceptors(acpHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
