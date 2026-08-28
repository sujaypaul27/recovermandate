package com.recovermandate.scheduler;

import com.recovermandate.service.BankHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job to periodically calculate bank health metrics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankHealthScheduler {

    private final BankHealthService bankHealthService;

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
