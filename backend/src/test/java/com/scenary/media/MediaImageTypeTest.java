package com.scenary.media;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class MediaImageTypeTest {

    @Test
    void detectsSupportedMagicHeaders() {
        assertEquals(MediaImageType.JPEG, MediaImageType.detect(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff}));
        assertEquals(MediaImageType.PNG, MediaImageType.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        assertEquals(MediaImageType.WEBP, MediaImageType.detect("RIFFxxxxWEBP".getBytes()));
        assertEquals(MediaImageType.GIF, MediaImageType.detect("GIF89a".getBytes()));
    }

    @Test
    void rejectsShortOrForgedHeaders() {
        assertNull(MediaImageType.detect(new byte[]{(byte) 0xff, (byte) 0xd8}));
        assertNull(MediaImageType.detect("RIFFxxxxNOPE".getBytes()));
        assertNull(MediaImageType.detect("not-an-image".getBytes()));
    }

    @Test
    void readHeadIsBoundedBySniffWindow() throws Exception {
        byte[] input = "0123456789abcdefghijkl".getBytes();
        byte[] head = MediaImageType.readHead(new ByteArrayInputStream(input));

        assertEquals(MediaImageType.sniffBytes(), head.length);
        assertArrayEquals("0123456789abcdef".getBytes(), head);
    }
}
