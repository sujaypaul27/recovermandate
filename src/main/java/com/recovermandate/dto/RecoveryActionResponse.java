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
    private String status;
    private Instant createdAt;
    private String actor;
}
