package com.recovermandate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchApproveResponse {
    private int totalRequested;
    private int successful;
    private int failed;
    private List<Long> approvedActionIds;
    private List<BatchItemError> errors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchItemError {
        private Long actionId;
        private String errorMessage;
    }
}
