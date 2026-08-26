package com.recovermandate.service;

import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.repository.FailureClassificationRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic classifier for payment failure reasons.
 * Pure Java logic — zero AI involvement. This is money-decision code.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Never throws for bad/missing error_code — "unknown" is the safe terminal state.</li>
 *   <li>Idempotent: duplicate calls for the same PaymentEvent are no-ops.</li>
 *   <li>Every new classification produces exactly one AuditLog entry.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FailureClassificationService {

    public static final String EVENT_PAYMENT_FAILED = "payment.failed";
    public static final String CATEGORY_INSUFFICIENT_FUNDS = "insufficient_funds";
    public static final String CATEGORY_TECHNICAL_DECLINE = "technical_decline";
    public static final String CATEGORY_EXPIRED_MANDATE = "expired_mandate";
    public static final String CATEGORY_UNKNOWN = "unknown";

    private final FailureClassificationRepository failureClassificationRepository;
    private final AuditService auditService;

    /**
     * Classifies a payment failure event, saves the classification, and records an audit log.
     * Only runs when event.eventType == "payment.failed".
     *
     * <p>Idempotency: if a FailureClassification already exists for this PaymentEvent
     * (e.g. duplicate webhook delivery retry), returns the existing one without
     * creating a second row or a second AuditLog entry.
     *
     * @param event the payment event to classify
     * @return the saved or existing FailureClassification entity, or null if event is not a payment failure
     */
    @Transactional
    public FailureClassification classify(PaymentEvent event) {
        if (event == null || !EVENT_PAYMENT_FAILED.equals(event.getEventType())) {
            log.warn("Skipping failure classification: event is null or not {}", EVENT_PAYMENT_FAILED);
            return null;
        }

        // Idempotency guard: do not duplicate classification or audit log
        Optional<FailureClassification> existing = failureClassificationRepository.findByPaymentEvent(event);
        if (existing.isPresent()) {
            log.info("Classification already exists for PaymentEvent id={}, skipping duplicate",
                    event.getId());
            return existing.get();
        }

        String rawErrorCode = event.getFailureReasonCode();
        String category = determineCategory(rawErrorCode);
        boolean autoRecoverable = CATEGORY_TECHNICAL_DECLINE.equals(category);
        String matchedRule = describeMatchedRule(rawErrorCode, category);

        FailureClassification classification = FailureClassification.builder()
                .paymentEvent(event)
                .category(category)
                .autoRecoverable(autoRecoverable)
                .rawErrorCode(rawErrorCode)
                .decidedAt(Instant.now())
                .build();

        FailureClassification savedClassification = failureClassificationRepository.save(classification);
        log.info("Payment failure classified: eventId={}, category={}, autoRecoverable={}",
                event.getId(), category, autoRecoverable);

        // Structured audit reasoning — independently readable by a reviewer
        String reasoning = String.format(
                "raw_error_code=%s | category=%s | auto_recoverable=%s | rule=%s",
                rawErrorCode != null ? rawErrorCode : "(null)",
                category,
                autoRecoverable,
                matchedRule
        );

        auditService.log(
                "PAYMENT_EVENT",
                event.getId() != null ? event.getId() : 0L,
                "FAILURE_CLASSIFIED",
                "SYSTEM",
                reasoning
        );

        return savedClassification;
    }

    /**
     * Determines the failure category from the raw error code.
     * Uses exhaustive matching: exact match first, then substring, then unknown.
     *
     * @param failureReasonCode the raw error_code from the Razorpay payload (may be null/blank)
     * @return one of the four category constants
     */
    String determineCategory(String failureReasonCode) {
        if (failureReasonCode == null || failureReasonCode.isBlank()) {
            return CATEGORY_UNKNOWN;
        }

        String upper = failureReasonCode.trim().toUpperCase(Locale.ROOT);

        // Exact-match rules (switch-style)
        switch (upper) {
            case "BAD_REQUEST_ERROR":
                return CATEGORY_INSUFFICIENT_FUNDS;
            case "GATEWAY_ERROR":
            case "SERVER_ERROR":
                return CATEGORY_TECHNICAL_DECLINE;
            default:
                break;
        }

        // Substring-match rules (case-insensitive via the uppercased value)
        if (upper.contains("EXPIRED") || upper.contains("MANDATE")) {
            return CATEGORY_EXPIRED_MANDATE;
        }

        return CATEGORY_UNKNOWN;
    }

    /**
     * Produces a human-readable description of which classification rule matched.
     * This goes into the audit log so a reviewer can understand the decision
     * without reading source code.
     */
    private String describeMatchedRule(String rawErrorCode, String category) {
        if (rawErrorCode == null || rawErrorCode.isBlank()) {
            return "null/blank error_code -> defaults to unknown";
        }

        String upper = rawErrorCode.trim().toUpperCase(Locale.ROOT);

        switch (category) {
            case CATEGORY_INSUFFICIENT_FUNDS:
                return "exact match BAD_REQUEST_ERROR -> insufficient_funds";
            case CATEGORY_TECHNICAL_DECLINE:
                return "exact match " + upper + " -> technical_decline";
            case CATEGORY_EXPIRED_MANDATE:
                if (upper.contains("EXPIRED") && upper.contains("MANDATE")) {
                    return "substring match (contains 'expired' AND 'mandate') -> expired_mandate";
                } else if (upper.contains("EXPIRED")) {
                    return "substring match (contains 'expired') -> expired_mandate";
                } else {
                    return "substring match (contains 'mandate') -> expired_mandate";
                }
            default:
                return "no rule matched for '" + rawErrorCode + "' -> unknown";
        }
    }
}
