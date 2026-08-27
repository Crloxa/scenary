package com.scenary.feed;

import com.scenary.common.AuthorVO;

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
        Long createdAt) {

    /** 后端截断前 48 字符加省略号（契约 §6.1） */
    public static String preview(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        String trimmed = content.strip();
        return trimmed.length() <= 48 ? trimmed : trimmed.substring(0, 48) + "…";
    }
}
