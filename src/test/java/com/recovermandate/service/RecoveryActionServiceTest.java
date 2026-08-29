package com.recovermandate.service;

import com.recovermandate.ai.GeminiClient;
import com.recovermandate.audit.AuditService;
import com.recovermandate.entity.FailureClassification;
import com.recovermandate.entity.PaymentEvent;
import com.recovermandate.entity.RecoveryAction;
import com.recovermandate.repository.RecoveryActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryActionServiceTest {

    @Mock
    private GeminiClient geminiClient;
    @Mock
    private RecoveryActionValidationService validationService;
    @Mock
    private RecoveryActionRepository recoveryActionRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private SseService sseService;
    @Mock
    private PaymentLinkService paymentLinkService;
    @Mock
    private DispatchService dispatchService;
    @Mock
    private BankHealthService bankHealthService;
    @Mock
    private MerchantSettingsService merchantSettingsService;

    @InjectMocks
    private RecoveryActionService recoveryActionService;

    private FailureClassification classification;
    private PaymentEvent paymentEvent;

    @BeforeEach
    void setUp() {
        paymentEvent = new PaymentEvent();
        paymentEvent.setId(10L);
        paymentEvent.setAmount(5000L); // 50.00

        classification = new FailureClassification();
        classification.setId(100L);
        classification.setPaymentEvent(paymentEvent);
    }

    @Test
    void testProcessFailure_AutoRecoverable_SkipsDraft() {
        classification.setAutoRecoverable(true);

        recoveryActionService.processFailure(classification);

        verify(geminiClient, never()).generateDraft(any(), any(), any(), any(), anyInt());
        verify(recoveryActionRepository, never()).save(any());
    }

    @Test
    void testProcessFailure_DuplicateActionExists_SkipsAndLogsAudit() {
        classification.setAutoRecoverable(false);
        RecoveryAction existing = new RecoveryAction();
        existing.setId(99L);
        when(recoveryActionRepository.findByFailureClassification(classification)).thenReturn(Optional.of(existing));

        recoveryActionService.processFailure(classification);

        verify(geminiClient, never()).generateDraft(any(), any(), any(), any(), anyInt());
        verify(recoveryActionRepository, never()).save(any());
        verify(auditService).log(
                eq("FAILURE_CLASSIFICATION"),
                eq(100L),
                eq("DUPLICATE_RECOVERY_ACTION_SKIPPED"),
                eq("SYSTEM"),
                contains("id 100")
        );
    }

    @Test
    void testProcessFailure_GeminiApiFails_LogsAuditAndSkipsSave() {
        classification.setAutoRecoverable(false);
        when(geminiClient.generateDraft(any(), any(), any(), any(), anyInt())).thenReturn(null);

        recoveryActionService.processFailure(classification);

        verify(auditService).log(
                eq("FAILURE_CLASSIFICATION"),
                eq(100L),
                eq("AI_DRAFT_FAILED"),
                eq("SYSTEM"),
                anyString()
        );
        verify(recoveryActionRepository, never()).save(any());
    }

    @Test
    void testProcessFailure_DraftValid_SavesAsDrafted() {
        classification.setAutoRecoverable(false);
        String draft = "Valid draft message";
        when(geminiClient.generateDraft(any(), any(), any(), any(), anyInt())).thenReturn(new com.recovermandate.ai.DraftResult(draft, "AI"));
        when(validationService.validateDraft(draft, 5000L)).thenReturn(Optional.empty());

        RecoveryAction mockSaved = new RecoveryAction();
        mockSaved.setId(1L);
        when(recoveryActionRepository.save(any(RecoveryAction.class))).thenReturn(mockSaved);

        recoveryActionService.processFailure(classification);

        ArgumentCaptor<RecoveryAction> actionCaptor = ArgumentCaptor.forClass(RecoveryAction.class);
        verify(recoveryActionRepository).save(actionCaptor.capture());
        
        RecoveryAction savedAction = actionCaptor.getValue();
        assertEquals("DRAFTED", savedAction.getStatus());
        assertEquals("AI", savedAction.getDraftSource());
        assertEquals(draft, savedAction.getAiDraftMessage());
        assertEquals("SYSTEM", savedAction.getActor());
        assertNotNull(savedAction.getCreatedAt());

        verify(auditService).log(
                eq("RECOVERY_ACTION"),
                eq(1L),
                eq("AI_DRAFT_GENERATED"),
                eq("SYSTEM"),
                anyString()
        );
    }

    @Test
    void testProcessFailure_DraftInvalid_SavesAsBlocked() {
        classification.setAutoRecoverable(false);
        String draft = "Invalid draft message with discount";
        when(geminiClient.generateDraft(any(), any(), any(), any(), anyInt())).thenReturn(new com.recovermandate.ai.DraftResult(draft, "AI"));
        when(validationService.validateDraft(draft, 5000L)).thenReturn(Optional.of("Contains discount"));

        RecoveryAction mockSaved = new RecoveryAction();
        mockSaved.setId(2L);
        when(recoveryActionRepository.save(any(RecoveryAction.class))).thenReturn(mockSaved);

        recoveryActionService.processFailure(classification);

        ArgumentCaptor<RecoveryAction> actionCaptor = ArgumentCaptor.forClass(RecoveryAction.class);
        verify(recoveryActionRepository).save(actionCaptor.capture());
        
        RecoveryAction savedAction = actionCaptor.getValue();
        assertEquals("BLOCKED", savedAction.getStatus());
        assertEquals(draft, savedAction.getAiDraftMessage());

        verify(auditService).log(
                eq("RECOVERY_ACTION"),
                eq(2L),
                eq("AI_DRAFT_BLOCKED"),
                eq("SYSTEM"),
                contains("Contains discount")
        );
    }

    @Test
    void testProcessFailure_HeuristicFallback_RecordsHeuristicSource() {
        classification.setAutoRecoverable(false);
        String heuristicDraft = "Dear Customer,\n\nWe were unable to process your payment.";
        when(geminiClient.generateDraft(any(), any(), any(), any(), anyInt())).thenReturn(new com.recovermandate.ai.DraftResult(heuristicDraft, "HEURISTIC"));
        when(validationService.validateDraft(heuristicDraft, 5000L)).thenReturn(Optional.empty());

        RecoveryAction mockSaved = new RecoveryAction();
        mockSaved.setId(3L);
        when(recoveryActionRepository.save(any(RecoveryAction.class))).thenReturn(mockSaved);

        recoveryActionService.processFailure(classification);

        ArgumentCaptor<RecoveryAction> actionCaptor = ArgumentCaptor.forClass(RecoveryAction.class);
        verify(recoveryActionRepository).save(actionCaptor.capture());

        RecoveryAction savedAction = actionCaptor.getValue();
        assertEquals("DRAFTED", savedAction.getStatus());
        assertEquals("HEURISTIC", savedAction.getDraftSource());
        assertEquals(heuristicDraft, savedAction.getAiDraftMessage());

        verify(auditService).log(
                eq("RECOVERY_ACTION"),
                eq(3L),
                eq("AI_DRAFT_GENERATED"),
                eq("SYSTEM"),
                contains("via HEURISTIC")
        );
    }

    @Test
    void testApproveAndDispatch_GeneratesLinkAndDispatchesEmail() {
        Long actionId = 55L;
        RecoveryAction action = new RecoveryAction();
        action.setId(actionId);
        action.setStatus("DRAFTED");

        when(recoveryActionRepository.findById(actionId)).thenReturn(Optional.of(action));
        when(recoveryActionRepository.save(any(RecoveryAction.class))).thenAnswer(i -> i.getArgument(0));

        com.recovermandate.entity.PaymentLink paymentLink = com.recovermandate.entity.PaymentLink.builder()
                .id(1L)
                .shortUrl("https://rzp.io/l/testLink")
                .build();
        when(paymentLinkService.createLinkForRecoveryAction(action)).thenReturn(paymentLink);

        RecoveryAction result = recoveryActionService.approveAndDispatch(actionId, "ADMIN_USER");

        assertEquals("DISPATCHED", result.getStatus());
        assertEquals("ADMIN_USER", result.getApprovedBy());
        assertNotNull(result.getSentAt());
        assertNotNull(result.getApprovedAt());

        verify(paymentLinkService).createLinkForRecoveryAction(action);
        verify(dispatchService).dispatchRecovery(action, "https://rzp.io/l/testLink");
        verify(auditService).log(
                eq("RECOVERY_ACTION"),
                eq(actionId),
                eq("ACTION_DISPATCHED"),
                eq("ADMIN_USER"),
                contains("https://rzp.io/l/testLink")
        );
    }

    @Test
    void testGetRecoveryActions_PopulatesExplainabilityFields() {
        PaymentEvent pe = new PaymentEvent();
        pe.setId(10L);
        pe.setRazorpayPaymentId("pay_abc123");
        pe.setFailureReasonCode("BAD_REQUEST_ERROR");
        pe.setAmount(99900L);

        FailureClassification fc = new FailureClassification();
        fc.setId(100L);
        fc.setCategory("insufficient_funds");
        fc.setAutoRecoverable(false);
        fc.setRawErrorCode("BAD_REQUEST_ERROR");
        fc.setPaymentEvent(pe);

        RecoveryAction action = new RecoveryAction();
        action.setId(1000L);
        action.setFailureClassification(fc);
        action.setStatus("DRAFTED");
        action.setDraftSource("GEMINI");
        action.setAiDraftMessage("Test draft message");

        when(bankHealthService.extractBankCode(pe)).thenReturn("HDFC");
        when(recoveryActionRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(action)));

        org.springframework.data.domain.Page<com.recovermandate.dto.RecoveryActionResponse> page =
                recoveryActionService.getRecoveryActions(null, org.springframework.data.domain.PageRequest.of(0, 10));

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
        com.recovermandate.dto.RecoveryActionResponse resp = page.getContent().get(0);
        assertEquals(1000L, resp.getId());
        assertEquals("BAD_REQUEST_ERROR", resp.getRawErrorCode());
        assertEquals("insufficient_funds", resp.getCategory());
        assertEquals(false, resp.getAutoRecoverable());
        assertEquals("HDFC", resp.getBank());
        assertEquals("pay_abc123", resp.getRazorpayPaymentId());
        assertEquals(99900L, resp.getAmount());
        assertNotNull(resp.getMatchedRule());
        assertTrue(resp.getMatchedRule().contains("insufficient_funds"));
    }

    @Test
    void testProcessFailure_AutoPilotEligible_AutoDispatches() {
        classification.setCategory("insufficient_funds");
        classification.setAutoRecoverable(false);

        when(geminiClient.generateDraft(any(), any(), any(), any(), anyInt()))
                .thenReturn(new com.recovermandate.ai.DraftResult("Valid draft with no issues", "GEMINI"));
        when(validationService.validateDraft(any(), any())).thenReturn(Optional.empty()); // passed validation
        when(recoveryActionRepository.save(any(RecoveryAction.class))).thenAnswer(i -> {
            RecoveryAction a = i.getArgument(0);
            a.setId(99L);
            return a;
        });

        // Configure auto-pilot to be eligible
        when(merchantSettingsService.isAutoPilotEligible(5000L, "insufficient_funds")).thenReturn(true);
        com.recovermandate.dto.MerchantSettingsDto settingsDto = com.recovermandate.dto.MerchantSettingsDto.builder()
                .defaultTone("gentle")
                .autoPilotEnabled(true)
                .build();
        when(merchantSettingsService.getSettings()).thenReturn(settingsDto);

        when(recoveryActionRepository.findById(99L)).thenReturn(Optional.of(
                RecoveryAction.builder().id(99L).status("DRAFTED").build()
        ));

        com.recovermandate.entity.PaymentLink link = com.recovermandate.entity.PaymentLink.builder()
                .id(1L).shortUrl("https://rzp.io/l/auto1").build();
        when(paymentLinkService.createLinkForRecoveryAction(any())).thenReturn(link);

        recoveryActionService.processFailure(classification);

        verify(paymentLinkService).createLinkForRecoveryAction(any());
        verify(dispatchService).dispatchRecovery(any(), eq("https://rzp.io/l/auto1"));
        verify(auditService).log(eq("RECOVERY_ACTION"), eq(99L), eq("AUTO_PILOT_DISPATCHED"), eq("AUTO_PILOT"), contains("GENTLE"));
    }

    @Test
    void testProcessFailure_ValidationFails_BlocksAndDoesNotAutoDispatch() {
        classification.setCategory("insufficient_funds");
        classification.setAutoRecoverable(false);

        when(geminiClient.generateDraft(any(), any(), any(), any(), anyInt()))
                .thenReturn(new com.recovermandate.ai.DraftResult("Draft containing prohibited leak", "GEMINI"));
        when(validationService.validateDraft(any(), any())).thenReturn(Optional.of("Blocked by keyword deny-list"));
        when(recoveryActionRepository.save(any(RecoveryAction.class))).thenAnswer(i -> {
            RecoveryAction a = i.getArgument(0);
            a.setId(99L);
            return a;
        });

        recoveryActionService.processFailure(classification);

        // Verification: Auto-pilot must NEVER be called if validation gate blocks the draft
        verify(paymentLinkService, never()).createLinkForRecoveryAction(any());
        verify(dispatchService, never()).dispatchRecovery(any(), any());
        verify(auditService).log(eq("RECOVERY_ACTION"), eq(99L), eq("AI_DRAFT_BLOCKED"), eq("SYSTEM"), contains("Blocked by keyword deny-list"));
    }

    @Test
    void testBatchApprove_ProcessesAllWithIsolation() {
        RecoveryAction action1 = RecoveryAction.builder().id(1L).status("DRAFTED").build();
        RecoveryAction action2 = RecoveryAction.builder().id(2L).status("DRAFTED").build();

        when(recoveryActionRepository.findById(1L)).thenReturn(Optional.of(action1));
        when(recoveryActionRepository.findById(2L)).thenThrow(new RuntimeException("DB Timeout on item 2"));
        when(recoveryActionRepository.save(any(RecoveryAction.class))).thenAnswer(i -> i.getArgument(0));

        com.recovermandate.entity.PaymentLink link = com.recovermandate.entity.PaymentLink.builder()
                .id(1L).shortUrl("https://rzp.io/l/batch1").build();
        when(paymentLinkService.createLinkForRecoveryAction(any())).thenReturn(link);

        com.recovermandate.dto.BatchApproveRequest req = com.recovermandate.dto.BatchApproveRequest.builder()
                .actionIds(java.util.List.of(1L, 2L))
                .tone("balanced")
                .approvedBy("MERCHANT_BATCH")
                .build();

        com.recovermandate.dto.BatchApproveResponse response = recoveryActionService.batchApprove(req);

        assertNotNull(response);
        assertEquals(2, response.getTotalRequested());
        assertEquals(1, response.getSuccessful());
        assertEquals(1, response.getFailed());
        assertEquals(java.util.List.of(1L), response.getApprovedActionIds());
        assertEquals(1, response.getErrors().size());
        assertEquals(2L, response.getErrors().get(0).getActionId());
    }
}
