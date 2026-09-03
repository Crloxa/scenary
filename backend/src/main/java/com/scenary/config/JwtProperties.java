package com.scenary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * JWT 配置映射 scnenary.jwt.*（application-dev.yml），secret 真实值由 run-dev.sh 从 .env 注入。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "scenary.jwt")
public class JwtProperties {

    /** HS256 密钥，长度约束 ≥32 字符（docs/01 §8） */
    @NotBlank
    @Size(min = 32)
    private String secret;
    @Positive
    private long accessTtl;
    @Positive
    private long refreshTtl;
    @NotBlank
    private String issuer;
}
