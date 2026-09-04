package com.scenary.media;

import java.io.InputStream;
import java.time.Duration;
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

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.config.MinioProperties;
import com.scenary.config.RabbitConfig;

/** P12-E1：分片会话、预签名对象、服务端 compose/copy 与完整性校验。 */
@Service
public class VideoUploadService {

    private static final Logger log = LoggerFactory.getLogger(VideoUploadService.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    static final int CHUNK_SIZE = 8 * 1024 * 1024;
    static final int MAX_PARTS = 64;
    static final long MAX_VIDEO_BYTES = 200L * 1024 * 1024;
    static final long SESSION_TTL_MS = Duration.ofHours(2).toMillis();
    static final int SESSION_UPLOADING = 0;
    static final int SESSION_MERGING = 1;
    static final int SESSION_COMPLETED = 2;
    static final int SESSION_EXPIRED = 3;

    private final MinioProperties minioProps;
    private final MinioService minio;
    private final MediaMapper mediaMapper;
    private final VideoUploadSessionMapper sessionMapper;
    private final RabbitTemplate rabbitTemplate;

    public VideoUploadService(MinioProperties minioProps, MinioService minio,
                              MediaMapper mediaMapper, VideoUploadSessionMapper sessionMapper,
                              RabbitTemplate rabbitTemplate) {
        this.minioProps = minioProps;
        this.minio = minio;
        this.mediaMapper = mediaMapper;
        this.sessionMapper = sessionMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    public VideoUploadSessionVO create(long userId, VideoUploadCreateRequest request) {
        validateCreate(request);
        long size = request.sizeBytes();
        int totalParts = (int) ((size + CHUNK_SIZE - 1) / CHUNK_SIZE);
        if (totalParts > MAX_PARTS) {
            throw new BizException(ErrorCode.VALIDATION, "视频分片数量超过限制");
        }

        String uploadId = UUID.randomUUID().toString();
        Date expiresAt = new Date(System.currentTimeMillis() + SESSION_TTL_MS);
        VideoUploadSessionEntity session = new VideoUploadSessionEntity();
        session.setUploadId(uploadId);
        session.setUserId(userId);
        session.setOriginalName(request.fileName().trim());
        session.setDeclaredMime(blankToNull(request.mime()));
        session.setSizeBytes(size);
        session.setTotalParts(totalParts);
        session.setStatus(SESSION_UPLOADING);
        session.setExpiresAt(expiresAt);
        sessionMapper.insertSession(session);

        List<VideoUploadPartEntity> parts = new ArrayList<>(totalParts);
        for (int number = 1; number <= totalParts; number++) {
            VideoUploadPartEntity part = new VideoUploadPartEntity();
            part.setUploadId(uploadId);
            part.setPartNumber(number);
            part.setObjectKey(partKey(uploadId, number));
            parts.add(part);
        }
        try {
            sessionMapper.insertParts(parts);
        } catch (RuntimeException e) {
            sessionMapper.deleteSession(uploadId);
            throw e;
        }
        return new VideoUploadSessionVO(uploadId, CHUNK_SIZE, totalParts, SESSION_UPLOADING,
                expiresAt.getTime(), List.of());
    }

    public VideoUploadSessionVO get(long userId, String uploadId) {
        VideoUploadSessionEntity session = requireOwned(userId, uploadId);
        expireIfDue(session);
        return toSessionVO(session, sessionMapper.findParts(uploadId));
    }

    public VideoUploadPartUrlVO presignPart(long userId, String uploadId, int partNumber) {
        VideoUploadSessionEntity session = requireOwned(userId, uploadId);
        ensureActive(session);
        if (partNumber < 1 || partNumber > session.getTotalParts()) {
            throw new BizException(ErrorCode.VALIDATION, "分片序号超出范围");
        }
        VideoUploadPartEntity part = sessionMapper.findParts(uploadId).stream()
                .filter(item -> item.getPartNumber() == partNumber)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        long expiresAt = System.currentTimeMillis() + Duration.ofMinutes(15).toMillis();
        return new VideoUploadPartUrlVO(partNumber, minio.presignPut(part.getObjectKey(), 15 * 60), expiresAt);
    }

    public MediaUploadVO complete(long userId, String uploadId) {
        VideoUploadSessionEntity session = requireOwned(userId, uploadId);
        expireIfDue(session);
        if (session.getStatus() == SESSION_COMPLETED && session.getCompletedMediaId() != null) {
            return new MediaUploadVO(List.of(toItem(mediaMapper.findById(session.getCompletedMediaId()))));
        }
        if (session.getStatus() == SESSION_EXPIRED) {
            throw new BizException(ErrorCode.UPLOAD_SESSION_EXPIRED);
        }
        if (sessionMapper.claimMerging(uploadId, new Date()) != 1) {
            VideoUploadSessionEntity current = requireOwned(userId, uploadId);
            if (current.getStatus() == SESSION_COMPLETED && current.getCompletedMediaId() != null) {
                return new MediaUploadVO(List.of(toItem(mediaMapper.findById(current.getCompletedMediaId()))));
            }
            if (current.getStatus() == SESSION_EXPIRED) {
                throw new BizException(ErrorCode.UPLOAD_SESSION_EXPIRED);
            }
            throw new BizException(ErrorCode.VALIDATION, "上传会话正在合并，请稍后重试");
        }

        String finalKey = null;
        boolean mediaInserted = false;
        Long mediaId = null;
        try {
            List<VideoUploadPartEntity> parts = sessionMapper.findParts(uploadId);
            validatePartCount(session, parts);
            List<String> sourceKeys = new ArrayList<>(parts.size());
            long total = 0;
            for (VideoUploadPartEntity part : parts) {
                long actualSize = minio.statSize(part.getObjectKey());
                validatePartSize(session, part.getPartNumber(), actualSize);
                sessionMapper.updatePartObservation(uploadId, part.getPartNumber(), actualSize);
                sourceKeys.add(part.getObjectKey());
                total += actualSize;
            }
            if (total != session.getSizeBytes()) {
                throw new BizException(ErrorCode.UPLOAD_INCOMPLETE, "分片总大小与会话声明不一致");
            }

            MediaVideoType type = detectType(sourceKeys.get(0));
            if (type == null) {
                throw new BizException(ErrorCode.VALIDATION,
                        "仅支持 MP4/MOV/WEBM 视频，且文件内容必须通过容器校验");
            }
            finalKey = "orig/" + MONTH.format(LocalDate.now()) + "/" + UUID.randomUUID() + "." + type.ext();
            if (sourceKeys.size() == 1) {
                minio.copy(sourceKeys.get(0), finalKey);
            } else {
                minio.compose(finalKey, sourceKeys);
            }

            MediaEntity media = new MediaEntity();
            media.setUserId(userId);
            media.setOrderNo(1);
            media.setBucket(minioProps.getBucket());
            media.setObjectKey(finalKey);
            media.setUrl(minio.publicUrl(finalKey));
            media.setMime(type.mime());
            media.setMediaType(MediaType.VIDEO.code());
            media.setSizeBytes(total);
            media.setStatus(MediaService.VIDEO_PROCESSING);
            mediaMapper.insert(media);
            mediaInserted = true;
            mediaId = media.getId();
            if (sessionMapper.markCompleted(uploadId, media.getId()) != 1) {
                throw new IllegalStateException("upload session changed before completion");
            }
            publish(media.getId());
            removeParts(parts);
            return new MediaUploadVO(List.of(toItem(media)));
        } catch (BizException e) {
            if (finalKey != null) removeQuietly(finalKey);
            if (!mediaInserted) sessionMapper.releaseMerging(uploadId);
            else if (mediaId != null) mediaMapper.deleteStaleUnbound(mediaId);
            throw e;
        } catch (Exception e) {
            if (finalKey != null) removeQuietly(finalKey);
            if (!mediaInserted) sessionMapper.releaseMerging(uploadId);
            else if (mediaId != null) mediaMapper.deleteStaleUnbound(mediaId);
            log.error("complete video upload failed, uploadId={}", uploadId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "视频合并失败");
        }
    }

    public void cancel(long userId, String uploadId) {
        VideoUploadSessionEntity session = requireOwned(userId, uploadId);
        if (session.getStatus() == SESSION_COMPLETED) {
            throw new BizException(ErrorCode.VALIDATION, "已完成的上传会话不能取消");
        }
        deleteSessionObjects(uploadId, sessionMapper.findParts(uploadId));
    }

    public void cleanupExpired() {
        Date now = new Date();
        for (VideoUploadSessionEntity session : sessionMapper.selectExpired(now, 100)) {
            if (session.getStatus() == SESSION_EXPIRED
                    || sessionMapper.markExpired(session.getUploadId(), now) == 1) {
                deleteSessionObjects(session.getUploadId(), sessionMapper.findParts(session.getUploadId()));
            }
        }
    }

    private void validateCreate(VideoUploadCreateRequest request) {
        if (request == null || request.fileName() == null || request.fileName().isBlank()) {
            throw new BizException(ErrorCode.VALIDATION, "视频文件名不能为空");
        }
        if (request.fileName().trim().length() > 255) {
            throw new BizException(ErrorCode.VALIDATION, "视频文件名过长");
        }
        if (request.sizeBytes() == null || request.sizeBytes() <= 0 || request.sizeBytes() > MAX_VIDEO_BYTES) {
            throw new BizException(ErrorCode.VALIDATION, "单个视频不能超过 200MB");
        }
    }

    private VideoUploadSessionEntity requireOwned(long userId, String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        VideoUploadSessionEntity session = sessionMapper.findById(uploadId);
        if (session == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (session.getUserId() != userId) throw new BizException(ErrorCode.FORBIDDEN);
        return session;
    }

    private void ensureActive(VideoUploadSessionEntity session) {
        expireIfDue(session);
        if (session.getStatus() == SESSION_EXPIRED) {
            throw new BizException(ErrorCode.UPLOAD_SESSION_EXPIRED);
        }
        if (session.getStatus() != SESSION_UPLOADING) {
            throw new BizException(ErrorCode.VALIDATION, "上传会话当前不可写入分片");
        }
    }

    private void expireIfDue(VideoUploadSessionEntity session) {
        if (session.getStatus() != SESSION_COMPLETED
                && session.getExpiresAt() != null && !session.getExpiresAt().after(new Date())) {
            sessionMapper.markExpired(session.getUploadId(), new Date());
            session.setStatus(SESSION_EXPIRED);
        }
    }

    private VideoUploadSessionVO toSessionVO(VideoUploadSessionEntity session,
                                             List<VideoUploadPartEntity> parts) {
        List<VideoUploadPartVO> uploaded = new ArrayList<>();
        if (parts != null) {
            for (VideoUploadPartEntity part : parts) {
                long size = minio.statSize(part.getObjectKey());
                if (size >= 0) {
                    uploaded.add(new VideoUploadPartVO(part.getPartNumber(), size));
                    if (!Long.valueOf(size).equals(part.getSizeBytes())) {
                        sessionMapper.updatePartObservation(session.getUploadId(), part.getPartNumber(), size);
                    }
                }
            }
        }
        return new VideoUploadSessionVO(session.getUploadId(), CHUNK_SIZE, session.getTotalParts(),
                session.getStatus(), session.getExpiresAt() == null ? null : session.getExpiresAt().getTime(), uploaded);
    }

    private void validatePartCount(VideoUploadSessionEntity session, List<VideoUploadPartEntity> parts) {
        if (parts == null || parts.size() != session.getTotalParts()) {
            throw new BizException(ErrorCode.UPLOAD_INCOMPLETE, "上传分片记录不完整");
        }
    }

    private void validatePartSize(VideoUploadSessionEntity session, int partNumber, long actualSize) {
        if (actualSize < 0) {
            throw new BizException(ErrorCode.UPLOAD_INCOMPLETE, "缺少第 " + partNumber + " 个分片");
        }
        long expected = partNumber == session.getTotalParts()
                ? session.getSizeBytes() - (long) (session.getTotalParts() - 1) * CHUNK_SIZE
                : CHUNK_SIZE;
        if (actualSize != expected) {
            throw new BizException(ErrorCode.UPLOAD_INCOMPLETE, "第 " + partNumber + " 个分片大小不正确");
        }
    }

    private MediaVideoType detectType(String objectKey) {
        try (InputStream input = minio.get(objectKey)) {
            return MediaVideoType.detect(input);
        } catch (Exception e) {
            throw new BizException(ErrorCode.UPLOAD_INCOMPLETE, "无法读取已上传分片");
        }
    }

    private String partKey(String uploadId, int partNumber) {
        return "upload/video/" + uploadId + "/part-" + String.format("%05d", partNumber);
    }

    private void publish(long mediaId) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_MEDIA_EVENT,
                    RabbitConfig.RK_VIDEO_TRANSCODE, Map.of("mediaId", mediaId));
            mediaMapper.markPublishSuccess(mediaId);
        } catch (Exception e) {
            mediaMapper.markPublishFailure(mediaId, e.getClass().getSimpleName());
            log.error("publish video.transcode failed, mediaId={}", mediaId, e);
        }
    }

    private MediaItemVO toItem(MediaEntity media) {
        return new MediaItemVO(media.getId(), MediaType.from(media.getMediaType()).label(),
                minio.displayUrl(media), media.getThumbUrl(), media.getStatus(), media.getWidth(),
                media.getHeight(), media.getDurationMs(), media.getPlaybackUrl(), media.getPlaybackLowUrl());
    }

    private void removeParts(List<VideoUploadPartEntity> parts) {
        if (parts == null) return;
        for (VideoUploadPartEntity part : parts) removeQuietly(part.getObjectKey());
    }

    private void deleteSessionObjects(String uploadId, List<VideoUploadPartEntity> parts) {
        removeParts(parts);
        sessionMapper.deleteSession(uploadId);
    }

    private void removeQuietly(String objectKey) {
        try {
            minio.remove(objectKey);
        } catch (Exception e) {
            log.warn("deferred upload object cleanup, objectKey={}", objectKey);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
