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
import java.util.UUID;

/**
 * HTTP client for interacting with the Razorpay API v1.
 * <p>
 * Operates in two runtime modes based on credential configuration:
 * <ul>
 *   <li><b>Live Gateway Mode:</b> Invokes actual Razorpay endpoints ({@code /v1/payment_links}, {@code /v1/payments})
 *       when valid credentials are supplied. Uses deterministic idempotency keys to guarantee at-most-once creation.</li>
 *   <li><b>Simulated Sandbox Mode:</b> Automatically engages when credentials are missing or during dry-run testing,
 *       generating realistic mock payment link tokens ({@code plink_sim_...}) mapped to the local hosted demo checkout.</li>
 * </ul>
 */
@Slf4j
@Component
public class RazorpayApiClient {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    
    @Value("${razorpay.api.key.id:${razorpay.api.key-id:${RAZORPAY_KEY_ID:${RAZORPAY_API_KEY_ID:${RAZORPAY_KEY:}}}}}")
    private String keyId;
    
    @Value("${razorpay.api.key.secret:${razorpay.api.key-secret:${RAZORPAY_KEY_SECRET:${RAZORPAY_API_KEY_SECRET:${RAZORPAY_SECRET:}}}}}")
    private String keySecret;

    @Value("${razorpay.api.base-url:https://api.razorpay.com/v1}")
    private String baseUrl;

    @Value("${recovermandate.app-url:http://localhost:5173}")
    private String appUrl;

    public RazorpayApiClient(
            org.springframework.boot.web.client.RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(java.time.Duration.ofSeconds(5))
                .setReadTimeout(java.time.Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
    }

    public String getEffectiveKeyId() {
        if (keyId != null && !keyId.isBlank()) return keyId.trim();
        String sp1 = System.getProperty("razorpay.api.key.id");
        if (sp1 != null && !sp1.isBlank()) return sp1.trim();
        String sp2 = System.getProperty("razorpay.api.key-id");
        if (sp2 != null && !sp2.isBlank()) return sp2.trim();
        String sp3 = System.getProperty("RAZORPAY_KEY_ID");
        if (sp3 != null && !sp3.isBlank()) return sp3.trim();

        String env1 = System.getenv("RAZORPAY_KEY_ID");
        if (env1 != null && !env1.isBlank()) return env1.trim();
        String env2 = System.getenv("RAZORPAY_API_KEY_ID");
        if (env2 != null && !env2.isBlank()) return env2.trim();
        String env3 = System.getenv("RAZORPAY_KEY");
        if (env3 != null && !env3.isBlank()) return env3.trim();
        String env4 = System.getenv("razorpay.api.key.id");
        if (env4 != null && !env4.isBlank()) return env4.trim();
        return "";
    }

    public String getEffectiveKeySecret() {
        if (keySecret != null && !keySecret.isBlank()) return keySecret.trim();
        String sp1 = System.getProperty("razorpay.api.key.secret");
        if (sp1 != null && !sp1.isBlank()) return sp1.trim();
        String sp2 = System.getProperty("razorpay.api.key-secret");
        if (sp2 != null && !sp2.isBlank()) return sp2.trim();
        String sp3 = System.getProperty("RAZORPAY_KEY_SECRET");
        if (sp3 != null && !sp3.isBlank()) return sp3.trim();

        String env1 = System.getenv("RAZORPAY_KEY_SECRET");
        if (env1 != null && !env1.isBlank()) return env1.trim();
        String env2 = System.getenv("RAZORPAY_API_KEY_SECRET");
        if (env2 != null && !env2.isBlank()) return env2.trim();
        String env3 = System.getenv("RAZORPAY_SECRET");
        if (env3 != null && !env3.isBlank()) return env3.trim();
        String env4 = System.getenv("razorpay.api.key.secret");
        if (env4 != null && !env4.isBlank()) return env4.trim();
        return "";
    }

    public boolean isLiveMode() {
        String effectiveKeyId = getEffectiveKeyId();
        return effectiveKeyId.startsWith("rzp_live_") && !getEffectiveKeySecret().isBlank();
    }

    public boolean isApiConfigured() {
        return !getEffectiveKeyId().isBlank() && !getEffectiveKeySecret().isBlank();
    }

    public String getFrontendUrl() {
        String envFrontend = System.getenv("FRONTEND_URL");
        if (envFrontend != null && !envFrontend.isBlank()) {
            return envFrontend.replaceAll("/+$", "");
        }
        String envApp = System.getenv("APP_URL");
        if (envApp != null && !envApp.isBlank()) {
            return envApp.replaceAll("/+$", "");
        }
        if (appUrl != null && !appUrl.isBlank()) {
            return appUrl.replaceAll("/+$", "");
        }
        return "http://localhost:5173";
    }

    @jakarta.annotation.PostConstruct
    public void logStartupCredentialStatus() {
        String effectiveKeyId = getEffectiveKeyId();
        String effectiveKeySecret = getEffectiveKeySecret();
        boolean keyIdConfigured = !effectiveKeyId.isBlank();
        boolean keySecretConfigured = !effectiveKeySecret.isBlank();
        String maskedKey = keyIdConfigured 
                ? (effectiveKeyId.length() >= 8 ? effectiveKeyId.substring(0, 8) + "..." : effectiveKeyId)
                : "[BLANK]";

        log.info("==========================================================================================");
        log.info("RAZORPAY CREDENTIAL CHECK AT STARTUP: keyId={} configured={}, keySecret configured={}",
                maskedKey, keyIdConfigured, keySecretConfigured);
        log.info("RAZORPAY API CLIENT MODE: {}", (keyIdConfigured && keySecretConfigured ? "LIVE RAZORPAY API" : "SIMULATED SANDBOX MODE"));
        log.info("==========================================================================================");
    }

    private String maskKey(String key) {
        if (key == null || key.isBlank()) return "BLANK";
        if (key.length() <= 8) return "****";
        return key.substring(0, 8) + "..." + key.substring(key.length() - 4);
    }

    public List<String> fetchRecentFailedPaymentEvents(Instant since) {
        String effectiveKeyId = getEffectiveKeyId();
        String effectiveKeySecret = getEffectiveKeySecret();

        if (effectiveKeyId.isBlank() || effectiveKeySecret.isBlank()) {
            log.info("Using SIMULATED mode for fetching failed events — credentials missing: RAZORPAY_KEY_ID is '{}', RAZORPAY_KEY_SECRET configured={}",
                    maskKey(effectiveKeyId), !effectiveKeySecret.isBlank());
            return List.of();
        }

        try {
            String url = baseUrl + "/payments?count=100&from=" + since.getEpochSecond();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(effectiveKeyId, effectiveKeySecret);
            
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class);
            
            JsonNode root = objectMapper.readTree(response.getBody());
            List<String> rawEvents = new java.util.ArrayList<>();
            if (root.has("items")) {
                for (JsonNode item : root.get("items")) {
                    String status = item.path("status").asText();
                    if ("failed".equalsIgnoreCase(status)) {
                        java.util.Map<String, Object> eventWrapper = java.util.Map.of(
                                "entity", "event",
                                "event", "payment.failed",
                                "contains", java.util.List.of("payment"),
                                "payload", java.util.Map.of(
                                        "payment", java.util.Map.of("entity", item)
                                )
                        );
                        rawEvents.add(objectMapper.writeValueAsString(eventWrapper));
                    }
                }
            }
            return rawEvents;
        } catch (Exception e) {
            log.warn("Notice: Razorpay API payment poll returned notice (relying on webhooks and real-time link sync): {}", e.getMessage());
            return java.util.List.of();
        }
    }

    /**
     * Generates a Razorpay Payment Link with deterministic idempotency reference for mandate recovery.
     */
    public java.util.Map<String, String> createPaymentLink(
            Long amountInPaise,
            String currency,
            String customerEmail,
            String customerName,
            String description,
            Instant expireBy) {
        return createPaymentLink(amountInPaise, currency, customerEmail, customerName, description, expireBy, null);
    }

    public java.util.Map<String, String> createPaymentLink(
            Long amountInPaise,
            String currency,
            String customerEmail,
            String customerName,
            String description,
            Instant expireBy,
            String referenceId) {

        String effectiveKeyId = getEffectiveKeyId();
        String effectiveKeySecret = getEffectiveKeySecret();
        boolean keyIdConfigured = !effectiveKeyId.isBlank();
        boolean keySecretConfigured = !effectiveKeySecret.isBlank();
        String maskedKey = keyIdConfigured 
                ? (effectiveKeyId.length() >= 8 ? effectiveKeyId.substring(0, 8) + "..." : effectiveKeyId)
                : "[BLANK]";

        if (!keyIdConfigured || !keySecretConfigured) {
            log.info("createPaymentLink() -> Decision: SIMULATED branch taken (keyId='{}' configured={}, keySecret configured={})",
                    maskedKey, keyIdConfigured, keySecretConfigured);
            String simId = "plink_sim_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            String frontendUrl = getFrontendUrl();
            String simUrl = frontendUrl + "/#/pay/" + simId;
            log.info("Simulated Payment Link generated: id={}, short_url={}", simId, simUrl);
            return java.util.Map.of("id", simId, "short_url", simUrl);
        }

        log.info("createPaymentLink() -> Decision: LIVE branch taken (keyId='{}' configured={}, keySecret configured={}, baseUrl='{}')",
                maskedKey, keyIdConfigured, keySecretConfigured, baseUrl);

        String url = baseUrl + "/payment_links";
        HttpEntity<String> entity = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.setBasicAuth(effectiveKeyId, effectiveKeySecret);
            if (referenceId != null && !referenceId.isBlank()) {
                headers.set("Idempotency-Key", referenceId);
            }

            java.util.Map<String, Object> body = new java.util.HashMap<>();
            long safeAmount = (amountInPaise != null && amountInPaise >= 100) ? amountInPaise : 49900L;
            body.put("amount", safeAmount);
            body.put("currency", currency != null && !currency.isBlank() ? currency : "INR");
            body.put("description", description != null ? description : "RecoverMandate Mandate Recovery");

            String uniqueRef = (referenceId != null && !referenceId.isBlank())
                    ? referenceId + "_" + (System.currentTimeMillis() % 1000000)
                    : "rec_" + java.util.UUID.randomUUID().toString().substring(0, 12);
            body.put("reference_id", uniqueRef);

            java.util.Map<String, String> customer = new java.util.HashMap<>();
            if (customerName != null && !customerName.isBlank()) customer.put("name", customerName);
            if (customerEmail != null && !customerEmail.isBlank() && !customerEmail.endsWith("@example.com")) {
                customer.put("email", customerEmail);
            }
            if (!customer.isEmpty()) {
                body.put("customer", customer);
            }

            if (expireBy != null) {
                body.put("expire_by", expireBy.getEpochSecond());
            }

            body.put("notify", java.util.Map.of("email", false, "sms", false));

            log.info("Dispatching POST request to Razorpay live API: url={}, amount=₹{}, email={}, ref={}",
                    url, safeAmount / 100.0, customerEmail, uniqueRef);

            entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String linkId = root.path("id").asText();
            String shortUrl = root.path("short_url").asText();

            // If short_url is missing or empty in Test/Demo mode, fall back to local demo payment link
            if (shortUrl == null || shortUrl.isBlank() || "null".equals(shortUrl)) {
                if (!isLiveMode()) {
                    log.warn("Razorpay test limit reached or API failed. Falling back to local demo payment link.");
                    String frontendUrl = getFrontendUrl();
                    String fallbackId = "plink_quota_" + System.currentTimeMillis();
                    String fallbackUrl = frontendUrl + "/#/pay/" + fallbackId;
                    return java.util.Map.of("id", fallbackId, "short_url", fallbackUrl);
                }
            }

            log.info("Successfully created LIVE Razorpay Payment Link: id={}, short_url={}", linkId, shortUrl);
            return java.util.Map.of("id", linkId, "short_url", shortUrl);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("Failed to create Razorpay Payment Link via live API. HTTP Status: {}, Response: {}",
                    e.getStatusCode(), errorBody);

            // In Live Mode (rzp_live_...), strict error handling is preserved:
            if (isLiveMode()) {
                throw new RuntimeException("Razorpay Live API Link Creation Failed [" + e.getStatusCode() + "]: " + errorBody, e);
            }

            // In Test/Demo Mode: Attempt to auto-free 30-link test quota and retry once
            if (e.getStatusCode() == org.springframework.http.HttpStatus.TOO_MANY_REQUESTS
                    || (errorBody != null && errorBody.contains("test mode limit of 30 reached"))) {
                log.warn("Razorpay test mode limit (30 payment links) reached. Attempting to free quota by cancelling older test links...");
                boolean quotaFreed = freeUpTestPaymentLinkQuota(effectiveKeyId, effectiveKeySecret);
                if (quotaFreed && entity != null) {
                    try {
                        log.info("Retrying Razorpay live payment link creation after freeing quota...");
                        ResponseEntity<String> retryResponse = restTemplate.postForEntity(url, entity, String.class);
                        JsonNode retryRoot = objectMapper.readTree(retryResponse.getBody());
                        String linkId = retryRoot.path("id").asText();
                        String shortUrl = retryRoot.path("short_url").asText();
                        if (shortUrl != null && !shortUrl.isBlank() && !"null".equals(shortUrl)) {
                            log.info("Successfully created LIVE Razorpay Payment Link on retry: id={}, short_url={}", linkId, shortUrl);
                            return java.util.Map.of("id", linkId, "short_url", shortUrl);
                        }
                    } catch (Exception retryEx) {
                        log.warn("Retry link creation failed after quota clean: {}", retryEx.getMessage());
                    }
                }
            }

            // Fallback for Demo / Test Mode:
            log.warn("Razorpay test limit reached or API failed. Falling back to local demo payment link.");
            String frontendUrl = getFrontendUrl();
            String fallbackId = "plink_quota_" + System.currentTimeMillis();
            String fallbackUrl = frontendUrl + "/#/pay/" + fallbackId;
            return java.util.Map.of("id", fallbackId, "short_url", fallbackUrl);

        } catch (Exception e) {
            log.error("Failed to create Razorpay Payment Link: {}", e.getMessage(), e);
            if (isLiveMode()) {
                throw new RuntimeException("Failed to create Razorpay Payment Link: " + e.getMessage(), e);
            }
            log.warn("Razorpay test limit reached or API failed. Falling back to local demo payment link.");
            String frontendUrl = getFrontendUrl();
            String fallbackId = "plink_quota_" + System.currentTimeMillis();
            String fallbackUrl = frontendUrl + "/#/pay/" + fallbackId;
            return java.util.Map.of("id", fallbackId, "short_url", fallbackUrl);
        }
    }

    /**
     * In Razorpay Test Mode, accounts are restricted to 30 active payment links.
     * This method cancels older active test links so new links can be created seamlessly.
     */
    public boolean freeUpTestPaymentLinkQuota(String effectiveKeyId, String effectiveKeySecret) {
        try {
            String listUrl = baseUrl + "/payment_links?count=20";
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(effectiveKeyId, effectiveKeySecret);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(listUrl, org.springframework.http.HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode items = root.path("payment_links");
                int cancelledCount = 0;
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String status = item.path("status").asText();
                        String linkId = item.path("id").asText();
                        if ("created".equalsIgnoreCase(status) || "partially_paid".equalsIgnoreCase(status)) {
                            if (cancelPaymentLink(linkId)) {
                                cancelledCount++;
                                log.info("Successfully cancelled older test payment link {} to free up quota", linkId);
                                if (cancelledCount >= 2) {
                                    break; // Cancel at least 2 links to give comfortable headroom
                                }
                            }
                        }
                    }
                }
                return cancelledCount > 0;
            }
        } catch (Exception e) {
            log.warn("Could not auto-cancel older test payment link to free up quota: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Cancels an active Razorpay Payment Link when automated retry succeeds.
     */
    public boolean cancelPaymentLink(String razorpayLinkId) {
        if (razorpayLinkId == null || razorpayLinkId.isBlank()) return false;

        String effectiveKeyId = getEffectiveKeyId();
        String effectiveKeySecret = getEffectiveKeySecret();

        if (effectiveKeyId.isBlank() || effectiveKeySecret.isBlank()) {
            log.info("Simulated cancellation for payment link {}", razorpayLinkId);
            return true;
        }

        try {
            String url = baseUrl + "/payment_links/" + razorpayLinkId + "/cancel";
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(effectiveKeyId, effectiveKeySecret);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Failed to cancel Razorpay Payment Link {}: {}", razorpayLinkId, e.getMessage());
            return false;
        }
    }

    /**
     * Fetches real-time status of a Payment Link directly from Razorpay API.
     */
    public JsonNode fetchPaymentLink(String razorpayLinkId) {
        if (razorpayLinkId == null || razorpayLinkId.isBlank() || razorpayLinkId.startsWith("plink_sim_") || razorpayLinkId.startsWith("plink_preview_")) {
            return null;
        }

        String effectiveKeyId = getEffectiveKeyId();
        String effectiveKeySecret = getEffectiveKeySecret();

        if (effectiveKeyId.isBlank() || effectiveKeySecret.isBlank()) {
            return null;
        }

        try {
            String url = baseUrl + "/payment_links/" + razorpayLinkId;
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(effectiveKeyId, effectiveKeySecret);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readTree(response.getBody());
            }
        } catch (Exception e) {
            log.debug("Notice: Could not fetch Razorpay live status for payment link {}: {}", razorpayLinkId, e.getMessage());
        }
        return null;
    }
}
