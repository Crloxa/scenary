package com.scenary.common;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 连通性自测端点，响应契约固定为 docs/02 §7：{"code":0,"data":{"pong":"v1"}}。
 */
@RestController
@RequestMapping("/api/v1")
public class PingController {

    @GetMapping("/ping")
    public Result<Map<String, String>> ping() {
        return Result.ok(Map.of("pong", "v1"));
    }
}
