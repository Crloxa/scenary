package com.scenary.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scenary.common.Result;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<AuthVO> register(@Valid @RequestBody RegisterRequest req,
                                   HttpServletRequest http) {
        return Result.ok(authService.register(req, clientIp(http)));
    }

    @PostMapping("/login")
    public Result<AuthVO> login(@Valid @RequestBody LoginRequest req,
                                HttpServletRequest http) {
        return Result.ok(authService.login(req, clientIp(http)));
    }

    @PostMapping("/refresh")
    public Result<AuthVO> refresh(@Valid @RequestBody RefreshRequest req) {
        return Result.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return Result.ok();
    }

    /**
     * 取真实客户端 IP：信任层只有 nginx 一个反代，取 XFF 第一段即可；
     * 直连开发环境走 remoteAddr。
     */
    static String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
