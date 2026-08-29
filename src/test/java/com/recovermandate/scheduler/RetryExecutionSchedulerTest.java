package com.recovermandate.scheduler;

import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.RetrySchedule;
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
    private Random random;

    private RetryExecutionScheduler retryExecutionScheduler;

    @BeforeEach
    void setUp() {
        retryExecutionScheduler = new RetryExecutionScheduler(
                retryScheduleRepository,
                bankHealthService,
                auditService,
                random
        );
    }

    @Test
    @DisplayName("Should skip retry if issuer bank is DOWN")
    void executeDueRetries_skipsWhenDown() {
        PaymentEvent event = new PaymentEvent();
        event.setId(5L);

        RetrySchedule retry = RetrySchedule.builder()
                .id(1L)
                .paymentEvent(event)
                .attemptNumber(1)
                .result("PENDING")
                .scheduledAt(Instant.now().minusSeconds(10))
                .build();

        when(retryScheduleRepository.findByResultAndScheduledAtLessThanEqual(eq("PENDING"), any(), any()))
                .thenReturn(List.of(retry));
        when(bankHealthService.extractBankCode(event)).thenReturn("HDFC");
        when(bankHealthService.getBankHealth("HDFC")).thenReturn("DOWN");

        retryExecutionScheduler.executeDueRetries();

        assertEquals("SKIPPED", retry.getResult());
        assertNotNull(retry.getExecutedAt());
        verify(retryScheduleRepository).save(retry);
        verify(auditService).log(eq("RETRY_SCHEDULE"), eq(1L), eq("RETRY_SKIPPED_BANK_DOWN"), eq("SYSTEM"), contains("DOWN"));
    }

    @Test
    @DisplayName("Should execute retry successfully when bank is HEALTHY and simulation succeeds")
    void executeDueRetries_executesWhenHealthy() {
        PaymentEvent event = new PaymentEvent();
        event.setId(6L);

        RetrySchedule retry = RetrySchedule.builder()
                .id(2L)
                .paymentEvent(event)
                .attemptNumber(1)
                .result("PENDING")
                .scheduledAt(Instant.now().minusSeconds(10))
                .build();

        when(retryScheduleRepository.findByResultAndScheduledAtLessThanEqual(eq("PENDING"), any(), any()))
                .thenReturn(List.of(retry));
        when(bankHealthService.extractBankCode(event)).thenReturn("ICICI");
        when(bankHealthService.getBankHealth("ICICI")).thenReturn("HEALTHY");
        when(random.nextDouble()).thenReturn(0.50); // > 0.30 failure threshold -> SUCCESS

        retryExecutionScheduler.executeDueRetries();

        assertEquals("SUCCESS", retry.getResult());
        assertNotNull(retry.getExecutedAt());
        assertNotNull(retry.getRazorpayRetryPaymentId());
        verify(retryScheduleRepository).save(retry);
        verify(auditService).log(eq("RETRY_SCHEDULE"), eq(2L), eq("RETRY_EXECUTED_SUCCESS"), eq("SYSTEM"), contains("HEALTHY"));
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
