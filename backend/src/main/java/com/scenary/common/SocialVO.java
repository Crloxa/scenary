package com.scenary.common;

/**
 * P9 社交状态：状态字段仅在有登录视角时为 true，计数对公开资源仍可匿名读取。
 */
public record SocialVO(
        boolean liked,
        boolean bookmarked,
        boolean following,
        long likeCount,
        long bookmarkCount,
        long followerCount,
        long followingCount) {

    public static SocialVO empty() {
        return new SocialVO(false, false, false, 0, 0, 0, 0);
    }
}
