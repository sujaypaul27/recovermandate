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
        when(geminiClient.generateDraft(any(), any(), any(), any(), anyInt())).thenReturn(draft);
        when(validationService.validateDraft(draft, 5000L)).thenReturn(Optional.empty());

        RecoveryAction mockSaved = new RecoveryAction();
        mockSaved.setId(1L);
        when(recoveryActionRepository.save(any(RecoveryAction.class))).thenReturn(mockSaved);

        recoveryActionService.processFailure(classification);

        ArgumentCaptor<RecoveryAction> actionCaptor = ArgumentCaptor.forClass(RecoveryAction.class);
        verify(recoveryActionRepository).save(actionCaptor.capture());
        
        RecoveryAction savedAction = actionCaptor.getValue();
        assertEquals("DRAFTED", savedAction.getStatus());
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
        when(geminiClient.generateDraft(any(), any(), any(), any(), anyInt())).thenReturn(draft);
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
        when(geminiClient.generateDraft(any(), any(), any(), any(), anyInt())).thenReturn(heuristicDraft);
        when(geminiClient.getLastDraftSource()).thenReturn("HEURISTIC");
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
}
