package com.recovermandate.service;

import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.Customer;
import com.recovermandate.entity.DispatchLog;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.entity.Subscription;
import com.recovermandate.repository.DispatchLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private DispatchLogRepository dispatchLogRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private SseService sseService;

    @InjectMocks
    private DispatchService dispatchService;

    @Test
    @DisplayName("Should dispatch recovery email, log dispatch and broadcast SSE")
    void dispatchRecovery_success() {
        Customer customer = new Customer();
        customer.setEmail("alice@acme.com");

        Subscription subscription = new Subscription();
        subscription.setCustomer(customer);

        PaymentEvent event = new PaymentEvent();
        event.setSubscription(subscription);

        FailureClassification classification = new FailureClassification();
        classification.setPaymentEvent(event);

        RecoveryAction action = new RecoveryAction();
        action.setId(10L);
        action.setFailureClassification(classification);

        when(dispatchLogRepository.save(any(DispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        DispatchLog log = dispatchService.dispatchRecovery(action, "https://rzp.io/l/alicePay");

        assertNotNull(log);
        assertEquals("EMAIL", log.getChannel());
        assertEquals("alice@acme.com", log.getRecipient());
        assertEquals("SENT", log.getStatus());

        verify(auditService).log(
                eq("RECOVERY_ACTION"),
                eq(10L),
                eq("RECOVERY_DISPATCHED"),
                eq("SYSTEM"),
                contains("ali***@acme.com")
        );

        verify(sseService).broadcast(eq("recovery.dispatched"), anyMap());
    }
}
