package com.scenary.mq;

import java.io.IOException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.scenary.config.RabbitConfig;
import com.scenary.media.MediaMapper;
import com.scenary.media.VideoTranscodeService;

/** 独立视频转码消费者；单个毒消息只进入 video.dlq，不占住图片缩略图队列。 */
@Component
public class VideoTranscodeConsumer {

    private static final Logger log = LoggerFactory.getLogger(VideoTranscodeConsumer.class);

    private final VideoTranscodeService transcodeService;
    private final MediaMapper mediaMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoTranscodeConsumer(VideoTranscodeService transcodeService, MediaMapper mediaMapper) {
        this.transcodeService = transcodeService;
        this.mediaMapper = mediaMapper;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_VIDEO_TRANSCODE, concurrency = "1")
    public void onVideoTranscode(Message message, Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        Long mediaId = extractMediaId(message);
        if (mediaId == null) {
            log.warn("malformed video.transcode payload, dead-lettering");
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        try {
            transcodeService.transcode(mediaId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("video transcode failed after validation, mediaId={} -> dlq", mediaId, e);
            markFailure(mediaId, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private Long extractMediaId(Message message) {
        try {
            JsonNode root = objectMapper.readTree(message.getBody());
            long id = root.path("mediaId").asLong(0);
            return id > 0 ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void markFailure(long mediaId, Exception e) {
        String reason = e.getClass().getSimpleName();
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            reason += ": " + e.getMessage();
        }
        if (reason.length() > 255) {
            reason = reason.substring(0, 255);
        }
        try {
            mediaMapper.updateVideoFailureResult(mediaId, reason, new Date());
        } catch (Exception updateError) {
            log.warn("failed to persist video failure state, mediaId={}", mediaId, updateError);
        }
    }
}
