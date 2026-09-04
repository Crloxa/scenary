package com.scenary.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.scenary.common.BizException;
import com.scenary.config.MinioProperties;
import com.scenary.config.RabbitConfig;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class VideoUploadServiceTest {

    @Mock
    private MinioService minio;
    @Mock
    private MediaMapper mediaMapper;
    @Mock
    private VideoUploadSessionMapper sessionMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private VideoUploadService service;

    @BeforeEach
    void setUp() {
        MinioProperties props = new MinioProperties();
        props.setBucket("scenary-media");
        props.setPublicHost("http://example/minio");
        service = new VideoUploadService(props, minio, mediaMapper, sessionMapper, rabbitTemplate);
    }

    @Test
    void createsEightMegabyteSessionAndPartRowsWithoutReadingObjects() {
        VideoUploadSessionVO result = service.create(7L,
                new VideoUploadCreateRequest("clip.mp4", (long) VideoUploadService.CHUNK_SIZE + 10, "video/mp4"));

        assertEquals(VideoUploadService.CHUNK_SIZE, result.chunkSize());
        assertEquals(2, result.totalParts());
        assertEquals(VideoUploadService.SESSION_UPLOADING, result.status());
        assertEquals(0, result.uploadedParts().size());
        verify(sessionMapper).insertSession(any(VideoUploadSessionEntity.class));
        verify(sessionMapper).insertParts(any());
        verify(minio, never()).statSize(any());
    }

    @Test
    void refusesCompleteWhenAChunkIsMissingAndLeavesSessionRetryable() {
        VideoUploadSessionEntity session = session("u-1", 3L, 1, VideoUploadService.SESSION_UPLOADING);
        VideoUploadPartEntity part = part("u-1", 1, "upload/video/u-1/part-00001");
        when(sessionMapper.findById("u-1")).thenReturn(session);
        when(sessionMapper.findParts("u-1")).thenReturn(List.of(part));
        when(sessionMapper.claimMerging(eq("u-1"), any(Date.class))).thenReturn(1);
        when(minio.statSize(part.getObjectKey())).thenReturn(-1L);

        BizException error = assertThrows(BizException.class, () -> service.complete(7L, "u-1"));

        assertEquals(40903, error.getErrorCode().getCode());
        verify(sessionMapper).releaseMerging("u-1");
        verify(minio, never()).copy(any(), any());
        verify(minio, never()).compose(any(), any());
    }

    @Test
    void refusesForeignOwnerBeforeReadingSessionObjects() {
        VideoUploadSessionEntity session = session("u-owner", 16L, 1, VideoUploadService.SESSION_UPLOADING);
        when(sessionMapper.findById("u-owner")).thenReturn(session);

        BizException error = assertThrows(BizException.class, () -> service.get(8L, "u-owner"));

        assertEquals(40300, error.getErrorCode().getCode());
        verify(sessionMapper, never()).findParts("u-owner");
        verify(minio, never()).statSize(any());
    }

    @Test
    void copiesSingleChunkValidatesMagicAndPublishesExactlyOnce() {
        VideoUploadSessionEntity session = session("u-2", 16L, 1, VideoUploadService.SESSION_UPLOADING);
        VideoUploadPartEntity part = part("u-2", 1, "upload/video/u-2/part-00001");
        when(sessionMapper.findById("u-2")).thenReturn(session);
        when(sessionMapper.findParts("u-2")).thenReturn(List.of(part));
        when(sessionMapper.claimMerging(eq("u-2"), any(Date.class))).thenReturn(1);
        when(sessionMapper.markCompleted("u-2", 701L)).thenReturn(1);
        when(minio.statSize(part.getObjectKey())).thenReturn(16L);
        when(minio.get(part.getObjectKey())).thenReturn(new ByteArrayInputStream(mp4Head()));
        when(minio.publicUrl(any())).thenReturn("http://example/orig.mp4");
        when(minio.displayUrl(any(MediaEntity.class))).thenReturn("http://example/thumb.jpg");
        doAnswer(invocation -> {
            invocation.<MediaEntity>getArgument(0).setId(701L);
            return 1;
        }).when(mediaMapper).insert(any(MediaEntity.class));

        MediaItemVO result = service.complete(7L, "u-2").items().get(0);

        assertEquals(701L, result.mediaId());
        assertEquals("VIDEO", result.mediaType());
        assertEquals(MediaService.VIDEO_PROCESSING, result.status());
        verify(minio).copy(eq(part.getObjectKey()), startsWith("orig/"));
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE_MEDIA_EVENT),
                eq(RabbitConfig.RK_VIDEO_TRANSCODE), any(Object.class));
        verify(sessionMapper).markCompleted("u-2", 701L);
        verify(minio).remove(part.getObjectKey());
    }

    @Test
    void repeatedCompleteReturnsTheExistingMedia() {
        VideoUploadSessionEntity session = session("u-3", 16L, 1, VideoUploadService.SESSION_COMPLETED);
        session.setCompletedMediaId(702L);
        MediaEntity media = new MediaEntity();
        media.setId(702L);
        media.setMediaType(MediaType.VIDEO.code());
        media.setStatus(MediaService.VIDEO_PROCESSING);
        when(sessionMapper.findById("u-3")).thenReturn(session);
        when(mediaMapper.findById(702L)).thenReturn(media);
        when(minio.displayUrl(media)).thenReturn("http://example/thumb.jpg");

        assertEquals(702L, service.complete(7L, "u-3").items().get(0).mediaId());
        verify(sessionMapper, never()).claimMerging(any(), any());
    }

    @Test
    void cleanupRemovesExpiredPartObjectsAndSessionRows() {
        VideoUploadSessionEntity session = session("u-4", 16L, 1, VideoUploadService.SESSION_UPLOADING);
        VideoUploadPartEntity part = part("u-4", 1, "upload/video/u-4/part-00001");
        when(sessionMapper.selectExpired(any(Date.class), anyInt())).thenReturn(List.of(session));
        when(sessionMapper.markExpired(eq("u-4"), any(Date.class))).thenReturn(1);
        when(sessionMapper.findParts("u-4")).thenReturn(List.of(part));

        service.cleanupExpired();

        verify(minio).remove(part.getObjectKey());
        verify(sessionMapper).deleteSession("u-4");
    }

    @Test
    void cleanupAlsoRemovesObjectsWhenReadPathAlreadyMarkedSessionExpired() {
        VideoUploadSessionEntity session = session("u-5", 16L, 1, VideoUploadService.SESSION_EXPIRED);
        VideoUploadPartEntity part = part("u-5", 1, "upload/video/u-5/part-00001");
        when(sessionMapper.selectExpired(any(Date.class), anyInt())).thenReturn(List.of(session));
        when(sessionMapper.findParts("u-5")).thenReturn(List.of(part));

        service.cleanupExpired();

        verify(sessionMapper, never()).markExpired(any(), any());
        verify(minio).remove(part.getObjectKey());
        verify(sessionMapper).deleteSession("u-5");
    }

    private VideoUploadSessionEntity session(String id, long size, int parts, int status) {
        VideoUploadSessionEntity session = new VideoUploadSessionEntity();
        session.setUploadId(id);
        session.setUserId(7L);
        session.setSizeBytes(size);
        session.setTotalParts(parts);
        session.setStatus(status);
        session.setExpiresAt(new Date(System.currentTimeMillis() + 60_000));
        return session;
    }

    private VideoUploadPartEntity part(String uploadId, int number, String key) {
        VideoUploadPartEntity part = new VideoUploadPartEntity();
        part.setUploadId(uploadId);
        part.setPartNumber(number);
        part.setObjectKey(key);
        return part;
    }

    private byte[] mp4Head() {
        byte[] head = new byte[16];
        head[4] = 'f';
        head[5] = 't';
        head[6] = 'y';
        head[7] = 'p';
        return head;
    }
}
