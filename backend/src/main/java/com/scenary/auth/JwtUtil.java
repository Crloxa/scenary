package com.scenary.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 双令牌签发与解析。claim 约定：sub=userId、type=access|refresh、jti=令牌唯一标识。
 * access 无状态短命；refresh 必须在 Redis 白名单命中才可用（AuthService）。
 */
@Component
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final JwtProperties props;

    public JwtUtil(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String sign(long userId, String type, long ttlSeconds) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", type)
                .id(UUID.randomUUID().toString())
                .issuer(props.getIssuer())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /**
     * 验签+过期校验，失败统一抛 40101；需要区分"已过期"做幂等处理的调用方自己捕 ExpiredJwtException。
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "令牌已过期");
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
    }
}
