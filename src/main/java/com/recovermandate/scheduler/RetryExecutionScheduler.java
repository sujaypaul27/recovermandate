package com.recovermandate.scheduler;

import com.recovermandate.audit.AuditService;
import com.recovermandate.client.RazorpayApiClient;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.PaymentLink;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.entity.RetrySchedule;
import com.recovermandate.repository.PaymentLinkRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import com.recovermandate.repository.RetryScheduleRepository;
import com.recovermandate.service.BankHealthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Scheduled job to execute due retry attempts while respecting bank health states
 * and preventing double-charge race conditions with active payment links.
 */
@Slf4j
@Component
public class RetryExecutionScheduler {

    private final RetryScheduleRepository retryScheduleRepository;
    private final BankHealthService bankHealthService;
    private final AuditService auditService;
    private final PaymentLinkRepository paymentLinkRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final RazorpayApiClient razorpayApiClient;
    private final Random random;

    @org.springframework.beans.factory.annotation.Autowired
    public RetryExecutionScheduler(RetryScheduleRepository retryScheduleRepository,
                                  BankHealthService bankHealthService,
                                  AuditService auditService,
                                  PaymentLinkRepository paymentLinkRepository,
                                  RecoveryActionRepository recoveryActionRepository,
                                  RazorpayApiClient razorpayApiClient) {
        this(retryScheduleRepository, bankHealthService, auditService, paymentLinkRepository, recoveryActionRepository, razorpayApiClient, new Random());
    }

    public RetryExecutionScheduler(RetryScheduleRepository retryScheduleRepository,
                                  BankHealthService bankHealthService,
                                  AuditService auditService,
                                  PaymentLinkRepository paymentLinkRepository,
                                  RecoveryActionRepository recoveryActionRepository,
                                  RazorpayApiClient razorpayApiClient,
                                  Random random) {
        this.retryScheduleRepository = retryScheduleRepository;
        this.bankHealthService = bankHealthService;
        this.auditService = auditService;
        this.paymentLinkRepository = paymentLinkRepository;
        this.recoveryActionRepository = recoveryActionRepository;
        this.razorpayApiClient = razorpayApiClient;
        this.random = random != null ? random : new Random();
    }

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
            executeSingleRetry(retry, false);
        }
    }

    /**
     * Executes an individual retry attempt with full domain safety guards (inactive subscription check,
     * double-charge prevention, bank health status, simulated gateway response, and closed-loop link revocation).
     *
     * @param retry the retry schedule entity to execute
     * @param manualOverride true if invoked manually by a human support operator
     * @return the updated retry schedule entity
     */
    @Transactional
    public RetrySchedule executeSingleRetry(RetrySchedule retry, boolean manualOverride) {
        PaymentEvent event = retry.getPaymentEvent();
        String actor = manualOverride ? "HUMAN" : "SYSTEM";

        // 1. Subscription Inactive Guard
        if (event != null && event.getSubscription() != null) {
            String subStatus = event.getSubscription().getStatus();
            if ("cancelled".equalsIgnoreCase(subStatus) || "halted".equalsIgnoreCase(subStatus) || "paused".equalsIgnoreCase(subStatus)) {
                log.info("Skipping retry id={} because subscription is {}", retry.getId(), subStatus);
                retry.setResult("SKIPPED");
                retry.setExecutedAt(Instant.now());
                retry.setScheduleReason("SUBSCRIPTION_" + subStatus.toUpperCase());
                RetrySchedule saved = retryScheduleRepository.save(retry);

                auditService.log(
                        "RETRY_SCHEDULE",
                        retry.getId(),
                        "RETRY_SKIPPED_SUBSCRIPTION_INACTIVE",
                        actor,
                        String.format("Skipped retry attempt #%d because subscription is %s",
                                retry.getAttemptNumber(), subStatus)
                );
                return saved;
            }
        }

        // 2. Double-Charge Prevention Guard: Check if payment link was already PAID / RECOVERED
        if (event != null && recoveryActionRepository != null) {
            Optional<RecoveryAction> actionOpt = recoveryActionRepository.findByFailureClassificationPaymentEvent(event);
            if (actionOpt.isPresent()) {
                RecoveryAction action = actionOpt.get();
                if ("RECOVERED".equalsIgnoreCase(action.getStatus()) || "PAID".equalsIgnoreCase(action.getStatus())) {
                    log.info("Skipping retry id={} because mandate is already recovered via payment link", retry.getId());
                    retry.setResult("SKIPPED");
                    retry.setExecutedAt(Instant.now());
                    retry.setScheduleReason("SUPERSEDED_BY_LINK_PAYMENT");
                    RetrySchedule saved = retryScheduleRepository.save(retry);

                    auditService.log(
                            "RETRY_SCHEDULE",
                            retry.getId(),
                            "RETRY_CANCELLED_ALREADY_PAID",
                            actor,
                            "Retry #" + retry.getAttemptNumber() + " cancelled because payment was already recovered via payment link"
                    );
                    return saved;
                }
            }
        }

        // 3. Bank Health Guard (if automated, defer if DOWN; if manual override, bypass)
        String bankCode = bankHealthService.extractBankCode(event);
        String health = bankHealthService.getBankHealth(bankCode);

        if (!manualOverride && "DOWN".equalsIgnoreCase(health)) {
            log.warn("Bank {} is DOWN. Deferring retry id={} (attempt #{}) by 60 minutes",
                    bankCode, retry.getId(), retry.getAttemptNumber());
            retry.setScheduledAt(Instant.now().plus(60, java.time.temporal.ChronoUnit.MINUTES));
            retry.setScheduleReason("RETRY_DEFERRED_BANK_" + bankCode + "_DOWN");
            RetrySchedule saved = retryScheduleRepository.save(retry);

            auditService.log(
                    "RETRY_SCHEDULE",
                    retry.getId(),
                    "RETRY_DEFERRED_BANK_OUTAGE",
                    "SYSTEM",
                    String.format("Deferred retry attempt #%d for event %d by 60 minutes because bank %s is DOWN. Attempt counter preserved.",
                            retry.getAttemptNumber(),
                            event != null ? event.getId() : 0L,
                            bankCode)
            );
            return saved;
        }

        // Determine simulated failure rate based on bank health
        double failureRate;
        if ("DEGRADED".equalsIgnoreCase(health)) {
            failureRate = 0.65; // 65% failure rate for degraded banks
        } else if ("HEALTHY".equalsIgnoreCase(health)) {
            failureRate = 0.30; // ~30% standard failure rate
        } else {
            failureRate = 0.50; // default 50% for unknown state
        }

        boolean isFailure = random.nextDouble() < failureRate;

        if (isFailure) {
            log.warn("Simulated retry failure for id={} (attempt #{}) for bank {} (health: {})",
                    retry.getId(), retry.getAttemptNumber(), bankCode, health);
            retry.setResult("FAILED");
            retry.setExecutedAt(Instant.now());
            RetrySchedule saved = retryScheduleRepository.save(retry);

            auditService.log(
                    "RETRY_SCHEDULE",
                    retry.getId(),
                    "RETRY_EXECUTED_FAILED",
                    actor,
                    String.format("Executed retry attempt #%d with FAILED outcome against bank %s (health: %s)",
                            retry.getAttemptNumber(),
                            bankCode,
                            health)
            );
            return saved;
        } else {
            log.info("Executing retry id={} (attempt #{}) successfully for bank {} (health: {})",
                    retry.getId(), retry.getAttemptNumber(), bankCode, health);

            String simulatedPaymentId = "pay_retry_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            retry.setResult("SUCCESS");
            retry.setExecutedAt(Instant.now());
            retry.setRazorpayRetryPaymentId(simulatedPaymentId);
            RetrySchedule saved = retryScheduleRepository.save(retry);

            auditService.log(
                    "RETRY_SCHEDULE",
                    retry.getId(),
                    "RETRY_EXECUTED_SUCCESS",
                    actor,
                    String.format("Executed retry attempt #%d with simulated retry payment ID %s (bank: %s, health: %s)",
                            retry.getAttemptNumber(),
                            simulatedPaymentId,
                            bankCode,
                            health)
            );

            // 4. Closed-Loop Cancellation: Supersede any active Razorpay Payment Link for this mandate
            if (event != null && recoveryActionRepository != null && paymentLinkRepository != null) {
                Optional<RecoveryAction> actionOpt = recoveryActionRepository.findByFailureClassificationPaymentEvent(event);
                if (actionOpt.isPresent()) {
                    RecoveryAction action = actionOpt.get();
                    Optional<PaymentLink> linkOpt = paymentLinkRepository.findByRecoveryAction(action);
                    if (linkOpt.isPresent()) {
                        PaymentLink link = linkOpt.get();
                        if ("CREATED".equalsIgnoreCase(link.getStatus()) || "DISPATCHED".equalsIgnoreCase(link.getStatus())) {
                            link.setStatus("SUPERSEDED");
                            paymentLinkRepository.save(link);
                            if (razorpayApiClient != null) {
                                razorpayApiClient.cancelPaymentLink(link.getRazorpayLinkId());
                            }

                            auditService.log(
                                    "PAYMENT_LINK",
                                    link.getId(),
                                    "PAYMENT_LINK_SUPERSEDED_BY_RETRY",
                                    actor,
                                    "Payment link " + link.getRazorpayLinkId() + " marked SUPERSEDED after automated retry #" + retry.getAttemptNumber() + " succeeded"
                            );
                        }
                    }
                }
            }
            return saved;
        }
    }
}
