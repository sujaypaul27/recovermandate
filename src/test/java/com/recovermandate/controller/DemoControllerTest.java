package com.recovermandate.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovermandate.dto.DemoFailureSimulationRequest;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.repository.PaymentLinkRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import com.recovermandate.service.RecoveryActionService;
import com.recovermandate.service.WebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoController.class)
@AutoConfigureMockMvc(addFilters = false)
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebhookService webhookService;

    @MockBean
    private RecoveryActionService recoveryActionService;

    @MockBean
    private PaymentLinkRepository paymentLinkRepository;

    @MockBean
    private RecoveryActionRepository recoveryActionRepository;

    @Test
    @DisplayName("POST /api/demo/simulate-failure should return 200 with simulation details")
    void simulateFailure_returnsSuccess() throws Exception {
        PaymentEvent fakeEvent = PaymentEvent.builder()
                .id(123L)
                .razorpayPaymentId("pay_demo_test123")
                .eventType("payment.failed")
                .amount(49900L)
                .build();

        when(webhookService.handleVerifiedEvent(anyString())).thenReturn(fakeEvent);

        DemoFailureSimulationRequest request = DemoFailureSimulationRequest.builder()
                .category("insufficient_funds")
                .amount(49900L)
                .customerName("John Doe")
                .build();

        mockMvc.perform(post("/api/demo/simulate-failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.category").value("insufficient_funds"))
                .andExpect(jsonPath("$.eventId").value(123));
    }

    @Test
    @DisplayName("POST /api/demo/simulate-payment-paid should trigger payment_link.paid")
    void simulatePaymentPaid_returnsSuccess() throws Exception {
        PaymentEvent paidEvent = PaymentEvent.builder()
                .id(124L)
                .eventType("payment_link.paid")
                .amount(49900L)
                .build();

        when(webhookService.handleVerifiedEvent(anyString())).thenReturn(paidEvent);

        mockMvc.perform(post("/api/demo/simulate-payment-paid?paymentLinkId=plink_demo_123&amount=49900"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentLinkId").value("plink_demo_123"));
    }
}
