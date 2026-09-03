package com.scenary.media;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 清理超过一天仍未绑定笔记的失败/处理中媒体及其对象。 */
@Component
public class MediaCleanupScheduler {

    private final MediaService mediaService;

    public MediaCleanupScheduler(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000L, initialDelay = 60 * 60 * 1000L)
    public void cleanupStaleUnbound() {
        mediaService.cleanupStaleUnbound();
    }
}
