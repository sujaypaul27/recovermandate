package com.recovermandate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoFailureSimulationResponse {
    private String status;
    private Long eventId;
    private String paymentId;
    private String category;
    private Long amount;
    private String customerEmail;
    private String bankCode;
    private Long recoveryActionId;
    private String message;
}
