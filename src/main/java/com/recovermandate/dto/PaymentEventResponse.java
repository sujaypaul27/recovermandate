package com.recovermandate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEventResponse {
    private Long id;
    private UUID traceId;
    private String razorpayPaymentId;
    private String eventType;
    private Long amount;
    private Instant receivedAt;
    
    // Customer & Subscription details
    private String customerName;
    private String customerEmail;
    private String subscriptionId;
    private String planName;
    private String failureReasonCode;
    
    // Classification fields
    private String classificationCategory;
    private Boolean autoRecoverable;
    private String classificationStatus; // E.g. COMPLETED, PENDING, or the RecoveryAction status

    // Smart Retry Schedules
    private java.util.List<RetryScheduleDto> retrySchedules;

    // Recovery Action & Payment Link details
    private Long recoveryActionId;
    private String paymentLinkId;
    private String paymentLinkUrl;
}
