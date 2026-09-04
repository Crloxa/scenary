package com.scenary.notification;

/** 通知事务提交后的实时刷新事件，不携带正文或令牌。 */
public record NotificationCreatedEvent(long recipientId) {
}
