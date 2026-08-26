package com.recovermandate.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.recovermandate.dto.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // In-memory map for webhook limits by IP
    private final Map<String, Bucket> webhookBuckets = new ConcurrentHashMap<>();
    // In-memory map for action limits by IP
    private final Map<String, Bucket> actionBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String clientIp = getClientIp(request);

        if (path.startsWith("/api/webhooks/razorpay")) {
            Bucket bucket = webhookBuckets.computeIfAbsent(clientIp, k -> createWebhookBucket());
            if (!bucket.tryConsume(1)) {
                sendTooManyRequestsError(response, request);
                return;
            }
        } else if (path.startsWith("/api/recovery-actions/") && (path.endsWith("/approve") || path.endsWith("/reject"))) {
            Bucket bucket = actionBuckets.computeIfAbsent(clientIp, k -> createActionBucket());
            if (!bucket.tryConsume(1)) {
                sendTooManyRequestsError(response, request);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    private Bucket createWebhookBucket() {
        // 100 requests per minute
        Bandwidth limit = Bandwidth.builder().capacity(100).refillGreedy(100, Duration.ofMinutes(1)).build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createActionBucket() {
        // 50 requests per minute
        Bandwidth limit = Bandwidth.builder().capacity(50).refillGreedy(50, Duration.ofMinutes(1)).build();
        return Bucket.builder().addLimit(limit).build();
    }

    private void sendTooManyRequestsError(HttpServletResponse response, HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message("Too many requests, please try again later")
                .path(request.getRequestURI())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
