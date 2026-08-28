package com.recovermandate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO containing comprehensive ROI, performance, and funnel metrics for the executive dashboard.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    private long recoveredAmount;
    private long failedCount;
    private long pendingApprovalsCount;
    private long blockedDraftsCount;

    // Enhanced ROI & Performance Metrics
    private long totalPaymentsProcessed;
    private long successfulPaymentsCount;
    private double successRate; // in percentage (0.0 to 100.0)
    private double avgResolutionTimeMinutes;
    private Map<String, Long> failuresByCategory;

    // Recovery Funnel Metrics
    private long draftsGenerated;
    private long draftsApproved;
    private long messagesDispatched;
    private long paymentsRecovered;
    private double recoveryRate; // in percentage (0.0 to 100.0)
}
