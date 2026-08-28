package com.recovermandate.integration;

import com.recovermandate.entity.AuditLog;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.repository.AuditLogRepository;
import com.recovermandate.repository.FailureClassificationRepository;
import com.recovermandate.repository.PaymentEventRepository;
import com.recovermandate.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class WebhookIntegrationTest {

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private FailureClassificationRepository failureClassificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private com.recovermandate.repository.RecoveryActionRepository recoveryActionRepository;

    @Autowired
    private com.recovermandate.repository.PaymentLinkRepository paymentLinkRepository;

    @Autowired
    private com.recovermandate.repository.DispatchLogRepository dispatchLogRepository;

    @Autowired
    private com.recovermandate.repository.RetryScheduleRepository retryScheduleRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        dispatchLogRepository.deleteAll();
        paymentLinkRepository.deleteAll();
        recoveryActionRepository.deleteAll();
        retryScheduleRepository.deleteAll();
        failureClassificationRepository.deleteAll();
        paymentEventRepository.deleteAll();
    }

    @Test
    void testDuplicateWebhookDelivery_idempotency() {
        String payload = """
                {
                  "entity": "event",
                  "event": "payment.failed",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_integ_001",
                        "amount": 99900,
                        "error_code": "BAD_REQUEST_ERROR"
                      }
                    }
                  }
                }
                """;

        // 1st delivery
        webhookService.handleVerifiedEvent(payload);

        // 2nd delivery (duplicate)
        webhookService.handleVerifiedEvent(payload);

        // Assert: PaymentEvent created once
        List<PaymentEvent> events = paymentEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getRazorpayPaymentId()).isEqualTo("pay_integ_001");

        // Assert: FailureClassification created once
        List<FailureClassification> classifications = failureClassificationRepository.findAll();
        assertThat(classifications).hasSize(1);
        assertThat(classifications.get(0).getRawErrorCode()).isEqualTo("BAD_REQUEST_ERROR");

        // Assert: Audit entries
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        
        long ingestedCount = auditLogs.stream()
                .filter(a -> "WEBHOOK_INGESTED".equals(a.getAction()))
                .count();
        assertThat(ingestedCount).isEqualTo(1);

        long duplicateCount = auditLogs.stream()
                .filter(a -> "DUPLICATE_WEBHOOK_IGNORED".equals(a.getAction()))
                .count();
        assertThat(duplicateCount).isEqualTo(1);
    }
}
