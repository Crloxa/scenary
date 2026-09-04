package com.scenary.notification;

import java.util.List;

/** 通知分页额外携带当前未读数，避免导航栏为计数读取整页通知。 */
public record NotificationPage(
        List<NotificationVO> list,
        Long nextCursor,
        boolean hasMore,
        long unreadCount) {
}
