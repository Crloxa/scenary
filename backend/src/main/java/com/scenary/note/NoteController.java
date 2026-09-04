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
import com.scenary.social.SocialService;

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
    private final SocialService socialService;

    public NoteController(NoteService noteService, JwtUtil jwtUtil, SocialService socialService) {
        this.noteService = noteService;
        this.jwtUtil = jwtUtil;
        this.socialService = socialService;
    }

    @PostMapping
    public Result<NoteCreatedVO> create(@Valid @RequestBody NoteCreateRequest req) {
        NoteCreatedVO created = noteService.create(UserContext.require(), req);
        // NoteService 返回时事务已提交；此处再推进版本，保证发布响应后的首页读取不会复用旧快照。
        noteService.invalidateFirstPageCache();
        return Result.ok(created);
    }

    @GetMapping("/{noteId}")
    public Result<NoteDetailVO> detail(
            @PathVariable long noteId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long viewer = jwtUtil.peekUserId(authorization);
        return Result.ok(noteService.detail(viewer, noteId)
                .withSocial(socialService.noteSocial(noteId, viewer)));
    }

    @DeleteMapping("/{noteId}")
    public Result<Void> delete(@PathVariable long noteId) {
        noteService.delete(UserContext.require(), noteId);
        noteService.invalidateFirstPageCache();
        return Result.ok();
    }
}
