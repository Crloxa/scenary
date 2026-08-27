package com.scenary.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;

/**
 * MinIO SDK 客户端：endpoint/密钥全部来自 MinioProperties，测试可通过换配置注入假实现边界。
 */
@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties props) {
        return MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .build();
    }
}
