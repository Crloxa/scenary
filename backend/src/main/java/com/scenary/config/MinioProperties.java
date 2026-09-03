package com.scenary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MinIO 连接与寻址配置（scenary.minio.*）。secret 真实值由 run-dev.sh 注入；
 * publicHost 是生成浏览器可直读链接的主机前缀，生产指向 nginx 反代路径。
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "scenary.minio")
public class MinioProperties {

    @NotBlank
    private String endpoint;
    @NotBlank
    private String accessKey;
    @NotBlank
    private String secretKey;
    @NotBlank
    private String bucket;
    @NotBlank
    private String publicHost;
    private boolean exposeOriginalUrl;
}
