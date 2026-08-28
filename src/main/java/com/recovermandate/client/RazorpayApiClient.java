package com.recovermandate.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayApiClient {

    private final ObjectMapper objectMapper;
    
    @Value("${razorpay.api.key.id:}")
    private String keyId;
    
    @Value("${razorpay.api.key.secret:}")
    private String keySecret;

    @Value("${razorpay.api.base-url:https://api.razorpay.com/v1}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<String> fetchRecentFailedPaymentEvents(Instant since) {
        if (keyId.isEmpty() || keySecret.isEmpty()) {
            log.warn("Razorpay API credentials not configured, skipping API fetch.");
            return List.of();
        }

        try {
            // NOTE: This simulates fetching recent payment.failed events from Razorpay API.
            // A real implementation would iterate pagination of /events or /payments 
            // filtered by date and status.
            String url = baseUrl + "/events?count=100&from=" + since.getEpochSecond();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(keyId, keySecret);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            List<String> rawEvents = new ArrayList<>();
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("items")) {
                for (JsonNode item : root.get("items")) {
                    if ("payment.failed".equals(item.path("event").asText())) {
                        rawEvents.add(item.toString());
                    }
                }
            }
            return rawEvents;
        } catch (Exception e) {
            log.error("Failed to fetch events from Razorpay API: {}", e.getMessage());
            throw new RuntimeException("Razorpay API fetch failed", e);
        }
    }

    /**
     * Generates a Razorpay Payment Link for manual invoice or mandate recovery.
     * If credentials are not configured, returns a simulated link for demo/testing environments.
     */
    public java.util.Map<String, String> createPaymentLink(
            Long amountInPaise,
            String currency,
            String customerEmail,
            String customerName,
            String description,
            Instant expireBy) {

        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            log.warn("Razorpay API credentials not configured. Generating simulated payment link.");
            String simId = "plink_sim_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            String simUrl = "https://rzp.io/simulated/" + java.util.UUID.randomUUID().toString().substring(0, 8);
            return java.util.Map.of("id", simId, "short_url", simUrl);
        }

        try {
            String url = baseUrl + "/payment_links";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.setBasicAuth(keyId, keySecret);

            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("amount", amountInPaise != null ? amountInPaise : 0L);
            body.put("currency", currency != null ? currency : "INR");
            body.put("description", description != null ? description : "RecoverMandate Payment");

            java.util.Map<String, String> customer = new java.util.HashMap<>();
            if (customerName != null) customer.put("name", customerName);
            if (customerEmail != null) customer.put("email", customerEmail);
            body.put("customer", customer);

            if (expireBy != null) {
                body.put("expire_by", expireBy.getEpochSecond());
            }

            body.put("notify", java.util.Map.of("email", false, "sms", false));
            body.put("callback_url", "");
            body.put("callback_method", "get");

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String linkId = root.path("id").asText();
            String shortUrl = root.path("short_url").asText();

            return java.util.Map.of("id", linkId, "short_url", shortUrl);
        } catch (Exception e) {
            log.error("Failed to create Razorpay Payment Link: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Razorpay Payment Link: " + e.getMessage(), e);
        }
    }
}
