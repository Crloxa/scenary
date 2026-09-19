package com.scenary.social;

import com.scenary.common.AuthorVO;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.PageResult;
import com.scenary.common.RateLimitService;
import com.scenary.common.SocialVO;
import com.scenary.note.NoteService;
import com.scenary.notification.NotificationService;
import com.scenary.notification.NotificationType;
import com.scenary.user.UserService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P9 社交关系门面：关系表是唯一事实源，写操作靠唯一键实现幂等。
 * 跨模块只依赖 note/user 的 Service 门面，不触碰其 Mapper 或 Entity。
 */
@Service
public class SocialService {

    private final SocialMapper socialMapper;
    private final NoteService noteService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final RateLimitService rateLimitService;

    public SocialService(SocialMapper socialMapper, NoteService noteService, UserService userService) {
        this(socialMapper, noteService, userService, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SocialService(SocialMapper socialMapper, NoteService noteService, UserService userService,
                         NotificationService notificationService, RateLimitService rateLimitService) {
        this.socialMapper = socialMapper;
        this.noteService = noteService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.rateLimitService = rateLimitService;
    }

    @Transactional
    public SocialVO like(long userId, long noteId, boolean enabled) {
        if (rateLimitService != null) {
            rateLimitService.social(userId);
        }
        userService.ensureActive(userId);
        NoteService.NoteTarget target = noteService.socialTarget(noteId);
        userService.ensureActive(target.authorId());
        if (enabled) {
            int inserted = socialMapper.insertLike(noteId, userId);
            if (inserted == 1 && notificationService != null && target.authorId() != userId) {
                notificationService.create(target.authorId(), userId, NotificationType.LIKE, noteId, null);
            }
        } else {
            socialMapper.deleteLike(noteId, userId);
        }
        return noteSocial(noteId, userId);
    }

    @Transactional
    public SocialVO bookmark(long userId, long noteId, boolean enabled) {
        if (rateLimitService != null) {
            rateLimitService.social(userId);
        }
        userService.ensureActive(userId);
        NoteService.NoteTarget target = noteService.socialTarget(noteId);
        userService.ensureActive(target.authorId());
        if (enabled) {
            socialMapper.insertBookmark(noteId, userId);
        } else {
            socialMapper.deleteBookmark(noteId, userId);
        }
        return noteSocial(noteId, userId);
    }

    @Transactional
    public SocialVO follow(long followerId, long followingId, boolean enabled) {
        if (rateLimitService != null) {
            rateLimitService.social(followerId);
        }
        userService.ensureActive(followerId);
        userService.ensureActive(followingId);
        if (followerId == followingId) {
            throw new BizException(ErrorCode.VALIDATION, "不能关注自己");
        }
        if (enabled) {
            int inserted = socialMapper.insertFollow(followerId, followingId);
            if (inserted == 1 && notificationService != null) {
                notificationService.create(followingId, followerId, NotificationType.FOLLOW, null, null);
            }
        } else {
            socialMapper.deleteFollow(followerId, followingId);
        }
        return userSocial(followingId, followerId);
    }

    /**
     * P18 屏蔽/取消屏蔽（docs/02 §10.2）：幂等；不能屏蔽自己；
     * 目标禁用/注销/不存在统一 40400（对齐 follow 边界）。不产生通知。
     */
    @Transactional
    public void block(long userId, long targetUserId, boolean enabled) {
        if (rateLimitService != null) {
            rateLimitService.social(userId);
        }
        userService.ensureActive(userId);
        userService.ensureActive(targetUserId);
        if (userId == targetUserId) {
            throw new BizException(ErrorCode.VALIDATION, "不能屏蔽自己");
        }
        if (enabled) {
            socialMapper.insertBlock(userId, targetUserId);
        } else {
            socialMapper.deleteBlock(userId, targetUserId);
        }
    }

    /** 双向屏蔽判定（P18）：NoteController 详情过滤等场景使用。 */
    public boolean isBlockedEitherWay(long userA, long userB) {
        if (userA == userB) {
            return false;
        }
        Boolean blocked = socialMapper.existsBlockEitherWay(userA, userB);
        return Boolean.TRUE.equals(blocked);
    }

    public SocialVO noteSocial(long noteId, Long viewerId) {
        return socialRow(noteId, viewerId);
    }

    public SocialVO userSocial(long targetUserId, Long viewerId) {
        SocialRow row = socialMapper.selectUserSocial(targetUserId, viewerId);
        return row == null ? SocialVO.empty() : toSocial(row);
    }

    public Map<Long, SocialVO> noteStatuses(List<Long> noteIds, Long viewerId) {
        return socialRows(noteIds, viewerId);
    }

    public PageResult<BookmarkVO> bookmarks(long userId, Long cursorParam, Integer limitParam) {
        userService.ensureActive(userId);
        int limit = clampLimit(limitParam);
        long cursor = cursorParam == null ? Long.MAX_VALUE : cursorParam;
        List<BookmarkRow> rows = socialMapper.selectBookmarks(userId, cursor, limit + 1);
        List<BookmarkRow> pageRows = rows.stream().limit(limit).toList();
        Map<Long, SocialVO> statuses = socialRows(pageRows.stream().map(BookmarkRow::getNoteId).toList(), userId);
        List<BookmarkVO> items = pageRows.stream()
                .map(row -> toBookmark(row, statuses.getOrDefault(row.getNoteId(), SocialVO.empty())))
                .toList();
        boolean hasMore = rows.size() > limit;
        Long nextCursor = hasMore ? rows.get(limit - 1).getBookmarkId() : null;
        return PageResult.of(items, nextCursor, hasMore);
    }

    /**
     * 关注者列表（docs/02 §3.9，P16-02）：目标禁用/注销/不存在即 40400（对齐 3.4 边界）。
     * 游标由 follows.id 驱动，仅列出 status=1 用户；匿名视角 following=false。
     */
    public PageResult<FollowVO> followers(long targetUserId, Long viewerId,
                                          Long cursorParam, Integer limitParam) {
        return followList(targetUserId, viewerId, cursorParam, limitParam, true);
    }

    /** 正在关注列表（docs/02 §3.10，P16-02），语义与 followers 对称。 */
    public PageResult<FollowVO> following(long targetUserId, Long viewerId,
                                          Long cursorParam, Integer limitParam) {
        return followList(targetUserId, viewerId, cursorParam, limitParam, false);
    }

    private PageResult<FollowVO> followList(long targetUserId, Long viewerId,
                                            Long cursorParam, Integer limitParam, boolean followerDirection) {
        userService.ensureActive(targetUserId);
        int limit = clampLimit(limitParam);
        long cursor = cursorParam == null ? Long.MAX_VALUE : cursorParam;
        List<FollowRow> rows = followerDirection
                ? socialMapper.selectFollowers(targetUserId, viewerId, cursor, limit + 1)
                : socialMapper.selectFollowing(targetUserId, viewerId, cursor, limit + 1);
        List<FollowVO> items = rows.stream().limit(limit)
                .map(row -> new FollowVO(row.getUserId(), row.getNickname(),
                        userService.viewUrl(row.getAvatarUrl()),
                        Boolean.TRUE.equals(row.getFollowing())))
                .toList();
        boolean hasMore = rows.size() > limit;
        Long nextCursor = hasMore ? rows.get(limit - 1).getId() : null;
        return PageResult.of(items, nextCursor, hasMore);
    }

    private SocialVO noteSocial(long noteId, long viewerId) {
        return socialRow(noteId, viewerId);
    }

    private SocialVO socialRow(long noteId, Long viewerId) {
        Map<Long, SocialVO> rows = socialRows(List.of(noteId), viewerId);
        return rows.getOrDefault(noteId, SocialVO.empty());
    }

    private Map<Long, SocialVO> socialRows(List<Long> noteIds, Long viewerId) {
        Map<Long, SocialVO> out = new LinkedHashMap<>();
        if (noteIds.isEmpty()) {
            return out;
        }
        for (SocialRow row : socialMapper.selectNoteSocial(noteIds, viewerId)) {
            out.put(row.getNoteId(), toSocial(row));
        }
        return out;
    }

    private SocialVO toSocial(SocialRow row) {
        return new SocialVO(Boolean.TRUE.equals(row.getLiked()),
                Boolean.TRUE.equals(row.getBookmarked()),
                Boolean.TRUE.equals(row.getFollowing()),
                value(row.getLikeCount()), value(row.getBookmarkCount()),
                value(row.getFollowerCount()), value(row.getFollowingCount()));
    }

    private BookmarkVO toBookmark(BookmarkRow row, SocialVO social) {
        AuthorVO author = new AuthorVO(row.getAuthorId(), row.getAuthorNickname(),
                noteService.viewUrl(row.getAuthorAvatarUrl()));
        return new BookmarkVO(row.getNoteId(), row.getTitle(), preview(row.getContent()),
                noteService.viewUrl(row.getCoverUrl()), row.getMediaCount() == null ? 0 : row.getMediaCount(),
                author, row.getCreatedAt().getTime(), social);
    }

    private static long value(Long value) {
        return value == null ? 0 : value;
    }

    private static String preview(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        String trimmed = content.strip();
        return trimmed.length() <= 48 ? trimmed : trimmed.substring(0, 48) + "…";
    }

    static int clampLimit(Integer limitParam) {
        if (limitParam == null) {
            return 10;
        }
        return Math.max(1, Math.min(20, limitParam));
    }
}
