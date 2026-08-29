package com.recovermandate.service;

import com.recovermandate.audit.AuditService;
import com.recovermandate.client.RazorpayApiClient;
import com.recovermandate.entity.*;
import com.recovermandate.repository.PaymentLinkRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentLinkServiceTest {

    @Mock
    private RazorpayApiClient razorpayApiClient;

    @Mock
    private PaymentLinkRepository paymentLinkRepository;

    @Mock
    private RecoveryActionRepository recoveryActionRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PaymentLinkService paymentLinkService;

    @Test
    @DisplayName("Should create payment link, attach to action and audit")
    void createLinkForRecoveryAction_success() {
        Customer customer = new Customer();
        customer.setEmail("customer@test.com");
        customer.setName("John Doe");

        Plan plan = new Plan();
        plan.setCurrency("INR");

        Subscription subscription = new Subscription();
        subscription.setCustomer(customer);
        subscription.setPlan(plan);

        PaymentEvent event = new PaymentEvent();
        event.setAmount(10000L);
        event.setSubscription(subscription);

        FailureClassification classification = new FailureClassification();
        classification.setPaymentEvent(event);

        RecoveryAction action = new RecoveryAction();
        action.setId(42L);
        action.setFailureClassification(classification);

        when(razorpayApiClient.createPaymentLink(eq(10000L), eq("INR"), eq("customer@test.com"), eq("John Doe"), anyString(), any(), eq("rec_link_act_42")))
                .thenReturn(Map.of("id", "plink_test_123", "short_url", "https://rzp.io/l/test1234"));

        when(paymentLinkRepository.save(any(PaymentLink.class))).thenAnswer(i -> i.getArgument(0));
        when(recoveryActionRepository.save(any(RecoveryAction.class))).thenAnswer(i -> i.getArgument(0));

        PaymentLink link = paymentLinkService.createLinkForRecoveryAction(action);

        assertNotNull(link);
        assertEquals("plink_test_123", link.getRazorpayLinkId());
        assertEquals("https://rzp.io/l/test1234", link.getShortUrl());
        assertEquals("CREATED", link.getStatus());
        assertEquals(10000L, link.getAmount());
        assertEquals("https://rzp.io/l/test1234", action.getPaymentLinkUrl());

        verify(auditService).log(
                eq("RECOVERY_ACTION"),
                eq(42L),
                eq("PAYMENT_LINK_CREATED"),
                eq("SYSTEM"),
                contains("plink_test_123")
        );
    }
}
