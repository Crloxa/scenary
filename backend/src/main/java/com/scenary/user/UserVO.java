package com.scenary.user;

/**
 * /users/me 完整视角（docs/02 §3.1）：含 username 与全部未删笔记计数。
 */
public record UserVO(
        long id,
        String username,
        String nickname,
        String avatarUrl,
        String bio,
        long noteCount,
        long createdAt) {
}
