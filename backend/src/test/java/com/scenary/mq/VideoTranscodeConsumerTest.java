package com.scenary.mq;

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
import org.springframework.amqp.core.Message;

import com.rabbitmq.client.Channel;
import com.scenary.media.MediaMapper;
import com.scenary.media.VideoTranscodeService;

@ExtendWith(MockitoExtension.class)
class VideoTranscodeConsumerTest {

    @Mock
    private VideoTranscodeService transcodeService;
    @Mock
    private MediaMapper mediaMapper;
    @Mock
    private Channel channel;

    @Test
    void persistsVideoFailureBeforeDeadLetter() throws Exception {
        when(mediaMapper.updateVideoFailureResult(eq(701L), any(String.class), any(Date.class))).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("unsupported video"))
                .when(transcodeService).transcode(701L);

        new VideoTranscodeConsumer(transcodeService, mediaMapper)
                .onVideoTranscode(new Message("{\"mediaId\":701}".getBytes(StandardCharsets.UTF_8)),
                        channel, 17L);

        verify(mediaMapper).updateVideoFailureResult(eq(701L), any(String.class), any(Date.class));
        verify(channel).basicNack(17L, false, false);
        verify(channel, org.mockito.Mockito.never()).basicAck(17L, false);
        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(mediaMapper).updateVideoFailureResult(eq(701L), reason.capture(), any(Date.class));
        org.junit.jupiter.api.Assertions.assertTrue(reason.getValue().contains("IllegalArgumentException"));
    }
}
