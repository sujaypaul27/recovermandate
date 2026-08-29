package com.recovermandate.controller;

import com.recovermandate.entity.AuditLog;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.repository.AuditLogRepository;
import com.recovermandate.repository.PaymentEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@AutoConfigureMockMvc(addFilters = false)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentEventRepository paymentEventRepository;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @Test
    void search_withQuery_returnsMatchingResults() throws Exception {
        PaymentEvent pe = PaymentEvent.builder()
                .id(1L)
                .razorpayPaymentId("pay_search_123")
                .eventType("payment.failed")
                .amount(49900L)
                .receivedAt(Instant.now())
                .build();

        AuditLog log = AuditLog.builder()
                .id(10L)
                .entityType("PAYMENT_EVENT")
                .entityId(1L)
                .action("PAYMENT_RECOVERED")
                .reasoning("Recovered ₹499")
                .createdAt(Instant.now())
                .build();

        when(paymentEventRepository.searchEvents(eq("search"), any())).thenReturn(List.of(pe));
        when(auditLogRepository.searchAuditLogs(eq("search"), any())).thenReturn(List.of(log));

        mockMvc.perform(get("/api/search?q=search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("pay_search_123"))
                .andExpect(jsonPath("$[1].type").value("AUDIT_LOG"));
    }

    @Test
    void search_emptyQuery_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/search?q="))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
