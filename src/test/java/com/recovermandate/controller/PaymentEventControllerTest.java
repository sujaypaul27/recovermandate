package com.recovermandate.controller;

import com.recovermandate.dto.PaymentEventResponse;
import com.recovermandate.service.PaymentEventQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentEventController.class)
class PaymentEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentEventQueryService paymentEventQueryService;

    @Test
    void getPaymentEvents_returnsPage() throws Exception {
        PaymentEventResponse event = PaymentEventResponse.builder()
                .id(1L)
                .razorpayPaymentId("pay_123")
                .amount(5000L)
                .build();
        Page<PaymentEventResponse> page = new PageImpl<>(List.of(event));

        when(paymentEventQueryService.getPaymentEvents(any(), any(), any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/payment-events?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].razorpayPaymentId").value("pay_123"));
    }

    @Test
    void getPaymentEvents_invalidPagination_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/payment-events?page=-1&size=20"))
                .andExpect(status().isBadRequest());
        
        mockMvc.perform(get("/api/payment-events?size=500")) // over max 100
                .andExpect(status().isBadRequest());
    }
}
