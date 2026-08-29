package com.recovermandate.controller;

import com.recovermandate.audit.AuditService;
import com.recovermandate.dto.RetryScheduleDto;
import com.recovermandate.entity.RetrySchedule;
import com.recovermandate.repository.RetryScheduleRepository;
import com.recovermandate.scheduler.RetryExecutionScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Controller providing support operations for automated retries (trigger now, cancel).
 */
@Slf4j
@RestController
@RequestMapping("/api/retries")
@RequiredArgsConstructor
public class RetryController {

    private final RetryScheduleRepository retryScheduleRepository;
    private final RetryExecutionScheduler retryExecutionScheduler;
    private final AuditService auditService;

    /**
     * Triggers an immediate retry execution on behalf of a support agent, bypassing normal schedule delays.
     */
    @PostMapping("/{id}/trigger-now")
    public ResponseEntity<?> triggerRetryNow(@PathVariable Long id) {
        log.info("Support agent triggered immediate retry for schedule id={}", id);

        Optional<RetrySchedule> opt = retryScheduleRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Retry schedule not found with id " + id));
        }

        RetrySchedule schedule = opt.get();
        if (!"PENDING".equalsIgnoreCase(schedule.getResult())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Only PENDING retries can be triggered. Current status: " + schedule.getResult()
            ));
        }

        RetrySchedule executed = retryExecutionScheduler.executeSingleRetry(schedule, true);
        return ResponseEntity.ok(mapToDto(executed));
    }

    /**
     * Cancels a pending retry attempt on behalf of a support agent.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelRetry(@PathVariable Long id) {
        log.info("Support agent requested cancellation for retry schedule id={}", id);

        Optional<RetrySchedule> opt = retryScheduleRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Retry schedule not found with id " + id));
        }

        RetrySchedule schedule = opt.get();
        if (!"PENDING".equalsIgnoreCase(schedule.getResult())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Only PENDING retries can be cancelled. Current status: " + schedule.getResult()
            ));
        }

        schedule.setResult("SKIPPED");
        schedule.setScheduleReason("CANCELLED_BY_SUPPORT_AGENT");
        schedule.setExecutedAt(Instant.now());
        RetrySchedule saved = retryScheduleRepository.save(schedule);

        auditService.log(
                "RETRY_SCHEDULE",
                schedule.getId(),
                "RETRY_CANCELLED_MANUALLY",
                "HUMAN",
                "Support agent manually cancelled retry attempt #" + schedule.getAttemptNumber()
        );

        return ResponseEntity.ok(mapToDto(saved));
    }

    private RetryScheduleDto mapToDto(RetrySchedule s) {
        return RetryScheduleDto.builder()
                .id(s.getId())
                .attemptNumber(s.getAttemptNumber())
                .scheduledAt(s.getScheduledAt())
                .executedAt(s.getExecutedAt())
                .status(s.getResult())
                .scheduleReason(s.getScheduleReason())
                .failureCategory(s.getFailureCategory())
                .razorpayRetryPaymentId(s.getRazorpayRetryPaymentId())
                .build();
    }
}
