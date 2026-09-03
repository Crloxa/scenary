package com.scenary.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import jakarta.servlet.FilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    @Test
    void generatesTraceIdAndClearsMdcAfterRequest() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenInsideChain.set(MDC.get(TraceIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        String traceId = response.getHeader(TraceIdFilter.HEADER);
        assertNotNull(traceId);
        assertEquals(traceId, seenInsideChain.get());
        assertNull(MDC.get(TraceIdFilter.MDC_KEY));
    }

    @Test
    void acceptsSafeIncomingTraceIdAndRejectsUnsafeValue() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ping");
        request.addHeader(TraceIdFilter.HEADER, " client-trace-1 ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { });
        assertEquals("client-trace-1", response.getHeader(TraceIdFilter.HEADER));

        MockHttpServletRequest unsafe = new MockHttpServletRequest("GET", "/api/v1/ping");
        unsafe.addHeader(TraceIdFilter.HEADER, "bad\\ntrace");
        MockHttpServletResponse generated = new MockHttpServletResponse();
        filter.doFilter(unsafe, generated, (req, res) -> { });
        assertNotNull(generated.getHeader(TraceIdFilter.HEADER));
        assertNull(MDC.get(TraceIdFilter.MDC_KEY));
    }
}
