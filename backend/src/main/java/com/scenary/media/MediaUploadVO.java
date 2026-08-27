package com.scenary.media;

import java.util.List;

/**
 * 批量上传响应包装，固定 docs/02 §4.1 的 {"items":[...]} 形态。
 */
public record MediaUploadVO(List<MediaItemVO> items) {
}
