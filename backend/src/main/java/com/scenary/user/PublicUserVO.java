package com.scenary.user;

/**
 * 公开主页视角（docs/02 §3.4）：去 username 类敏感字段，笔记计数仅公开。
 */
public record PublicUserVO(
        long id,
        String nickname,
        String avatarUrl,
        String bio,
        long noteCount,
        long createdAt) {
}
