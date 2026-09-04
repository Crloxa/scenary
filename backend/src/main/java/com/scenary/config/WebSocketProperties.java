package com.scenary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** WebSocket 握手来源白名单；生产环境应按实际前端域名覆盖。 */
@Component
@ConfigurationProperties(prefix = "scenary.websocket")
public class WebSocketProperties {

    private String allowedOrigins = "http://localhost:8081,http://127.0.0.1:8081,http://localhost:5173,http://127.0.0.1:5173";

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
