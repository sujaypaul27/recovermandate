package com.recovermandate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.Customer;
import com.recovermandate.entity.Merchant;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.Plan;
import com.recovermandate.entity.Subscription;
import com.recovermandate.repository.CustomerRepository;
import com.recovermandate.repository.MerchantRepository;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.repository.PlanRepository;
import com.recovermandate.repository.SubscriptionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to process verified Razorpay webhook events and maintain audit logs.
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
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * Handles a verified Razorpay webhook event.
     *
     * @param rawPayload the verified raw JSON webhook body
     * @return the created or existing PaymentEvent
     */
    @Transactional
    public PaymentEvent handleVerifiedEvent(String rawPayload) {
        UUID traceId = UUID.randomUUID();
        MDC.put("traceId", traceId.toString());
        try {
            JsonNode root = objectMapper.readTree(rawPayload);

            String eventType = root.path("event").asText("unknown");

            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            JsonNode subscriptionEntity = root.path("payload").path("subscription").path("entity");

            if (isWebhookStale(root, paymentEntity)) {
                return null;
            }

            // Extract payment ID
            String razorpayPaymentId = extractPaymentId(root, paymentEntity);

            // Extract amount
            Long amount = extractAmount(root, paymentEntity, subscriptionEntity);

            // Extract failure reason code
            String failureReasonCode = extractFailureReasonCode(root, paymentEntity);

            // Extract subscription ID and find/create Subscription
            String razorpaySubscriptionId = extractSubscriptionId(root, paymentEntity, subscriptionEntity);
            Subscription subscription = null;
            if (razorpaySubscriptionId != null && !razorpaySubscriptionId.isBlank()) {
                subscription = getOrCreateSubscription(razorpaySubscriptionId, root, paymentEntity, subscriptionEntity);
            }

            // Check for duplicate payment event (idempotency)
            if (razorpayPaymentId != null && !razorpayPaymentId.isBlank()) {
                Optional<PaymentEvent> existingEvent = paymentEventRepository.findByRazorpayPaymentId(razorpayPaymentId);
                if (existingEvent.isPresent()) {
                    PaymentEvent existing = existingEvent.get();
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
            } else {
                // Generate a fallback ID if payload has no payment ID
                razorpayPaymentId = "evt_" + UUID.randomUUID().toString().replace("-", "");
            }

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
                    .build();

            PaymentEvent savedEvent = paymentEventRepository.save(paymentEvent);
            log.info("Payment event saved: id={}, razorpayPaymentId={}, eventType={}, traceId={}",
                    savedEvent.getId(), razorpayPaymentId, eventType, traceId);

            // Record successful ingestion audit log
            auditService.log(
                    "PAYMENT_EVENT",
                    savedEvent.getId(),
                    "WEBHOOK_INGESTED",
                    "SYSTEM",
                    "Payment event " + razorpayPaymentId + " of type " + eventType + " ingested successfully"
            );

            // Classify failure if event is payment.failed
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
                    if (classification != null && !classification.isAutoRecoverable()) {
                        recoveryActionService.processFailure(classification);
                    }
                }
            }

            return savedEvent;
        } catch (Exception e) {
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
        if (!paymentEntity.isMissingNode() && paymentEntity.hasNonNull("id")) {
            return paymentEntity.get("id").asText();
        }
        if (root.hasNonNull("payment_id")) {
            return root.get("payment_id").asText();
        }
        if (root.hasNonNull("id") && root.get("id").asText().startsWith("pay_")) {
            return root.get("id").asText();
        }
        return null;
    }

    private Long extractAmount(JsonNode root, JsonNode paymentEntity, JsonNode subscriptionEntity) {
        if (!paymentEntity.isMissingNode() && paymentEntity.hasNonNull("amount")) {
            return paymentEntity.get("amount").asLong();
        }
        if (root.hasNonNull("amount")) {
            return root.get("amount").asLong();
        }
        if (!subscriptionEntity.isMissingNode() && subscriptionEntity.hasNonNull("plan_amount")) {
            return subscriptionEntity.get("plan_amount").asLong();
        }
        return null;
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
        if (!paymentEntity.isMissingNode() && paymentEntity.hasNonNull("subscription_id")) {
            return paymentEntity.get("subscription_id").asText();
        }
        if (!subscriptionEntity.isMissingNode() && subscriptionEntity.hasNonNull("id")) {
            return subscriptionEntity.get("id").asText();
        }
        if (root.hasNonNull("subscription_id")) {
            return root.get("subscription_id").asText();
        }
        return null;
    }

    private Subscription getOrCreateSubscription(
            String razorpaySubscriptionId,
            JsonNode root,
            JsonNode paymentEntity,
            JsonNode subscriptionEntity) {

        String status = !subscriptionEntity.isMissingNode() && subscriptionEntity.hasNonNull("status")
                ? subscriptionEntity.get("status").asText()
                : "active";

        Optional<Subscription> existingSub = subscriptionRepository.findByRazorpaySubscriptionId(razorpaySubscriptionId);
        if (existingSub.isPresent()) {
            Subscription sub = existingSub.get();
            if (!status.equals(sub.getStatus())) {
                sub.setStatus(status);
                subscriptionRepository.save(sub);
            }
            return sub;
        }

        // Look up Merchant by razorpayAccountRef, create if not found
        String accountRef = root.path("account_id").asText("acc_default");
        Merchant merchant = merchantRepository.findByRazorpayAccountRef(accountRef).orElseGet(() -> {
            Merchant m = Merchant.builder()
                    .name("Default Merchant")
                    .razorpayAccountRef(accountRef)
                    .build();
            return merchantRepository.save(m);
        });

        // Extract Customer details
        String customerId = extractCustomerId(root, paymentEntity, subscriptionEntity);
        String customerEmail = extractCustomerEmail(root, paymentEntity);
        String customerName = extractCustomerName(root, paymentEntity);

        // Look up Customer by razorpayCustomerId, create if not found
        Customer customer = customerRepository.findByRazorpayCustomerId(customerId).orElseGet(() -> {
            Customer c = Customer.builder()
                    .merchant(merchant)
                    .name(customerName)
                    .email(customerEmail)
                    .razorpayCustomerId(customerId)
                    .build();
            return customerRepository.save(c);
        });

        // Extract Plan details
        String planId = !subscriptionEntity.isMissingNode() && subscriptionEntity.hasNonNull("plan_id")
                ? subscriptionEntity.get("plan_id").asText()
                : "plan_default";

        Long planAmount = !subscriptionEntity.isMissingNode() && subscriptionEntity.hasNonNull("plan_amount")
                ? subscriptionEntity.get("plan_amount").asLong()
                : 0L;

        String planCurrency = !subscriptionEntity.isMissingNode() && subscriptionEntity.hasNonNull("currency")
                ? subscriptionEntity.get("currency").asText()
                : "INR";

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

    private String extractCustomerId(JsonNode root, JsonNode paymentEntity, JsonNode subscriptionEntity) {
        if (!paymentEntity.isMissingNode() && paymentEntity.hasNonNull("customer_id")) {
            return paymentEntity.get("customer_id").asText();
        }
        if (!subscriptionEntity.isMissingNode() && subscriptionEntity.hasNonNull("customer_id")) {
            return subscriptionEntity.get("customer_id").asText();
        }
        JsonNode customerEntity = root.path("payload").path("customer").path("entity");
        if (!customerEntity.isMissingNode() && customerEntity.hasNonNull("id")) {
            return customerEntity.get("id").asText();
        }
        if (root.hasNonNull("customer_id")) {
            return root.get("customer_id").asText();
        }
        return "cust_placeholder";
    }

    private String extractCustomerEmail(JsonNode root, JsonNode paymentEntity) {
        if (!paymentEntity.isMissingNode() && paymentEntity.hasNonNull("email")) {
            return paymentEntity.get("email").asText();
        }
        JsonNode customerEntity = root.path("payload").path("customer").path("entity");
        if (!customerEntity.isMissingNode() && customerEntity.hasNonNull("email")) {
            return customerEntity.get("email").asText();
        }
        if (root.hasNonNull("email")) {
            return root.get("email").asText();
        }
        return "customer@example.com";
    }

    private String extractCustomerName(JsonNode root, JsonNode paymentEntity) {
        if (!paymentEntity.isMissingNode()) {
            if (paymentEntity.hasNonNull("name")) {
                return paymentEntity.get("name").asText();
            }
            if (paymentEntity.hasNonNull("customer_name")) {
                return paymentEntity.get("customer_name").asText();
            }
        }
        JsonNode customerEntity = root.path("payload").path("customer").path("entity");
        if (!customerEntity.isMissingNode()) {
            if (customerEntity.hasNonNull("name")) {
                return customerEntity.get("name").asText();
            }
            if (customerEntity.hasNonNull("customer_name")) {
                return customerEntity.get("customer_name").asText();
            }
        }
        if (root.hasNonNull("name")) {
            return root.get("name").asText();
        }
        if (root.hasNonNull("customer_name")) {
            return root.get("customer_name").asText();
        }
        return "Razorpay Customer";
    }
}
