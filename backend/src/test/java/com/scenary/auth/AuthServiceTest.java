package com.scenary.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:register:127.0.0.1")).thenReturn(1L);
        when(redis.expire("rl:register:127.0.0.1", 3600L, TimeUnit.SECONDS)).thenReturn(Boolean.TRUE);
    }

    @Test
    void registerMapsDuplicateKeyToUsernameExists() {
        AuthService service = new AuthService(userMapper, passwordEncoder, jwtUtil, jwtProperties, redis);

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
}
