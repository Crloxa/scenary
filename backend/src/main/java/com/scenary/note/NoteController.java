package com.scenary.note;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scenary.auth.JwtUtil;
import com.scenary.auth.UserContext;
import com.scenary.common.Result;

import jakarta.validation.Valid;

/**
 * 笔记模块接口（docs/02 §5）。POST/DELETE 受拦截器保护取 UserContext；
 * GET 详情公开但支持可选令牌判定 mine。
 */
@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    private final NoteService noteService;
    private final JwtUtil jwtUtil;

    public NoteController(NoteService noteService, JwtUtil jwtUtil) {
        this.noteService = noteService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    public Result<NoteCreatedVO> create(@Valid @RequestBody NoteCreateRequest req) {
        return Result.ok(noteService.create(UserContext.require(), req));
    }

    @GetMapping("/{noteId}")
    public Result<NoteDetailVO> detail(
            @PathVariable long noteId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long viewer = jwtUtil.peekUserId(authorization);
        return Result.ok(noteService.detail(viewer, noteId));
    }

    @DeleteMapping("/{noteId}")
    public Result<Void> delete(@PathVariable long noteId) {
        noteService.delete(UserContext.require(), noteId);
        return Result.ok();
    }
}
