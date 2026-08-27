package com.scenary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * MinIO 连接与寻址配置（scenary.minio.*）。secret 真实值由 run-dev.sh 注入；
 * publicHost 是生成浏览器可直读链接的主机前缀，生产指向 nginx 反代路径。
 */
@Data
@Component
@ConfigurationProperties(prefix = "scenary.minio")
public class MinioProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String publicHost;
}
