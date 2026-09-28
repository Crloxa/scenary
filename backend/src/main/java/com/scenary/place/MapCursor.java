package com.scenary.place;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;

/**
 * 地图浏览 keyset 游标（docs/02 §10.5）：opaque base64（createdAt\nid）。
 * 排序键与 /places/notes 绑定；解析失败按 40000 处理（不外泄内部结构）。
 */
record MapCursor(long createdAt, long id) {

    String encode() {
        String payload = createdAt + "\n" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    static MapCursor parse(String raw) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            String[] parts = payload.split("\\n", -1);
            if (parts.length != 2) {
                throw invalid();
            }
            return new MapCursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw invalid();
        }
    }

    private static BizException invalid() {
        return new BizException(ErrorCode.VALIDATION, "cursor 无效");
    }
}
