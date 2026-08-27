package com.scenary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * JWT 配置映射 scnenary.jwt.*（application-dev.yml），secret 真实值由 run-dev.sh 从 .env 注入。
 */
@Data
@Component
@ConfigurationProperties(prefix = "scenary.jwt")
public class JwtProperties {

    /** HS256 密钥，长度约束 ≥32 字符（docs/01 §8） */
    private String secret;
    private long accessTtl;
    private long refreshTtl;
    private String issuer;
}
