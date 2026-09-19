package com.scenary.social;

import com.scenary.auth.JwtUtil;
import com.scenary.auth.UserContext;
import com.scenary.common.PageResult;
import com.scenary.common.Result;
import com.scenary.common.SocialVO;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** P9 社交关系接口。所有写接口由 AuthInterceptor 保护；关注列表公开（P16-02）。 */
@RestController
@RequestMapping("/api/v1")
public class SocialController {

    private final SocialService socialService;
    private final JwtUtil jwtUtil;

    public SocialController(SocialService socialService, JwtUtil jwtUtil) {
        this.socialService = socialService;
        this.jwtUtil = jwtUtil;
    }

    @PutMapping("/notes/{noteId}/like")
    public Result<SocialVO> like(@PathVariable long noteId) {
        return Result.ok(socialService.like(UserContext.require(), noteId, true));
    }

    @DeleteMapping("/notes/{noteId}/like")
    public Result<SocialVO> unlike(@PathVariable long noteId) {
        return Result.ok(socialService.like(UserContext.require(), noteId, false));
    }

    @PutMapping("/notes/{noteId}/bookmark")
    public Result<SocialVO> bookmark(@PathVariable long noteId) {
        return Result.ok(socialService.bookmark(UserContext.require(), noteId, true));
    }

    @DeleteMapping("/notes/{noteId}/bookmark")
    public Result<SocialVO> unbookmark(@PathVariable long noteId) {
        return Result.ok(socialService.bookmark(UserContext.require(), noteId, false));
    }

    @PutMapping("/users/{userId}/follow")
    public Result<SocialVO> follow(@PathVariable long userId) {
        return Result.ok(socialService.follow(UserContext.require(), userId, true));
    }

    @DeleteMapping("/users/{userId}/follow")
    public Result<SocialVO> unfollow(@PathVariable long userId) {
        return Result.ok(socialService.follow(UserContext.require(), userId, false));
    }

    /** 屏蔽/取消屏蔽（docs/02 §10.2，P18）：路径已由拦截器要求登录。 */
    @org.springframework.web.bind.annotation.RequestMapping(
            value = "/users/{targetUserId}/block", method = {org.springframework.web.bind.annotation.RequestMethod.PUT})
    public Result<Void> block(@PathVariable long targetUserId) {
        socialService.block(UserContext.require(), targetUserId, true);
        return Result.ok();
    }

    @org.springframework.web.bind.annotation.RequestMapping(
            value = "/users/{targetUserId}/block", method = {org.springframework.web.bind.annotation.RequestMethod.DELETE})
    public Result<Void> unblock(@PathVariable long targetUserId) {
        socialService.block(UserContext.require(), targetUserId, false);
        return Result.ok();
    }

    @GetMapping("/users/me/bookmarks")
    public Result<PageResult<BookmarkVO>> bookmarks(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit) {
        return Result.ok(socialService.bookmarks(UserContext.require(), cursor, limit));
    }

    /** 关注者列表（docs/02 §3.9，P16-02）：公开可看，登录视角返回 following。 */
    @GetMapping("/users/{userId}/followers")
    public Result<PageResult<FollowVO>> followers(
            @PathVariable long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit) {
        return Result.ok(socialService.followers(userId, jwtUtil.peekUserId(authorization), cursor, limit));
    }

    /** 正在关注列表（docs/02 §3.10，P16-02）。 */
    @GetMapping("/users/{userId}/following")
    public Result<PageResult<FollowVO>> following(
            @PathVariable long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit) {
        return Result.ok(socialService.following(userId, jwtUtil.peekUserId(authorization), cursor, limit));
    }
}
