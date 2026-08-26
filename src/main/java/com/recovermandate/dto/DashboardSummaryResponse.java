package com.recovermandate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {
    private long recoveredAmount;
    private long failedCount;
    private long pendingApprovalsCount;
    private long blockedDraftsCount;
}
