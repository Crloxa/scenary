package com.scenary.media;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期补发短暂不可用时遗漏的 media.uploaded 消息。 */
@Component
public class MediaPublishRetryScheduler {

    private final MediaService mediaService;

    public MediaPublishRetryScheduler(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void retryPendingPublishes() {
        mediaService.retryPendingPublishes();
    }
}
