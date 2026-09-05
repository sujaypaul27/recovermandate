package com.recovermandate.service;

import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.RetrySchedule;
import com.recovermandate.repository.RetryScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.recovermandate.heuristic.RecoveryWindowCalculator;
import com.recovermandate.heuristic.RecoveryWindowCalculator.SuggestedRetryWindow;

/**
 * Service to calculate and schedule deterministic retry attempts based on Indian banking rail heuristics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrySchedulerService {

    private final RetryScheduleRepository retryScheduleRepository;
    private final AuditService auditService;

    /**
     * Schedules smart retries for a failed payment event based on its classified failure category
     * and Indian banking rail heuristics (CBS maintenance, salary credit, UPI peak avoidance).
     *
     * @param event          the failed PaymentEvent
     * @param classification the deterministic FailureClassification
     * @return the list of created RetrySchedule records
     */
    @Transactional
    public List<RetrySchedule> scheduleRetries(PaymentEvent event, FailureClassification classification) {
        if (event == null || classification == null) {
            log.warn("Cannot schedule retries: event or classification is null");
            return Collections.emptyList();
        }

        if (event.getId() != null) {
            List<RetrySchedule> existing = retryScheduleRepository.findByPaymentEventIdOrderByAttemptNumberAsc(event.getId());
            if (existing != null && !existing.isEmpty()) {
                log.info("Retries already scheduled for event id={} (found {} existing schedules). Skipping duplicate scheduling.",
                        event.getId(), existing.size());
                return existing;
            }
        }

        String category = classification.getCategory() != null ? classification.getCategory() : "unknown";
        int totalAttempts = getAttemptCount(category);

        if (totalAttempts == 0) {
            log.info("No retries scheduled for category '{}' on event id={}", category, event.getId());
            return Collections.emptyList();
        }

        Instant now = Instant.now();
        Instant failureTime = event.getReceivedAt() != null ? event.getReceivedAt() : now;
        List<RetrySchedule> schedules = new ArrayList<>();

        for (int i = 0; i < totalAttempts; i++) {
            int attemptNumber = i + 1;
            SuggestedRetryWindow window = RecoveryWindowCalculator.calculateOptimalWindow(failureTime, category, attemptNumber);

            RetrySchedule schedule = RetrySchedule.builder()
                    .paymentEvent(event)
                    .failureCategory(category)
                    .attemptNumber(attemptNumber)
                    .scheduledAt(window.scheduledAt())
                    .scheduleReason(window.reason())
                    .result("PENDING")
                    .createdAt(now)
                    .isDemoData(event.isDemoData())
                    .build();

            schedules.add(retryScheduleRepository.save(schedule));
        }

        log.info("Scheduled {} retries for event id={} under category '{}'",
                schedules.size(), event.getId(), category);

        String strategyReason = !schedules.isEmpty() && schedules.get(0).getScheduleReason() != null
                ? schedules.get(0).getScheduleReason()
                : "Scheduled via Indian banking rail settlement heuristic";

        auditService.log(
                "PAYMENT_EVENT",
                event.getId() != null ? event.getId() : 0L,
                "RETRY_SCHEDULED",
                "SYSTEM",
                String.format("Scheduled %d retry attempt(s) for category '%s'. Strategy: %s",
                        schedules.size(), category, strategyReason)
        );

        return schedules;
    }

    private int getAttemptCount(String category) {
        switch (category) {
            case FailureClassificationService.CATEGORY_INSUFFICIENT_FUNDS:
                return 3;
            case FailureClassificationService.CATEGORY_TECHNICAL_DECLINE:
                return 3;
            case FailureClassificationService.CATEGORY_EXPIRED_MANDATE:
                return 0; // 0 retries: no point retrying expired mandates
            case FailureClassificationService.CATEGORY_UNKNOWN:
            default:
                return 2;
        }
    }
}
