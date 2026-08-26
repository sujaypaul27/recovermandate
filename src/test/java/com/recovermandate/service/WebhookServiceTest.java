package com.recovermandate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.Customer;
import com.recovermandate.entity.Merchant;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.Plan;
import com.recovermandate.entity.Subscription;
import com.recovermandate.repository.CustomerRepository;
import com.recovermandate.repository.MerchantRepository;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.repository.PlanRepository;
import com.recovermandate.repository.SubscriptionRepository;
import java.time.Instant;
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
                auditService,
                objectMapper
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
        verify(failureClassificationService).classify(savedMock);
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
        when(customerRepository.findByRazorpayCustomerId("cust_specific_456")).thenReturn(Optional.of(existingCustomer));
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

        when(customerRepository.findByRazorpayCustomerId("cust_brand_new")).thenReturn(Optional.empty());
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
}
