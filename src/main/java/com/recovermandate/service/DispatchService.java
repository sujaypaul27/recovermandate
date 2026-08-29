package com.recovermandate.service;

import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.Customer;
import com.recovermandate.entity.DispatchLog;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.repository.DispatchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Service to orchestrate multi-channel customer communications for approved recovery actions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final DispatchLogRepository dispatchLogRepository;
    private final AuditService auditService;
    private final SseService sseService;

    /**
     * Dispatches recovery message with embedded payment link to customer.
     *
     * @param action         the target RecoveryAction
     * @param paymentLinkUrl the generated short payment link URL
     * @return the saved DispatchLog record
     */
    @Transactional
    public DispatchLog dispatchRecovery(RecoveryAction action, String paymentLinkUrl) {
        Customer customer = null;
        if (action.getFailureClassification() != null && action.getFailureClassification().getPaymentEvent() != null) {
            PaymentEvent event = action.getFailureClassification().getPaymentEvent();
            if (event.getSubscription() != null) {
                customer = event.getSubscription().getCustomer();
            }
        }

        String recipientEmail = customer != null && customer.getEmail() != null && !customer.getEmail().isBlank()
                ? customer.getEmail().trim()
                : null;

        if (recipientEmail == null) {
            log.warn("Cannot dispatch recovery email for action id={}: missing customer email", action.getId());

            DispatchLog failedLog = DispatchLog.builder()
                    .recoveryAction(action)
                    .channel("EMAIL")
                    .recipient("UNKNOWN")
                    .status("DISPATCH_FAILED")
                    .errorDetail("missing customer email")
                    .sentAt(Instant.now())
                    .build();

            DispatchLog savedLog = dispatchLogRepository.save(failedLog);

            auditService.log(
                    "RECOVERY_ACTION",
                    action.getId(),
                    "DISPATCH_FAILED",
                    "SYSTEM",
                    "Dispatch failed for action id " + action.getId() + ": missing customer email"
            );

            return savedLog;
        }

        String maskedRecipient = maskEmail(recipientEmail);

        log.info("Dispatching recovery email to {} for action id={} with link {}",
                maskedRecipient, action.getId(), paymentLinkUrl);

        // 1. Persist Dispatch Log
        DispatchLog dispatchLog = DispatchLog.builder()
                .recoveryAction(action)
                .channel("EMAIL")
                .recipient(recipientEmail)
                .status("SENT")
                .sentAt(Instant.now())
                .build();

        DispatchLog savedLog = dispatchLogRepository.save(dispatchLog);

        // 2. Audit Record
        auditService.log(
                "RECOVERY_ACTION",
                action.getId(),
                "RECOVERY_DISPATCHED",
                "SYSTEM",
                "Dispatched recovery email to " + maskedRecipient + " with payment link " + paymentLinkUrl
        );

        // 3. Real-time SSE Broadcast
        sseService.broadcast("recovery.dispatched", Map.of(
                "actionId", action.getId(),
                "recipient", maskedRecipient,
                "channel", "EMAIL",
                "paymentLinkUrl", paymentLinkUrl != null ? paymentLinkUrl : "",
                "timestamp", Instant.now().toString()
        ));

        return savedLog;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***@example.com";
        }
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 3) {
            return name.charAt(0) + "***@" + domain;
        }
        return name.substring(0, 3) + "***@" + domain;
    }
}
