package com.recovermandate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditChainVerificationResponse {
    private boolean valid;
    private long chainLength;
    private Long brokenAtId;
    private String message;
}
