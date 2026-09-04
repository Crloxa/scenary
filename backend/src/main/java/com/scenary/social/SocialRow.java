package com.scenary.social;

import lombok.Data;

/** MyBatis 查询出的单笔记社交聚合，模块私有。 */
@Data
public class SocialRow {
    private Long noteId;
    private Boolean liked;
    private Boolean bookmarked;
    private Boolean following;
    private Long likeCount;
    private Long bookmarkCount;
    private Long followerCount;
    private Long followingCount;
}
