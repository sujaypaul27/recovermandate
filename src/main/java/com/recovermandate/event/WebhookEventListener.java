package com.recovermandate.event;

import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.service.FailureClassificationService;
import com.recovermandate.service.RecoveryActionService;
import com.recovermandate.service.RetrySchedulerService;
import com.recovermandate.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Asynchronous listener for ingested webhook events.
 * Executes classification, smart retry scheduling, and AI draft generation
 * decoupled from the incoming webhook HTTP transaction lock window.
 *
 * <p>Guaranteed to execute ONLY AFTER the transaction creating the PaymentEvent has committed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventListener {

    private final FailureClassificationService failureClassificationService;
    private final RetrySchedulerService retrySchedulerService;
    private final RecoveryActionService recoveryActionService;
    private final SseService sseService;
    private final PaymentEventRepository paymentEventRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentFailed(PaymentFailedEvent event) {
        PaymentEvent paymentEvent = event.getPaymentEvent();
        if (paymentEvent == null || paymentEvent.getId() == null) {
            return;
        }

        log.info("Processing asynchronous PaymentFailedEvent AFTER_COMMIT for eventId={}, paymentId={}",
                paymentEvent.getId(), paymentEvent.getRazorpayPaymentId());

        try {
            // Ensure entity is cleanly attached to this async thread's transaction context
            PaymentEvent managedEvent = paymentEventRepository.findById(paymentEvent.getId())
                    .orElse(paymentEvent);

            FailureClassification classification = failureClassificationService.classify(managedEvent);
            if (classification != null) {
                sseService.broadcast("classification.complete", Map.of(
                        "eventId", managedEvent.getId(),
                        "category", classification.getCategory()
                ));

                // Schedule automated smart retries
                retrySchedulerService.scheduleRetries(managedEvent, classification);

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
