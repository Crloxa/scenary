package com.scenary.comment;

import java.util.List;
import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scenary.common.AuthorVO;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.PageResult;
import com.scenary.common.RateLimitService;
import com.scenary.note.NoteService;
import com.scenary.notification.NotificationService;
import com.scenary.notification.NotificationType;
import com.scenary.user.UserService;

/** P10 评论门面：文本校验、同笔记回复约束、软删和同步通知。 */
@Service
public class CommentService {

    private static final String DELETED_CONTENT = "该评论已删除";

    private final CommentMapper commentMapper;
    private final NoteService noteService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final RateLimitService rateLimitService;
    private final SensitiveWordFilter sensitiveWordFilter;

    public CommentService(CommentMapper commentMapper, NoteService noteService,
                          UserService userService, NotificationService notificationService,
                          RateLimitService rateLimitService,
                          SensitiveWordFilter sensitiveWordFilter) {
        this.commentMapper = commentMapper;
        this.noteService = noteService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.rateLimitService = rateLimitService;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    public PageResult<CommentVO> page(Long viewerId, long noteId,
                                      Long cursorParam, Integer limitParam) {
        noteService.commentTarget(viewerId, noteId);
        int limit = clampLimit(limitParam);
        long cursor = cursorParam == null ? 0L : Math.max(0L, cursorParam);
        List<CommentRow> rows = commentMapper.selectPage(noteId, cursor, limit + 1);
        List<CommentRow> pageRows = rows.stream().limit(limit).toList();
        List<CommentVO> items = pageRows.stream().map(row -> toVO(row, viewerId)).toList();
        boolean hasMore = rows.size() > limit;
        Long nextCursor = hasMore ? pageRows.get(pageRows.size() - 1).getId() : null;
        return PageResult.of(items, nextCursor, hasMore);
    }

    @Transactional
    public CommentVO create(long userId, long noteId, CommentCreateRequest request) {
        userService.ensureActive(userId);
        NoteService.NoteTarget target = noteService.commentTargetForUpdate(userId, noteId);
        String content = normalizeContent(request.content());
        Long parentId = request.parentId();
        int maxLength = parentId == null ? 500 : 300;
        if (content.codePointCount(0, content.length()) > maxLength) {
            throw new BizException(ErrorCode.VALIDATION,
                    (parentId == null ? "一级评论" : "回复") + "最长 " + maxLength + " 字");
        }
        if (sensitiveWordFilter.contains(content)) {
            throw new BizException(ErrorCode.VALIDATION, "评论包含不适宜内容");
        }
        CommentEntity parent = null;
        if (parentId != null) {
            parent = commentMapper.findByIdForUpdate(parentId);
            if (parent == null || parent.getNoteId() == null || parent.getNoteId() != noteId) {
                throw new BizException(ErrorCode.VALIDATION, "回复目标不属于该笔记");
            }
            if (parent.getStatus() == null || parent.getStatus() != 1) {
                throw new BizException(ErrorCode.VALIDATION, "不能回复已删除评论");
            }
        }
        enforceRateLimit(userId);

        CommentEntity comment = new CommentEntity();
        comment.setNoteId(noteId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setContent(content);
        commentMapper.insert(comment);

        NotificationType type = parentId == null ? NotificationType.COMMENT : NotificationType.REPLY;
        long recipientId = parentId == null ? target.authorId()
                : parent == null || parent.getUserId() == null ? target.authorId() : parent.getUserId();
        if (recipientId != userId) {
            notificationService.create(recipientId, userId, type, noteId, comment.getId());
        }
        CommentRow row = rowForCreated(comment, userId);
        return toVO(row, userId);
    }

    @Transactional
    public void delete(long userId, long commentId) {
        userService.ensureActive(userId);
        CommentEntity comment = commentMapper.findById(commentId);
        if (comment == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (comment.getUserId() != userId) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (comment.getStatus() == null || comment.getStatus() == 2) {
            return;
        }
        commentMapper.softDelete(commentId, userId);
    }

    /** 账号注销：作者名下全部未删评论软删，展示侧按既有"已删除"占位渲染（docs/02 §3.8）。 */
    @Transactional
    /**
     * P18 评论举报前置校验（docs/02 §10.1）：评论存在、未删除、所属笔记对举报者可见、
     * 且不是自己的评论。仅做校验与门面收敛，报告行由 report 模块落库。
     */
    public void reportTarget(long reporterId, long commentId) {
        var c = commentMapper.findById(commentId);
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (c.getUserId() == reporterId) {
            throw new BizException(ErrorCode.VALIDATION, "不能举报自己的评论");
        }
        noteService.commentTarget(reporterId, c.getNoteId());
    }

    public int deactivateAuthorComments(long userId) {
        return commentMapper.softDeleteAllByAuthor(userId);
    }

    private CommentRow rowForCreated(CommentEntity comment, long userId) {
        CommentRow row = new CommentRow();
        row.setId(comment.getId());
        row.setNoteId(comment.getNoteId());
        row.setUserId(userId);
        row.setParentId(comment.getParentId());
        row.setContent(comment.getContent());
        row.setStatus(1);
        Date now = new Date();
        row.setCreatedAt(comment.getCreatedAt() == null ? now : comment.getCreatedAt());
        row.setUpdatedAt(comment.getUpdatedAt() == null ? now : comment.getUpdatedAt());
        var author = userService.author(userId);
        row.setAuthorNickname(author.nickname());
        row.setAuthorAvatarUrl(author.avatarUrl());
        return row;
    }

    private CommentVO toVO(CommentRow row, Long viewerId) {
        int status = row.getStatus() == null ? 2 : row.getStatus();
        boolean mine = viewerId != null && viewerId.equals(row.getUserId());
        String nickname = row.getAuthorNickname() == null ? "已注销" : row.getAuthorNickname();
        long authorId = row.getUserId() == null ? 0L : row.getUserId();
        String content = status == 2 ? DELETED_CONTENT : row.getContent();
        long createdAt = row.getCreatedAt() == null ? 0L : row.getCreatedAt().getTime();
        Long updatedAt = row.getUpdatedAt() == null ? null : row.getUpdatedAt().getTime();
        return new CommentVO(row.getId(), row.getNoteId(), row.getParentId(), content, status,
                new AuthorVO(authorId, nickname, noteService.viewUrl(row.getAuthorAvatarUrl())),
                createdAt, updatedAt, mine, mine && status == 1);
    }

    private String normalizeContent(String raw) {
        if (raw == null) {
            throw new BizException(ErrorCode.VALIDATION, "评论内容不能为空");
        }
        String content = raw.strip();
        if (content.isEmpty()) {
            throw new BizException(ErrorCode.VALIDATION, "评论内容不能为空");
        }
        if (content.matches("(?s).*<[^>]*>.*")) {
            throw new BizException(ErrorCode.VALIDATION, "评论仅支持纯文本");
        }
        if (content.codePoints().anyMatch(cp -> Character.isISOControl(cp)
                && cp != '\n' && cp != '\r' && cp != '\t')) {
            throw new BizException(ErrorCode.VALIDATION, "评论包含非法控制字符");
        }
        return content;
    }

    private void enforceRateLimit(long userId) {
        rateLimitService.comments(userId);
    }

    static int clampLimit(Integer limitParam) {
        if (limitParam == null) {
            return 10;
        }
        return Math.max(1, Math.min(20, limitParam));
    }
}
