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
import java.util.Optional;

/**
 * Service to generate and track Razorpay payment links for mandate recovery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentLinkService {

    @org.springframework.beans.factory.annotation.Value("${razorpay.dry-run:false}")
    private boolean dryRun = false;

    private final RazorpayApiClient razorpayApiClient;
    private final PaymentLinkRepository paymentLinkRepository;
    private final RecoveryActionRepository recoveryActionRepository;
    private final AuditService auditService;

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Generates a 48-hour Razorpay Payment Link for an approved recovery action.
     *
     * @param action the target RecoveryAction
     * @return the created PaymentLink entity
     */
    @Transactional
    public PaymentLink createLinkForRecoveryAction(RecoveryAction action) {
        log.info("Generating payment link for recovery action id={}", action.getId());

        if (action.getId() != null) {
            Optional<PaymentLink> existing = paymentLinkRepository.findByRecoveryActionId(action.getId());
            if (existing.isPresent()) {
                PaymentLink link = existing.get();
                log.info("Idempotency guard: Reusing existing payment link for recovery action id={}: linkId={}, url={}",
                        action.getId(), link.getRazorpayLinkId(), link.getShortUrl());
                if (action.getPaymentLinkUrl() == null || !action.getPaymentLinkUrl().equals(link.getShortUrl())) {
                    action.setPaymentLinkUrl(link.getShortUrl());
                    recoveryActionRepository.save(action);
                }
                return link;
            }
        }

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

        boolean isDemo = action.isDemoData()
                || (event != null && event.isDemoData())
                || dryRun;

        Map<String, String> linkData;
        if (isDemo) {
            log.info("[DEMO] Generated local preview payment link for demo data.");
            String frontendUrl = razorpayApiClient.getFrontendUrl();
            String demoId = "demo_" + ((action.getId() != null) ? action.getId() : java.util.UUID.randomUUID().toString().substring(0, 8));
            String demoUrl = frontendUrl + "/#/pay/" + demoId;
            linkData = Map.of("id", demoId, "short_url", demoUrl);
        } else {
            try {
                linkData = razorpayApiClient.createPaymentLink(
                        amount,
                        currency,
                        customerEmail,
                        customerName,
                        description,
                        expireBy,
                        referenceId
                );
            } catch (Exception e) {
                if (razorpayApiClient.isLiveMode()) {
                    throw e;
                }
                log.warn("Razorpay API failed in test mode. Falling back to local demo payment link: {}", e.getMessage());
                String frontendUrl = razorpayApiClient.getFrontendUrl();
                String fallbackId = "plink_quota_" + System.currentTimeMillis();
                String fallbackUrl = frontendUrl + "/#/pay/" + fallbackId;
                linkData = Map.of("id", fallbackId, "short_url", fallbackUrl);
            }

            if (linkData == null || linkData.get("short_url") == null || linkData.get("short_url").isBlank()) {
                if (!razorpayApiClient.isLiveMode()) {
                    log.warn("Razorpay test limit reached or API failed. Falling back to local demo payment link.");
                    String frontendUrl = razorpayApiClient.getFrontendUrl();
                    String fallbackId = "plink_quota_" + System.currentTimeMillis();
                    String fallbackUrl = frontendUrl + "/#/pay/" + fallbackId;
                    linkData = Map.of("id", fallbackId, "short_url", fallbackUrl);
                }
            }

            String shortUrl = (linkData != null) ? linkData.get("short_url") : null;
            log.info("[LIVE] Created real Razorpay payment link for live recovery: {}", shortUrl);
        }

        PaymentLink paymentLink = PaymentLink.builder()
                .recoveryAction(action)
                .razorpayLinkId(linkData.get("id"))
                .shortUrl(linkData.get("short_url"))
                .amount(amount)
                .currency(currency != null ? currency : "INR")
                .expireBy(expireBy)
                .status("CREATED")
                .createdAt(Instant.now())
                .isDemoData(isDemo)
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
