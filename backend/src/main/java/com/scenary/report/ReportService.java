package com.scenary.report;

import com.scenary.comment.CommentService;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.RateLimitService;
import com.scenary.note.NoteService;
import com.scenary.user.UserService;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 举报编排（docs/02 §10.1，P18）：报告行插入与计数/隐藏分属两个事务边界——
 * 计数与自动隐藏在 note 门面内完成，报告行插入幂等（唯一键去重）。
 * 无管理员角色，处置动作只有"达阈值自动隐藏"；人工处置走 ops 只读报表（P13 边界）。
 */
@Service
public class ReportService {

    private final ReportMapper reportMapper;
    private final NoteService noteService;
    private final CommentService commentService;
    private final UserService userService;
    private final RateLimitService rateLimitService;

    public ReportService(ReportMapper reportMapper, NoteService noteService,
                         CommentService commentService, UserService userService,
                         RateLimitService rateLimitService) {
        this.reportMapper = reportMapper;
        this.noteService = noteService;
        this.commentService = commentService;
        this.userService = userService;
        this.rateLimitService = rateLimitService;
    }

    @Transactional
    public ReportCreatedVO create(long reporterId, ReportRequest req) {
        rateLimitService.reports(reporterId);
        userService.ensureActive(reporterId);
        boolean hidden = false;
        boolean created;
        try {
            if ("note".equals(req.targetType())) {
                noteService.reportTarget(reporterId, req.targetId());
                reportMapper.insert(reporterId, "note", req.targetId(),
                        req.reasonCode(), req.reasonText());
                hidden = noteService.applyReportCount(reporterId, req.targetId());
            } else {
                commentService.reportTarget(reporterId, req.targetId());
                reportMapper.insert(reporterId, "comment", req.targetId(),
                        req.reasonCode(), req.reasonText());
            }
            created = true;
        } catch (DuplicateKeyException e) {
            // 同用户同目标重复举报：幂等返回，不重复计数
            created = false;
        }
        return new ReportCreatedVO(created, hidden);
    }
}
