package com.recovermandate.service;

import com.recovermandate.dto.DashboardSummaryResponse;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PaymentEventRepository paymentEventRepository;
    private final RecoveryActionRepository recoveryActionRepository;

    public DashboardSummaryResponse getSummary() {
        long recoveredAmount = paymentEventRepository.sumAmountByEventType("subscription.charged");
        long failedCount = paymentEventRepository.countByEventType("payment.failed");
        long pendingApprovalsCount = recoveryActionRepository.countByStatus("DRAFTED");
        long blockedDraftsCount = recoveryActionRepository.countByStatus("BLOCKED");

        return DashboardSummaryResponse.builder()
                .recoveredAmount(recoveredAmount)
                .failedCount(failedCount)
                .pendingApprovalsCount(pendingApprovalsCount)
                .blockedDraftsCount(blockedDraftsCount)
                .build();
    }
}
