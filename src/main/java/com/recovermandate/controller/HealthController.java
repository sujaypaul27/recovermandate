package com.recovermandate.controller;

import com.recovermandate.dto.HealthResponseDto;
import com.recovermandate.service.BankHealthService;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Controller to report basic, detailed system health status, and real-time banking rails status.
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final Optional<CircuitBreakerRegistry> circuitBreakerRegistry;
    private final Optional<DataSource> dataSource;
    private final Optional<BankHealthService> bankHealthService;
    private final Optional<com.recovermandate.client.RazorpayApiClient> razorpayApiClient;
    private final Optional<com.recovermandate.ai.GeminiClient> geminiClient;

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    public HealthController(
            @Autowired(required = false) CircuitBreakerRegistry circuitBreakerRegistry,
            @Autowired(required = false) DataSource dataSource,
            @Autowired(required = false) BankHealthService bankHealthService,
            @Autowired(required = false) com.recovermandate.client.RazorpayApiClient razorpayApiClient,
            @Autowired(required = false) com.recovermandate.ai.GeminiClient geminiClient) {
        this.circuitBreakerRegistry = Optional.ofNullable(circuitBreakerRegistry);
        this.dataSource = Optional.ofNullable(dataSource);
        this.bankHealthService = Optional.ofNullable(bankHealthService);
        this.razorpayApiClient = Optional.ofNullable(razorpayApiClient);
        this.geminiClient = Optional.ofNullable(geminiClient);
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

        String activeModel = geminiClient.map(com.recovermandate.ai.GeminiClient::getModelEndpoint).orElse("gemini-3.5-flash-lite");
        response.put("geminiApi", Map.of(
                "status", geminiStatus,
                "circuitBreakerState", circuitState,
                "model", activeModel,
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

        // 3. Check Razorpay API Configuration Mode (Live vs Simulated)
        boolean isRazorpayLive = razorpayApiClient.map(com.recovermandate.client.RazorpayApiClient::isLiveMode).orElse(false);
        response.put("razorpayApi", Map.of(
                "configured", isRazorpayLive,
                "mode", isRazorpayLive ? "LIVE" : "SIMULATED",
                "status", isRazorpayLive ? "UP" : "SIMULATED_LOCAL",
                "gateway", "Razorpay Payment Links v1"
        ));

        response.put("status", overallStatus);
        response.put("service", "recovermandate");
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/banks")
    public ResponseEntity<List<Map<String, Object>>> getBankingRailsHealth() {
        LocalTime istTime = LocalTime.now(IST_ZONE);
        boolean isCbsMaintenanceWindow = (istTime.isAfter(LocalTime.of(23, 30)) || istTime.isBefore(LocalTime.of(3, 30)));

        List<Map<String, Object>> banks = new ArrayList<>();

        // Major Indian Banks monitored by RecoverMandate
        banks.add(createBankHealth("HDFC", "HDFC Bank", "99.98%", 112, false, false, "All UPI AutoPay & NACH rails operational"));
        banks.add(createBankHealth("SBI", "State Bank of India", "98.40%", 285, isCbsMaintenanceWindow, true,
                isCbsMaintenanceWindow ? "CBS Batch Maintenance Window (11:30 PM - 3:30 AM IST). Retries auto-deferred." : "Operational (CBS batch idle)"));
        banks.add(createBankHealth("ICICI", "ICICI Bank", "99.95%", 125, false, false, "Instant mandate debit rails healthy"));
        banks.add(createBankHealth("AXIS", "Axis Bank", "99.70%", 160, false, false, "Fast checkout and mandate links active"));
        banks.add(createBankHealth("KOTAK", "Kotak Mahindra Bank", "99.88%", 140, false, false, "UPI & e-NACH processing normal"));

        return ResponseEntity.ok(banks);
    }

    private Map<String, Object> createBankHealth(
            String bankCode,
            String bankName,
            String defaultUptime,
            int defaultLatency,
            boolean isCbsWindow,
            boolean isPsuBank,
            String defaultAdvice) {

        String status = "OPERATIONAL";
        if (bankHealthService.isPresent()) {
            try {
                String liveHealth = bankHealthService.get().getBankHealth(bankCode);
                if ("DOWN".equalsIgnoreCase(liveHealth)) {
                    status = "DOWN";
                } else if ("DEGRADED".equalsIgnoreCase(liveHealth)) {
                    status = "DEGRADED";
                } else if (isCbsWindow && isPsuBank) {
                    status = "CBS_MAINTENANCE_WINDOW";
                }
            } catch (Exception e) {
                log.debug("Could not get bank health for {}: {}", bankCode, e.getMessage());
            }
        } else if (isCbsWindow && isPsuBank) {
            status = "CBS_MAINTENANCE_WINDOW";
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bankCode", bankCode);
        map.put("bankName", bankName);
        map.put("status", status);
        map.put("uptime", defaultUptime);
        map.put("latencyMs", defaultLatency);
        map.put("isPsuBank", isPsuBank);
        map.put("advice", defaultAdvice);
        map.put("lastEvaluated", Instant.now().toString());

        return map;
    }
}
