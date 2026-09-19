package com.scenary.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * P16-03 OpenAPI 元数据（docs/01 §3.1）。仅补充标题与版本信息；
 * 接口定义以手写 02 契约为唯一事实源，springdoc 扫描结果只作交互式调试视图。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI scenaryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Scenary API")
                .description("极简风景图文社区。鉴权：Authorization: Bearer <accessToken>；"
                        + "响应统一包络 {code,message,data}；契约全文见 docs/02-API接口规范.md。")
                .version("v1.7"));
    }
}
