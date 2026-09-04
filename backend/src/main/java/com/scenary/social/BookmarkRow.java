package com.scenary.social;

import lombok.Data;

import java.util.Date;

/** 我的收藏查询行，避免把其他模块 Entity 带出边界。 */
@Data
public class BookmarkRow {
    private Long bookmarkId;
    private Long noteId;
    private String title;
    private String content;
    private String coverUrl;
    private Integer mediaCount;
    private Long authorId;
    private String authorNickname;
    private String authorAvatarUrl;
    private Date createdAt;
}
