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
import java.util.Optional;

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

        when(paymentLinkRepository.findByRecoveryActionId(42L)).thenReturn(Optional.empty());
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

    @Test
    @DisplayName("Should return existing payment link without calling Razorpay API when link already exists")
    void createLinkForRecoveryAction_idempotentWhenAlreadyExists() {
        RecoveryAction action = new RecoveryAction();
        action.setId(30L);

        PaymentLink existing = PaymentLink.builder()
                .id(100L)
                .recoveryAction(action)
                .razorpayLinkId("plink_existing_30")
                .shortUrl("https://rzp.io/l/existingLink30")
                .status("CREATED")
                .build();

        when(paymentLinkRepository.findByRecoveryActionId(30L)).thenReturn(java.util.Optional.of(existing));

        PaymentLink result = paymentLinkService.createLinkForRecoveryAction(action);

        assertNotNull(result);
        assertEquals("plink_existing_30", result.getRazorpayLinkId());
        assertEquals("https://rzp.io/l/existingLink30", result.getShortUrl());
        assertEquals("https://rzp.io/l/existingLink30", action.getPaymentLinkUrl());

        verify(razorpayApiClient, never()).createPaymentLink(any(), any(), any(), any(), any(), any(), any());
        verify(paymentLinkRepository, never()).save(any());
        verify(recoveryActionRepository).save(action);
    }

    @Test
    @DisplayName("Should fall back to local demo link when Razorpay API fails in test mode")
    void createLinkForRecoveryAction_fallbackInTestMode() {
        Customer customer = new Customer();
        customer.setEmail("customer@test.com");
        customer.setName("John Doe");

        PaymentEvent event = new PaymentEvent();
        event.setAmount(10000L);

        FailureClassification classification = new FailureClassification();
        classification.setPaymentEvent(event);

        RecoveryAction action = new RecoveryAction();
        action.setId(77L);
        action.setFailureClassification(classification);

        when(paymentLinkRepository.findByRecoveryActionId(77L)).thenReturn(Optional.empty());
        when(razorpayApiClient.isLiveMode()).thenReturn(false);
        when(razorpayApiClient.getFrontendUrl()).thenReturn("http://localhost:5173");
        when(razorpayApiClient.createPaymentLink(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Test quota reached"));

        when(paymentLinkRepository.save(any(PaymentLink.class))).thenAnswer(i -> i.getArgument(0));
        when(recoveryActionRepository.save(any(RecoveryAction.class))).thenAnswer(i -> i.getArgument(0));

        PaymentLink link = paymentLinkService.createLinkForRecoveryAction(action);

        assertNotNull(link);
        assertTrue(link.getRazorpayLinkId().startsWith("plink_quota_"));
        assertTrue(link.getShortUrl().startsWith("http://localhost:5173/#/pay/plink_quota_"));
        assertEquals("CREATED", link.getStatus());
        assertEquals(link.getShortUrl(), action.getPaymentLinkUrl());
    }

    @Test
    @DisplayName("Should generate local preview demo link and skip Razorpay API when isDemoData is true")
    void createLinkForRecoveryAction_demoData_skipsRazorpayApi() {
        Customer customer = new Customer();
        customer.setEmail("sujaypaul2711@gmail.com");
        customer.setName("Sujay Paul");

        PaymentEvent event = new PaymentEvent();
        event.setAmount(49900L);
        event.setDemoData(true);

        FailureClassification classification = new FailureClassification();
        classification.setPaymentEvent(event);

        RecoveryAction action = new RecoveryAction();
        action.setId(88L);
        action.setDemoData(true);
        action.setFailureClassification(classification);

        when(paymentLinkRepository.findByRecoveryActionId(88L)).thenReturn(Optional.empty());
        when(razorpayApiClient.getFrontendUrl()).thenReturn("http://localhost:5173");
        when(paymentLinkRepository.save(any(PaymentLink.class))).thenAnswer(i -> i.getArgument(0));
        when(recoveryActionRepository.save(any(RecoveryAction.class))).thenAnswer(i -> i.getArgument(0));

        PaymentLink link = paymentLinkService.createLinkForRecoveryAction(action);

        assertNotNull(link);
        assertTrue(link.isDemoData());
        assertEquals("demo_88", link.getRazorpayLinkId());
        assertEquals("http://localhost:5173/#/pay/demo_88", link.getShortUrl());
        assertEquals("CREATED", link.getStatus());
        assertEquals(49900L, link.getAmount());
        assertEquals(link.getShortUrl(), action.getPaymentLinkUrl());

        // Strictly verify Razorpay API was never called
        verify(razorpayApiClient, never()).createPaymentLink(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should generate local preview demo link and skip Razorpay API when dryRun is true")
    void createLinkForRecoveryAction_dryRun_skipsRazorpayApi() {
        PaymentEvent event = new PaymentEvent();
        event.setAmount(99900L);
        event.setDemoData(false);

        FailureClassification classification = new FailureClassification();
        classification.setPaymentEvent(event);

        RecoveryAction action = new RecoveryAction();
        action.setId(99L);
        action.setDemoData(false);
        action.setFailureClassification(classification);

        paymentLinkService.setDryRun(true);
        try {
            when(paymentLinkRepository.findByRecoveryActionId(99L)).thenReturn(Optional.empty());
            when(razorpayApiClient.getFrontendUrl()).thenReturn("http://localhost:5173");
            when(paymentLinkRepository.save(any(PaymentLink.class))).thenAnswer(i -> i.getArgument(0));
            when(recoveryActionRepository.save(any(RecoveryAction.class))).thenAnswer(i -> i.getArgument(0));

            PaymentLink link = paymentLinkService.createLinkForRecoveryAction(action);

            assertNotNull(link);
            assertTrue(link.isDemoData());
            assertEquals("demo_99", link.getRazorpayLinkId());
            assertEquals("http://localhost:5173/#/pay/demo_99", link.getShortUrl());

            // Strictly verify Razorpay API was never called
            verify(razorpayApiClient, never()).createPaymentLink(any(), any(), any(), any(), any(), any(), any());
        } finally {
            paymentLinkService.setDryRun(false);
        }
    }
}
