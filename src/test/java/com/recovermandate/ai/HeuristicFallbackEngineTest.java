package com.recovermandate.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeuristicFallbackEngineTest {

    private HeuristicFallbackEngine engine;

    @BeforeEach
    void setUp() {
        engine = new HeuristicFallbackEngine();
    }

    @Test
    @DisplayName("Should generate polite template for insufficient_funds with correct currency and amount")
    void generateTemplate_insufficientFunds() {
        String draft = engine.generateTemplate("insufficient_funds", 49900L, "INR");

        assertNotNull(draft);
        assertTrue(draft.contains("Dear Customer"));
        assertTrue(draft.contains("insufficient funds"));
        assertTrue(draft.contains("499.00 INR"));
        assertTrue(draft.contains("The RecoverMandate Team"));
    }

    @Test
    @DisplayName("Should generate template for technical_decline")
    void generateTemplate_technicalDecline() {
        String draft = engine.generateTemplate("technical_decline", 150000L, "INR");

        assertNotNull(draft);
        assertTrue(draft.contains("Dear Customer"));
        assertTrue(draft.contains("temporary technical issue"));
        assertTrue(draft.contains("1500.00 INR"));
    }

    @Test
    @DisplayName("Should generate template for expired_mandate")
    void generateTemplate_expiredMandate() {
        String draft = engine.generateTemplate("expired_mandate", 25000L, "USD");

        assertNotNull(draft);
        assertTrue(draft.contains("Dear Customer"));
        assertTrue(draft.contains("expired"));
        assertTrue(draft.contains("250.00 USD"));
    }

    @Test
    @DisplayName("Should fallback to unknown template for unrecognized category")
    void generateTemplate_unknownCategory() {
        String draft = engine.generateTemplate("some_unmapped_category", 10000L, "INR");

        assertNotNull(draft);
        assertTrue(draft.contains("Dear Customer"));
        assertTrue(draft.contains("issue while processing your recent subscription payment"));
        assertTrue(draft.contains("100.00 INR"));
    }

    @Test
    @DisplayName("Should handle null category and null amount gracefully")
    void generateTemplate_nullInputs() {
        String draft = engine.generateTemplate(null, null, null);

        assertNotNull(draft);
        assertTrue(draft.contains("Dear Customer"));
        assertTrue(draft.contains("0.00 INR"));
    }
}
