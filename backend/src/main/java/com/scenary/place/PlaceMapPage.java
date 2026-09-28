package com.scenary.place;

import java.util.List;

/**
 * 地图视野框分页包络（docs/02 §10.5）：keyset 游标语义同 §1.3。
 */
public record PlaceMapPage(List<PlaceNoteVO> list, String nextCursor, boolean hasMore) {
}
