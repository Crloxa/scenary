package com.scenary.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知保留策略（P15-05，docs/02 §5.9）：定时清理"已读且超过保留期"的通知，
 * 与 V8 会话清理同为 @Scheduled 兜底任务。未读通知不清理，避免丢提醒。
 */
@Component
public class NotificationCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupScheduler.class);

    private final NotificationService notificationService;

    @Value("${scenary.notification.cleanup-enabled:true}")
    boolean cleanupEnabled;

    @Value("${scenary.notification.retention-days:90}")
    int retentionDays;

    public NotificationCleanupScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000L, initialDelay = 10 * 60 * 1000L)
    public void purgeExpiredRead() {
        if (!cleanupEnabled || retentionDays <= 0) {
            return;
        }
        long purged = notificationService.purgeOldReadNotifications(retentionDays);
        if (purged > 0) {
            log.info("通知保留策略：清理已读超 {} 天通知 {} 条", retentionDays, purged);
        }
    }
}
