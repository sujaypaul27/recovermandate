package com.recovermandate.service;

import com.recovermandate.dto.DashboardSummaryResponse;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.repository.FailureClassificationRepository;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private PaymentEventRepository paymentEventRepository;

    @Mock
    private RecoveryActionRepository recoveryActionRepository;

    @Mock
    private FailureClassificationRepository failureClassificationRepository;

    @Mock
    private com.recovermandate.repository.AuditLogRepository auditLogRepository;

    @Mock
    private com.recovermandate.repository.PaymentLinkRepository paymentLinkRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    @DisplayName("Should compute all ROI, performance, funnel, and category metrics")
    void getSummary_computesComprehensiveMetrics() {
        when(paymentEventRepository.count()).thenReturn(100L);
        when(paymentEventRepository.countByEventType("subscription.charged")).thenReturn(80L);
        when(paymentEventRepository.countByEventType("payment.failed")).thenReturn(20L);
        when(paymentEventRepository.sumAmountByEventType("subscription.charged")).thenReturn(500000L);

        when(recoveryActionRepository.countByStatus("DRAFTED")).thenReturn(5L);
        when(recoveryActionRepository.countByStatus("BLOCKED")).thenReturn(2L);
        when(recoveryActionRepository.countByStatus("APPROVED")).thenReturn(8L);
        when(recoveryActionRepository.countByStatus("DISPATCHED")).thenReturn(5L);
        when(recoveryActionRepository.count()).thenReturn(20L);

        List<Object[]> categoryCounts = List.of(
                new Object[]{"insufficient_funds", 12L},
                new Object[]{"technical_decline", 8L}
        );
        when(failureClassificationRepository.countByCategory()).thenReturn(categoryCounts);

        PaymentEvent event = new PaymentEvent();
        event.setReceivedAt(Instant.now().minusSeconds(300));

        FailureClassification fc = new FailureClassification();
        fc.setPaymentEvent(event);

        RecoveryAction action = new RecoveryAction();
        action.setFailureClassification(fc);
        action.setApprovedAt(Instant.now());

        when(recoveryActionRepository.findByApprovedAtIsNotNull(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(action)));

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertNotNull(summary);
        assertEquals(500000L, summary.getRecoveredAmount());
        assertEquals(20L, summary.getFailedCount());
        assertEquals(5L, summary.getPendingApprovalsCount());
        assertEquals(2L, summary.getBlockedDraftsCount());
        assertEquals(100L, summary.getTotalPaymentsProcessed());
        assertEquals(80L, summary.getSuccessfulPaymentsCount());
        assertEquals(80.0, summary.getSuccessRate());
        assertEquals(20L, summary.getDraftsGenerated());
        assertEquals(13L, summary.getDraftsApproved());
        assertEquals(5L, summary.getMessagesDispatched());
        assertEquals(5L, summary.getPaymentsRecovered());
        assertEquals(25.0, summary.getRecoveryRate());
        assertTrue(summary.getAvgResolutionTimeMinutes() > 0.0);
        assertEquals(12L, summary.getFailuresByCategory().get("insufficient_funds"));
        assertEquals(8L, summary.getFailuresByCategory().get("technical_decline"));
    }

    @Test
    @DisplayName("Should generate valid CSV with all required headers and rows")
    void exportRecoveryLedgerCsv_generatesValidCsv() {
        PaymentEvent event = new PaymentEvent();
        event.setId(1L);
        event.setRazorpayPaymentId("pay_123");
        event.setAmount(49900L);
        event.setEventType("payment.failed");
        event.setReceivedAt(Instant.now());

        FailureClassification fc = new FailureClassification();
        fc.setId(10L);
        fc.setCategory("insufficient_funds");
        fc.setPaymentEvent(event);

        RecoveryAction ra = new RecoveryAction();
        ra.setId(100L);
        ra.setStatus("RECOVERED");
        ra.setPaymentLinkUrl("https://rzp.io/l/test");
        ra.setFailureClassification(fc);

        when(paymentEventRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(event)));
        when(failureClassificationRepository.findByPaymentEvent(event)).thenReturn(java.util.Optional.of(fc));
        when(recoveryActionRepository.findByFailureClassification(fc)).thenReturn(java.util.Optional.of(ra));

        com.recovermandate.entity.AuditLog audit = new com.recovermandate.entity.AuditLog();
        audit.setChecksum("checksum12345");
        when(auditLogRepository.findTopByEntityTypeAndEntityIdOrderByIdDesc("RECOVERY_ACTION", 100L))
                .thenReturn(java.util.Optional.of(audit));

        byte[] csv = dashboardService.exportRecoveryLedgerCsv();
        assertNotNull(csv);
        String csvString = new String(csv, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csvString.contains("Payment ID,Subscription ID,Customer Name,Customer Email,Failure Category,Original Failure Time,Recovery Channel,Settled Amount (INR),Status,Audit Hash"));
        assertTrue(csvString.contains("pay_123"));
        assertTrue(csvString.contains("insufficient_funds"));
        assertTrue(csvString.contains("RAZORPAY_PAYMENT_LINK"));
        assertTrue(csvString.contains("499.00"));
        assertTrue(csvString.contains("RECOVERED"));
        assertTrue(csvString.contains("checksum12345"));
    }
}
