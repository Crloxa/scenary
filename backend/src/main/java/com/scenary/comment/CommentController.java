package com.scenary.comment;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scenary.auth.JwtUtil;
import com.scenary.auth.UserContext;
import com.scenary.common.PageResult;
import com.scenary.common.Result;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class CommentController {

    private final CommentService commentService;
    private final JwtUtil jwtUtil;

    public CommentController(CommentService commentService, JwtUtil jwtUtil) {
        this.commentService = commentService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/notes/{noteId}/comments")
    public Result<PageResult<CommentVO>> page(
            @PathVariable long noteId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.ok(commentService.page(jwtUtil.peekUserId(authorization), noteId, cursor, limit));
    }

    @PostMapping("/notes/{noteId}/comments")
    public Result<CommentVO> create(@PathVariable long noteId,
                                    @Valid @RequestBody CommentCreateRequest request) {
        return Result.ok(commentService.create(UserContext.require(), noteId, request));
    }

    @DeleteMapping("/comments/{commentId}")
    public Result<Void> delete(@PathVariable long commentId) {
        commentService.delete(UserContext.require(), commentId);
        return Result.ok();
    }
}
