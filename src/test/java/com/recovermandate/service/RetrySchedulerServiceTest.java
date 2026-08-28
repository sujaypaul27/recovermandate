package com.recovermandate.service;

import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.RetrySchedule;
import com.recovermandate.repository.RetryScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrySchedulerServiceTest {

    @Mock
    private RetryScheduleRepository retryScheduleRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private RetrySchedulerService retrySchedulerService;

    @Test
    @DisplayName("Should schedule 3 retries for insufficient funds (Day 1, 3, 7)")
    void scheduleRetries_insufficientFunds() {
        PaymentEvent event = new PaymentEvent();
        event.setId(101L);

        FailureClassification classification = new FailureClassification();
        classification.setCategory(FailureClassificationService.CATEGORY_INSUFFICIENT_FUNDS);

        when(retryScheduleRepository.save(any(RetrySchedule.class))).thenAnswer(i -> i.getArgument(0));

        List<RetrySchedule> schedules = retrySchedulerService.scheduleRetries(event, classification);

        assertEquals(3, schedules.size());
        assertEquals(1, schedules.get(0).getAttemptNumber());
        assertEquals(2, schedules.get(1).getAttemptNumber());
        assertEquals(3, schedules.get(2).getAttemptNumber());
        assertEquals("PENDING", schedules.get(0).getResult());

        verify(auditService).log(
                eq("PAYMENT_EVENT"),
                eq(101L),
                eq("RETRY_SCHEDULED"),
                eq("SYSTEM"),
                contains("insufficient_funds")
        );
    }

    @Test
    @DisplayName("Should schedule 3 retries for technical decline (5min, 30min, 2hr)")
    void scheduleRetries_technicalDecline() {
        PaymentEvent event = new PaymentEvent();
        event.setId(102L);

        FailureClassification classification = new FailureClassification();
        classification.setCategory(FailureClassificationService.CATEGORY_TECHNICAL_DECLINE);

        when(retryScheduleRepository.save(any(RetrySchedule.class))).thenAnswer(i -> i.getArgument(0));

        List<RetrySchedule> schedules = retrySchedulerService.scheduleRetries(event, classification);

        assertEquals(3, schedules.size());
    }

    @Test
    @DisplayName("Should schedule 0 retries for expired mandate")
    void scheduleRetries_expiredMandate() {
        PaymentEvent event = new PaymentEvent();
        event.setId(103L);

        FailureClassification classification = new FailureClassification();
        classification.setCategory(FailureClassificationService.CATEGORY_EXPIRED_MANDATE);

        List<RetrySchedule> schedules = retrySchedulerService.scheduleRetries(event, classification);

        assertTrue(schedules.isEmpty());
        verify(retryScheduleRepository, never()).save(any());
    }
}
