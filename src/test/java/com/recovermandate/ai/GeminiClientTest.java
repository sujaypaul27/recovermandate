package com.recovermandate.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiClientTest {

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private HeuristicFallbackEngine heuristicFallbackEngine;

    private GeminiClient geminiClient;

    @BeforeEach
    void setUp() {
        lenient().when(restTemplateBuilder.setConnectTimeout(any())).thenReturn(restTemplateBuilder);
        lenient().when(restTemplateBuilder.setReadTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(new org.springframework.web.client.RestTemplate());
        geminiClient = new GeminiClient(restTemplateBuilder, new ObjectMapper(), heuristicFallbackEngine);
    }

    @Test
    @DisplayName("Should invoke heuristic fallback when API key is missing and mark source as HEURISTIC")
    void generateDraft_missingApiKey_usesFallback() {
        when(heuristicFallbackEngine.generateTemplate(eq("insufficient_funds"), eq(49900L), eq("INR"), anyString()))
                .thenReturn("Dear Customer,\n\nFallback draft");

        DraftResult draft = geminiClient.generateDraft("John Doe", 49900L, "INR", "insufficient_funds", 2);

        assertNotNull(draft);
        assertEquals("Dear Customer,\n\nFallback draft", draft.message());
        assertEquals("HEURISTIC", draft.source());
        verify(heuristicFallbackEngine).generateTemplate("insufficient_funds", 49900L, "INR", "RecoverMandate");
    }

    @Test
    @DisplayName("Should invoke generateDraftFallback directly on error")
    void generateDraftFallback_setsDraftSourceToHeuristic() {
        when(heuristicFallbackEngine.generateTemplate(eq("technical_decline"), eq(10000L), eq("INR"), anyString()))
                .thenReturn("Dear Customer,\n\nTechnical fallback");

        DraftResult draft = geminiClient.generateDraftFallback(
                "Sensitive Customer Name",
                10000L,
                "INR",
                "technical_decline",
                0,
                new RuntimeException("503 Service Unavailable")
        );

        assertNotNull(draft);
        assertEquals("HEURISTIC", draft.source());
        assertEquals("Dear Customer,\n\nTechnical fallback", draft.message());
    }

    @Test
    @DisplayName("Should sanitize LLM responses by stripping markdown fences, conversational intro, and quotes")
    void sanitizeDraftText_cleansOutput() {
        String rawWithIntro = "Here is a draft recovery email:\n\nDear Customer,\nYour payment failed.";
        assertEquals("Dear Customer,\nYour payment failed.", GeminiClient.sanitizeDraftText(rawWithIntro));

        String rawWithMarkdown = "```markdown\nDear Customer,\nYour subscription payment of Rs 499 failed.\n```";
        assertEquals("Dear Customer,\nYour subscription payment of Rs 499 failed.", GeminiClient.sanitizeDraftText(rawWithMarkdown));

        String rawWithSubject = "Subject: Action Required: Payment Failed\n\nDear Customer,\nPlease update your card.";
        assertEquals("Dear Customer,\nPlease update your card.", GeminiClient.sanitizeDraftText(rawWithSubject));

        String rawWithQuotes = "\"Dear Customer,\nPlease retry payment.\"";
        assertEquals("Dear Customer,\nPlease retry payment.", GeminiClient.sanitizeDraftText(rawWithQuotes));
    }
}
