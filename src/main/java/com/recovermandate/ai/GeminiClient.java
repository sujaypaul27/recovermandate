package com.recovermandate.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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

    public GeminiClient(org.springframework.boot.web.client.RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a draft recovery message for a failed payment.
     *
     * @param customerName     The name of the customer
     * @param amount           The amount of the failed payment (in smallest currency unit, e.g. paise)
     * @param currency         The currency of the failed payment
     * @param failureCategory  The category of the failure (e.g. insufficient_funds)
     * @param daysSinceFailure The number of days since the failure occurred
     * @return The drafted message, or null if the API call fails
     */
    public String generateDraft(String customerName, Long amount, String currency, String failureCategory, int daysSinceFailure) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key is not configured. Cannot generate draft.");
            return null;
        }

        try {
            double amountFormatted = amount != null ? amount / 100.0 : 0.0;
            String prompt = String.format(
                    "Write a polite, professional, and concise email to a customer informing them that their recent subscription payment failed. " +
                    "Do not offer any discounts, refunds, waivers, or free periods. Do not use aggressive or threatening language. " +
                    "Details:\n" +
                    "- Customer Name: %s\n" +
                    "- Amount: %.2f %s\n" +
                    "- Failure Category: %s\n" +
                    "- Days Since Failure: %d\n\n" +
                    "Provide only the message content.",
                    customerName, amountFormatted, currency, failureCategory, daysSinceFailure
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
                        return parts.get(0).path("text").asText().trim();
                    }
                }
            }
            log.warn("Gemini API returned unexpected response format or status: {}", response.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error("Failed to generate draft using Gemini API", e);
            return null;
        }
    }
}
