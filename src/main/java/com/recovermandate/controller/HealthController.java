package com.recovermandate.controller;

import com.recovermandate.dto.HealthResponseDto;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller to report basic and detailed system health status.
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final Optional<CircuitBreakerRegistry> circuitBreakerRegistry;
    private final Optional<DataSource> dataSource;

    public HealthController(
            @Autowired(required = false) CircuitBreakerRegistry circuitBreakerRegistry,
            @Autowired(required = false) DataSource dataSource) {
        this.circuitBreakerRegistry = Optional.ofNullable(circuitBreakerRegistry);
        this.dataSource = Optional.ofNullable(dataSource);
    }

    @GetMapping
    public ResponseEntity<HealthResponseDto> getHealth() {
        return ResponseEntity.ok(HealthResponseDto.builder()
                .status("UP")
                .service("recovermandate")
                .timestamp(Instant.now())
                .build());
    }

    @GetMapping("/detailed")
    public ResponseEntity<Map<String, Object>> getDetailedHealth() {
        Map<String, Object> response = new HashMap<>();
        String overallStatus = "UP";

        // 1. Check Gemini API Circuit Breaker Status
        String geminiStatus = "UP";
        String circuitState = "CLOSED";
        if (circuitBreakerRegistry.isPresent()) {
            try {
                Optional<CircuitBreaker> cb = circuitBreakerRegistry.get().find("geminiApi");
                if (cb.isPresent()) {
                    circuitState = cb.get().getState().name();
                    if ("OPEN".equalsIgnoreCase(circuitState) || "FORCED_OPEN".equalsIgnoreCase(circuitState)) {
                        geminiStatus = "DEGRADED";
                        overallStatus = "DEGRADED";
                    }
                }
            } catch (Exception e) {
                log.warn("Error reading CircuitBreaker state: {}", e.getMessage());
                geminiStatus = "UNKNOWN";
            }
        }

        response.put("geminiApi", Map.of(
                "status", geminiStatus,
                "circuitBreakerState", circuitState,
                "model", "gemini-3.5-flash-lite",
                "fallbackEngine", "HeuristicFallbackEngine"
        ));

        // 2. Check Database Connectivity
        String dbStatus = "UP";
        if (dataSource.isPresent()) {
            try (Connection conn = dataSource.get().getConnection()) {
                if (!conn.isValid(2)) {
                    dbStatus = "DOWN";
                    overallStatus = "DEGRADED";
                }
            } catch (Exception e) {
                log.error("Database health check failed: {}", e.getMessage());
                dbStatus = "DOWN";
                overallStatus = "DEGRADED";
            }
        }

        response.put("database", Map.of(
                "status", dbStatus
        ));

        response.put("status", overallStatus);
        response.put("service", "recovermandate");
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(response);
    }
}
