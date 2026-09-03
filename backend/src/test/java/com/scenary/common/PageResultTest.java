package com.scenary.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void buildTrimsProbeRowAndUsesLastCursor() {
        PageResult<Long> page = PageResult.build(List.of(10L, 9L, 8L), 2, value -> value);

        assertEquals(List.of(10L, 9L), page.getList());
        assertTrue(page.isHasMore());
        assertEquals(9L, page.getNextCursor());
    }

    @Test
    void buildMarksLastPageWithoutCursor() {
        PageResult<Long> page = PageResult.build(List.of(10L, 9L), 2, value -> value);

        assertEquals(List.of(10L, 9L), page.getList());
        assertFalse(page.isHasMore());
        assertNull(page.getNextCursor());
    }

    @Test
    void buildSupportsEmptyPage() {
        PageResult<Long> page = PageResult.build(List.of(), 10, value -> value);

        assertTrue(page.getList().isEmpty());
        assertFalse(page.isHasMore());
        assertNull(page.getNextCursor());
    }
}
