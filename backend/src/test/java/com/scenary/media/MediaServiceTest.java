package com.scenary.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.RateLimitService;
import com.scenary.config.MinioProperties;
import com.scenary.config.RabbitConfig;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private MinioService minio;
    @Mock
    private MediaMapper mediaMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void stubRateLimitRedis() {
        // 图片上传限流器查询 Redis；未打桩的 increment 返回 null 视为放行
        org.mockito.Mockito.lenient().when(redis.opsForValue()).thenReturn(valueOperations);
    }

    private MediaService service() {
        return new MediaService(properties(), minio, mediaMapper, rabbitTemplate,
                new RateLimitService(redis));
    }

    @Test
    void removesObjectWhenDatabaseInsertFails() {
        MediaService service = service();
        MockMultipartFile file = new MockMultipartFile("files", "photo.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        doNothing().when(minio).put(anyString(), any(), any(Long.class), anyString());
        doThrow(new DuplicateKeyException("database unavailable")).when(mediaMapper).insert(any(MediaEntity.class));

        BizException error = assertThrows(BizException.class,
                () -> service.uploadImages(7L, List.of(file)));

        assertEquals(ErrorCode.INTERNAL_ERROR, error.getErrorCode());
        verify(minio).remove(argThat(key -> key.startsWith("orig/")));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void uploadsVideoToTheDedicatedTranscodeRouteWithoutExposingOriginalUrl() {
        MediaService service = service();
        byte[] head = new byte[16];
        head[4] = 'f';
        head[5] = 't';
        head[6] = 'y';
        head[7] = 'p';
        MockMultipartFile file = new MockMultipartFile("file", "clip.mov", "video/quicktime", head);

        doNothing().when(minio).put(anyString(), any(), any(Long.class), anyString());
        when(minio.displayUrl(any(MediaEntity.class))).thenReturn("http://example/thumb.jpg");
        doAnswer(invocation -> {
            MediaEntity media = invocation.getArgument(0);
            media.setId(701L);
            return 1;
        }).when(mediaMapper).insert(any(MediaEntity.class));
        when(mediaMapper.claimPublish(eq(701L), any())).thenReturn(1);

        MediaItemVO item = service.uploadVideo(7L, file).items().get(0);

        assertEquals(701L, item.mediaId());
        assertEquals("VIDEO", item.mediaType());
        assertEquals(MediaService.VIDEO_PROCESSING, item.status());
        assertEquals("http://example/thumb.jpg", item.url());
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE_MEDIA_EVENT),
                eq(RabbitConfig.RK_VIDEO_TRANSCODE), any(Object.class));
    }

    private MinioProperties properties() {
        MinioProperties properties = new MinioProperties();
        properties.setBucket("scenary-media");
        properties.setPublicHost("http://example/minio");
        return properties;
    }
}
