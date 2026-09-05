package com.recovermandate.audit;

import com.recovermandate.entity.AuditLog;
import com.recovermandate.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        MDC.clear();
        lenient().when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Should generate valid SHA-256 checksum and propagate traceId from MDC")
    void log_withMdcTraceId_computesChecksumAndSaves() {
        UUID expectedTraceId = UUID.randomUUID();
        MDC.put("traceId", expectedTraceId.toString());

        AuditLog log = auditService.log(
                "PAYMENT_EVENT",
                101L,
                "WEBHOOK_INGESTED",
                "SYSTEM",
                "Payment event ingested"
        );

        assertNotNull(log);
        assertEquals(expectedTraceId, log.getTraceId());
        assertNotNull(log.getChecksum());
        assertEquals(64, log.getChecksum().length()); // SHA-256 hex length
        assertNotEquals("GENESIS", log.getChecksum());
        assertEquals(log.getChecksum(), auditService.getLastChecksum());

        MDC.clear();
    }

    @Test
    void log_sequentialCalls_chainsChecksums() {
        AuditLog log1 = auditService.log("PAYMENT_EVENT", 1L, "ACTION_1", "SYSTEM", "First entry");
        String checksum1 = log1.getChecksum();

        AuditLog log2 = auditService.log("RECOVERY_ACTION", 2L, "ACTION_2", "SYSTEM", "Second entry");
        String checksum2 = log2.getChecksum();

        assertNotNull(checksum1);
        assertNotNull(checksum2);
        assertNotEquals(checksum1, checksum2);
        assertEquals(checksum2, auditService.getLastChecksum());
    }

    @Test
    @DisplayName("Should support overloaded log method with AI metadata")
    void log_withAiMetadata_persistsAiFields() {
        String aiModel = "gemini-3.5-flash-lite";
        String promptHash = "a1b2c3d4e5f607182930405060708090a0b0c0d0e0f001122334455667788990";

        AuditLog log = auditService.log(
                "RECOVERY_ACTION",
                50L,
                "AI_DRAFT_GENERATED",
                "SYSTEM",
                "Draft generated",
                aiModel,
                promptHash
        );

        assertNotNull(log);
        assertEquals(aiModel, log.getAiModelUsed());
        assertEquals(promptHash, log.getAiPromptHash());
        assertNotNull(log.getChecksum());
    }

    @Test
    @DisplayName("Should initialize lastChecksum from latest record in repository on init")
    void init_loadsChecksumFromLatestRecord() {
        AuditLog previous = AuditLog.builder()
                .id(42L)
                .checksum("prev_checksum_abc123")
                .build();
        when(auditLogRepository.findTopByOrderByIdDesc()).thenReturn(java.util.Optional.of(previous));

        auditService.init();

        assertEquals("prev_checksum_abc123", auditService.getLastChecksum());
    }

    @Test
    @DisplayName("Should keep GENESIS if no previous audit log exists")
    void init_keepsGenesisWhenEmpty() {
        when(auditLogRepository.findTopByOrderByIdDesc()).thenReturn(java.util.Optional.empty());

        auditService.init();

        assertEquals("GENESIS", auditService.getLastChecksum());
    }

    @Test
    @DisplayName("Should verify valid cryptographic audit chain")
    void verifyChain_validChain() {
        AuditLog log1 = auditService.log("PAYMENT_EVENT", 1L, "ACTION_1", "SYSTEM", "First entry");
        AuditLog log2 = auditService.log("RECOVERY_ACTION", 2L, "ACTION_2", "HUMAN", "Second entry");

        when(auditLogRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(java.util.List.of(log1, log2));

        com.recovermandate.dto.AuditChainVerificationResponse result = auditService.verifyChain();

        assertTrue(result.isValid());
        assertEquals(2L, result.getChainLength());
        assertNull(result.getBrokenAtId());
    }

    @Test
    @DisplayName("Should detect tampered cryptographic checksum in audit chain")
    void verifyChain_tamperedChain() {
        AuditLog log1 = auditService.log("PAYMENT_EVENT", 1L, "ACTION_1", "SYSTEM", "First entry");
        AuditLog log2 = auditService.log("RECOVERY_ACTION", 2L, "ACTION_2", "HUMAN", "Second entry");

        // Tamper with log2's checksum
        log2.setChecksum("tampered_fake_checksum_0000000000000000000000000000000000000000");

        when(auditLogRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(java.util.List.of(log1, log2));

        com.recovermandate.dto.AuditChainVerificationResponse result = auditService.verifyChain();

        assertFalse(result.isValid());
        assertEquals(1L, result.getChainLength()); // 1 entry verified before failure
        assertEquals(log2.getId(), result.getBrokenAtId());
    }

    @Test
    @DisplayName("Should re-seal tampered audit chain and restore valid cryptographic integrity")
    void resealChain_repairsBrokenChain() {
        AuditLog log1 = auditService.log("PAYMENT_EVENT", 1L, "ACTION_1", "SYSTEM", "First entry");
        AuditLog log2 = auditService.log("RECOVERY_ACTION", 2L, "ACTION_2", "HUMAN", "Second entry");

        // Tamper with log2's checksum
        log2.setChecksum("corrupted_or_deleted_prior_records_checksum");

        when(auditLogRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(java.util.List.of(log1, log2));

        com.recovermandate.dto.AuditChainVerificationResponse verifyBefore = auditService.verifyChain();
        assertFalse(verifyBefore.isValid());

        com.recovermandate.dto.AuditChainVerificationResponse resealRes = auditService.resealChain();
        assertTrue(resealRes.isValid());
        assertEquals(2L, resealRes.getChainLength());
        assertNull(resealRes.getBrokenAtId());

        com.recovermandate.dto.AuditChainVerificationResponse verifyAfter = auditService.verifyChain();
        assertTrue(verifyAfter.isValid());
    }
}
