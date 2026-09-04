package com.scenary.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 只在通知事务提交成功后推送刷新信号，避免客户端看到回滚事件。 */
@Service
public class NotificationRealtimeService {

    private final NotificationMapper notificationMapper;
    private final NotificationWebSocketHandler webSocketHandler;

    public NotificationRealtimeService(NotificationMapper notificationMapper,
                                       NotificationWebSocketHandler webSocketHandler) {
        this.notificationMapper = notificationMapper;
        this.webSocketHandler = webSocketHandler;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        webSocketHandler.push(event.recipientId(), notificationMapper.countUnread(event.recipientId()));
    }
}
