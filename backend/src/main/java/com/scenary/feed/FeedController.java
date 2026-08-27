package com.scenary.feed;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scenary.common.PageResult;
import com.scenary.common.Result;

/**
 * 首页瀑布流数据源（docs/02 §6.1），免认证。
 */
@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping
    public Result<PageResult<NoteCardVO>> feed(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit) {
        return Result.ok(feedService.page(cursor, limit));
    }
}
