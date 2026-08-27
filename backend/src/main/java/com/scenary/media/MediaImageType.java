package com.scenary.media;

import java.util.Map;

/**
 * 图片魔数嗅探：多部分表单的 Content-Type 由客户端自报，绝不可信；
 * 服务端按文件头字节判定真实类型（契约 docs/02 §4.1 允许 jpeg/png/webp/gif）。
 */
public enum MediaImageType {

    JPEG("jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'}),
    WEBP("webp", "image/webp", null),   // 特判 RIFF....WEBP
    GIF("gif", "image/gif", "GIF8".getBytes());

    private final String ext;
    private final String mime;
    private final byte[] magicPrefix;

    private static final int SNIFF_BYTES = 16;

    /** 嗅探窗口字节数，调用方 mark/reset 需要据此预留缓冲 */
    public static int sniffBytes() {
        return SNIFF_BYTES;
    }

    MediaImageType(String ext, String mime, byte[] magicPrefix) {
        this.ext = ext;
        this.mime = mime;
        this.magicPrefix = magicPrefix;
    }

    public String ext() {
        return ext;
    }

    public String mime() {
        return mime;
    }

    /**
     * @return 不在白名单内返回 null，由调用方决定拒绝语义
     */
    public static MediaImageType detect(byte[] head) {
        for (MediaImageType t : values()) {
            if (t == WEBP) {
                if (startsWith(head, "RIFF".getBytes()) && head.length >= 12
                        && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
                    return t;
                }
                continue;
            }
            if (startsWith(head, t.magicPrefix)) {
                return t;
            }
        }
        return null;
    }

    public static byte[] readHead(java.io.InputStream in) throws java.io.IOException {
        byte[] buf = new byte[SNIFF_BYTES];
        int n = in.readNBytes(buf, 0, SNIFF_BYTES);
        return java.util.Arrays.copyOf(buf, n);
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
