package com.scenary.common;

/**
 * 个人九宫格卡片（docs/02 §3.5）：由 note 模块门面产出、user 模块展示，跨包故居 common。
 */
public record GridCardVO(
        long id,
        String title,
        String coverUrl,
        int mediaCount,
        Integer visibility,
        Long createdAt) {
}
