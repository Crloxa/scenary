package com.scenary.common;

import java.util.List;
import java.util.function.Function;

/**
 * 游标分页包络，契约见 docs/02 §1.3：hasMore=false 时 nextCursor 为 null。
 * build 约定调用方多取一条(limit+1)探测，超出部分在此裁掉。
 */
public class PageResult<T> {

    private final List<T> list;
    private final Long nextCursor;
    private final boolean hasMore;

    private PageResult(List<T> list, Long nextCursor, boolean hasMore) {
        this.list = list;
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
    }

    /** 已装配完整列表的直构入口（如缓存命中重建、聚合后回填） */
    public static <T> PageResult<T> of(List<T> page, Long nextCursor, boolean hasMore) {
        return new PageResult<>(page, nextCursor, hasMore);
    }

    public static <T> PageResult<T> build(List<T> fetched, int limit, Function<T, Long> cursorOf) {
        boolean hasMore = fetched.size() > limit;
        List<T> page = hasMore ? fetched.subList(0, limit) : fetched;
        Long nextCursor = hasMore ? cursorOf.apply(page.get(page.size() - 1)) : null;
        return new PageResult<>(page, nextCursor, hasMore);
    }

    public List<T> getList() {
        return list;
    }

    public Long getNextCursor() {
        return nextCursor;
    }

    public boolean isHasMore() {
        return hasMore;
    }
}
