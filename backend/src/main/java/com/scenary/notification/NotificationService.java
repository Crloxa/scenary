package com.scenary.notification;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scenary.common.AuthorVO;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.user.UserService;

/** 通知门面：事件与评论/社交写入共享事务，查询失败不影响其他页面。 */
@Service
public class NotificationService {

    private static final String DELETED_CONTENT = "该评论已删除";

    private final NotificationMapper notificationMapper;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(NotificationMapper notificationMapper, UserService userService,
                               ApplicationEventPublisher eventPublisher) {
        this.notificationMapper = notificationMapper;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    public void create(long recipientId, long actorId, NotificationType type,
                       Long noteId, Long commentId) {
        if (recipientId == actorId) {
            return;
        }
        notificationMapper.insert(recipientId, actorId, type.name(), noteId, commentId);
        eventPublisher.publishEvent(new NotificationCreatedEvent(recipientId));
    }

    /** 保留策略（P15-05）：分批删除已读且超过保留期(天)的通知，返回清理总数。 */
    @Transactional
    public long purgeOldReadNotifications(int retentionDays) {
        long cutoff = System.currentTimeMillis() - retentionDays * 24L * 3600 * 1000;
        long total = 0;
        int batch;
        do {
            batch = notificationMapper.deleteReadOlderThan(cutoff, 500);
            total += batch;
        } while (batch == 500);
        return total;
    }

    /** dry-run 统计：当前将命中保留策略的通知量。 */
    public long countOldReadNotifications(int retentionDays) {
        long cutoff = System.currentTimeMillis() - retentionDays * 24L * 3600 * 1000;
        return notificationMapper.countReadOlderThan(cutoff);
    }

    public NotificationPage page(long userId, Long cursorParam, Integer limitParam) {
        userService.ensureActive(userId);
        int limit = clampLimit(limitParam);
        long cursor = cursorParam == null ? Long.MAX_VALUE : cursorParam;
        List<NotificationRow> rows = notificationMapper.selectPage(userId, cursor, limit + 1);
        List<NotificationRow> pageRows = rows.stream().limit(limit).toList();
        List<NotificationVO> items = pageRows.stream().map(this::toVO).toList();
        boolean hasMore = rows.size() > limit;
        Long nextCursor = hasMore ? pageRows.get(pageRows.size() - 1).getId() : null;
        return new NotificationPage(items, nextCursor, hasMore, notificationMapper.countUnread(userId));
    }

    @Transactional
    public void markRead(long userId, NotificationReadRequest request) {
        userService.ensureActive(userId);
        List<Long> ids = request.ids();
        if (ids == null) {
            throw new BizException(ErrorCode.VALIDATION, "ids 不能为空");
        }
        if (ids.isEmpty()) {
            notificationMapper.markAllRead(userId);
        } else {
            notificationMapper.markRead(userId, ids);
        }
    }

    private NotificationVO toVO(NotificationRow row) {
        NotificationType type = NotificationType.valueOf(row.getType());
        String nickname = row.getActorNickname() == null ? "已注销" : row.getActorNickname();
        String content = row.getCommentContent();
        String preview = content == null ? null : content.strip();
        if (row.getCommentId() != null && !Integer.valueOf(1).equals(row.getCommentStatus())) {
            preview = DELETED_CONTENT;
        } else if (preview != null && preview.length() > 48) {
            preview = preview.substring(0, 48) + "…";
        }
        Long readAt = row.getReadAt() == null ? null : row.getReadAt().getTime();
        long createdAt = row.getCreatedAt() == null ? 0L : row.getCreatedAt().getTime();
        return new NotificationVO(row.getId(), type,
                new AuthorVO(row.getActorId() == null ? 0L : row.getActorId(), nickname,
                        userService.viewUrl(row.getActorAvatarUrl())),
                row.getNoteId(), row.getCommentId(), row.getNoteTitle(), preview,
                readAt, createdAt);
    }

    static int clampLimit(Integer limitParam) {
        if (limitParam == null) {
            return 10;
        }
        return Math.max(1, Math.min(20, limitParam));
    }
}
