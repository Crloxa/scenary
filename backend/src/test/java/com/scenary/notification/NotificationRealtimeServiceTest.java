package com.scenary.notification;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRealtimeServiceTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private NotificationWebSocketHandler webSocketHandler;

    @Test
    void pushesOnlyWithCurrentUnreadCountAfterNotificationCommitEvent() {
        when(notificationMapper.countUnread(11L)).thenReturn(3L);

        NotificationRealtimeService service = new NotificationRealtimeService(notificationMapper, webSocketHandler);
        service.onNotificationCreated(new NotificationCreatedEvent(11L));

        verify(notificationMapper).countUnread(11L);
        verify(webSocketHandler).push(11L, 3L);
    }
}
