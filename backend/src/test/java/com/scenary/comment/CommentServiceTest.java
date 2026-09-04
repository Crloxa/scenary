package com.scenary.comment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.scenary.common.AuthorVO;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.PageResult;
import com.scenary.note.NoteService;
import com.scenary.notification.NotificationService;
import com.scenary.user.UserService;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentMapper commentMapper;
    @Mock private NoteService noteService;
    @Mock private UserService userService;
    @Mock private NotificationService notificationService;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOperations;

    @Test
    void pageKeepsDeletedPlaceholderAndUsesAscendingCursor() {
        CommentService service = service();
        when(noteService.commentTarget(null, 9L)).thenReturn(new NoteService.NoteTarget(9L, 11L));
        CommentRow active = row(3L, 7L, 1, "好看", 100L);
        CommentRow deleted = row(4L, 8L, 2, "原文不应直接暴露", 200L);
        when(commentMapper.selectPage(9L, 0L, 3)).thenReturn(List.of(active, deleted));

        PageResult<CommentVO> page = service.page(null, 9L, null, 2);

        assertEquals(List.of(3L, 4L), page.getList().stream().map(CommentVO::id).toList());
        assertEquals("该评论已删除", page.getList().get(1).content());
        assertEquals(null, page.getNextCursor());
        assertTrue(!page.isHasMore());
        verify(commentMapper).selectPage(9L, 0L, 3);
    }

    @Test
    void createRootCommentWritesOneSynchronousNotification() {
        CommentService service = service();
        when(noteService.commentTargetForUpdate(7L, 9L))
                .thenReturn(new NoteService.NoteTarget(9L, 11L));
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("rl:comment:7")).thenReturn(1L);
        when(userService.author(7L)).thenReturn(new AuthorVO(7L, "读者", null));
        doAnswer(invocation -> {
            CommentEntity comment = invocation.getArgument(0);
            comment.setId(21L);
            comment.setCreatedAt(new Date(300L));
            comment.setUpdatedAt(new Date(300L));
            return 1;
        }).when(commentMapper).insert(any(CommentEntity.class));

        CommentVO result = service.create(7L, 9L,
                new CommentCreateRequest("  好看的云  ", null));

        assertEquals(21L, result.id());
        assertEquals("好看的云", result.content());
        assertTrue(result.mine());
        verify(notificationService).create(11L, 7L,
                com.scenary.notification.NotificationType.COMMENT, 9L, 21L);
    }

    @Test
    void crossNoteReplyIsRejectedBeforeInsert() {
        CommentService service = service();
        when(noteService.commentTargetForUpdate(7L, 9L))
                .thenReturn(new NoteService.NoteTarget(9L, 11L));
        CommentEntity parent = new CommentEntity();
        parent.setId(31L);
        parent.setNoteId(10L);
        parent.setStatus(1);
        when(commentMapper.findByIdForUpdate(31L)).thenReturn(parent);

        BizException ex = assertThrows(BizException.class,
                () -> service.create(7L, 9L, new CommentCreateRequest("回复", 31L)));

        assertEquals(ErrorCode.VALIDATION, ex.getErrorCode());
        verify(commentMapper, never()).insert(any(CommentEntity.class));
        verify(redis, never()).opsForValue();
    }

    @Test
    void deleteIsOwnerOnlyAndRepeatedDeleteIsIdempotent() {
        CommentService service = service();
        CommentEntity comment = new CommentEntity();
        comment.setId(41L);
        comment.setUserId(7L);
        comment.setStatus(2);
        when(commentMapper.findById(41L)).thenReturn(comment);

        service.delete(7L, 41L);

        verify(commentMapper, never()).softDelete(41L, 7L);
        comment.setStatus(1);
        service.delete(7L, 41L);
        verify(commentMapper).softDelete(41L, 7L);
    }

    private CommentService service() {
        return new CommentService(commentMapper, noteService, userService, notificationService, redis);
    }

    private CommentRow row(long id, long userId, int status, String content, long createdAt) {
        CommentRow row = new CommentRow();
        row.setId(id);
        row.setNoteId(9L);
        row.setUserId(userId);
        row.setContent(content);
        row.setStatus(status);
        row.setCreatedAt(new Date(createdAt));
        row.setUpdatedAt(new Date(createdAt));
        row.setAuthorNickname("用户" + userId);
        return row;
    }
}
