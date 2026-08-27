package com.scenary.note;

import com.scenary.common.AuthorVO;

import java.util.List;

/**
 * 笔记详情响应（docs/02 §5.2）。mine 由服务端按令牌判定。
 */
public record NoteDetailVO(
        long id,
        String title,
        String content,
        String placeName,
        Integer visibility,
        Long createdAt,
        AuthorVO author,
        List<ImageItem> images,
        boolean mine) {

    public record ImageItem(long mediaId, String url, String thumbUrl, Integer width, Integer height) {
    }
}
