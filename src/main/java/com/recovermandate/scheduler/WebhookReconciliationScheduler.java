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

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookReconciliationScheduler {

    private final RazorpayApiClient razorpayApiClient;
    private final WebhookService webhookService;
    private final PaymentEventRepository paymentEventRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

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
                JsonNode root = objectMapper.readTree(rawEvent);
                String paymentId = extractPaymentId(root);
                
                if (paymentId != null && !paymentId.isBlank()) {
                    boolean exists = paymentEventRepository.findByRazorpayPaymentId(paymentId).isPresent();
                    if (!exists) {
                        log.info("Missing payment event found during reconciliation, ingesting: {}", paymentId);
                        webhookService.handleVerifiedEvent(rawEvent);
                        ingestedCount++;
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
