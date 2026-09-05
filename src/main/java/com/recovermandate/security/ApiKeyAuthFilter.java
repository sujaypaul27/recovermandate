package com.recovermandate.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.recovermandate.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * HTTP filter enforcing API key authentication on protected endpoints.
 * <p>
 * Requires an {@code X-API-Key} header matching {@code recovermandate.security.api-key}
 * for all {@code /api/**} routes, with the following deliberate exceptions:
 * <ul>
 *   <li>{@code /api/webhooks/razorpay} — Authenticated independently via HMAC-SHA256 signature verification.</li>
 *   <li>{@code /api/checkout/**} — Public-facing customer payment recovery portals.</li>
 *   <li>{@code /api/stream/**} — Supports query parameter authentication ({@code ?apiKey=...}) for standard EventSource SSE clients.</li>
 *   <li>{@code OPTIONS} — CORS preflight handshakes.</li>
 * </ul>
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Value("${recovermandate.security.api-key:default-dev-key}")
    private String expectedApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip auth for webhook endpoint, public customer checkout endpoints, and CORS preflight requests
        if (path.startsWith("/api/webhooks/razorpay") || path.startsWith("/api/checkout/") || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Support API Key in query parameter for SSE streaming (EventSource API does not support custom headers)
        if (path.startsWith("/api/stream/")) {
            String queryApiKey = request.getParameter("apiKey");
            if (isApiKeyValid(queryApiKey)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // Only protect /api/** endpoints
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String actualApiKey = request.getHeader(API_KEY_HEADER);

        if (!isApiKeyValid(actualApiKey)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .timestamp(Instant.now())
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                    .message("Invalid or missing API Key")
                    .path(request.getRequestURI())
                    .build();
            
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isApiKeyValid(String providedApiKey) {
        if (providedApiKey == null || expectedApiKey == null || expectedApiKey.isBlank()) {
            return false;
        }
        byte[] expectedBytes = expectedApiKey.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = providedApiKey.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
