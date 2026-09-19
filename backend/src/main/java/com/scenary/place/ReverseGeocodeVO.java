package com.scenary.place;

/**
 * GET /places/reverse-geocode 响应体（契约 docs/02 §7B）。
 * placeName 为 null 表示空候选（provider 关闭/超时/失败/无结果），前端保留手工输入。
 */
public record ReverseGeocodeVO(String placeName, String provider, boolean cached) {

    /** provider 关闭时的固定降级响应 */
    public static ReverseGeocodeVO empty() {
        return new ReverseGeocodeVO(null, "none", false);
    }
}
