package com.scenary.search;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;

/**
 * 搜索排序键编码器。cursor 同时绑定 query/sort，防止把一组结果的游标带到另一组结果中。
 */
record SearchCursor(String query, String sort, int score, long createdAt, long id) {

    String encode() {
        String queryPart = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(query.getBytes(StandardCharsets.UTF_8));
        String payload = sort + "\n" + queryPart + "\n" + score + "\n" + createdAt + "\n" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    static SearchCursor parse(String raw, String expectedQuery, String expectedSort) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\n", -1);
            if (parts.length != 5) {
                throw invalid();
            }
            String query = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            SearchCursor cursor = new SearchCursor(query, parts[0], Integer.parseInt(parts[2]),
                    Long.parseLong(parts[3]), Long.parseLong(parts[4]));
            if (!expectedQuery.equals(cursor.query) || !expectedSort.equals(cursor.sort)
                    || cursor.score < 0 || cursor.score > 10 || cursor.createdAt <= 0 || cursor.id <= 0) {
                throw invalid();
            }
            return cursor;
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw invalid();
        }
    }

    private static BizException invalid() {
        return new BizException(ErrorCode.VALIDATION, "搜索游标无效");
    }
}
