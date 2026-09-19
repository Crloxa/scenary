package com.scenary.user;

import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.scenary.auth.AuthService;
import com.scenary.auth.UserContext;
import com.scenary.common.PageResult;
import com.scenary.common.GridCardVO;
import com.scenary.common.Result;
import com.scenary.social.SocialService;

import jakarta.validation.Valid;

/**
 * 用户模块接口（docs/02 §3）。鉴权路径由拦截器覆盖 /users/me/**；/{userId} 类为公开读，
 * "登录则增强"的视角差异经 Authorization 可选解析实现。
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final com.scenary.auth.JwtUtil jwtUtil;
    private final SocialService socialService;
    private final AuthService authService;
    private final AccountDeactivationService deactivationService;

    public UserController(UserService userService, com.scenary.auth.JwtUtil jwtUtil,
                          SocialService socialService, AuthService authService,
                          AccountDeactivationService deactivationService) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.socialService = socialService;
        this.authService = authService;
        this.deactivationService = deactivationService;
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.me(UserContext.require()));
    }

    @PatchMapping("/me")
    public Result<UserVO> updateMe(@Valid @RequestBody ProfileUpdateRequest req) {
        return Result.ok(userService.updateProfile(UserContext.require(), req));
    }

    @PostMapping("/me/avatar")
    public Result<AvatarVO> avatar(
            @RequestPart(value = "file", required = false) MultipartFile file) {
        String url = userService.updateAvatar(UserContext.require(), file);
        return Result.ok(new AvatarVO(url));
    }

    public record AvatarVO(String avatarUrl) {
    }

    /** 注销账号（docs/02 §3.8）：数据库事务提交后再吊销令牌，避免 Redis 操作混入事务。 */
    @DeleteMapping("/me")
    public Result<Map<String, Object>> deactivateMe(@Valid @RequestBody DeactivateRequest req) {
        long userId = UserContext.require();
        deactivationService.deactivate(userId, req.password());
        authService.deactivateSessions(userId);
        return Result.ok(Map.of("deactivated", true));
    }

    @GetMapping("/{userId}")
    public Result<PublicUserVO> profile(
            @PathVariable long userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long viewer = jwtUtil.peekUserId(authorization);
        return Result.ok(userService.publicProfile(userId)
                .withSocial(socialService.userSocial(userId, viewer)));
    }

    @GetMapping("/{userId}/notes")
    public Result<PageResult<GridCardVO>> notes(
            @PathVariable long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long viewer = jwtUtil.peekUserId(authorization);
        return Result.ok(userService.gridNotes(userId, viewer, cursor, limit));
    }
}
