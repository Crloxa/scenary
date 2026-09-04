package com.scenary.notification;

import com.scenary.common.AuthorVO;

/** 对外通知条目。 */
public record NotificationVO(
        long id,
        NotificationType type,
        AuthorVO actor,
        Long noteId,
        Long commentId,
        String noteTitle,
        String commentPreview,
        Long readAt,
        long createdAt) {
}
