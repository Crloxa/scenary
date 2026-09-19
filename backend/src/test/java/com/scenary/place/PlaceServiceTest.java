package com.scenary.place;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.RateLimitService;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private PlaceProvider provider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void stubRedis() {
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
    }

    private PlaceService service(boolean enabled) {
        PlaceProperties properties = new PlaceProperties();
        properties.setProviderEnabled(enabled);
        return new PlaceService(redis, new RateLimitService(redis), properties, provider, objectMapper);
    }

    @Test
    void disabledReturnsEmptyCandidateWithoutProviderOrCache() {
        ReverseGeocodeVO vo = service(false).reverseGeocode(1L, 31.2304, 121.4737);
        assertNull(vo.placeName());
        assertEquals("none", vo.provider());
        assertFalse(vo.cached());
        verify(provider, never()).reverseGeocode(anyDouble(), anyDouble());
        verify(valueOperations, never()).get(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        PlaceService svc = service(true);
        BizException lat = assertThrows(BizException.class,
                () -> svc.reverseGeocode(1L, 90.1, 0.0));
        assertEquals(ErrorCode.VALIDATION, lat.getErrorCode());
        BizException lng = assertThrows(BizException.class,
                () -> svc.reverseGeocode(1L, 0.0, -180.000001));
        assertEquals(ErrorCode.VALIDATION, lng.getErrorCode());
        BizException missing = assertThrows(BizException.class,
                () -> svc.reverseGeocode(1L, null, 0.0));
        assertEquals(ErrorCode.VALIDATION, missing.getErrorCode());
    }

    @Test
    void cacheHitSkipsProvider() throws Exception {
        when(valueOperations.get("place:rg:" + Geohash.encode(31.2304, 121.4737, 5)))
                .thenReturn(objectMapper.writeValueAsString(new JsonCacheValue("人民广场", "nominatim")));
        ReverseGeocodeVO vo = service(true).reverseGeocode(1L, 31.2304, 121.4737);
        assertEquals("人民广场", vo.placeName());
        assertEquals("nominatim", vo.provider());
        assertTrue(vo.cached());
        verify(provider, never()).reverseGeocode(anyDouble(), anyDouble());
    }

    @Test
    void cacheMissQueriesProviderAndStoresForThirtyDays() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(provider.reverseGeocode(31.2304, 121.4737)).thenReturn(Optional.of("人民广场"));
        ReverseGeocodeVO vo = service(true).reverseGeocode(1L, 31.2304, 121.4737);
        assertEquals("人民广场", vo.placeName());
        assertFalse(vo.cached());
        verify(valueOperations).set(anyString(), anyString(),
                eq(TimeUnit.DAYS.toSeconds(30)), eq(TimeUnit.SECONDS));
    }

    @Test
    void providerFailureYieldsEmptyCandidateAndNoCacheWrite() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(provider.id()).thenReturn("nominatim");
        when(provider.reverseGeocode(anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("provider timeout simulation"));
        ReverseGeocodeVO vo = service(true).reverseGeocode(1L, 5.0, 5.0);
        assertNull(vo.placeName());
        assertEquals("nominatim", vo.provider());
        assertFalse(vo.cached());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void providerEmptyResultIsReturnedButNotCached() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(provider.id()).thenReturn("nominatim");
        when(provider.reverseGeocode(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        ReverseGeocodeVO vo = service(true).reverseGeocode(1L, 0.0, 0.0);
        assertNull(vo.placeName());
        assertEquals("nominatim", vo.provider());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void rateLimitExceededThrows429() {
        when(valueOperations.increment(anyString())).thenReturn(31L);
        BizException ex = assertThrows(BizException.class,
                () -> service(true).reverseGeocode(1L, 31.2304, 121.4737));
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getErrorCode());
        verify(provider, never()).reverseGeocode(anyDouble(), anyDouble());
    }

    /** 与 PlaceService 内部缓存 JSON 同形，用于构造缓存值 */
    private record JsonCacheValue(String placeName, String provider) {
    }
}
