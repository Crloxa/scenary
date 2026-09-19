package com.scenary.place;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.scenary.auth.UserContext;
import com.scenary.common.Result;

/**
 * 地点模块（契约 docs/02 §7B，P12-E3）。仅登录用户可用——
 * 受保护路径已在 WebConfig 登记（P18 学习 24：白名单式拦截，漏登记即匿名可达）。
 */
@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping("/reverse-geocode")
    public Result<ReverseGeocodeVO> reverseGeocode(@RequestParam double latitude,
                                                   @RequestParam double longitude) {
        return Result.ok(placeService.reverseGeocode(UserContext.require(), latitude, longitude));
    }
}
