package com.recovermandate.service;

import com.recovermandate.audit.AuditService;
import com.recovermandate.client.RazorpayApiClient;
import com.recovermandate.entity.Customer;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.PaymentLink;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.repository.PaymentLinkRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Service to generate and track Razorpay payment links for mandate recovery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentLinkService {

    private final RazorpayApiClient razorpayApiClient;
    private final PaymentLinkRepository paymentLinkRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final AuditService auditService;

    /**
     * Generates a 48-hour Razorpay Payment Link for an approved recovery action.
     *
     * @param action the target RecoveryAction
     * @return the created PaymentLink entity
     */
    @Transactional
    public PaymentLink createLinkForRecoveryAction(RecoveryAction action) {
        log.info("Generating payment link for recovery action id={}", action.getId());

        PaymentEvent event = null;
        Customer customer = null;
        String currency = "INR";
        Long amount = 0L;

        if (action.getFailureClassification() != null && action.getFailureClassification().getPaymentEvent() != null) {
            event = action.getFailureClassification().getPaymentEvent();
            amount = event.getAmount() != null ? event.getAmount() : 0L;
            if (event.getSubscription() != null) {
                customer = event.getSubscription().getCustomer();
                if (event.getSubscription().getPlan() != null) {
                    currency = event.getSubscription().getPlan().getCurrency();
                }
            }
        }

        String customerEmail = customer != null ? customer.getEmail() : null;
        String customerName = customer != null && customer.getName() != null && !customer.getName().isBlank()
                ? customer.getName()
                : "Valued Customer";
        Instant expireBy = Instant.now().plus(48, ChronoUnit.HOURS);
        String description = "Recovery payment for mandate failure (Action #" + action.getId() + ")";
        String referenceId = (event != null && event.getRazorpayPaymentId() != null)
                ? "rec_link_" + event.getRazorpayPaymentId()
                : "rec_link_act_" + action.getId();

        Map<String, String> linkData = razorpayApiClient.createPaymentLink(
                amount,
                currency,
                customerEmail,
                customerName,
                description,
                expireBy,
                referenceId
        );

        PaymentLink paymentLink = PaymentLink.builder()
                .recoveryAction(action)
                .razorpayLinkId(linkData.get("id"))
                .shortUrl(linkData.get("short_url"))
                .amount(amount)
                .currency(currency != null ? currency : "INR")
                .expireBy(expireBy)
                .status("CREATED")
                .createdAt(Instant.now())
                .build();

        PaymentLink savedLink = paymentLinkRepository.save(paymentLink);

        action.setPaymentLinkUrl(savedLink.getShortUrl());
        recoveryActionRepository.save(action);

        log.info("Payment link created for action id={}: linkId={}, url={}",
                action.getId(), savedLink.getRazorpayLinkId(), savedLink.getShortUrl());

        auditService.log(
                "RECOVERY_ACTION",
                action.getId(),
                "PAYMENT_LINK_CREATED",
                "SYSTEM",
                "Created Razorpay payment link " + savedLink.getRazorpayLinkId() + " (" + savedLink.getShortUrl() + ")"
        );

        return savedLink;
    }
}
