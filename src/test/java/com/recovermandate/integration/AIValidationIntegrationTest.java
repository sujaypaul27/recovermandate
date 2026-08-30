package com.recovermandate.integration;

import com.recovermandate.ai.GeminiClient;
import com.recovermandate.entity.AuditLog;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.repository.AuditLogRepository;
import com.recovermandate.repository.FailureClassificationRepository;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import com.recovermandate.service.RecoveryActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class AIValidationIntegrationTest {

    @Autowired
    private RecoveryActionService recoveryActionService;

    @Autowired
    private RecoveryActionRepository recoveryActionRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private FailureClassificationRepository failureClassificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockBean
    private GeminiClient geminiClient;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        recoveryActionRepository.deleteAll();
        failureClassificationRepository.deleteAll();
        paymentEventRepository.deleteAll();
    }

    @Test
    void testAiDraftBlocked_WrongAmount() {
        PaymentEvent event = PaymentEvent.builder()
                .razorpayPaymentId("pay_ai_1")
                .amount(50000L) // 500.00
                .receivedAt(Instant.now())
                .eventType("payment.failed")
                .build();
        paymentEventRepository.save(event);

        FailureClassification classification = new FailureClassification();
        classification.setPaymentEvent(event);
        classification.setCategory("insufficient_funds");
        classification.setAutoRecoverable(false);
        classification.setRawErrorCode("BAD_REQUEST");
        classification.setDecidedAt(Instant.now());
        failureClassificationRepository.save(classification);

        // Mock Gemini returning wrong amount in draft (50.00 instead of 500.00)
        when(geminiClient.generateDraft(any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(new com.recovermandate.ai.DraftResult("Please pay $50.00 to resolve your failure.", "AI"));

        recoveryActionService.processFailure(classification);

        List<RecoveryAction> actions = recoveryActionRepository.findAll();
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getStatus()).isEqualTo("BLOCKED");

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        boolean hasBlockedLog = auditLogs.stream()
                .anyMatch(a -> "AI_DRAFT_BLOCKED".equals(a.getAction()) && a.getReasoning().contains("incorrect monetary amount"));
        assertThat(hasBlockedLog).isTrue();
    }

    @Test
    void testAiDraftBlocked_DenylistLanguage() {
        PaymentEvent event = PaymentEvent.builder()
                .razorpayPaymentId("pay_ai_2")
                .amount(10000L) // 100.00
                .receivedAt(Instant.now())
                .eventType("payment.failed")
                .build();
        paymentEventRepository.save(event);

        FailureClassification classification = new FailureClassification();
        classification.setPaymentEvent(event);
        classification.setCategory("insufficient_funds");
        classification.setAutoRecoverable(false);
        classification.setRawErrorCode("BAD_REQUEST");
        classification.setDecidedAt(Instant.now());
        failureClassificationRepository.save(classification);

        // Mock Gemini returning denylist language (discount)
        when(geminiClient.generateDraft(any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(new com.recovermandate.ai.DraftResult("Please pay 100.00. We will give you a discount if you pay now.", "AI"));

        recoveryActionService.processFailure(classification);

        List<RecoveryAction> actions = recoveryActionRepository.findAll();
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).getStatus()).isEqualTo("BLOCKED");

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        boolean hasBlockedLog = auditLogs.stream()
                .anyMatch(a -> "AI_DRAFT_BLOCKED".equals(a.getAction()) && a.getReasoning().toLowerCase().contains("discount"));
        assertThat(hasBlockedLog).isTrue();
    }
}
