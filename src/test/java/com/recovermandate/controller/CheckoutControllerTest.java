package com.recovermandate.controller;

import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.Customer;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.PaymentLink;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.entity.Subscription;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.repository.PaymentLinkRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import com.recovermandate.repository.RetryScheduleRepository;
import com.recovermandate.repository.SubscriptionRepository;
import com.recovermandate.service.MerchantSettingsService;
import com.recovermandate.service.SseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CheckoutController.class)
@AutoConfigureMockMvc(addFilters = false)
class CheckoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentLinkRepository paymentLinkRepository;

    @MockBean
    private RecoveryActionRepository recoveryActionRepository;

    @MockBean
    private PaymentEventRepository paymentEventRepository;

    @MockBean
    private RetryScheduleRepository retryScheduleRepository;

    @MockBean
    private SubscriptionRepository subscriptionRepository;

    @MockBean
    private MerchantSettingsService merchantSettingsService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private SseService sseService;

    @Test
    @DisplayName("GET /api/checkout/{linkId} should return checkout details")
    void getCheckoutDetails_returnsDetails() throws Exception {
        Customer customer = Customer.builder().id(1L).name("Priya Sharma").email("priya@example.com").build();
        Subscription sub = Subscription.builder().id(2L).customer(customer).build();
        PaymentEvent event = PaymentEvent.builder().id(10L).subscription(sub).amount(29900L).build();
        FailureClassification fc = FailureClassification.builder().id(3L).paymentEvent(event).category("insufficient_funds").build();
        RecoveryAction action = RecoveryAction.builder().id(4L).failureClassification(fc).aiDraftMessage("Payment reminder").status("DISPATCHED").build();
        PaymentLink link = PaymentLink.builder()
                .id(5L)
                .recoveryAction(action)
                .razorpayLinkId("plink_test_123")
                .amount(29900L)
                .currency("INR")
                .shortUrl("https://rzp.io/simulated/plink_test_123")
                .status("CREATED")
                .expireBy(Instant.now().plusSeconds(3600))
                .build();

        when(paymentLinkRepository.findByRazorpayLinkId("plink_test_123")).thenReturn(Optional.of(link));

        mockMvc.perform(get("/api/checkout/plink_test_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkId").value("plink_test_123"))
                .andExpect(jsonPath("$.amount").value(29900))
                .andExpect(jsonPath("$.customerName").value("Priya Sharma"))
                .andExpect(jsonPath("$.customerEmail").value("priya@example.com"));
    }

    @Test
    @DisplayName("POST /api/checkout/{linkId}/pay should simulate payment success")
    void simulatePayment_completesPayment() throws Exception {
        PaymentEvent event = PaymentEvent.builder().id(10L).amount(29900L).build();
        FailureClassification fc = FailureClassification.builder().id(3L).paymentEvent(event).build();
        RecoveryAction action = RecoveryAction.builder().id(4L).failureClassification(fc).status("DISPATCHED").build();
        PaymentLink link = PaymentLink.builder()
                .id(5L)
                .recoveryAction(action)
                .razorpayLinkId("plink_test_456")
                .amount(29900L)
                .status("CREATED")
                .build();

        when(paymentLinkRepository.findByRazorpayLinkId("plink_test_456")).thenReturn(Optional.of(link));
        when(retryScheduleRepository.findByPaymentEventIdAndResult(eq(10L), eq("PENDING"))).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/checkout/plink_test_456/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentId").exists());

        verify(paymentLinkRepository).save(argThat(l -> "PAID".equals(l.getStatus())));
        verify(recoveryActionRepository).save(argThat(a -> "RECOVERED".equals(a.getStatus())));
        verify(auditService).log(eq("PAYMENT_LINK"), eq(5L), eq("PAYMENT_LINK_PAID"), eq("CUSTOMER"), anyString());
        verify(sseService).broadcast(eq("payment.recovered"), anyMap());
    }
}
