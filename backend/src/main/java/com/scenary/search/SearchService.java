package com.scenary.search;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.scenary.common.AuthorVO;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.SocialVO;
import com.scenary.social.SocialService;

/** P11 首期搜索：受控 LIKE + 稳定复合游标，不引入外部搜索服务。 */
@Service
public class SearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_QUERY_LENGTH = 64;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final SearchMapper searchMapper;
    private final SocialService socialService;

    /** 供不需要社交状态的单元测试/旧调用方使用。 */
    public SearchService(SearchMapper searchMapper) {
        this(searchMapper, null);
    }

    @Autowired
    public SearchService(SearchMapper searchMapper, SocialService socialService) {
        this.searchMapper = searchMapper;
        this.socialService = socialService;
    }

    public SearchPageResult<SearchNoteVO> search(Long viewerId, String query,
                                                  String rawCursor, Integer limitParam,
                                                  String rawSort) {
        String normalizedQuery = normalizeQuery(query);
        String sort = normalizeSort(rawSort);
        if (rawCursor != null && rawCursor.isBlank()) {
            throw new BizException(ErrorCode.VALIDATION, "搜索游标无效");
        }
        SearchCursor cursor = rawCursor == null
                ? null : SearchCursor.parse(rawCursor, normalizedQuery, sort);
        int limit = clampLimit(limitParam);

        List<SearchRow> rows = searchMapper.selectNotes(
                escapeLike(normalizedQuery), sort,
                cursor == null ? null : cursor.score(),
                cursor == null ? null : new java.util.Date(cursor.createdAt()),
                cursor == null ? null : cursor.id(),
                limit + 1);

        boolean hasMore = rows.size() > limit;
        List<SearchRow> pageRows = rows.stream().limit(limit).toList();
        Map<Long, SocialVO> statuses = socialService == null
                ? Map.of()
                : socialService.noteStatuses(pageRows.stream().map(SearchRow::getId).toList(), viewerId);
        List<SearchNoteVO> items = pageRows.stream()
                .map(row -> toVO(row, normalizedQuery,
                        statuses.getOrDefault(row.getId(), SocialVO.empty())))
                .toList();
        String nextCursor = hasMore && !pageRows.isEmpty()
                ? new SearchCursor(normalizedQuery, sort,
                        value(pageRows.get(pageRows.size() - 1).getRelevanceScore()),
                        pageRows.get(pageRows.size() - 1).getCreatedAt().getTime(),
                        pageRows.get(pageRows.size() - 1).getId()).encode()
                : null;
        return new SearchPageResult<>(items, nextCursor, hasMore);
    }

    static String normalizeQuery(String query) {
        if (query == null) {
            throw new BizException(ErrorCode.VALIDATION, "搜索词不能为空");
        }
        String normalized = WHITESPACE.matcher(query.strip()).replaceAll(" ");
        if (normalized.length() < 2) {
            throw new BizException(ErrorCode.VALIDATION, "搜索词至少 2 个字符");
        }
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new BizException(ErrorCode.VALIDATION, "搜索词最长 64 个字符");
        }
        return normalized;
    }

    static String normalizeSort(String sort) {
        String normalized = sort == null || sort.isBlank()
                ? "recent" : sort.trim().toLowerCase(Locale.ROOT);
        if (!Objects.equals(normalized, "recent") && !Objects.equals(normalized, "relevance")) {
            throw new BizException(ErrorCode.VALIDATION, "sort 仅支持 recent 或 relevance");
        }
        return normalized;
    }

    /** 只处理 LIKE 的三个特殊字符；SQL 本身始终使用 #{keyword} 参数绑定。 */
    static String escapeLike(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' || ch == '%' || ch == '_') {
                out.append('\\');
            }
            out.append(ch);
        }
        return out.toString();
    }

    private SearchNoteVO toVO(SearchRow row, String query, SocialVO social) {
        AuthorVO author = new AuthorVO(row.getUserId(), row.getAuthorNickname(), row.getAuthorAvatarUrl());
        return new SearchNoteVO(row.getId(), row.getTitle(), preview(row.getContent()), row.getCoverUrl(),
                row.getCoverWidth(), row.getCoverHeight(), value(row.getMediaCount()), author,
                row.getCreatedAt().getTime(), social,
                new HighlightVO(excerpt(row.getTitle(), query), excerpt(row.getContent(), query),
                        excerpt(row.getPlaceName(), query), excerpt(row.getAuthorNickname(), query)));
    }

    private static String excerpt(String value, String query) {
        if (value == null || value.isBlank()) {
            return null;
        }
        int match = value.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
        if (match < 0) {
            return null;
        }
        int start = Math.max(0, match - 24);
        int end = Math.min(value.length(), match + query.length() + 48);
        String result = value.substring(start, end);
        return (start > 0 ? "…" : "") + result + (end < value.length() ? "…" : "");
    }

    private static String preview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String trimmed = content.strip();
        return trimmed.length() <= 48 ? trimmed : trimmed.substring(0, 48) + "…";
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    static int clampLimit(Integer limitParam) {
        if (limitParam == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(20, limitParam));
    }
}
