package com.recovermandate.integration;

import com.recovermandate.ai.GeminiClient;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.repository.FailureClassificationRepository;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.repository.RecoveryActionRepository;
import com.recovermandate.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class FailureClassificationIntegrationTest {

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private FailureClassificationRepository failureClassificationRepository;

    @Autowired
    private RecoveryActionRepository recoveryActionRepository;

    @MockBean
    private GeminiClient geminiClient;

    @BeforeEach
    void setUp() {
        recoveryActionRepository.deleteAll();
        failureClassificationRepository.deleteAll();
        paymentEventRepository.deleteAll();
    }

    @Test
    void testUnknownFailureReason_RemainsVisibleForHumanReview() {
        // Mock Gemini to return a valid draft so it goes to DRAFTED (visible for review)
        when(geminiClient.generateDraft(any(), any(), any(), any(), anyInt()))
                .thenReturn("This is a valid draft asking for 999.00 payment.");

        String payload = """
                {
                  "entity": "event",
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_unknown_001",
                        "amount": 99900,
                        "error_code": "BIZARRE_BANK_ERROR"
                      }
                    }
                  }
                }
                """;

        webhookService.handleVerifiedEvent(payload);

        List<PaymentEvent> events = paymentEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFailureReasonCode()).isEqualTo("BIZARRE_BANK_ERROR");

        List<FailureClassification> classifications = failureClassificationRepository.findAll();
        assertThat(classifications).hasSize(1);
        assertThat(classifications.get(0).getCategory()).isEqualTo("unknown");
        assertThat(classifications.get(0).getCategory()).isNotEqualTo("technical_decline");
        assertThat(classifications.get(0).isAutoRecoverable()).isFalse();

        List<RecoveryAction> actions = recoveryActionRepository.findAll();
        assertThat(actions).hasSize(1);
        // Because autoRecoverable=false, it should create a RecoveryAction in DRAFTED or BLOCKED
        // We mocked a valid draft, so it should be DRAFTED, which means it sits in approval queue
        assertThat(actions.get(0).getStatus()).isEqualTo("DRAFTED");
        assertThat(actions.get(0).getAiDraftMessage()).contains("999.00");
    }
}
