package com.scenary.feed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenary.common.AuthorVO;
import com.scenary.common.PageResult;
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
 * L1 首页整页 JSON（feed:first:v1, TTL 300s，写穿透由发布/删除侧 DEL）；
 * L2 卡片单条 note:card:{id} TTL 1h——翻页时命中即免回表拼装作者与封面宽高。
 */
@Service
public class FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration FIRST_PAGE_TTL = Duration.ofSeconds(300);
    private static final Duration CARD_TTL = Duration.ofHours(1);

    private final NoteMapper noteMapper;
    private final NoteService noteService;
    private final UserMapper userMapper;
    private final com.scenary.media.MediaMapper mediaMapper;
    private final StringRedisTemplate redis;

    public FeedService(NoteMapper noteMapper, NoteService noteService,
                       UserMapper userMapper, com.scenary.media.MediaMapper mediaMapper,
                       StringRedisTemplate redis) {
        this.noteMapper = noteMapper;
        this.noteService = noteService;
        this.userMapper = userMapper;
        this.mediaMapper = mediaMapper;
        this.redis = redis;
    }

    public PageResult<NoteCardVO> page(Long cursorParam, Integer limitParam) {
        boolean firstPage = cursorParam == null;
        int limit = clampLimit(limitParam);
        long cursor = cursorParam == null ? Long.MAX_VALUE : cursorParam;

        if (firstPage) {
            PageResult<NoteCardVO> cached = readFirstPageCache();
            if (cached != null) {
                return cached;
            }
        }

        var rows = noteMapper.selectFeedRows(cursor, limit + 1);
        var cards = assembleCards(rows);
        PageResult<NoteCardVO> result = PageResult.of(
                cards.subList(0, Math.min(limit, cards.size())),
                cards.size() > limit ? rows.get(limit - 1).getId() : null,
                rows.size() > limit);

        if (firstPage) {
            writeFirstPageCache(result);
        }
        return result;
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

    private PageResult<NoteCardVO> readFirstPageCache() {
        try {
            String raw = redis.opsForValue().get(NoteService.KEY_FEED_FIRST);
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

    private void writeFirstPageCache(PageResult<NoteCardVO> page) {
        try {
            redis.opsForValue().set(NoteService.KEY_FEED_FIRST,
                    MAPPER.writeValueAsString(page), FIRST_PAGE_TTL);
        } catch (Exception e) {
            log.warn("feed first-page cache write failed", e);
        }
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
            return 10;
        }
        return Math.max(1, Math.min(20, limitParam));
    }
}
