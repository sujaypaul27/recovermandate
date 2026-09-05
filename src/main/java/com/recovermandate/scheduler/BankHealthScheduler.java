package com.recovermandate.scheduler;

import com.recovermandate.service.BankHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background scheduler that periodically aggregates recent payment events to compute
 * real-time bank health metrics (success rate, average latency, and degradation state).
 * <p>
 * Evaluates core Indian banking rails (HDFC, SBI, ICICI, AXIS, KOTAK) every 5 minutes
 * to dynamically steer smart retry scheduling away from bank outages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankHealthScheduler {

    private final BankHealthService bankHealthService;

    /**
     * Executes the periodic bank health snapshot computation every 5 minutes (300,000 ms).
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void computeSnapshots() {
        log.info("Starting periodic bank health computation");
        try {
            bankHealthService.computeHealthSnapshots();
        } catch (Exception e) {
            log.error("Failed to compute bank health snapshots: {}", e.getMessage(), e);
        }
    }
}
