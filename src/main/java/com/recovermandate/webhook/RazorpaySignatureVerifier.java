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

    @Value("${razorpay.webhook.secret:}")
    private String secret;

    public RazorpaySignatureVerifier() {
    }

    public RazorpaySignatureVerifier(String secret) {
        this.secret = secret;
    }

    /**
     * Verifies the given payload and signature using the configured webhook secret.
     *
     * @param payload   raw request body as a string
     * @param signature value of the X-Razorpay-Signature header
     * @return true if signature is valid, false otherwise
     */
    public boolean verify(String payload, String signature) {
        return verify(payload, signature, this.secret);
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
