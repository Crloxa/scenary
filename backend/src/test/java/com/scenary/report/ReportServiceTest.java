package com.scenary.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.scenary.comment.CommentService;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.RateLimitService;
import com.scenary.note.NoteService;
import com.scenary.user.UserService;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportMapper reportMapper;
    @Mock
    private NoteService noteService;
    @Mock
    private CommentService commentService;
    @Mock
    private UserService userService;
    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private ReportService service;

    private ReportRequest noteReport() {
        return new ReportRequest("note", 9L, "SPAM", "广告");
    }

    @Test
    void noteReportAppliesCountAndReturnsHiddenFlag() {
        when(noteService.applyReportCount(7L, 9L)).thenReturn(true);

        ReportCreatedVO vo = service.create(7L, noteReport());

        assertTrue(vo.created());
        assertTrue(vo.hidden());
        verify(noteService).reportTarget(7L, 9L);
        verify(reportMapper).insert(7L, "note", 9L, "SPAM", "广告");
    }

    @Test
    void duplicateReportIsIdempotentAndSkipsCounting() {
        when(reportMapper.insert(anyLong(), anyString(), anyLong(), anyString(), any()))
                .thenThrow(new DuplicateKeyException("uk"));

        ReportCreatedVO vo = service.create(7L, noteReport());

        assertFalse(vo.created());
        assertFalse(vo.hidden());
        verify(noteService, never()).applyReportCount(anyLong(), anyLong());
    }

    @Test
    void invalidTargetPropagatesWithoutInsert() {
        doThrow(new BizException(ErrorCode.VALIDATION, "不能举报自己的笔记"))
                .when(noteService).reportTarget(7L, 9L);

        BizException ex = assertThrows(BizException.class, () -> service.create(7L, noteReport()));
        assertEquals(ErrorCode.VALIDATION, ex.getErrorCode());
        verify(reportMapper, never()).insert(anyLong(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void commentReportValidatesViaCommentFacade() {
        ReportCreatedVO vo = service.create(7L,
                new ReportRequest("comment", 55L, "OTHER", null));

        assertTrue(vo.created());
        assertFalse(vo.hidden());
        verify(commentService).reportTarget(7L, 55L);
        verify(noteService, never()).applyReportCount(anyLong(), anyLong());
    }
}
