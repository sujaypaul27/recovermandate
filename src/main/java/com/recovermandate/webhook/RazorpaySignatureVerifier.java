package com.recovermandate.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verifies Razorpay webhook signatures using HMAC-SHA256 with constant-time comparison.
 */
@Slf4j
@Component
public class RazorpaySignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Value("${razorpay.webhook.secret:${RAZORPAY_WEBHOOK_SECRET:${RAZORPAY_SECRET:}}}")
    private String secret;

    public RazorpaySignatureVerifier() {
    }

    public RazorpaySignatureVerifier(String secret) {
        this.secret = secret;
    }

    public String getEffectiveSecret() {
        if (secret != null && !secret.isBlank()) return secret.trim();
        String sp1 = System.getProperty("razorpay.webhook.secret");
        if (sp1 != null && !sp1.isBlank()) return sp1.trim();
        String sp2 = System.getProperty("RAZORPAY_WEBHOOK_SECRET");
        if (sp2 != null && !sp2.isBlank()) return sp2.trim();

        String env1 = System.getenv("RAZORPAY_WEBHOOK_SECRET");
        if (env1 != null && !env1.isBlank()) return env1.trim();
        String env2 = System.getenv("RAZORPAY_SECRET");
        if (env2 != null && !env2.isBlank()) return env2.trim();
        String env3 = System.getenv("razorpay.webhook.secret");
        if (env3 != null && !env3.isBlank()) return env3.trim();
        return "";
    }

    @jakarta.annotation.PostConstruct
    public void logStartupSecretStatus() {
        String effective = getEffectiveSecret();
        boolean isConfigured = !effective.isBlank();
        log.info("RAZORPAY WEBHOOK SECRET CHECK AT STARTUP: configured={}", isConfigured);
    }

    /**
     * Verifies the given payload and signature using the configured webhook secret.
     *
     * @param payload   raw request body as a string
     * @param signature value of the X-Razorpay-Signature header
     * @return true if signature is valid, false otherwise
     */
    public boolean verify(String payload, String signature) {
        return verify(payload, signature, getEffectiveSecret());
    }

    /**
     * Verifies the given payload and signature using a specific secret.
     *
     * @param payload   raw request body as a string
     * @param signature value of the X-Razorpay-Signature header
     * @param secretKey secret key used to compute HMAC-SHA256
     * @return true if signature is valid, false otherwise
     */
    public boolean verify(String payload, String signature, String secretKey) {
        if (payload == null || signature == null || secretKey == null || secretKey.isBlank() || signature.isBlank()) {
            log.warn("Webhook signature verification failed: missing payload, signature, or secret");
            return false;
        }

        try {
            String calculatedSignature = calculateHmacSha256(payload, secretKey);
            byte[] expectedBytes = calculatedSignature.toLowerCase().getBytes(StandardCharsets.UTF_8);
            byte[] actualBytes = signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);

            return MessageDigest.isEqual(expectedBytes, actualBytes);
        } catch (Exception e) {
            log.error("Error during webhook signature calculation", e);
            return false;
        }
    }

    /**
     * Calculates HMAC-SHA256 hex string for a given payload and secret.
     *
     * @param payload   the string payload
     * @param secretKey secret key
     * @return lowercase hex string of HMAC-SHA256
     */
    String calculateHmacSha256(String payload, String secretKey)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
