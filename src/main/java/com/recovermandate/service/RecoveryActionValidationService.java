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
        // Extract numbers from the draft message
        Pattern numberPattern = Pattern.compile("\\d+(\\.\\d+)?");
        Matcher matcher = numberPattern.matcher(draftMessage);
        
        double expectedAmount = actualAmount != null ? actualAmount / 100.0 : 0.0;
        
        while (matcher.find()) {
            try {
                double mentionedAmount = Double.parseDouble(matcher.group());
                // We check if the mentioned number looks like the amount. 
                // A draft might mention days like "3 days ago". So we only reject if a number is clearly not the expected amount and seems like a currency amount, 
                // but a simpler rule is: if the expected amount is NOT mentioned anywhere in the text, and there are numbers, or if any number doesn't match?
                // The requirement: "Reject/flag draft if it mentions any monetary amount that doesn't match the actual failed payment amount."
                // Since it's hard to distinguish days from amounts without NLP, we look for monetary indicators (like $, INR, Rs, etc.) near the number, or we just ensure the exact amount string exists.
                // Let's look for currency symbols near the number.
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        // Simpler deterministic rule for amount:
        // Extract all currency-like amounts (e.g. $10.00, Rs. 10.00, 10.00 INR)
        Pattern currencyPattern = Pattern.compile("(?i)(?:\\$|£|€|rs\\.?|inr)\\s*(\\d+(?:\\.\\d+)?)");
        Matcher currMatcher = currencyPattern.matcher(draftMessage);
        boolean foundMismatchedAmount = false;
        
        while (currMatcher.find()) {
            try {
                double mentionedAmount = Double.parseDouble(currMatcher.group(1));
                if (Math.abs(mentionedAmount - expectedAmount) > 0.01) {
                    foundMismatchedAmount = true;
                    return Optional.of(String.format("Draft mentions incorrect monetary amount: %.2f (expected %.2f)", mentionedAmount, expectedAmount));
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        // Also check amount followed by currency
        Pattern currencyPatternSuffix = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(?:usd|inr|eur|gbp)");
        Matcher currSuffixMatcher = currencyPatternSuffix.matcher(draftMessage);
        while (currSuffixMatcher.find()) {
            try {
                double mentionedAmount = Double.parseDouble(currSuffixMatcher.group(1));
                if (Math.abs(mentionedAmount - expectedAmount) > 0.01) {
                    return Optional.of(String.format("Draft mentions incorrect monetary amount: %.2f (expected %.2f)", mentionedAmount, expectedAmount));
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return Optional.empty();
    }
}
