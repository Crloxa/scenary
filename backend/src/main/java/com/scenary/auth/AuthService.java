package com.scenary.auth;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.config.JwtProperties;
import com.scenary.user.UserEntity;
import com.scenary.user.UserMapper;

import io.jsonwebtoken.Claims;

/**
 * 认证域服务。Redis 键清单（docs/01 §6）：
 * auth:refresh:{userId}:{jti}          刷新令牌白名单（旋转=删旧签新）
 * auth:access:bl:{jti}                 登出后 access jti 黑名单，TTL=剩余寿命
 * rl:register:{ip}                     注册限流 20 次/IP/小时
 * rl:loginfail:{username}:{ip}         登录连错计数；满 5 次 -> rl:loginlock 同键名空间锁 15 分钟
 */
@Service
public class AuthService {

    private static final String KEY_REFRESH = "auth:refresh:";
    private static final String KEY_ACCESS_BL = "auth:access:bl:";
    private static final String KEY_REG_RL = "rl:register:";
    private static final String KEY_LOGIN_FAIL = "rl:loginfail:";
    private static final String KEY_LOGIN_LOCK = "rl:loginlock:";

    private static final int REGISTER_LIMIT_PER_HOUR = 20;
    private static final int LOGIN_MAX_FAILS = 5;
    private static final long LOGIN_LOCK_SECONDS = TimeUnit.MINUTES.toSeconds(15);

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProps;
    private final StringRedisTemplate redis;

    public AuthService(UserMapper userMapper, BCryptPasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, JwtProperties jwtProps, StringRedisTemplate redis) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.jwtProps = jwtProps;
        this.redis = redis;
    }

    public AuthVO register(RegisterRequest req, String ip) {
        enforceRegisterRateLimit(ip);
        if (userMapper.findByUsername(req.username()) != null) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        checkPasswordShape(req.password());

        UserEntity user = new UserEntity();
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname((req.nickname() == null || req.nickname().isBlank())
                ? req.username() : req.nickname());
        user.setBio("");
        user.setStatus(1);
        userMapper.insert(user);
        return issuePair(user);
    }

    public AuthVO login(LoginRequest req, String ip) {
        String failKey = KEY_LOGIN_FAIL + req.username() + ":" + ip;
        String lockKey = KEY_LOGIN_LOCK + req.username() + ":" + ip;

        Long remain = redis.getExpire(lockKey);
        if (remain != null && remain > 0) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS,
                    "登录失败次数过多，请 " + remain + " 秒后再试");
        }

        UserEntity user = userMapper.findByUsername(req.username());
        // 统一文案防账号枚举；契约未定义"密码错误"独立码，归入 40000（已记 CHANGELOG）
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            long fails = requireIncrease(failKey, LOGIN_LOCK_SECONDS);
            if (fails >= LOGIN_MAX_FAILS) {
                redis.opsForValue().set(lockKey, "1", LOGIN_LOCK_SECONDS, TimeUnit.SECONDS);
                redis.delete(failKey);
                throw new BizException(ErrorCode.TOO_MANY_REQUESTS,
                        "登录失败次数过多，请 " + LOGIN_LOCK_SECONDS + " 秒后再试");
            }
            throw new BizException(ErrorCode.VALIDATION, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        redis.delete(failKey);
        return issuePair(user);
    }

    public AuthVO refresh(RefreshRequest req) {
        Claims claims = jwtUtil.parse(req.refreshToken());
        if (!JwtUtil.TYPE_REFRESH.equals(claims.get("type", String.class))) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        long userId = Long.parseLong(claims.getSubject());
        String oldKey = KEY_REFRESH + userId + ":" + claims.getId();
        if (Boolean.FALSE.equals(redis.hasKey(oldKey))) {
            // 已旋转或已登出：白名单未命中即拒绝（重放防线）
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        redis.delete(oldKey);

        UserEntity user = userMapper.findById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        return issuePair(user);
    }

    public void logout(String authorizationHeader) {
        String token = stripBearer(authorizationHeader);
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (BizException e) {
            throw e;
        }
        if (!JwtUtil.TYPE_ACCESS.equals(claims.get("type", String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        long userId = Long.parseLong(claims.getSubject());

        // 双保险之一：吊销该用户全部刷新白名单（KEYS 在本数据量级可接受，二期可换 SCAN 游标）
        Set<String> refreshKeys = redis.keys(KEY_REFRESH + userId + ":*");
        if (refreshKeys != null && !refreshKeys.isEmpty()) {
            redis.delete(refreshKeys);
        }
        // 双保险之二：当前 access jti 拉黑至自然过期
        long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remainingMs > 0) {
            redis.opsForValue().set(KEY_ACCESS_BL + claims.getId(), "1",
                    remainingMs, TimeUnit.MILLISECONDS);
        }
    }

    /** 3.5 AuthInterceptor 将复用：黑名单命中的 access 一律拒绝 */
    public boolean isAccessBlacklisted(Claims accessClaims) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_ACCESS_BL + accessClaims.getId()));
    }

    public boolean isRefreshWhitelisted(long userId, String jti) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_REFRESH + userId + ":" + jti));
    }

    private void enforceRegisterRateLimit(String ip) {
        long count = requireIncrease(KEY_REG_RL + ip, TimeUnit.HOURS.toSeconds(1));
        if (count > REGISTER_LIMIT_PER_HOUR) {
            Long ttl = redis.getExpire(KEY_REG_RL + ip);
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS,
                    "注册过于频繁，请 " + (ttl == null ? 3600 : ttl) + " 秒后再试");
        }
    }

    private long requireIncrease(String key, long windowSeconds) {
        Long v = redis.opsForValue().increment(key);
        if (v != null && v == 1L) {
            redis.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return v == null ? 0 : v;
    }

    private void checkPasswordShape(String password) {
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BizException(ErrorCode.VALIDATION, "密码需至少同时包含字母和数字");
        }
    }

    private AuthVO issuePair(UserEntity user) {
        String access = jwtUtil.sign(user.getId(), JwtUtil.TYPE_ACCESS, jwtProps.getAccessTtl());
        String refresh = jwtUtil.sign(user.getId(), JwtUtil.TYPE_REFRESH, jwtProps.getRefreshTtl());
        Claims rc = jwtUtil.parse(refresh);
        redis.opsForValue().set(KEY_REFRESH + user.getId() + ":" + rc.getId(), "1",
                jwtProps.getRefreshTtl(), TimeUnit.SECONDS);
        return new AuthVO(user.getId(), user.getUsername(), user.getNickname(),
                user.getAvatarUrl(), access, jwtProps.getAccessTtl(),
                refresh, jwtProps.getRefreshTtl());
    }

    static String stripBearer(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return header.substring(7);
    }
}
