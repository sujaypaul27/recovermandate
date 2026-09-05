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
public class RecoveryActionResponse {
    private Long id;
    private Long failureClassificationId;
    private String aiDraftMessage;
    private String draftSource;
    private String paymentLinkUrl;
    private String status;
    private String approvedBy;
    private Instant approvedAt;
    private Instant sentAt;
    private Instant createdAt;
    private String actor;
    private String rawErrorCode;
    private String bank;
    private String category;
    private Boolean autoRecoverable;
    private String matchedRule;
    private String razorpayPaymentId;
    private Long amount;
    private String customerEmail;
    private String customerName;
    private Boolean isDemoData;
}
