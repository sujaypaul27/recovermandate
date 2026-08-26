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
            String auth = keyId + ":" + keySecret;
            headers.setBasicAuth(Base64.getEncoder().encodeToString(auth.getBytes()));
            
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
}
