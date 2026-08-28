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
}
