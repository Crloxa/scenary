package com.scenary.comment;

import java.util.Date;

import lombok.Data;

/** 评论表实体，仅限 comment 模块内部使用。 */
@Data
public class CommentEntity {

    private Long id;
    private Long noteId;
    private Long userId;
    private Long parentId;
    private String content;
    /** 1 正常，2 已删除 */
    private Integer status;
    private Date createdAt;
    private Date updatedAt;
    private Date deletedAt;
}
