package com.recovermandate.controller;

import com.recovermandate.dto.AuditLogResponse;
import com.recovermandate.service.AuditQueryService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(AuditLogController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditQueryService auditQueryService;

    @MockBean
    private com.recovermandate.audit.AuditService auditService;

    @Test
    void getAuditLogs_returnsPage() throws Exception {
        AuditLogResponse log = AuditLogResponse.builder()
                .id(1L)
                .action("ACTION_APPROVED")
                .actor("HUMAN")
                .build();
        Page<AuditLogResponse> page = new PageImpl<>(List.of(log));

        when(auditQueryService.getAuditLogs(any(), any(), any(), any(), any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/audit-log?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("ACTION_APPROVED"));
    }

    @Test
    void verifyChain_returnsResult() throws Exception {
        com.recovermandate.dto.AuditChainVerificationResponse response = com.recovermandate.dto.AuditChainVerificationResponse.builder()
                .valid(true)
                .chainLength(15L)
                .message("Cryptographic hash chain verified successfully")
                .build();

        when(auditService.verifyChain()).thenReturn(response);

        mockMvc.perform(get("/api/audit-log/verify-chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.chainLength").value(15));
    }

    @Test
    void resealChain_returnsResult() throws Exception {
        com.recovermandate.dto.AuditChainVerificationResponse response = com.recovermandate.dto.AuditChainVerificationResponse.builder()
                .valid(true)
                .chainLength(15L)
                .message("Cryptographic hash chain re-sealed successfully")
                .build();

        when(auditService.resealChain()).thenReturn(response);

        mockMvc.perform(post("/api/audit-log/reseal-chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.message").value("Cryptographic hash chain re-sealed successfully"));
    }
}
