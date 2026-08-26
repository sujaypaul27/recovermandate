package com.recovermandate.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryActionValidationServiceTest {

    private final RecoveryActionValidationService validationService = new RecoveryActionValidationService();

    @Test
    void testValidDraft() {
        String draft = "Hello, your payment of 100.50 INR failed. Please update your payment method.";
        Optional<String> blockReason = validationService.validateDraft(draft, 10050L);
        assertTrue(blockReason.isEmpty(), "Draft should be valid");
    }

    @Test
    void testDraftWithEmptyText() {
        Optional<String> blockReason = validationService.validateDraft("", 10050L);
        assertTrue(blockReason.isPresent());
        assertEquals("Draft message is empty", blockReason.get());
    }

    @Test
    void testDraftWithWrongAmount() {
        String draft = "Your payment of 150.00 INR failed.";
        Optional<String> blockReason = validationService.validateDraft(draft, 10050L); // Expected 100.50
        assertTrue(blockReason.isPresent());
        assertTrue(blockReason.get().contains("incorrect monetary amount"));
    }

    @Test
    void testDraftWithDenyListWord() {
        String draft = "Your payment of 100.50 INR failed. We can offer you a discount if you pay now.";
        Optional<String> blockReason = validationService.validateDraft(draft, 10050L);
        assertTrue(blockReason.isPresent());
        assertTrue(blockReason.get().contains("unauthorized offer language: discount"));
    }

    @Test
    void testDraftWithAggressiveTone() {
        String draft = "Your payment of 100.50 INR failed. If you don't pay we will sue you in court.";
        Optional<String> blockReason = validationService.validateDraft(draft, 10050L);
        assertTrue(blockReason.isPresent());
        assertTrue(blockReason.get().contains("aggressive or threatening language: sue"));
    }

    @Test
    void testDraftWithFinalNotice() {
        String draft = "This is your final notice regarding the failed payment of 100.50 INR.";
        Optional<String> blockReason = validationService.validateDraft(draft, 10050L);
        assertTrue(blockReason.isPresent());
        assertTrue(blockReason.get().contains("aggressive or threatening language: final notice"));
    }
}
