package com.scenary.search;

import com.scenary.common.AuthorVO;
import com.scenary.common.SocialVO;

/** 搜索结果卡片，与 Feed NoteCardVO 同构并附带 P11 highlight。 */
public record SearchNoteVO(
        long id,
        String title,
        String contentPreview,
        String coverUrl,
        Integer coverWidth,
        Integer coverHeight,
        int mediaCount,
        AuthorVO author,
        Long createdAt,
        SocialVO social,
        HighlightVO highlight) {
}
