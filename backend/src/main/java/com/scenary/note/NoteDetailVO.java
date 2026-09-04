package com.scenary.note;

import com.scenary.common.AuthorVO;
import com.scenary.common.SocialVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 笔记详情响应（docs/02 §5.2）。mine 由服务端按令牌判定。
 */
public record NoteDetailVO(
        long id,
        String title,
        String content,
        String placeName,
        BigDecimal latitude,
        BigDecimal longitude,
        String placeSource,
        String placePrecision,
        Integer visibility,
        Long createdAt,
        AuthorVO author,
        List<ImageItem> images,
        SocialVO social,
        boolean mine) {

    public NoteDetailVO(long id, String title, String content, String placeName,
                       Integer visibility, Long createdAt, AuthorVO author,
                       List<ImageItem> images, boolean mine) {
        this(id, title, content, placeName, null, null, null, null, visibility, createdAt, author, images,
                SocialVO.empty(), mine);
    }

    public NoteDetailVO withSocial(SocialVO nextSocial) {
        return new NoteDetailVO(id, title, content, placeName, latitude, longitude,
                placeSource, placePrecision, visibility, createdAt,
                author, images, nextSocial, mine);
    }

    public record ImageItem(long mediaId, String url, String thumbUrl, Integer width, Integer height,
                            String mediaType, Long durationMs, String playbackUrl, String playbackLowUrl) {
        public ImageItem(long mediaId, String url, String thumbUrl, Integer width, Integer height) {
            this(mediaId, url, thumbUrl, width, height, "IMAGE", null, null, null);
        }
    }
}
