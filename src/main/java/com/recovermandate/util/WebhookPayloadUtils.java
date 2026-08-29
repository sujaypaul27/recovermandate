package com.recovermandate.util;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Utility functions for parsing Razorpay webhook payloads and JSON structures.
 */
public final class WebhookPayloadUtils {

    private WebhookPayloadUtils() {
        // Utility class
    }

    /**
     * Extracts Razorpay payment ID from a root webhook JSON node and optional payment entity node.
     */
    public static String extractPaymentId(JsonNode root, JsonNode paymentEntity) {
        if (paymentEntity != null && !paymentEntity.isMissingNode() && paymentEntity.hasNonNull("id")) {
            return paymentEntity.get("id").asText();
        }
        if (root != null) {
            if (root.hasNonNull("payment_id")) {
                return root.get("payment_id").asText();
            }
            if (root.hasNonNull("id") && root.get("id").asText().startsWith("pay_")) {
                return root.get("id").asText();
            }
        }
        return null;
    }

    /**
     * Extracts Razorpay payment ID from a root webhook JSON node.
     */
    public static String extractPaymentId(JsonNode root) {
        if (root == null) return null;
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        return extractPaymentId(root, paymentEntity);
    }
}
