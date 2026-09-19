package com.scenary.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        service = new RateLimitService(redis);
        service.setRegisterPerHour(2);
        service.setSocialPerMin(3);
        service.setCommentPerMin(1);
    }

    @Test
    void registerBlocksAfterThresholdAndReportsRemainingSeconds() {
        stubCount("rl:register:10.1.1.1", 1L, 2L, 3L);
        stubTtl("rl:register:10.1.1.1", 777L);

        assertDoesNotThrow(() -> service.register("10.1.1.1"));
        assertDoesNotThrow(() -> service.register("10.1.1.1"));
        BizException ex = assertThrows(BizException.class, () -> service.register("10.1.1.1"));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("777"));
    }

    @Test
    void registerExemptsLoopbackClientsByDefault() {
        // 本地开发与自动化测试共享回环地址，默认豁免（docs/02 §1.4）
        assertDoesNotThrow(() -> service.register("127.0.0.1"));
        assertDoesNotThrow(() -> service.register("::1"));
        assertDoesNotThrow(() -> service.register("0:0:0:0:0:0:0:1"));
    }

    @Test
    void registerExemptionCanBeDisabledForProductionHardening() {
        service.setExemptLoopback(false);
        stubCount("rl:register:127.0.0.1", 3L);
        stubTtl("rl:register:127.0.0.1", -1L);

        BizException ex = assertThrows(BizException.class, () -> service.register("127.0.0.1"));

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("3600"));
    }

    @Test
    void socialAndCommentLimitsAreUserScoped() {
        stubCount("rl:social:7", 4L);
        stubTtl("rl:social:7", 12L);
        BizException social = assertThrows(BizException.class, () -> service.social(7L));
        assertTrue(social.getMessage().contains("12"));

        stubCount("rl:comment:7", 2L);
        stubTtl("rl:comment:7", -1L);
        BizException comment = assertThrows(BizException.class, () -> service.comments(7L));
        assertTrue(comment.getMessage().contains("60"));
    }

    @Test
    void nullRedisCountTreatedAsPassThrough() {
        // Redis 异常降级放行，不阻塞主流程（与评论限流既有语义一致）
        assertDoesNotThrow(() -> service.social(9L));
    }

    private void stubCount(String key, Long... counts) {
        lenient().when(valueOps.increment(key)).thenReturn(counts[0],
                java.util.Arrays.copyOfRange(counts, 1, counts.length));
    }

    private void stubTtl(String key, Long ttl) {
        lenient().when(redis.getExpire(key)).thenReturn(ttl);
    }
}
