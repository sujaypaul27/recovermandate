package com.recovermandate.controller;

import com.recovermandate.dto.DashboardSummaryResponse;
import com.recovermandate.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    void getSummary_returnsSummary() throws Exception {
        DashboardSummaryResponse response = DashboardSummaryResponse.builder()
                .recoveredAmount(15000L)
                .failedCount(10)
                .pendingApprovalsCount(2)
                .blockedDraftsCount(1)
                .build();

        when(dashboardService.getSummary(org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(response);
        when(dashboardService.getSummary()).thenReturn(response);

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveredAmount").value(15000))
                .andExpect(jsonPath("$.failedCount").value(10))
                .andExpect(jsonPath("$.pendingApprovalsCount").value(2))
                .andExpect(jsonPath("$.blockedDraftsCount").value(1));
    }

    @Test
    void exportCsv_returnsCsvAttachment() throws Exception {
        byte[] csv = "Payment ID,Subscription ID,Customer Email,Failure Category,Original Failure Time,Recovery Channel,Settled Amount (INR),Status,Audit Hash\r\npay_123,sub_456,user@test.com,insufficient_funds,2026-08-29T10:00:00Z,RAZORPAY_PAYMENT_LINK,499.00,RECOVERED,abc123hash\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(dashboardService.exportRecoveryLedgerCsv()).thenReturn(csv);

        mockMvc.perform(get("/api/dashboard/export-csv"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"recovermandate-recovery-ledger.csv\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(csv));
    }
}
