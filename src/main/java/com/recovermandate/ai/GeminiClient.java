package com.recovermandate.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Client for interacting with the Gemini API to generate draft recovery messages.
 */
@Slf4j
@Service
public class GeminiClient {

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=";

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final HeuristicFallbackEngine heuristicFallbackEngine;

    public GeminiClient(org.springframework.boot.web.client.RestTemplateBuilder restTemplateBuilder,
                        ObjectMapper objectMapper,
                        HeuristicFallbackEngine heuristicFallbackEngine) {
        this.restTemplate = restTemplateBuilder.build();
        this.objectMapper = objectMapper;
        this.heuristicFallbackEngine = heuristicFallbackEngine;
    }

    /**
     * Generates a draft recovery message for a failed payment with merchant branding.
     *
     * @param customerName     The name of the customer (redacted/omitted from prompt for PII minimization)
     * @param amount           The amount of the failed payment (in smallest currency unit, e.g. paise)
     * @param currency         The currency of the failed payment
     * @param failureCategory  The category of the failure (e.g. insufficient_funds)
     * @param daysSinceFailure The number of days since the failure occurred
     * @param merchantName     The merchant / business display name
     * @return The DraftResult with message and source ("AI" or "HEURISTIC")
     */
    @CircuitBreaker(name = "geminiApi", fallbackMethod = "generateDraftFallback")
    @Retry(name = "geminiApi")
    public DraftResult generateDraft(String customerName, Long amount, String currency, String failureCategory, int daysSinceFailure, String merchantName) {
        String safeMerchant = (merchantName != null && !merchantName.isBlank()) ? merchantName.trim() : "RecoverMandate";

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key is not configured. Falling back to heuristic engine.");
            return generateDraftFallback(customerName, amount, currency, failureCategory, daysSinceFailure, safeMerchant,
                    new IllegalStateException("Gemini API key is not configured"));
        }

        try {
            double amountFormatted = amount != null ? amount / 100.0 : 0.0;
            String prompt = String.format(
                    "Write a polite, professional, and concise email from '%s' informing a customer that their recent subscription payment failed. " +
                    "Address the recipient as 'Dear Customer' and do not reference any real personal names. " +
                    "Do not offer any discounts, refunds, waivers, or free periods. Do not use aggressive or threatening language. " +
                    "Include a natural, courteous subscription cancellation / opt-out notice near the end of the message (e.g. 'If you no longer wish to continue your subscription, you can cancel anytime in your account settings or by contacting our support team.'). " +
                    "Details:\n" +
                    "- Sender / Merchant: %s\n" +
                    "- Recipient: Customer\n" +
                    "- Amount: %.2f %s\n" +
                    "- Failure Category: %s\n" +
                    "- Days Since Failure: %d\n\n" +
                    "Sign off the email with 'Sincerely,\\nThe %s Team'. " +
                    "Do not use generic placeholders like '[Your Company Name]', '[Company Name]', or '[Merchant]'. " +
                    "Provide only the message content.",
                    safeMerchant, safeMerchant, amountFormatted, currency != null ? currency : "INR", failureCategory, daysSinceFailure, safeMerchant
            );

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)
                            ))
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            String url = GEMINI_API_URL + apiKey;
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode parts = candidates.get(0).path("content").path("parts");
                    if (parts.isArray() && !parts.isEmpty()) {
                        String rawMessage = parts.get(0).path("text").asText().trim();
                        String sanitized = sanitizeDraftText(rawMessage, safeMerchant);
                        return new DraftResult(sanitized, "AI");
                    }
                }
            }
            log.warn("Gemini API returned unexpected response format or status: {}", response.getStatusCode());
            throw new RuntimeException("Unexpected response from Gemini API: " + response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to generate draft using Gemini API: {}", e.getMessage());
            throw new RuntimeException("Failed to generate draft via Gemini API", e);
        }
    }

    public DraftResult generateDraft(String customerName, Long amount, String currency, String failureCategory, int daysSinceFailure) {
        return generateDraft(customerName, amount, currency, failureCategory, daysSinceFailure, "RecoverMandate");
    }

    public static String sanitizeDraftText(String raw, String merchantName) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        String safeMerchant = (merchantName != null && !merchantName.isBlank()) ? merchantName.trim() : "RecoverMandate";

        // Strip markdown code fences if wrapped (e.g. ```markdown ... ``` or ``` ... ```)
        if (text.startsWith("```")) {
            text = text.replaceAll("^```[a-zA-Z]*\\r?\\n", "");
            text = text.replaceAll("\\r?\\n```$", "");
            text = text.trim();
        }

        // Strip conversational AI preambles (e.g. "Here is a draft recovery email:", "Sure, here is your message:")
        text = text.replaceAll("(?i)^((here (is|are)|here's|sure,?\\s*(here (is|are)|here's)?)[^\r\n]*:?\\s*\\r?\\n+)", "");
        text = text.replaceAll("(?i)^(subject:\\s*[^\r\n]*\\r?\\n+)", ""); // Strip standalone subject lines

        // Strip quotes if wrapped in entire double quotes
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
            text = text.substring(1, text.length() - 1).trim();
        }

        // Replace any lingering or hallucinated placeholders with the actual merchant branding
        text = text.replaceAll("(?i)\\[(?:Your\\s+)?Company(?:\\s+Name)?\\]", safeMerchant);
        text = text.replaceAll("(?i)\\[(?:Your\\s+)?Business(?:\\s+Name)?\\]", safeMerchant);
        text = text.replaceAll("(?i)\\[(?:Your\\s+)?Merchant(?:\\s+Name)?\\]", safeMerchant);
        text = text.replaceAll("(?i)\\[(?:Your\\s+)?Organization(?:\\s+Name)?\\]", safeMerchant);
        text = text.replaceAll("(?i)\\[(?:Your\\s+)?Brand(?:\\s+Name)?\\]", safeMerchant);
        text = text.replaceAll("(?i)\\[(?:Your\\s+)?Team\\s+Name\\]", safeMerchant + " Team");

        return text.trim();
    }

    public static String sanitizeDraftText(String raw) {
        return sanitizeDraftText(raw, "RecoverMandate");
    }

    public DraftResult generateDraftFallback(String customerName, Long amount, String currency, String failureCategory, int daysSinceFailure, String merchantName, Throwable t) {
        log.warn("Gemini API circuit breaker activated, using heuristic fallback. Cause: {}", t.getMessage());
        String template = heuristicFallbackEngine.generateTemplate(failureCategory, amount, currency, merchantName);
        return new DraftResult(template, "HEURISTIC");
    }

    public DraftResult generateDraftFallback(String customerName, Long amount, String currency, String failureCategory, int daysSinceFailure, Throwable t) {
        return generateDraftFallback(customerName, amount, currency, failureCategory, daysSinceFailure, "RecoverMandate", t);
    }
}
