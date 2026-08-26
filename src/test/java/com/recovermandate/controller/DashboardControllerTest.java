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

        when(dashboardService.getSummary()).thenReturn(response);

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveredAmount").value(15000))
                .andExpect(jsonPath("$.failedCount").value(10))
                .andExpect(jsonPath("$.pendingApprovalsCount").value(2))
                .andExpect(jsonPath("$.blockedDraftsCount").value(1));
    }
}
