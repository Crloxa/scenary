package com.scenary.mq;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rabbitmq.client.Channel;
import com.scenary.media.MediaEntity;
import com.scenary.media.MediaMapper;
import com.scenary.media.MinioService;

import org.springframework.amqp.core.Message;

@ExtendWith(MockitoExtension.class)
class ThumbnailConsumerTest {

    @Mock
    private MinioService minio;
    @Mock
    private MediaMapper mediaMapper;
    @Mock
    private Channel channel;

    @Test
    void finalFailurePersistsFailedStateBeforeDeadLetter() throws Exception {
        ThumbnailConsumer consumer = new ThumbnailConsumer(minio, mediaMapper);

        MediaEntity media = new MediaEntity();
        media.setId(7L);
        media.setObjectKey("orig/202609/abcd.jpg");
        media.setStatus(0);
        when(mediaMapper.findById(7L)).thenReturn(media);
        when(minio.get("orig/202609/abcd.jpg")).thenThrow(new RuntimeException("boom"));

        consumer.onMediaUploaded(new Message("{\"mediaId\":7}".getBytes(StandardCharsets.UTF_8)), channel, 99L);

        verify(channel).basicNack(99L, false, false);
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Date> failedAt = ArgumentCaptor.forClass(Date.class);
        verify(mediaMapper).updateFailureResult(eq(7L), reason.capture(), failedAt.capture());
        assertTrue(reason.getValue().contains("RuntimeException"));
        assertTrue(failedAt.getValue() != null);
    }

    @Test
    void duplicateMessageForSettledMediaIsAcknowledgedWithoutReprocessing() throws Exception {
        ThumbnailConsumer consumer = new ThumbnailConsumer(minio, mediaMapper);

        MediaEntity media = new MediaEntity();
        media.setId(8L);
        media.setStatus(1);
        when(mediaMapper.findById(8L)).thenReturn(media);

        consumer.onMediaUploaded(new Message("{\"mediaId\":8}".getBytes(StandardCharsets.UTF_8)), channel, 100L);

        verify(channel).basicAck(100L, false);
        verify(minio, org.mockito.Mockito.never()).get(any());
        verify(mediaMapper, org.mockito.Mockito.never()).updateFailureResult(any(), any(), any());
    }
}
