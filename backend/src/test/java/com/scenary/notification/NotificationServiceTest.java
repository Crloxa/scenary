package com.scenary.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
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
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void pageUsesDescendingCursorAndReturnsUnreadCount() {
        NotificationService service = new NotificationService(notificationMapper, userService, eventPublisher);
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
        NotificationService service = new NotificationService(notificationMapper, userService, eventPublisher);

        service.markRead(11L, new NotificationReadRequest(List.of()));
        service.markRead(11L, new NotificationReadRequest(List.of(8L, 9L)));

        verify(notificationMapper).markAllRead(11L);
        verify(notificationMapper).markRead(11L, List.of(8L, 9L));
    }

    @Test
    void purgeBatchesUntilExhaustedAndDryRunCountsOnly() {
        NotificationService service = new NotificationService(notificationMapper, userService, eventPublisher);
        when(notificationMapper.deleteReadOlderThan(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(500))).thenReturn(500, 120, 0);
        when(notificationMapper.countReadOlderThan(org.mockito.ArgumentMatchers.anyLong())).thenReturn(620L);

        long purged = service.purgeOldReadNotifications(90);

        assertEquals(620L, purged);
        verify(notificationMapper, org.mockito.Mockito.times(2))
                .deleteReadOlderThan(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.eq(500));
        assertEquals(620L, service.countOldReadNotifications(90));
    }
}
