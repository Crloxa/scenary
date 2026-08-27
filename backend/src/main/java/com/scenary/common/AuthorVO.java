package com.scenary.common;

/**
 * 作者摘要视图：feed 卡片与笔记详情共用（docs/02 §6.1/§5.2），故升入 common 供跨包引用。
 */
public record AuthorVO(long id, String nickname, String avatarUrl) {
}
