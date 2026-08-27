package com.scenary.user;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scenary.auth.UserContext;
import com.scenary.common.Result;

/**
 * 用户模块入口。当前为 3.5 拦截链验收占位：仅回显 UserContext 解析结果；
 * GET/PATCH 的完整契约实现与资料编辑在 Phase 3-3.8 补全。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        return Result.ok(Map.of("userId", UserContext.require(), "stage", "stub-for-3.5"));
    }

    @PatchMapping("/me")
    public Result<Map<String, Object>> patchMe() {
        return Result.ok(Map.of("userId", UserContext.require(), "stage", "stub-for-3.5"));
    }
}
