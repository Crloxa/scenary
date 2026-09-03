package com.scenary.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.config.MinioProperties;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private MinioService minio;
    @Mock
    private MediaMapper mediaMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void removesObjectWhenDatabaseInsertFails() {
        MediaService service = new MediaService(properties(), minio, mediaMapper, rabbitTemplate);
        MockMultipartFile file = new MockMultipartFile("files", "photo.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        doNothing().when(minio).put(anyString(), any(), any(Long.class), anyString());
        when(minio.publicUrl(anyString())).thenReturn("http://example/original.png");
        doThrow(new DuplicateKeyException("database unavailable")).when(mediaMapper).insert(any(MediaEntity.class));

        BizException error = assertThrows(BizException.class,
                () -> service.uploadImages(7L, List.of(file)));

        assertEquals(ErrorCode.INTERNAL_ERROR, error.getErrorCode());
        verify(minio).remove(argThat(key -> key.startsWith("orig/")));
        verifyNoInteractions(rabbitTemplate);
    }

    private MinioProperties properties() {
        MinioProperties properties = new MinioProperties();
        properties.setBucket("scenary-media");
        properties.setPublicHost("http://example/minio");
        return properties;
    }
}
