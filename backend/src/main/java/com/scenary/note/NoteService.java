package com.scenary.note;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.scenary.common.AuthorVO;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.GridCardVO;
import com.scenary.common.PageResult;
import com.scenary.media.MediaMapper;
import com.scenary.media.MinioService;
import com.scenary.user.UserEntity;
import com.scenary.user.UserMapper;

/**
 * 笔记域服务：发布事务 / 软删 / 详情聚合 / 个人网格门面（user 模块经此取数，依赖铁律 docs/01 §4.1）。
 * 缓存失效契约（docs/02 §5.1/§5.3）：发布与删除都 DEL feed:first:v1；删除另清 note:card:{id}。
 */
@Service
public class NoteService {

    public static final String KEY_FEED_FIRST = "feed:first:v1";
    public static final String KEY_FEED_FIRST_VERSION = KEY_FEED_FIRST + ":version";
    public static final String KEY_NOTE_CARD = "note:card:";

    private final NoteMapper noteMapper;
    private final MediaMapper mediaMapper;
    private final UserMapper userMapper;
    private final MinioService minio;
    private final StringRedisTemplate redis;

    public NoteService(NoteMapper noteMapper, MediaMapper mediaMapper,
                       UserMapper userMapper, MinioService minio, StringRedisTemplate redis) {
        this.noteMapper = noteMapper;
        this.mediaMapper = mediaMapper;
        this.userMapper = userMapper;
        this.minio = minio;
        this.redis = redis;
    }

    @Transactional(noRollbackFor = DuplicateKeyException.class)
    public NoteCreatedVO create(long userId, NoteCreateRequest req) {
        String requestKey = normalizeRequestKey(req.requestKey());
        if (requestKey != null) {
            NoteEntity existing = noteMapper.findByUserAndRequestKey(userId, requestKey);
            if (existing != null) {
                return toCreated(existing);
            }
        }

        List<MediaItemSnapshot> snapshots = req.mediaIds().stream()
                .map(id -> {
                    var m = mediaMapper.findById(id);
                    if (m == null) {
                        throw new BizException(ErrorCode.NOT_FOUND, "媒体不存在: " + id);
                    }
                    if (m.getUserId() != userId) {
                        throw new BizException(ErrorCode.FORBIDDEN, "包含不属于你的媒体");
                    }
                    if (m.getNoteId() != null) {
                        throw new BizException(ErrorCode.VALIDATION, "媒体已被其他笔记使用: " + id);
                    }
                    if (m.getStatus() == null || m.getStatus() != 1) {
                        throw new BizException(ErrorCode.MEDIA_NOT_READY,
                                "媒体仍在处理中，请稍后重试");
                    }
                    return new MediaItemSnapshot(m.getId(), m.getThumbUrl());
                })
                .toList();

        NoteEntity note = new NoteEntity();
        note.setUserId(userId);
        note.setTitle(req.title());
        note.setContent(req.content() == null ? "" : req.content());
        note.setPlaceName(req.placeName());
        note.setRequestKey(requestKey);
        // 契约 v2.3：可见性入参缺省公开；越界值已被 Bean Validation 拦截
        note.setVisibility(req.visibility() == null ? 1 : req.visibility());
        note.setCoverUrl(snapshots.get(0).thumbUrl());
        note.setMediaCount(snapshots.size());
        try {
            noteMapper.insert(note);
            for (int i = 0; i < snapshots.size(); i++) {
                int updated = mediaMapper.bindToNote(note.getId(), i + 1, snapshots.get(i).id());
                if (updated != 1) {
                    throw new BizException(ErrorCode.INTERNAL_ERROR, "媒体绑定失败，请重试");
                }
            }
        } catch (DuplicateKeyException e) {
            if (requestKey == null) {
                throw e;
            }
            NoteEntity existing = noteMapper.findByUserAndRequestKey(userId, requestKey);
            if (existing != null) {
                return toCreated(existing);
            }
            throw new BizException(ErrorCode.INTERNAL_ERROR, "笔记发布失败，请重试");
        }
        // 先推进版本，再在事务提交后推进一次，隔离提交窗口内启动的旧查询。
        invalidateFirstPageCache();
        invalidateFirstPageAfterCommit();
        return toCreated(note);
    }

    public void delete(long userId, long noteId) {
        NoteEntity note = noteMapper.findById(noteId);
        if (note == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (note.getUserId() != userId) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        // 幂等：重复删除仍走同一条 UPDATE，code=0
        noteMapper.softDelete(noteId);
        invalidateFirstPageCache();
        redis.delete(KEY_NOTE_CARD + noteId);
    }

    /** 记录快照的轻量内部结构，避免把 media 实体散出模块 */
    private record MediaItemSnapshot(long id, String thumbUrl) {
    }

    /**
     * 推进首页缓存版本；旧查询即使在失效后完成，也只能写回旧版本键。
     * 由事务代理外的 HTTP 层在发布提交成功后再调用一次。
     */
    public void invalidateFirstPageCache() {
        redis.delete(KEY_FEED_FIRST);
        redis.opsForValue().increment(KEY_FEED_FIRST_VERSION);
    }

    /** 事务真正提交后推进版本，确保提交前快照不会成为当前首页缓存。 */
    private void invalidateFirstPageAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidateFirstPageCache();
            }
        });
    }

    // ---------- 聚合查询（详情/网格门面） ----------

    public NoteDetailVO detail(Long viewerId, long noteId) {
        NoteEntity n = requireVisible(viewerId, noteId);
        var briefs = briefsMap(List.of(n.getUserId()));
        var author = toAuthor(n.getUserId(), briefs);
        var images = mediaMapper.selectByNoteIds(List.of(noteId)).stream()
                .map(m -> new NoteDetailVO.ImageItem(m.getId(), minio.displayUrl(m),
                        m.getThumbUrl(), m.getWidth(), m.getHeight()))
                .toList();
        boolean mine = Objects.equals(viewerId, n.getUserId());
        return new NoteDetailVO(n.getId(), n.getTitle(), n.getContent(), n.getPlaceName(),
                n.getVisibility(), n.getCreatedAt().getTime(), author, images, mine);
    }

    public PageResult<GridCardVO> gridNotes(long targetUserId, Long viewerId,
                                            Long cursorParam, Integer limitParam) {
        boolean showPrivate = viewerId != null && viewerId == targetUserId;
        int limit = clampLimit(limitParam);
        long cursor = cursorParam == null ? Long.MAX_VALUE : cursorParam;
        var rows = noteMapper.selectGridRows(targetUserId, cursor, limit, showPrivate);
        List<GridCardVO> vos = rows.stream()
                .map(n -> new GridCardVO(n.getId(), n.getTitle(), n.getCoverUrl(),
                        n.getMediaCount(), n.getVisibility(), n.getCreatedAt().getTime()))
                .toList();
        return PageResult.build(vos, limit, GridCardVO::id);
    }

    NoteEntity requireVisible(Long viewerId, long noteId) {
        NoteEntity n = noteMapper.findById(noteId);
        // 不存在 / 已软删 / 他人私密 —— 一律 404 隐藏存在性
        if (n == null || n.getVisibility() == null || n.getVisibility() == 2
                || (n.getVisibility() == 0 && !Objects.equals(viewerId, n.getUserId()))) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return n;
    }

    public Map<Long, UserEntity> briefsMap(List<Long> userIds) {
        var distinct = userIds.stream().distinct().toList();
        return userMapper.selectBriefs(distinct).stream()
                .collect(java.util.stream.Collectors.toMap(UserEntity::getId, u -> u));
    }

    public AuthorVO toAuthor(long userId, Map<Long, UserEntity> briefs) {
        UserEntity u = briefs.get(userId);
        return u == null ? new AuthorVO(userId, "已注销", null)
                : new AuthorVO(u.getId(), u.getNickname(), u.getAvatarUrl());
    }

    private String normalizeRequestKey(String requestKey) {
        if (requestKey == null) {
            return null;
        }
        String trimmed = requestKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private NoteCreatedVO toCreated(NoteEntity note) {
        return new NoteCreatedVO(note.getId(), note.getCoverUrl());
    }

    static int clampLimit(Integer limitParam) {
        if (limitParam == null) {
            return 10;
        }
        return Math.max(1, Math.min(20, limitParam));
    }
}
