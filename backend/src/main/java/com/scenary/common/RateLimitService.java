package com.scenary.common;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 写接口限流基线（docs/02 §1.4，P15-01）：Redis INCR+EXPIRE 滑动窗口，超出抛 42001。
 * 键命名空间沿用既有约定 rl:{scene}:{主体}；注册按客户端 IP，其余按用户。
 * 回环客户端豁免仅作用于注册限流：本地开发与自动化测试共享 127.0.0.1，
 * 生产流量经反代携带真实客户端 IP，不受豁免影响。
 */
@Service
public class RateLimitService {

    private final StringRedisTemplate redis;

    int registerPerHour = 20;
    int notePer10min = 30;
    int socialPerMin = 120;
    int mediaPer10min = 60;
    int commentPerMin = 20;
    boolean exemptLoopback = true;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Value("${scenary.ratelimit.register-per-hour:20}")
    public void setRegisterPerHour(int v) { this.registerPerHour = v; }

    @Value("${scenary.ratelimit.note-per-10min:30}")
    public void setNotePer10min(int v) { this.notePer10min = v; }

    @Value("${scenary.ratelimit.social-per-min:120}")
    public void setSocialPerMin(int v) { this.socialPerMin = v; }

    @Value("${scenary.ratelimit.media-per-10min:60}")
    public void setMediaPer10min(int v) { this.mediaPer10min = v; }

    @Value("${scenary.ratelimit.comment-per-min:20}")
    public void setCommentPerMin(int v) { this.commentPerMin = v; }

    @Value("${scenary.ratelimit.exempt-loopback:true}")
    public void setExemptLoopback(boolean v) { this.exemptLoopback = v; }

    public void register(String ip) {
        if (exemptLoopback && isLoopback(ip)) {
            return;
        }
        check("rl:register:" + ip, TimeUnit.HOURS.toSeconds(1), registerPerHour,
                "注册过于频繁");
    }

    public void notes(long userId) {
        check("rl:note:" + userId, TimeUnit.MINUTES.toSeconds(10), notePer10min,
                "发布过于频繁");
    }

    /** 点赞/收藏/关注写操作共用一个用户级窗口（docs/02 §1.4）。 */
    public void social(long userId) {
        check("rl:social:" + userId, TimeUnit.MINUTES.toSeconds(1), socialPerMin,
                "操作过于频繁");
    }

    public void media(long userId) {
        check("rl:media:" + userId, TimeUnit.MINUTES.toSeconds(10), mediaPer10min,
                "上传过于频繁");
    }

    public void comments(long userId) {
        check("rl:comment:" + userId, TimeUnit.MINUTES.toSeconds(1), commentPerMin,
                "评论过于频繁");
    }

    void check(String key, long windowSeconds, int limit, String action) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        if (count != null && count > limit) {
            Long ttl = redis.getExpire(key);
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS,
                    action + "，请 " + (ttl == null || ttl < 0 ? windowSeconds : ttl) + " 秒后再试");
        }
    }

    static boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip) || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip);
    }
}
