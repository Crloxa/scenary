package com.scenary.feed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenary.common.AuthorVO;
import com.scenary.common.PageResult;
import com.scenary.common.SocialVO;
import com.scenary.social.SocialService;
import com.scenary.note.NoteEntity;
import com.scenary.note.NoteMapper;
import com.scenary.note.NoteService;
import com.scenary.user.UserMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * feed 服务：Newest-first 游标分页 + 两级缓存（docs/01 §5.3）。
 * L1 默认首页整页 JSON（feed:first:v1:{version}, TTL 300s，写穿透由发布/删除侧推进版本）；
 * L2 卡片单条 note:card:{id} TTL 1h——翻页时命中即免回表拼装作者与封面宽高。
 */
@Service
public class FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_PAGE_LIMIT = 10;
    private static final Duration FIRST_PAGE_TTL = Duration.ofSeconds(300);
    private static final Duration CARD_TTL = Duration.ofHours(1);

    private final NoteMapper noteMapper;
    private final NoteService noteService;
    private final UserMapper userMapper;
    private final com.scenary.media.MediaMapper mediaMapper;
    private final StringRedisTemplate redis;
    private final SocialService socialService;

    /** 保留给不需要社交视角的单元测试/旧调用方；生产由 Spring 使用完整构造器。 */
    public FeedService(NoteMapper noteMapper, NoteService noteService,
                       UserMapper userMapper, com.scenary.media.MediaMapper mediaMapper,
                       StringRedisTemplate redis) {
        this(noteMapper, noteService, userMapper, mediaMapper, redis, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FeedService(NoteMapper noteMapper, NoteService noteService,
                       UserMapper userMapper, com.scenary.media.MediaMapper mediaMapper,
                       StringRedisTemplate redis, SocialService socialService) {
        this.noteMapper = noteMapper;
        this.noteService = noteService;
        this.userMapper = userMapper;
        this.mediaMapper = mediaMapper;
        this.redis = redis;
        this.socialService = socialService;
    }

    public PageResult<NoteCardVO> page(Long cursorParam, Integer limitParam) {
        return page(null, cursorParam, limitParam);
    }

    public PageResult<NoteCardVO> page(Long viewerId, Long cursorParam, Integer limitParam) {
        boolean firstPage = cursorParam == null;
        int limit = clampLimit(limitParam);
        long cursor = cursorParam == null ? Long.MAX_VALUE : cursorParam;
        // 单个 L1 key 只缓存首页默认 10 条，避免把自定义 limit 的结果错误复用给其他请求。
        boolean cacheableFirstPage = firstPage && limit == 10;
        String cacheVersion = cacheableFirstPage ? firstPageCacheVersion() : null;

        if (cacheVersion != null) {
            PageResult<NoteCardVO> cached = readFirstPageCache(cacheVersion);
            if (cached != null && matchesCurrentFirstPage(cached)) {
                return enrich(sign(cached), viewerId);
            }
            if (cached != null) {
                redis.delete(firstPageCacheKey(cacheVersion));
            }
        }

        var rows = noteMapper.selectFeedRows(viewerId, cursor, limit + 1);
        var cards = assembleCards(rows);
        PageResult<NoteCardVO> result = PageResult.of(
                cards.subList(0, Math.min(limit, cards.size())),
                cards.size() > limit ? rows.get(limit - 1).getId() : null,
                rows.size() > limit);

        if (cacheVersion != null) {
            writeFirstPageCache(result, cacheVersion);
        }
        return enrich(sign(result), viewerId);
    }

    /**
     * E2-02 缓存语义：L1/L2 缓存里持久化的是 cover/avatar key（或切换前的遗留 URL），
     * 出响应前才在此统一转成短时签名 URL（docs/02 §1.5）。
     */
    private PageResult<NoteCardVO> sign(PageResult<NoteCardVO> page) {
        var cards = page.getList().stream().map(this::signCard).toList();
        return PageResult.of(cards, page.getNextCursor(), page.isHasMore());
    }

    private NoteCardVO signCard(NoteCardVO card) {
        AuthorVO author = card.author();
        AuthorVO signed = author == null ? null
                : new AuthorVO(author.id(), author.nickname(), noteService.viewUrl(author.avatarUrl()));
        return new NoteCardVO(card.id(), card.title(), card.contentPreview(),
                noteService.viewUrl(card.coverUrl()), card.coverWidth(), card.coverHeight(),
                card.mediaCount(), signed, card.createdAt(), card.social());
    }

    private PageResult<NoteCardVO> enrich(PageResult<NoteCardVO> page, Long viewerId) {
        if (socialService == null || page.getList().isEmpty()) {
            return page;
        }
        var statuses = socialService.noteStatuses(page.getList().stream().map(NoteCardVO::id).toList(), viewerId);
        var cards = page.getList().stream()
                .map(card -> card.withSocial(statuses.getOrDefault(card.id(), SocialVO.empty())))
                .toList();
        return PageResult.of(cards, page.getNextCursor(), page.isHasMore());
    }

    private List<NoteCardVO> assembleCards(List<NoteEntity> rows) {
        LinkedHashMap<Long, NoteCardVO> out = new LinkedHashMap<>();
        List<Long> missIds = new ArrayList<>();
        for (NoteEntity n : rows) {
            String raw = cacheGet(NoteService.KEY_NOTE_CARD + n.getId());
            if (raw != null) {
                try {
                    out.put(n.getId(), MAPPER.readValue(raw, NoteCardVO.class));
                    continue;
                } catch (Exception ignore) {
                    // 缓存脏数据按未命中处理
                }
            }
            missIds.add(n.getId());
        }
        fillMissing(out, missIds, rows);
        return new ArrayList<>(out.values());
    }

    private void fillMissing(LinkedHashMap<Long, NoteCardVO> out,
                             List<Long> missIds, List<NoteEntity> rows) {
        if (missIds.isEmpty()) {
            return;
        }
        Map<Long, NoteEntity> byId = new LinkedHashMap<>();
        rows.forEach(n -> byId.put(n.getId(), n));
        Map<Long, com.scenary.user.UserEntity> briefs =
                noteService.briefsMap(missIds.stream().map(byId::get).map(NoteEntity::getUserId).toList());

        // 每个 note 取 order_no 最小的一条作封面
        Map<Long, com.scenary.media.MediaEntity> covers = new LinkedHashMap<>();
        for (var m : mediaMapper.selectByNoteIds(missIds)) {
            covers.putIfAbsent(m.getNoteId(), m);
        }

        for (Long id : missIds) {
            NoteEntity n = byId.get(id);
            if (n == null) {
                continue;   // L1 缓存中已被删除的旧卡，跳过
            }
            var cover = covers.get(id);
            NoteCardVO vo = new NoteCardVO(
                    n.getId(), n.getTitle(), NoteCardVO.preview(n.getContent()),
                    n.getCoverUrl(),
                    cover == null ? null : cover.getWidth(),
                    cover == null ? null : cover.getHeight(),
                    n.getMediaCount(),
                    toAuthor(n.getUserId(), briefs),
                    n.getCreatedAt().getTime());
            out.put(id, vo);
            cacheSet(NoteService.KEY_NOTE_CARD + id, vo);
        }
    }

    private AuthorVO toAuthor(long userId, Map<Long, com.scenary.user.UserEntity> briefs) {
        return noteService.toAuthor(userId, briefs);
    }

    private String firstPageCacheVersion() {
        try {
            String version = redis.opsForValue().get(NoteService.KEY_FEED_FIRST_VERSION);
            return version == null ? "0" : version;
        } catch (Exception e) {
            log.warn("feed first-page cache version read failed, degrade to db", e);
            return null;
        }
    }

    private PageResult<NoteCardVO> readFirstPageCache(String version) {
        try {
            String raw = redis.opsForValue().get(firstPageCacheKey(version));
            if (raw == null) {
                return null;
            }
            return MAPPER.readValue(raw, new TypeReference<PageResult<NoteCardVO>>() {
            });
        } catch (Exception e) {
            log.warn("feed first-page cache read failed, degrade to db", e);
            return null;
        }
    }

    private void writeFirstPageCache(PageResult<NoteCardVO> page, String version) {
        try {
            redis.opsForValue().set(firstPageCacheKey(version),
                    MAPPER.writeValueAsString(page), FIRST_PAGE_TTL);
        } catch (Exception e) {
            log.warn("feed first-page cache write failed", e);
        }
    }

    private String firstPageCacheKey(String version) {
        return NoteService.KEY_FEED_FIRST + ":" + version;
    }

    /** 仅核对覆盖索引返回的笔记 ID，防止交错写入的旧 L1 页对外可见。 */
    private boolean matchesCurrentFirstPage(PageResult<NoteCardVO> cached) {
        List<NoteEntity> current = noteMapper.selectFeedRows(null, Long.MAX_VALUE, DEFAULT_PAGE_LIMIT + 1);
        int expectedSize = Math.min(DEFAULT_PAGE_LIMIT, current.size());
        if (cached.getList().size() != expectedSize || cached.isHasMore() != (current.size() > DEFAULT_PAGE_LIMIT)) {
            return false;
        }
        for (int i = 0; i < expectedSize; i++) {
            if (cached.getList().get(i).id() != current.get(i).getId()) {
                return false;
            }
        }
        return true;
    }

    private String cacheGet(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            return null;
        }
    }

    private void cacheSet(String key, Object vo) {
        try {
            redis.opsForValue().set(key, MAPPER.writeValueAsString(vo), CARD_TTL);
        } catch (Exception e) {
            log.warn("card cache write failed: {}", key);
        }
    }

    static int clampLimit(Integer limitParam) {
        if (limitParam == null) {
            return DEFAULT_PAGE_LIMIT;
        }
        return Math.max(1, Math.min(20, limitParam));
    }
}
