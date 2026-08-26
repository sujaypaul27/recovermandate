package com.recovermandate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.repository.FailureClassificationRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FailureClassificationServiceTest {

    @Mock
    private FailureClassificationRepository failureClassificationRepository;

    @Mock
    private AuditService auditService;

    private FailureClassificationService failureClassificationService;

    @BeforeEach
    void setUp() {
        failureClassificationService = new FailureClassificationService(
                failureClassificationRepository,
                auditService
        );
    }

    // Helper: stub repo to return empty (no existing classification) and pass-through save
    private void stubNoExistingClassification() {
        when(failureClassificationRepository.findByPaymentEvent(any(PaymentEvent.class)))
                .thenReturn(Optional.empty());
        when(failureClassificationRepository.save(any(FailureClassification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PaymentEvent buildFailedEvent(Long id, String errorCode) {
        return PaymentEvent.builder()
                .id(id)
                .eventType("payment.failed")
                .failureReasonCode(errorCode)
                .receivedAt(Instant.now())
                .build();
    }

    // ============================================================
    // Category 1: BAD_REQUEST_ERROR -> insufficient_funds
    // ============================================================

    @Nested
    @DisplayName("Category 1: BAD_REQUEST_ERROR -> insufficient_funds")
    class InsufficientFundsTests {

        @Test
        @DisplayName("BAD_REQUEST_ERROR maps to insufficient_funds with auto_recoverable=false")
        void classify_badRequestError_mapsToInsufficientFunds() {
            PaymentEvent event = buildFailedEvent(101L, "BAD_REQUEST_ERROR");
            stubNoExistingClassification();

            FailureClassification result = failureClassificationService.classify(event);

            assertNotNull(result);
            assertEquals("insufficient_funds", result.getCategory());
            assertFalse(result.isAutoRecoverable());
            assertEquals("BAD_REQUEST_ERROR", result.getRawErrorCode());
            assertEquals(event, result.getPaymentEvent());
            assertNotNull(result.getDecidedAt());

            verify(failureClassificationRepository).save(argThat(fc ->
                    "insufficient_funds".equals(fc.getCategory()) &&
                    !fc.isAutoRecoverable() &&
                    "BAD_REQUEST_ERROR".equals(fc.getRawErrorCode()) &&
                    fc.getPaymentEvent().getId().equals(101L)
            ));

            verifyAuditContains(101L, "category=insufficient_funds", "auto_recoverable=false",
                    "BAD_REQUEST_ERROR");
        }

        @Test
        @DisplayName("bad_request_error (lowercase) maps to insufficient_funds — case insensitive")
        void classify_badRequestError_lowerCase() {
            PaymentEvent event = buildFailedEvent(111L, "bad_request_error");
            stubNoExistingClassification();

            FailureClassification result = failureClassificationService.classify(event);

            assertNotNull(result);
            assertEquals("insufficient_funds", result.getCategory());
            assertFalse(result.isAutoRecoverable());
        }
    }

    // ============================================================
    // Category 2: GATEWAY_ERROR / SERVER_ERROR -> technical_decline
    // ============================================================

    @Nested
    @DisplayName("Category 2: GATEWAY_ERROR / SERVER_ERROR -> technical_decline")
    class TechnicalDeclineTests {

        @ParameterizedTest
        @ValueSource(strings = {"GATEWAY_ERROR", "SERVER_ERROR", "gateway_error", "server_error",
                "Gateway_Error", "Server_Error"})
        @DisplayName("GATEWAY_ERROR or SERVER_ERROR maps to technical_decline with auto_recoverable=true (case-insensitive)")
        void classify_gatewayOrServerError_mapsToTechnicalDecline(String errorCode) {
            PaymentEvent event = buildFailedEvent(102L, errorCode);
            stubNoExistingClassification();

            FailureClassification result = failureClassificationService.classify(event);

            assertNotNull(result);
            assertEquals("technical_decline", result.getCategory());
            assertTrue(result.isAutoRecoverable());
            assertEquals(errorCode, result.getRawErrorCode());
            assertEquals(event, result.getPaymentEvent());

            verify(failureClassificationRepository).save(argThat(fc ->
                    "technical_decline".equals(fc.getCategory()) &&
                    fc.isAutoRecoverable() &&
                    fc.getPaymentEvent().getId().equals(102L)
            ));

            verifyAuditContains(102L, "category=technical_decline", "auto_recoverable=true");
        }
    }

    // ============================================================
    // Category 3: expired/mandate substring -> expired_mandate
    // ============================================================

    @Nested
    @DisplayName("Category 3: expired/mandate substring -> expired_mandate")
    class ExpiredMandateTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "mandate_inactive",
                "MANDATE_EXPIRED",
                "card_expired",
                "EXPIRED_CARD",
                "INVALID_MANDATE",
                "mandate_max_amount_exceeded",
                "customer_mandate_not_found",
                "Mandate_Inactive",
                "CARD_EXPIRED"
        })
        @DisplayName("Error code containing expired or mandate maps to expired_mandate with auto_recoverable=false")
        void classify_expiredOrMandate_mapsToExpiredMandate(String errorCode) {
            PaymentEvent event = buildFailedEvent(103L, errorCode);
            stubNoExistingClassification();

            FailureClassification result = failureClassificationService.classify(event);

            assertNotNull(result);
            assertEquals("expired_mandate", result.getCategory());
            assertFalse(result.isAutoRecoverable());
            assertEquals(errorCode, result.getRawErrorCode());
            assertEquals(event, result.getPaymentEvent());

            verify(failureClassificationRepository).save(argThat(fc ->
                    "expired_mandate".equals(fc.getCategory()) &&
                    !fc.isAutoRecoverable() &&
                    fc.getPaymentEvent().getId().equals(103L)
            ));

            verifyAuditContains(103L, "category=expired_mandate", "auto_recoverable=false");
        }
    }

    // ============================================================
    // Category 4: unknown (null, blank, unrecognized)
    // ============================================================

    @Nested
    @DisplayName("Category 4: unknown (null, blank, whitespace, garbage)")
    class UnknownTests {

        @Test
        @DisplayName("Null error code maps to unknown with auto_recoverable=false")
        void classify_nullErrorCode_mapsToUnknown() {
            PaymentEvent event = buildFailedEvent(104L, null);
            stubNoExistingClassification();

            FailureClassification result = failureClassificationService.classify(event);

            assertNotNull(result);
            assertEquals("unknown", result.getCategory());
            assertFalse(result.isAutoRecoverable());
            assertNull(result.getRawErrorCode());
            assertEquals(event, result.getPaymentEvent());

            verify(failureClassificationRepository).save(argThat(fc ->
                    "unknown".equals(fc.getCategory()) &&
                    !fc.isAutoRecoverable() &&
                    fc.getPaymentEvent().getId().equals(104L)
            ));

            verifyAuditContains(104L, "category=unknown", "auto_recoverable=false", "(null)");
        }

        @Test
        @DisplayName("Empty string error code maps to unknown with auto_recoverable=false")
        void classify_emptyErrorCode_mapsToUnknown() {
            PaymentEvent event = buildFailedEvent(105L, "");
            stubNoExistingClassification();

            FailureClassification result = failureClassificationService.classify(event);

            assertNotNull(result);
            assertEquals("unknown", result.getCategory());
            assertFalse(result.isAutoRecoverable());
        }

        @Test
        @DisplayName("Whitespace-only error code maps to unknown with auto_recoverable=false")
        void classify_whitespaceErrorCode_mapsToUnknown() {
            PaymentEvent event = buildFailedEvent(106L, "   ");
            stubNoExistingClassification();

            FailureClassification result = failureClassificationService.classify(event);

            assertNotNull(result);
            assertEquals("unknown", result.getCategory());
            assertFalse(result.isAutoRecoverable());
        }

        @ParameterizedTest
        @ValueSource(strings = {"AUTHENTICATION_ERROR", "INVALID_PARAMS", "UNKNOWN_ERROR",
                "xyz_garbage_123", "!!invalid!!"})
        @DisplayName("Unrecognized/garbage error code maps to unknown with auto_recoverable=false")
        void classify_unrecognizedErrorCode_mapsToUnknown(String errorCode) {
            PaymentEvent event = buildFailedEvent(107L, errorCode);
            stubNoExistingClassification();

            FailureClassification result = failureClassificationService.classify(event);

            assertNotNull(result);
            assertEquals("unknown", result.getCategory());
            assertFalse(result.isAutoRecoverable());

            verify(failureClassificationRepository).save(argThat(fc ->
                    "unknown".equals(fc.getCategory()) && !fc.isAutoRecoverable()
            ));
            verifyAuditContains(107L, "category=unknown", "auto_recoverable=false");
        }
    }

    // ============================================================
    // Non-payment.failed events and null event
    // ============================================================

    @Nested
    @DisplayName("Non-applicable events")
    class NonApplicableTests {

        @Test
        @DisplayName("Should skip classification and return null when eventType is not payment.failed")
        void classify_nonPaymentFailedEvent_skipsClassification() {
            PaymentEvent event = PaymentEvent.builder()
                    .id(108L)
                    .eventType("subscription.charged")
                    .failureReasonCode("GATEWAY_ERROR")
                    .receivedAt(Instant.now())
                    .build();

            FailureClassification result = failureClassificationService.classify(event);

            assertNull(result);
            verify(failureClassificationRepository, never()).save(any(FailureClassification.class));
            verify(auditService, never()).log(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should return null when event is null")
        void classify_nullEvent_returnsNull() {
            FailureClassification result = failureClassificationService.classify(null);

            assertNull(result);
            verify(failureClassificationRepository, never()).save(any(FailureClassification.class));
            verify(auditService, never()).log(any(), any(), any(), any(), any());
        }
    }

    // ============================================================
    // Idempotency: duplicate classification
    // ============================================================

    @Nested
    @DisplayName("Idempotency guarantees")
    class IdempotencyTests {

        @Test
        @DisplayName("Duplicate classification attempt on the same PaymentEvent does not create second row or second AuditLog")
        void classify_duplicateAttempt_returnsExistingWithoutSavingOrAuditing() {
            PaymentEvent event = buildFailedEvent(200L, "GATEWAY_ERROR");

            FailureClassification existingClassification = FailureClassification.builder()
                    .id(50L)
                    .paymentEvent(event)
                    .category("technical_decline")
                    .autoRecoverable(true)
                    .rawErrorCode("GATEWAY_ERROR")
                    .decidedAt(Instant.now().minusSeconds(60))
                    .build();

            when(failureClassificationRepository.findByPaymentEvent(event))
                    .thenReturn(Optional.of(existingClassification));

            FailureClassification result = failureClassificationService.classify(event);

            // Must return the existing one, not a new one
            assertNotNull(result);
            assertSame(existingClassification, result);
            assertEquals(50L, result.getId());
            assertEquals("technical_decline", result.getCategory());

            // Must NOT call save or auditService.log
            verify(failureClassificationRepository, never()).save(any(FailureClassification.class));
            verify(auditService, never()).log(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("First classification creates a row and audit log, second call is a no-op")
        void classify_firstThenSecond_onlyFirstCreatesRowAndAudit() {
            PaymentEvent event = buildFailedEvent(201L, "BAD_REQUEST_ERROR");

            // First call: no existing classification
            when(failureClassificationRepository.findByPaymentEvent(event))
                    .thenReturn(Optional.empty());

            FailureClassification saved = FailureClassification.builder()
                    .id(51L)
                    .paymentEvent(event)
                    .category("insufficient_funds")
                    .autoRecoverable(false)
                    .rawErrorCode("BAD_REQUEST_ERROR")
                    .decidedAt(Instant.now())
                    .build();

            when(failureClassificationRepository.save(any(FailureClassification.class)))
                    .thenReturn(saved);

            FailureClassification firstResult = failureClassificationService.classify(event);
            assertNotNull(firstResult);
            assertEquals("insufficient_funds", firstResult.getCategory());

            verify(failureClassificationRepository).save(any(FailureClassification.class));
            verify(auditService).log(eq("PAYMENT_EVENT"), eq(201L), eq("FAILURE_CLASSIFIED"),
                    eq("SYSTEM"), any(String.class));

            // Second call: classification already exists
            when(failureClassificationRepository.findByPaymentEvent(event))
                    .thenReturn(Optional.of(saved));

            FailureClassification secondResult = failureClassificationService.classify(event);
            assertNotNull(secondResult);
            assertSame(saved, secondResult);

            // save and auditService.log should still have been called only once total
            verify(failureClassificationRepository, org.mockito.Mockito.times(1))
                    .save(any(FailureClassification.class));
            verify(auditService, org.mockito.Mockito.times(1))
                    .log(any(), any(), any(), any(), any());
        }
    }

    // ============================================================
    // Audit log structured reasoning validation
    // ============================================================

    @Nested
    @DisplayName("Audit log structured reasoning")
    class AuditReasoningTests {

        @Test
        @DisplayName("Audit reasoning includes raw_error_code, category, auto_recoverable, and rule description")
        void classify_auditReasoningIsStructured() {
            PaymentEvent event = buildFailedEvent(300L, "GATEWAY_ERROR");
            stubNoExistingClassification();

            failureClassificationService.classify(event);

            ArgumentCaptor<String> reasoningCaptor = ArgumentCaptor.forClass(String.class);
            verify(auditService).log(
                    eq("PAYMENT_EVENT"),
                    eq(300L),
                    eq("FAILURE_CLASSIFIED"),
                    eq("SYSTEM"),
                    reasoningCaptor.capture()
            );

            String reasoning = reasoningCaptor.getValue();
            assertTrue(reasoning.contains("raw_error_code=GATEWAY_ERROR"),
                    "Reasoning should contain raw_error_code. Got: " + reasoning);
            assertTrue(reasoning.contains("category=technical_decline"),
                    "Reasoning should contain category. Got: " + reasoning);
            assertTrue(reasoning.contains("auto_recoverable=true"),
                    "Reasoning should contain auto_recoverable. Got: " + reasoning);
            assertTrue(reasoning.contains("rule="),
                    "Reasoning should contain rule description. Got: " + reasoning);
        }

        @Test
        @DisplayName("Null error_code audit reasoning shows (null)")
        void classify_nullErrorCode_auditShowsNull() {
            PaymentEvent event = buildFailedEvent(301L, null);
            stubNoExistingClassification();

            failureClassificationService.classify(event);

            ArgumentCaptor<String> reasoningCaptor = ArgumentCaptor.forClass(String.class);
            verify(auditService).log(any(), eq(301L), any(), any(), reasoningCaptor.capture());

            String reasoning = reasoningCaptor.getValue();
            assertTrue(reasoning.contains("raw_error_code=(null)"),
                    "Reasoning should show (null) for null error code. Got: " + reasoning);
        }
    }

    // ============================================================
    // determineCategory unit tests (direct, for exhaustiveness)
    // ============================================================

    @Nested
    @DisplayName("determineCategory — direct unit tests")
    class DetermineCategoryTests {

        @Test
        void nullInput() {
            assertEquals("unknown", failureClassificationService.determineCategory(null));
        }

        @Test
        void emptyInput() {
            assertEquals("unknown", failureClassificationService.determineCategory(""));
        }

        @Test
        void whitespaceOnly() {
            assertEquals("unknown", failureClassificationService.determineCategory("   "));
        }

        @Test
        void badRequestExact() {
            assertEquals("insufficient_funds", failureClassificationService.determineCategory("BAD_REQUEST_ERROR"));
        }

        @Test
        void badRequestLowerCase() {
            assertEquals("insufficient_funds", failureClassificationService.determineCategory("bad_request_error"));
        }

        @Test
        void gatewayErrorExact() {
            assertEquals("technical_decline", failureClassificationService.determineCategory("GATEWAY_ERROR"));
        }

        @Test
        void gatewayErrorLowerCase() {
            assertEquals("technical_decline", failureClassificationService.determineCategory("gateway_error"));
        }

        @Test
        void serverErrorExact() {
            assertEquals("technical_decline", failureClassificationService.determineCategory("SERVER_ERROR"));
        }

        @Test
        void serverErrorMixedCase() {
            assertEquals("technical_decline", failureClassificationService.determineCategory("Server_Error"));
        }

        @Test
        void containsExpired() {
            assertEquals("expired_mandate", failureClassificationService.determineCategory("card_expired"));
        }

        @Test
        void containsMandate() {
            assertEquals("expired_mandate", failureClassificationService.determineCategory("INVALID_MANDATE"));
        }

        @Test
        void containsBothExpiredAndMandate() {
            assertEquals("expired_mandate", failureClassificationService.determineCategory("MANDATE_EXPIRED"));
        }

        @Test
        void garbage() {
            assertEquals("unknown", failureClassificationService.determineCategory("xyz_garbage_123"));
        }

        @Test
        void authenticationError() {
            assertEquals("unknown", failureClassificationService.determineCategory("AUTHENTICATION_ERROR"));
        }
    }

    // ============================================================
    // Helper
    // ============================================================

    private void verifyAuditContains(Long entityId, String... substrings) {
        ArgumentCaptor<String> reasoningCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(
                eq("PAYMENT_EVENT"),
                eq(entityId),
                eq("FAILURE_CLASSIFIED"),
                eq("SYSTEM"),
                reasoningCaptor.capture()
        );
        String reasoning = reasoningCaptor.getValue();
        for (String sub : substrings) {
            assertTrue(reasoning.contains(sub),
                    "Audit reasoning should contain '" + sub + "'. Got: " + reasoning);
        }
    }
}
