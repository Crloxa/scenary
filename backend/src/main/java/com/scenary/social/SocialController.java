package com.scenary.social;

import com.scenary.auth.UserContext;
import com.scenary.common.PageResult;
import com.scenary.common.Result;
import com.scenary.common.SocialVO;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** P9 社交关系接口。所有写接口由 AuthInterceptor 保护。 */
@RestController
@RequestMapping("/api/v1")
public class SocialController {

    private final SocialService socialService;

    public SocialController(SocialService socialService) {
        this.socialService = socialService;
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

    @GetMapping("/users/me/bookmarks")
    public Result<PageResult<BookmarkVO>> bookmarks(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit) {
        return Result.ok(socialService.bookmarks(UserContext.require(), cursor, limit));
    }
}
