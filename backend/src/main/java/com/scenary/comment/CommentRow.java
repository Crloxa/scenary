package com.scenary.comment;

import java.util.Date;

import lombok.Data;

/** 评论查询行，作者摘要由 SQL 一并取出；不把 user Entity 跨模块传播。 */
@Data
public class CommentRow {

    private Long id;
    private Long noteId;
    private Long userId;
    private Long parentId;
    private String content;
    private Integer status;
    private Date createdAt;
    private Date updatedAt;
    private Date deletedAt;
    private String authorNickname;
    private String authorAvatarUrl;
}
