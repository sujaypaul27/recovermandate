package com.recovermandate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to validate AI-generated draft messages.
 * Uses deterministic rules, no AI self-grading.
 */
@Slf4j
@Service
public class RecoveryActionValidationService {

    private static final List<String> DENY_LIST = List.of("discount", "refund", "waiver", "free");
    private static final List<String> AGGRESSIVE_TONE_LIST = List.of("sue", "legal", "police", "court", "lawsuit", "threat", "penalty", "immediately", "failure to", "suspend", "terminate", "final notice", "consequences");

    private static final Pattern CURRENCY_PREFIX_PATTERN = Pattern.compile("(?i)(?:\\$|£|€|₹|rs\\.?|inr)\\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern CURRENCY_SUFFIX_PATTERN = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(?:usd|inr|eur|gbp)");

    /**
     * Validates a drafted message.
     *
     * @param draftMessage The AI drafted message
     * @param actualAmount The actual amount of the failed payment in paise/cents
     * @return Optional containing the block reason if invalid, or empty if valid
     */
    public Optional<String> validateDraft(String draftMessage, Long actualAmount) {
        if (draftMessage == null || draftMessage.isBlank()) {
            return Optional.of("Draft message is empty");
        }

        String lowerDraft = draftMessage.toLowerCase();

        // 1. Deny-list check
        for (String word : DENY_LIST) {
            if (lowerDraft.contains(word)) {
                return Optional.of("Draft contains unauthorized offer language: " + word);
            }
        }

        // 2. Tone check
        for (String word : AGGRESSIVE_TONE_LIST) {
            if (lowerDraft.contains(word)) {
                return Optional.of("Draft contains aggressive or threatening language: " + word);
            }
        }

        // 3. Amount check
        double expectedAmount = actualAmount != null ? actualAmount / 100.0 : 0.0;

        // Check currency prefix (e.g. ₹499.00, $10.00, Rs. 10.00, INR 10.00)
        Matcher currMatcher = CURRENCY_PREFIX_PATTERN.matcher(draftMessage);
        while (currMatcher.find()) {
            try {
                double mentionedAmount = Double.parseDouble(currMatcher.group(1));
                if (Math.abs(mentionedAmount - expectedAmount) > 0.01) {
                    return Optional.of(String.format("Draft mentions incorrect monetary amount: %.2f (expected %.2f)", mentionedAmount, expectedAmount));
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // Check currency suffix (e.g. 499.00 INR, 10.00 USD)
        Matcher currSuffixMatcher = CURRENCY_SUFFIX_PATTERN.matcher(draftMessage);
        while (currSuffixMatcher.find()) {
            try {
                double mentionedAmount = Double.parseDouble(currSuffixMatcher.group(1));
                if (Math.abs(mentionedAmount - expectedAmount) > 0.01) {
                    return Optional.of(String.format("Draft mentions incorrect monetary amount: %.2f (expected %.2f)", mentionedAmount, expectedAmount));
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return Optional.empty();
    }
}
