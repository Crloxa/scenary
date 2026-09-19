package com.scenary.place;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.common.RateLimitService;

/**
 * 逆地理编码编排（docs/05 §6.5 E3-2）：限流（30/min/用户）→ 参数校验 →
 * geohash-5 缓存（TTL 30 天）→ provider SPI。任何 provider 侧失败都折叠为
 * 空候选返回 200，绝不向发布链路传播异常。
 */
@Service
public class PlaceService {

    private static final Logger log = LoggerFactory.getLogger(PlaceService.class);

    private static final String CACHE_KEY_PREFIX = "place:rg:";

    private final StringRedisTemplate redis;
    private final RateLimitService rateLimitService;
    private final PlaceProperties properties;
    private final PlaceProvider provider;
    private final ObjectMapper objectMapper;

    public PlaceService(StringRedisTemplate redis, RateLimitService rateLimitService,
                        PlaceProperties properties, PlaceProvider provider, ObjectMapper objectMapper) {
        this.redis = redis;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.provider = provider;
        this.objectMapper = objectMapper;
    }

    public ReverseGeocodeVO reverseGeocode(long userId, Double latitude, Double longitude) {
        rateLimitService.places(userId);
        if (latitude == null || longitude == null
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new BizException(ErrorCode.VALIDATION, "纬度范围 -90~90，经度范围 -180~180");
        }
        if (!properties.isProviderEnabled()) {
            return ReverseGeocodeVO.empty();
        }
        String cacheKey = CACHE_KEY_PREFIX + Geohash.encode(latitude, longitude, 5);
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            ReverseGeocodeVO fromCache = readCache(cached);
            if (fromCache != null) {
                return fromCache;
            }
        }
        String placeName;
        try {
            placeName = provider.reverseGeocode(latitude, longitude).orElse(null);
        } catch (Exception e) {
            // SPI 实现不可信时的兜底：provider 抛错同样按空候选降级，绝不 500 发布链路
            log.warn("reverse geocode provider threw, degrade to empty candidate: {}", e.getMessage());
            placeName = null;
        }
        if (placeName != null) {
            // 只缓存成功地名：超时/失败的空结果落缓存会把整个网格污染 30 天
            writeCache(cacheKey, placeName);
        }
        return new ReverseGeocodeVO(placeName, provider.id(), false);
    }

    /** 缓存内容损坏返回 null（按未命中继续走 provider） */
    private ReverseGeocodeVO readCache(String cached) {
        try {
            JsonCacheValue value = objectMapper.readValue(cached, JsonCacheValue.class);
            return new ReverseGeocodeVO(value.placeName(), value.provider(), true);
        } catch (Exception e) {
            log.warn("reverse geocode cache unreadable, treat as miss: {}", e.getMessage());
            return null;
        }
    }

    private void writeCache(String cacheKey, String placeName) {
        try {
            String value = objectMapper.writeValueAsString(new JsonCacheValue(placeName, provider.id()));
            redis.opsForValue().set(cacheKey, value,
                    TimeUnit.DAYS.toSeconds(properties.getCacheTtlDays()), TimeUnit.SECONDS);
        } catch (Exception e) {
            // 缓存写失败不影响本次响应（结果已可用），下次查询重试
            log.warn("reverse geocode cache write failed: {}", e.getMessage());
        }
    }

    /** 只序列化 placeName/provider，cached 恒由读取侧判定 */
    private record JsonCacheValue(String placeName, String provider) {
    }
}
