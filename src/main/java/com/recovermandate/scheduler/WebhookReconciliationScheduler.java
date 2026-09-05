package com.recovermandate.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovermandate.audit.AuditService;
import com.recovermandate.client.RazorpayApiClient;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Background scheduler that periodically reconciles payment events and payment link
 * statuses against the Razorpay API.
 * <p>
 * Performs two key operational safety functions:
 * <ol>
 *   <li><b>Backfill Ingestion:</b> Scans Razorpay for recent failed payment events within a 24-hour lookback window
 *       that may have been dropped due to network partitions or webhook timeouts. Reconciled events are ingested
 *       with {@code isDemoData = true} to prevent artificial backlog inflation on live operational metrics.</li>
 *   <li><b>Payment Link Settlement:</b> Reconciles open live Razorpay payment links against the gateway API,
 *       transitioning recovered mandates to {@code PAID} / {@code RECOVERED} if customer settled out-of-band.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookReconciliationScheduler {

    private final RazorpayApiClient razorpayApiClient;
    private final WebhookService webhookService;
    private final PaymentEventRepository paymentEventRepository;
    private final com.recovermandate.repository.PaymentLinkRepository paymentLinkRepository;
    private final com.recovermandate.repository.RecoveryActionRepository recoveryActionRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * Executes the reconciliation cycle on a configurable cron expression
     * (defaults to every 15 minutes: {@code 0 0/15 * * * ?}).
     */
    @Scheduled(cron = "${recovermandate.reconciliation.cron:0 0/15 * * * ?}")
    public void reconcileWebhooks() {
        log.info("Starting webhook reconciliation job");
        int foundCount = 0;
        int ingestedCount = 0;

        try {
            // Check for events in the last 24 hours
            Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
            List<String> rawEvents = razorpayApiClient.fetchRecentFailedPaymentEvents(since);
            foundCount = rawEvents.size();

            for (String rawEvent : rawEvents) {
                if (ingestedCount >= 5) {
                    break;
                }
                JsonNode root = objectMapper.readTree(rawEvent);
                String paymentId = extractPaymentId(root);
                
                if (paymentId != null && !paymentId.isBlank()) {
                    boolean exists = paymentEventRepository.findByRazorpayPaymentIdIgnoreCase(paymentId).isPresent();
                    if (!exists) {
                        String desc = root.path("payload").path("payment").path("entity").path("description").asText("");
                        if (desc.startsWith("#plink_") || desc.contains("plink_")) {
                            log.debug("Skipping reconciliation ingestion for payment link attempt: {}", paymentId);
                            continue;
                        }

                        log.info("Missing payment event found during reconciliation, ingesting: {}", paymentId);
                        webhookService.handleVerifiedEvent(rawEvent, true); // reconciled/backfilled events are not live demo traffic — mark as demo data to avoid polluting live metrics
                        ingestedCount++;
                    }
                }
            }

            // Reconcile open live payment links against Razorpay API
            if (paymentLinkRepository != null) {
                List<com.recovermandate.entity.PaymentLink> openLinks = paymentLinkRepository.findAll();
                for (com.recovermandate.entity.PaymentLink pl : openLinks) {
                    if ("CREATED".equalsIgnoreCase(pl.getStatus()) && pl.getRazorpayLinkId() != null 
                            && !pl.getRazorpayLinkId().startsWith("plink_sim_") && !pl.getRazorpayLinkId().startsWith("plink_preview_")) {
                        try {
                            JsonNode rzpLink = razorpayApiClient.fetchPaymentLink(pl.getRazorpayLinkId());
                            if (rzpLink != null) {
                                String rzpStatus = rzpLink.path("status").asText();
                                long amountPaid = rzpLink.path("amount_paid").asLong(0);
                                if ("paid".equalsIgnoreCase(rzpStatus) || amountPaid > 0) {
                                    pl.setStatus("PAID");
                                    pl.setPaidAt(Instant.now());
                                    paymentLinkRepository.save(pl);

                                    com.recovermandate.entity.RecoveryAction action = pl.getRecoveryAction();
                                    if (action != null && recoveryActionRepository != null) {
                                        action.setStatus("RECOVERED");
                                        recoveryActionRepository.save(action);
                                    }
                                    log.info("Reconciled paid payment link from Razorpay API: linkId={}", pl.getRazorpayLinkId());
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            auditService.log(
                    "SCHEDULER",
                    0L,
                    "RECONCILIATION_RUN",
                    "SYSTEM",
                    String.format("Reconciliation run completed successfully. Found %d events, ingested %d missing events.", foundCount, ingestedCount)
            );

            log.info("Webhook reconciliation job completed. Found {}, Ingested {}", foundCount, ingestedCount);

        } catch (Exception e) {
            log.error("Error during webhook reconciliation", e);
            auditService.log(
                    "SCHEDULER",
                    0L,
                    "RECONCILIATION_RUN_FAILED",
                    "SYSTEM",
                    "Reconciliation run failed: " + e.getMessage()
            );
        }
    }

    private String extractPaymentId(JsonNode root) {
        return com.recovermandate.util.WebhookPayloadUtils.extractPaymentId(root);
    }
}
