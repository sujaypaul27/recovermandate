package com.recovermandate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoFailureSimulationRequest {
    private String category; // insufficient_funds, technical_decline, expired_mandate, unknown
    private Long amount; // in paise, optional (default 49900 = ₹499.00)
    private String customerName;
    private String customerEmail;
    private String bankCode;
}
