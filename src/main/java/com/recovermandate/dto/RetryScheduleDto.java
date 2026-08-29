package com.recovermandate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryScheduleDto {
    private Long id;
    private int attemptNumber;
    private Instant scheduledAt;
    private Instant executedAt;
    private String status; // PENDING, SUCCESS, FAILED, SKIPPED
    private String scheduleReason;
    private String failureCategory;
    private String razorpayRetryPaymentId;
}
