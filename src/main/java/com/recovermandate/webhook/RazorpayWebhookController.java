package com.recovermandate.webhook;

import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.WebhookDlq;
import com.recovermandate.repository.WebhookDlqRepository;
import com.recovermandate.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller to receive Razorpay webhook notifications, manage Dead-Letter Queue (DLQ),
 * and provide forensic replay capabilities for rejected or malformed webhooks.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RazorpaySignatureVerifier signatureVerifier;
    private final WebhookService webhookService;
    private final WebhookDlqRepository webhookDlqRepository;

    /**
     * Receives and processes Razorpay webhook events.
     *
     * @param payload   raw request body as a String
     * @param signature X-Razorpay-Signature header value
     * @return 200 OK if valid and processed, 400 BAD REQUEST if signature is invalid (saved to DLQ)
     */
    @PostMapping("/razorpay")
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        log.info("Received Razorpay webhook request");

        // 1. Verify signature
        if (!signatureVerifier.verify(payload, signature)) {
            log.warn("Rejected Razorpay webhook due to invalid signature, persisting to DLQ");
            webhookService.recordInvalidSignature(payload, signature);

            WebhookDlq dlq = WebhookDlq.builder()
                    .payload(payload)
                    .headers("X-Razorpay-Signature: " + (signature != null ? signature : "MISSING"))
                    .errorMessage("Invalid HMAC-SHA256 signature verification")
                    .status("REJECTED")
                    .createdAt(Instant.now())
                    .build();
            webhookDlqRepository.save(dlq);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        // 2. Process verified webhook payload with DLQ error safety net
        try {
            PaymentEvent paymentEvent = webhookService.handleVerifiedEvent(payload);

            if (paymentEvent == null) {
                return ResponseEntity.ok("Webhook rejected or ignored");
            }

            return ResponseEntity.ok("Webhook processed successfully with event id: " + paymentEvent.getId());
        } catch (Exception e) {
            if (isDuplicateViolation(e)) {
                log.info("Concurrent duplicate webhook delivery acknowledged idempotently as 200 OK");
                return ResponseEntity.ok("Webhook already processed (concurrent duplicate acknowledged)");
            }

            log.error("Unhandled error processing verified webhook, routing to DLQ: {}", e.getMessage(), e);

            WebhookDlq dlq = WebhookDlq.builder()
                    .payload(payload)
                    .headers("X-Razorpay-Signature: " + (signature != null ? signature : "VERIFIED"))
                    .errorMessage("Processing failure: " + e.getMessage())
                    .status("REJECTED")
                    .createdAt(Instant.now())
                    .build();
            webhookDlqRepository.save(dlq);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing failed; routed to DLQ: " + e.getMessage());
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
     * Lists all captured DLQ webhooks for inspection.
     */
    @GetMapping("/dlq")
    public ResponseEntity<List<WebhookDlq>> getDlqEvents() {
        List<WebhookDlq> events = webhookDlqRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(events);
    }

    /**
     * Replays a specific webhook payload through the processing pipeline.
     */
    @PostMapping("/dlq/{id}/replay")
    public ResponseEntity<?> replayDlqEvent(@PathVariable Long id) {
        log.info("Request received to replay DLQ event id={}", id);

        Optional<WebhookDlq> dlqOpt = webhookDlqRepository.findById(id);
        if (dlqOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DLQ event not found with id " + id));
        }

        WebhookDlq dlq = dlqOpt.get();
        try {
            PaymentEvent event = webhookService.handleVerifiedEvent(dlq.getPayload());

            dlq.setStatus("REPLAYED");
            dlq.setReplayedAt(Instant.now());
            webhookDlqRepository.save(dlq);

            log.info("Successfully replayed DLQ event id={}, produced event id={}", id, event != null ? event.getId() : "null");

            return ResponseEntity.ok(Map.of(
                    "status", "REPLAYED",
                    "dlqId", id,
                    "eventId", event != null ? event.getId() : 0L,
                    "message", "DLQ webhook successfully replayed into recovery pipeline"
            ));
        } catch (Exception e) {
            log.error("Failed to replay DLQ event id={}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Replay execution failed: " + e.getMessage(),
                    "dlqId", id
            ));
        }
    }

    /**
     * Deletes a specific DLQ event.
     */
    @DeleteMapping("/dlq/{id}")
    public ResponseEntity<?> deleteDlqEvent(@PathVariable Long id) {
        if (webhookDlqRepository.existsById(id)) {
            webhookDlqRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "DLQ event " + id + " deleted", "id", id));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "DLQ event not found with id " + id));
    }
}
