package com.recovermandate.service;

import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.Customer;
import com.recovermandate.entity.DispatchLog;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.entity.Subscription;
import com.recovermandate.mail.EmailSendResult;
import com.recovermandate.mail.EmailService;
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

    @Mock
    private EmailService emailService;

    @InjectMocks
    private DispatchService dispatchService;

    @Test
    @DisplayName("Should dispatch recovery email via real SMTP, log dispatch and broadcast SSE")
    void dispatchRecovery_realSmtp_success() {
        Customer customer = new Customer();
        customer.setName("Alice Wonderland");
        customer.setEmail("alice@acme.com");

        Subscription subscription = new Subscription();
        subscription.setCustomer(customer);

        PaymentEvent event = new PaymentEvent();
        event.setAmount(49900L);
        event.setSubscription(subscription);

        FailureClassification classification = new FailureClassification();
        classification.setPaymentEvent(event);

        RecoveryAction action = new RecoveryAction();
        action.setId(10L);
        action.setAiDraftMessage("Hello Alice, please update payment details.");
        action.setFailureClassification(classification);

        when(emailService.sendRecoveryEmail(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(EmailSendResult.realSent("smtp-msg-12345"));
        when(dispatchLogRepository.save(any(DispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        DispatchLog log = dispatchService.dispatchRecovery(action, "https://rzp.io/l/alicePay");

        assertNotNull(log);
        assertEquals("EMAIL", log.getChannel());
        assertEquals("alice@acme.com", log.getRecipient());
        assertEquals("SENT", log.getStatus());
        assertEquals("smtp-msg-12345", log.getProviderMessageId());

        verify(emailService).sendRecoveryEmail(
                eq("alice@acme.com"),
                eq("Alice Wonderland"),
                anyString(),
                eq("Hello Alice, please update payment details."),
                eq("https://rzp.io/l/alicePay"),
                eq(49900L),
                eq("INR")
        );

        verify(auditService).log(
                eq("RECOVERY_ACTION"),
                eq(10L),
                eq("RECOVERY_DISPATCHED"),
                eq("SYSTEM"),
                contains("REAL_SMTP")
        );

        verify(sseService).broadcast(eq("recovery.dispatched"), anyMap());
    }

    @Test
    @DisplayName("Should gracefully dispatch in simulated mode when SMTP credentials are unconfigured")
    void dispatchRecovery_simulated_success() {
        Customer customer = new Customer();
        customer.setEmail("bob@acme.com");

        Subscription subscription = new Subscription();
        subscription.setCustomer(customer);

        PaymentEvent event = new PaymentEvent();
        event.setSubscription(subscription);

        FailureClassification classification = new FailureClassification();
        classification.setPaymentEvent(event);

        RecoveryAction action = new RecoveryAction();
        action.setId(12L);
        action.setFailureClassification(classification);

        when(emailService.sendRecoveryEmail(anyString(), any(), anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(EmailSendResult.simulated("sim-msg-999"));
        when(dispatchLogRepository.save(any(DispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        DispatchLog log = dispatchService.dispatchRecovery(action, "https://rzp.io/l/bobPay");

        assertNotNull(log);
        assertEquals("SENT", log.getStatus());
        assertEquals("sim-msg-999", log.getProviderMessageId());

        verify(auditService).log(
                eq("RECOVERY_ACTION"),
                eq(12L),
                eq("RECOVERY_DISPATCHED"),
                eq("SYSTEM"),
                contains("SIMULATED")
        );
    }

    @Test
    @DisplayName("Should mark DISPATCH_FAILED when real email sending encounters SMTP error")
    void dispatchRecovery_smtpFailure_marksDispatchFailed() {
        Customer customer = new Customer();
        customer.setEmail("charlie@acme.com");

        Subscription subscription = new Subscription();
        subscription.setCustomer(customer);

        PaymentEvent event = new PaymentEvent();
        event.setSubscription(subscription);

        FailureClassification classification = new FailureClassification();
        classification.setPaymentEvent(event);

        RecoveryAction action = new RecoveryAction();
        action.setId(15L);
        action.setFailureClassification(classification);

        when(emailService.sendRecoveryEmail(anyString(), any(), anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(EmailSendResult.failed("SMTP Error: 535 Authentication failed"));
        when(dispatchLogRepository.save(any(DispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        DispatchLog log = dispatchService.dispatchRecovery(action, "https://rzp.io/l/charliePay");

        assertNotNull(log);
        assertEquals("DISPATCH_FAILED", log.getStatus());
        assertEquals("SMTP Error: 535 Authentication failed", log.getErrorDetail());

        verify(auditService).log(
                eq("RECOVERY_ACTION"),
                eq(15L),
                eq("DISPATCH_FAILED"),
                eq("SYSTEM"),
                contains("535 Authentication failed")
        );

        verify(sseService).broadcast(eq("recovery.dispatch_failed"), anyMap());
    }

    @Test
    @DisplayName("Should mark DISPATCH_FAILED and skip sending when customer email is missing")
    void dispatchRecovery_missingEmail_fails() {
        PaymentEvent event = new PaymentEvent();
        event.setSubscription(null); // No subscription -> no customer -> no email

        FailureClassification classification = new FailureClassification();
        classification.setPaymentEvent(event);

        RecoveryAction action = new RecoveryAction();
        action.setId(11L);
        action.setFailureClassification(classification);

        when(dispatchLogRepository.save(any(DispatchLog.class))).thenAnswer(i -> i.getArgument(0));

        DispatchLog log = dispatchService.dispatchRecovery(action, "https://rzp.io/l/noEmail");

        assertNotNull(log);
        assertEquals("DISPATCH_FAILED", log.getStatus());
        assertEquals("UNKNOWN", log.getRecipient());
        assertEquals("missing customer email", log.getErrorDetail());

        verify(auditService).log(
                eq("RECOVERY_ACTION"),
                eq(11L),
                eq("DISPATCH_FAILED"),
                eq("SYSTEM"),
                contains("missing customer email")
        );

        verify(emailService, never()).sendRecoveryEmail(any(), any(), any(), any(), any(), any(), any());
        verify(sseService, never()).broadcast(anyString(), any());
    }
}
