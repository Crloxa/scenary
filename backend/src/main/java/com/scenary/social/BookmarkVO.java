package com.scenary.social;

import com.scenary.common.AuthorVO;
import com.scenary.common.SocialVO;

/** “我的收藏”列表沿用 Feed 卡片形态，游标由收藏关系 id 驱动。 */
public record BookmarkVO(
        long id,
        String title,
        String contentPreview,
        String coverUrl,
        int mediaCount,
        AuthorVO author,
        Long createdAt,
        SocialVO social) {
}
