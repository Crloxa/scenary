package com.scenary.auth;

/**
 * 注册/登录/刷新三接口共用响应体（docs/02 §2），avatarUrl 未设置时为 null。
 */
public record AuthVO(
        long userId,
        String username,
        String nickname,
        String avatarUrl,
        String accessToken,
        long accessExpiresIn,
        String refreshToken,
        long refreshExpiresIn) {
}
