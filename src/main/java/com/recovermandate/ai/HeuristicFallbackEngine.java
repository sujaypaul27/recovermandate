package com.recovermandate.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * Deterministic template-based fallback engine for generating recovery message drafts
 * when the external AI engine (Gemini API) is unavailable or circuit-broken.
 */
@Slf4j
@Service
public class HeuristicFallbackEngine {

    private static final Map<String, String> TEMPLATES = Map.of(
            "insufficient_funds",
            "Dear Customer,\n\n" +
            "We were unable to process your scheduled subscription payment of %.2f %s due to insufficient funds in your account. " +
            "Please ensure your account has adequate balance so the payment can be completed successfully. " +
            "You can update your payment method or retry the transaction through your account portal.\n\n" +
            "Sincerely,\n" +
            "The RecoverMandate Team",

            "technical_decline",
            "Dear Customer,\n\n" +
            "Your scheduled subscription payment of %.2f %s could not be completed due to a temporary technical issue with your bank or payment gateway. " +
            "An automatic retry is underway, so no immediate action is required on your part. " +
            "If the issue persists, you may verify your payment details in your portal.\n\n" +
            "Sincerely,\n" +
            "The RecoverMandate Team",

            "expired_mandate",
            "Dear Customer,\n\n" +
            "Your scheduled subscription payment of %.2f %s could not be processed because your recurring payment mandate or card has expired. " +
            "Please re-authorize your payment method or set up a new mandate to avoid any disruption to your service.\n\n" +
            "Sincerely,\n" +
            "The RecoverMandate Team",

            "unknown",
            "Dear Customer,\n\n" +
            "We encountered an issue while processing your recent subscription payment of %.2f %s. " +
            "Please review your account and update your payment details to ensure uninterrupted access to your subscription.\n\n" +
            "Sincerely,\n" +
            "The RecoverMandate Team"
    );

    /**
     * Generates a deterministic recovery message template based on failure category.
     *
     * @param category      Failure category (e.g. insufficient_funds, technical_decline)
     * @param amountInPaise Amount in smallest currency unit (e.g. paise)
     * @param currency      Currency code (e.g. INR)
     * @return Formatted recovery message draft
     */
    public String generateTemplate(String category, Long amountInPaise, String currency) {
        String safeCategory = category != null ? category.toLowerCase(Locale.ROOT) : "unknown";
        String template = TEMPLATES.getOrDefault(safeCategory, TEMPLATES.get("unknown"));

        double amountFormatted = amountInPaise != null ? amountInPaise / 100.0 : 0.0;
        String safeCurrency = currency != null && !currency.isBlank() ? currency : "INR";

        log.info("Heuristic fallback draft generated for category={}", safeCategory);
        return String.format(Locale.US, template, amountFormatted, safeCurrency);
    }
}
