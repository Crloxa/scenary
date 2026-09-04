package com.scenary.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class MediaVideoTypeTest {

    @Test
    void detectsMp4AndMovFromIsoBmffFtypWithoutTrustingExtension() {
        byte[] head = new byte[16];
        head[4] = 'f';
        head[5] = 't';
        head[6] = 'y';
        head[7] = 'p';

        assertEquals(MediaVideoType.MP4, MediaVideoType.detect(head));
    }

    @Test
    void detectsWebmEbmlHeaderAndRejectsImageOrShortPayloads() {
        byte[] webm = new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

        assertEquals(MediaVideoType.WEBM, MediaVideoType.detect(webm));
        assertNull(MediaVideoType.detect("not-a-video".getBytes(StandardCharsets.US_ASCII)));
        assertNull(MediaVideoType.detect(new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p'}));
    }
}
