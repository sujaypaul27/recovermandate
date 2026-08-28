package com.recovermandate.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MdcFilterTest {

    private final MdcFilter filter = new MdcFilter();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Test
    @DisplayName("Should generate traceId and set in MDC and response header, then clear MDC in finally")
    void doFilterInternal_generatesTraceIdAndCleansUp() throws ServletException, IOException {
        when(request.getHeader("X-Trace-Id")).thenReturn(null);

        doAnswer(invocation -> {
            String mdcTraceId = MDC.get("traceId");
            assertNotNull(mdcTraceId);
            assertFalse(mdcTraceId.isBlank());
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Trace-Id"), anyString());
        verify(filterChain).doFilter(request, response);
        assertNull(MDC.get("traceId")); // Verified cleared in finally
    }

    @Test
    @DisplayName("Should preserve existing X-Trace-Id from incoming request")
    void doFilterInternal_preservesExistingTraceId() throws ServletException, IOException {
        String existingTraceId = "custom-trace-id-12345";
        when(request.getHeader("X-Trace-Id")).thenReturn(existingTraceId);

        doAnswer(invocation -> {
            assertEquals(existingTraceId, MDC.get("traceId"));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Trace-Id", existingTraceId);
        assertNull(MDC.get("traceId"));
    }
}
