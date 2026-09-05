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

/**
 * Token-bucket rate limiting filter using Bucket4j to guard against denial-of-service,
 * webhook flooding, and credential stuffing.
 * <p>
 * Enforces per-client-IP bounded buckets for:
 * <ul>
 *   <li>{@code /api/webhooks/razorpay} — 100 requests per minute</li>
 *   <li>{@code /api/recovery-actions/**} approval mutations — 50 requests per minute</li>
 *   <li>{@code /api/checkout/**} customer resolution — 60 requests per minute</li>
 * </ul>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // In-memory map for webhook limits by IP
    private final Map<String, Bucket> webhookBuckets = new ConcurrentHashMap<>();
    // In-memory map for action limits by IP
    private final Map<String, Bucket> actionBuckets = new ConcurrentHashMap<>();
    // In-memory map for public checkout limits by IP
    private final Map<String, Bucket> checkoutBuckets = new ConcurrentHashMap<>();

    private static final int MAX_BUCKET_CACHE_SIZE = 5000;

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
            Bucket bucket = getBucketWithBoundedCache(webhookBuckets, clientIp, this::createWebhookBucket);
            if (!bucket.tryConsume(1)) {
                sendTooManyRequestsError(response, request);
                return;
            }
        } else if (path.startsWith("/api/recovery-actions/") && 
                (path.endsWith("/approve") || path.endsWith("/reject") || path.endsWith("/batch-approve") || path.endsWith("/approve-and-dispatch"))) {
            Bucket bucket = getBucketWithBoundedCache(actionBuckets, clientIp, this::createActionBucket);
            if (!bucket.tryConsume(1)) {
                sendTooManyRequestsError(response, request);
                return;
            }
        } else if (path.startsWith("/api/checkout/")) {
            Bucket bucket = getBucketWithBoundedCache(checkoutBuckets, clientIp, this::createCheckoutBucket);
            if (!bucket.tryConsume(1)) {
                sendTooManyRequestsError(response, request);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private Bucket getBucketWithBoundedCache(Map<String, Bucket> cache, String key, java.util.function.Supplier<Bucket> bucketSupplier) {
        if (cache.size() >= MAX_BUCKET_CACHE_SIZE) {
            cache.clear();
        }
        return cache.computeIfAbsent(key, k -> bucketSupplier.get());
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            String firstIp = xfHeader.split(",")[0].trim();
            if (!firstIp.isBlank()) {
                return firstIp;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "unknown";
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

    private Bucket createCheckoutBucket() {
        // 60 requests per minute
        Bandwidth limit = Bandwidth.builder().capacity(60).refillGreedy(60, Duration.ofMinutes(1)).build();
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
