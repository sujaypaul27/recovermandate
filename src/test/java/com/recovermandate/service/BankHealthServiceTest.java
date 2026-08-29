package com.recovermandate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.BankHealthSnapshot;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.repository.BankHealthSnapshotRepository;
import com.recovermandate.repository.PaymentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankHealthServiceTest {

    @Mock
    private BankHealthSnapshotRepository bankHealthSnapshotRepository;

    @Mock
    private PaymentEventRepository paymentEventRepository;

    @Mock
    private AuditService auditService;

    private ObjectMapper objectMapper;
    private BankHealthService bankHealthService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        bankHealthService = new BankHealthService(
                bankHealthSnapshotRepository,
                paymentEventRepository,
                auditService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should extract bank code from raw payload")
    void extractBankCode_fromPayload() {
        PaymentEvent event = new PaymentEvent();
        event.setRawPayload("{\"payload\":{\"payment\":{\"entity\":{\"bank\":\"HDFC\"}}}}");

        String bank = bankHealthService.extractBankCode(event);
        assertEquals("HDFC", bank);
    }

    @Test
    @DisplayName("Should determine DOWN status if failure rate >= 80%")
    void determineStatus_down() {
        String status = bankHealthService.determineStatus(0.85, 20);
        assertEquals("DOWN", status);
    }

    @Test
    @DisplayName("Should determine DEGRADED status if failure rate >= 40%")
    void determineStatus_degraded() {
        String status = bankHealthService.determineStatus(0.50, 10);
        assertEquals("DEGRADED", status);
    }

    @Test
    @DisplayName("Should determine HEALTHY status if failure rate < 40%")
    void determineStatus_healthy() {
        String status = bankHealthService.determineStatus(0.10, 10);
        assertEquals("HEALTHY", status);
    }

    @Test
    @DisplayName("Should compute health snapshots and save snapshot records")
    void computeHealthSnapshots_success() {
        PaymentEvent event1 = new PaymentEvent();
        event1.setEventType("payment.failed");
        event1.setFailureReasonCode("HDFC_TIMEOUT");

        PaymentEvent event2 = new PaymentEvent();
        event2.setEventType("subscription.charged");
        event2.setRawPayload("{\"bank\":\"HDFC\"}");

        when(paymentEventRepository.findByReceivedAtGreaterThanEqual(any())).thenReturn(List.of(event1, event2));
        when(bankHealthSnapshotRepository.save(any(BankHealthSnapshot.class))).thenAnswer(i -> i.getArgument(0));

        List<BankHealthSnapshot> snapshots = bankHealthService.computeHealthSnapshots();

        assertFalse(snapshots.isEmpty());
        verify(bankHealthSnapshotRepository, atLeastOnce()).save(any());
        verify(auditService).log(eq("SYSTEM"), eq(0L), eq("BANK_HEALTH_COMPUTED"), eq("SYSTEM"), anyString());
    }

    @Test
    @DisplayName("Should query bank health from latest snapshot")
    void getBankHealth_returnsLatest() {
        BankHealthSnapshot snapshot = BankHealthSnapshot.builder()
                .bankCode("HDFC")
                .status("DEGRADED")
                .createdAt(Instant.now())
                .build();

        when(bankHealthSnapshotRepository.findTopByBankCodeOrderByCreatedAtDesc("HDFC"))
                .thenReturn(Optional.of(snapshot));

        String health = bankHealthService.getBankHealth("HDFC");
        assertEquals("DEGRADED", health);
    }

    @Test
    @DisplayName("Should return single latest snapshot per distinct bank code")
    void getLatestSnapshots_returnsSingleLatestPerBank() {
        when(bankHealthSnapshotRepository.findDistinctBankCodes()).thenReturn(List.of("HDFC", "ICICI"));

        BankHealthSnapshot hdfc = BankHealthSnapshot.builder().bankCode("HDFC").status("HEALTHY").build();
        BankHealthSnapshot icici = BankHealthSnapshot.builder().bankCode("ICICI").status("DEGRADED").build();

        when(bankHealthSnapshotRepository.findTopByBankCodeOrderByCreatedAtDesc("HDFC")).thenReturn(Optional.of(hdfc));
        when(bankHealthSnapshotRepository.findTopByBankCodeOrderByCreatedAtDesc("ICICI")).thenReturn(Optional.of(icici));

        List<BankHealthSnapshot> latest = bankHealthService.getLatestSnapshots();

        assertNotNull(latest);
        assertTrue(latest.contains(hdfc));
        assertTrue(latest.contains(icici));
    }
}
