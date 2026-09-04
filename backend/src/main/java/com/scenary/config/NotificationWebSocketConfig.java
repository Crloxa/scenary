package com.scenary.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.scenary.notification.NotificationWebSocketHandler;

/** 通知增强通道：HTTP API 仍是可靠读取来源。 */
@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler handler;
    private final WebSocketProperties properties;

    public NotificationWebSocketConfig(NotificationWebSocketHandler handler,
                                        WebSocketProperties properties) {
        this.handler = handler;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = properties.getAllowedOrigins().split(",");
        registry.addHandler(handler, "/api/v1/ws/notifications")
                .setAllowedOriginPatterns(origins);
    }
}
