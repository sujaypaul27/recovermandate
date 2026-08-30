package com.recovermandate.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovermandate.dto.DemoFailureSimulationRequest;
import com.recovermandate.dto.DemoFailureSimulationResponse;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.PaymentLink;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.repository.PaymentLinkRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import com.recovermandate.service.RecoveryActionService;
import com.recovermandate.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Controller providing realistic demonstration endpoints for Buildathon jury evaluations.
 * Allows triggering end-to-end webhook ingestion, AI drafting, and payment link recovery loops
 * without requiring live Razorpay account credentials.
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final WebhookService webhookService;
    private final RecoveryActionService recoveryActionService;
    private final PaymentLinkRepository paymentLinkRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final com.recovermandate.repository.FailureClassificationRepository failureClassificationRepository;
    private final com.recovermandate.repository.PaymentEventRepository paymentEventRepository;
    private final com.recovermandate.repository.RetryScheduleRepository retryScheduleRepository;
    private final com.recovermandate.repository.DispatchLogRepository dispatchLogRepository;
    private final com.recovermandate.repository.AuditLogRepository auditLogRepository;
    private final com.recovermandate.repository.WebhookDlqRepository webhookDlqRepository;
    private final com.recovermandate.audit.AuditService auditService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final List<String> DEMO_CATEGORIES = List.of(
            "insufficient_funds",
            "technical_decline",
            "expired_mandate",
            "unknown"
    );

    private static final List<String> DEMO_NAMES = List.of(
            "Aarav Sharma",
            "Priya Patel",
            "Vikram Malhotra",
            "Ananya Iyer",
            "Rohan Mehta",
            "Sneha Reddy"
    );

    @PostMapping("/simulate-failure")
    public ResponseEntity<DemoFailureSimulationResponse> simulateFailure(
            @RequestBody(required = false) DemoFailureSimulationRequest request) {

        String category = request != null && request.getCategory() != null && !request.getCategory().isBlank()
                ? request.getCategory().toLowerCase(Locale.ROOT)
                : DEMO_CATEGORIES.get(new Random().nextInt(DEMO_CATEGORIES.size()));

        Long amount = (request != null && request.getAmount() != null && request.getAmount() > 0)
                ? request.getAmount()
                : 49900L; // ₹499.00 default

        String customerName = (request != null && request.getCustomerName() != null && !request.getCustomerName().isBlank())
                ? request.getCustomerName()
                : DEMO_NAMES.get(new Random().nextInt(DEMO_NAMES.size()));

        String randomId = UUID.randomUUID().toString().substring(0, 6);
        String customerEmail = (request != null && request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank())
                ? request.getCustomerEmail().trim()
                : "demo.customer+" + randomId + "@example.com";

        String bankCode = (request != null && request.getBankCode() != null && !request.getBankCode().isBlank())
                ? request.getBankCode().toUpperCase(Locale.ROOT)
                : getBankCodeForCategory(category);

        String paymentId = "pay_demo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        String subscriptionId = "sub_demo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String customerId = "cust_demo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        String payloadJson = buildRazorpayPaymentFailedPayload(
                paymentId,
                subscriptionId,
                customerId,
                customerName,
                customerEmail,
                amount,
                bankCode,
                category
        );

        PaymentEvent event = webhookService.handleVerifiedEvent(payloadJson);

        // Find the recovery action created for this event if applicable
        Long actionId = null;
        if (event != null) {
            List<RecoveryAction> actions = recoveryActionRepository.findAll();
            for (RecoveryAction a : actions) {
                if (a.getFailureClassification() != null &&
                        a.getFailureClassification().getPaymentEvent() != null &&
                        event.getId().equals(a.getFailureClassification().getPaymentEvent().getId())) {
                    actionId = a.getId();
                    break;
                }
            }
        }

        DemoFailureSimulationResponse response = DemoFailureSimulationResponse.builder()
                .status("SUCCESS")
                .eventId(event != null ? event.getId() : null)
                .paymentId(paymentId)
                .category(category)
                .amount(amount)
                .customerEmail(customerEmail)
                .bankCode(bankCode)
                .recoveryActionId(actionId)
                .message("Simulated mandate failure ingested and classified into pipeline")
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/simulate-payment-paid")
    public ResponseEntity<Map<String, Object>> simulatePaymentPaid(
            @RequestParam(required = false) String paymentLinkId,
            @RequestParam(required = false) Long actionId,
            @RequestParam(required = false) Long amount) {

        String targetLinkId = paymentLinkId;
        Long targetAmount = amount;

        if ((targetLinkId == null || targetLinkId.isBlank()) && actionId != null) {
            Optional<PaymentLink> plOpt = paymentLinkRepository.findByRecoveryActionId(actionId);
            if (plOpt.isPresent()) {
                targetLinkId = plOpt.get().getRazorpayLinkId();
                if (targetAmount == null) {
                    targetAmount = plOpt.get().getAmount();
                }
            }
        }

        if (targetLinkId == null || targetLinkId.isBlank()) {
            // Find most recent created/dispatched payment link
            List<PaymentLink> links = paymentLinkRepository.findAll();
            if (!links.isEmpty()) {
                PaymentLink latest = links.get(links.size() - 1);
                targetLinkId = latest.getRazorpayLinkId();
                if (targetAmount == null) {
                    targetAmount = latest.getAmount();
                }
            } else {
                targetLinkId = "plink_demo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            }
        }

        if (targetAmount == null || targetAmount <= 0) {
            targetAmount = 49900L;
        }

        String payloadJson = buildRazorpayPaymentLinkPaidPayload(targetLinkId, targetAmount);
        PaymentEvent resultEvent = webhookService.handleVerifiedEvent(payloadJson);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "SUCCESS");
        resp.put("paymentLinkId", targetLinkId);
        resp.put("amountRecoveredPaise", targetAmount);
        resp.put("eventId", resultEvent != null ? resultEvent.getId() : null);
        resp.put("message", "Simulated customer payment received. Revenue closed loop completed.");

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/simulate-full-flow")
    public ResponseEntity<Map<String, Object>> simulateFullFlow(
            @RequestBody(required = false) DemoFailureSimulationRequest request) {

        // 1. Simulate Failure
        ResponseEntity<DemoFailureSimulationResponse> simResponse = simulateFailure(request);
        DemoFailureSimulationResponse failureData = simResponse.getBody();
        if (failureData == null) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Simulation failure"));
        }

        Long actionId = failureData.getRecoveryActionId();
        Map<String, Object> flowReport = new LinkedHashMap<>();
        flowReport.put("step1_failure_ingested", failureData);

        // 2. If recovery action generated, auto approve & dispatch
        if (actionId != null) {
            try {
                RecoveryAction dispatched = recoveryActionService.approveAndDispatch(actionId, "DEMO_AUTO_SYSTEM");
                flowReport.put("step2_draft_approved_and_dispatched", Map.of(
                        "actionId", dispatched.getId(),
                        "status", dispatched.getStatus(),
                        "sentAt", dispatched.getSentAt()
                ));

                // 3. Find created payment link
                Optional<PaymentLink> plOpt = paymentLinkRepository.findByRecoveryActionId(actionId);
                if (plOpt.isPresent()) {
                    PaymentLink pl = plOpt.get();
                    flowReport.put("step3_payment_link_created", Map.of(
                            "razorpayLinkId", pl.getRazorpayLinkId(),
                            "shortUrl", pl.getShortUrl(),
                            "amount", pl.getAmount()
                    ));

                    // 4. Simulate customer paying
                    String paidPayload = buildRazorpayPaymentLinkPaidPayload(pl.getRazorpayLinkId(), pl.getAmount());
                    webhookService.handleVerifiedEvent(paidPayload);
                    flowReport.put("step4_revenue_recovered", Map.of(
                            "status", "RECOVERED",
                            "razorpayLinkId", pl.getRazorpayLinkId(),
                            "amount", pl.getAmount()
                    ));
                }
            } catch (Exception e) {
                log.warn("Full flow auto dispatch/recovery simulation encountered note: {}", e.getMessage());
                flowReport.put("note", e.getMessage());
            }
        }

        flowReport.put("status", "SUCCESS");
        flowReport.put("message", "End-to-end mandate recovery workflow simulated cleanly.");
        return ResponseEntity.ok(flowReport);
    }

    @PostMapping("/reset-ledger")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> resetLedger() {
        log.info("Executing demo clean reset: wiping test operational tables & resetting audit hash chain to GENESIS...");
        
        boolean truncated = false;
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.execute("TRUNCATE TABLE dispatch_logs, payment_links, retry_schedules, recovery_actions, failure_classifications, webhook_dlq, audit_logs, payment_events CASCADE");
                truncated = true;
                log.info("Successfully executed TRUNCATE TABLE ... CASCADE across all operational tables.");
            } catch (Exception e) {
                log.warn("TRUNCATE CASCADE failed (falling back to ordered batch deletes): {}", e.getMessage());
            }
        }

        if (!truncated) {
            // Strict reverse foreign-key dependency order (children before parents):
            // 1. dispatch_logs (references recovery_actions)
            if (dispatchLogRepository != null) dispatchLogRepository.deleteAllInBatch();
            // 2. payment_links (references recovery_actions)
            if (paymentLinkRepository != null) paymentLinkRepository.deleteAllInBatch();
            // 3. retry_schedules (references payment_events)
            if (retryScheduleRepository != null) retryScheduleRepository.deleteAllInBatch();
            // 4. recovery_actions (references failure_classifications)
            if (recoveryActionRepository != null) recoveryActionRepository.deleteAllInBatch();
            // 5. failure_classifications (references payment_events)
            if (failureClassificationRepository != null) failureClassificationRepository.deleteAllInBatch();
            // 6. webhook_dlq (standalone)
            if (webhookDlqRepository != null) webhookDlqRepository.deleteAllInBatch();
            // 7. audit_logs (standalone)
            if (auditLogRepository != null) auditLogRepository.deleteAllInBatch();
            // 8. payment_events (parent, deleted last)
            if (paymentEventRepository != null) paymentEventRepository.deleteAllInBatch();
        }

        if (auditService != null) {
            auditService.resetGenesis();
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "SUCCESS");
        resp.put("message", "Operational ledger cleanly wiped. Audit chain reset to GENESIS root.");
        resp.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(resp);
    }

    private String getBankCodeForCategory(String category) {
        return switch (category) {
            case "insufficient_funds" -> "HDFC";
            case "technical_decline" -> "SBI";
            case "expired_mandate" -> "ICICI";
            default -> "AXIS";
        };
    }

    private String buildRazorpayPaymentFailedPayload(
            String paymentId,
            String subscriptionId,
            String customerId,
            String customerName,
            String customerEmail,
            Long amount,
            String bankCode,
            String category) {

        String errorCode = "BAD_REQUEST_ERROR";
        String errorReason = "payment_failed_due_to_insufficient_funds";
        String errorDescription = "The customer account does not have sufficient funds for the transaction.";

        if ("technical_decline".equalsIgnoreCase(category)) {
            errorCode = "GATEWAY_ERROR";
            errorReason = "bank_declined_due_to_internal_error";
            errorDescription = "Bank server is currently experiencing high load or connectivity failure.";
        } else if ("expired_mandate".equalsIgnoreCase(category)) {
            errorCode = "BAD_REQUEST_ERROR";
            errorReason = "mandate_authorization_expired";
            errorDescription = "Customer e-mandate registration period has elapsed or reached max execution cycle.";
        } else if ("unknown".equalsIgnoreCase(category)) {
            errorCode = "TRANSACTION_FAILURE";
            errorReason = "unmapped_issuer_decline";
            errorDescription = "The bank responded with an unmapped error code.";
        }

        long nowSec = Instant.now().getEpochSecond();

        return String.format(Locale.ROOT, """
                {
                  "entity": "event",
                  "account_id": "acc_demo_recovermandate",
                  "event": "payment.failed",
                  "contains": ["payment", "subscription"],
                  "created_at": %d,
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "%s",
                        "amount": %d,
                        "currency": "INR",
                        "status": "failed",
                        "order_id": "order_demo_101",
                        "invoice_id": "inv_demo_101",
                        "international": false,
                        "method": "emandate",
                        "amount_refunded": 0,
                        "refund_status": null,
                        "captured": false,
                        "description": "Recurring subscription billing (Demo Simulation)",
                        "card_id": null,
                        "bank": "%s",
                        "wallet": null,
                        "vpa": null,
                        "email": "%s",
                        "contact": "+919876543210",
                        "customer_id": "%s",
                        "subscription_id": "%s",
                        "error_code": "%s",
                        "error_description": "%s",
                        "error_source": "issuer",
                        "error_step": "payment_execution",
                        "error_reason": "%s",
                        "created_at": %d
                      }
                    },
                    "subscription": {
                      "entity": {
                        "id": "%s",
                        "plan_id": "plan_pro_annual",
                        "customer_id": "%s",
                        "status": "active",
                        "current_start": %d,
                        "current_end": %d,
                        "ended_at": null,
                        "quantity": 1,
                        "notes": {"tier": "Professional Growth", "source": "Demo Simulator"},
                        "charge_at": %d,
                        "start_at": %d,
                        "end_at": %d,
                        "auth_attempts": 1,
                        "total_count": 12,
                        "paid_count": 1,
                        "customer": {
                          "id": "%s",
                          "name": "%s",
                          "email": "%s",
                          "contact": "+919876543210"
                        },
                        "plan": {
                          "id": "plan_pro_annual",
                          "name": "Professional SaaS Subscription",
                          "amount": %d,
                          "currency": "INR"
                        }
                      }
                    }
                  }
                }
                """,
                nowSec,
                paymentId,
                amount,
                bankCode,
                customerEmail,
                customerId,
                subscriptionId,
                errorCode,
                errorDescription,
                errorReason,
                nowSec,
                subscriptionId,
                customerId,
                nowSec - 86400,
                nowSec + 2592000,
                nowSec,
                nowSec - 86400,
                nowSec + 2592000,
                customerId,
                customerName,
                customerEmail,
                amount
        );
    }

    private String buildRazorpayPaymentLinkPaidPayload(String linkId, Long amount) {
        long nowSec = Instant.now().getEpochSecond();
        String paymentId = "pay_paid_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        return String.format(Locale.ROOT, """
                {
                  "entity": "event",
                  "account_id": "acc_demo_recovermandate",
                  "event": "payment_link.paid",
                  "contains": ["payment_link", "payment"],
                  "created_at": %d,
                  "payload": {
                    "payment_link": {
                      "entity": {
                        "id": "%s",
                        "amount": %d,
                        "currency": "INR",
                        "status": "paid",
                        "short_url": "https://rzp.io/l/%s",
                        "created_at": %d
                      }
                    },
                    "payment": {
                      "entity": {
                        "id": "%s",
                        "amount": %d,
                        "currency": "INR",
                        "status": "captured",
                        "payment_link_id": "%s"
                      }
                    }
                  }
                }
                """,
                nowSec,
                linkId,
                amount,
                linkId,
                nowSec - 100,
                paymentId,
                amount,
                linkId
        );
    }
}
