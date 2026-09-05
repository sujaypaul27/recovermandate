package com.recovermandate.service;

import com.recovermandate.ai.DraftResult;
import com.recovermandate.ai.GeminiClient;
import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.Customer;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.PaymentLink;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.entity.Subscription;
import com.recovermandate.repository.RecoveryActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrator service for the recovery action lifecycle:
 * <ol>
 *   <li><b>AI Drafting:</b> Invokes {@link GeminiClient} (with fallback to {@link com.recovermandate.ai.HeuristicFallbackEngine})
 *       to draft customized dunning emails.</li>
 *   <li><b>Deterministic Safety Validation:</b> Screens generated text via {@link RecoveryActionValidationService}
 *       for unauthorized promises, aggressive tone, or amount mismatches. Transitions invalid drafts to {@code BLOCKED}.</li>
 *   <li><b>Human Approval Queue:</b> Routes high-value drafts or manual interventions to the operator approval queue.</li>
 *   <li><b>Payment Link Creation:</b> Invokes {@link PaymentLinkService} to generate hosted Razorpay recovery links.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryActionService {

    private final GeminiClient geminiClient;
    private final RecoveryActionValidationService validationService;
    private final RecoveryActionRepository recoveryActionRepository;
    private final AuditService auditService;
    private final SseService sseService;
    private final PaymentLinkService paymentLinkService;
    private final DispatchService dispatchService;
    private final BankHealthService bankHealthService;
    private final MerchantSettingsService merchantSettingsService;

    /**
     * Processes unrecovered payment failures by generating and validating AI recovery drafts.
     */
    @Transactional
    public void processFailure(FailureClassification classification) {
        if (classification.isAutoRecoverable()) {
            log.info("Classification {} is auto-recoverable, skipping manual recovery draft", classification.getId());
            return;
        }

        PaymentEvent event = classification.getPaymentEvent();
        
        // Prevent duplicate RecoveryAction if one already exists
        Optional<RecoveryAction> existing = recoveryActionRepository.findByFailureClassification(classification);
        if (existing.isPresent()) {
            log.info("RecoveryAction already exists for classification id={}, skipping duplicate", classification.getId());
            auditService.log(
                    "FAILURE_CLASSIFICATION",
                    classification.getId() != null ? classification.getId() : 0L,
                    "DUPLICATE_RECOVERY_ACTION_SKIPPED",
                    "SYSTEM",
                    "RecoveryAction already exists for classification id " + classification.getId()
            );
            return;
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

        String merchantName = "RecoverMandate";
        if (merchantSettingsService != null) {
            try {
                var settings = merchantSettingsService.getSettings();
                if (settings != null && settings.getBusinessDisplayName() != null && !settings.getBusinessDisplayName().isBlank()) {
                    merchantName = settings.getBusinessDisplayName();
                }
            } catch (Exception ignored) {
            }
        }

        DraftResult draftResult = geminiClient.generateDraft(
                customerName,
                event != null ? event.getAmount() : null,
                currency,
                classification.getCategory(),
                daysSinceFailure,
                merchantName
        );

        if (draftResult == null || draftResult.message() == null) {
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

        String draftMessage = draftResult.message();
        String draftSource = draftResult.source();

        Optional<String> blockReason = validationService.validateDraft(draftMessage, event != null ? event.getAmount() : null);
        
        String status = blockReason.isPresent() ? "BLOCKED" : "DRAFTED";

        RecoveryAction action = RecoveryAction.builder()
                .failureClassification(classification)
                .aiDraftMessage(draftMessage)
                .draftSource(draftSource)
                .status(status)
                .createdAt(Instant.now())
                .actor("SYSTEM")
                .isDemoData(event != null && event.isDemoData())
                .build();

        RecoveryAction savedAction = recoveryActionRepository.save(action);

        // Broadcast real-time draft generation event for dashboard
        sseService.broadcast("draft.generated", java.util.Map.of(
                "actionId", savedAction.getId(),
                "status", status,
                "draftSource", draftSource != null ? draftSource : "AI"
        ));
        
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
            log.info("AI Draft generated via {} and validated for classification {}", draftSource, classification.getId());
            auditService.log(
                    "RECOVERY_ACTION",
                    savedAction.getId(),
                    "AI_DRAFT_GENERATED",
                    "SYSTEM",
                    "AI draft generated via " + (draftSource != null ? draftSource : "AI") + " and passed validation."
            );

            // Auto-Pilot Execution Gate: only triggers for validated, unblocked drafts matching merchant policy
            if (merchantSettingsService != null && merchantSettingsService.isAutoPilotEligible(event != null ? event.getAmount() : null, classification.getCategory())) {
                String autoPilotTone = merchantSettingsService.getSettings().getDefaultTone();
                log.info("Auto-Pilot rule matched for action {}. Auto-dispatching with tone: {}", savedAction.getId(), autoPilotTone);
                try {
                    approveAndDispatch(savedAction.getId(), "AUTO_PILOT", autoPilotTone, null);
                    auditService.log(
                            "RECOVERY_ACTION",
                            savedAction.getId(),
                            "AUTO_PILOT_DISPATCHED",
                            "AUTO_PILOT",
                            String.format("Action auto-dispatched by Merchant Auto-Pilot Policy (Amount: ₹%.2f, Tone: %s)",
                                    (event != null && event.getAmount() != null ? event.getAmount() : 0L) / 100.0,
                                    autoPilotTone.toUpperCase(java.util.Locale.ROOT))
                    );
                } catch (Exception e) {
                    log.error("Failed to auto-dispatch action {} via auto-pilot", savedAction.getId(), e);
                }
            }
        }
    }

    /**
     * Batch approves and dispatches recovery actions with per-item error isolation.
     */
    public com.recovermandate.dto.BatchApproveResponse batchApprove(com.recovermandate.dto.BatchApproveRequest request) {
        List<Long> actionIds = request != null ? request.getActionIds() : null;
        if ((actionIds == null || actionIds.isEmpty()) && request != null && request.getMaxAmount() != null) {
            List<RecoveryAction> drafted = recoveryActionRepository.findByStatus("DRAFTED");
            actionIds = drafted.stream()
                    .filter(a -> {
                        if (a.getFailureClassification() == null || a.getFailureClassification().getPaymentEvent() == null) return false;
                        Long amt = a.getFailureClassification().getPaymentEvent().getAmount();
                        return amt != null && amt <= request.getMaxAmount();
                    })
                    .map(RecoveryAction::getId)
                    .toList();
        }

        if (actionIds == null || actionIds.isEmpty()) {
            return com.recovermandate.dto.BatchApproveResponse.builder()
                    .totalRequested(0)
                    .successful(0)
                    .failed(0)
                    .approvedActionIds(java.util.Collections.emptyList())
                    .errors(java.util.Collections.emptyList())
                    .build();
        }

        String approvedBy = (request.getApprovedBy() != null && !request.getApprovedBy().isBlank())
                ? request.getApprovedBy()
                : "MERCHANT_BATCH";
        String tone = (request.getTone() != null && !request.getTone().isBlank())
                ? request.getTone()
                : (merchantSettingsService != null ? merchantSettingsService.getSettings().getDefaultTone() : "balanced");

        List<Long> approvedIds = new java.util.ArrayList<>();
        List<com.recovermandate.dto.BatchApproveResponse.BatchItemError> errors = new java.util.ArrayList<>();

        for (Long id : actionIds) {
            try {
                approveAndDispatch(id, approvedBy, tone, null);
                approvedIds.add(id);
            } catch (Exception e) {
                log.error("Failed to approve and dispatch action id in batch: {}", id, e);
                errors.add(com.recovermandate.dto.BatchApproveResponse.BatchItemError.builder()
                        .actionId(id)
                        .errorMessage(e.getMessage() != null ? e.getMessage() : "Failed to approve and dispatch")
                        .build());
            }
        }

        return com.recovermandate.dto.BatchApproveResponse.builder()
                .totalRequested(actionIds.size())
                .successful(approvedIds.size())
                .failed(errors.size())
                .approvedActionIds(approvedIds)
                .errors(errors)
                .build();
    }

    private Customer extractCustomer(PaymentEvent event) {
        if (event != null && event.getSubscription() != null && event.getSubscription().getCustomer() != null) {
            Customer c = event.getSubscription().getCustomer();
            if (c.getEmail() != null && !c.getEmail().isBlank() && !WebhookService.isPlaceholderOrVoidEmail(c.getEmail())) {
                String safeName = (c.getName() != null && !c.getName().isBlank() && !"Void".equalsIgnoreCase(c.getName())) ? c.getName() : "Rsiv ece2024";
                return Customer.builder()
                        .id(c.getId())
                        .name(safeName)
                        .email(c.getEmail())
                        .razorpayCustomerId(c.getRazorpayCustomerId())
                        .merchant(c.getMerchant())
                        .build();
            }
        }
        if (event != null && event.getRawPayload() != null && !event.getRawPayload().isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(event.getRawPayload());
                com.fasterxml.jackson.databind.JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                com.fasterxml.jackson.databind.JsonNode subscriptionEntity = root.path("payload").path("subscription").path("entity");
                String email = WebhookService.extractCustomerEmail(root, paymentEntity, subscriptionEntity);
                if (email != null && !WebhookService.isPlaceholderOrVoidEmail(email)) {
                    String name = WebhookService.extractCustomerName(root, paymentEntity, subscriptionEntity, email);
                    return Customer.builder()
                            .name(name != null && !"Void".equalsIgnoreCase(name) ? name : "Rsiv ece2024")
                            .email(email)
                            .razorpayCustomerId(WebhookService.extractCustomerId(root, paymentEntity, subscriptionEntity, email, event.getRazorpayPaymentId()))
                            .build();
                }
            } catch (Exception ignored) {
            }
        }
        
        // If customer record exists but has void email or is blank, provide safe fallback
        if (event != null && event.getSubscription() != null && event.getSubscription().getCustomer() != null) {
            Customer c = event.getSubscription().getCustomer();
            String safeName = (c.getName() != null && !c.getName().isBlank() && !"Void".equalsIgnoreCase(c.getName())) ? c.getName() : "Sujay Paul";
            return Customer.builder()
                    .id(c.getId())
                    .name(safeName)
                    .email("sujaypaul2711@gmail.com")
                    .razorpayCustomerId(c.getRazorpayCustomerId())
                    .merchant(c.getMerchant())
                    .build();
        }
        return Customer.builder()
                .name("Sujay Paul")
                .email("sujaypaul2711@gmail.com")
                .build();
    }
    
    @Transactional
    public void approveAction(Long actionId, String approvedBy) {
        RecoveryAction action = recoveryActionRepository.findById(actionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("RecoveryAction not found with id: " + actionId));

        if (!"DRAFTED".equals(action.getStatus())) {
            throw new IllegalStateException("Action cannot be approved because it is not in DRAFTED state. Current state: " + action.getStatus());
        }

        if (action.getVersion() == null) {
            action.setVersion(0L);
        }

        action.setStatus("APPROVED");
        action.setApprovedBy(approvedBy);
        action.setApprovedAt(Instant.now());
        recoveryActionRepository.save(action);

        // Broadcast real-time approval event
        sseService.broadcast("action.approved", java.util.Map.of(
                "actionId", actionId
        ));

        auditService.log("RECOVERY_ACTION", actionId, "ACTION_APPROVED", approvedBy, "Draft message approved.");
    }

    /**
     * Approves a recovery action, generates a Razorpay payment link, and dispatches multi-channel communications.
     */
    @Transactional
    public RecoveryAction approveAndDispatch(Long actionId, String approvedBy) {
        return approveAndDispatch(actionId, approvedBy, null, null);
    }

    /**
     * Approves a recovery action with selected tone strategy, generates a Razorpay payment link,
     * and dispatches multi-channel communications.
     */
    @Transactional
    public RecoveryAction approveAndDispatch(Long actionId, String approvedBy, String tone, String customMessage) {
        approveAction(actionId, approvedBy);

        RecoveryAction action = recoveryActionRepository.findById(actionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("RecoveryAction not found with id: " + actionId));

        if (action.getVersion() == null) {
            action.setVersion(0L);
        }

        if (tone != null && !tone.isBlank()) {
            action.setTone(tone.toLowerCase(java.util.Locale.ROOT));
        }

        PaymentLink paymentLink = paymentLinkService.createLinkForRecoveryAction(action);
        String realLinkUrl = paymentLink.getShortUrl();
        action.setPaymentLinkUrl(realLinkUrl);

        String messageToUse = (customMessage != null && !customMessage.isBlank())
                ? customMessage
                : action.getAiDraftMessage();

        if (messageToUse != null && realLinkUrl != null && !realLinkUrl.isBlank()) {
            messageToUse = com.recovermandate.util.PaymentLinkPlaceholderUtil.replacePlaceholderLinks(messageToUse, realLinkUrl);
        }
        action.setAiDraftMessage(messageToUse);

        dispatchService.dispatchRecovery(action, realLinkUrl);

        action.setStatus("DISPATCHED");
        action.setSentAt(Instant.now());
        RecoveryAction updated = recoveryActionRepository.save(action);

        sseService.broadcast("recovery.dispatched", java.util.Map.of(
                "actionId", actionId,
                "tone", tone != null ? tone : "balanced",
                "paymentLinkUrl", paymentLink.getShortUrl() != null ? paymentLink.getShortUrl() : "",
                "timestamp", Instant.now().toString()
        ));

        String toneSuffix = (tone != null && !tone.isBlank()) ? " (Tone: " + tone.toUpperCase(java.util.Locale.ROOT) + ")" : "";
        auditService.log(
                "RECOVERY_ACTION",
                actionId,
                "ACTION_DISPATCHED",
                approvedBy,
                "Recovery action dispatched via EMAIL" + toneSuffix + " with payment link: " + paymentLink.getShortUrl()
        );

        return updated;
    }

    @Transactional
    public void rejectAction(Long actionId, String rejectedBy, String reason) {
        RecoveryAction action = recoveryActionRepository.findById(actionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("RecoveryAction not found with id: " + actionId));

        if (!"DRAFTED".equals(action.getStatus())) {
            throw new IllegalStateException("Action cannot be rejected because it is not in DRAFTED state. Current state: " + action.getStatus());
        }

        if (action.getVersion() == null) {
            action.setVersion(0L);
        }

        action.setStatus("REJECTED");
        recoveryActionRepository.save(action);

        // Broadcast real-time rejection event
        sseService.broadcast("action.rejected", java.util.Map.of(
                "actionId", actionId,
                "reason", reason != null ? reason : ""
        ));

        auditService.log("RECOVERY_ACTION", actionId, "ACTION_REJECTED", rejectedBy, "Draft message rejected. Reason: " + reason);
    }
    
    @Transactional
    public void markAsSent(Long actionId) {
        auditService.log("RECOVERY_ACTION", actionId, "ACTION_SENT", "SYSTEM", "Message sent to customer.");
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.recovermandate.dto.RecoveryActionResponse> getRecoveryActions(String status, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<RecoveryAction> actions;
        if (status != null && !status.trim().isEmpty()) {
            actions = recoveryActionRepository.findByStatus(status, pageable);
        } else {
            actions = recoveryActionRepository.findAll(pageable);
        }
        return actions.map(this::toResponse);
    }

    private com.recovermandate.dto.RecoveryActionResponse toResponse(RecoveryAction action) {
        FailureClassification fc = action.getFailureClassification();
        PaymentEvent pe = fc != null ? fc.getPaymentEvent() : null;

        String rawErrorCode = fc != null ? fc.getRawErrorCode() : (pe != null ? pe.getFailureReasonCode() : null);
        String category = fc != null ? fc.getCategory() : null;
        Boolean autoRecoverable = fc != null ? fc.isAutoRecoverable() : null;
        String bank = (pe != null && bankHealthService != null) ? bankHealthService.extractBankCode(pe) : null;
        String paymentId = pe != null ? pe.getRazorpayPaymentId() : null;
        Long amount = pe != null ? pe.getAmount() : null;
        
        Customer customer = (pe != null) ? extractCustomer(pe) : null;
        String customerEmail = customer != null ? customer.getEmail() : null;
        String customerName = customer != null ? customer.getName() : null;

        if (WebhookService.isPlaceholderOrVoidEmail(customerEmail)) {
            customerEmail = "sujaypaul2711@gmail.com";
        }
        if (customerName == null || customerName.isBlank() || "Void".equalsIgnoreCase(customerName)) {
            customerName = "Sujay Paul";
        }

        String matchedRule = FailureClassificationService.describeMatchedRule(rawErrorCode, category != null ? category : "unknown");

        boolean isDemo = Boolean.TRUE.equals(action.isDemoData())
                || (action.getActor() != null && action.getActor().startsWith("DEMO"))
                || (paymentId != null && paymentId.startsWith("pay_demo_"))
                || (customerEmail != null && (customerEmail.contains("demo.customer") || customerEmail.contains("sujaypaul2711@gmail.com")));

        return com.recovermandate.dto.RecoveryActionResponse.builder()
                .id(action.getId())
                .failureClassificationId(fc != null ? fc.getId() : null)
                .aiDraftMessage(action.getAiDraftMessage())
                .draftSource(action.getDraftSource())
                .paymentLinkUrl(action.getPaymentLinkUrl())
                .status(action.getStatus())
                .approvedBy(action.getApprovedBy())
                .approvedAt(action.getApprovedAt())
                .sentAt(action.getSentAt())
                .createdAt(action.getCreatedAt())
                .actor(action.getActor())
                .rawErrorCode(rawErrorCode)
                .bank(bank)
                .category(category)
                .autoRecoverable(autoRecoverable)
                .matchedRule(matchedRule)
                .razorpayPaymentId(paymentId)
                .amount(amount)
                .customerEmail(customerEmail)
                .customerName(customerName)
                .isDemoData(isDemo)
                .build();
    }
}
