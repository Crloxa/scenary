package com.scenary.mq;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;

import javax.imageio.ImageIO;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.scenary.config.RabbitConfig;
import com.scenary.media.MediaEntity;
import com.scenary.media.MediaMapper;
import com.scenary.media.MinioService;

import net.coobird.thumbnailator.Thumbnails;

/**
 * 缩略图消费者（docs/01 §5.2）：手动 ack；解析失败或最终处理失败统一
 * basicNack(requeue=false)，经队列 x-dead-letter 参数落 media.dlq 留档，绝不 requeue 循环。
 * 缩略图强制重编码为 JPEG q0.8 —— 重编码天然剥离 EXIF/GPS 元数据（隐私模型，docs/01 §5.4）。
 */
@Component
public class ThumbnailConsumer {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailConsumer.class);
    private static final int MAX_EDGE = 800;
    private static final int LOCAL_RETRIES = 2;

    private final MinioService minio;
    private final MediaMapper mediaMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ThumbnailConsumer(MinioService minio, MediaMapper mediaMapper) {
        this.minio = minio;
        this.mediaMapper = mediaMapper;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_MEDIA_THUMBNAIL)
    public void onMediaUploaded(Message message, Channel channel,
                                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {
        Long mediaId = extractMediaId(message);
        if (mediaId == null) {
            log.warn("malformed media.uploaded payload, dead-lettering: {}",
                    new String(message.getBody()));
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        try {
            processWithLocalRetry(mediaId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("thumbnail failed after retries, mediaId={} -> dlq", mediaId, e);
            markFailure(mediaId, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private Long extractMediaId(Message message) {
        try {
            JsonNode root = objectMapper.readTree(message.getBody());
            if (!root.has("mediaId")) {
                return null;
            }
            long id = root.get("mediaId").asLong();
            return id > 0 ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 本地即时重试 2 次（覆盖瞬时 IO 抖动），仍失败抛给外层走死信 */
    private void processWithLocalRetry(long mediaId) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= LOCAL_RETRIES; attempt++) {
            try {
                process(mediaId);
                return;
            } catch (Exception e) {
                last = e;
                log.warn("attempt {}/{} failed for mediaId={}", attempt, LOCAL_RETRIES, mediaId, e);
            }
        }
        throw last;
    }

    private void process(long mediaId) throws Exception {
        MediaEntity media = mediaMapper.findById(mediaId);
        if (media == null) {
            throw new IllegalStateException("media row missing: " + mediaId);
        }
        // RabbitMQ 至少一次投递：已完成或已最终失败的重复消息直接确认，避免重复写对象/反复告警。
        if (media.getStatus() != null && media.getStatus() != 0) {
            log.info("thumbnail message already settled, mediaId={} status={}", mediaId, media.getStatus());
            return;
        }
        BufferedImage src = ImageIO.read(minio.get(media.getObjectKey()));
        String thumbKey = thumbKeyOf(media.getObjectKey());
        if (src == null) {
            // JDK 解不了的格式不能把原图复制为公开缩略图，否则会绕过原图隐私策略。
            throw new IllegalStateException("unsupported image encoding");
        }
        int w = src.getWidth();
        int h = src.getHeight();
        // ≤800 限边且不放大：超界等比缩小，小图保持原尺寸
        double scale = Math.min(1.0, Math.min((double) MAX_EDGE / w, (double) MAX_EDGE / h));
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Thumbnails.of(src).size(tw, th).outputQuality(0.8).outputFormat("jpg")
                .toOutputStream(buffer);

        minio.put(thumbKey, new ByteArrayInputStream(buffer.toByteArray()),
                buffer.size(), "image/jpeg");
        mediaMapper.updateProcessResult(mediaId, 1, thumbKey,
                minio.publicUrl(thumbKey), tw, th);
        log.info("thumb ready mediaId={} {}x{} bytes={}", mediaId, tw, th, buffer.size());
    }

    /** orig/{yyyyMM}/{uuid}.{ext} -> thumb/{yyyyMM}/{uuid}_t.jpg（与原图同 uuid 成对） */
    static String thumbKeyOf(String objectKey) {
        return MinioService.thumbKeyOf(objectKey);
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
            mediaMapper.updateFailureResult(mediaId, reason, new Date());
        } catch (Exception updateError) {
            log.warn("failed to persist failure state, mediaId={}", mediaId, updateError);
        }
    }
}
