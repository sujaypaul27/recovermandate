package com.recovermandate.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovermandate.audit.AuditService;
import com.recovermandate.dto.CheckoutDetailsDto;
import com.recovermandate.entity.Customer;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.PaymentLink;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.entity.RetrySchedule;
import com.recovermandate.entity.Subscription;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.repository.PaymentLinkRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import com.recovermandate.repository.RetryScheduleRepository;
import com.recovermandate.repository.SubscriptionRepository;
import com.recovermandate.service.MerchantSettingsService;
import com.recovermandate.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller providing customer-facing hosted checkout resolution and payment simulation.
 */
@Slf4j
@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final PaymentLinkRepository paymentLinkRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final RetryScheduleRepository retryScheduleRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MerchantSettingsService merchantSettingsService;
    private final AuditService auditService;
    private final SseService sseService;

    @GetMapping("/{linkId}")
    public ResponseEntity<CheckoutDetailsDto> getCheckoutDetails(@PathVariable String linkId) {
        log.info("Fetching checkout details for linkId={}", linkId);

        PaymentLink link = resolvePaymentLink(linkId);
        if (link == null) {
            // Fallback for demo when link was simulated by event id
            CheckoutDetailsDto mockDto = resolveFallbackCheckout(linkId);
            if (mockDto != null) {
                return ResponseEntity.ok(mockDto);
            }
            return ResponseEntity.notFound().build();
        }

        RecoveryAction action = link.getRecoveryAction();
        PaymentEvent event = null;
        Customer customer = null;
        Subscription subscription = null;

        if (action != null && action.getFailureClassification() != null) {
            event = action.getFailureClassification().getPaymentEvent();
            if (event != null && event.getSubscription() != null) {
                subscription = event.getSubscription();
                customer = subscription.getCustomer();
            }
        }

        String merchantName = "RecoverMandate Merchant";
        if (merchantSettingsService != null) {
            try {
                com.recovermandate.dto.MerchantSettingsDto settings = merchantSettingsService.getSettings();
                if (settings != null && settings.getBusinessDisplayName() != null && !settings.getBusinessDisplayName().isBlank()) {
                    merchantName = settings.getBusinessDisplayName();
                }
            } catch (Exception ignored) {
            }
        }

        String failureCategory = action != null && action.getFailureClassification() != null
                ? action.getFailureClassification().getCategory()
                : "technical_decline";

        String failureReason = event != null && event.getFailureReasonCode() != null
                ? event.getFailureReasonCode()
                : "Temporary bank maintenance window";

        String aiExplanation = action != null && action.getAiDraftMessage() != null
                ? action.getAiDraftMessage()
                : "Your subscription mandate encountered a transient bank processing delay. Complete payment now to avoid service interruption.";

        CheckoutDetailsDto dto = CheckoutDetailsDto.builder()
                .linkId(link.getRazorpayLinkId())
                .paymentEventId(event != null ? event.getId() : null)
                .amount(link.getAmount() != null ? link.getAmount() : 49900L)
                .currency(link.getCurrency() != null ? link.getCurrency() : "INR")
                .customerName(customer != null && customer.getName() != null ? customer.getName() : "Valued Customer")
                .customerEmail(customer != null ? customer.getEmail() : "customer@example.com")
                .merchantName(merchantName)
                .planName(subscription != null && subscription.getPlan() != null && subscription.getPlan().getRazorpayPlanId() != null
                        ? "Pro Plan (" + subscription.getPlan().getRazorpayPlanId() + ")"
                        : "Pro SaaS Subscription Plan")
                .failureCategory(failureCategory)
                .failureReason(failureReason)
                .aiExplanation(aiExplanation)
                .status(link.getStatus())
                .expireBy(link.getExpireBy())
                .shortUrl(link.getShortUrl())
                .build();

        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{linkId}/pay")
    @Transactional
    public ResponseEntity<Map<String, Object>> simulateCustomerPayment(@PathVariable String linkId) {
        log.info("Simulating customer payment completion for linkId={}", linkId);

        String simulatedPaymentId = "pay_cust_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        Instant now = Instant.now();

        PaymentLink link = resolvePaymentLink(linkId);
        Long paymentEventId = null;
        Long amountPaise = 49900L;

        if (link != null) {
            link.setStatus("PAID");
            link.setPaidAt(now);
            paymentLinkRepository.save(link);

            RecoveryAction action = link.getRecoveryAction();
            if (action != null) {
                action.setStatus("RECOVERED");
                recoveryActionRepository.save(action);

                if (action.getFailureClassification() != null && action.getFailureClassification().getPaymentEvent() != null) {
                    PaymentEvent event = action.getFailureClassification().getPaymentEvent();
                    paymentEventId = event.getId();
                    amountPaise = event.getAmount() != null ? event.getAmount() : link.getAmount();

                    if (event.getSubscription() != null) {
                        Subscription sub = event.getSubscription();
                        sub.setStatus("active");
                        subscriptionRepository.save(sub);
                    }

                    // Cancel pending retries: closed-loop double-charge guard
                    List<RetrySchedule> pendingRetries = retryScheduleRepository.findByPaymentEventIdAndResult(event.getId(), "PENDING");
                    for (RetrySchedule retry : pendingRetries) {
                        retry.setResult("SKIPPED");
                        retry.setExecutedAt(now);
                        retry.setScheduleReason("SUPERSEDED_BY_LINK_PAYMENT");
                        retryScheduleRepository.save(retry);

                        auditService.log(
                                "RETRY_SCHEDULE",
                                retry.getId(),
                                "RETRY_CANCELLED_ALREADY_PAID",
                                "SYSTEM",
                                "Pending retry #" + retry.getAttemptNumber() + " cancelled because customer completed payment via Razorpay recovery link"
                        );
                    }
                }
            }

            auditService.log(
                    "PAYMENT_LINK",
                    link.getId(),
                    "PAYMENT_LINK_PAID",
                    "CUSTOMER",
                    "Customer completed payment via hosted link " + link.getRazorpayLinkId() + " (Payment ID: " + simulatedPaymentId + ")"
            );
        } else {
            // Check if linkId maps to pay_rec_{eventId}
            if (linkId.startsWith("pay_rec_")) {
                try {
                    Long evtId = Long.parseLong(linkId.replace("pay_rec_", ""));
                    Optional<PaymentEvent> evtOpt = paymentEventRepository.findById(evtId);
                    if (evtOpt.isPresent()) {
                        PaymentEvent evt = evtOpt.get();
                        paymentEventId = evt.getId();
                        amountPaise = evt.getAmount() != null ? evt.getAmount() : 49900L;

                        if (evt.getSubscription() != null) {
                            Subscription sub = evt.getSubscription();
                            sub.setStatus("active");
                            subscriptionRepository.save(sub);
                        }

                        Optional<RecoveryAction> actionOpt = recoveryActionRepository.findByFailureClassificationPaymentEvent(evt);
                        if (actionOpt.isPresent()) {
                            RecoveryAction action = actionOpt.get();
                            action.setStatus("RECOVERED");
                            recoveryActionRepository.save(action);
                        }

                        List<RetrySchedule> pendingRetries = retryScheduleRepository.findByPaymentEventIdAndResult(evt.getId(), "PENDING");
                        for (RetrySchedule retry : pendingRetries) {
                            retry.setResult("SKIPPED");
                            retry.setExecutedAt(now);
                            retry.setScheduleReason("SUPERSEDED_BY_LINK_PAYMENT");
                            retryScheduleRepository.save(retry);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        // Real-time broadcast for dashboard & ledgers
        sseService.broadcast("payment.recovered", Map.of(
                "linkId", linkId,
                "paymentId", simulatedPaymentId,
                "amount", amountPaise,
                "status", "PAID",
                "paymentEventId", paymentEventId != null ? paymentEventId : 0L,
                "timestamp", now.toString()
        ));

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "paymentId", simulatedPaymentId,
                "message", "Payment captured successfully and mandate restored.",
                "amount", amountPaise,
                "paidAt", now.toString()
        ));
    }

    private PaymentLink resolvePaymentLink(String linkId) {
        if (linkId == null || linkId.isBlank()) return null;

        // 1. Direct Razorpay Link ID match
        Optional<PaymentLink> byRzp = paymentLinkRepository.findByRazorpayLinkId(linkId);
        if (byRzp.isPresent()) return byRzp.get();

        // 2. Numeric ID match
        try {
            Long numericId = Long.parseLong(linkId);
            Optional<PaymentLink> byId = paymentLinkRepository.findById(numericId);
            if (byId.isPresent()) return byId.get();
        } catch (NumberFormatException ignored) {
        }

        // 3. pay_rec_{paymentEventId} match
        if (linkId.startsWith("pay_rec_")) {
            try {
                Long eventId = Long.parseLong(linkId.replace("pay_rec_", ""));
                Optional<PaymentEvent> eventOpt = paymentEventRepository.findById(eventId);
                if (eventOpt.isPresent()) {
                    Optional<RecoveryAction> actionOpt = recoveryActionRepository.findByFailureClassificationPaymentEvent(eventOpt.get());
                    if (actionOpt.isPresent()) {
                        return paymentLinkRepository.findByRecoveryAction(actionOpt.get()).orElse(null);
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return null;
    }

    private CheckoutDetailsDto resolveFallbackCheckout(String linkId) {
        if (linkId != null && linkId.startsWith("pay_rec_")) {
            try {
                Long eventId = Long.parseLong(linkId.replace("pay_rec_", ""));
                Optional<PaymentEvent> eventOpt = paymentEventRepository.findById(eventId);
                if (eventOpt.isPresent()) {
                    PaymentEvent evt = eventOpt.get();
                    Customer cust = evt.getSubscription() != null ? evt.getSubscription().getCustomer() : null;
                    return CheckoutDetailsDto.builder()
                            .linkId(linkId)
                            .paymentEventId(evt.getId())
                            .amount(evt.getAmount() != null ? evt.getAmount() : 49900L)
                            .currency("INR")
                            .customerName(cust != null && cust.getName() != null ? cust.getName() : "Valued Customer")
                            .customerEmail(cust != null ? cust.getEmail() : "customer@example.com")
                            .merchantName("RecoverMandate Merchant")
                            .planName("Pro SaaS Subscription")
                            .failureCategory(evt.getFailureReasonCode() != null ? evt.getFailureReasonCode() : "insufficient_funds")
                            .failureReason(evt.getFailureReasonCode() != null ? evt.getFailureReasonCode() : "Payment failure")
                            .aiExplanation("We encountered an issue processing your recurring subscription payment. Please complete payment below to avoid service interruption.")
                            .status("CREATED")
                            .expireBy(Instant.now().plus(48, java.time.temporal.ChronoUnit.HOURS))
                            .shortUrl("https://rzp.io/simulated/" + linkId)
                            .build();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
