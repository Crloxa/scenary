package com.scenary.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** XFF 归因规则（docs/02 §1.4）：取最后一段，客户端伪造的段只落在前面。 */
class ClientIpTest {

    @Test
    void takesLastXffSegmentAppendedByOurNginx() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "6.6.6.6, 10.0.0.9");

        assertEquals("10.0.0.9", AuthController.clientIp(request));
    }

    @Test
    void fallsBackToXRealIpThenRemoteAddr() {
        MockHttpServletRequest withRealIp = new MockHttpServletRequest();
        withRealIp.addHeader("X-Real-IP", "192.168.1.8");
        assertEquals("192.168.1.8", AuthController.clientIp(withRealIp));

        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("127.0.0.1");
        assertEquals("127.0.0.1", AuthController.clientIp(direct));
    }
}
