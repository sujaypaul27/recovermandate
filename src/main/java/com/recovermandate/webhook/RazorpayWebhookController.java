package com.recovermandate.webhook;

import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller to receive Razorpay webhook notifications.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final RazorpaySignatureVerifier signatureVerifier;
    private final WebhookService webhookService;

    /**
     * Receives and processes Razorpay webhook events.
     *
     * @param payload   raw request body as a String
     * @param signature X-Razorpay-Signature header value
     * @return 200 OK if valid and processed, 400 BAD REQUEST if signature is invalid
     */
    @PostMapping("/razorpay")
    public ResponseEntity<String> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        log.info("Received Razorpay webhook request");

        // Verify signature
        if (!signatureVerifier.verify(payload, signature)) {
            log.warn("Rejected Razorpay webhook due to invalid signature");
            webhookService.recordInvalidSignature(payload, signature);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        // Process verified webhook payload
        PaymentEvent paymentEvent = webhookService.handleVerifiedEvent(payload);

        // Return 200 OK immediately — Razorpay requires response within 5s
        if (paymentEvent == null) {
            return ResponseEntity.ok("Webhook rejected or ignored");
        }

        return ResponseEntity.ok("Webhook processed successfully with event id: " + paymentEvent.getId());
    }
}
