package com.scenary.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.scenary.common.AuthorVO;
import com.scenary.user.UserService;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private UserService userService;

    @Test
    void pageUsesDescendingCursorAndReturnsUnreadCount() {
        NotificationService service = new NotificationService(notificationMapper, userService);
        NotificationRow row = new NotificationRow();
        row.setId(8L);
        row.setActorId(7L);
        row.setActorNickname("山间来客");
        row.setType("LIKE");
        row.setNoteId(9L);
        row.setNoteTitle("山");
        row.setCreatedAt(new Date(100L));
        when(notificationMapper.selectPage(11L, Long.MAX_VALUE, 3)).thenReturn(List.of(row));
        when(notificationMapper.countUnread(11L)).thenReturn(4L);

        NotificationPage page = service.page(11L, null, 2);

        assertEquals(1, page.list().size());
        assertEquals(NotificationType.LIKE, page.list().get(0).type());
        assertEquals(4L, page.unreadCount());
        assertEquals(7L, page.list().get(0).actor().id());
        verify(notificationMapper).countUnread(11L);
    }

    @Test
    void emptyIdsMarkAllAndSpecificIdsOnlyTouchCurrentUser() {
        NotificationService service = new NotificationService(notificationMapper, userService);

        service.markRead(11L, new NotificationReadRequest(List.of()));
        service.markRead(11L, new NotificationReadRequest(List.of(8L, 9L)));

        verify(notificationMapper).markAllRead(11L);
        verify(notificationMapper).markRead(11L, List.of(8L, 9L));
    }
}
