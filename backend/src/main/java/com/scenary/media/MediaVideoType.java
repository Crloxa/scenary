package com.scenary.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** 视频容器魔数检测；不信任扩展名和 multipart MIME。 */
public enum MediaVideoType {
    MP4("mp4", "video/mp4"),
    WEBM("webm", "video/webm");

    private static final int SNIFF_BYTES = 16;
    private final String ext;
    private final String mime;

    MediaVideoType(String ext, String mime) {
        this.ext = ext;
        this.mime = mime;
    }

    public String ext() {
        return ext;
    }

    public String mime() {
        return mime;
    }

    public static int sniffBytes() {
        return SNIFF_BYTES;
    }

    public static MediaVideoType detect(byte[] head) {
        if (head.length >= 12 && head[4] == 'f' && head[5] == 't'
                && head[6] == 'y' && head[7] == 'p') {
            return MP4; // MP4/MOV 均以 ISO-BMFF ftyp 开始，输出统一转为 MP4。
        }
        if (startsWith(head, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3})) {
            return WEBM;
        }
        return null;
    }

    public static MediaVideoType detect(InputStream in) throws IOException {
        byte[] head = in.readNBytes(SNIFF_BYTES);
        return detect(head);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.compare(data, 0, prefix.length, prefix, 0, prefix.length) == 0;
    }
}
