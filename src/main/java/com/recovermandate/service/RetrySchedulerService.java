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

/**
 * Service to calculate and schedule algorithmic retry attempts based on failure category.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrySchedulerService {

    private final RetryScheduleRepository retryScheduleRepository;
    private final AuditService auditService;

    /**
     * Schedules retries for a failed payment event based on its classified failure category.
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

        String category = classification.getCategory() != null ? classification.getCategory() : "unknown";
        List<Duration> retryOffsets = getRetryOffsets(category);

        if (retryOffsets.isEmpty()) {
            log.info("No retries scheduled for category '{}' on event id={}", category, event.getId());
            return Collections.emptyList();
        }

        Instant now = Instant.now();
        List<RetrySchedule> schedules = new ArrayList<>();

        for (int i = 0; i < retryOffsets.size(); i++) {
            Duration offset = retryOffsets.get(i);
            int attemptNumber = i + 1;
            Instant scheduledAt = now.plus(offset);

            RetrySchedule schedule = RetrySchedule.builder()
                    .paymentEvent(event)
                    .failureCategory(category)
                    .attemptNumber(attemptNumber)
                    .scheduledAt(scheduledAt)
                    .result("PENDING")
                    .createdAt(now)
                    .build();

            schedules.add(retryScheduleRepository.save(schedule));
        }

        log.info("Scheduled {} retries for event id={} under category '{}'",
                schedules.size(), event.getId(), category);

        auditService.log(
                "PAYMENT_EVENT",
                event.getId() != null ? event.getId() : 0L,
                "RETRY_SCHEDULED",
                "SYSTEM",
                String.format("Scheduled %d retry attempt(s) for category '%s'", schedules.size(), category)
        );

        return schedules;
    }

    private List<Duration> getRetryOffsets(String category) {
        switch (category) {
            case FailureClassificationService.CATEGORY_INSUFFICIENT_FUNDS:
                // 3 retries: Day 1, Day 3, Day 7
                return List.of(Duration.ofDays(1), Duration.ofDays(3), Duration.ofDays(7));
            case FailureClassificationService.CATEGORY_TECHNICAL_DECLINE:
                // 3 retries: 5min, 30min, 2hr
                return List.of(Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2));
            case FailureClassificationService.CATEGORY_EXPIRED_MANDATE:
                // 0 retries: no point retrying expired mandates
                return Collections.emptyList();
            case FailureClassificationService.CATEGORY_UNKNOWN:
            default:
                // 2 retries: 1hr, 24hr
                return List.of(Duration.ofHours(1), Duration.ofHours(24));
        }
    }
}
