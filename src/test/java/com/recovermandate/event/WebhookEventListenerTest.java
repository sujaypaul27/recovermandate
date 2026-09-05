package com.recovermandate.event;

import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.service.FailureClassificationService;
import com.recovermandate.service.RecoveryActionService;
import com.recovermandate.service.RetrySchedulerService;
import com.recovermandate.service.SseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookEventListenerTest {

    @Mock
    private FailureClassificationService failureClassificationService;

    @Mock
    private RetrySchedulerService retrySchedulerService;

    @Mock
    private RecoveryActionService recoveryActionService;

    @Mock
    private SseService sseService;

    @Mock
    private PaymentEventRepository paymentEventRepository;

    @InjectMocks
    private WebhookEventListener webhookEventListener;

    @Test
    @DisplayName("REGRESSION GUARD: onPaymentFailed must have @TransactionalEventListener(phase = AFTER_COMMIT)")
    void regressionGuard_verifyTransactionalEventListenerAnnotation() throws NoSuchMethodException {
        Method method = WebhookEventListener.class.getMethod("onPaymentFailed", PaymentFailedEvent.class);

        // Verify @TransactionalEventListener is present
        TransactionalEventListener tel = method.getAnnotation(TransactionalEventListener.class);
        assertNotNull(tel, "WebhookEventListener.onPaymentFailed must be annotated with @TransactionalEventListener");
        assertEquals(TransactionPhase.AFTER_COMMIT, tel.phase(), "phase must be TransactionPhase.AFTER_COMMIT to prevent foreign key race conditions");
        assertTrue(tel.fallbackExecution(), "fallbackExecution must be true to allow execution in non-transactional test contexts");

        // Verify @Async is present
        Async async = method.getAnnotation(Async.class);
        assertNotNull(async, "WebhookEventListener.onPaymentFailed must be annotated with @Async");

        // Verify @Transactional(propagation = Propagation.REQUIRES_NEW)
        org.springframework.transaction.annotation.Transactional tx =
                method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertNotNull(tx, "WebhookEventListener.onPaymentFailed must be annotated with @Transactional");
        assertEquals(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, tx.propagation(),
                "@Transactional must use Propagation.REQUIRES_NEW to be valid on @TransactionalEventListener(AFTER_COMMIT)");
    }

    @Test
    @DisplayName("Should successfully classify, schedule retries, and trigger recovery action for non-auto-recoverable failure")
    void onPaymentFailed_nonAutoRecoverable_processesSuccessfully() {
        PaymentEvent paymentEvent = PaymentEvent.builder()
                .id(44L)
                .razorpayPaymentId("pay_test_race_44")
                .eventType("payment.failed")
                .build();

        PaymentFailedEvent event = new PaymentFailedEvent(this, paymentEvent);

        FailureClassification classification = new FailureClassification();
        classification.setId(10L);
        classification.setCategory("insufficient_funds");
        classification.setAutoRecoverable(false);

        when(paymentEventRepository.findById(44L)).thenReturn(Optional.of(paymentEvent));
        when(failureClassificationService.classify(paymentEvent)).thenReturn(classification);

        webhookEventListener.onPaymentFailed(event);

        verify(paymentEventRepository).findById(44L);
        verify(failureClassificationService).classify(paymentEvent);
        verify(sseService).broadcast(eq("classification.complete"), any());
        verify(retrySchedulerService).scheduleRetries(paymentEvent, classification);
        verify(recoveryActionService).processFailure(classification);
    }

    @Test
    @DisplayName("Should not trigger recovery action when failure is auto-recoverable (technical decline)")
    void onPaymentFailed_autoRecoverable_skipsRecoveryAction() {
        PaymentEvent paymentEvent = PaymentEvent.builder()
                .id(45L)
                .razorpayPaymentId("pay_test_tech_45")
                .eventType("payment.failed")
                .build();

        PaymentFailedEvent event = new PaymentFailedEvent(this, paymentEvent);

        FailureClassification classification = new FailureClassification();
        classification.setId(11L);
        classification.setCategory("technical_decline");
        classification.setAutoRecoverable(true);

        when(paymentEventRepository.findById(45L)).thenReturn(Optional.of(paymentEvent));
        when(failureClassificationService.classify(paymentEvent)).thenReturn(classification);

        webhookEventListener.onPaymentFailed(event);

        verify(paymentEventRepository).findById(45L);
        verify(failureClassificationService).classify(paymentEvent);
        verify(sseService).broadcast(eq("classification.complete"), any());
        verify(retrySchedulerService).scheduleRetries(paymentEvent, classification);
        verify(recoveryActionService, never()).processFailure(any());
    }

    @Test
    @DisplayName("Should safely ignore null event or event without persisted ID")
    void onPaymentFailed_nullOrUnsaved_isNoOp() {
        webhookEventListener.onPaymentFailed(new PaymentFailedEvent(this, null));
        webhookEventListener.onPaymentFailed(new PaymentFailedEvent(this, new PaymentEvent()));

        verifyNoInteractions(paymentEventRepository);
        verifyNoInteractions(failureClassificationService);
        verifyNoInteractions(retrySchedulerService);
        verifyNoInteractions(recoveryActionService);
    }
}
