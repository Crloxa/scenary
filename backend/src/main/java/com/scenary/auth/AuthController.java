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
     * 取真实客户端 IP：信任层只有 nginx 一个反代。XFF 取最后一段——该段由本方 nginx
     * 追加（$proxy_add_x_forwarded_for），客户端伪造的段只会落在前面；
     * 直连开发环境（无 XFF/X-Real-IP）走 remoteAddr。
     */
    static String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] segments = xff.split(",");
            return segments[segments.length - 1].trim();
        }
        String realIp = http.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return http.getRemoteAddr();
    }
}
