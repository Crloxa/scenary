package com.scenary.place;

/**
 * GET /places/notes 列表项（契约 docs/02 §10.5）。coverUrl 为运行时短时签名 URL，可为 null。
 */
public record PlaceNoteVO(
        Long id,
        String title,
        String coverUrl,
        Double latitude,
        Double longitude,
        String placeName,
        Long authorId,
        String authorNickname) {
}
