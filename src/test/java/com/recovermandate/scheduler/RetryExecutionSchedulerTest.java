package com.recovermandate.scheduler;

import com.recovermandate.audit.AuditService;
import com.recovermandate.client.RazorpayApiClient;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.PaymentLink;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.entity.RetrySchedule;
import com.recovermandate.entity.Subscription;
import com.recovermandate.repository.PaymentLinkRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import com.recovermandate.repository.RetryScheduleRepository;
import com.recovermandate.service.BankHealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryExecutionSchedulerTest {

    @Mock
    private RetryScheduleRepository retryScheduleRepository;

    @Mock
    private BankHealthService bankHealthService;

    @Mock
    private AuditService auditService;

    @Mock
    private PaymentLinkRepository paymentLinkRepository;

    @Mock
    private RecoveryActionRepository recoveryActionRepository;

    @Mock
    private RazorpayApiClient razorpayApiClient;

    @Mock
    private Random random;

    private RetryExecutionScheduler retryExecutionScheduler;

    @BeforeEach
    void setUp() {
        retryExecutionScheduler = new RetryExecutionScheduler(
                retryScheduleRepository,
                bankHealthService,
                auditService,
                paymentLinkRepository,
                recoveryActionRepository,
                razorpayApiClient,
                random
        );
    }

    @Test
    @DisplayName("Should defer retry by 60 minutes if issuer bank is DOWN")
    void executeDueRetries_defersWhenDown() {
        PaymentEvent event = new PaymentEvent();
        event.setId(5L);

        Instant originalScheduledAt = Instant.now().minusSeconds(10);
        RetrySchedule retry = RetrySchedule.builder()
                .id(1L)
                .paymentEvent(event)
                .attemptNumber(1)
                .result("PENDING")
                .scheduledAt(originalScheduledAt)
                .build();

        when(retryScheduleRepository.findByResultAndScheduledAtLessThanEqual(eq("PENDING"), any(), any()))
                .thenReturn(List.of(retry));
        when(bankHealthService.extractBankCode(event)).thenReturn("HDFC");
        when(bankHealthService.getBankHealth("HDFC")).thenReturn("DOWN");

        retryExecutionScheduler.executeDueRetries();

        assertEquals("PENDING", retry.getResult());
        assertTrue(retry.getScheduledAt().isAfter(originalScheduledAt));
        assertEquals("RETRY_DEFERRED_BANK_HDFC_DOWN", retry.getScheduleReason());
        verify(retryScheduleRepository).save(retry);
        verify(auditService).log(eq("RETRY_SCHEDULE"), eq(1L), eq("RETRY_DEFERRED_BANK_OUTAGE"), eq("SYSTEM"), contains("DOWN"));
    }

    @Test
    @DisplayName("Should execute retry successfully when bank is HEALTHY and supersede active payment link")
    void executeDueRetries_executesWhenHealthyAndSupersedesLink() {
        PaymentEvent event = new PaymentEvent();
        event.setId(6L);

        RetrySchedule retry = RetrySchedule.builder()
                .id(2L)
                .paymentEvent(event)
                .attemptNumber(1)
                .result("PENDING")
                .scheduledAt(Instant.now().minusSeconds(10))
                .build();

        RecoveryAction action = RecoveryAction.builder().id(10L).status("DRAFTED").build();
        PaymentLink link = PaymentLink.builder().id(20L).razorpayLinkId("plink_123").status("DISPATCHED").build();

        when(retryScheduleRepository.findByResultAndScheduledAtLessThanEqual(eq("PENDING"), any(), any()))
                .thenReturn(List.of(retry));
        when(recoveryActionRepository.findByFailureClassificationPaymentEvent(event)).thenReturn(Optional.of(action));
        when(paymentLinkRepository.findByRecoveryAction(action)).thenReturn(Optional.of(link));
        when(bankHealthService.extractBankCode(event)).thenReturn("ICICI");
        when(bankHealthService.getBankHealth("ICICI")).thenReturn("HEALTHY");
        when(random.nextDouble()).thenReturn(0.50); // > 0.30 failure threshold -> SUCCESS

        retryExecutionScheduler.executeDueRetries();

        assertEquals("SUCCESS", retry.getResult());
        assertNotNull(retry.getExecutedAt());
        assertNotNull(retry.getRazorpayRetryPaymentId());
        assertEquals("SUPERSEDED", link.getStatus());
        verify(razorpayApiClient).cancelPaymentLink("plink_123");
        verify(paymentLinkRepository).save(link);
        verify(retryScheduleRepository).save(retry);
        verify(auditService).log(eq("RETRY_SCHEDULE"), eq(2L), eq("RETRY_EXECUTED_SUCCESS"), eq("SYSTEM"), contains("HEALTHY"));
        verify(auditService).log(eq("PAYMENT_LINK"), eq(20L), eq("PAYMENT_LINK_SUPERSEDED_BY_RETRY"), eq("SYSTEM"), contains("SUPERSEDED"));
    }

    @Test
    @DisplayName("Should skip retry if mandate is already recovered via payment link (double-charge prevention)")
    void executeDueRetries_skipsWhenAlreadyRecovered() {
        PaymentEvent event = new PaymentEvent();
        event.setId(8L);

        RetrySchedule retry = RetrySchedule.builder()
                .id(4L)
                .paymentEvent(event)
                .attemptNumber(1)
                .result("PENDING")
                .scheduledAt(Instant.now().minusSeconds(10))
                .build();

        RecoveryAction action = RecoveryAction.builder().id(11L).status("RECOVERED").build();

        when(retryScheduleRepository.findByResultAndScheduledAtLessThanEqual(eq("PENDING"), any(), any()))
                .thenReturn(List.of(retry));
        when(recoveryActionRepository.findByFailureClassificationPaymentEvent(event)).thenReturn(Optional.of(action));

        retryExecutionScheduler.executeDueRetries();

        assertEquals("SKIPPED", retry.getResult());
        assertEquals("SUPERSEDED_BY_LINK_PAYMENT", retry.getScheduleReason());
        verify(retryScheduleRepository).save(retry);
        verify(auditService).log(eq("RETRY_SCHEDULE"), eq(4L), eq("RETRY_CANCELLED_ALREADY_PAID"), eq("SYSTEM"), contains("already recovered"));
        verifyNoInteractions(bankHealthService);
    }

    @Test
    @DisplayName("Should skip retry if subscription is cancelled or paused")
    void executeDueRetries_skipsWhenSubscriptionCancelled() {
        Subscription sub = Subscription.builder().id(99L).status("cancelled").build();
        PaymentEvent event = PaymentEvent.builder().id(9L).subscription(sub).build();

        RetrySchedule retry = RetrySchedule.builder()
                .id(5L)
                .paymentEvent(event)
                .attemptNumber(1)
                .result("PENDING")
                .scheduledAt(Instant.now().minusSeconds(10))
                .build();

        when(retryScheduleRepository.findByResultAndScheduledAtLessThanEqual(eq("PENDING"), any(), any()))
                .thenReturn(List.of(retry));

        retryExecutionScheduler.executeDueRetries();

        assertEquals("SKIPPED", retry.getResult());
        assertEquals("SUBSCRIPTION_CANCELLED", retry.getScheduleReason());
        verify(retryScheduleRepository).save(retry);
        verify(auditService).log(eq("RETRY_SCHEDULE"), eq(5L), eq("RETRY_SKIPPED_SUBSCRIPTION_INACTIVE"), eq("SYSTEM"), contains("cancelled"));
        verifyNoInteractions(bankHealthService);
    }

    @Test
    @DisplayName("Should record FAILED retry when simulated failure occurs")
    void executeDueRetries_recordsFailureWhenSimulated() {
        PaymentEvent event = new PaymentEvent();
        event.setId(7L);

        RetrySchedule retry = RetrySchedule.builder()
                .id(3L)
                .paymentEvent(event)
                .attemptNumber(2)
                .result("PENDING")
                .scheduledAt(Instant.now().minusSeconds(10))
                .build();

        when(retryScheduleRepository.findByResultAndScheduledAtLessThanEqual(eq("PENDING"), any(), any()))
                .thenReturn(List.of(retry));
        when(bankHealthService.extractBankCode(event)).thenReturn("SBI");
        when(bankHealthService.getBankHealth("SBI")).thenReturn("DEGRADED");
        when(random.nextDouble()).thenReturn(0.20); // < 0.65 failure threshold -> FAILED

        retryExecutionScheduler.executeDueRetries();

        assertEquals("FAILED", retry.getResult());
        assertNotNull(retry.getExecutedAt());
        assertNull(retry.getRazorpayRetryPaymentId());
        verify(retryScheduleRepository).save(retry);
        verify(auditService).log(eq("RETRY_SCHEDULE"), eq(3L), eq("RETRY_EXECUTED_FAILED"), eq("SYSTEM"), contains("DEGRADED"));
    }
}
