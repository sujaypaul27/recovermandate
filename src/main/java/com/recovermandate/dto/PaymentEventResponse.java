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
public class PaymentEventResponse {
    private Long id;
    private String razorpayPaymentId;
    private String eventType;
    private Long amount;
    private Instant receivedAt;
    
    // Classification fields
    private String classificationCategory;
    private Boolean autoRecoverable;
    private String classificationStatus; // E.g. COMPLETED, PENDING, or the RecoveryAction status
}
