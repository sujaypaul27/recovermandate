package com.recovermandate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.*;
import com.recovermandate.repository.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private PaymentEventRepository paymentEventRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private FailureClassificationService failureClassificationService;

    @Mock
    private RecoveryActionService recoveryActionService;

    @Mock
    private AuditService auditService;

    @Mock
    private SseService sseService;

    @Mock
    private RetrySchedulerService retrySchedulerService;

    @Mock
    private com.recovermandate.repository.PaymentLinkRepository paymentLinkRepository;

    @Mock
    private com.recovermandate.repository.RecoveryActionRepository recoveryActionRepository;

    @Mock
    private com.recovermandate.repository.FailureClassificationRepository failureClassificationRepository;

    @Mock
    private com.recovermandate.repository.RetryScheduleRepository retryScheduleRepository;

    @Mock
    private org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private com.recovermandate.client.RazorpayApiClient razorpayApiClient;

    private ObjectMapper objectMapper;
    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        webhookService = new WebhookService(
                paymentEventRepository,
                subscriptionRepository,
                customerRepository,
                planRepository,
                merchantRepository,
                failureClassificationService,
                recoveryActionService,
                retrySchedulerService,
                auditService,
                sseService,
                objectMapper,
                paymentLinkRepository,
                recoveryActionRepository,
                failureClassificationRepository,
                retryScheduleRepository,
                applicationEventPublisher,
                razorpayApiClient
        );
    }

    @Test
    @DisplayName("Should ingest new PaymentEvent and create successful audit log")
    void handleVerifiedEvent_newPayment_successful() {
        String payload = """
                {
                  "entity": "event",
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_test_001",
                        "amount": 49900,
                        "subscription_id": "sub_test_001",
                        "error_code": "BAD_REQUEST_ERROR"
                      }
                    },
                    "subscription": {
                      "entity": {
                        "id": "sub_test_001",
                        "status": "active"
                      }
                    }
                  }
                }
                """;

        when(paymentEventRepository.findByRazorpayPaymentId("pay_test_001")).thenReturn(Optional.empty());
        when(subscriptionRepository.findByRazorpaySubscriptionId("sub_test_001"))
                .thenReturn(Optional.of(Subscription.builder().id(10L).razorpaySubscriptionId("sub_test_001").status("active").build()));

        PaymentEvent savedMock = PaymentEvent.builder()
                .id(1L)
                .razorpayPaymentId("pay_test_001")
                .eventType("payment.failed")
                .amount(49900L)
                .failureReasonCode("BAD_REQUEST_ERROR")
                .receivedAt(Instant.now())
                .rawPayload(payload)
                .build();

        when(paymentEventRepository.save(any(PaymentEvent.class))).thenReturn(savedMock);

        PaymentEvent result = webhookService.handleVerifiedEvent(payload);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("pay_test_001", result.getRazorpayPaymentId());
        assertEquals("payment.failed", result.getEventType());

        verify(paymentEventRepository).save(any(PaymentEvent.class));
        verify(auditService).log(
                eq("PAYMENT_EVENT"),
                eq(1L),
                eq("WEBHOOK_INGESTED"),
                eq("SYSTEM"),
                org.mockito.ArgumentMatchers.contains("pay_test_001")
        );
        verify(failureClassificationService, org.mockito.Mockito.times(1)).classify(savedMock);
    }

    @Test
    @DisplayName("Should invoke classify, scheduleRetries, and processFailure exactly once for payment.failed")
    void handleVerifiedEvent_paymentFailed_invokesPipelineExactlyOnce() {
        String payload = """
                {
                  "entity": "event",
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_once_101",
                        "amount": 49900,
                        "error_code": "BAD_REQUEST_ERROR"
                      }
                    }
                  }
                }
                """;

        PaymentEvent savedMock = PaymentEvent.builder()
                .id(101L)
                .razorpayPaymentId("pay_once_101")
                .eventType("payment.failed")
                .build();

        FailureClassification classification = new FailureClassification();
        classification.setId(1L);
        classification.setCategory("insufficient_funds");
        classification.setAutoRecoverable(false);

        when(paymentEventRepository.save(any(PaymentEvent.class))).thenReturn(savedMock);
        when(failureClassificationService.classify(savedMock)).thenReturn(classification);

        webhookService.handleVerifiedEvent(payload);

        verify(failureClassificationService, org.mockito.Mockito.times(1)).classify(savedMock);
        verify(retrySchedulerService, org.mockito.Mockito.times(1)).scheduleRetries(savedMock, classification);
        verify(recoveryActionService, org.mockito.Mockito.times(1)).processFailure(classification);
    }

    @Test
    @DisplayName("Should look up existing Merchant, Customer, and Plan by identifier when creating missing Subscription")
    void handleVerifiedEvent_createsSubscriptionWithSpecificIdentifierLookups() {
        String payload = """
                {
                  "entity": "event",
                  "account_id": "acc_specific_123",
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_test_002",
                        "amount": 25000,
                        "customer_id": "cust_specific_456",
                        "name": "Jane Doe",
                        "email": "jane.doe@example.com",
                        "contact": "+919876543210",
                        "subscription_id": "sub_specific_789"
                      }
                    },
                    "subscription": {
                      "entity": {
                        "id": "sub_specific_789",
                        "plan_id": "plan_specific_999",
                        "status": "active"
                      }
                    }
                  }
                }
                """;

        when(paymentEventRepository.findByRazorpayPaymentId("pay_test_002")).thenReturn(Optional.empty());
        when(subscriptionRepository.findByRazorpaySubscriptionId("sub_specific_789")).thenReturn(Optional.empty());

        Merchant existingMerchant = Merchant.builder().id(5L).razorpayAccountRef("acc_specific_123").name("Specific Merchant").build();
        Customer existingCustomer = Customer.builder().id(6L).razorpayCustomerId("cust_specific_456").name("Jane Doe").email("jane.doe@example.com").build();
        Plan existingPlan = Plan.builder().id(7L).razorpayPlanId("plan_specific_999").amount(25000L).currency("INR").interval("monthly").build();

        when(merchantRepository.findByRazorpayAccountRef("acc_specific_123")).thenReturn(Optional.of(existingMerchant));
        when(customerRepository.findByRazorpayCustomerId("cust_specific_456")).thenReturn(java.util.List.of(existingCustomer));
        when(planRepository.findByRazorpayPlanId("plan_specific_999")).thenReturn(Optional.of(existingPlan));

        Subscription savedSub = Subscription.builder()
                .id(20L)
                .razorpaySubscriptionId("sub_specific_789")
                .customer(existingCustomer)
                .plan(existingPlan)
                .status("active")
                .build();
        when(subscriptionRepository.save(any(Subscription.class))).thenReturn(savedSub);

        PaymentEvent savedPayment = PaymentEvent.builder().id(2L).razorpayPaymentId("pay_test_002").eventType("payment.failed").subscription(savedSub).build();
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenReturn(savedPayment);

        PaymentEvent result = webhookService.handleVerifiedEvent(payload);

        assertNotNull(result);
        verify(merchantRepository).findByRazorpayAccountRef("acc_specific_123");
        verify(customerRepository).findByRazorpayCustomerId("cust_specific_456");
        verify(planRepository).findByRazorpayPlanId("plan_specific_999");
        verify(subscriptionRepository).save(argThat(sub ->
                "sub_specific_789".equals(sub.getRazorpaySubscriptionId()) &&
                sub.getCustomer().getId().equals(6L) &&
                sub.getPlan().getId().equals(7L)
        ));
    }

    @Test
    @DisplayName("Should create new Merchant, Customer, and Plan with extracted identifiers and name (not contact) when not found")
    void handleVerifiedEvent_createsNewEntitiesWithExtractedIdentifiers() {
        String payload = """
                {
                  "entity": "event",
                  "account_id": "acc_brand_new",
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_test_003",
                        "amount": 15000,
                        "customer_id": "cust_brand_new",
                        "customer_name": "Alice Smith",
                        "email": "alice@example.com",
                        "contact": "+911122334455",
                        "subscription_id": "sub_brand_new"
                      }
                    },
                    "subscription": {
                      "entity": {
                        "id": "sub_brand_new",
                        "plan_id": "plan_brand_new",
                        "plan_amount": 15000,
                        "currency": "INR",
                        "interval": "monthly",
                        "status": "active"
                      }
                    }
                  }
                }
                """;

        when(paymentEventRepository.findByRazorpayPaymentId("pay_test_003")).thenReturn(Optional.empty());
        when(subscriptionRepository.findByRazorpaySubscriptionId("sub_brand_new")).thenReturn(Optional.empty());

        when(merchantRepository.findByRazorpayAccountRef("acc_brand_new")).thenReturn(Optional.empty());
        Merchant newMerchant = Merchant.builder().id(11L).name("Default Merchant").razorpayAccountRef("acc_brand_new").build();
        when(merchantRepository.save(any(Merchant.class))).thenReturn(newMerchant);

        when(customerRepository.findByRazorpayCustomerId("cust_brand_new")).thenReturn(java.util.List.of());
        Customer newCustomer = Customer.builder().id(12L).merchant(newMerchant).name("Alice Smith").email("alice@example.com").razorpayCustomerId("cust_brand_new").build();
        when(customerRepository.save(any(Customer.class))).thenReturn(newCustomer);

        when(planRepository.findByRazorpayPlanId("plan_brand_new")).thenReturn(Optional.empty());
        Plan newPlan = Plan.builder().id(13L).merchant(newMerchant).razorpayPlanId("plan_brand_new").amount(15000L).currency("INR").interval("monthly").build();
        when(planRepository.save(any(Plan.class))).thenReturn(newPlan);

        Subscription newSub = Subscription.builder().id(30L).razorpaySubscriptionId("sub_brand_new").customer(newCustomer).plan(newPlan).status("active").build();
        when(subscriptionRepository.save(any(Subscription.class))).thenReturn(newSub);

        PaymentEvent savedPayment = PaymentEvent.builder().id(3L).razorpayPaymentId("pay_test_003").eventType("payment.failed").subscription(newSub).build();
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenReturn(savedPayment);

        PaymentEvent result = webhookService.handleVerifiedEvent(payload);

        assertNotNull(result);
        verify(merchantRepository).save(argThat(m -> "acc_brand_new".equals(m.getRazorpayAccountRef())));
        verify(customerRepository).save(argThat(c ->
                "cust_brand_new".equals(c.getRazorpayCustomerId()) &&
                "Alice Smith".equals(c.getName()) &&
                "alice@example.com".equals(c.getEmail())
        ));
        verify(planRepository).save(argThat(p ->
                "plan_brand_new".equals(p.getRazorpayPlanId()) &&
                Long.valueOf(15000L).equals(p.getAmount())
        ));
    }

    @Test
    @DisplayName("Should ignore duplicate webhook when razorpayPaymentId already exists in DB")
    void handleVerifiedEvent_duplicatePayment_ignored() {
        String payload = """
                {
                  "entity": "event",
                  "event": "subscription.charged",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_test_duplicate",
                        "amount": 10000
                      }
                    }
                  }
                }
                """;

        PaymentEvent existing = PaymentEvent.builder()
                .id(99L)
                .razorpayPaymentId("pay_test_duplicate")
                .eventType("subscription.charged")
                .build();

        when(paymentEventRepository.findByRazorpayPaymentId("pay_test_duplicate"))
                .thenReturn(Optional.of(existing));

        PaymentEvent result = webhookService.handleVerifiedEvent(payload);

        assertNotNull(result);
        assertEquals(99L, result.getId());

        verify(paymentEventRepository, never()).save(any(PaymentEvent.class));
        verify(auditService).log(
                eq("PAYMENT_EVENT"),
                eq(99L),
                eq("DUPLICATE_WEBHOOK_IGNORED"),
                eq("SYSTEM"),
                org.mockito.ArgumentMatchers.contains("pay_test_duplicate")
        );
        verify(failureClassificationService, never()).classify(any());
    }

    @Test
    @DisplayName("Should not classify failure when eventType is not payment.failed")
    void handleVerifiedEvent_nonFailedEvent_skipsClassification() {
        String payload = """
                {
                  "entity": "event",
                  "event": "subscription.charged",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_charged_001",
                        "amount": 50000
                      }
                    }
                  }
                }
                """;

        when(paymentEventRepository.findByRazorpayPaymentId("pay_charged_001")).thenReturn(Optional.empty());

        PaymentEvent savedPayment = PaymentEvent.builder()
                .id(50L)
                .razorpayPaymentId("pay_charged_001")
                .eventType("subscription.charged")
                .amount(50000L)
                .receivedAt(Instant.now())
                .rawPayload(payload)
                .build();

        when(paymentEventRepository.save(any(PaymentEvent.class))).thenReturn(savedPayment);

        PaymentEvent result = webhookService.handleVerifiedEvent(payload);

        assertNotNull(result);
        assertEquals("subscription.charged", result.getEventType());
        verify(failureClassificationService, never()).classify(any());
    }

    @Test
    @DisplayName("recordInvalidSignature logs audit trail with SYSTEM actor")
    void recordInvalidSignature_logsAudit() {
        webhookService.recordInvalidSignature("{}", "bad_sig");

        verify(auditService).log(
                eq("WEBHOOK"),
                eq(0L),
                eq("INVALID_SIGNATURE"),
                eq("SYSTEM"),
                org.mockito.ArgumentMatchers.contains("signature verification failed")
        );
    }

    @Test
    @DisplayName("Should skip failure classification if subscription is halted")
    void handleVerifiedEvent_subscriptionHalted_skipsClassification() {
        String payload = """
                {
                  "entity": "event",
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_test_halted_001",
                        "amount": 49900,
                        "subscription_id": "sub_test_halted"
                      }
                    },
                    "subscription": {
                      "entity": {
                        "id": "sub_test_halted",
                        "status": "halted"
                      }
                    }
                  }
                }
                """;

        when(paymentEventRepository.findByRazorpayPaymentId("pay_test_halted_001")).thenReturn(Optional.empty());
        
        Subscription sub = Subscription.builder().id(20L).razorpaySubscriptionId("sub_test_halted").status("active").build();
        when(subscriptionRepository.findByRazorpaySubscriptionId("sub_test_halted"))
                .thenReturn(Optional.of(sub));
                
        PaymentEvent savedPayment = PaymentEvent.builder()
                .id(1L)
                .razorpayPaymentId("pay_test_halted_001")
                .eventType("payment.failed")
                .subscription(sub)
                .build();
                
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenReturn(savedPayment);

        PaymentEvent result = webhookService.handleVerifiedEvent(payload);

        assertNotNull(result);
        assertEquals("halted", sub.getStatus()); // Should have updated the status
        verify(subscriptionRepository).save(sub); // Should have saved the updated sub
        
        verify(failureClassificationService, never()).classify(any());
        
        verify(auditService).log(
                eq("SUBSCRIPTION"),
                eq(20L),
                eq("SUBSCRIPTION_HALTED_SKIPPED"),
                eq("SYSTEM"),
                org.mockito.ArgumentMatchers.contains("halted")
        );
    }

    @Test
    @DisplayName("Should reject stale webhook older than replay window")
    void handleVerifiedEvent_staleWebhook_rejected() {
        long oldTimestamp = Instant.now().getEpochSecond() - 600; // 10 minutes ago (> 300s)
        String payload = """
                {
                  "entity": "event",
                  "event": "payment.failed",
                  "created_at": %d,
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_stale_001",
                        "amount": 49900
                      }
                    }
                  }
                }
                """.formatted(oldTimestamp);

        PaymentEvent result = webhookService.handleVerifiedEvent(payload);

        org.junit.jupiter.api.Assertions.assertNull(result);
        verify(paymentEventRepository, never()).save(any());
        verify(auditService).log(
                eq("WEBHOOK"),
                eq(0L),
                eq("STALE_WEBHOOK_REJECTED"),
                eq("SYSTEM"),
                org.mockito.ArgumentMatchers.contains("exceeds replay window")
        );
    }

    @Test
    @DisplayName("Should accept fresh webhook with recent created_at timestamp")
    void handleVerifiedEvent_freshWebhook_accepted() {
        long recentTimestamp = Instant.now().getEpochSecond() - 10; // 10 seconds ago (< 300s)
        String payload = """
                {
                  "entity": "event",
                  "event": "payment.failed",
                  "created_at": %d,
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_fresh_001",
                        "amount": 49900
                      }
                    }
                  }
                }
                """.formatted(recentTimestamp);

        when(paymentEventRepository.findByRazorpayPaymentId("pay_fresh_001")).thenReturn(Optional.empty());
        PaymentEvent savedPayment = PaymentEvent.builder()
                .id(999L)
                .razorpayPaymentId("pay_fresh_001")
                .eventType("payment.failed")
                .build();
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenReturn(savedPayment);

        PaymentEvent result = webhookService.handleVerifiedEvent(payload);

        assertNotNull(result);
        assertEquals(999L, result.getId());
        verify(paymentEventRepository).save(any(PaymentEvent.class));
    }

    @Test
    @DisplayName("Should handle payment_link.paid event, mark RecoveryAction as RECOVERED, and cancel pending retries")
    void handleVerifiedEvent_paymentLinkPaid_marksRecoveredAndCancelsRetries() {
        String payload = """
                {
                  "entity": "event",
                  "event": "payment_link.paid",
                  "payload": {
                    "payment_link": {
                      "entity": {
                        "id": "plink_test_123",
                        "amount": 75000,
                        "status": "paid"
                      }
                    }
                  }
                }
                """;

        PaymentEvent event = PaymentEvent.builder().id(777L).build();
        com.recovermandate.entity.FailureClassification classification = com.recovermandate.entity.FailureClassification.builder()
                .id(888L)
                .paymentEvent(event)
                .build();

        com.recovermandate.entity.RecoveryAction action = com.recovermandate.entity.RecoveryAction.builder()
                .id(42L)
                .status("DISPATCHED")
                .failureClassification(classification)
                .build();

        com.recovermandate.entity.PaymentLink link = com.recovermandate.entity.PaymentLink.builder()
                .id(10L)
                .razorpayLinkId("plink_test_123")
                .amount(75000L)
                .status("CREATED")
                .recoveryAction(action)
                .build();

        com.recovermandate.entity.RetrySchedule pendingRetry = com.recovermandate.entity.RetrySchedule.builder()
                .id(101L)
                .paymentEvent(event)
                .attemptNumber(1)
                .result("PENDING")
                .build();

        when(paymentLinkRepository.findByRazorpayLinkId("plink_test_123")).thenReturn(Optional.of(link));
        when(retryScheduleRepository.findByPaymentEventIdAndResult(777L, "PENDING")).thenReturn(java.util.List.of(pendingRetry));
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(i -> {
            PaymentEvent pe = i.getArgument(0);
            pe.setId(555L);
            return pe;
        });

        PaymentEvent result = webhookService.handleVerifiedEvent(payload);

        assertNotNull(result);
        assertEquals("PAID", link.getStatus());
        assertNotNull(link.getPaidAt());
        assertEquals("RECOVERED", action.getStatus());
        assertEquals("SKIPPED", pendingRetry.getResult());
        assertEquals("SUPERSEDED_BY_LINK_PAYMENT", pendingRetry.getScheduleReason());

        verify(paymentLinkRepository).save(link);
        verify(recoveryActionRepository).save(action);
        verify(retryScheduleRepository).save(pendingRetry);
        verify(auditService).log(eq("RECOVERY_ACTION"), eq(42L), eq("PAYMENT_RECOVERED"), eq("SYSTEM"), anyString());
        verify(auditService).log(eq("RETRY_SCHEDULE"), eq(101L), eq("RETRY_CANCELLED_ALREADY_PAID"), eq("SYSTEM"), contains("plink_test_123"));
        verify(sseService).broadcast(eq("recovery.completed"), argThat((java.util.Map<String, Object> map) ->
                map.containsKey("actionId") && Long.valueOf(75000L).equals(map.get("amount"))
        ));
    }

    @Test
    @DisplayName("Should handle payment_link.expired event and mark PaymentLink and RecoveryAction as EXPIRED")
    void handleVerifiedEvent_paymentLinkExpired_marksExpired() {
        String payload = """
                {
                  "entity": "event",
                  "event": "payment_link.expired",
                  "payload": {
                    "payment_link": {
                      "entity": {
                        "id": "plink_expired_999",
                        "status": "expired"
                      }
                    }
                  }
                }
                """;

        com.recovermandate.entity.RecoveryAction action = com.recovermandate.entity.RecoveryAction.builder()
                .id(43L)
                .status("DISPATCHED")
                .build();

        com.recovermandate.entity.PaymentLink link = com.recovermandate.entity.PaymentLink.builder()
                .id(20L)
                .razorpayLinkId("plink_expired_999")
                .amount(50000L)
                .status("CREATED")
                .recoveryAction(action)
                .build();

        when(paymentLinkRepository.findByRazorpayLinkId("plink_expired_999")).thenReturn(Optional.of(link));
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(i -> {
            PaymentEvent pe = i.getArgument(0);
            pe.setId(666L);
            return pe;
        });

        PaymentEvent result = webhookService.handleVerifiedEvent(payload);

        assertNotNull(result);
        assertEquals("EXPIRED", link.getStatus());
        assertEquals("LINK_EXPIRED", action.getStatus());
        verify(paymentLinkRepository).save(link);
        verify(recoveryActionRepository).save(action);
        verify(auditService).log(eq("PAYMENT_LINK"), eq(20L), eq("PAYMENT_LINK_EXPIRED"), eq("SYSTEM"), anyString());
        verify(auditService).log(eq("RECOVERY_ACTION"), eq(43L), eq("PAYMENT_LINK_EXPIRED_UNRECOVERED"), eq("SYSTEM"), anyString());
    }

    @Test
    @DisplayName("Should handle subscription.cancelled event and cancel pending retries")
    void handleVerifiedEvent_subscriptionCancelled_cancelsPendingRetries() {
        String payload = """
                {
                  "entity": "event",
                  "event": "subscription.cancelled",
                  "payload": {
                    "subscription": {
                      "entity": {
                        "id": "sub_cancel_123",
                        "status": "cancelled"
                      }
                    }
                  }
                }
                """;

        Subscription sub = Subscription.builder()
                .id(999L)
                .razorpaySubscriptionId("sub_cancel_123")
                .status("active")
                .build();

        com.recovermandate.entity.RetrySchedule pendingRetry = com.recovermandate.entity.RetrySchedule.builder()
                .id(202L)
                .attemptNumber(1)
                .result("PENDING")
                .build();

        when(subscriptionRepository.findByRazorpaySubscriptionId("sub_cancel_123")).thenReturn(Optional.of(sub));
        when(retryScheduleRepository.findByPaymentEventSubscriptionIdAndResult(999L, "PENDING"))
                .thenReturn(java.util.List.of(pendingRetry));
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(i -> {
            PaymentEvent pe = i.getArgument(0);
            pe.setId(888L);
            return pe;
        });

        PaymentEvent result = webhookService.handleVerifiedEvent(payload);

        assertNotNull(result);
        assertEquals("cancelled", sub.getStatus());
        assertEquals("SKIPPED", pendingRetry.getResult());
        assertEquals("SUBSCRIPTION_CANCELLED", pendingRetry.getScheduleReason());
        verify(subscriptionRepository, atLeastOnce()).save(sub);
        verify(retryScheduleRepository).save(pendingRetry);
        verify(auditService).log(eq("RETRY_SCHEDULE"), eq(202L), eq("RETRY_SKIPPED_SUBSCRIPTION_INACTIVE"), eq("SYSTEM"), contains("cancelled"));
        verify(auditService).log(eq("SUBSCRIPTION"), eq(999L), eq("SUBSCRIPTION_STATUS_UPDATED"), eq("SYSTEM"), contains("cancelled"));
    }

    @Test
    @DisplayName("Should extract customer email and create mandate subscription from real Razorpay test payment webhook")
    void handleVerifiedEvent_realRazorpayWebhookWithoutSubscription_extractsCustomerEmail() {
        String payload = """
                {
                  "entity": "event",
                  "account_id": "acc_real_razorpay",
                  "event": "payment.failed",
                  "contains": ["payment"],
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_real_test_001",
                        "amount": 79900,
                        "currency": "INR",
                        "status": "failed",
                        "email": "ssupport@gmail.com",
                        "contact": "+919876543210",
                        "notes": {
                          "customer_email": "ssupport@gmail.com"
                        },
                        "error_code": "BAD_REQUEST_ERROR",
                        "error_reason": "payment_failed_due_to_insufficient_funds"
                      }
                    }
                  }
                }
                """;

        when(paymentEventRepository.findByRazorpayPaymentId("pay_real_test_001")).thenReturn(Optional.empty());
        when(merchantRepository.findByRazorpayAccountRef("acc_real_razorpay")).thenReturn(Optional.empty());
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(i -> i.getArgument(0));

        when(customerRepository.findByRazorpayCustomerId(anyString())).thenReturn(java.util.List.of());
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> {
            Customer c = i.getArgument(0);
            c.setId(101L);
            return c;
        });

        when(planRepository.findByRazorpayPlanId(anyString())).thenReturn(Optional.empty());
        when(planRepository.save(any(Plan.class))).thenAnswer(i -> i.getArgument(0));

        when(subscriptionRepository.findByRazorpaySubscriptionId(anyString())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> {
            Subscription s = i.getArgument(0);
            s.setId(201L);
            return s;
        });

        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(i -> {
            PaymentEvent pe = i.getArgument(0);
            pe.setId(301L);
            return pe;
        });

        PaymentEvent event = webhookService.handleVerifiedEvent(payload);

        assertNotNull(event);
        assertNotNull(event.getSubscription());
        assertNotNull(event.getSubscription().getCustomer());
        assertEquals("ssupport@gmail.com", event.getSubscription().getCustomer().getEmail());
        assertEquals(79900L, event.getAmount());
    }

    @Test
    @DisplayName("Should prioritize subscriber customer_email from subscription entity over merchant payment email")
    void extractCustomerEmail_prioritizesSubscriptionCustomerEmailOverPaymentEmail() throws Exception {
        String json = """
                {
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_test_123",
                        "email": "merchant.owner@business.com",
                        "amount": 49900
                      }
                    },
                    "subscription": {
                      "entity": {
                        "id": "sub_test_123",
                        "customer_email": "actual.subscriber@gmail.com"
                      }
                    }
                  }
                }
                """;
        var root = objectMapper.readTree(json);
        var paymentEntity = root.path("payload").path("payment").path("entity");
        var subscriptionEntity = root.path("payload").path("subscription").path("entity");

        String extracted = WebhookService.extractCustomerEmail(root, paymentEntity, subscriptionEntity);
        assertEquals("actual.subscriber@gmail.com", extracted);
    }

    @Test
    @DisplayName("Should prioritize customer object email from subscription entity over payment email")
    void extractCustomerEmail_prioritizesSubscriptionCustomerObjectOverPaymentEmail() throws Exception {
        String json = """
                {
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_test_123",
                        "email": "merchant.owner@business.com",
                        "amount": 49900
                      }
                    },
                    "subscription": {
                      "entity": {
                        "id": "sub_test_123",
                        "customer": {
                          "email": "actual.subscriber.customer@gmail.com",
                          "name": "Jane Subscriber"
                        }
                      }
                    }
                  }
                }
                """;
        var root = objectMapper.readTree(json);
        var paymentEntity = root.path("payload").path("payment").path("entity");
        var subscriptionEntity = root.path("payload").path("subscription").path("entity");

        String extracted = WebhookService.extractCustomerEmail(root, paymentEntity, subscriptionEntity);
        assertEquals("actual.subscriber.customer@gmail.com", extracted);
    }

    @Test
    @DisplayName("Should prioritize customer entity email over payment email")
    void extractCustomerEmail_prioritizesCustomerEntityOverPaymentEmail() throws Exception {
        String json = """
                {
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_test_123",
                        "email": "merchant.owner@business.com"
                      }
                    },
                    "customer": {
                      "entity": {
                        "id": "cust_123",
                        "email": "subscriber.direct@domain.com"
                      }
                    }
                  }
                }
                """;
        var root = objectMapper.readTree(json);
        var paymentEntity = root.path("payload").path("payment").path("entity");
        var subscriptionEntity = root.path("payload").path("subscription").path("entity");

        String extracted = WebhookService.extractCustomerEmail(root, paymentEntity, subscriptionEntity);
        assertEquals("subscriber.direct@domain.com", extracted);
    }

    @Test
    @DisplayName("Should fall back to payment email when no subscription or customer entity email exists")
    void extractCustomerEmail_fallsBackToPaymentEmailWhenNoSubscriptionEmail() throws Exception {
        String json = """
                {
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_test_123",
                        "email": "direct.payer@domain.com"
                      }
                    }
                  }
                }
                """;
        var root = objectMapper.readTree(json);
        var paymentEntity = root.path("payload").path("payment").path("entity");
        var subscriptionEntity = root.path("payload").path("subscription").path("entity");

        String extracted = WebhookService.extractCustomerEmail(root, paymentEntity, subscriptionEntity);
        assertEquals("direct.payer@domain.com", extracted);
    }

    @Test
    @DisplayName("Should create distinct customer and not mutate existing customer when a different email is received")
    void handleVerifiedEvent_createsDistinctCustomersPerEmail() {
        Customer existingCustomer = Customer.builder()
                .id(1L)
                .name("Alice First")
                .email("alice.first@example.com")
                .razorpayCustomerId("cust_shared_id")
                .build();

        when(customerRepository.findByMerchantAndEmailOrderByIdDesc(any(), eq("bob.second@example.com"))).thenReturn(java.util.List.of());
        when(customerRepository.findByEmailOrderByIdDesc("bob.second@example.com")).thenReturn(java.util.List.of());
        when(customerRepository.findByRazorpayCustomerId("cust_shared_id")).thenReturn(java.util.List.of(existingCustomer));

        Customer newBobCustomer = Customer.builder()
                .id(2L)
                .name("Bob Second")
                .email("bob.second@example.com")
                .razorpayCustomerId("cust_shared_id")
                .build();
        when(customerRepository.save(argThat(c -> "bob.second@example.com".equals(c.getEmail())))).thenReturn(newBobCustomer);

        String payload = """
                {
                  "entity": "event",
                  "account_id": "acc_real_razorpay",
                  "event": "payment.failed",
                  "contains": ["payment", "subscription"],
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_test_002",
                        "amount": 49900,
                        "currency": "INR",
                        "customer_id": "cust_shared_id",
                        "email": "bob.second@example.com"
                      }
                    },
                    "subscription": {
                      "entity": {
                        "id": "sub_test_002",
                        "customer_email": "bob.second@example.com"
                      }
                    }
                  }
                }
                """;

        when(paymentEventRepository.findByRazorpayPaymentId("pay_test_002")).thenReturn(Optional.empty());
        when(merchantRepository.findByRazorpayAccountRef(anyString())).thenReturn(Optional.of(Merchant.builder().id(1L).name("M").build()));
        when(planRepository.findByRazorpayPlanId(anyString())).thenReturn(Optional.of(Plan.builder().id(1L).build()));
        when(subscriptionRepository.findByRazorpaySubscriptionId("sub_test_002")).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(i -> {
            PaymentEvent pe = i.getArgument(0);
            pe.setId(301L);
            return pe;
        });

        PaymentEvent event = webhookService.handleVerifiedEvent(payload);

        assertNotNull(event);
        assertNotNull(event.getSubscription().getCustomer());
        assertEquals("bob.second@example.com", event.getSubscription().getCustomer().getEmail());
        // Verify that existing Alice record was NOT mutated
        assertEquals("alice.first@example.com", existingCustomer.getEmail());
    }

    @Test
    @DisplayName("Should select most recent customer when duplicate customer rows exist for the same email without throwing")
    void handleVerifiedEvent_withDuplicateCustomers_picksMostRecent() {
        Customer olderCustomer = Customer.builder().id(10L).email("duplicate@example.com").name("Old Name").build();
        Customer newerCustomer = Customer.builder().id(20L).email("duplicate@example.com").name("Newer Name").build();

        when(customerRepository.findByMerchantAndEmailOrderByIdDesc(any(), eq("duplicate@example.com")))
                .thenReturn(java.util.List.of(newerCustomer, olderCustomer));

        String payload = """
                {
                  "entity": "event",
                  "account_id": "acc_real_razorpay",
                  "event": "payment.failed",
                  "contains": ["payment"],
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_test_dup_001",
                        "amount": 10000,
                        "currency": "INR",
                        "email": "duplicate@example.com"
                      }
                    }
                  }
                }
                """;

        when(paymentEventRepository.findByRazorpayPaymentId("pay_test_dup_001")).thenReturn(Optional.empty());
        when(merchantRepository.findByRazorpayAccountRef(anyString())).thenReturn(Optional.of(Merchant.builder().id(1L).name("M").build()));
        when(planRepository.findByRazorpayPlanId(anyString())).thenReturn(Optional.of(Plan.builder().id(1L).build()));
        when(subscriptionRepository.findByRazorpaySubscriptionId(anyString())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(i -> {
            PaymentEvent pe = i.getArgument(0);
            pe.setId(401L);
            return pe;
        });

        PaymentEvent event = webhookService.handleVerifiedEvent(payload);

        assertNotNull(event);
        assertNotNull(event.getSubscription().getCustomer());
        assertEquals(20L, event.getSubscription().getCustomer().getId());
        assertEquals("duplicate@example.com", event.getSubscription().getCustomer().getEmail());
    }

    @Test
    @DisplayName("Should detect placeholder and void emails correctly")
    void isPlaceholderOrVoidEmail_detectsVoidEmails() {
        org.junit.jupiter.api.Assertions.assertTrue(WebhookService.isPlaceholderOrVoidEmail("void@razorpay.com"));
        org.junit.jupiter.api.Assertions.assertTrue(WebhookService.isPlaceholderOrVoidEmail("test@razorpay.com"));
        org.junit.jupiter.api.Assertions.assertTrue(WebhookService.isPlaceholderOrVoidEmail("void@customdomain.com"));
        org.junit.jupiter.api.Assertions.assertTrue(WebhookService.isPlaceholderOrVoidEmail("dummy@domain.com"));
        org.junit.jupiter.api.Assertions.assertTrue(WebhookService.isPlaceholderOrVoidEmail("noreply@company.com"));
        org.junit.jupiter.api.Assertions.assertTrue(WebhookService.isPlaceholderOrVoidEmail(null));
        org.junit.jupiter.api.Assertions.assertTrue(WebhookService.isPlaceholderOrVoidEmail("   "));
        org.junit.jupiter.api.Assertions.assertTrue(WebhookService.isPlaceholderOrVoidEmail("null"));

        org.junit.jupiter.api.Assertions.assertFalse(WebhookService.isPlaceholderOrVoidEmail("rsiv.ece2024@rmd.ac.in"));
        org.junit.jupiter.api.Assertions.assertFalse(WebhookService.isPlaceholderOrVoidEmail("sujaypaul2711@gmail.com"));
        org.junit.jupiter.api.Assertions.assertFalse(WebhookService.isPlaceholderOrVoidEmail("customer@example.com"));
    }

    @Test
    @DisplayName("Should resolve original customer email when payment.failed payload contains void@razorpay.com on a payment link")
    void handleVerifiedEvent_withVoidEmailOnPaymentLink_resolvesOriginalCustomer() {
        Customer realCustomer = Customer.builder()
                .id(101L)
                .name("Rsiv ece2024")
                .email("rsiv.ece2024@rmd.ac.in")
                .razorpayCustomerId("cust_rsiv_101")
                .build();

        Subscription realSub = Subscription.builder()
                .id(201L)
                .razorpaySubscriptionId("sub_orig_mandate_001")
                .customer(realCustomer)
                .status("active")
                .build();

        PaymentEvent origEvent = PaymentEvent.builder()
                .id(301L)
                .razorpayPaymentId("pay_orig_001")
                .subscription(realSub)
                .amount(49900L)
                .build();

        FailureClassification fc = FailureClassification.builder()
                .id(401L)
                .paymentEvent(origEvent)
                .category("insufficient_funds")
                .autoRecoverable(false)
                .build();

        RecoveryAction ra = RecoveryAction.builder()
                .id(501L)
                .failureClassification(fc)
                .status("DISPATCHED")
                .build();

        PaymentLink pl = PaymentLink.builder()
                .id(601L)
                .razorpayLinkId("plink_Q2wE3r4t5y")
                .shortUrl("https://rzp.io/rzp/abc123")
                .recoveryAction(ra)
                .amount(49900L)
                .status("CREATED")
                .build();

        when(paymentLinkRepository.findByRazorpayLinkId("plink_Q2wE3r4t5y")).thenReturn(Optional.of(pl));

        String payload = """
                {
                  "entity": "event",
                  "account_id": "acc_live_123",
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_declined_attempt_999",
                        "amount": 49900,
                        "currency": "INR",
                        "status": "failed",
                        "order_id": "order_Q2wE3r4t5yOrder",
                        "payment_link_id": "plink_Q2wE3r4t5y",
                        "email": "void@razorpay.com",
                        "error_code": "BAD_REQUEST_ERROR",
                        "error_description": "Payment was declined by the bank"
                      }
                    }
                  }
                }
                """;

        when(paymentEventRepository.findByRazorpayPaymentId("pay_declined_attempt_999")).thenReturn(Optional.empty());
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(i -> {
            PaymentEvent pe = i.getArgument(0);
            pe.setId(701L);
            return pe;
        });

        PaymentEvent processedEvent = webhookService.handleVerifiedEvent(payload);

        assertNotNull(processedEvent);
        assertNotNull(processedEvent.getSubscription());
        assertNotNull(processedEvent.getSubscription().getCustomer());
        assertEquals("rsiv.ece2024@rmd.ac.in", processedEvent.getSubscription().getCustomer().getEmail());
        assertEquals("Rsiv ece2024", processedEvent.getSubscription().getCustomer().getName());
    }

    @Test
    @DisplayName("Should resolve original customer via reference_id when payment.failed has void@razorpay.com")
    void handleVerifiedEvent_withVoidEmailAndReferenceId_resolvesOriginalCustomer() {
        Customer realCustomer = Customer.builder()
                .id(102L)
                .name("Sujaypaul2711")
                .email("sujaypaul2711@gmail.com")
                .razorpayCustomerId("cust_sujay_102")
                .build();

        Subscription realSub = Subscription.builder()
                .id(202L)
                .razorpaySubscriptionId("sub_sujay_002")
                .customer(realCustomer)
                .status("active")
                .build();

        PaymentEvent origEvent = PaymentEvent.builder()
                .id(302L)
                .razorpayPaymentId("pay_TwP6eyEHCg8c0p")
                .subscription(realSub)
                .amount(49900L)
                .build();

        when(paymentEventRepository.findByRazorpayPaymentId("pay_TwP6eyEHCg8c0p")).thenReturn(Optional.of(origEvent));

        String payload = """
                {
                  "entity": "event",
                  "account_id": "acc_live_123",
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_rejected_link_888",
                        "amount": 49900,
                        "currency": "INR",
                        "status": "failed",
                        "email": "void@razorpay.com",
                        "notes": {
                          "reference_id": "rec_link_pay_TwP6eyEHCg8c0p_987654"
                        },
                        "error_code": "BAD_REQUEST_ERROR"
                      }
                    }
                  }
                }
                """;

        when(paymentEventRepository.findByRazorpayPaymentId("pay_rejected_link_888")).thenReturn(Optional.empty());
        when(paymentEventRepository.save(any(PaymentEvent.class))).thenAnswer(i -> {
            PaymentEvent pe = i.getArgument(0);
            pe.setId(702L);
            return pe;
        });

        PaymentEvent processedEvent = webhookService.handleVerifiedEvent(payload);

        assertNotNull(processedEvent);
        assertNotNull(processedEvent.getSubscription());
        assertNotNull(processedEvent.getSubscription().getCustomer());
        assertEquals("sujaypaul2711@gmail.com", processedEvent.getSubscription().getCustomer().getEmail());
        assertEquals("Sujaypaul2711", processedEvent.getSubscription().getCustomer().getName());
    }
}
