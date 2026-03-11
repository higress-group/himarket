package com.alibaba.himarket.config;

import com.alibaba.himarket.service.hicoding.terminal.TerminalWebSocketHandler;
import com.alibaba.himarket.service.hicoding.websocket.HiCodingHandshakeInterceptor;
import com.alibaba.himarket.service.hicoding.websocket.HiCodingWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final HiCodingWebSocketHandler hiCodingWebSocketHandler;
    private final TerminalWebSocketHandler terminalWebSocketHandler;
    private final HiCodingHandshakeInterceptor hiCodingHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(hiCodingWebSocketHandler, "/ws/acp")
                .addInterceptors(hiCodingHandshakeInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(terminalWebSocketHandler, "/ws/terminal")
                .addInterceptors(hiCodingHandshakeInterceptor)
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // 10MB — matches the frontend 5MB file-size cap after base64 expansion + JSON overhead
        container.setMaxTextMessageBufferSize(10 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(10 * 1024 * 1024);
        return container;
    }
}
