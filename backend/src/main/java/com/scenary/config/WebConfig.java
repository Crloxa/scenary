package com.scenary.config;

import org.springframework.lang.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.scenary.auth.AuthInterceptor;

/**
 * MVC 装配：统一受保护路径注册（docs/03 3.5）。方法级条件由拦截器内部豁免（见其 GET notes 分支）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns(
                        "/api/v1/users/me/**",
                        "/api/v1/media/**",
                        "/api/v1/notes",
                        "/api/v1/notes/*",
                        "/api/v1/notes/*/like",
                        "/api/v1/notes/*/bookmark",
                        "/api/v1/notes/*/comments",
                        "/api/v1/comments/*",
                        "/api/v1/users/*/follow",
                        "/api/v1/users/*/block",
                        "/api/v1/reports",
                        "/api/v1/users/me/bookmarks",
                        "/api/v1/notifications",
                        "/api/v1/notifications/**");
    }
}
