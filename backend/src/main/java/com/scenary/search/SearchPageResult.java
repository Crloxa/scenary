package com.scenary.search;

import java.util.List;

/** P11 专用分页包络：相关性排序需要携带复合排序键的 opaque cursor。 */
public record SearchPageResult<T>(List<T> list, String nextCursor, boolean hasMore) {
}
