package com.scenary.media;

/** 媒体类型常量；数据库使用 tinyint，响应使用稳定字符串。 */
public enum MediaType {
    IMAGE(0, "IMAGE"),
    VIDEO(1, "VIDEO");

    private final int code;
    private final String label;

    MediaType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static MediaType from(Integer code) {
        return code != null && code == VIDEO.code ? VIDEO : IMAGE;
    }
}
