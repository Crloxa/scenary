package com.scenary.media;

/**
 * 媒体对外视图：上传响应条目（docs/02 §4.1）与状态轮询响应（§4.2）字段一致，共用此形态。
 */
public record MediaItemVO(
        Long mediaId,
        String url,
        String thumbUrl,
        Integer status,
        Integer width,
        Integer height) {
}
