package com.recovermandate.controller;

import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.RetrySchedule;
import com.recovermandate.repository.RetryScheduleRepository;
import com.recovermandate.scheduler.RetryExecutionScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RetryController.class)
@AutoConfigureMockMvc(addFilters = false)
class RetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RetryScheduleRepository retryScheduleRepository;

    @MockBean
    private RetryExecutionScheduler retryExecutionScheduler;

    @MockBean
    private AuditService auditService;

    @Test
    @DisplayName("POST /api/retries/{id}/trigger-now triggers retry immediately")
    void triggerRetryNow_success() throws Exception {
        RetrySchedule schedule = RetrySchedule.builder()
                .id(1L)
                .attemptNumber(1)
                .result("PENDING")
                .scheduledAt(Instant.now().plusSeconds(3600))
                .build();

        RetrySchedule executed = RetrySchedule.builder()
                .id(1L)
                .attemptNumber(1)
                .result("SUCCESS")
                .razorpayRetryPaymentId("pay_retry_123")
                .scheduledAt(schedule.getScheduledAt())
                .executedAt(Instant.now())
                .build();

        when(retryScheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));
        when(retryExecutionScheduler.executeSingleRetry(eq(schedule), eq(true))).thenReturn(executed);

        mockMvc.perform(post("/api/retries/1/trigger-now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.razorpayRetryPaymentId").value("pay_retry_123"));

        verify(retryExecutionScheduler).executeSingleRetry(eq(schedule), eq(true));
    }

    @Test
    @DisplayName("POST /api/retries/{id}/cancel marks PENDING retry as SKIPPED")
    void cancelRetry_success() throws Exception {
        RetrySchedule schedule = RetrySchedule.builder()
                .id(2L)
                .attemptNumber(2)
                .result("PENDING")
                .scheduledAt(Instant.now().plusSeconds(7200))
                .build();

        when(retryScheduleRepository.findById(2L)).thenReturn(Optional.of(schedule));
        when(retryScheduleRepository.save(any(RetrySchedule.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/retries/2/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.status").value("SKIPPED"))
                .andExpect(jsonPath("$.scheduleReason").value("CANCELLED_BY_SUPPORT_AGENT"));

        verify(auditService).log(eq("RETRY_SCHEDULE"), eq(2L), eq("RETRY_CANCELLED_MANUALLY"), eq("HUMAN"), any());
    }

    @Test
    @DisplayName("POST /api/retries/{id}/cancel returns 400 if retry is not PENDING")
    void cancelRetry_notPending_returns400() throws Exception {
        RetrySchedule schedule = RetrySchedule.builder()
                .id(3L)
                .attemptNumber(1)
                .result("SUCCESS")
                .build();

        when(retryScheduleRepository.findById(3L)).thenReturn(Optional.of(schedule));

        mockMvc.perform(post("/api/retries/3/cancel"))
                .andExpect(status().isBadRequest());
    }
}
