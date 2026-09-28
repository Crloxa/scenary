package com.scenary.place;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scenary.auth.JwtUtil;
import com.scenary.auth.UserContext;
import com.scenary.common.Result;

/**
 * 地点模块（契约 docs/02 §7B，P12-E3 / E5）。reverse-geocode 仅登录用户——
 * 受保护路径已在 WebConfig 登记（P18 学习 24：白名单式拦截，漏登记即匿名可达）；
 * places/notes 为公开只读（§10.5），不进拦截白名单，匿名与登录视角由 viewerId 区分。
 */
@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final PlaceService placeService;
    private final JwtUtil jwtUtil;

    public PlaceController(PlaceService placeService, JwtUtil jwtUtil) {
        this.placeService = placeService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/reverse-geocode")
    public Result<ReverseGeocodeVO> reverseGeocode(@RequestParam double latitude,
                                                   @RequestParam double longitude) {
        return Result.ok(placeService.reverseGeocode(UserContext.require(), latitude, longitude));
    }

    @GetMapping("/notes")
    public Result<PlaceMapPage> mapNotes(@RequestParam double minLat,
                                         @RequestParam double maxLat,
                                         @RequestParam double minLng,
                                         @RequestParam double maxLng,
                                         @RequestParam(required = false) String cursor,
                                         @RequestParam(required = false) Integer limit,
                                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long viewerId = jwtUtil.peekUserId(authorization);
        return Result.ok(placeService.mapNotes(viewerId, minLat, maxLat, minLng, maxLng, cursor, limit));
    }
}
