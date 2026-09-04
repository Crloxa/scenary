package com.scenary.comment;

import com.scenary.common.AuthorVO;

/** 评论条目；已删除评论以 status=2 和固定占位文案保留位置。 */
public record CommentVO(
        long id,
        long noteId,
        Long parentId,
        String content,
        int status,
        AuthorVO author,
        long createdAt,
        Long updatedAt,
        boolean mine,
        boolean canDelete) {
}
