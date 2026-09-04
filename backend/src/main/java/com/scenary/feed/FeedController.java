package com.scenary.feed;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.scenary.common.PageResult;
import com.scenary.common.Result;
import com.scenary.auth.JwtUtil;

/**
 * 首页瀑布流数据源（docs/02 §6.1），免认证。
 */
@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

    private final FeedService feedService;
    private final JwtUtil jwtUtil;

    public FeedController(FeedService feedService, JwtUtil jwtUtil) {
        this.feedService = feedService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public Result<PageResult<NoteCardVO>> feed(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.ok(feedService.page(jwtUtil.peekUserId(authorization), cursor, limit));
    }
}
