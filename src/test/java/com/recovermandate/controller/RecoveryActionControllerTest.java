package com.recovermandate.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovermandate.dto.RejectActionRequest;
import com.recovermandate.service.RecoveryActionService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@WebMvcTest(RecoveryActionController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecoveryActionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RecoveryActionService recoveryActionService;

    @Test
    void approveAction_success_returnsOk() throws Exception {
        mockMvc.perform(post("/api/recovery-actions/1/approve"))
                .andExpect(status().isOk());
        verify(recoveryActionService).approveAction(eq(1L), eq("HUMAN"));
    }

    @Test
    void approveAndDispatch_success_returnsOk() throws Exception {
        mockMvc.perform(post("/api/recovery-actions/1/approve-and-dispatch"))
                .andExpect(status().isOk());
        verify(recoveryActionService).approveAndDispatch(eq(1L), eq("HUMAN"));
    }

    @Test
    void approveAction_notFound_returns404() throws Exception {
        doThrow(new EntityNotFoundException("Not found")).when(recoveryActionService).approveAction(1L, "HUMAN");
        
        mockMvc.perform(post("/api/recovery-actions/1/approve"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approveAction_illegalState_returns409() throws Exception {
        doThrow(new IllegalStateException("Conflict")).when(recoveryActionService).approveAction(1L, "HUMAN");
        
        mockMvc.perform(post("/api/recovery-actions/1/approve"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectAction_success_returnsOk() throws Exception {
        RejectActionRequest request = new RejectActionRequest("Unprofessional tone");

        mockMvc.perform(post("/api/recovery-actions/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        verify(recoveryActionService).rejectAction(eq(1L), eq("HUMAN"), eq("Unprofessional tone"));
    }

    @Test
    void rejectAction_missingReason_returns400() throws Exception {
        RejectActionRequest request = new RejectActionRequest("");

        mockMvc.perform(post("/api/recovery-actions/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectAction_illegalState_returns409() throws Exception {
        RejectActionRequest request = new RejectActionRequest("Unprofessional tone");
        doThrow(new IllegalStateException("Conflict")).when(recoveryActionService).rejectAction(1L, "HUMAN", "Unprofessional tone");
        
        mockMvc.perform(post("/api/recovery-actions/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}
