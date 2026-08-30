package com.recovermandate.event;

import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.service.FailureClassificationService;
import com.recovermandate.service.RecoveryActionService;
import com.recovermandate.service.RetrySchedulerService;
import com.recovermandate.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Asynchronous listener for ingested webhook events.
 * Executes classification, smart retry scheduling, and AI draft generation
 * decoupled from the incoming webhook HTTP transaction lock window.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventListener {

    private final FailureClassificationService failureClassificationService;
    private final RetrySchedulerService retrySchedulerService;
    private final RecoveryActionService recoveryActionService;
    private final SseService sseService;

    @Async
    @EventListener
    public void onPaymentFailed(PaymentFailedEvent event) {
        PaymentEvent paymentEvent = event.getPaymentEvent();
        if (paymentEvent == null) {
            return;
        }

        log.info("Processing asynchronous PaymentFailedEvent for eventId={}, paymentId={}",
                paymentEvent.getId(), paymentEvent.getRazorpayPaymentId());

        try {
            FailureClassification classification = failureClassificationService.classify(paymentEvent);
            if (classification != null) {
                sseService.broadcast("classification.complete", Map.of(
                        "eventId", paymentEvent.getId(),
                        "category", classification.getCategory()
                ));

                // Schedule automated smart retries
                retrySchedulerService.scheduleRetries(paymentEvent, classification);

                // Process AI recovery action draft if not auto-recoverable
                if (!classification.isAutoRecoverable()) {
                    recoveryActionService.processFailure(classification);
                }
            }
        } catch (Exception e) {
            log.error("Error processing asynchronous PaymentFailedEvent for event id={}", paymentEvent.getId(), e);
        }
    }
}
