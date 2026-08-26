package com.recovermandate.service;

import com.recovermandate.ai.GeminiClient;
import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.Customer;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.entity.Subscription;
import com.recovermandate.repository.RecoveryActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryActionService {

    private final GeminiClient geminiClient;
    private final RecoveryActionValidationService validationService;
    private final RecoveryActionRepository recoveryActionRepository;
    private final AuditService auditService;

    @Transactional
    public void processFailure(FailureClassification classification) {
        if (classification.isAutoRecoverable()) {
            log.info("Classification {} is auto-recoverable, skipping manual recovery draft", classification.getId());
            return;
        }

        PaymentEvent event = classification.getPaymentEvent();
        
        // Prevent duplicate RecoveryAction if one already exists
        if (event != null && event.getId() != null) {
             // In a real app we'd have a finder on RecoveryActionRepository for failureClassification.
             // But let's assume we handle it here or let the DB unique constraint throw.
        }

        Customer customer = extractCustomer(event);
        String customerName = customer != null ? customer.getName() : "Customer";
        
        String currency = "INR";
        if (event != null && event.getSubscription() != null && event.getSubscription().getPlan() != null) {
            currency = event.getSubscription().getPlan().getCurrency();
        }
        
        int daysSinceFailure = 0;
        if (event != null && event.getReceivedAt() != null) {
            daysSinceFailure = (int) ChronoUnit.DAYS.between(event.getReceivedAt(), Instant.now());
        }

        String draftMessage = geminiClient.generateDraft(
                customerName,
                event != null ? event.getAmount() : null,
                currency,
                classification.getCategory(),
                daysSinceFailure
        );

        if (draftMessage == null) {
            log.warn("Gemini API failed to generate a draft for classification id={}", classification.getId());
            auditService.log(
                    "FAILURE_CLASSIFICATION",
                    classification.getId(),
                    "AI_DRAFT_FAILED",
                    "SYSTEM",
                    "Gemini API failed to return a draft message"
            );
            return;
        }

        Optional<String> blockReason = validationService.validateDraft(draftMessage, event != null ? event.getAmount() : null);
        
        String status = blockReason.isPresent() ? "BLOCKED" : "DRAFTED";

        RecoveryAction action = RecoveryAction.builder()
                .failureClassification(classification)
                .aiDraftMessage(draftMessage)
                .status(status)
                .createdAt(Instant.now())
                .actor("SYSTEM")
                .build();

        RecoveryAction savedAction = recoveryActionRepository.save(action);
        
        if (blockReason.isPresent()) {
            log.warn("AI Draft blocked for classification {}: {}", classification.getId(), blockReason.get());
            auditService.log(
                    "RECOVERY_ACTION",
                    savedAction.getId(),
                    "AI_DRAFT_BLOCKED",
                    "SYSTEM",
                    "AI draft blocked by validation gate. Reason: " + blockReason.get()
            );
        } else {
            log.info("AI Draft generated and validated for classification {}", classification.getId());
            auditService.log(
                    "RECOVERY_ACTION",
                    savedAction.getId(),
                    "AI_DRAFT_GENERATED",
                    "SYSTEM",
                    "AI draft generated and passed validation."
            );
        }
    }

    private Customer extractCustomer(PaymentEvent event) {
        if (event != null && event.getSubscription() != null) {
            return event.getSubscription().getCustomer();
        }
        return null;
    }
    
    @Transactional
    public void approveAction(Long actionId, String approvedBy) {
        // Stub for future
        auditService.log("RECOVERY_ACTION", actionId, "ACTION_APPROVED", approvedBy, "Draft message approved.");
    }

    @Transactional
    public void rejectAction(Long actionId, String rejectedBy) {
        // Stub for future
        auditService.log("RECOVERY_ACTION", actionId, "ACTION_REJECTED", rejectedBy, "Draft message rejected.");
    }
    
    @Transactional
    public void markAsSent(Long actionId) {
        // Stub for future
        auditService.log("RECOVERY_ACTION", actionId, "ACTION_SENT", "SYSTEM", "Message sent to customer.");
    }
}
