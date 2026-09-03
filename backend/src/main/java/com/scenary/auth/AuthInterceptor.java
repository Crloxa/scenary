package com.scenary.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;

import io.jsonwebtoken.Claims;

/**
 * Bearer access token 校验链：格式 -> 验签/过期 -> 类型必须是 access -> 登出黑名单。
 * 通过后 userId 进 UserContext，afterCompletion 无条件清理防跨请求串号。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final AuthService authService;

    public AuthInterceptor(JwtUtil jwtUtil, AuthService authService) {
        this.jwtUtil = jwtUtil;
        this.authService = authService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        // 注册模式无法表达方法条件，契约里的公开读在此豁免：笔记详情匿名可看（docs/02 §5.2）。
        // 必须同时限定 GET，否则会连 DELETE /notes/{id} 一起放行。
        String uri = request.getRequestURI();
        if (HttpMethod.GET.matches(request.getMethod())
                && (uri.endsWith("/api/v1/notes") || uri.matches(".*/notes/\\d+"))) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Claims claims = jwtUtil.parse(JwtUtil.stripBearer(header));
        if (!JwtUtil.TYPE_ACCESS.equals(claims.get("type", String.class))) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (authService.isAccessBlacklisted(claims)) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "令牌已登出");
        }
        long userId = Long.parseLong(claims.getSubject());
        UserContext.set(userId);
        MDC.put("userId", String.valueOf(userId));
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        UserContext.clear();
        MDC.remove("userId");
    }
}
