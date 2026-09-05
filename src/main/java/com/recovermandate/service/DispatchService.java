package com.recovermandate.service;

import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.Customer;
import com.recovermandate.entity.DispatchLog;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.mail.EmailSendResult;
import com.recovermandate.mail.EmailService;
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
    private final EmailService emailService;

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
        PaymentEvent event = null;
        if (action.getFailureClassification() != null && action.getFailureClassification().getPaymentEvent() != null) {
            event = action.getFailureClassification().getPaymentEvent();
            if (event.getSubscription() != null && event.getSubscription().getCustomer() != null) {
                customer = event.getSubscription().getCustomer();
            }
        }

        String recipientEmail = customer != null && customer.getEmail() != null && !customer.getEmail().isBlank() && !WebhookService.isPlaceholderOrVoidEmail(customer.getEmail())
                ? customer.getEmail().trim()
                : null;

        if (recipientEmail == null && event != null && event.getRawPayload() != null && !event.getRawPayload().isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(event.getRawPayload());
                com.fasterxml.jackson.databind.JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                com.fasterxml.jackson.databind.JsonNode subscriptionEntity = root.path("payload").path("subscription").path("entity");
                recipientEmail = WebhookService.extractCustomerEmail(root, paymentEntity, subscriptionEntity);
                if (recipientEmail != null && WebhookService.isPlaceholderOrVoidEmail(recipientEmail)) {
                    recipientEmail = null;
                }
            } catch (Exception ignored) {
            }
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
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
        String customerName = customer != null ? customer.getName() : "Customer";
        Long amount = event != null ? event.getAmount() : null;
        String currency = "INR";
        String messageText = action.getAiDraftMessage();
        if (messageText != null && paymentLinkUrl != null && !paymentLinkUrl.isBlank()) {
            messageText = com.recovermandate.util.PaymentLinkPlaceholderUtil.replacePlaceholderLinks(messageText, paymentLinkUrl);
            action.setAiDraftMessage(messageText);
        }

        log.info("Executing recovery email dispatch to {} for action id={} with link {}",
                maskedRecipient, action.getId(), paymentLinkUrl);

        // 1. Invoke Email Service (Live SMTP or Simulated)
        EmailSendResult result = emailService.sendRecoveryEmail(
                recipientEmail,
                customerName,
                "Action Required: Your Subscription Mandate Payment Failed",
                messageText,
                paymentLinkUrl,
                amount,
                currency
        );

        if (result.isFailed()) {
            log.error("Email dispatch failed for action id={} recipient={}: {}",
                    action.getId(), maskedRecipient, result.getErrorMessage());

            DispatchLog failedLog = DispatchLog.builder()
                    .recoveryAction(action)
                    .channel("EMAIL")
                    .recipient(recipientEmail)
                    .status("DISPATCH_FAILED")
                    .errorDetail(result.getErrorMessage())
                    .sentAt(Instant.now())
                    .build();

            DispatchLog savedLog = dispatchLogRepository.save(failedLog);

            auditService.log(
                    "RECOVERY_ACTION",
                    action.getId(),
                    "DISPATCH_FAILED",
                    "SYSTEM",
                    "Email dispatch failed for action id " + action.getId() + " to " + maskedRecipient + ": " + result.getErrorMessage()
            );

            sseService.broadcast("recovery.dispatch_failed", Map.of(
                    "actionId", action.getId(),
                    "recipient", maskedRecipient,
                    "channel", "EMAIL",
                    "error", result.getErrorMessage(),
                    "timestamp", Instant.now().toString()
            ));

            return savedLog;
        }

        // 2. Persist Successful Dispatch Log
        String dispatchMode = result.isRealSent() ? "REAL_SMTP" : "SIMULATED";
        DispatchLog dispatchLog = DispatchLog.builder()
                .recoveryAction(action)
                .channel("EMAIL")
                .recipient(recipientEmail)
                .status("SENT")
                .providerMessageId(result.getProviderMessageId())
                .sentAt(Instant.now())
                .build();

        DispatchLog savedLog = dispatchLogRepository.save(dispatchLog);

        // 3. Cryptographic Audit Record
        auditService.log(
                "RECOVERY_ACTION",
                action.getId(),
                "RECOVERY_DISPATCHED",
                "SYSTEM",
                String.format("Dispatched recovery email (%s) to %s with payment link %s [msgId=%s]",
                        dispatchMode, maskedRecipient, paymentLinkUrl, result.getProviderMessageId())
        );

        // 4. Real-time SSE Broadcast
        sseService.broadcast("recovery.dispatched", Map.of(
                "actionId", action.getId(),
                "recipient", maskedRecipient,
                "channel", "EMAIL",
                "mode", dispatchMode,
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
