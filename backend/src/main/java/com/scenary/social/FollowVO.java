package com.scenary.social;

/**
 * 关注者/正在关注列表项（docs/02 §3.9/§3.10，P16-02）。
 * id 为用户 id；following 为登录视角的 viewer→成员 关注态（匿名恒 false）。
 */
public record FollowVO(long id, String nickname, String avatarUrl, boolean following) {
}
