package com.scenary.search;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scenary.auth.JwtUtil;
import com.scenary.common.Result;

/** 公开搜索入口；可选 access token 只用于补齐当前用户的社交状态。 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;
    private final JwtUtil jwtUtil;

    public SearchController(SearchService searchService, JwtUtil jwtUtil) {
        this.searchService = searchService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/notes")
    public Result<SearchPageResult<SearchNoteVO>> notes(
            @RequestParam String q,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false, defaultValue = "recent") String sort,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.ok(searchService.search(jwtUtil.peekUserId(authorization), q, cursor, limit, sort));
    }
}
