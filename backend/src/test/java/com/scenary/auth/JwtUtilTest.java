package com.scenary.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.scenary.common.BizException;
import com.scenary.common.ErrorCode;
import com.scenary.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

    private JwtUtil jwt;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("01234567890123456789012345678901");
        properties.setIssuer("scenary-test");
        jwt = new JwtUtil(properties);
    }

    @Test
    void signAndParseExposeContractClaims() {
        Claims claims = jwt.parse(jwt.sign(42L, JwtUtil.TYPE_ACCESS, 300));

        assertEquals("42", claims.getSubject());
        assertEquals(JwtUtil.TYPE_ACCESS, claims.get("type", String.class));
        assertEquals("scenary-test", claims.getIssuer());
    }

    @Test
    void stripBearerRejectsMissingOrWrongScheme() {
        assertEquals("abc", JwtUtil.stripBearer("Bearer abc"));
        BizException exception = assertThrows(BizException.class, () -> JwtUtil.stripBearer("Basic abc"));
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
    }

    @Test
    void peekUserIdOnlyAcceptsAccessToken() {
        String refresh = jwt.sign(42L, JwtUtil.TYPE_REFRESH, 300);
        assertNull(jwt.peekUserId("Bearer " + refresh));
        assertNull(jwt.peekUserId("Bearer malformed"));
        assertEquals(42L, jwt.peekUserId("Bearer " + jwt.sign(42L, JwtUtil.TYPE_ACCESS, 300)));
    }
}
