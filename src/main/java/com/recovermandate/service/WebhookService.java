package com.recovermandate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.*;
import com.recovermandate.repository.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core event ingestion service processing verified Razorpay webhook payloads.
 * <p>
 * Implements end-to-end automated recovery orchestration:
 * <ol>
 *   <li><b>Replay Protection:</b> Validates webhook timestamps against configurable replay windows ({@code replayWindowSeconds}).</li>
 *   <li><b>Idempotency & Deduplication:</b> Identifies duplicate webhook deliveries and links to existing entities.</li>
 *   <li><b>Classification & Scheduling:</b> Coordinates deterministic failure classification, bank downtime avoidance, and AI dunning.</li>
 *   <li><b>Real-Time SSE Streaming:</b> Broadcasts pipeline phase updates to client dashboards via {@link SseService}.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    @Value("${razorpay.webhook.replay-window-seconds:300}")
    private int replayWindowSeconds = 300;

    private final PaymentEventRepository paymentEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CustomerRepository customerRepository;
    private final PlanRepository planRepository;
    private final MerchantRepository merchantRepository;
    private final FailureClassificationService failureClassificationService;
    private final RecoveryActionService recoveryActionService;
    private final RetrySchedulerService retrySchedulerService;
    private final AuditService auditService;
    private final SseService sseService;
    private final ObjectMapper objectMapper;
    private final PaymentLinkRepository paymentLinkRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final com.recovermandate.repository.FailureClassificationRepository failureClassificationRepository;
    private final com.recovermandate.repository.RetryScheduleRepository retryScheduleRepository;
    private final org.springframework.context.ApplicationEventPublisher applicationEventPublisher;
    private final com.recovermandate.client.RazorpayApiClient razorpayApiClient;

    /**
     * Handles a verified Razorpay webhook event.
     *
     * @param rawPayload the verified raw JSON webhook body
     * @return the created or existing PaymentEvent
     */
    @Transactional
    public PaymentEvent handleVerifiedEvent(String rawPayload) {
        return handleVerifiedEvent(rawPayload, false);
    }

    /**
     * Handles a verified Razorpay webhook event with data source discrimination (LIVE vs DEMO).
     *
     * @param rawPayload the verified raw JSON webhook body
     * @param isDemoData true if generated from demo simulator, false for live webhooks
     * @return the created or existing PaymentEvent
     */
    @Transactional
    public PaymentEvent handleVerifiedEvent(String rawPayload, boolean isDemoData) {
        UUID traceId = UUID.randomUUID();
        MDC.put("traceId", traceId.toString());
        try {
            log.info("Processing verified Razorpay webhook event (isDemoData={})", isDemoData);
            log.debug("Verified webhook raw payload: {}", rawPayload);
            JsonNode root = objectMapper.readTree(rawPayload);

            String eventType = root.path("event").asText("unknown");

            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            JsonNode subscriptionEntity = root.path("payload").path("subscription").path("entity");

            if (isWebhookStale(root, paymentEntity)) {
                return null;
            }

            // Extract payment ID
            String razorpayPaymentId = extractPaymentId(root, paymentEntity);
            if (razorpayPaymentId == null || razorpayPaymentId.isBlank()) {
                // Generate a fallback ID if payload has no payment ID
                razorpayPaymentId = "evt_" + UUID.randomUUID().toString().replace("-", "");
            }

            // Check for duplicate payment event (idempotency, case-insensitive)
            Optional<PaymentEvent> existingEvent = paymentEventRepository.findByRazorpayPaymentId(razorpayPaymentId);
            if (existingEvent.isPresent()) {
                PaymentEvent existing = existingEvent.get();

                if (eventType.equals(existing.getEventType())) {
                    log.warn("Duplicate webhook event ignored for payment id: {}", razorpayPaymentId);
                    auditService.log(
                            "PAYMENT_EVENT",
                            existing.getId(),
                            "DUPLICATE_WEBHOOK_IGNORED",
                            "SYSTEM",
                            "Duplicate webhook event ignored for payment id: " + razorpayPaymentId
                    );
                    return existing;
                }

                log.info("Subsequent webhook event received for payment id: {}, eventType: {}", razorpayPaymentId, eventType);
                
                // Ensure payment link resolution and subscription state transitions execute on subsequent events
                if ("payment_link.paid".equals(eventType) || "payment.captured".equals(eventType) || "order.paid".equals(eventType)) {
                    handlePaymentLinkPaid(root, existing);
                } else if ("subscription.charged".equals(eventType) || "subscription.cancelled".equals(eventType) || "subscription.halted".equals(eventType)) {
                    handleSubscriptionStateChanged(root, eventType);
                }
                
                auditService.log(
                        "PAYMENT_EVENT",
                        existing.getId(),
                        "SUBSEQUENT_WEBHOOK_PROCESSED",
                        "SYSTEM",
                        "Processed status transition for payment id " + razorpayPaymentId + " (eventType=" + eventType + ")"
                );
                return existing;
            }

            // Extract amount
            Long amount = extractAmount(root, paymentEntity, subscriptionEntity);

            // Extract failure reason code
            String failureReasonCode = extractFailureReasonCode(root, paymentEntity);

            // Extract subscription ID and find/create Subscription (ensuring every event has a customer & plan)
            String razorpaySubscriptionId = extractSubscriptionId(root, paymentEntity, subscriptionEntity);
            if (razorpaySubscriptionId == null || razorpaySubscriptionId.isBlank()) {
                razorpaySubscriptionId = "sub_mandate_" + razorpayPaymentId;
            }
            Subscription subscription = getOrCreateSubscription(razorpaySubscriptionId, root, paymentEntity, subscriptionEntity, razorpayPaymentId);

            // Create and save new PaymentEvent
            PaymentEvent paymentEvent = PaymentEvent.builder()
                    .traceId(traceId)
                    .subscription(subscription)
                    .razorpayPaymentId(razorpayPaymentId)
                    .eventType(eventType)
                    .failureReasonCode(failureReasonCode)
                    .amount(amount)
                    .receivedAt(Instant.now())
                    .rawPayload(rawPayload)
                    .isDemoData(isDemoData)
                    .build();

            PaymentEvent savedEvent = paymentEventRepository.save(paymentEvent);
            log.info("Payment event saved: id={}, razorpayPaymentId={}, eventType={}, isDemoData={}, traceId={}",
                    savedEvent.getId(), razorpayPaymentId, eventType, isDemoData, traceId);

            // Broadcast real-time SSE event for dashboard
            sseService.broadcast("webhook.received", java.util.Map.of(
                    "id", savedEvent.getId(),
                    "paymentId", razorpayPaymentId,
                    "eventType", eventType,
                    "amount", amount != null ? amount : 0L,
                    "timestamp", Instant.now().toString()
            ));

            // Record successful ingestion audit log
            auditService.log(
                    "PAYMENT_EVENT",
                    savedEvent.getId(),
                    "WEBHOOK_INGESTED",
                    "SYSTEM",
                    "Payment event " + razorpayPaymentId + " of type " + eventType + " ingested successfully"
            );

            // Classify failure if event is payment.failed
            // NOTE [ARCHITECTURAL TRADE-OFF / LATENCY]:
            // Failure classification and Gemini AI draft generation currently execute synchronously within
            // this transactional webhook handler to ensure immediate consistency for real-time SSE streaming
            // and synchronous test assertions. In high-throughput production environments with strict 5-second
            // webhook SLA cutoffs from Razorpay, webhook acknowledgement (saving PaymentEvent and returning 200)
            // would be decoupled from classification/draft generation via an asynchronous event bus (@Async,
            // Spring ApplicationEventPublisher, or RabbitMQ/Kafka queue worker). Circuit breakers & heuristic
            // fallbacks in GeminiClient (500ms timeout) mitigate gateway timeout risk in the interim.
            if ("payment.failed".equals(eventType)) {
                if (subscription != null && ("halted".equalsIgnoreCase(subscription.getStatus()) || "cancelled".equalsIgnoreCase(subscription.getStatus()))) {
                    log.info("Skipping failure classification because subscription {} is {}", subscription.getRazorpaySubscriptionId(), subscription.getStatus());
                    auditService.log(
                            "SUBSCRIPTION",
                            subscription.getId(),
                            "SUBSCRIPTION_HALTED_SKIPPED",
                            "SYSTEM",
                            "Skipping failure classification because subscription is " + subscription.getStatus()
                    );
                } else {
                    com.recovermandate.entity.FailureClassification classification = failureClassificationService.classify(savedEvent);
                    if (classification != null) {
                        sseService.broadcast("classification.complete", java.util.Map.of(
                                "eventId", savedEvent.getId(),
                                "category", classification.getCategory()
                        ));

                        // Schedule automated algorithmic retries based on category backoff
                        retrySchedulerService.scheduleRetries(savedEvent, classification);

                        if (!classification.isAutoRecoverable()) {
                            recoveryActionService.processFailure(classification);
                        }
                    }
                }
            } else if ("payment_link.paid".equals(eventType) || "payment.captured".equals(eventType) || "order.paid".equals(eventType)) {
                String linkId = extractPaymentLinkId(root);
                if (linkId != null && !linkId.isBlank()) {
                    handlePaymentLinkPaid(root, savedEvent);
                }
            } else if ("payment_link.expired".equals(eventType)) {
                handlePaymentLinkExpired(root, savedEvent);
            } else if ("subscription.cancelled".equals(eventType) || "subscription.paused".equals(eventType) || "subscription.halted".equals(eventType)) {
                handleSubscriptionStateChanged(root, eventType);
            }

            return savedEvent;
        } catch (Exception e) {
            if (isDuplicateViolation(e)) {
                log.info("Concurrent duplicate webhook delivery race condition detected and handled: {}", e.getMessage());
                auditService.log(
                        "PAYMENT_EVENT",
                        0L,
                        "DUPLICATE_WEBHOOK_IGNORED",
                        "SYSTEM",
                        "Concurrent duplicate webhook delivery safely ignored"
                );
                throw new RuntimeException("Duplicate webhook delivery already processed", e);
            }
            log.error("Error processing verified webhook payload", e);
            auditService.log(
                    "WEBHOOK",
                    0L,
                    "WEBHOOK_PROCESSING_FAILED",
                    "SYSTEM",
                    "Failed to process webhook payload: " + e.getMessage()
            );
            throw new RuntimeException("Failed to process webhook payload", e);
        } finally {
            MDC.remove("traceId");
        }
    }

    private boolean isDuplicateViolation(Throwable t) {
        while (t != null) {
            if (t instanceof org.springframework.dao.DataIntegrityViolationException
                    || t instanceof org.hibernate.exception.ConstraintViolationException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("duplicate key") || lower.contains("unique constraint") || lower.contains("ukcr6bk3kokmyexwxpleuhel7sx") || lower.contains("razorpay_payment_id")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * Records an audit log for an invalid signature attempt.
     *
     * @param rawPayload the raw webhook body
     * @param signature  the received signature header
     */
    public void recordInvalidSignature(String rawPayload, String signature) {
        log.warn("Invalid webhook signature attempt received");
        auditService.log(
                "WEBHOOK",
                0L,
                "INVALID_SIGNATURE",
                "SYSTEM",
                "Invalid webhook signature verification failed for incoming request"
        );
    }

    private boolean isWebhookStale(JsonNode root, JsonNode paymentEntity) {
        long webhookCreatedAt = root.path("created_at").asLong(0);
        if (webhookCreatedAt == 0) {
            return false;
        }
        long age = Instant.now().getEpochSecond() - webhookCreatedAt;
        if (age > replayWindowSeconds) {
            String paymentId = extractPaymentId(root, paymentEntity);
            log.warn("Stale webhook rejected: age={}s, paymentId={}", age, paymentId);
            auditService.log(
                    "WEBHOOK",
                    0L,
                    "STALE_WEBHOOK_REJECTED",
                    "SYSTEM",
                    "Webhook rejected: age=" + age + "s exceeds replay window"
            );
            return true;
        }
        return false;
    }

    private String extractPaymentId(JsonNode root, JsonNode paymentEntity) {
        return com.recovermandate.util.WebhookPayloadUtils.extractPaymentId(root, paymentEntity);
    }

    private Long extractAmount(JsonNode root, JsonNode paymentEntity, JsonNode subscriptionEntity) {
        if (paymentEntity != null && !paymentEntity.isMissingNode()) {
            if (paymentEntity.hasNonNull("amount") && paymentEntity.get("amount").asLong() > 0) {
                return paymentEntity.get("amount").asLong();
            }
            if (paymentEntity.hasNonNull("amount_due") && paymentEntity.get("amount_due").asLong() > 0) {
                return paymentEntity.get("amount_due").asLong();
            }
        }
        if (subscriptionEntity != null && !subscriptionEntity.isMissingNode()) {
            if (subscriptionEntity.hasNonNull("plan_amount") && subscriptionEntity.get("plan_amount").asLong() > 0) {
                return subscriptionEntity.get("plan_amount").asLong();
            }
            if (subscriptionEntity.hasNonNull("amount") && subscriptionEntity.get("amount").asLong() > 0) {
                return subscriptionEntity.get("amount").asLong();
            }
        }
        if (root != null) {
            JsonNode orderEntity = root.path("payload").path("order").path("entity");
            if (!orderEntity.isMissingNode() && orderEntity.hasNonNull("amount") && orderEntity.get("amount").asLong() > 0) {
                return orderEntity.get("amount").asLong();
            }
            JsonNode invoiceEntity = root.path("payload").path("invoice").path("entity");
            if (!invoiceEntity.isMissingNode() && invoiceEntity.hasNonNull("amount") && invoiceEntity.get("amount").asLong() > 0) {
                return invoiceEntity.get("amount").asLong();
            }
            if (root.hasNonNull("amount") && root.get("amount").asLong() > 0) {
                return root.get("amount").asLong();
            }
        }
        return 49900L;
    }

    private String extractFailureReasonCode(JsonNode root, JsonNode paymentEntity) {
        if (!paymentEntity.isMissingNode()) {
            if (paymentEntity.hasNonNull("error_code")) {
                return paymentEntity.get("error_code").asText();
            }
            if (paymentEntity.hasNonNull("error_reason")) {
                return paymentEntity.get("error_reason").asText();
            }
        }
        if (root.hasNonNull("error_code")) {
            return root.get("error_code").asText();
        }
        return null;
    }

    private String extractSubscriptionId(JsonNode root, JsonNode paymentEntity, JsonNode subscriptionEntity) {
        if (!paymentEntity.isMissingNode() && paymentEntity.hasNonNull("subscription_id") && !paymentEntity.get("subscription_id").asText().isBlank()) {
            return paymentEntity.get("subscription_id").asText();
        }
        if (!subscriptionEntity.isMissingNode() && subscriptionEntity.hasNonNull("id") && !subscriptionEntity.get("id").asText().isBlank()) {
            return subscriptionEntity.get("id").asText();
        }
        if (root.hasNonNull("subscription_id") && !root.get("subscription_id").asText().isBlank()) {
            return root.get("subscription_id").asText();
        }
        return null;
    }

    private static class ResolvedCustomerContext {
        private Customer customer;
        private Subscription subscription;
        private String email;
        private String name;
        private String customerId;
        private PaymentLink paymentLink;
        private RecoveryAction recoveryAction;
    }

    /**
     * Checks if an email is a Razorpay void placeholder or system placeholder address.
     */
    public static boolean isPlaceholderOrVoidEmail(String email) {
        if (email == null || email.isBlank()) {
            return true;
        }
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("void@razorpay.com")
                || normalized.endsWith("@razorpay.com")
                || normalized.startsWith("void@")
                || normalized.startsWith("dummy@")
                || normalized.startsWith("placeholder@")
                || normalized.startsWith("null@")
                || normalized.startsWith("undefined@")
                || normalized.startsWith("noreply@")
                || normalized.startsWith("no-reply@")
                || normalized.equals("null")
                || normalized.equals("undefined")
                || normalized.equals("void");
    }

    private ResolvedCustomerContext resolveLinkedCustomerContext(
            JsonNode root,
            JsonNode paymentEntity,
            JsonNode subscriptionEntity,
            String razorpayPaymentId) {

        // 1. Try extracting payment link ID (e.g. plink_...)
        String paymentLinkId = extractPaymentLinkId(root);
        if (paymentLinkId != null && !paymentLinkId.isBlank()) {
            Optional<PaymentLink> linkOpt = paymentLinkRepository.findByRazorpayLinkId(paymentLinkId);
            if (linkOpt.isEmpty()) {
                List<PaymentLink> allLinks = paymentLinkRepository.findAll();
                for (PaymentLink pl : allLinks) {
                    if (pl.getRazorpayLinkId() != null && pl.getRazorpayLinkId().equalsIgnoreCase(paymentLinkId)) {
                        linkOpt = Optional.of(pl);
                        break;
                    }
                    if (pl.getShortUrl() != null && pl.getShortUrl().contains(paymentLinkId)) {
                        linkOpt = Optional.of(pl);
                        break;
                    }
                }
            }
            if (linkOpt.isPresent()) {
                PaymentLink link = linkOpt.get();
                RecoveryAction action = link.getRecoveryAction();
                if (action != null) {
                    ResolvedCustomerContext ctx = extractContextFromRecoveryAction(action);
                    if (ctx != null) {
                        ctx.paymentLink = link;
                        log.info("Resolved real customer '{}' ({}) via PaymentLink id={}", ctx.name, ctx.email, paymentLinkId);
                        return ctx;
                    }
                }
            }
        }

        // 2. Try extracting reference_id (e.g. rec_link_pay_..., rec_link_act_...)
        String refId = extractReferenceId(root, paymentEntity);
        if (refId != null && !refId.isBlank()) {
            if (refId.contains("pay_")) {
                int idx = refId.indexOf("pay_");
                String payId = refId.substring(idx);
                if (payId.contains("_") && payId.lastIndexOf("_") > 4) {
                    payId = payId.substring(0, payId.lastIndexOf("_"));
                }
                Optional<PaymentEvent> origEventOpt = paymentEventRepository.findByRazorpayPaymentId(payId);
                if (origEventOpt.isPresent()) {
                    ResolvedCustomerContext ctx = extractContextFromPaymentEvent(origEventOpt.get());
                    if (ctx != null) {
                        log.info("Resolved real customer '{}' ({}) via reference_id payment {}", ctx.name, ctx.email, payId);
                        return ctx;
                    }
                }
            }
            if (refId.contains("act_")) {
                int idx = refId.indexOf("act_");
                String actStr = refId.substring(idx + 4);
                if (actStr.contains("_")) {
                    actStr = actStr.substring(0, actStr.indexOf("_"));
                }
                try {
                    Long actionId = Long.parseLong(actStr);
                    Optional<RecoveryAction> actionOpt = recoveryActionRepository.findById(actionId);
                    if (actionOpt.isPresent()) {
                        ResolvedCustomerContext ctx = extractContextFromRecoveryAction(actionOpt.get());
                        if (ctx != null) {
                            log.info("Resolved real customer '{}' ({}) via reference_id action {}", ctx.name, ctx.email, actionId);
                            return ctx;
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // 3. Try extracting action ID from notes or description
        Long actionId = extractActionIdFromNotesOrDescription(root, paymentEntity);
        if (actionId != null) {
            Optional<RecoveryAction> actionOpt = recoveryActionRepository.findById(actionId);
            if (actionOpt.isPresent()) {
                ResolvedCustomerContext ctx = extractContextFromRecoveryAction(actionOpt.get());
                if (ctx != null) {
                    log.info("Resolved real customer '{}' ({}) via action ID {} from notes/description", ctx.name, ctx.email, actionId);
                    return ctx;
                }
            }
        }

        // 4. Try order_id matching against existing PaymentEvents
        String orderId = extractOrderId(root, paymentEntity);
        if (orderId != null && !orderId.isBlank()) {
            List<PaymentEvent> allEvents = paymentEventRepository.findAll();
            for (int i = allEvents.size() - 1; i >= 0; i--) {
                PaymentEvent pe = allEvents.get(i);
                if (pe.getRawPayload() != null && pe.getRawPayload().contains(orderId)) {
                    ResolvedCustomerContext ctx = extractContextFromPaymentEvent(pe);
                    if (ctx != null) {
                        log.info("Resolved real customer '{}' ({}) via order_id {}", ctx.name, ctx.email, orderId);
                        return ctx;
                    }
                }
            }
        }

        // 5. Try Live Razorpay API lookup if payment link ID exists
        if (paymentLinkId != null && !paymentLinkId.isBlank() && razorpayApiClient != null) {
            try {
                JsonNode rzpLink = razorpayApiClient.fetchPaymentLink(paymentLinkId);
                if (rzpLink != null) {
                    JsonNode rzpCust = rzpLink.path("customer");
                    String rzpEmail = rzpCust.path("email").asText(null);
                    String rzpName = rzpCust.path("name").asText(null);
                    if (rzpEmail != null && !isPlaceholderOrVoidEmail(rzpEmail)) {
                        ResolvedCustomerContext ctx = new ResolvedCustomerContext();
                        ctx.email = rzpEmail;
                        ctx.name = (rzpName != null && !rzpName.isBlank() && !"Void".equalsIgnoreCase(rzpName)) ? rzpName : null;
                        log.info("Resolved real customer '{}' ({}) via Razorpay Live API for link {}", ctx.name, ctx.email, paymentLinkId);
                        return ctx;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 6. Fallback: Search most recent dispatched RecoveryAction with an active PaymentLink
        List<RecoveryAction> actions = recoveryActionRepository.findAll();
        for (int i = actions.size() - 1; i >= 0; i--) {
            RecoveryAction ra = actions.get(i);
            if ("DISPATCHED".equalsIgnoreCase(ra.getStatus()) || "APPROVED".equalsIgnoreCase(ra.getStatus())) {
                ResolvedCustomerContext ctx = extractContextFromRecoveryAction(ra);
                if (ctx != null && ctx.customer != null && !isPlaceholderOrVoidEmail(ctx.customer.getEmail())) {
                    log.info("Resolved real customer '{}' ({}) via fallback to latest dispatched RecoveryAction id={}", ctx.name, ctx.email, ra.getId());
                    return ctx;
                }
            }
        }

        return null;
    }

    private ResolvedCustomerContext extractContextFromRecoveryAction(RecoveryAction action) {
        if (action == null) return null;
        ResolvedCustomerContext ctx = new ResolvedCustomerContext();
        ctx.recoveryAction = action;
        if (action.getFailureClassification() != null && action.getFailureClassification().getPaymentEvent() != null) {
            PaymentEvent pe = action.getFailureClassification().getPaymentEvent();
            ResolvedCustomerContext eventCtx = extractContextFromPaymentEvent(pe);
            if (eventCtx != null) {
                eventCtx.recoveryAction = action;
                return eventCtx;
            }
        }
        return ctx;
    }

    private ResolvedCustomerContext extractContextFromPaymentEvent(PaymentEvent pe) {
        if (pe == null) return null;
        ResolvedCustomerContext ctx = new ResolvedCustomerContext();
        if (pe.getSubscription() != null) {
            ctx.subscription = pe.getSubscription();
            if (pe.getSubscription().getCustomer() != null) {
                Customer c = pe.getSubscription().getCustomer();
                if (c.getEmail() != null && !isPlaceholderOrVoidEmail(c.getEmail())) {
                    ctx.customer = c;
                    ctx.email = c.getEmail();
                    ctx.name = c.getName();
                    ctx.customerId = c.getRazorpayCustomerId();
                    return ctx;
                }
            }
        }
        return ctx;
    }

    private String extractReferenceId(JsonNode root, JsonNode paymentEntity) {
        if (paymentEntity != null && !paymentEntity.isMissingNode()) {
            JsonNode notes = paymentEntity.path("notes");
            if (!notes.isMissingNode() && notes.hasNonNull("reference_id")) {
                return notes.get("reference_id").asText();
            }
            if (paymentEntity.hasNonNull("reference_id")) {
                return paymentEntity.get("reference_id").asText();
            }
        }
        if (root != null) {
            JsonNode plNode = root.path("payload").path("payment_link").path("entity");
            if (!plNode.isMissingNode() && plNode.hasNonNull("reference_id")) {
                return plNode.get("reference_id").asText();
            }
            if (root.hasNonNull("reference_id")) {
                return root.get("reference_id").asText();
            }
        }
        return null;
    }

    private Long extractActionIdFromNotesOrDescription(JsonNode root, JsonNode paymentEntity) {
        if (paymentEntity != null && !paymentEntity.isMissingNode()) {
            JsonNode notes = paymentEntity.path("notes");
            if (!notes.isMissingNode()) {
                if (notes.hasNonNull("recovery_action_id")) {
                    try { return notes.get("recovery_action_id").asLong(); } catch (Exception ignored) {}
                }
                if (notes.hasNonNull("action_id")) {
                    try { return notes.get("action_id").asLong(); } catch (Exception ignored) {}
                }
            }
            if (paymentEntity.hasNonNull("description")) {
                String desc = paymentEntity.get("description").asText();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:Action\\s*#?\\s*|act_id[=:]\\s*|action_id[=:]\\s*|act_)(\\d+)").matcher(desc);
                if (m.find()) {
                    try { return Long.parseLong(m.group(1)); } catch (Exception ignored) {}
                }
            }
        }
        if (root != null) {
            JsonNode plNotes = root.path("payload").path("payment_link").path("entity").path("notes");
            if (!plNotes.isMissingNode() && plNotes.hasNonNull("recovery_action_id")) {
                try { return plNotes.get("recovery_action_id").asLong(); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private String extractOrderId(JsonNode root, JsonNode paymentEntity) {
        if (paymentEntity != null && !paymentEntity.isMissingNode()) {
            if (paymentEntity.hasNonNull("order_id") && !paymentEntity.get("order_id").asText().isBlank()) {
                return paymentEntity.get("order_id").asText();
            }
        }
        if (root != null) {
            JsonNode orderEntity = root.path("payload").path("order").path("entity");
            if (!orderEntity.isMissingNode() && orderEntity.hasNonNull("id") && !orderEntity.get("id").asText().isBlank()) {
                return orderEntity.get("id").asText();
            }
            if (root.hasNonNull("order_id") && !root.get("order_id").asText().isBlank()) {
                return root.get("order_id").asText();
            }
        }
        return null;
    }

    private Subscription getOrCreateSubscription(
            String razorpaySubscriptionId,
            JsonNode root,
            JsonNode paymentEntity,
            JsonNode subscriptionEntity,
            String razorpayPaymentId) {

        String status = !subscriptionEntity.isMissingNode() && subscriptionEntity.hasNonNull("status")
                ? subscriptionEntity.get("status").asText()
                : "active";

        // Extract Customer details from all available webhook fields
        String customerEmail = extractCustomerEmail(root, paymentEntity, subscriptionEntity);
        Customer resolvedCustomer = null;
        Subscription originalSubscription = null;

        // If customer email is void/placeholder (e.g. "void@razorpay.com") or missing, resolve from linked records
        if (isPlaceholderOrVoidEmail(customerEmail)) {
            log.info("Incoming webhook has void/placeholder customer email ('{}') for payment {}. Looking up original customer via PaymentLink/RecoveryAction...",
                    customerEmail, razorpayPaymentId);
            ResolvedCustomerContext resolvedContext = resolveLinkedCustomerContext(root, paymentEntity, subscriptionEntity, razorpayPaymentId);
            if (resolvedContext != null) {
                if (resolvedContext.customer != null) {
                    resolvedCustomer = resolvedContext.customer;
                    customerEmail = resolvedCustomer.getEmail();
                } else if (resolvedContext.email != null && !isPlaceholderOrVoidEmail(resolvedContext.email)) {
                    customerEmail = resolvedContext.email;
                }
                if (resolvedContext.subscription != null) {
                    originalSubscription = resolvedContext.subscription;
                }
            }
        }

        String customerName = extractCustomerName(root, paymentEntity, subscriptionEntity, customerEmail);
        if (resolvedCustomer != null && resolvedCustomer.getName() != null && !resolvedCustomer.getName().isBlank()) {
            if ("Customer".equals(customerName) || "Void".equalsIgnoreCase(customerName) || customerName == null || customerName.isBlank()) {
                customerName = resolvedCustomer.getName();
            }
        }

        String customerId = extractCustomerId(root, paymentEntity, subscriptionEntity, customerEmail, razorpayPaymentId);
        if (resolvedCustomer != null && resolvedCustomer.getRazorpayCustomerId() != null && !resolvedCustomer.getRazorpayCustomerId().isBlank()) {
            if (customerId == null || (customerId.startsWith("cust_") && customerId.length() < 12)) {
                customerId = resolvedCustomer.getRazorpayCustomerId();
            }
        }

        // Look up Merchant by razorpayAccountRef, create if not found
        String accountRef = root.path("account_id").asText("acc_default");
        Merchant merchant = (resolvedCustomer != null && resolvedCustomer.getMerchant() != null)
                ? resolvedCustomer.getMerchant()
                : merchantRepository.findByRazorpayAccountRef(accountRef).orElseGet(() -> {
                    Merchant m = Merchant.builder()
                            .name("Default Merchant")
                            .razorpayAccountRef(accountRef)
                            .build();
                    return merchantRepository.save(m);
                });

        // Resolve or create distinct Customer per specific email/subscription without mutating other customer records
        Customer customer = null;
        if (resolvedCustomer != null) {
            customer = resolvedCustomer;
        } else if (customerEmail != null && !customerEmail.isBlank() && !isPlaceholderOrVoidEmail(customerEmail)) {
            List<Customer> matching = customerRepository.findByMerchantAndEmailOrderByIdDesc(merchant, customerEmail);
            if (matching.isEmpty()) {
                matching = customerRepository.findByEmailOrderByIdDesc(customerEmail);
            }
            if (!matching.isEmpty()) {
                customer = matching.get(0);
                if (matching.size() > 1) {
                    log.warn("Found {} duplicate Customer records for email '{}'. Reusing most recent Customer record id={}",
                            matching.size(), customerEmail, customer.getId());
                }
            }
        }

        if (customer == null && customerId != null && !customerId.isBlank()) {
            List<Customer> byCustId = customerRepository.findByRazorpayCustomerId(customerId);
            if (!byCustId.isEmpty()) {
                for (Customer existing : byCustId) {
                    if (customerEmail == null || customerEmail.isBlank() || customerEmail.equalsIgnoreCase(existing.getEmail()) || existing.getEmail() == null || existing.getEmail().isBlank()) {
                        if (!isPlaceholderOrVoidEmail(existing.getEmail())) {
                            customer = existing;
                            break;
                        }
                    }
                }
            }
        }

        if (customer == null) {
            String safeEmail = (customerEmail != null && !customerEmail.isBlank() && !isPlaceholderOrVoidEmail(customerEmail))
                    ? customerEmail
                    : "sujaypaul2711@gmail.com";
            String safeName = (customerName != null && !customerName.isBlank() && !"Void".equalsIgnoreCase(customerName))
                    ? customerName
                    : "Customer";

            Customer c = Customer.builder()
                    .merchant(merchant)
                    .name(safeName)
                    .email(safeEmail)
                    .razorpayCustomerId(customerId != null && !customerId.isBlank() ? customerId : "cust_" + UUID.randomUUID().toString().substring(0, 8))
                    .build();
            customer = customerRepository.save(c);
        } else {
            // Update customer name only if existing name was generic or Void
            if (customerName != null && !customerName.isBlank() && !customerName.equals(customer.getName()) && !"Void".equalsIgnoreCase(customerName)) {
                if (customer.getName() == null || customer.getName().isBlank() || "Customer".equals(customer.getName()) || "Razorpay Customer".equals(customer.getName()) || "Valued Customer".equals(customer.getName()) || "Void".equalsIgnoreCase(customer.getName())) {
                    customer.setName(customerName);
                    customer = customerRepository.save(customer);
                }
            }
            if (isPlaceholderOrVoidEmail(customer.getEmail()) && customerEmail != null && !isPlaceholderOrVoidEmail(customerEmail)) {
                customer.setEmail(customerEmail);
                customer = customerRepository.save(customer);
            }
        }

        // If subscription ID is synthetic (sub_mandate_...) and we found the original subscription, reuse it
        if (originalSubscription != null && razorpaySubscriptionId != null && razorpaySubscriptionId.startsWith("sub_mandate_")) {
            return originalSubscription;
        }

        Optional<Subscription> existingSub = subscriptionRepository.findByRazorpaySubscriptionId(razorpaySubscriptionId);
        if (existingSub.isPresent()) {
            Subscription sub = existingSub.get();
            boolean subChanged = false;
            if (!status.equals(sub.getStatus())) {
                sub.setStatus(status);
                subChanged = true;
            }
            if (customer != null && (sub.getCustomer() == null || !customer.getId().equals(sub.getCustomer().getId()))) {
                sub.setCustomer(customer);
                subChanged = true;
            }
            if (subChanged) {
                Subscription saved = subscriptionRepository.save(sub);
                return saved != null ? saved : sub;
            }
            return sub;
        }

        // Extract Plan details
        String planId = !subscriptionEntity.isMissingNode() && subscriptionEntity.hasNonNull("plan_id")
                ? subscriptionEntity.get("plan_id").asText()
                : "plan_default";

        Long planAmount = extractAmount(root, paymentEntity, subscriptionEntity);

        String planCurrency = !subscriptionEntity.isMissingNode() && subscriptionEntity.hasNonNull("currency")
                ? subscriptionEntity.get("currency").asText()
                : (!paymentEntity.isMissingNode() && paymentEntity.hasNonNull("currency") ? paymentEntity.get("currency").asText() : "INR");

        String planInterval = !subscriptionEntity.isMissingNode() && subscriptionEntity.hasNonNull("interval")
                ? subscriptionEntity.get("interval").asText()
                : "monthly";

        // Look up Plan by razorpayPlanId, create if not found
        Plan plan = planRepository.findByRazorpayPlanId(planId).orElseGet(() -> {
            Plan p = Plan.builder()
                    .merchant(merchant)
                    .razorpayPlanId(planId)
                    .amount(planAmount)
                    .currency(planCurrency)
                    .interval(planInterval)
                    .build();
            return planRepository.save(p);
        });

        Subscription newSubscription = Subscription.builder()
                .customer(customer)
                .plan(plan)
                .razorpaySubscriptionId(razorpaySubscriptionId)
                .status(status)
                .build();

        return subscriptionRepository.save(newSubscription);
    }

    public static String extractCustomerId(JsonNode root, JsonNode paymentEntity, JsonNode subscriptionEntity, String customerEmail, String razorpayPaymentId) {
        if (paymentEntity != null && !paymentEntity.isMissingNode() && paymentEntity.hasNonNull("customer_id") && !paymentEntity.get("customer_id").asText().isBlank()) {
            return paymentEntity.get("customer_id").asText();
        }
        if (subscriptionEntity != null && !subscriptionEntity.isMissingNode() && subscriptionEntity.hasNonNull("customer_id") && !subscriptionEntity.get("customer_id").asText().isBlank()) {
            return subscriptionEntity.get("customer_id").asText();
        }
        if (subscriptionEntity != null && !subscriptionEntity.isMissingNode()) {
            JsonNode subCust = subscriptionEntity.path("customer");
            if (!subCust.isMissingNode() && subCust.hasNonNull("id") && !subCust.get("id").asText().isBlank()) {
                return subCust.get("id").asText();
            }
        }
        if (root != null) {
            JsonNode customerEntity = root.path("payload").path("customer").path("entity");
            if (!customerEntity.isMissingNode() && customerEntity.hasNonNull("id") && !customerEntity.get("id").asText().isBlank()) {
                return customerEntity.get("id").asText();
            }
            if (root.hasNonNull("customer_id") && !root.get("customer_id").asText().isBlank()) {
                return root.get("customer_id").asText();
            }
        }
        if (customerEmail != null && !customerEmail.isBlank() && !isPlaceholderOrVoidEmail(customerEmail) && !customerEmail.endsWith("@example.com")) {
            return "cust_" + Math.abs(customerEmail.toLowerCase(java.util.Locale.ROOT).hashCode());
        }
        return "cust_" + (razorpayPaymentId != null ? razorpayPaymentId : UUID.randomUUID().toString().substring(0, 8));
    }

    public static String extractCustomerEmail(JsonNode root, JsonNode paymentEntity, JsonNode subscriptionEntity) {
        String email = null;
        String sourceField = null;

        // 1. Subscription entity customer email (Primary for recurring subscriptions)
        if (subscriptionEntity != null && !subscriptionEntity.isMissingNode()) {
            if (subscriptionEntity.hasNonNull("customer_email") && !subscriptionEntity.get("customer_email").asText().isBlank()) {
                String candidate = subscriptionEntity.get("customer_email").asText().trim();
                if (!isPlaceholderOrVoidEmail(candidate)) {
                    email = candidate;
                    sourceField = "payload.subscription.entity.customer_email";
                }
            }
            if (email == null) {
                JsonNode subCust = subscriptionEntity.path("customer");
                if (!subCust.isMissingNode() && subCust.hasNonNull("email") && !subCust.get("email").asText().isBlank()) {
                    String candidate = subCust.get("email").asText().trim();
                    if (!isPlaceholderOrVoidEmail(candidate)) {
                        email = candidate;
                        sourceField = "payload.subscription.entity.customer.email";
                    }
                }
            }
            if (email == null) {
                JsonNode subNotes = subscriptionEntity.path("notes");
                if (!subNotes.isMissingNode()) {
                    if (subNotes.hasNonNull("customer_email") && !subNotes.get("customer_email").asText().isBlank()) {
                        String candidate = subNotes.get("customer_email").asText().trim();
                        if (!isPlaceholderOrVoidEmail(candidate)) {
                            email = candidate;
                            sourceField = "payload.subscription.entity.notes.customer_email";
                        }
                    } else if (subNotes.hasNonNull("subscriber_email") && !subNotes.get("subscriber_email").asText().isBlank()) {
                        String candidate = subNotes.get("subscriber_email").asText().trim();
                        if (!isPlaceholderOrVoidEmail(candidate)) {
                            email = candidate;
                            sourceField = "payload.subscription.entity.notes.subscriber_email";
                        }
                    } else if (subNotes.hasNonNull("user_email") && !subNotes.get("user_email").asText().isBlank()) {
                        String candidate = subNotes.get("user_email").asText().trim();
                        if (!isPlaceholderOrVoidEmail(candidate)) {
                            email = candidate;
                            sourceField = "payload.subscription.entity.notes.user_email";
                        }
                    } else if (subNotes.hasNonNull("email") && !subNotes.get("email").asText().isBlank()) {
                        String candidate = subNotes.get("email").asText().trim();
                        if (!isPlaceholderOrVoidEmail(candidate)) {
                            email = candidate;
                            sourceField = "payload.subscription.entity.notes.email";
                        }
                    }
                }
            }
        }

        // 2. Customer entity in payload (Direct customer object)
        if (email == null && root != null) {
            JsonNode customerEntity = root.path("payload").path("customer").path("entity");
            if (!customerEntity.isMissingNode() && customerEntity.hasNonNull("email") && !customerEntity.get("email").asText().isBlank()) {
                String candidate = customerEntity.get("email").asText().trim();
                if (!isPlaceholderOrVoidEmail(candidate)) {
                    email = candidate;
                    sourceField = "payload.customer.entity.email";
                }
            }
        }

        // 3. Payment entity customer notes / customer object (Specific subscriber notes on the transaction)
        if (email == null && paymentEntity != null && !paymentEntity.isMissingNode()) {
            JsonNode notes = paymentEntity.path("notes");
            if (!notes.isMissingNode()) {
                if (notes.hasNonNull("customer_email") && !notes.get("customer_email").asText().isBlank()) {
                    String candidate = notes.get("customer_email").asText().trim();
                    if (!isPlaceholderOrVoidEmail(candidate)) {
                        email = candidate;
                        sourceField = "payload.payment.entity.notes.customer_email";
                    }
                } else if (notes.hasNonNull("subscriber_email") && !notes.get("subscriber_email").asText().isBlank()) {
                    String candidate = notes.get("subscriber_email").asText().trim();
                    if (!isPlaceholderOrVoidEmail(candidate)) {
                        email = candidate;
                        sourceField = "payload.payment.entity.notes.subscriber_email";
                    }
                } else if (notes.hasNonNull("user_email") && !notes.get("user_email").asText().isBlank()) {
                    String candidate = notes.get("user_email").asText().trim();
                    if (!isPlaceholderOrVoidEmail(candidate)) {
                        email = candidate;
                        sourceField = "payload.payment.entity.notes.user_email";
                    }
                }
            }
            if (email == null) {
                JsonNode cust = paymentEntity.path("customer");
                if (!cust.isMissingNode() && cust.hasNonNull("email") && !cust.get("email").asText().isBlank()) {
                    String candidate = cust.get("email").asText().trim();
                    if (!isPlaceholderOrVoidEmail(candidate)) {
                        email = candidate;
                        sourceField = "payload.payment.entity.customer.email";
                    }
                } else if (paymentEntity.hasNonNull("customer_email") && !paymentEntity.get("customer_email").asText().isBlank()) {
                    String candidate = paymentEntity.get("customer_email").asText().trim();
                    if (!isPlaceholderOrVoidEmail(candidate)) {
                        email = candidate;
                        sourceField = "payload.payment.entity.customer_email";
                    }
                }
            }
        }

        // 4. Order / Invoice specific subscriber email
        if (email == null && root != null) {
            JsonNode orderEntity = root.path("payload").path("order").path("entity");
            if (!orderEntity.isMissingNode()) {
                JsonNode orderNotes = orderEntity.path("notes");
                if (!orderNotes.isMissingNode() && orderNotes.hasNonNull("customer_email") && !orderNotes.get("customer_email").asText().isBlank()) {
                    String candidate = orderNotes.get("customer_email").asText().trim();
                    if (!isPlaceholderOrVoidEmail(candidate)) {
                        email = candidate;
                        sourceField = "payload.order.entity.notes.customer_email";
                    }
                } else if (orderEntity.hasNonNull("email") && !orderEntity.get("email").asText().isBlank()) {
                    String candidate = orderEntity.get("email").asText().trim();
                    if (!isPlaceholderOrVoidEmail(candidate)) {
                        email = candidate;
                        sourceField = "payload.order.entity.email";
                    }
                }
            }
            if (email == null) {
                JsonNode invoiceEntity = root.path("payload").path("invoice").path("entity");
                if (!invoiceEntity.isMissingNode()) {
                    if (invoiceEntity.hasNonNull("customer_email") && !invoiceEntity.get("customer_email").asText().isBlank()) {
                        String candidate = invoiceEntity.get("customer_email").asText().trim();
                        if (!isPlaceholderOrVoidEmail(candidate)) {
                            email = candidate;
                            sourceField = "payload.invoice.entity.customer_email";
                        }
                    } else if (invoiceEntity.hasNonNull("email") && !invoiceEntity.get("email").asText().isBlank()) {
                        String candidate = invoiceEntity.get("email").asText().trim();
                        if (!isPlaceholderOrVoidEmail(candidate)) {
                            email = candidate;
                            sourceField = "payload.invoice.entity.email";
                        }
                    }
                }
            }
        }

        // 5. Payment entity root email / notes email (Fallback for non-subscription direct payments)
        if (email == null && paymentEntity != null && !paymentEntity.isMissingNode()) {
            if (paymentEntity.hasNonNull("email") && !paymentEntity.get("email").asText().isBlank()) {
                String candidate = paymentEntity.get("email").asText().trim();
                if (!isPlaceholderOrVoidEmail(candidate)) {
                    email = candidate;
                    sourceField = "payload.payment.entity.email";
                }
            }
            if (email == null) {
                JsonNode notes = paymentEntity.path("notes");
                if (!notes.isMissingNode() && notes.hasNonNull("email") && !notes.get("email").asText().isBlank()) {
                    String candidate = notes.get("email").asText().trim();
                    if (!isPlaceholderOrVoidEmail(candidate)) {
                        email = candidate;
                        sourceField = "payload.payment.entity.notes.email";
                    }
                }
            }
        }

        // 6. Root level customer_email (Fallback)
        if (email == null && root != null) {
            if (root.hasNonNull("customer_email") && !root.get("customer_email").asText().isBlank()) {
                String candidate = root.get("customer_email").asText().trim();
                if (!isPlaceholderOrVoidEmail(candidate)) {
                    email = candidate;
                    sourceField = "root.customer_email";
                }
            } else if (root.hasNonNull("email") && !root.get("email").asText().isBlank()) {
                String candidate = root.get("email").asText().trim();
                if (!isPlaceholderOrVoidEmail(candidate)) {
                    email = candidate;
                    sourceField = "root.email";
                }
            }
        }

        if (email != null && !email.isBlank() && !isPlaceholderOrVoidEmail(email)) {
            org.slf4j.LoggerFactory.getLogger(WebhookService.class).info(
                    "Extracted customer/subscriber email '{}' from webhook field: {}", email, sourceField
            );
            return email;
        }

        return null;
    }

    public static String extractCustomerName(JsonNode root, JsonNode paymentEntity, JsonNode subscriptionEntity, String customerEmail) {
        String name = null;
        if (paymentEntity != null && !paymentEntity.isMissingNode()) {
            if (paymentEntity.hasNonNull("name") && !paymentEntity.get("name").asText().isBlank()) {
                name = paymentEntity.get("name").asText().trim();
            } else if (paymentEntity.hasNonNull("customer_name") && !paymentEntity.get("customer_name").asText().isBlank()) {
                name = paymentEntity.get("customer_name").asText().trim();
            }
            if (name == null) {
                JsonNode notes = paymentEntity.path("notes");
                if (!notes.isMissingNode()) {
                    if (notes.hasNonNull("name") && !notes.get("name").asText().isBlank()) {
                        name = notes.get("name").asText().trim();
                    } else if (notes.hasNonNull("customer_name") && !notes.get("customer_name").asText().isBlank()) {
                        name = notes.get("customer_name").asText().trim();
                    }
                }
            }
        }
        if (name == null && subscriptionEntity != null && !subscriptionEntity.isMissingNode()) {
            JsonNode subCust = subscriptionEntity.path("customer");
            if (!subCust.isMissingNode()) {
                if (subCust.hasNonNull("name") && !subCust.get("name").asText().isBlank()) {
                    name = subCust.get("name").asText().trim();
                } else if (subCust.hasNonNull("customer_name") && !subCust.get("customer_name").asText().isBlank()) {
                    name = subCust.get("customer_name").asText().trim();
                }
            }
        }
        if (name == null && root != null) {
            JsonNode customerEntity = root.path("payload").path("customer").path("entity");
            if (!customerEntity.isMissingNode()) {
                if (customerEntity.hasNonNull("name") && !customerEntity.get("name").asText().isBlank()) {
                    name = customerEntity.get("name").asText().trim();
                } else if (customerEntity.hasNonNull("customer_name") && !customerEntity.get("customer_name").asText().isBlank()) {
                    name = customerEntity.get("customer_name").asText().trim();
                }
            }
            if (name == null) {
                if (root.hasNonNull("name") && !root.get("name").asText().isBlank()) {
                    name = root.get("name").asText().trim();
                } else if (root.hasNonNull("customer_name") && !root.get("customer_name").asText().isBlank()) {
                    name = root.get("customer_name").asText().trim();
                }
            }
        }

        if (name != null && (name.equalsIgnoreCase("Void") || name.equalsIgnoreCase("void@razorpay.com") || name.toLowerCase(java.util.Locale.ROOT).contains("void@"))) {
            name = null;
        }

        if (name != null && !name.isBlank()) {
            return name;
        }
        if (customerEmail != null && !customerEmail.isBlank() && customerEmail.contains("@") && !isPlaceholderOrVoidEmail(customerEmail) && !customerEmail.endsWith("@example.com")) {
            String prefix = customerEmail.split("@")[0].replace(".", " ").replace("_", " ").replace("-", " ");
            if (!prefix.isBlank()) {
                return Character.toUpperCase(prefix.charAt(0)) + (prefix.length() > 1 ? prefix.substring(1) : "");
            }
        }
        return "Customer";
    }

    private void handlePaymentLinkPaid(JsonNode root, PaymentEvent savedEvent) {
        String razorpayLinkId = extractPaymentLinkId(root);
        if (razorpayLinkId == null || razorpayLinkId.isBlank()) {
            log.warn("payment_link.paid webhook missing payment link ID");
            return;
        }

        Optional<PaymentLink> linkOpt = paymentLinkRepository.findByRazorpayLinkId(razorpayLinkId);
        if (linkOpt.isEmpty()) {
            List<PaymentLink> allLinks = paymentLinkRepository.findAll();
            for (PaymentLink pl : allLinks) {
                if (pl.getRazorpayLinkId() != null && pl.getRazorpayLinkId().equalsIgnoreCase(razorpayLinkId)) {
                    linkOpt = Optional.of(pl);
                    break;
                }
                if (pl.getShortUrl() != null && pl.getShortUrl().contains(razorpayLinkId)) {
                    linkOpt = Optional.of(pl);
                    break;
                }
            }
        }
        if (linkOpt.isEmpty()) {
            // Check reference_id on root, payment_link, or payment entity
            String refId = null;
            if (root.path("payload").path("payment_link").path("entity").hasNonNull("reference_id")) {
                refId = root.path("payload").path("payment_link").path("entity").get("reference_id").asText();
            } else if (root.path("payload").path("payment").path("entity").path("notes").hasNonNull("reference_id")) {
                refId = root.path("payload").path("payment").path("entity").path("notes").get("reference_id").asText();
            } else if (root.hasNonNull("reference_id")) {
                refId = root.get("reference_id").asText();
            }
            
            if (refId != null && refId.startsWith("rec_link_")) {
                String targetPayId = refId.substring(9);
                if (targetPayId.contains("_")) {
                    targetPayId = targetPayId.substring(0, targetPayId.lastIndexOf("_"));
                }
                Optional<PaymentEvent> targetEventOpt = paymentEventRepository.findByRazorpayPaymentId(targetPayId);
                if (targetEventOpt.isPresent()) {
                    Optional<FailureClassification> fcOpt = failureClassificationRepository.findByPaymentEvent(targetEventOpt.get());
                    if (fcOpt.isPresent()) {
                        Optional<RecoveryAction> raOpt = recoveryActionRepository.findByFailureClassification(fcOpt.get());
                        if (raOpt.isPresent()) {
                            linkOpt = paymentLinkRepository.findByRecoveryAction(raOpt.get());
                        }
                    }
                }
            }
        }
        if (linkOpt.isPresent()) {
            PaymentLink link = linkOpt.get();
            link.setStatus("PAID");
            link.setPaidAt(Instant.now());
            paymentLinkRepository.save(link);

            RecoveryAction action = link.getRecoveryAction();
            Long recoveredAmount = link.getAmount() != null ? link.getAmount() : (savedEvent != null && savedEvent.getAmount() != null ? savedEvent.getAmount() : 0L);

            // Double-Charge Prevention: Cancel any PENDING retry schedules for this payment event / subscription
            PaymentEvent event = (action != null && action.getFailureClassification() != null)
                    ? action.getFailureClassification().getPaymentEvent()
                    : savedEvent;

            if (event != null && event.getId() != null) {
                List<com.recovermandate.entity.RetrySchedule> pendingRetries = retryScheduleRepository.findByPaymentEventIdAndResult(event.getId(), "PENDING");
                for (com.recovermandate.entity.RetrySchedule pendingRetry : pendingRetries) {
                    pendingRetry.setResult("SKIPPED");
                    pendingRetry.setExecutedAt(Instant.now());
                    pendingRetry.setScheduleReason("SUPERSEDED_BY_LINK_PAYMENT");
                    retryScheduleRepository.save(pendingRetry);

                    auditService.log(
                            "RETRY_SCHEDULE",
                            pendingRetry.getId(),
                            "RETRY_CANCELLED_ALREADY_PAID",
                            "SYSTEM",
                            "Automated retry #" + pendingRetry.getAttemptNumber() + " cancelled because customer paid via Razorpay Payment Link " + razorpayLinkId
                    );
                }
            }

            if (action != null) {
                action.setStatus("RECOVERED");
                recoveryActionRepository.save(action);

                auditService.log(
                        "RECOVERY_ACTION",
                        action.getId(),
                        "PAYMENT_RECOVERED",
                        "SYSTEM",
                        String.format("Payment link %s paid. Revenue recovered: %d paise", razorpayLinkId, recoveredAmount)
                );

                sseService.broadcast("recovery.completed", java.util.Map.of(
                        "actionId", action.getId(),
                        "amount", recoveredAmount,
                        "paymentLinkId", razorpayLinkId,
                        "timestamp", Instant.now().toString()
                ));

                // Mark any other intermediate/pending recovery actions on this same subscription as COMPLETED
                if (event != null && event.getSubscription() != null && event.getSubscription().getId() != null) {
                    Long subId = event.getSubscription().getId();
                    try {
                        List<RecoveryAction> siblingActions = recoveryActionRepository.findBySubscriptionId(subId);
                        for (RecoveryAction sibling : siblingActions) {
                            if (sibling.getId() != null && !sibling.getId().equals(action.getId())) {
                                if ("DRAFTED".equalsIgnoreCase(sibling.getStatus())
                                        || "APPROVED".equalsIgnoreCase(sibling.getStatus())
                                        || "DISPATCHED".equalsIgnoreCase(sibling.getStatus())
                                        || "PENDING_DRAFT".equalsIgnoreCase(sibling.getStatus())
                                        || "BLOCKED".equalsIgnoreCase(sibling.getStatus())) {
                                    sibling.setStatus("COMPLETED");
                                    recoveryActionRepository.save(sibling);

                                    auditService.log(
                                            "RECOVERY_ACTION",
                                            sibling.getId(),
                                            "ACTION_COMPLETED_SUBSCRIPTION_RESOLVED",
                                            "SYSTEM",
                                            String.format("Action #%d marked as COMPLETED because subscription %s was recovered via link %s",
                                                    sibling.getId(),
                                                    event.getSubscription().getRazorpaySubscriptionId(),
                                                    razorpayLinkId)
                                    );

                                    if (sibling.getFailureClassification() != null && sibling.getFailureClassification().getPaymentEvent() != null) {
                                        PaymentEvent siblingEvent = sibling.getFailureClassification().getPaymentEvent();
                                        if (siblingEvent.getId() != null) {
                                            List<com.recovermandate.entity.RetrySchedule> siblingRetries =
                                                    retryScheduleRepository.findByPaymentEventIdAndResult(siblingEvent.getId(), "PENDING");
                                            for (com.recovermandate.entity.RetrySchedule pr : siblingRetries) {
                                                pr.setResult("SKIPPED");
                                                pr.setExecutedAt(Instant.now());
                                                pr.setScheduleReason("SUPERSEDED_BY_LINK_PAYMENT");
                                                retryScheduleRepository.save(pr);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Error marking sibling recovery actions as COMPLETED for subscription id={}: {}", subId, e.getMessage());
                    }
                }
            } else {
                auditService.log(
                        "PAYMENT_LINK",
                        link.getId(),
                        "PAYMENT_RECOVERED",
                        "SYSTEM",
                        String.format("Payment link %s paid. Revenue recovered: %d paise", razorpayLinkId, recoveredAmount)
                );

                sseService.broadcast("recovery.completed", java.util.Map.of(
                        "amount", recoveredAmount,
                        "paymentLinkId", razorpayLinkId,
                        "timestamp", Instant.now().toString()
                ));
            }
            log.info("Closed-loop recovery completed for linkId={}, recoveredAmount={}", razorpayLinkId, recoveredAmount);
        } else {
            log.warn("PaymentLink not found for linkId={}", razorpayLinkId);
        }
    }

    private void handlePaymentLinkExpired(JsonNode root, PaymentEvent savedEvent) {
        String razorpayLinkId = extractPaymentLinkId(root);
        if (razorpayLinkId == null || razorpayLinkId.isBlank()) {
            log.warn("payment_link.expired webhook missing payment link ID");
            return;
        }

        Optional<PaymentLink> linkOpt = paymentLinkRepository.findByRazorpayLinkId(razorpayLinkId);
        if (linkOpt.isPresent()) {
            PaymentLink link = linkOpt.get();
            link.setStatus("EXPIRED");
            paymentLinkRepository.save(link);

            RecoveryAction action = link.getRecoveryAction();
            if (action != null) {
                action.setStatus("LINK_EXPIRED");
                recoveryActionRepository.save(action);

                auditService.log(
                        "RECOVERY_ACTION",
                        action.getId(),
                        "PAYMENT_LINK_EXPIRED_UNRECOVERED",
                        "SYSTEM",
                        "Payment link " + razorpayLinkId + " expired without customer settlement"
                );
            }

            auditService.log(
                    "PAYMENT_LINK",
                    link.getId(),
                    "PAYMENT_LINK_EXPIRED",
                    "SYSTEM",
                    "Payment link " + razorpayLinkId + " marked as EXPIRED"
            );
            log.info("Payment link marked as EXPIRED for linkId={}", razorpayLinkId);
        }
    }

    private void handleSubscriptionStateChanged(JsonNode root, String eventType) {
        JsonNode subEntity = root.path("payload").path("subscription").path("entity");
        String subId = subEntity.hasNonNull("id") ? subEntity.get("id").asText() : extractSubscriptionId(root, root.path("payload").path("payment").path("entity"), subEntity);
        if (subId == null || subId.isBlank()) {
            return;
        }

        String newStatus = eventType.replace("subscription.", "").toLowerCase(java.util.Locale.ROOT);
        Optional<Subscription> subOpt = subscriptionRepository.findByRazorpaySubscriptionId(subId);
        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            if (!newStatus.equals(sub.getStatus())) {
                sub.setStatus(newStatus);
                subscriptionRepository.save(sub);
            }

            // Cancel any pending retries for this subscription
            List<com.recovermandate.entity.RetrySchedule> pendingRetries = retryScheduleRepository.findByPaymentEventSubscriptionIdAndResult(sub.getId(), "PENDING");
            for (com.recovermandate.entity.RetrySchedule retry : pendingRetries) {
                retry.setResult("SKIPPED");
                retry.setExecutedAt(Instant.now());
                retry.setScheduleReason("SUBSCRIPTION_" + newStatus.toUpperCase(java.util.Locale.ROOT));
                retryScheduleRepository.save(retry);

                auditService.log(
                        "RETRY_SCHEDULE",
                        retry.getId(),
                        "RETRY_SKIPPED_SUBSCRIPTION_INACTIVE",
                        "SYSTEM",
                        String.format("Retry #%d cancelled because subscription %s transitioned to %s",
                                retry.getAttemptNumber(), subId, newStatus)
                );
            }

            auditService.log(
                    "SUBSCRIPTION",
                    sub.getId(),
                    "SUBSCRIPTION_STATUS_UPDATED",
                    "SYSTEM",
                    "Subscription " + subId + " status updated to " + newStatus + " via webhook"
            );
            log.info("Subscription {} status updated to {}, cancelled {} pending retries", subId, newStatus, pendingRetries.size());
        }
    }

    private String extractPaymentLinkId(JsonNode root) {
        if (root == null) return null;
        JsonNode plNode = root.path("payload").path("payment_link").path("entity");
        if (plNode.hasNonNull("id") && !plNode.get("id").asText().isBlank()) {
            return plNode.get("id").asText().trim();
        }
        JsonNode plDirect = root.path("payload").path("payment_link");
        if (plDirect.hasNonNull("id") && !plDirect.get("id").asText().isBlank()) {
            return plDirect.get("id").asText().trim();
        }
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        if (paymentEntity.hasNonNull("payment_link_id") && !paymentEntity.get("payment_link_id").asText().isBlank()) {
            return paymentEntity.get("payment_link_id").asText().trim();
        }
        if (paymentEntity.hasNonNull("notes")) {
            JsonNode notes = paymentEntity.path("notes");
            if (notes.hasNonNull("payment_link_id") && !notes.get("payment_link_id").asText().isBlank()) return notes.get("payment_link_id").asText().trim();
            if (notes.hasNonNull("link_id") && !notes.get("link_id").asText().isBlank()) return notes.get("link_id").asText().trim();
            if (notes.hasNonNull("plink_id") && !notes.get("plink_id").asText().isBlank()) return notes.get("plink_id").asText().trim();
            if (notes.hasNonNull("payment_link") && !notes.get("payment_link").asText().isBlank()) return notes.get("payment_link").asText().trim();
        }
        if (paymentEntity.hasNonNull("description")) {
            String desc = paymentEntity.get("description").asText();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(plink_[A-Za-z0-9_]+)").matcher(desc);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        if (root.hasNonNull("payment_link_id") && !root.get("payment_link_id").asText().isBlank()) {
            return root.get("payment_link_id").asText().trim();
        }
        return null;
    }
}
