package com.scenary.media;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.imaging.ImageMetadataReader;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.config.MinioProperties;
import com.scenary.config.RabbitConfig;

/**
 * 上传管线（docs/01 §5.2）：嗅探魔数 -> 存原图 -> 建 media -> 发对应 MQ 即返回。
 * 消息发送失败不回滚上传：媒体停留在 PROCESSING，由定时重试与清理策略兜底，避免整批作废。
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024;
    static final int VIDEO_PROCESSING = 11;
    static final int VIDEO_READY = 12;
    static final int VIDEO_FAILED = 13;

    private final MinioProperties minioProps;
    private final MinioService minio;
    private final MediaMapper mediaMapper;
    private final RabbitTemplate rabbitTemplate;

    public MediaService(MinioProperties minioProps, MinioService minio,
                        MediaMapper mediaMapper, RabbitTemplate rabbitTemplate) {
        this.minioProps = minioProps;
        this.minio = minio;
        this.mediaMapper = mediaMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    public MediaUploadVO uploadImages(long userId, List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > 9) {
            throw new BizException(ErrorCode.VALIDATION, "图片数量须为 1~9 张");
        }
        String month = MONTH.format(LocalDate.now());
        List<MediaItemVO> items = new ArrayList<>(files.size());
        List<Long> createdMediaIds = new ArrayList<>(files.size());
        int order = 1;
        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    throw new BizException(ErrorCode.VALIDATION, "包含空文件");
                }
                if (file.getSize() > MAX_FILE_BYTES) {
                    throw new BizException(ErrorCode.VALIDATION, "单张图片不能超过 10MB");
                }
                MediaEntity media = store(userId, file, order++, month);
                createdMediaIds.add(media.getId());
                publish(media.getId(), MediaType.IMAGE);
                items.add(toItem(media));
            }
        } catch (RuntimeException e) {
            // 批量请求是一个用户操作：后续文件失败时，回收本次请求已经创建的游离媒体。
            cleanupCreatedMedia(userId, createdMediaIds);
            throw e;
        }
        return new MediaUploadVO(items);
    }

    public MediaUploadVO uploadVideo(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION, "视频文件不能为空");
        }
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw new BizException(ErrorCode.VALIDATION, "单个视频不能超过 200MB");
        }
        MediaEntity media = storeVideo(userId, file, MONTH.format(LocalDate.now()));
        try {
            publish(media.getId(), MediaType.VIDEO);
        } catch (RuntimeException e) {
            cleanupCreatedMedia(userId, List.of(media.getId()));
            throw e;
        }
        return new MediaUploadVO(List.of(toItem(media)));
    }

    public MediaItemVO getStatus(long userId, long mediaId) {
        MediaEntity media = requireOwned(userId, mediaId);
        return toItem(media);
    }

    public void deleteUnbound(long userId, long mediaId) {
        MediaEntity media = requireOwned(userId, mediaId);
        if (media.getNoteId() != null) {
            throw new BizException(ErrorCode.VALIDATION, "已绑定笔记的媒体请通过删除笔记处理");
        }
        // 对象文件按契约保留（docs/02 §4.3），物理清理属于二期后台任务
        mediaMapper.deleteUnbound(mediaId, userId);
    }

    private MediaEntity requireOwned(long userId, long mediaId) {
        MediaEntity media = mediaMapper.findById(mediaId);
        if (media == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (media.getUserId() != userId) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return media;
    }

    private MediaEntity store(long userId, MultipartFile file, int order, String month) {
        String objectKey = null;
        try (InputStream raw = file.getInputStream()) {
            BufferedInputStream in = new BufferedInputStream(raw);
            in.mark(MediaImageType.sniffBytes() + 1);
            MediaImageType type = MediaImageType.detect(MediaImageType.readHead(in));
            in.reset();
            if (type == null || type == MediaImageType.WEBP) {
                throw new BizException(ErrorCode.VALIDATION,
                        "仅支持 jpeg/png/gif 图片，且文件内容须与扩展名一致");
            }
            objectKey = "orig/" + month + "/" + UUID.randomUUID() + "." + type.ext();
            minio.put(objectKey, in, file.getSize(), type.mime());

            MediaEntity media = new MediaEntity();
            media.setUserId(userId);
            media.setOrderNo(order);
            media.setBucket(minioProps.getBucket());
            media.setObjectKey(objectKey);
            media.setUrl(minio.publicUrl(objectKey));
            media.setMime(type.mime());
            media.setMediaType(MediaType.IMAGE.code());
            media.setSizeBytes(file.getSize());
            media.setStatus(0);
            ExifLocation exif = readExif(file);
            if (exif != null) {
                media.setExifLatitude(exif.latitude());
                media.setExifLongitude(exif.longitude());
            }
            mediaMapper.insert(media);
            return media;
        } catch (BizException e) {
            compensateObject(objectKey);
            throw e;
        } catch (Exception e) {
            compensateObject(objectKey);
            log.error("store image failed", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "图片处理失败");
        }
    }

    private MediaEntity storeVideo(long userId, MultipartFile file, String month) {
        String objectKey = null;
        try (InputStream raw = file.getInputStream()) {
            BufferedInputStream in = new BufferedInputStream(raw);
            in.mark(MediaVideoType.sniffBytes() + 1);
            MediaVideoType type = MediaVideoType.detect(in);
            in.reset();
            if (type == null) {
                throw new BizException(ErrorCode.VALIDATION,
                        "仅支持 MP4/MOV/WEBM 视频，且文件内容必须通过容器校验");
            }
            objectKey = "orig/" + month + "/" + UUID.randomUUID() + "." + type.ext();
            minio.put(objectKey, in, file.getSize(), type.mime());

            MediaEntity media = new MediaEntity();
            media.setUserId(userId);
            media.setOrderNo(1);
            media.setBucket(minioProps.getBucket());
            media.setObjectKey(objectKey);
            media.setUrl(minio.publicUrl(objectKey));
            media.setMime(type.mime());
            media.setMediaType(MediaType.VIDEO.code());
            media.setSizeBytes(file.getSize());
            media.setStatus(VIDEO_PROCESSING);
            mediaMapper.insert(media);
            return media;
        } catch (BizException e) {
            compensateObject(objectKey);
            throw e;
        } catch (Exception e) {
            compensateObject(objectKey);
            log.error("store video failed", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "视频上传失败");
        }
    }

    private void publish(long mediaId, MediaType mediaType) {
        tryPublish(mediaId, mediaType == MediaType.VIDEO
                ? RabbitConfig.RK_VIDEO_TRANSCODE : RabbitConfig.RK_MEDIA_UPLOADED);
    }

    /** 首发与定时重试共用同一抢占逻辑；最多 3 次，失败保持 status=0 可观测。 */
    private void tryPublish(long mediaId, String routingKey) {
        if (mediaMapper.claimPublish(mediaId, new Date()) != 1) {
            return;
        }
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_MEDIA_EVENT,
                    routingKey, Map.of("mediaId", mediaId));
            mediaMapper.markPublishSuccess(mediaId);
        } catch (Exception e) {
            String reason = e.getClass().getSimpleName();
            mediaMapper.markPublishFailure(mediaId, reason.length() > 255 ? reason.substring(0, 255) : reason);
            // 消息失败不假装上传完成：保持 status=0，由 retryPendingPublishes 再尝试，最终由清理任务兜底。
            log.error("publish media.uploaded failed, mediaId={}", mediaId, e);
        }
    }

    public void retryPendingPublishes() {
        Date before = new Date(System.currentTimeMillis() - 30_000L);
        for (MediaEntity media : mediaMapper.selectPendingPublish(before, 20)) {
            tryPublish(media.getId(), MediaType.from(media.getMediaType()) == MediaType.VIDEO
                    ? RabbitConfig.RK_VIDEO_TRANSCODE : RabbitConfig.RK_MEDIA_UPLOADED);
        }
    }

    /** 删除超过保留期且尚未绑定笔记的媒体及其对象；对象删除失败时保留数据库行待下次重试。 */
    public void cleanupStaleUnbound() {
        Date before = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L);
        for (MediaEntity media : mediaMapper.selectStaleUnbound(before, 100)) {
            try {
                if (media.getObjectKey() != null) {
                    minio.remove(media.getObjectKey());
                }
                if (media.getThumbObjectKey() != null
                        && !media.getThumbObjectKey().equals(media.getObjectKey())) {
                    minio.remove(media.getThumbObjectKey());
                }
                removePlaybackObjects(media);
                mediaMapper.deleteStaleUnbound(media.getId());
            } catch (Exception e) {
                log.warn("stale media cleanup deferred, mediaId={}", media.getId(), e);
            }
        }
    }

    private void compensateObject(String objectKey) {
        if (objectKey == null) {
            return;
        }
        try {
            minio.remove(objectKey);
        } catch (Exception cleanupError) {
            log.error("failed to compensate object after DB insert failure, objectKey={}", objectKey, cleanupError);
        }
    }

    private void cleanupCreatedMedia(long userId, List<Long> mediaIds) {
        for (Long mediaId : mediaIds) {
            try {
                MediaEntity media = mediaMapper.findById(mediaId);
                if (media == null || media.getNoteId() != null || media.getUserId() == null
                        || media.getUserId() != userId) {
                    continue;
                }
                if (media.getObjectKey() != null) {
                    minio.remove(media.getObjectKey());
                }
                if (media.getThumbObjectKey() != null
                        && !media.getThumbObjectKey().equals(media.getObjectKey())) {
                    minio.remove(media.getThumbObjectKey());
                }
                removePlaybackObjects(media);
                mediaMapper.deleteStaleUnbound(mediaId);
            } catch (Exception cleanupError) {
                log.error("failed to rollback media upload, mediaId={}", mediaId, cleanupError);
            }
        }
    }

    private MediaItemVO toItem(MediaEntity media) {
        return new MediaItemVO(media.getId(), MediaType.from(media.getMediaType()).label(),
                minio.displayUrl(media), media.getThumbUrl(), media.getStatus(), media.getWidth(),
                media.getHeight(), media.getDurationMs(), media.getPlaybackUrl(), media.getPlaybackLowUrl());
    }

    private void removePlaybackObjects(MediaEntity media) {
        if (media.getPlaybackObjectKey() != null
                && !media.getPlaybackObjectKey().equals(media.getObjectKey())) {
            minio.remove(media.getPlaybackObjectKey());
        }
        if (media.getPlaybackLowObjectKey() != null
                && !media.getPlaybackLowObjectKey().equals(media.getObjectKey())
                && !media.getPlaybackLowObjectKey().equals(media.getPlaybackObjectKey())) {
            minio.remove(media.getPlaybackLowObjectKey());
        }
    }

    private ExifLocation readExif(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(in);
            GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            GeoLocation location = gps == null ? null : gps.getGeoLocation();
            if (location == null || location.isZero()
                    || !Double.isFinite(location.getLatitude()) || !Double.isFinite(location.getLongitude())
                    || location.getLatitude() < -90 || location.getLatitude() > 90
                    || location.getLongitude() < -180 || location.getLongitude() > 180) {
                return null;
            }
            return new ExifLocation(BigDecimal.valueOf(location.getLatitude()).setScale(6,
                    java.math.RoundingMode.HALF_UP), BigDecimal.valueOf(location.getLongitude()).setScale(6,
                    java.math.RoundingMode.HALF_UP));
        } catch (Exception ignored) {
            // EXIF 是可选元数据；格式解析失败不影响图片上传，也不向日志回显原始 metadata。
            return null;
        }
    }

    private record ExifLocation(BigDecimal latitude, BigDecimal longitude) {
    }
}
