package com.scenary.feed;

import com.scenary.common.AuthorVO;
import com.scenary.common.SocialVO;

/**
 * 瀑布流卡片（docs/02 §6.1）。coverWidth/coverHeight 供前端预占位防抖动。
 */
public record NoteCardVO(
        long id,
        String title,
        String contentPreview,
        String coverUrl,
        Integer coverWidth,
        Integer coverHeight,
        int mediaCount,
        AuthorVO author,
        Long createdAt,
        SocialVO social) {

    public NoteCardVO(long id, String title, String contentPreview, String coverUrl,
                      Integer coverWidth, Integer coverHeight, int mediaCount,
                      AuthorVO author, Long createdAt) {
        this(id, title, contentPreview, coverUrl, coverWidth, coverHeight,
                mediaCount, author, createdAt, SocialVO.empty());
    }

    public NoteCardVO withSocial(SocialVO nextSocial) {
        return new NoteCardVO(id, title, contentPreview, coverUrl, coverWidth, coverHeight,
                mediaCount, author, createdAt, nextSocial);
    }

    /** 后端截断前 48 字符加省略号（契约 §6.1） */
    public static String preview(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        String trimmed = content.strip();
        return trimmed.length() <= 48 ? trimmed : trimmed.substring(0, 48) + "…";
    }
}
