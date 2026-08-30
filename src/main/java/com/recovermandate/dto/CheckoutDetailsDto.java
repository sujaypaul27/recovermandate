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
public class CheckoutDetailsDto {
    private String linkId;
    private Long paymentEventId;
    private Long amount; // in paise
    private String currency;
    private String customerName;
    private String customerEmail;
    private String merchantName;
    private String planName;
    private String failureCategory;
    private String failureReason;
    private String aiExplanation;
    private String status; // CREATED, PAID, EXPIRED, SUPERSEDED
    private Instant expireBy;
    private String shortUrl;
}
