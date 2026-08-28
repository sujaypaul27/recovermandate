package com.recovermandate.scheduler;

import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.RetrySchedule;
import com.recovermandate.repository.RetryScheduleRepository;
import com.recovermandate.service.BankHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Scheduled job to execute due retry attempts while respecting bank health states.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryExecutionScheduler {

    private final RetryScheduleRepository retryScheduleRepository;
    private final BankHealthService bankHealthService;
    private final AuditService auditService;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void executeDueRetries() {
        Instant now = Instant.now();
        List<RetrySchedule> dueRetries = retryScheduleRepository
                .findByResultAndScheduledAtLessThanEqual("PENDING", now, PageRequest.of(0, 10));

        if (dueRetries.isEmpty()) {
            return;
        }

        log.info("Processing {} due retry attempts", dueRetries.size());

        for (RetrySchedule retry : dueRetries) {
            String bankCode = bankHealthService.extractBankCode(retry.getPaymentEvent());
            String health = bankHealthService.getBankHealth(bankCode);

            if ("DOWN".equalsIgnoreCase(health)) {
                log.warn("Skipping retry id={} (attempt #{}) because bank {} is DOWN",
                        retry.getId(), retry.getAttemptNumber(), bankCode);
                retry.setResult("SKIPPED");
                retry.setExecutedAt(Instant.now());
                retryScheduleRepository.save(retry);

                auditService.log(
                        "RETRY_SCHEDULE",
                        retry.getId(),
                        "RETRY_SKIPPED_BANK_DOWN",
                        "SYSTEM",
                        String.format("Skipped attempt #%d for event %d because bank %s is DOWN",
                                retry.getAttemptNumber(),
                                retry.getPaymentEvent() != null ? retry.getPaymentEvent().getId() : 0L,
                                bankCode)
                );
            } else {
                log.info("Executing retry id={} (attempt #{}) for bank {} (health: {})",
                        retry.getId(), retry.getAttemptNumber(), bankCode, health);

                String simulatedPaymentId = "pay_retry_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                retry.setResult("SUCCESS");
                retry.setExecutedAt(Instant.now());
                retry.setRazorpayRetryPaymentId(simulatedPaymentId);
                retryScheduleRepository.save(retry);

                auditService.log(
                        "RETRY_SCHEDULE",
                        retry.getId(),
                        "RETRY_EXECUTED_SUCCESS",
                        "SYSTEM",
                        String.format("Executed retry attempt #%d with simulated retry payment ID %s (bank: %s, health: %s)",
                                retry.getAttemptNumber(),
                                simulatedPaymentId,
                                bankCode,
                                health)
                );
            }
        }
    }
}
