package com.scenary.user;

import com.scenary.common.SocialVO;

/**
 * 公开主页视角（docs/02 §3.4）：去 username 类敏感字段，笔记计数仅公开。
 */
public record PublicUserVO(
        long id,
        String nickname,
        String avatarUrl,
        String bio,
        long noteCount,
        long createdAt,
        SocialVO social) {

    public PublicUserVO(long id, String nickname, String avatarUrl, String bio,
                        long noteCount, long createdAt) {
        this(id, nickname, avatarUrl, bio, noteCount, createdAt, SocialVO.empty());
    }

    public PublicUserVO withSocial(SocialVO nextSocial) {
        return new PublicUserVO(id, nickname, avatarUrl, bio, noteCount, createdAt, nextSocial);
    }
}
