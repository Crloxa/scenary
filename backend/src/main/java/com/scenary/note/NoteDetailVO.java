package com.scenary.note;

import com.scenary.common.AuthorVO;
import com.scenary.common.SocialVO;

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
        SocialVO social,
        boolean mine) {

    public NoteDetailVO(long id, String title, String content, String placeName,
                       Integer visibility, Long createdAt, AuthorVO author,
                       List<ImageItem> images, boolean mine) {
        this(id, title, content, placeName, visibility, createdAt, author, images,
                SocialVO.empty(), mine);
    }

    public NoteDetailVO withSocial(SocialVO nextSocial) {
        return new NoteDetailVO(id, title, content, placeName, visibility, createdAt,
                author, images, nextSocial, mine);
    }

    public record ImageItem(long mediaId, String url, String thumbUrl, Integer width, Integer height) {
    }
}
