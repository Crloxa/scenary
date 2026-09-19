package com.scenary.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.config.JwtProperties;
import com.scenary.user.UserMapper;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private StringRedisTemplate redis;

    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        valueOps = mock(ValueOperations.class);
        // 各测试路径触达的 Redis 方法不同，统一 lenient 避免严格桩误报
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.increment("rl:register:127.0.0.1")).thenReturn(1L);
        lenient().when(redis.expire("rl:register:127.0.0.1", 3600L, TimeUnit.SECONDS))
                .thenReturn(Boolean.TRUE);
    }

    private AuthService service() {
        // 注册限流桩走 127.0.0.1，需关闭回环豁免才能命中限流计数
        com.scenary.common.RateLimitService rateLimit = new com.scenary.common.RateLimitService(redis);
        rateLimit.setExemptLoopback(false);
        return new AuthService(userMapper, passwordEncoder, jwtUtil, jwtProperties, redis, rateLimit);
    }

    @Test
    void registerMapsDuplicateKeyToUsernameExists() {
        AuthService service = service();

        when(userMapper.findByUsername("alice")).thenReturn(null);
        when(passwordEncoder.encode("abc12345")).thenReturn("hash");
        doThrow(new DuplicateKeyException("dup")).when(userMapper).insert(any());

        BizException ex = assertThrows(BizException.class,
                () -> service.register(new RegisterRequest("alice", "abc12345", "昵称"), "127.0.0.1"));

        assertEquals(ErrorCode.USERNAME_EXISTS, ex.getErrorCode());
        verify(userMapper).findByUsername("alice");
        verify(userMapper).insert(any());
        verify(redis).opsForValue();
        verify(redis).expire("rl:register:127.0.0.1", 3600L, TimeUnit.SECONDS);
        verify(jwtUtil, never()).sign(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void loginRejectsDeactivatedAccountWithDisabledCode() {
        AuthService service = service();
        com.scenary.user.UserEntity deactivated = new com.scenary.user.UserEntity();
        deactivated.setId(7L);
        deactivated.setUsername("alice");
        deactivated.setPasswordHash("$2a$hash");
        deactivated.setStatus(2);
        when(userMapper.findByUsername("alice")).thenReturn(deactivated);
        when(passwordEncoder.matches("abc12345", "$2a$hash")).thenReturn(true);

        BizException ex = assertThrows(BizException.class,
                () -> service.login(new LoginRequest("alice", "abc12345"), "10.0.0.1"));

        // 注销态(2)并入 40301 同码同文案，防账号状态枚举（docs/02 §3.8）
        assertEquals(ErrorCode.ACCOUNT_DISABLED, ex.getErrorCode());
    }

    @Test
    void deactivateSessionsRevokesRefreshWhitelistAndMarksStatus() {
        AuthService service = service();
        java.util.Set<String> keys = java.util.Set.of("auth:refresh:7:aaa", "auth:refresh:7:bbb");
        when(redis.keys("auth:refresh:7:*")).thenReturn(keys);

        service.deactivateSessions(7L);

        verify(redis).delete(keys);
        verify(valueOps).set("auth:ustatus:7", "2", 60L, TimeUnit.SECONDS);
    }
}
